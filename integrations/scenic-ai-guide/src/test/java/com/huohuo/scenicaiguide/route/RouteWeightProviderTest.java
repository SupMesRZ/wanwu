package com.huohuo.scenicaiguide.route;

import com.huohuo.scenicaiguide.route.service.RouteWeightProvider;
import com.huohuo.scenicaiguide.scenic.service.ScenicTopologyPathService;
import com.huohuo.scenicaiguide.system.service.ConfigService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RouteWeightProviderTest {

    @Test
    void readsWeightsFromConfigCenter() {
        ConfigService configService = mock(ConfigService.class);
        when(configService.getDouble(eq("route-dijkstra-weight-distance"), anyDouble())).thenReturn(0.8D);
        when(configService.getDouble(eq("route-dijkstra-weight-duration"), anyDouble())).thenReturn(0.15D);
        when(configService.getDouble(eq("route-dijkstra-weight-crowd"), anyDouble())).thenReturn(0.05D);

        ScenicTopologyPathService.RouteWeights weights = new RouteWeightProvider(configService).currentWeights();

        assertThat(weights.distance()).isEqualTo(0.8D);
        assertThat(weights.duration()).isEqualTo(0.15D);
        assertThat(weights.crowd()).isEqualTo(0.05D);
    }

    @Test
    void fallsBackToCodeDefaultsWhenConfigMissing() {
        // 配置中心不可用时 getDouble 返回入参默认值，算路不能因此失效
        ConfigService configService = mock(ConfigService.class);
        when(configService.getDouble(anyString(), anyDouble()))
            .thenAnswer(invocation -> invocation.getArgument(1));

        ScenicTopologyPathService.RouteWeights weights = new RouteWeightProvider(configService).currentWeights();

        assertThat(weights.distance()).isEqualTo(0.5D);
        assertThat(weights.duration()).isEqualTo(0.3D);
        assertThat(weights.crowd()).isEqualTo(0.2D);
    }
}
