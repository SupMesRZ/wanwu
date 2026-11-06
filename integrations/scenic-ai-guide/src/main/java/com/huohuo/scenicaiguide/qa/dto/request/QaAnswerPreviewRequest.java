package com.huohuo.scenicaiguide.qa.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class QaAnswerPreviewRequest {

    @NotNull(message = "景区ID不能为空")
    private Long scenicAreaId;

    @NotBlank(message = "问题不能为空")
    private String question;

    private String currentSpotName;
}
