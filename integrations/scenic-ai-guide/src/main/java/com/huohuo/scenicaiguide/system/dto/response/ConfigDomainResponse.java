package com.huohuo.scenicaiguide.system.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 配置域响应
 */
@Data
@AllArgsConstructor
@Schema(description = "配置域信息")
public class ConfigDomainResponse {

    @Schema(description = "配置域标识", example = "qa")
    private String domain;

    @Schema(description = "配置域名称", example = "智能问答")
    private String name;
}
