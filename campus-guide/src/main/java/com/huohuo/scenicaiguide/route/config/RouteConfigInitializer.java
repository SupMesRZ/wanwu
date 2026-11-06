package com.huohuo.scenicaiguide.route.config;

import com.huohuo.scenicaiguide.system.config.AbstractDomainConfigInitializer;
import com.huohuo.scenicaiguide.system.mapper.SysConfigMapper;
import com.huohuo.scenicaiguide.system.service.ConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * route 域配置初始化器
 */
@Component
public class RouteConfigInitializer extends AbstractDomainConfigInitializer {

    private static final Map<String, String> DEFAULT_KEYS = new LinkedHashMap<>();

    static {
        DEFAULT_KEYS.put("route-dijkstra-weight-distance", "Dijkstra 距离权重");
        DEFAULT_KEYS.put("route-dijkstra-weight-duration", "Dijkstra 耗时权重");
        DEFAULT_KEYS.put("route-dijkstra-weight-crowd", "Dijkstra 拥挤度权重");
        DEFAULT_KEYS.put("route-ai-prefer-tags", "AI 路线推荐偏好标签");
        DEFAULT_KEYS.put("route-max-targets", "预制路线最多途经地点数");
    }

    private final RouteConfigProperties routeConfigProperties;

    @Autowired
    public RouteConfigInitializer(SysConfigMapper configMapper,
                                  RouteConfigProperties routeConfigProperties,
                                  ConfigService configService) {
        super(configMapper, configService);
        this.routeConfigProperties = routeConfigProperties;
    }

    @Override
    protected List<String> keyPrefixes() {
        return List.of("route-");
    }

    @Override
    protected Map<String, String> defaultKeys() {
        return DEFAULT_KEYS;
    }

    @Override
    protected String defaultValue(String key) {
        return routeConfigProperties.get(key);
    }
}
