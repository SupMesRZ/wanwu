package com.huohuo.scenicaiguide.qa.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class KnowledgeDocRequest {

    @NotNull
    private Long scenicAreaId;

    @NotBlank
    private String title;

    private String fileName;

    private String fileUrl;

    private String docType;

    private String summary;

    private Integer status;
}
