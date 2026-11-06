package com.huohuo.scenicaiguide.system.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 短信验证码验证请求
 */
@Schema(description = "短信验证码验证请求")
@Data
public class SmsVerifyRequest {

    @Schema(description = "短信验证码", example = "123456")
    @NotBlank(message = "验证码不能为空")
    private String smsCode;
}
