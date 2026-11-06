package com.huohuo.scenicaiguide.tourist.controller;

import com.huohuo.scenicaiguide.common.api.ApiResponse;
import com.huohuo.scenicaiguide.system.service.AuthService;
import com.huohuo.scenicaiguide.tourist.dto.response.SessionInitResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 游客会话接口
 */
@RestController
@RequestMapping("/api/v1/tourist/session")
public class TouristSessionController {

    private final AuthService authService;
    @Autowired
    public TouristSessionController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 匿名身份初始化
     *
     * @param deviceId 设备ID（首次为空，后端生成后前端存 localStorage 再传）
     * @return 初始化响应
     */
    @GetMapping("/init")
    public ApiResponse<SessionInitResponse> init(
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId) {
        return ApiResponse.ok(authService.touristAnonymousInit(deviceId));
    }
}
