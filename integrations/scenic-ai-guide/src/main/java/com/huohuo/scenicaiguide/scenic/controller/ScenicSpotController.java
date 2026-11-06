package com.huohuo.scenicaiguide.scenic.controller;

import com.huohuo.scenicaiguide.common.api.ApiResponse;
import com.huohuo.scenicaiguide.scenic.dto.request.ScenicSpotRequest;
import com.huohuo.scenicaiguide.scenic.dto.response.ScenicSpotResponse;
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

@Tag(name = "景点管理", description = "景点与围栏基础管理")
@RestController
@RequestMapping("/api/v1/admin")
@RequireAdmin
@RequirePermission(PermissionCode.SCENIC_READ)
public class ScenicSpotController {

    private final ScenicAdminService scenicAdminService;

    @Autowired
    public ScenicSpotController(ScenicAdminService scenicAdminService) {
        this.scenicAdminService = scenicAdminService;
    }

    @Operation(summary = "查询景点列表")
    @GetMapping("/scenic-areas/{areaId}/spots")
    public ApiResponse<List<ScenicSpotResponse>> listSpots(@PathVariable Long areaId) {
        return ApiResponse.ok(scenicAdminService.listSpots(areaId));
    }

    @Operation(summary = "创建景点")
    @PostMapping("/scenic-areas/{areaId}/spots")
    @RequirePermission(PermissionCode.SCENIC_WRITE)
    public ApiResponse<ScenicSpotResponse> createSpot(
            @PathVariable Long areaId,
            @Valid @RequestBody ScenicSpotRequest request) {
        return ApiResponse.ok(scenicAdminService.createSpot(areaId, request));
    }

    @Operation(summary = "更新景点")
    @PutMapping("/spots/{spotId}")
    @RequirePermission(PermissionCode.SCENIC_WRITE)
    public ApiResponse<ScenicSpotResponse> updateSpot(
            @PathVariable Long spotId,
            @Valid @RequestBody ScenicSpotRequest request) {
        return ApiResponse.ok(scenicAdminService.updateSpot(spotId, request));
    }

    @Operation(summary = "删除景点并级联删除关联路线")
    @DeleteMapping("/spots/{spotId}")
    @RequirePermission(PermissionCode.SCENIC_WRITE)
    public ApiResponse<Void> deleteSpot(@PathVariable Long spotId) {
        scenicAdminService.deleteSpot(spotId);
        return ApiResponse.ok();
    }
}
