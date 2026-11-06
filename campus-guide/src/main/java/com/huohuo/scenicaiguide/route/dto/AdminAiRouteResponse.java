package com.huohuo.scenicaiguide.route.dto;

import com.huohuo.scenicaiguide.route.entity.RouteAiRoute;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminAiRouteResponse {

    private Long id;

    private Long sessionId;

    private Long scenicAreaId;

    private Long sourceDraftId;

    private String name;

    private String routeType;

    private String recommendationReason;

    private Integer estimatedDurationMin;

    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public static AdminAiRouteResponse from(RouteAiRoute entity) {
        AdminAiRouteResponse response = new AdminAiRouteResponse();
        response.setId(entity.getId());
        response.setSessionId(entity.getSessionId());
        response.setScenicAreaId(entity.getScenicAreaId());
        response.setSourceDraftId(entity.getSourceDraftId());
        response.setName(entity.getName());
        response.setRouteType(entity.getRouteType());
        response.setRecommendationReason(entity.getRecommendationReason());
        response.setEstimatedDurationMin(entity.getEstimatedDurationMin());
        response.setStatus(entity.getStatus());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }
}
