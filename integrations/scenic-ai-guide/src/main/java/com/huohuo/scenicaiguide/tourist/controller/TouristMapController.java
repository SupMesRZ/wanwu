package com.huohuo.scenicaiguide.tourist.controller;

import com.huohuo.scenicaiguide.common.api.ApiResponse;
import com.huohuo.scenicaiguide.scenic.dto.response.MapWorkbenchResponse;
import com.huohuo.scenicaiguide.scenic.service.ScenicAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "游客地图", description = "游客端读取线上地图")
@RestController
@RequestMapping("/api/v1/tourist/scenic-areas")
public class TouristMapController {

    private final ScenicAdminService scenicAdminService;

    @Autowired
    public TouristMapController(ScenicAdminService scenicAdminService) {
        this.scenicAdminService = scenicAdminService;
    }

    @Operation(summary = "读取景区线上地图")
    @GetMapping("/{areaId}/map")
    public ApiResponse<MapWorkbenchResponse> getOnlineMap(@PathVariable Long areaId) {
        return ApiResponse.ok(scenicAdminService.getOnlineTouristMap(areaId));
    }
}
