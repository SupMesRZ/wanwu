package com.huohuo.scenicaiguide.digitalhuman.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DhProfileRequest {

    @NotBlank(message = "数字人名称不能为空")
    @Size(max = 80, message = "数字人名称不能超过80个字符")
    private String name;

    @Size(max = 500, message = "头像地址不能超过500个字符")
    private String avatarUrl;

    @Size(max = 2000, message = "人设提示词不能超过2000个字符")
    private String personaPrompt;

    @Size(max = 1000, message = "欢迎语不能超过1000个字符")
    private String welcomeText;

    private String voiceCode;

    private String outputFormat;

    private Integer sampleRate;

    @Min(value = -500, message = "语速不能小于-500")
    @Max(value = 500, message = "语速不能大于500")
    private Integer speechRate;

    @Min(value = -500, message = "语调不能小于-500")
    @Max(value = 500, message = "语调不能大于500")
    private Integer pitchRate;

    @Min(value = 0, message = "音量不能小于0")
    @Max(value = 100, message = "音量不能大于100")
    private Integer volume;

    private String actionIdleUrl;

    private String actionListeningUrl;

    private String actionThinkingUrl;

    private String actionSpeakingUrl;

    private Integer status;
}
