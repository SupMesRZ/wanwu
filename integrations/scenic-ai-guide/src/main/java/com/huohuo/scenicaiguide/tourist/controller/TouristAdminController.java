package com.huohuo.scenicaiguide.tourist.controller;

import com.huohuo.scenicaiguide.common.api.ApiResponse;
import com.huohuo.scenicaiguide.common.api.PageResponse;
import com.huohuo.scenicaiguide.system.security.PermissionCode;
import com.huohuo.scenicaiguide.system.security.RequireAdmin;
import com.huohuo.scenicaiguide.system.security.RequirePermission;
import com.huohuo.scenicaiguide.tourist.dto.response.AdminFenceLogResponse;
import com.huohuo.scenicaiguide.tourist.dto.response.AdminGuideSessionResponse;
import com.huohuo.scenicaiguide.tourist.dto.response.AdminLocationLogResponse;
import com.huohuo.scenicaiguide.tourist.service.TouristAdminService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/tourist")
@RequireAdmin
@RequirePermission(PermissionCode.TOURIST_READ)
public class TouristAdminController {

    private final TouristAdminService touristAdminService;

    public TouristAdminController(TouristAdminService touristAdminService) {
        this.touristAdminService = touristAdminService;
    }

    @GetMapping("/sessions")
    public ApiResponse<PageResponse<AdminGuideSessionResponse>> listSessions(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.ok(touristAdminService.listSessions(keyword, status, page, pageSize));
    }

    @GetMapping("/fence-logs")
    public ApiResponse<PageResponse<AdminFenceLogResponse>> listFenceLogs(
            @RequestParam(required = false) Long sessionId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String broadcastStatus,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.ok(touristAdminService.listFenceLogs(sessionId, keyword, broadcastStatus, page, pageSize));
    }

    @GetMapping("/location-logs")
    public ApiResponse<PageResponse<AdminLocationLogResponse>> listLocationLogs(
            @RequestParam(required = false) Long sessionId,
            @RequestParam(required = false) Integer accepted,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.ok(touristAdminService.listLocationLogs(sessionId, accepted, page, pageSize));
    }
}
