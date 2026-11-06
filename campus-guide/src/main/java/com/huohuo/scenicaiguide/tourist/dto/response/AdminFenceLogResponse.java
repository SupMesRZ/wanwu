package com.huohuo.scenicaiguide.tourist.dto.response;

import com.huohuo.scenicaiguide.tourist.entity.TouristFenceLog;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class AdminFenceLogResponse {

    private Long id;

    private Long sessionId;

    private String spotId;

    private String spotName;

    private String triggerState;

    private String triggerType;

    private BigDecimal longitude;

    private BigDecimal latitude;

    private Integer accuracyMeter;

    private String detail;

    private String broadcastStatus;

    private LocalDateTime reportedAt;

    private LocalDateTime createdAt;

    public static AdminFenceLogResponse from(TouristFenceLog entity) {
        AdminFenceLogResponse response = new AdminFenceLogResponse();
        response.setId(entity.getId());
        response.setSessionId(entity.getSessionId());
        response.setSpotId(entity.getSpotId());
        response.setSpotName(entity.getSpotName());
        response.setTriggerState(entity.getTriggerState());
        response.setTriggerType(entity.getTriggerType());
        response.setLongitude(entity.getLongitude());
        response.setLatitude(entity.getLatitude());
        response.setAccuracyMeter(entity.getAccuracyMeter());
        response.setDetail(entity.getDetail());
        response.setBroadcastStatus(entity.getBroadcastStatus());
        response.setReportedAt(entity.getReportedAt());
        response.setCreatedAt(entity.getCreatedAt());
        return response;
    }
}
