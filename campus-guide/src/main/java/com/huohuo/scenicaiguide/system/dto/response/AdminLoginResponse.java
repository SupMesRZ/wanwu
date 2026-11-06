package com.huohuo.scenicaiguide.system.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 管理员登录响应
 */
@Schema(description = "管理员登录响应")
@Data
public class AdminLoginResponse {

    @Schema(description = "是否需要短信验证", example = "true")
    private Boolean needSmsVerify;

    @Schema(description = "临时令牌（需短信验证时返回）")
    private String tempToken;

    @Schema(description = "脱敏手机号", example = "138****8000")
    private String maskedPhone;

    @Schema(description = "验证码回显（定档：不接真实短信，前端直接展示）")
    private String displaySmsCode;

    @Schema(description = "访问令牌（直接登录成功时返回）")
    private String accessToken;

    @Schema(description = "刷新令牌（直接登录成功时返回）")
    private String refreshToken;

    @Schema(description = "访问令牌有效期（秒）", example = "3600")
    private Long expiresIn;
}
