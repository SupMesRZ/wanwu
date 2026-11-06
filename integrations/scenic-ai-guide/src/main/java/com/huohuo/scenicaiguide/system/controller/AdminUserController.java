package com.huohuo.scenicaiguide.system.controller;

import com.huohuo.scenicaiguide.common.api.ApiResponse;
import com.huohuo.scenicaiguide.common.api.PageResponse;
import com.huohuo.scenicaiguide.system.dto.request.AdminUserSaveRequest;
import com.huohuo.scenicaiguide.system.dto.response.AdminUserResponse;
import com.huohuo.scenicaiguide.system.dto.response.CurrentUserResponse;
import com.huohuo.scenicaiguide.system.security.AuthPrincipal;
import com.huohuo.scenicaiguide.system.security.PermissionCode;
import com.huohuo.scenicaiguide.system.security.RequireAdmin;
import com.huohuo.scenicaiguide.system.security.RequirePermission;
import com.huohuo.scenicaiguide.system.security.RequireRoot;
import com.huohuo.scenicaiguide.system.service.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员账号管理接口
 *
 * <p>查询需 {@code system:user:manage} 权限，增删改仍限 root，
 * 与角色接口同理："能建账号并给它配角色"等价于提权
 */
@Tag(name = "管理员账号管理", description = "后台管理员账号的查询与维护")
@RestController
@RequestMapping("/api/v1/admin/users")
@RequireAdmin
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @Operation(summary = "当前登录管理员信息", description = "返回身份、角色与权限码，供前端控制菜单可见性")
    @GetMapping("/me")
    public ApiResponse<CurrentUserResponse> me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        AuthPrincipal principal = (AuthPrincipal) auth.getPrincipal();
        return ApiResponse.ok(adminUserService.currentUser(principal.userId()));
    }

    @Operation(summary = "分页查询管理员", description = "支持按用户名/昵称/手机号模糊搜索")
    @GetMapping
    @RequirePermission(PermissionCode.USER_MANAGE)
    public ApiResponse<PageResponse<AdminUserResponse>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.ok(adminUserService.page(keyword, page, pageSize));
    }

    @Operation(summary = "新增管理员")
    @PostMapping
    @RequireRoot
    public ApiResponse<AdminUserResponse> create(@Valid @RequestBody AdminUserSaveRequest request) {
        return ApiResponse.ok(adminUserService.create(request));
    }

    @Operation(summary = "编辑管理员", description = "密码留空表示不修改")
    @PutMapping("/{id}")
    @RequireRoot
    public ApiResponse<AdminUserResponse> update(@PathVariable Long id,
                                                 @Valid @RequestBody AdminUserSaveRequest request) {
        return ApiResponse.ok(adminUserService.update(id, request));
    }

    @Operation(summary = "删除管理员", description = "逻辑删除，超级管理员不可删除")
    @DeleteMapping("/{id}")
    @RequireRoot
    public ApiResponse<Void> delete(@PathVariable Long id) {
        adminUserService.delete(id);
        return ApiResponse.ok();
    }
}
