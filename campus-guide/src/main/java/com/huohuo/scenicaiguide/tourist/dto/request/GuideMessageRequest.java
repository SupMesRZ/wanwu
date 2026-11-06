package com.huohuo.scenicaiguide.tourist.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class GuideMessageRequest {

    @NotBlank(message = "消息内容不能为空")
    @Size(max = 1000, message = "消息内容不能超过1000个字符")
    private String content;

    @Size(max = 64, message = "意图长度不能超过64个字符")
    private String intent;

    private Boolean speechEnabled = true;
}
