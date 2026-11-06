package com.huohuo.scenicaiguide.qa.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FaqRequest {

    @NotNull
    private Long scenicAreaId;

    @NotBlank
    private String question;

    @NotBlank
    private String answer;

    private String category;

    private String tags;

    private Integer status;
}
