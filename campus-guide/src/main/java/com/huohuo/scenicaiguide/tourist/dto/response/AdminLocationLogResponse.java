package com.huohuo.scenicaiguide.tourist.dto.response;

import com.huohuo.scenicaiguide.tourist.entity.TouristLocationLog;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class AdminLocationLogResponse {

    private Long id;

    private Long sessionId;

    private BigDecimal longitude;

    private BigDecimal latitude;

    private Integer accuracyMeter;

    private BigDecimal speedMeterSecond;

    private LocalDateTime reportedAt;

    private Integer accepted;

    private String rejectReason;

    private LocalDateTime createdAt;

    public static AdminLocationLogResponse from(TouristLocationLog entity) {
        AdminLocationLogResponse response = new AdminLocationLogResponse();
        response.setId(entity.getId());
        response.setSessionId(entity.getSessionId());
        response.setLongitude(entity.getLongitude());
        response.setLatitude(entity.getLatitude());
        response.setAccuracyMeter(entity.getAccuracyMeter());
        response.setSpeedMeterSecond(entity.getSpeedMeterSecond());
        response.setReportedAt(entity.getReportedAt());
        response.setAccepted(entity.getAccepted());
        response.setRejectReason(entity.getRejectReason());
        response.setCreatedAt(entity.getCreatedAt());
        return response;
    }
}
