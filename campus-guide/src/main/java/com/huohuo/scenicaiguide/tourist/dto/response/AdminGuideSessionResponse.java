package com.huohuo.scenicaiguide.tourist.dto.response;

import com.huohuo.scenicaiguide.tourist.entity.TouristGuideSession;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminGuideSessionResponse {

    private Long id;

    private String sessionNo;

    private String visitorSubject;

    private Long scenicAreaId;

    private String status;

    private String currentSpotId;

    private Long currentRouteId;

    private LocalDateTime lastLocationAt;

    private LocalDateTime startedAt;

    private LocalDateTime endedAt;

    private LocalDateTime updatedAt;

    public static AdminGuideSessionResponse from(TouristGuideSession entity) {
        AdminGuideSessionResponse response = new AdminGuideSessionResponse();
        response.setId(entity.getId());
        response.setSessionNo(entity.getSessionNo());
        response.setVisitorSubject(entity.getVisitorSubject());
        response.setScenicAreaId(entity.getScenicAreaId());
        response.setStatus(entity.getStatus());
        response.setCurrentSpotId(entity.getCurrentSpotId());
        response.setCurrentRouteId(entity.getCurrentRouteId());
        response.setLastLocationAt(entity.getLastLocationAt());
        response.setStartedAt(entity.getStartedAt());
        response.setEndedAt(entity.getEndedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }
}
