package com.huohuo.scenicaiguide.scenic.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ScenicSpotRequest {

    @NotBlank(message = "景点名称不能为空")
    @Size(max = 100, message = "景点名称不能超过100个字符")
    private String name;

    @Size(max = 1000, message = "景点描述不能超过1000个字符")
    private String description;

    private BigDecimal labelLongitude;

    private BigDecimal labelLatitude;

    private String fencePolygon;

    private String fenceType;

    private Integer radius;

    private Integer status;
}
