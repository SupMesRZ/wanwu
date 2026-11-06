package com.huohuo.scenicaiguide.scenic.controller;

import com.huohuo.scenicaiguide.common.api.ApiResponse;
import com.huohuo.scenicaiguide.scenic.dto.request.MapWorkbenchDraftRequest;
import com.huohuo.scenicaiguide.scenic.dto.request.ScenicMapDraftRequest;
import com.huohuo.scenicaiguide.scenic.dto.response.MapPublishCheckResponse;
import com.huohuo.scenicaiguide.scenic.dto.response.MapWorkbenchResponse;
import com.huohuo.scenicaiguide.scenic.dto.response.ScenicMapDraftResponse;
import com.huohuo.scenicaiguide.scenic.service.ScenicAdminService;
import com.huohuo.scenicaiguide.scenic.service.ScenicMapPublishService;
import com.huohuo.scenicaiguide.system.security.PermissionCode;
import com.huohuo.scenicaiguide.system.security.RequireAdmin;
import com.huohuo.scenicaiguide.system.security.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "地图草稿", description = "地图工作台草稿")
@RestController
@RequestMapping("/api/v1/admin/map-drafts")
@RequireAdmin
@RequirePermission(PermissionCode.SCENIC_READ)
public class ScenicMapDraftController {

    private final ScenicAdminService scenicAdminService;
    private final ScenicMapPublishService scenicMapPublishService;

    @Autowired
    public ScenicMapDraftController(ScenicAdminService scenicAdminService,
                                    ScenicMapPublishService scenicMapPublishService) {
        this.scenicAdminService = scenicAdminService;
        this.scenicMapPublishService = scenicMapPublishService;
    }

    @Operation(summary = "查询默认景区地图草稿")
    @GetMapping
    public ApiResponse<List<ScenicMapDraftResponse>> listDefaultMapDrafts() {
        return ApiResponse.ok(scenicAdminService.listDefaultMapDrafts());
    }

    @Operation(summary = "创建默认景区地图草稿")
    @PostMapping
    @RequirePermission(PermissionCode.SCENIC_WRITE)
    public ApiResponse<ScenicMapDraftResponse> createDefaultMapDraft(
            @RequestBody ScenicMapDraftRequest request) {
        return ApiResponse.ok(scenicAdminService.createDefaultMapDraft(request));
    }

    @Operation(summary = "加载地图草稿工作台数据")
    @GetMapping("/{draftId}/workbench")
    public ApiResponse<MapWorkbenchResponse> getMapDraftWorkbench(@PathVariable Long draftId) {
        return ApiResponse.ok(scenicAdminService.getMapDraftWorkbench(draftId));
    }

    @Operation(summary = "保存地图草稿工作台数据")
    @PutMapping("/{draftId}/workbench")
    @RequirePermission(PermissionCode.SCENIC_WRITE)
    public ApiResponse<MapWorkbenchResponse> saveMapDraftWorkbench(
            @PathVariable Long draftId,
            @RequestBody MapWorkbenchDraftRequest request) {
        return ApiResponse.ok(scenicAdminService.saveMapDraftWorkbench(draftId, request));
    }

    @Operation(summary = "删除地图草稿")
    @DeleteMapping("/{draftId}")
    @RequirePermission(PermissionCode.SCENIC_WRITE)
    public ApiResponse<Void> deleteMapDraft(@PathVariable Long draftId) {
        scenicAdminService.deleteMapDraft(draftId);
        return ApiResponse.ok();
    }

    @Operation(summary = "地图草稿发布校验")
    @GetMapping("/{draftId}/publish-check")
    public ApiResponse<MapPublishCheckResponse> checkMapDraftPublish(@PathVariable Long draftId) {
        return ApiResponse.ok(scenicMapPublishService.check(draftId));
    }

    @Operation(summary = "设置地图草稿为线上草稿")
    // 发布会直接影响游客端展示，单独一个权限码，允许"能编辑草稿但不能发布"
    @PostMapping("/{draftId}/publish")
    @RequirePermission(PermissionCode.MAP_PUBLISH)
    public ApiResponse<ScenicMapDraftResponse> publishMapDraft(@PathVariable Long draftId) {
        return ApiResponse.ok(scenicMapPublishService.publish(draftId));
    }

}
