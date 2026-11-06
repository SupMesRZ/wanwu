package com.huohuo.scenicaiguide.scenic.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ScenicAreaRequest {

    @NotBlank(message = "景区名称不能为空")
    @Size(max = 100, message = "景区名称不能超过100个字符")
    private String name;

    @Size(max = 1000, message = "景区描述不能超过1000个字符")
    private String description;

    private BigDecimal centerLongitude;

    private BigDecimal centerLatitude;

    @Min(value = 1, message = "缩放级别不能小于1")
    @Max(value = 22, message = "缩放级别不能大于22")
    private Integer defaultZoom;

    private Integer status;
}
