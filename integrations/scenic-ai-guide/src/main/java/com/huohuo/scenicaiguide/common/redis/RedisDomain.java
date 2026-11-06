package com.huohuo.scenicaiguide.common.redis;

import lombok.Getter;

/**
 * Redis 域枚举
 * <p>
 * 统一管理所有业务域标识，用于 Redis key 的 {域} 段拼接
 */
@Getter
public enum RedisDomain {

    QA("qa"),
    TOURIST("tourist"),
    DIGITAL_HUMAN("digitalhuman"),
    SCENIC("scenic"),
    ROUTE("route"),
    ANALYTICS("analytics"),
    SYSTEM("system");

    private final String domain;

    RedisDomain(String domain) {
        this.domain = domain;
    }
}
