package com.huohuo.scenicaiguide.system.controller;

import com.huohuo.scenicaiguide.common.api.ApiResponse;
import com.huohuo.scenicaiguide.system.dto.request.AdminLoginRequest;
import com.huohuo.scenicaiguide.system.dto.request.SmsSendRequest;
import com.huohuo.scenicaiguide.system.dto.request.SmsVerifyRequest;
import com.huohuo.scenicaiguide.system.dto.request.TouristLoginRequest;
import com.huohuo.scenicaiguide.system.dto.response.AdminLoginResponse;
import com.huohuo.scenicaiguide.system.dto.response.TokenResponse;
import com.huohuo.scenicaiguide.system.dto.response.SmsSendResponse;
import com.huohuo.scenicaiguide.system.security.AuthPrincipal;
import com.huohuo.scenicaiguide.system.security.OperationLog;
import com.huohuo.scenicaiguide.system.security.RequireAuth;
import com.huohuo.scenicaiguide.system.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * 认证接口
 */
@Tag(name = "认证接口", description = "管理员登录、用户登录、Token 刷新、登出")
@RestController
@RequestMapping("/api/v1/system/auth")
public class AuthController {

    private final AuthService authService;

    @Autowired
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 管理员账号密码登录
     */
    @Operation(summary = "管理员账号密码登录", description = "Root 强制短信验证，普通管理员检查设备信任")
    @PostMapping("/admin/login")
    @OperationLog(module = "认证", action = "管理员登录")
    public ApiResponse<AdminLoginResponse> adminLogin(
            @Valid @RequestBody AdminLoginRequest request,
            HttpServletRequest httpRequest) {
        String ip = getClientIp(httpRequest);
        String ua = httpRequest.getHeader("User-Agent");
        return ApiResponse.ok(authService.adminLogin(request, ip, ua));
    }

    /**
     * 管理员短信验证码验证
     */
    @Operation(summary = "管理员短信验证", description = "验证短信验证码后发放正式 Token")
    @PostMapping("/admin/sms-verify")
    @OperationLog(module = "认证", action = "管理员短信验证")
    public ApiResponse<TokenResponse> adminSmsVerify(
            @RequestHeader("X-Temp-Token") String tempToken,
            @Valid @RequestBody SmsVerifyRequest request,
            HttpServletRequest httpRequest) {
        String ip = getClientIp(httpRequest);
        String ua = httpRequest.getHeader("User-Agent");
        return ApiResponse.ok(authService.adminSmsVerify(tempToken, request.getSmsCode(), ip, ua));
    }

    /**
     * 发送短信验证码
     */
    @Operation(summary = "发送短信验证码", description = "向指定手机号发送 6 位验证码，含频率限制")
    @PostMapping("/sms/send")
    public ApiResponse<SmsSendResponse> sendSmsCode(@Valid @RequestBody SmsSendRequest request) {
        return ApiResponse.ok(new SmsSendResponse(authService.sendSmsCode(request.getPhone())));
    }

    /**
     * 游客手机号验证码登录
     */
    @Operation(summary = "用户手机号登录", description = "首次登录自动注册，发放 accessToken + refreshToken")
    @PostMapping("/sms/login")
    public ApiResponse<TokenResponse> touristLogin(
            @Valid @RequestBody TouristLoginRequest request) {
        return ApiResponse.ok(authService.touristLogin(request));
    }

    /**
     * 刷新 Token
     */
    @Operation(summary = "刷新 Token", description = "用 refreshToken 换取新的 accessToken 和 refreshToken")
    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refreshToken(
            @RequestHeader("Authorization") String authHeader) {
        String refreshToken = authHeader.replace("Bearer ", "");
        return ApiResponse.ok(authService.refreshToken(refreshToken));
    }

    /**
     * 登出
     */
    @Operation(summary = "登出", description = "注销当前会话，删除 Redis Session")
    @RequireAuth
    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthPrincipal principal) {
            authService.logout(principal.subject());
        }
        return ApiResponse.ok();
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) {
            ip = request.getRemoteAddr();
        }
        return ip.split(",")[0].trim();
    }
}
