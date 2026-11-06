package com.huohuo.scenicaiguide.system.controller;

import com.huohuo.scenicaiguide.common.api.ApiResponse;
import com.huohuo.scenicaiguide.common.api.PageResponse;
import com.huohuo.scenicaiguide.system.dto.request.RoleSaveRequest;
import com.huohuo.scenicaiguide.system.dto.response.PermissionCatalogResponse;
import com.huohuo.scenicaiguide.system.dto.response.RoleResponse;
import com.huohuo.scenicaiguide.system.security.PermissionCode;
import com.huohuo.scenicaiguide.system.security.RequireAdmin;
import com.huohuo.scenicaiguide.system.security.RequirePermission;
import com.huohuo.scenicaiguide.system.security.RequireRoot;
import com.huohuo.scenicaiguide.system.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 角色权限管理接口
 *
 * <p>读操作需 {@code system:role:manage} 权限，增删改仍限 root。
 * 拆开的原因是"能改角色"等价于"能给自己加任意权限"，这条提权路径只留给 root
 */
@Tag(name = "角色权限管理", description = "后台角色的维护与权限勾选")
@RestController
@RequestMapping("/api/v1/admin/roles")
@RequireAdmin
public class AdminRoleController {

    private final RoleService roleService;

    public AdminRoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @Operation(summary = "分页查询角色", description = "支持按标识/名称模糊搜索")
    @GetMapping
    @RequirePermission(PermissionCode.ROLE_MANAGE)
    public ApiResponse<PageResponse<RoleResponse>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.ok(roleService.page(keyword, page, pageSize));
    }

    @Operation(summary = "启用中的角色列表", description = "供管理员编辑页的角色分配下拉使用")
    @GetMapping("/options")
    @RequirePermission({PermissionCode.ROLE_MANAGE, PermissionCode.USER_MANAGE})
    public ApiResponse<List<RoleResponse>> options() {
        return ApiResponse.ok(roleService.listEnabled());
    }

    @Operation(summary = "权限清单", description = "权限码由后端枚举定义，前端只做勾选")
    @GetMapping("/permissions")
    @RequirePermission(PermissionCode.ROLE_MANAGE)
    public ApiResponse<List<PermissionCatalogResponse>> permissions() {
        return ApiResponse.ok(roleService.permissionCatalog());
    }

    @Operation(summary = "查询角色详情")
    @GetMapping("/{id}")
    @RequirePermission(PermissionCode.ROLE_MANAGE)
    public ApiResponse<RoleResponse> detail(@PathVariable Long id) {
        return ApiResponse.ok(roleService.detail(id));
    }

    @Operation(summary = "新增角色")
    @PostMapping
    @RequireRoot
    public ApiResponse<RoleResponse> create(@Valid @RequestBody RoleSaveRequest request) {
        return ApiResponse.ok(roleService.create(request));
    }

    @Operation(summary = "编辑角色", description = "permissions 为 null 表示不改动权限勾选")
    @PutMapping("/{id}")
    @RequireRoot
    public ApiResponse<RoleResponse> update(@PathVariable Long id,
                                            @Valid @RequestBody RoleSaveRequest request) {
        return ApiResponse.ok(roleService.update(id, request));
    }

    @Operation(summary = "更新角色权限勾选", description = "全量覆盖，传空数组表示清空")
    @PutMapping("/{id}/permissions")
    @RequireRoot
    public ApiResponse<RoleResponse> updatePermissions(@PathVariable Long id,
                                                       @RequestBody List<String> permissions) {
        return ApiResponse.ok(roleService.updatePermissions(id, permissions));
    }

    @Operation(summary = "删除角色", description = "内置角色与仍有管理员在用的角色不可删除")
    @DeleteMapping("/{id}")
    @RequireRoot
    public ApiResponse<Void> delete(@PathVariable Long id) {
        roleService.delete(id);
        return ApiResponse.ok();
    }
}
