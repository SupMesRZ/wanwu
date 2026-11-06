package com.huohuo.scenicaiguide.digitalhuman.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * digitalhuman 域配置属性
 *
 * <p>从 application.yaml 的 dh.config 节点读取，作为数据库配置缺失时的兜底
 */
@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "dh")
public class DhConfigProperties {

    private Map<String, String> config = new HashMap<>();

    public String get(String key) {
        return config.get(key);
    }
}
