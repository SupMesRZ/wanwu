package com.huohuo.scenicaiguide.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huohuo.scenicaiguide.common.exception.BusinessException;
import com.huohuo.scenicaiguide.system.dto.request.AdminUserSaveRequest;
import com.huohuo.scenicaiguide.system.dto.response.AdminUserResponse;
import com.huohuo.scenicaiguide.system.entity.SysRole;
import com.huohuo.scenicaiguide.system.entity.SysUser;
import com.huohuo.scenicaiguide.system.entity.SysUserRole;
import com.huohuo.scenicaiguide.system.mapper.SysRoleMapper;
import com.huohuo.scenicaiguide.system.mapper.SysUserMapper;
import com.huohuo.scenicaiguide.system.mapper.SysUserRoleMapper;
import com.huohuo.scenicaiguide.system.service.AdminUserService;
import com.huohuo.scenicaiguide.system.service.UserPermissionService;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 管理员账号管理测试
 */
class AdminUserServiceTest {

    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final SysUserRoleMapper userRoleMapper = mock(SysUserRoleMapper.class);
    private final SysRoleMapper roleMapper = mock(SysRoleMapper.class);
    private final UserPermissionService userPermissionService = mock(UserPermissionService.class);
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final AdminUserService service = new AdminUserService(
        userMapper, userRoleMapper, roleMapper, userPermissionService, passwordEncoder);

    @Test
    void createHashesPasswordAndNeverReturnsIt() {
        when(userMapper.selectCount(any())).thenReturn(0L);
        AdminUserSaveRequest request = new AdminUserSaveRequest();
        request.setUsername("operator");
        request.setNickname("运营小李");
        request.setPassword("Sup3rSecret!");

        AdminUserResponse response = service.create(request);

        assertThat(response.getUsername()).isEqualTo("operator");
        assertThat(response.getIsRoot()).isFalse();
        assertThat(response.getStatus()).isEqualTo(1);
        // 响应体不含任何密码字段，插入库中的必须是哈希
        verify(userMapper).insert(org.mockito.ArgumentMatchers.argThat((SysUser user) ->
            !"Sup3rSecret!".equals(user.getPassword())
                && passwordEncoder.matches("Sup3rSecret!", user.getPassword())));
    }

    @Test
    void createRejectsDuplicateUsername() {
        when(userMapper.selectCount(any())).thenReturn(1L);
        AdminUserSaveRequest request = new AdminUserSaveRequest();
        request.setUsername("operator");
        request.setPassword("Sup3rSecret!");

        assertThatThrownBy(() -> service.create(request))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("用户名已被占用");
        verify(userMapper, never()).insert(any(SysUser.class));
    }

    @Test
    void createRequiresPassword() {
        AdminUserSaveRequest request = new AdminUserSaveRequest();
        request.setUsername("operator");

        assertThatThrownBy(() -> service.create(request))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("必须设置密码");
    }

    @Test
    void updateKeepsExistingPasswordWhenBlank() {
        SysUser existing = existingUser(2L, 0);
        String originalHash = existing.getPassword();
        when(userMapper.selectById(2L)).thenReturn(existing);
        AdminUserSaveRequest request = new AdminUserSaveRequest();
        request.setNickname("改个昵称");

        service.update(2L, request);

        verify(userMapper).updateById(org.mockito.ArgumentMatchers.argThat((SysUser user) ->
            originalHash.equals(user.getPassword()) && "改个昵称".equals(user.getNickname())));
    }

    @Test
    void rootAccountCannotBeDeletedOrDisabled() {
        SysUser root = existingUser(1L, 1);
        when(userMapper.selectById(1L)).thenReturn(root);

        assertThatThrownBy(() -> service.delete(1L))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("不可删除");

        AdminUserSaveRequest disable = new AdminUserSaveRequest();
        disable.setStatus(0);
        assertThatThrownBy(() -> service.update(1L, disable))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("不可停用");

        verify(userMapper, never()).deleteById(any(Long.class));
    }

    @Test
    void deleteRemovesNonRootAccount() {
        when(userMapper.selectById(2L)).thenReturn(existingUser(2L, 0));

        service.delete(2L);

        verify(userMapper).deleteById(2L);
    }

    @Test
    void updateRejectsMissingAccount() {
        when(userMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> service.update(99L, new AdminUserSaveRequest()))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("管理员不存在");
    }

    @Test
    void createAssignsRolesAndEvictsPermissionCache() {
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(userMapper.insert(any(SysUser.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, SysUser.class).setId(7L);
            return 1;
        });
        when(roleMapper.selectList(any())).thenReturn(java.util.List.of(role(3L, "内容运营")));

        AdminUserSaveRequest request = new AdminUserSaveRequest();
        request.setUsername("editor");
        request.setPassword("Sup3rSecret!");
        request.setRoleIds(java.util.List.of(3L));

        AdminUserResponse response = service.create(request);

        assertThat(response.getRoleIds()).containsExactly(3L);
        assertThat(response.getRoleNames()).containsExactly("内容运营");
        verify(userRoleMapper).insert(org.mockito.ArgumentMatchers.argThat((SysUserRole relation) ->
            relation.getUserId() == 7L && relation.getRoleId() == 3L));
        // 分配角色必须立即失效权限缓存，否则新授权要等缓存过期才生效
        verify(userPermissionService).evictUser(7L);
    }

    @Test
    void assigningUnknownRoleIsRejected() {
        when(userMapper.selectById(2L)).thenReturn(existingUser(2L, 0));
        when(roleMapper.selectList(any())).thenReturn(java.util.List.of());

        AdminUserSaveRequest request = new AdminUserSaveRequest();
        request.setRoleIds(java.util.List.of(999L));

        assertThatThrownBy(() -> service.update(2L, request))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("不存在的角色");
        verify(userRoleMapper, never()).insert(any(SysUserRole.class));
    }

    @Test
    void updateWithoutRoleIdsKeepsExistingAssignment() {
        when(userMapper.selectById(2L)).thenReturn(existingUser(2L, 0));
        when(userRoleMapper.selectList(any())).thenReturn(java.util.List.of(userRole(2L, 3L)));
        when(roleMapper.selectList(any())).thenReturn(java.util.List.of(role(3L, "内容运营")));

        AdminUserResponse response = service.update(2L, new AdminUserSaveRequest());

        assertThat(response.getRoleIds()).containsExactly(3L);
        // roleIds 传 null 表示不动角色，不能顺手清空
        verify(userRoleMapper, never()).delete(any());
    }

    private SysRole role(Long id, String name) {
        SysRole role = new SysRole();
        role.setId(id);
        role.setName(name);
        role.setStatus(1);
        return role;
    }

    private SysUserRole userRole(Long userId, Long roleId) {
        SysUserRole relation = new SysUserRole();
        relation.setUserId(userId);
        relation.setRoleId(roleId);
        return relation;
    }

    private SysUser existingUser(Long id, int isRoot) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername(isRoot == 1 ? "root" : "operator");
        user.setPassword(passwordEncoder.encode("OriginalPass1"));
        user.setIsRoot(isRoot);
        user.setStatus(1);
        user.setDeleted(0);
        return user;
    }

    @Test
    void pageClampsOversizedPageSize() {
        when(userMapper.selectPage(any(), any(LambdaQueryWrapper.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.page(null, 0, 5000);

        assertThat(result.page()).isEqualTo(1);
        assertThat(result.pageSize()).isEqualTo(100);
    }
}
