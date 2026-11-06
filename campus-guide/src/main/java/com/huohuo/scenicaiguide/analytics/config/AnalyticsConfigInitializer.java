package com.huohuo.scenicaiguide.analytics.config;

import com.huohuo.scenicaiguide.system.config.AbstractDomainConfigInitializer;
import com.huohuo.scenicaiguide.system.mapper.SysConfigMapper;
import com.huohuo.scenicaiguide.system.service.ConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * analytics 域配置初始化器
 */
@Component
public class AnalyticsConfigInitializer extends AbstractDomainConfigInitializer {

    private static final Map<String, String> DEFAULT_KEYS = new LinkedHashMap<>();

    static {
        DEFAULT_KEYS.put("analytics-sentiment-batch-size", "情感分析批处理大小");
    }

    private final AnalyticsConfigProperties analyticsConfigProperties;

    @Autowired
    public AnalyticsConfigInitializer(SysConfigMapper configMapper,
                                      AnalyticsConfigProperties analyticsConfigProperties,
                                      ConfigService configService) {
        super(configMapper, configService);
        this.analyticsConfigProperties = analyticsConfigProperties;
    }

    @Override
    protected List<String> keyPrefixes() {
        return List.of("analytics-");
    }

    @Override
    protected Map<String, String> defaultKeys() {
        return DEFAULT_KEYS;
    }

    @Override
    protected String defaultValue(String key) {
        return analyticsConfigProperties.get(key);
    }
}
