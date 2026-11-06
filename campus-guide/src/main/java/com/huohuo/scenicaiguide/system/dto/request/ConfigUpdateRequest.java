package com.huohuo.scenicaiguide.system.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.Map;

/**
 * 配置更新请求
 */
@Data
@Schema(description = "配置更新请求")
public class ConfigUpdateRequest {

    @Schema(description = "配置值", example = "2000")
    @Size(max = 500, message = "配置值长度不超过 500")
    private String value;

    @Schema(description = "配置说明（可选，不传则不修改）", example = "围栏默认半径（米）")
    @Size(max = 255, message = "说明长度不超过 255")
    private String description;

    /**
     * 批量更新用的键值对
     */
    @Data
    @Schema(description = "批量配置更新请求")
    public static class Batch {
        @Schema(description = "配置键值对，key 为当前 domain 下的配置键，value 为配置值", example = "{\"rag-top-k\": \"6\"}")
        @NotEmpty(message = "配置项不能为空")
        @Valid
        private Map<
            @NotBlank(message = "配置键不能为空")
            @Size(max = 100, message = "配置键长度不超过 100")
            String,
            @Size(max = 500, message = "配置值长度不超过 500")
            String> configs;
    }
}
