package com.huohuo.scenicaiguide.qa.dto.response;

import com.huohuo.scenicaiguide.qa.entity.QaGuideScript;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class GuideScriptResponse {

    private Long id;
    private Long scenicAreaId;
    private String title;
    private String content;
    private String language;
    private Integer priority;
    private Long audioAssetId;
    private Long videoAssetId;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static GuideScriptResponse from(QaGuideScript entity) {
        GuideScriptResponse response = new GuideScriptResponse();
        response.setId(entity.getId());
        response.setScenicAreaId(entity.getScenicAreaId());
        response.setTitle(entity.getTitle());
        response.setContent(entity.getContent());
        response.setLanguage(entity.getLanguage());
        response.setPriority(entity.getPriority());
        response.setAudioAssetId(entity.getAudioAssetId());
        response.setVideoAssetId(entity.getVideoAssetId());
        response.setStatus(entity.getStatus());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }
}
