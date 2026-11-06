package com.huohuo.scenicaiguide.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huohuo.scenicaiguide.common.redis.RedisDomain;
import com.huohuo.scenicaiguide.system.entity.SysRole;
import com.huohuo.scenicaiguide.system.entity.SysRolePermission;
import com.huohuo.scenicaiguide.system.entity.SysUserRole;
import com.huohuo.scenicaiguide.system.enums.SystemRedisFeature;
import com.huohuo.scenicaiguide.system.mapper.SysRoleMapper;
import com.huohuo.scenicaiguide.system.mapper.SysRolePermissionMapper;
import com.huohuo.scenicaiguide.system.mapper.SysUserRoleMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 管理员权限集查询
 * <p>
 * 聚合链路：用户 → 启用中的角色 → 角色勾选的权限码
 * <p>
 * 权限集不写进 JWT：token 通常有数小时有效期，把权限固化进去意味着降权后旧 token 仍然放行。
 * 改为按 userId 从 Redis 读，DB 为准，角色或勾选变更时立即失效
 */
@Service
public class UserPermissionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserPermissionService.class);

    private static final String REDIS_PREFIX = "scenic-ai-guide:" + RedisDomain.SYSTEM.getDomain()
        + ":" + SystemRedisFeature.USER_PERMISSION.getValue() + ":";

    /**
     * 缓存兜底过期时间，防止漏失效导致权限长期不刷新
     */
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);

    private static final String SEPARATOR = ",";

    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final SysRolePermissionMapper rolePermissionMapper;
    private final StringRedisTemplate redisTemplate;

    @Autowired
    public UserPermissionService(SysUserRoleMapper userRoleMapper,
                                 SysRoleMapper roleMapper,
                                 SysRolePermissionMapper rolePermissionMapper,
                                 StringRedisTemplate redisTemplate) {
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 查询用户持有的权限码集合
     *
     * @param userId 管理员ID，null 时返回空集
     */
    public Set<String> permissionsOf(Long userId) {
        if (userId == null) {
            return Set.of();
        }
        String cached = readCache(userId);
        if (cached != null) {
            return decode(cached);
        }
        Set<String> permissions = loadFromDatabase(userId);
        writeCache(userId, permissions);
        return permissions;
    }

    /**
     * 判断用户是否持有任一权限
     */
    public boolean hasAnyPermission(Long userId, List<String> required) {
        if (required == null || required.isEmpty()) {
            return true;
        }
        Set<String> owned = permissionsOf(userId);
        return required.stream().anyMatch(owned::contains);
    }

    /**
     * 失效单个用户的权限缓存
     */
    public void evictUser(Long userId) {
        if (userId == null) {
            return;
        }
        safeDelete(redisKey(userId));
    }

    /**
     * 失效持有该角色的所有用户缓存，用于角色权限调整或角色停用
     */
    public void evictByRole(Long roleId) {
        if (roleId == null) {
            return;
        }
        List<SysUserRole> relations = userRoleMapper.selectList(
            new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, roleId));
        for (SysUserRole relation : relations) {
            evictUser(relation.getUserId());
        }
    }

    // ========== 内部实现 ==========

    private Set<String> loadFromDatabase(Long userId) {
        List<SysUserRole> relations = userRoleMapper.selectList(
            new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        if (relations.isEmpty()) {
            return Set.of();
        }
        List<Long> roleIds = relations.stream().map(SysUserRole::getRoleId).distinct().toList();

        // 停用的角色不参与授权，避免"停用角色"变成只改了显示
        List<SysRole> enabledRoles = roleMapper.selectList(
            new LambdaQueryWrapper<SysRole>()
                .in(SysRole::getId, roleIds)
                .eq(SysRole::getStatus, 1));
        if (enabledRoles.isEmpty()) {
            return Set.of();
        }
        List<Long> enabledRoleIds = enabledRoles.stream().map(SysRole::getId).toList();

        List<SysRolePermission> permissions = rolePermissionMapper.selectList(
            new LambdaQueryWrapper<SysRolePermission>().in(SysRolePermission::getRoleId, enabledRoleIds));
        Set<String> codes = new LinkedHashSet<>();
        for (SysRolePermission permission : permissions) {
            if (permission.getPermissionCode() != null) {
                codes.add(permission.getPermissionCode());
            }
        }
        return codes;
    }

    private String readCache(Long userId) {
        try {
            return redisTemplate.opsForValue().get(redisKey(userId));
        } catch (Exception e) {
            // Redis 不可用时回落到数据库，不影响鉴权正确性
            LOGGER.warn("读取权限缓存失败，回落数据库：{}", e.getMessage());
            return null;
        }
    }

    private void writeCache(Long userId, Set<String> permissions) {
        try {
            redisTemplate.opsForValue().set(redisKey(userId), encode(permissions), CACHE_TTL);
        } catch (Exception e) {
            LOGGER.warn("写入权限缓存失败：{}", e.getMessage());
        }
    }

    private void safeDelete(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            LOGGER.warn("清理权限缓存失败：{}", e.getMessage());
        }
    }

    private String encode(Set<String> permissions) {
        return String.join(SEPARATOR, permissions);
    }

    private Set<String> decode(String cached) {
        // 空串代表"确实没有任何权限"，与缓存未命中区分开
        if (cached.isEmpty()) {
            return Set.of();
        }
        return new LinkedHashSet<>(List.of(cached.split(SEPARATOR)));
    }

    private String redisKey(Long userId) {
        return REDIS_PREFIX + userId;
    }
}
