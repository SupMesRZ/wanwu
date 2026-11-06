package com.huohuo.scenicaiguide.system.config;

import com.huohuo.scenicaiguide.common.AbstractIntegrationTest;

import com.huohuo.scenicaiguide.common.enums.CoordinateSystem;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 坐标配置绑定测试
 */
class CoordinatePropertiesTest extends AbstractIntegrationTest {

    @Autowired
    private CoordinateProperties coordinateProperties;

    @Test
    void defaultSystem_shouldBindFromAppCoordinate() {
        assertEquals(CoordinateSystem.GCJ02, coordinateProperties.getDefaultSystem());
    }
}
