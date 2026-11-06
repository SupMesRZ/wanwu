package com.huohuo.scenicaiguide.scenic.controller;

import com.huohuo.scenicaiguide.common.api.ApiResponse;
import com.huohuo.scenicaiguide.scenic.dto.request.ScenicMapDraftRequest;
import com.huohuo.scenicaiguide.scenic.dto.request.ScenicAreaRequest;
import com.huohuo.scenicaiguide.scenic.dto.response.ScenicMapDraftResponse;
import com.huohuo.scenicaiguide.scenic.dto.response.ScenicAreaResponse;
import com.huohuo.scenicaiguide.scenic.service.ScenicAdminService;
import com.huohuo.scenicaiguide.system.security.PermissionCode;
import com.huohuo.scenicaiguide.system.security.RequireAdmin;
import com.huohuo.scenicaiguide.system.security.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

@Tag(name = "景区管理", description = "景区资产基础管理")
@RestController
@RequestMapping("/api/v1/admin/scenic-areas")
@RequireAdmin
@RequirePermission(PermissionCode.SCENIC_READ)
public class ScenicAreaController {

    private final ScenicAdminService scenicAdminService;

    @Autowired
    public ScenicAreaController(ScenicAdminService scenicAdminService) {
        this.scenicAdminService = scenicAdminService;
    }

    @Operation(summary = "查询景区列表")
    @GetMapping
    public ApiResponse<List<ScenicAreaResponse>> listAreas() {
        return ApiResponse.ok(scenicAdminService.listAreas());
    }

    @Operation(summary = "查询景区详情")
    @GetMapping("/{areaId}")
    public ApiResponse<ScenicAreaResponse> getArea(@PathVariable Long areaId) {
        return ApiResponse.ok(scenicAdminService.getArea(areaId));
    }

    @Operation(summary = "创建景区")
    @PostMapping
    @RequirePermission(PermissionCode.SCENIC_WRITE)
    public ApiResponse<ScenicAreaResponse> createArea(@Valid @RequestBody ScenicAreaRequest request) {
        return ApiResponse.ok(scenicAdminService.createArea(request));
    }

    @Operation(summary = "更新景区")
    @PutMapping("/{areaId}")
    @RequirePermission(PermissionCode.SCENIC_WRITE)
    public ApiResponse<ScenicAreaResponse> updateArea(
            @PathVariable Long areaId,
            @Valid @RequestBody ScenicAreaRequest request) {
        return ApiResponse.ok(scenicAdminService.updateArea(areaId, request));
    }

    @Operation(summary = "删除景区")
    @DeleteMapping("/{areaId}")
    @RequirePermission(PermissionCode.SCENIC_WRITE)
    public ApiResponse<Void> deleteArea(@PathVariable Long areaId) {
        scenicAdminService.deleteArea(areaId);
        return ApiResponse.ok();
    }

    @Operation(summary = "查询景区地图草稿")
    @GetMapping("/{areaId}/map-drafts")
    public ApiResponse<List<ScenicMapDraftResponse>> listMapDrafts(@PathVariable Long areaId) {
        return ApiResponse.ok(scenicAdminService.listMapDraftsWithDefaultFallback(areaId));
    }

    @Operation(summary = "创建地图草稿")
    @PostMapping("/{areaId}/map-drafts")
    @RequirePermission(PermissionCode.SCENIC_WRITE)
    public ApiResponse<ScenicMapDraftResponse> createMapDraft(
            @PathVariable Long areaId,
            @RequestBody ScenicMapDraftRequest request) {
        return ApiResponse.ok(scenicAdminService.createMapDraftWithDefaultFallback(areaId, request));
    }
}
