package com.huohuo.scenicaiguide.system.service;

import com.huohuo.scenicaiguide.system.entity.SysRole;
import com.huohuo.scenicaiguide.system.entity.SysRolePermission;
import com.huohuo.scenicaiguide.system.entity.SysUserRole;
import com.huohuo.scenicaiguide.system.mapper.SysRoleMapper;
import com.huohuo.scenicaiguide.system.mapper.SysRolePermissionMapper;
import com.huohuo.scenicaiguide.system.mapper.SysUserRoleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 管理员权限集聚合测试
 * <p>
 * 覆盖聚合链路（用户 → 启用角色 → 权限码）、缓存命中与回落、以及缓存失效
 */
class UserPermissionServiceTest {

    private static final String KEY_PREFIX = "scenic-ai-guide:system:user-permission:";

    private final SysUserRoleMapper userRoleMapper = mock(SysUserRoleMapper.class);
    private final SysRoleMapper roleMapper = mock(SysRoleMapper.class);
    private final SysRolePermissionMapper rolePermissionMapper = mock(SysRolePermissionMapper.class);
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOps = mock(ValueOperations.class);

    private UserPermissionService service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        service = new UserPermissionService(userRoleMapper, roleMapper, rolePermissionMapper, redisTemplate);
    }

    // ========== 聚合链路 ==========

    @Test
    void aggregatesPermissionsOfAssignedRoles() {
        when(userRoleMapper.selectList(any())).thenReturn(List.of(userRole(7L, 3L)));
        when(roleMapper.selectList(any())).thenReturn(List.of(role(3L)));
        when(rolePermissionMapper.selectList(any())).thenReturn(List.of(
            rolePermission(3L, "scenic:read"),
            rolePermission(3L, "qa:read")));

        Set<String> permissions = service.permissionsOf(7L);

        assertThat(permissions).containsExactlyInAnyOrder("scenic:read", "qa:read");
        // 首次查库后必须回写缓存，否则每个请求都要穿透到三次查询
        verify(valueOps).set(eq(KEY_PREFIX + "7"), any(), eq(Duration.ofMinutes(30)));
    }

    @Test
    void disabledRoleGrantsNothing() {
        when(userRoleMapper.selectList(any())).thenReturn(List.of(userRole(7L, 3L)));
        // 角色查询带 status=1 条件，停用角色查不出来
        when(roleMapper.selectList(any())).thenReturn(List.of());

        assertThat(service.permissionsOf(7L)).isEmpty();
        // 停用角色不该再去查它勾了什么，"停用"必须是真的停用而不只是改了显示
        verifyNoInteractions(rolePermissionMapper);
    }

    @Test
    void userWithoutAnyRoleGetsEmptyPermission() {
        when(userRoleMapper.selectList(any())).thenReturn(List.of());

        assertThat(service.permissionsOf(7L)).isEmpty();
        verifyNoInteractions(roleMapper);
        verifyNoInteractions(rolePermissionMapper);
    }

    @Test
    void nullUserIdGetsEmptyPermissionWithoutLookup() {
        assertThat(service.permissionsOf(null)).isEmpty();
        verifyNoInteractions(userRoleMapper);
        verifyNoInteractions(valueOps);
    }

    // ========== 缓存 ==========

    @Test
    void cacheHitSkipsDatabase() {
        when(valueOps.get(KEY_PREFIX + "7")).thenReturn("scenic:read,qa:read");

        assertThat(service.permissionsOf(7L)).containsExactly("scenic:read", "qa:read");
        verifyNoInteractions(userRoleMapper);
    }

    @Test
    void cachedEmptyStringMeansNoPermissionRatherThanCacheMiss() {
        when(valueOps.get(KEY_PREFIX + "7")).thenReturn("");

        assertThat(service.permissionsOf(7L)).isEmpty();
        // 空串是"确实一个权限都没有"的有效缓存，不能被当成未命中再去查一遍库
        verifyNoInteractions(userRoleMapper);
    }

    @Test
    void redisFailureFallsBackToDatabase() {
        when(valueOps.get(KEY_PREFIX + "7")).thenThrow(new IllegalStateException("redis down"));
        when(userRoleMapper.selectList(any())).thenReturn(List.of(userRole(7L, 3L)));
        when(roleMapper.selectList(any())).thenReturn(List.of(role(3L)));
        when(rolePermissionMapper.selectList(any())).thenReturn(List.of(rolePermission(3L, "scenic:read")));

        // Redis 挂了只该降级为慢，不该降级为无权限
        assertThat(service.permissionsOf(7L)).containsExactly("scenic:read");
    }

    @Test
    void evictUserDeletesOwnKeyOnly() {
        service.evictUser(7L);

        verify(redisTemplate).delete(KEY_PREFIX + "7");
    }

    @Test
    void evictUserIgnoresNullId() {
        service.evictUser(null);

        verify(redisTemplate, never()).delete(any(String.class));
    }

    @Test
    void evictByRoleClearsEveryHolder() {
        when(userRoleMapper.selectList(any())).thenReturn(List.of(userRole(7L, 3L), userRole(8L, 3L)));

        service.evictByRole(3L);

        // 角色的权限勾选一变，持有该角色的每个人都要立刻失效
        verify(redisTemplate).delete(KEY_PREFIX + "7");
        verify(redisTemplate).delete(KEY_PREFIX + "8");
    }

    // ========== 权限判定 ==========

    @Test
    void hasAnyPermissionMatchesAnyOfRequired() {
        when(valueOps.get(KEY_PREFIX + "7")).thenReturn("qa:write");

        assertThat(service.hasAnyPermission(7L, List.of("qa:read", "qa:write"))).isTrue();
        assertThat(service.hasAnyPermission(7L, List.of("scenic:write"))).isFalse();
    }

    @Test
    void emptyRequirementIsAlwaysAllowed() {
        assertThat(service.hasAnyPermission(7L, List.of())).isTrue();
        verifyNoInteractions(valueOps);
    }

    // ========== 测试数据 ==========

    private SysUserRole userRole(Long userId, Long roleId) {
        SysUserRole relation = new SysUserRole();
        relation.setUserId(userId);
        relation.setRoleId(roleId);
        return relation;
    }

    private SysRole role(Long id) {
        SysRole role = new SysRole();
        role.setId(id);
        role.setStatus(1);
        return role;
    }

    private SysRolePermission rolePermission(Long roleId, String code) {
        SysRolePermission permission = new SysRolePermission();
        permission.setRoleId(roleId);
        permission.setPermissionCode(code);
        return permission;
    }
}
