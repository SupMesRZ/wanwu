package com.huohuo.scenicaiguide.scenic.dto.response;

import com.huohuo.scenicaiguide.scenic.entity.ScenicArea;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ScenicAreaResponse {

    private Long id;
    private String name;
    private String description;
    private BigDecimal centerLongitude;
    private BigDecimal centerLatitude;
    private Integer defaultZoom;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ScenicAreaResponse from(ScenicArea entity) {
        ScenicAreaResponse response = new ScenicAreaResponse();
        response.setId(entity.getId());
        response.setName(entity.getName());
        response.setDescription(entity.getDescription());
        response.setCenterLongitude(entity.getCenterLongitude());
        response.setCenterLatitude(entity.getCenterLatitude());
        response.setDefaultZoom(entity.getDefaultZoom());
        response.setStatus(entity.getStatus());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }
}
