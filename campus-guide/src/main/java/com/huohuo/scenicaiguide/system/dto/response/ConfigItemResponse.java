package com.huohuo.scenicaiguide.system.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 配置项响应
 */
@Data
@Schema(description = "配置项信息")
public class ConfigItemResponse {

    @Schema(description = "配置键", example = "fence-radius-meters")
    private String configKey;

    @Schema(description = "配置值", example = "2000")
    private String configValue;

    @Schema(description = "配置说明", example = "围栏默认半径（米）")
    private String description;

    @Schema(description = "配置分类", example = "围栏")
    private String category;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;

    @Schema(description = "更新人")
    private Long updatedBy;
}
