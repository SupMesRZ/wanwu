package com.huohuo.scenicaiguide.tourist.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 匿名 session 初始化响应
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SessionInitResponse {

    private String deviceId;

    private String accessToken;

    private String authLevel;
}
