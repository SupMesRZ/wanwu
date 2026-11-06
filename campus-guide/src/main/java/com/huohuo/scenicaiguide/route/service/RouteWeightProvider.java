package com.huohuo.scenicaiguide.route.service;

import com.huohuo.scenicaiguide.scenic.service.ScenicTopologyPathService;
import com.huohuo.scenicaiguide.system.service.ConfigService;
import org.springframework.stereotype.Service;

/**
 * 算路权重提供者
 *
 * <p>按 CLAUDE.md 域职责，算路策略归 route 域；算法本身留在 scenic 域，
 * 因为它遍历的是 scenic 拥有的地图数据，且发布校验要调用它，
 * 搬到 route 会造成 scenic 与 route 相互依赖
 */
@Service
public class RouteWeightProvider {

    private static final String KEY_DISTANCE = "route-dijkstra-weight-distance";
    private static final String KEY_DURATION = "route-dijkstra-weight-duration";
    private static final String KEY_CROWD = "route-dijkstra-weight-crowd";

    private static final double DEFAULT_DISTANCE = 0.5D;
    private static final double DEFAULT_DURATION = 0.3D;
    private static final double DEFAULT_CROWD = 0.2D;

    private final ConfigService configService;

    public RouteWeightProvider(ConfigService configService) {
        this.configService = configService;
    }

    /**
     * 取当前生效的算路权重，配置缺失时回落到代码默认值
     */
    public ScenicTopologyPathService.RouteWeights currentWeights() {
        return new ScenicTopologyPathService.RouteWeights(
            configService.getDouble(KEY_DISTANCE, DEFAULT_DISTANCE),
            configService.getDouble(KEY_DURATION, DEFAULT_DURATION),
            configService.getDouble(KEY_CROWD, DEFAULT_CROWD));
    }
}
