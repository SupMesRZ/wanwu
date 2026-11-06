package com.huohuo.scenicaiguide.scenic.controller;

import com.huohuo.scenicaiguide.common.api.ApiResponse;
import com.huohuo.scenicaiguide.common.api.PageResponse;
import com.huohuo.scenicaiguide.scenic.dto.request.ScenicTagRequest;
import com.huohuo.scenicaiguide.scenic.dto.response.ScenicTagResponse;
import com.huohuo.scenicaiguide.scenic.service.ScenicTagService;
import com.huohuo.scenicaiguide.system.security.PermissionCode;
import com.huohuo.scenicaiguide.system.security.RequireAdmin;
import com.huohuo.scenicaiguide.system.security.RequirePermission;
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

@Tag(name = "标签管理", description = "景点分类词表维护")
@RestController
@RequestMapping("/api/v1/admin/scenic/tags")
@RequireAdmin
@RequirePermission(PermissionCode.SCENIC_READ)
public class ScenicTagController {

    private final ScenicTagService tagService;

    public ScenicTagController(ScenicTagService tagService) {
        this.tagService = tagService;
    }

    @Operation(summary = "分页查询标签")
    @GetMapping
    public ApiResponse<PageResponse<ScenicTagResponse>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.ok(tagService.page(keyword, page, pageSize));
    }

    @Operation(summary = "全部启用标签", description = "供地图工作台的景点标签多选下拉使用")
    @GetMapping("/enabled")
    public ApiResponse<List<ScenicTagResponse>> listEnabled() {
        return ApiResponse.ok(tagService.listEnabled());
    }

    @Operation(summary = "创建标签")
    @PostMapping
    @RequirePermission(PermissionCode.SCENIC_WRITE)
    public ApiResponse<ScenicTagResponse> create(@Valid @RequestBody ScenicTagRequest request) {
        return ApiResponse.ok(tagService.create(request));
    }

    @Operation(summary = "更新标签", description = "标识建后不可改，请求中的 code 会被忽略")
    @PutMapping("/{id}")
    @RequirePermission(PermissionCode.SCENIC_WRITE)
    public ApiResponse<ScenicTagResponse> update(@PathVariable Long id,
                                                 @Valid @RequestBody ScenicTagRequest request) {
        return ApiResponse.ok(tagService.update(id, request));
    }

    @Operation(summary = "删除标签", description = "已发布地图里的引用不做清理，展示时按查不到即忽略处理")
    @DeleteMapping("/{id}")
    @RequirePermission(PermissionCode.SCENIC_WRITE)
    public ApiResponse<Void> delete(@PathVariable Long id) {
        tagService.delete(id);
        return ApiResponse.ok();
    }
}
