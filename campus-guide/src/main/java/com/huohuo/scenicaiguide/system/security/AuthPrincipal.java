package com.huohuo.scenicaiguide.system.security;

/**
 * 认证主体
 * <p>
 * 封装 JWT 中的用户标识和认证级别
 */
public record AuthPrincipal(
    String subject,
    String role,
    String authLevel,
    Boolean isRoot,
    Long userId
) {
}
