package com.huohuo.scenicaiguide.digitalhuman.dto.response;

import com.huohuo.scenicaiguide.digitalhuman.entity.DhProfile;
import lombok.Data;

@Data
public class DhProfileResponse {

    private Long id;

    private Long scenicAreaId;

    private String name;

    private String avatarUrl;

    private String personaPrompt;

    private String welcomeText;

    private String provider;

    private String voiceCode;

    private String outputFormat;

    private Integer sampleRate;

    private Integer speechRate;

    private Integer pitchRate;

    private Integer volume;

    private String actionIdleUrl;

    private String actionListeningUrl;

    private String actionThinkingUrl;

    private String actionSpeakingUrl;

    private Integer status;

    public static DhProfileResponse from(DhProfile entity) {
        DhProfileResponse response = new DhProfileResponse();
        response.setId(entity.getId());
        response.setScenicAreaId(entity.getScenicAreaId());
        response.setName(entity.getName());
        response.setAvatarUrl(entity.getAvatarUrl());
        response.setPersonaPrompt(entity.getPersonaPrompt());
        response.setWelcomeText(entity.getWelcomeText());
        response.setProvider(entity.getProvider());
        response.setVoiceCode(entity.getVoiceCode());
        response.setOutputFormat(entity.getOutputFormat());
        response.setSampleRate(entity.getSampleRate());
        response.setSpeechRate(entity.getSpeechRate());
        response.setPitchRate(entity.getPitchRate());
        response.setVolume(entity.getVolume());
        response.setActionIdleUrl(entity.getActionIdleUrl());
        response.setActionListeningUrl(entity.getActionListeningUrl());
        response.setActionThinkingUrl(entity.getActionThinkingUrl());
        response.setActionSpeakingUrl(entity.getActionSpeakingUrl());
        response.setStatus(entity.getStatus());
        return response;
    }
}
