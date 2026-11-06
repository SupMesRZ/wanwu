package com.huohuo.scenicaiguide.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huohuo.scenicaiguide.common.api.PageResponse;
import com.huohuo.scenicaiguide.common.enums.ErrorCode;
import com.huohuo.scenicaiguide.common.exception.BusinessException;
import com.huohuo.scenicaiguide.system.dto.request.AdminUserSaveRequest;
import com.huohuo.scenicaiguide.system.dto.response.AdminUserResponse;
import com.huohuo.scenicaiguide.system.dto.response.CurrentUserResponse;
import com.huohuo.scenicaiguide.system.entity.SysRole;
import com.huohuo.scenicaiguide.system.entity.SysUser;
import com.huohuo.scenicaiguide.system.entity.SysUserRole;
import com.huohuo.scenicaiguide.system.mapper.SysRoleMapper;
import com.huohuo.scenicaiguide.system.mapper.SysUserMapper;
import com.huohuo.scenicaiguide.system.mapper.SysUserRoleMapper;
import com.huohuo.scenicaiguide.system.security.PermissionCode;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 管理员账号管理服务
 *
 * <p>root 账号受保护：不可删除、不可停用，避免把自己锁在系统外
 */
@Service
public class AdminUserService {

    private static final int MAX_PAGE_SIZE = 100;

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final UserPermissionService userPermissionService;
    private final PasswordEncoder passwordEncoder;

    public AdminUserService(SysUserMapper userMapper,
                            SysUserRoleMapper userRoleMapper,
                            SysRoleMapper roleMapper,
                            UserPermissionService userPermissionService,
                            PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.userPermissionService = userPermissionService;
        this.passwordEncoder = passwordEncoder;
    }

    public PageResponse<AdminUserResponse> page(String keyword, long page, long pageSize) {
        long safePage = Math.max(page, 1);
        long safeSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);

        LambdaQueryWrapper<SysUser> query = new LambdaQueryWrapper<SysUser>()
            .orderByDesc(SysUser::getId);
        if (StringUtils.hasText(keyword)) {
            String trimmed = keyword.trim();
            query.and(wrapper -> wrapper
                .like(SysUser::getUsername, trimmed)
                .or().like(SysUser::getNickname, trimmed)
                .or().like(SysUser::getPhone, trimmed));
        }

