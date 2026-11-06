package com.huohuo.scenicaiguide.scenic.dto.response;

import com.huohuo.scenicaiguide.scenic.entity.ScenicFacility;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ScenicFacilityResponse {

    private Long id;
    private Long scenicAreaId;
    private String name;
    private String facilityType;
    private String description;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private List<BigDecimal> point;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ScenicFacilityResponse from(ScenicFacility entity) {
        ScenicFacilityResponse response = new ScenicFacilityResponse();
        response.setId(entity.getId());
        response.setScenicAreaId(entity.getScenicAreaId());
        response.setName(entity.getName());
        response.setFacilityType(entity.getFacilityType());
        response.setDescription(entity.getDescription());
        response.setLongitude(entity.getLongitude());
        response.setLatitude(entity.getLatitude());
        if (entity.getLatitude() != null && entity.getLongitude() != null) {
            response.setPoint(List.of(entity.getLatitude(), entity.getLongitude()));
        }
        response.setStatus(entity.getStatus());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }
}
