package com.huohuo.scenicaiguide.scenic.controller;

import com.huohuo.scenicaiguide.common.api.ApiResponse;
import com.huohuo.scenicaiguide.scenic.dto.request.ScenicFacilityRequest;
import com.huohuo.scenicaiguide.scenic.dto.response.ScenicFacilityResponse;
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

@Tag(name = "服务点管理", description = "景区服务点基础管理")
@RestController
@RequestMapping("/api/v1/admin")
@RequireAdmin
@RequirePermission(PermissionCode.SCENIC_READ)
public class ScenicFacilityController {

    private final ScenicAdminService scenicAdminService;

    @Autowired
    public ScenicFacilityController(ScenicAdminService scenicAdminService) {
        this.scenicAdminService = scenicAdminService;
    }

    @Operation(summary = "查询服务点列表")
    @GetMapping("/scenic-areas/{areaId}/facilities")
    public ApiResponse<List<ScenicFacilityResponse>> listFacilities(@PathVariable Long areaId) {
        return ApiResponse.ok(scenicAdminService.listFacilities(areaId));
    }

    @Operation(summary = "创建服务点")
    @PostMapping("/scenic-areas/{areaId}/facilities")
    @RequirePermission(PermissionCode.SCENIC_WRITE)
    public ApiResponse<ScenicFacilityResponse> createFacility(
            @PathVariable Long areaId,
            @Valid @RequestBody ScenicFacilityRequest request) {
        return ApiResponse.ok(scenicAdminService.createFacility(areaId, request));
    }

    @Operation(summary = "更新服务点")
    @PutMapping("/facilities/{facilityId}")
    @RequirePermission(PermissionCode.SCENIC_WRITE)
    public ApiResponse<ScenicFacilityResponse> updateFacility(
            @PathVariable Long facilityId,
            @Valid @RequestBody ScenicFacilityRequest request) {
        return ApiResponse.ok(scenicAdminService.updateFacility(facilityId, request));
    }

    @Operation(summary = "删除服务点并级联删除关联路线")
    @DeleteMapping("/facilities/{facilityId}")
    @RequirePermission(PermissionCode.SCENIC_WRITE)
    public ApiResponse<Void> deleteFacility(@PathVariable Long facilityId) {
        scenicAdminService.deleteFacility(facilityId);
        return ApiResponse.ok();
    }
}
