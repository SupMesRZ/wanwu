package com.huohuo.scenicaiguide.scenic.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ScenicFacilityRequest {

    @NotBlank(message = "服务点名称不能为空")
    @Size(max = 100, message = "服务点名称不能超过100个字符")
    private String name;

    @NotBlank(message = "服务点类型不能为空")
    @Size(max = 32, message = "服务点类型不能超过32个字符")
    private String facilityType;

    @Size(max = 1000, message = "服务点描述不能超过1000个字符")
    private String description;

    @NotNull(message = "经度不能为空")
    private BigDecimal longitude;

    @NotNull(message = "纬度不能为空")
    private BigDecimal latitude;

    private Integer status;
}
