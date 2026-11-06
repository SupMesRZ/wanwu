package com.huohuo.scenicaiguide.scenic.dto.response;

import com.huohuo.scenicaiguide.scenic.entity.ScenicMapDraft;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ScenicMapDraftResponse {

    private Long id;
    private Long scenicAreaId;
    private String name;
    private String status;
    private LocalDateTime lastSavedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ScenicMapDraftResponse from(ScenicMapDraft entity) {
        ScenicMapDraftResponse response = new ScenicMapDraftResponse();
        response.setId(entity.getId());
        response.setScenicAreaId(entity.getScenicAreaId());
        response.setName(entity.getName());
        response.setStatus(entity.getStatus());
        response.setLastSavedAt(entity.getLastSavedAt());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }
}
