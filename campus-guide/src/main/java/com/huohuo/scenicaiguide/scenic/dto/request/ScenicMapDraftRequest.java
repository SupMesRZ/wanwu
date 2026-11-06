package com.huohuo.scenicaiguide.scenic.dto.request;

import lombok.Data;

@Data
public class ScenicMapDraftRequest {

    private String name;

    private Long copyFromDraftId;
}
