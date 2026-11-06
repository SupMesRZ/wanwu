package com.huohuo.scenicaiguide.digitalhuman.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.huohuo.scenicaiguide.common.enums.ErrorCode;
import com.huohuo.scenicaiguide.common.exception.BusinessException;
import com.huohuo.scenicaiguide.digitalhuman.dto.request.DhProfileRequest;
import com.huohuo.scenicaiguide.digitalhuman.dto.response.DhProfileResponse;
import com.huohuo.scenicaiguide.digitalhuman.dto.response.DhTtsOptionResponse;
import com.huohuo.scenicaiguide.digitalhuman.entity.DhProfile;
import com.huohuo.scenicaiguide.digitalhuman.mapper.DhProfileMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class DigitalHumanService {

    private static final Long DEFAULT_SCENIC_AREA_ID = 1L;
    private static final Map<String, String> ALIYUN_VOICES = Map.of(
        "siyue", "思悦 siyue",
        "xiaoyun", "小云 xiaoyun",
        "xiaogang", "小刚 xiaogang",
        "aixia", "艾夏 aixia",
        "aishan", "艾山 aishan"
    );
    private static final List<String> VOICE_ORDER = List.of("siyue", "xiaoyun", "xiaogang", "aixia", "aishan");
    private static final List<String> OUTPUT_FORMATS = List.of("wav", "mp3", "pcm");
    private static final List<Integer> SAMPLE_RATES = List.of(8000, 16000, 24000, 48000);

    private final DhProfileMapper profileMapper;

    public DigitalHumanService(DhProfileMapper profileMapper) {
        this.profileMapper = profileMapper;
    }

    public DhProfile getOrCreateDefaultProfile() {
        DhProfile existing = profileMapper.selectList(new QueryWrapper<DhProfile>()
                .eq("scenic_area_id", DEFAULT_SCENIC_AREA_ID)
                .eq("status", 1)
                .orderByAsc("id")
                .last("LIMIT 1"))
            .stream()
            .findFirst()
            .orElse(null);
        if (existing != null) {
            ensureDefaultHaruAssets(existing);
            return existing;
        }

        DhProfile profile = new DhProfile();
        profile.setScenicAreaId(DEFAULT_SCENIC_AREA_ID);
        profile.setName("小桃导游");
        profile.setAvatarUrl("/digital-human/haru/Haru.model3.json");
        profile.setPersonaPrompt("你是桃花源景区的 AI 导游，回答必须基于景区资料，不能编造景区事实");
        profile.setWelcomeText("欢迎来到桃花源，我是你的 AI 导游。你可以直接问我景点故事、厕所餐饮位置，也可以告诉我游览时长、同行人群和兴趣偏好，我会为你安排一条适合今天的路线。");
        profile.setProvider("ALIYUN_NLS");
        profile.setVoiceCode("siyue");
        profile.setOutputFormat("wav");
        profile.setSampleRate(16000);
        profile.setSpeechRate(0);
        profile.setPitchRate(0);
        profile.setVolume(50);
        applyDefaultHaruActions(profile);
        profile.setStatus(1);
        profile.setCreatedAt(LocalDateTime.now());
        profile.setUpdatedAt(LocalDateTime.now());
        profile.setDeleted(0);
        profileMapper.insert(profile);
        return profile;
    }

    private void ensureDefaultHaruAssets(DhProfile profile) {
        boolean changed = false;
        if (profile.getAvatarUrl() == null || profile.getAvatarUrl().isBlank()) {
            profile.setAvatarUrl("/digital-human/haru/Haru.model3.json");
            changed = true;
        }
        if (profile.getActionIdleUrl() == null || profile.getActionIdleUrl().isBlank()) {
            profile.setActionIdleUrl("/digital-human/haru/motions/haru_g_idle.motion3.json");
            changed = true;
        }
        if (profile.getActionListeningUrl() == null || profile.getActionListeningUrl().isBlank()) {
            profile.setActionListeningUrl("/digital-human/haru/motions/haru_g_m15.motion3.json");
            changed = true;
        }
        if (profile.getActionThinkingUrl() == null || profile.getActionThinkingUrl().isBlank()) {
            profile.setActionThinkingUrl("/digital-human/haru/motions/haru_g_m06.motion3.json");
            changed = true;
        }
        if (profile.getActionSpeakingUrl() == null || profile.getActionSpeakingUrl().isBlank()) {
            profile.setActionSpeakingUrl("/digital-human/haru/motions/haru_g_m26.motion3.json");
            changed = true;
        }
        if (changed) {
            profile.setUpdatedAt(LocalDateTime.now());
            profileMapper.updateById(profile);
        }
    }

    private void applyDefaultHaruActions(DhProfile profile) {
        profile.setActionIdleUrl("/digital-human/haru/motions/haru_g_idle.motion3.json");
        profile.setActionListeningUrl("/digital-human/haru/motions/haru_g_m15.motion3.json");
        profile.setActionThinkingUrl("/digital-human/haru/motions/haru_g_m06.motion3.json");
        profile.setActionSpeakingUrl("/digital-human/haru/motions/haru_g_m26.motion3.json");
    }

    public DhProfileResponse getDefaultProfileResponse() {
        return DhProfileResponse.from(getOrCreateDefaultProfile());
    }

    public DhProfileResponse updateDefaultProfile(DhProfileRequest request) {
        DhProfile profile = getOrCreateDefaultProfile();
        fill(profile, request);
        profile.setUpdatedAt(LocalDateTime.now());
        profileMapper.updateById(profile);
        return DhProfileResponse.from(profile);
    }

    public DhTtsOptionResponse getTtsOptions() {
        DhTtsOptionResponse response = new DhTtsOptionResponse();
        response.setVoices(VOICE_ORDER.stream()
            .map(value -> new DhTtsOptionResponse.OptionItem(ALIYUN_VOICES.get(value), value))
            .toList());
        response.setOutputFormats(OUTPUT_FORMATS);
        response.setSampleRates(SAMPLE_RATES);
        response.setSpeechRate(new DhTtsOptionResponse.Range(-500, 500, 0));
        response.setPitchRate(new DhTtsOptionResponse.Range(-500, 500, 0));
        response.setVolume(new DhTtsOptionResponse.Range(0, 100, 50));
        return response;
    }

    private void fill(DhProfile profile, DhProfileRequest request) {
        profile.setName(request.getName().trim());
        profile.setAvatarUrl(request.getAvatarUrl());
        profile.setPersonaPrompt(request.getPersonaPrompt());
        profile.setWelcomeText(request.getWelcomeText());
        profile.setVoiceCode(validateVoice(defaultText(request.getVoiceCode(), "siyue")));
        profile.setOutputFormat(validateFormat(defaultText(request.getOutputFormat(), "wav")));
        profile.setSampleRate(validateSampleRate(request.getSampleRate() == null ? 16000 : request.getSampleRate()));
        profile.setSpeechRate(request.getSpeechRate() == null ? 0 : request.getSpeechRate());
        profile.setPitchRate(request.getPitchRate() == null ? 0 : request.getPitchRate());
        profile.setVolume(request.getVolume() == null ? 50 : request.getVolume());
        profile.setActionIdleUrl(request.getActionIdleUrl());
        profile.setActionListeningUrl(request.getActionListeningUrl());
        profile.setActionThinkingUrl(request.getActionThinkingUrl());
        profile.setActionSpeakingUrl(request.getActionSpeakingUrl());
        profile.setStatus(request.getStatus() == null ? 1 : request.getStatus());
    }

    private String validateVoice(String voice) {
        String normalized = voice.trim();
        if (!ALIYUN_VOICES.containsKey(normalized)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "不支持的阿里云音色：" + voice);
        }
        return normalized;
    }

    private String validateFormat(String format) {
        String normalized = format.trim().toLowerCase();
        if (!OUTPUT_FORMATS.contains(normalized)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "不支持的音频格式：" + format);
        }
        return normalized;
    }

    private Integer validateSampleRate(Integer sampleRate) {
        if (!SAMPLE_RATES.contains(sampleRate)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "不支持的采样率：" + sampleRate);
        }
        return sampleRate;
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
