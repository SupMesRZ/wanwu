package com.huohuo.scenicaiguide.digitalhuman.dto.response;

import com.huohuo.scenicaiguide.digitalhuman.entity.DhTtsCache;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TtsCacheResponse {

    private Long id;

    private Long scenicAreaId;

    private String textPreview;

    private String voiceCode;

    private String outputFormat;

    private Integer sampleRate;

    private String audioUrl;

    private Integer audioBase64Size;

    private Integer hitCount;

    private LocalDateTime expireAt;

    private boolean expired;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public static TtsCacheResponse from(DhTtsCache entity) {
        TtsCacheResponse response = new TtsCacheResponse();
        response.setId(entity.getId());
        response.setScenicAreaId(entity.getScenicAreaId());
        response.setTextPreview(entity.getTextPreview());
        response.setVoiceCode(entity.getVoiceCode());
        response.setOutputFormat(entity.getOutputFormat());
        response.setSampleRate(entity.getSampleRate());
        response.setAudioUrl(entity.getAudioUrl());
        response.setAudioBase64Size(entity.getAudioBase64() == null ? 0 : entity.getAudioBase64().length());
        response.setHitCount(entity.getHitCount());
        response.setExpireAt(entity.getExpireAt());
        response.setExpired(entity.getExpireAt() != null && entity.getExpireAt().isBefore(LocalDateTime.now()));
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }
}
