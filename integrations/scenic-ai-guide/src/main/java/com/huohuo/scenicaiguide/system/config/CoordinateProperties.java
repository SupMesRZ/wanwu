package com.huohuo.scenicaiguide.system.config;

import com.huohuo.scenicaiguide.common.enums.CoordinateSystem;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 坐标系统配置属性
 *
 * <p>绑定 application.yaml 的 app.coordinate 节点
 */
@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "app.coordinate")
public class CoordinateProperties {

    /**
     * 对外 API 默认返回的坐标系
     */
    private CoordinateSystem defaultSystem = CoordinateSystem.GCJ02;
}
