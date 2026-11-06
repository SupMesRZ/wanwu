package com.huohuo.scenicaiguide.system.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类
 */
@Component
public class JwtUtil {

    @Value("${security.jwt.secret}")
    private String secret;

    private SecretKey key() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成 token
     *
     * @param sub         用户标识
     * @param role        角色
     * @param authLevel   认证级别
     * @param expireMinutes 过期时长（分钟）
     * @return JWT token
     */
    public String generateToken(String sub, String role, String authLevel, long expireMinutes) {
        return generateToken(sub, role, authLevel, null, expireMinutes);
    }

    public String generateToken(String sub, String role, String authLevel, Boolean isRoot, long expireMinutes) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expireMinutes * 60 * 1000);
        var builder = Jwts.builder()
            .subject(sub)
            .claim("role", role)
            .claim("authLevel", authLevel)
            .issuedAt(now)
            .expiration(expiry);
        if (isRoot != null) {
            builder.claim("isRoot", isRoot);
        }
        return builder.signWith(key()).compact();
    }

    /**
     * 解析 token
     *
     * @param token JWT token
     * @return Claims
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
            .verifyWith(key())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    /**
     * 验证 token 是否有效
     *
     * @param token JWT token
     * @return true 表示有效
     */
    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
