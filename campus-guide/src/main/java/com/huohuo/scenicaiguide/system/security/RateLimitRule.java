package com.huohuo.scenicaiguide.system.security;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import org.springframework.util.AntPathMatcher;

import java.util.List;

/**
 * 限流规则
 * <p>
 * 规则不是表数据而是配置中心的 JSON 值（§5 定档：限流不建表），
 * 由 {@code ops-rate-limit-rules} 承载，后台"限流配置"页结构化编辑
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RateLimitRule {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    /** 规则名，同时是计数 key 的一部分，改名等于重置计数 */
    private String name;

    /** Ant 风格路径模式，如 /api/v1/system/auth/** */
    private String pattern;

    /** 命中的 HTTP 方法，空表示不限方法 */
    private List<String> methods;

    /** 窗口内允许的最大请求数 */
    private Integer limit;

    /** 固定窗口时长（秒） */
    private Integer windowSeconds;

    private Boolean enabled;

    public boolean isEffective() {
        return Boolean.TRUE.equals(enabled)
            && name != null && !name.isBlank()
            && pattern != null && !pattern.isBlank()
            && limit != null && limit > 0
            && windowSeconds != null && windowSeconds > 0;
    }

    public boolean matches(String uri, String httpMethod) {
        if (methods != null && !methods.isEmpty()
            && methods.stream().noneMatch(m -> m.equalsIgnoreCase(httpMethod))) {
            return false;
        }
        return PATH_MATCHER.match(pattern, uri);
    }
}
