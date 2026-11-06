package com.huohuo.scenicaiguide.qa.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class GuideScriptRequest {

    @NotNull
    private Long scenicAreaId;

    @NotBlank
    private String title;

    @NotBlank
    private String content;

    private String language;

    private Integer priority;

    private Long audioAssetId;

    private Long videoAssetId;

    private Integer status;
}
