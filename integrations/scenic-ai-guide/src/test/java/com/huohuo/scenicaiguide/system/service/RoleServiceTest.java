package com.huohuo.scenicaiguide.system.service;

import com.huohuo.scenicaiguide.common.exception.BusinessException;
import com.huohuo.scenicaiguide.system.dto.request.RoleSaveRequest;
import com.huohuo.scenicaiguide.system.dto.response.PermissionCatalogResponse;
import com.huohuo.scenicaiguide.system.dto.response.RoleResponse;
import com.huohuo.scenicaiguide.system.entity.SysRole;
import com.huohuo.scenicaiguide.system.entity.SysRolePermission;
import com.huohuo.scenicaiguide.system.mapper.SysRoleMapper;
import com.huohuo.scenicaiguide.system.mapper.SysRolePermissionMapper;
import com.huohuo.scenicaiguide.system.mapper.SysUserRoleMapper;
import com.huohuo.scenicaiguide.system.security.PermissionCode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 角色管理测试
 * <p>
 * 重点在三条约束：陌生权限码不落库、内置角色不可删、授权变更即时失效缓存
 */
class RoleServiceTest {

    private final SysRoleMapper roleMapper = mock(SysRoleMapper.class);
    private final SysRolePermissionMapper rolePermissionMapper = mock(SysRolePermissionMapper.class);
    private final SysUserRoleMapper userRoleMapper = mock(SysUserRoleMapper.class);
    private final UserPermissionService userPermissionService = mock(UserPermissionService.class);
    private final RoleService service = new RoleService(
        roleMapper, rolePermissionMapper, userRoleMapper, userPermissionService);

    // ========== 新增 ==========

    @Test
    void createPersistsCheckedPermissions() {
        when(roleMapper.selectCount(any())).thenReturn(0L);
        when(roleMapper.insert(any(SysRole.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, SysRole.class).setId(5L);
            return 1;
        });

        RoleResponse response = service.create(request("content-editor", "内容编辑",
            List.of("qa:write", "qa:read")));

        assertThat(response.getBuiltin()).isFalse();
        assertThat(response.getStatus()).isEqualTo(1);
        // 落库顺序按枚举声明归一，前端勾选顺序不影响比对
        assertThat(response.getPermissions()).containsExactly("qa:read", "qa:write");
        verify(rolePermissionMapper, times(2)).insert(org.mockito.ArgumentMatchers
            .argThat((SysRolePermission permission) -> Long.valueOf(5L).equals(permission.getRoleId())));
    }

    @Test
    void createRejectsUnknownPermissionCode() {
        when(roleMapper.selectCount(any())).thenReturn(0L);
        when(roleMapper.insert(any(SysRole.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, SysRole.class).setId(5L);
            return 1;
        });

        // 陌生权限码没有任何接口会校验它，勾上只会制造"配了但不生效"的错觉
        assertThatThrownBy(() -> service.create(request("bad-role", "越权角色", List.of("scenic:read", "god:mode"))))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("god:mode");
        verify(rolePermissionMapper, never()).insert(any(SysRolePermission.class));
    }

    @Test
    void createRejectsDuplicateCode() {
        when(roleMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.create(request("content-editor", "内容编辑", List.of())))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("角色标识已被占用");
        verify(roleMapper, never()).insert(any(SysRole.class));
    }

    @Test
    void createRequiresCodeAndName() {
        assertThatThrownBy(() -> service.create(request(null, "内容编辑", List.of())))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("角色标识不能为空");
        assertThatThrownBy(() -> service.create(request("content-editor", " ", List.of())))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("角色名称不能为空");
    }

    // ========== 编辑 ==========

    @Test
    void updateEvictsPermissionCacheOfEveryHolder() {
        when(roleMapper.selectById(3L)).thenReturn(role(3L, 0));
        when(rolePermissionMapper.selectList(any())).thenReturn(List.of());

        service.update(3L, request(null, "内容运营", List.of("qa:read")));

        // 改勾选或改状态都会影响授权，不失效缓存就要等 30 分钟兜底过期才生效
        verify(userPermissionService).evictByRole(3L);
    }

    @Test
    void updateWithNullPermissionsKeepsExistingChecks() {
        when(roleMapper.selectById(3L)).thenReturn(role(3L, 0));
        when(rolePermissionMapper.selectList(any())).thenReturn(List.of(rolePermission("qa:read")));

        RoleResponse response = service.update(3L, request(null, "内容运营", null));

        assertThat(response.getPermissions()).containsExactly("qa:read");
        // permissions 传 null 表示只改资料不动授权，不能顺手清空
        verify(rolePermissionMapper, never()).delete(any());
    }

    @Test
    void updateOnMissingRoleFails() {
        when(roleMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> service.update(99L, request(null, "不存在", null)))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("角色不存在");
    }

    @Test
    void updatePermissionsRejectsUnknownCode() {
        when(roleMapper.selectById(3L)).thenReturn(role(3L, 0));

        assertThatThrownBy(() -> service.updatePermissions(3L, List.of("god:mode")))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("god:mode");
        verify(userPermissionService, never()).evictByRole(any());
    }

    // ========== 删除 ==========

    @Test
    void builtinRoleCannotBeDeleted() {
        when(roleMapper.selectById(1L)).thenReturn(role(1L, 1));

        // 删掉内置角色可能把系统调成"谁都管不了"的状态
        assertThatThrownBy(() -> service.delete(1L))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("内置角色不可删除");
        verify(roleMapper, never()).deleteById(any(Long.class));
    }

    @Test
    void roleStillAssignedCannotBeDeleted() {
        when(roleMapper.selectById(3L)).thenReturn(role(3L, 0));
        when(userRoleMapper.selectCount(any())).thenReturn(2L);

        assertThatThrownBy(() -> service.delete(3L))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("仍有管理员在用");
        verify(roleMapper, never()).deleteById(any(Long.class));
    }

    @Test
    void deleteRemovesRoleWithItsPermissionRows() {
        when(roleMapper.selectById(3L)).thenReturn(role(3L, 0));
        when(userRoleMapper.selectCount(any())).thenReturn(0L);

        service.delete(3L);

        // 关联表是物理删除，角色没了勾选行不能留成孤儿
        verify(rolePermissionMapper).delete(any());
        verify(roleMapper).deleteById(3L);
    }

    // ========== 权限清单 ==========

    @Test
    void permissionCatalogCoversEveryDeclaredCode() {
        List<PermissionCatalogResponse> catalog = service.permissionCatalog();

        List<String> codes = catalog.stream()
            .flatMap(group -> group.getPermissions().stream())
            .map(PermissionCatalogResponse.PermissionItem::getCode)
            .toList();
        // 清单直接来自枚举，新增权限码无需改接口就会出现在勾选页
        assertThat(codes).containsExactlyElementsOf(PermissionCode.allCodes());
        assertThat(catalog).allSatisfy(group -> assertThat(group.getModule()).isNotBlank());
    }

    // ========== 测试数据 ==========

    private RoleSaveRequest request(String code, String name, List<String> permissions) {
        RoleSaveRequest request = new RoleSaveRequest();
        request.setCode(code);
        request.setName(name);
        request.setPermissions(permissions);
        return request;
    }

    private SysRole role(Long id, int builtin) {
        SysRole role = new SysRole();
        role.setId(id);
        role.setCode("role-" + id);
        role.setName("角色" + id);
        role.setBuiltin(builtin);
        role.setStatus(1);
        return role;
    }

    private SysRolePermission rolePermission(String code) {
        SysRolePermission permission = new SysRolePermission();
        permission.setPermissionCode(code);
        return permission;
    }
}
