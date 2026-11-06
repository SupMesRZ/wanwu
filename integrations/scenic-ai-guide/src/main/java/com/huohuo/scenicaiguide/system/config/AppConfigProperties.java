package com.huohuo.scenicaiguide.system.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * system 域应用配置属性
 * <p>
 * 从 application.yaml 的 app.config 节点读取 system 级配置，作为数据库配置缺失时的兜底
 * <p>
 * 注意：业务域配置默认值由各域 XxxConfigProperties 定义，不在 system 属性类中混放
 */
@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "app")
public class AppConfigProperties {

    private Map<String, String> config = new HashMap<>();

    /**
     * 获取配置值
     *
     * @param key 配置键
     * @return 配置值，不存在返回 null
     */
    public String get(String key) {
        return config.get(key);
    }
}
