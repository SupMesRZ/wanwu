package com.huohuo.scenicaiguide.route.controller;

import com.huohuo.scenicaiguide.common.api.ApiResponse;
import com.huohuo.scenicaiguide.common.api.PageResponse;
import com.huohuo.scenicaiguide.route.dto.AdminAiRouteResponse;
import com.huohuo.scenicaiguide.route.service.RouteAdminService;
import com.huohuo.scenicaiguide.scenic.dto.request.MapWorkbenchDraftRequest;
import com.huohuo.scenicaiguide.scenic.dto.response.MapWorkbenchResponse;
import com.huohuo.scenicaiguide.scenic.service.ScenicAdminService;
import com.huohuo.scenicaiguide.system.security.PermissionCode;
import com.huohuo.scenicaiguide.system.security.RequireAdmin;
import com.huohuo.scenicaiguide.system.security.RequirePermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/routes")
@RequireAdmin
@RequirePermission(PermissionCode.ROUTE_READ)
public class RouteAdminController {

    private final RouteAdminService routeAdminService;
    private final ScenicAdminService scenicAdminService;

    public RouteAdminController(RouteAdminService routeAdminService,
                                ScenicAdminService scenicAdminService) {
        this.routeAdminService = routeAdminService;
        this.scenicAdminService = scenicAdminService;
    }

    @GetMapping("/ai")
    public ApiResponse<PageResponse<AdminAiRouteResponse>> listAiRoutes(
            @RequestParam(required = false) Long sessionId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.ok(routeAdminService.listAiRoutes(sessionId, keyword, status, page, pageSize));
    }

    /**
     * 保存预置路线（P1-9 定档：管理入口收在 route 域）
     *
     * <p>draftId 是 scenic 域地图草稿 id——预置路线与景点、路网连线同处草稿
     * payload，随地图版本一次性原子发布，故存储不拆表、写入仍委托 scenic 草稿服务
     */
    @PutMapping("/preset/{draftId}")
    @RequirePermission(PermissionCode.ROUTE_WRITE)
    public ApiResponse<MapWorkbenchResponse> savePresetRoutes(
            @PathVariable Long draftId,
            @RequestBody List<MapWorkbenchDraftRequest.PresetRouteDraft> presetRoutes) {
        return ApiResponse.ok(scenicAdminService.savePresetRoutes(draftId, presetRoutes));
    }
}
