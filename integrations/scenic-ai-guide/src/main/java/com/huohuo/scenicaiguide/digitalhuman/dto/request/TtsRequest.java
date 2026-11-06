package com.huohuo.scenicaiguide.digitalhuman.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TtsRequest {

    @NotBlank(message = "合成文本不能为空")
    @Size(max = 1000, message = "合成文本不能超过1000个字符")
    private String text;
}