        Page<SysUser> result = userMapper.selectPage(new Page<>(safePage, safeSize), query);
        Map<Long, List<SysRole>> rolesByUser = rolesOfUsers(
            result.getRecords().stream().map(SysUser::getId).toList());
        List<AdminUserResponse> records = result.getRecords().stream()
            .map(user -> toResponse(user, rolesByUser.getOrDefault(user.getId(), List.of())))
            .toList();
        return PageResponse.of(records, result.getCurrent(), result.getSize(), result.getTotal());
    }

    /**
     * 当前登录管理员的身份与权限，供前端驱动菜单可见性
     *
     * <p>root 直接返回全量权限码，与切面里的 root 放行保持一致，避免前端菜单比接口更严
     */
    public CurrentUserResponse currentUser(Long userId) {
        SysUser user = getExisting(userId);
        List<SysRole> roles = rolesOfUsers(List.of(userId)).getOrDefault(userId, List.of());

        CurrentUserResponse response = new CurrentUserResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setNickname(user.getNickname());
        response.setIsRoot(isRoot(user));
        response.setRoleNames(roles.stream().map(SysRole::getName).toList());
        response.setPermissions(isRoot(user)
            ? List.copyOf(PermissionCode.allCodes())
            : List.copyOf(userPermissionService.permissionsOf(userId)));
        return response;
    }

    @Transactional
    public AdminUserResponse create(AdminUserSaveRequest request) {
        if (!StringUtils.hasText(request.getUsername())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "用户名不能为空");
        }
        if (!StringUtils.hasText(request.getPassword())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "新增管理员必须设置密码");
        }
        requireUsernameAvailable(request.getUsername(), null);

        SysUser user = new SysUser();
        user.setUsername(request.getUsername().trim());
        user.setNickname(request.getNickname());
        user.setPhone(request.getPhone());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setIsRoot(0);
        user.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        user.setDeleted(0);
        userMapper.insert(user);

        List<SysRole> roles = replaceRoles(user.getId(), request.getRoleIds());
        return toResponse(user, roles);
    }

    @Transactional
    public AdminUserResponse update(Long id, AdminUserSaveRequest request) {
        SysUser user = getExisting(id);
        if (StringUtils.hasText(request.getUsername())) {
            requireUsernameAvailable(request.getUsername(), id);
            user.setUsername(request.getUsername().trim());
        }
        user.setNickname(request.getNickname());
        user.setPhone(request.getPhone());
        if (StringUtils.hasText(request.getPassword())) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        if (request.getStatus() != null) {
            // root 停用后无人能恢复权限，直接拒绝
            if (isRoot(user) && request.getStatus() != 1) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "超级管理员不可停用");
            }
            user.setStatus(request.getStatus());
        }
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);

        List<SysRole> roles = request.getRoleIds() == null
            ? rolesOfUsers(List.of(id)).getOrDefault(id, List.of())
            : replaceRoles(id, request.getRoleIds());
        return toResponse(user, roles);
    }

    @Transactional
    public void delete(Long id) {
        SysUser user = getExisting(id);
        if (isRoot(user)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "超级管理员不可删除");
        }
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, id));
        userMapper.deleteById(id);
        userPermissionService.evictUser(id);
    }

    /**
     * 全量替换用户的角色分配，返回落库后的角色
     */
    private List<SysRole> replaceRoles(Long userId, List<Long> requested) {
        if (requested == null) {
            return List.of();
        }
        Set<Long> distinct = new LinkedHashSet<>(requested);
        distinct.remove(null);

        List<SysRole> roles = List.of();
        if (!distinct.isEmpty()) {
            roles = roleMapper.selectList(new LambdaQueryWrapper<SysRole>().in(SysRole::getId, distinct));
            // 角色不存在时直接拒绝，避免写入指向空角色的脏关联
            if (roles.size() != distinct.size()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "存在不存在的角色");
            }
        }

        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        for (SysRole role : roles) {
            SysUserRole relation = new SysUserRole();
            relation.setUserId(userId);
            relation.setRoleId(role.getId());
            relation.setCreatedAt(LocalDateTime.now());
            userRoleMapper.insert(relation);
        }
        userPermissionService.evictUser(userId);
        return roles;
    }

    /**
     * 批量查询用户的角色，避免列表页逐行查询
     */
    private Map<Long, List<SysRole>> rolesOfUsers(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        List<SysUserRole> relations = userRoleMapper.selectList(
            new LambdaQueryWrapper<SysUserRole>().in(SysUserRole::getUserId, userIds));
        if (relations.isEmpty()) {
            return Map.of();
        }
        List<Long> roleIds = relations.stream().map(SysUserRole::getRoleId).distinct().toList();
        Map<Long, SysRole> roleById = new LinkedHashMap<>();
        for (SysRole role : roleMapper.selectList(new LambdaQueryWrapper<SysRole>().in(SysRole::getId, roleIds))) {
            roleById.put(role.getId(), role);
        }

        Map<Long, List<SysRole>> grouped = new LinkedHashMap<>();
        for (SysUserRole relation : relations) {
            SysRole role = roleById.get(relation.getRoleId());
            if (role != null) {
                grouped.computeIfAbsent(relation.getUserId(), key -> new ArrayList<>()).add(role);
            }
        }
        return grouped;
    }

    private SysUser getExisting(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "管理员不存在");
        }
        return user;
    }

    private void requireUsernameAvailable(String username, Long excludeId) {
        LambdaQueryWrapper<SysUser> query = new LambdaQueryWrapper<SysUser>()
            .eq(SysUser::getUsername, username.trim());
        if (excludeId != null) {
            query.ne(SysUser::getId, excludeId);
        }
        if (userMapper.selectCount(query) > 0) {
            throw new BusinessException(ErrorCode.RESOURCE_ALREADY_EXISTS, "用户名已被占用");
        }
    }

    private boolean isRoot(SysUser user) {
        return Integer.valueOf(1).equals(user.getIsRoot());
    }

    private AdminUserResponse toResponse(SysUser user, List<SysRole> roles) {
        AdminUserResponse response = new AdminUserResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setNickname(user.getNickname());
        response.setPhone(user.getPhone());
        response.setIsRoot(isRoot(user));
        response.setStatus(user.getStatus());
        response.setRoleIds(roles.stream().map(SysRole::getId).toList());
        response.setRoleNames(roles.stream().map(SysRole::getName).toList());
        response.setLastLoginIp(user.getLastLoginIp());
        response.setLastLoginAt(user.getLastLoginAt());
        response.setCreatedAt(user.getCreatedAt());
        return response;
    }
}
