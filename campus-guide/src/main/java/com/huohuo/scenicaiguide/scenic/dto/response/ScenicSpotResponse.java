package com.huohuo.scenicaiguide.scenic.dto.response;

import com.huohuo.scenicaiguide.scenic.entity.ScenicSpot;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ScenicSpotResponse {

    private Long id;
    private Long scenicAreaId;
    private String name;
    private String description;
    private BigDecimal labelLongitude;
    private BigDecimal labelLatitude;
    private List<BigDecimal> labelPoint;
    private String fencePolygon;
    private List<List<BigDecimal>> polygon;
    private String fenceType;
    private Integer radius;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ScenicSpotResponse from(ScenicSpot entity) {
        ScenicSpotResponse response = new ScenicSpotResponse();
        response.setId(entity.getId());
        response.setScenicAreaId(entity.getScenicAreaId());
        response.setName(entity.getName());
        response.setDescription(entity.getDescription());
        response.setLabelLongitude(entity.getLabelLongitude());
        response.setLabelLatitude(entity.getLabelLatitude());
        if (entity.getLabelLatitude() != null && entity.getLabelLongitude() != null) {
            response.setLabelPoint(List.of(entity.getLabelLatitude(), entity.getLabelLongitude()));
        }
        response.setFencePolygon(entity.getFencePolygon());
        response.setFenceType(entity.getFenceType());
        response.setRadius(entity.getFenceRadiusMeter());
        response.setStatus(entity.getStatus());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }
}
