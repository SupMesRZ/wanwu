package com.huohuo.scenicaiguide.system.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Token 响应
 */
@Schema(description = "Token 响应")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TokenResponse {

    @Schema(description = "访问令牌")
    private String accessToken;

    @Schema(description = "刷新令牌")
    private String refreshToken;

    @Schema(description = "访问令牌有效期（秒）", example = "3600")
    private Long expiresIn;

    @Schema(description = "认证级别: ANONYMOUS / AUTHENTICATED / FENCE", example = "AUTHENTICATED")
    private String authLevel;
}
