package com.huohuo.scenicaiguide.digitalhuman.controller;

import com.huohuo.scenicaiguide.common.api.ApiResponse;
import com.huohuo.scenicaiguide.common.api.PageResponse;
import com.huohuo.scenicaiguide.digitalhuman.dto.request.DhProfileRequest;
import com.huohuo.scenicaiguide.digitalhuman.dto.request.TtsRequest;
import com.huohuo.scenicaiguide.digitalhuman.dto.response.DhProfileResponse;
import com.huohuo.scenicaiguide.digitalhuman.dto.response.DhTtsOptionResponse;
import com.huohuo.scenicaiguide.digitalhuman.dto.response.TtsCacheResponse;
import com.huohuo.scenicaiguide.digitalhuman.dto.response.TtsResponse;
import com.huohuo.scenicaiguide.digitalhuman.service.DigitalHumanService;
import com.huohuo.scenicaiguide.digitalhuman.service.TtsService;
import com.huohuo.scenicaiguide.system.security.PermissionCode;
import com.huohuo.scenicaiguide.system.security.RequireAdmin;
import com.huohuo.scenicaiguide.system.security.RequirePermission;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/digital-human")
@RequireAdmin
@RequirePermission(PermissionCode.DH_READ)
public class DigitalHumanController {

    private final DigitalHumanService digitalHumanService;
    private final TtsService ttsService;

    public DigitalHumanController(DigitalHumanService digitalHumanService, TtsService ttsService) {
        this.digitalHumanService = digitalHumanService;
        this.ttsService = ttsService;
    }

    @GetMapping
    public ApiResponse<DhProfileResponse> getProfile() {
        return ApiResponse.ok(digitalHumanService.getDefaultProfileResponse());
    }

    @GetMapping("/options")
    public ApiResponse<DhTtsOptionResponse> getOptions() {
        return ApiResponse.ok(digitalHumanService.getTtsOptions());
    }

    @PutMapping
    @RequirePermission(PermissionCode.DH_WRITE)
    public ApiResponse<DhProfileResponse> updateProfile(@Valid @RequestBody DhProfileRequest request) {
        return ApiResponse.ok(digitalHumanService.updateDefaultProfile(request));
    }

    // 试听会真实调用 TTS 并消耗额度，按写权限管控
    @PostMapping("/tts")
    @RequirePermission(PermissionCode.DH_WRITE)
    public ApiResponse<TtsResponse> synthesize(@Valid @RequestBody TtsRequest request) {
        return ApiResponse.ok(ttsService.synthesize(1L, request.getText()));
    }

    @GetMapping("/tts-cache")
    public ApiResponse<PageResponse<TtsCacheResponse>> listTtsCaches(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String voiceCode,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.ok(ttsService.listCaches(keyword, voiceCode, page, pageSize));
    }

    @GetMapping("/tts-cache/{id}/audio")
    public ApiResponse<TtsResponse> getCachedAudio(@PathVariable Long id) {
        return ApiResponse.ok(ttsService.getCachedAudio(id));
    }

    @DeleteMapping("/tts-cache/{id}")
    @RequirePermission(PermissionCode.DH_WRITE)
    public ApiResponse<Void> deleteTtsCache(@PathVariable Long id) {
        ttsService.deleteCache(id);
        return ApiResponse.ok();
    }
}
