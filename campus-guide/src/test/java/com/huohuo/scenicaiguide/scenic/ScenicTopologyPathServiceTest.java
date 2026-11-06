package com.huohuo.scenicaiguide.scenic;

import com.huohuo.scenicaiguide.scenic.dto.request.MapWorkbenchDraftRequest;
import com.huohuo.scenicaiguide.scenic.service.ScenicTopologyPathService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScenicTopologyPathServiceTest {

    private final ScenicTopologyPathService service = new ScenicTopologyPathService();

    @Test
    void findsPathThroughIntermediateMapEndpoint() {
        MapWorkbenchDraftRequest payload = new MapWorkbenchDraftRequest();
        payload.setRouteSegments(List.of(
            route("a4-gate", endpoint("SPOT", "a4"), endpoint("ENDPOINT", "gate"), 120),
            route("gate-canteen", endpoint("ENDPOINT", "gate"), endpoint("SPOT", "canteen"), 80)
        ));

        ScenicTopologyPathService.PathResult result = service.findShortestPath(payload, "SPOT:a4", "SPOT:canteen");

        assertThat(result.reachable()).isTrue();
        assertThat(result.routeIds()).containsExactly("a4-gate", "gate-canteen");
        assertThat(result.totalLengthMeter()).isEqualTo(200);
    }

    @Test
    void choosesShortestDistanceInsteadOfFewestSegments() {
        MapWorkbenchDraftRequest payload = new MapWorkbenchDraftRequest();
        payload.setRouteSegments(List.of(
            route("direct", endpoint("SPOT", "a4"), endpoint("SPOT", "canteen"), 900),
            route("a4-gate", endpoint("SPOT", "a4"), endpoint("ENDPOINT", "gate"), 120),
            route("gate-canteen", endpoint("ENDPOINT", "gate"), endpoint("SPOT", "canteen"), 80)
        ));

        ScenicTopologyPathService.PathResult result = service.findShortestPath(payload, "SPOT:a4", "SPOT:canteen");

        assertThat(result.routeIds()).containsExactly("a4-gate", "gate-canteen");
        assertThat(result.totalLengthMeter()).isEqualTo(200);
    }

    @Test
    void ignoresDisabledSegments() {
        MapWorkbenchDraftRequest payload = new MapWorkbenchDraftRequest();
        MapWorkbenchDraftRequest.RouteDraft disabled = route("closed", endpoint("SPOT", "a4"), endpoint("SPOT", "canteen"), 100);
        disabled.setStatus(0);
        payload.setRouteSegments(List.of(disabled));

        ScenicTopologyPathService.PathResult result = service.findShortestPath(payload, "SPOT:a4", "SPOT:canteen");

        assertThat(result.reachable()).isFalse();
        assertThat(result.routeIds()).isEmpty();
    }

    @Test
    void durationWeightAvoidsShortButSlowSegment() {
        MapWorkbenchDraftRequest payload = new MapWorkbenchDraftRequest();
        // 直连更短但要爬 20 分钟台阶，绕行两段各 200 米只要 3 分钟
        MapWorkbenchDraftRequest.RouteDraft stairs = route("stairs", endpoint("SPOT", "a4"), endpoint("SPOT", "canteen"), 300);
        stairs.setDurationMin(20);
        MapWorkbenchDraftRequest.RouteDraft first = route("a4-gate", endpoint("SPOT", "a4"), endpoint("ENDPOINT", "gate"), 200);
        first.setDurationMin(3);
        MapWorkbenchDraftRequest.RouteDraft second = route("gate-canteen", endpoint("ENDPOINT", "gate"), endpoint("SPOT", "canteen"), 200);
        second.setDurationMin(3);
        payload.setRouteSegments(List.of(stairs, first, second));

        ScenicTopologyPathService.PathResult byDistance = service.findShortestPath(
            payload, "SPOT:a4", "SPOT:canteen");
        ScenicTopologyPathService.PathResult byDuration = service.findShortestPath(
            payload, "SPOT:a4", "SPOT:canteen", new ScenicTopologyPathService.RouteWeights(0.5D, 0.3D, 0.2D));

        assertThat(byDistance.routeIds()).containsExactly("stairs");
        assertThat(byDuration.routeIds()).containsExactly("a4-gate", "gate-canteen");
        // 里程与耗时沿选中路段累加真实标注，不能是 Dijkstra 的加权代价
        assertThat(byDuration.totalLengthMeter()).isEqualTo(400);
        assertThat(byDuration.totalDurationMin()).isEqualTo(6);
    }

    @Test
    void crowdWeightDetoursAroundCrowdedSegment() {
        MapWorkbenchDraftRequest payload = new MapWorkbenchDraftRequest();
        MapWorkbenchDraftRequest.RouteDraft crowded = route("crowded", endpoint("SPOT", "a4"), endpoint("SPOT", "canteen"), 300);
        crowded.setCrowdLevel(5);
        MapWorkbenchDraftRequest.RouteDraft first = route("a4-gate", endpoint("SPOT", "a4"), endpoint("ENDPOINT", "gate"), 170);
        first.setCrowdLevel(1);
        MapWorkbenchDraftRequest.RouteDraft second = route("gate-canteen", endpoint("ENDPOINT", "gate"), endpoint("SPOT", "canteen"), 170);
        second.setCrowdLevel(1);
        payload.setRouteSegments(List.of(crowded, first, second));

        ScenicTopologyPathService.PathResult byDistance = service.findShortestPath(
            payload, "SPOT:a4", "SPOT:canteen");
        ScenicTopologyPathService.PathResult weighted = service.findShortestPath(
            payload, "SPOT:a4", "SPOT:canteen", new ScenicTopologyPathService.RouteWeights(0.5D, 0.3D, 0.2D));

        assertThat(byDistance.routeIds()).containsExactly("crowded");
        assertThat(weighted.routeIds()).containsExactly("a4-gate", "gate-canteen");
        assertThat(weighted.totalLengthMeter()).isEqualTo(340);
    }

    @Test
    void allZeroWeightsFallBackToDistanceOnly() {
        ScenicTopologyPathService.RouteWeights weights = new ScenicTopologyPathService.RouteWeights(0D, 0D, 0D);

        // 三项全零会让所有路段代价相等，退化成"按路段数最少"，必须回落纯距离
        assertThat(weights.distance()).isEqualTo(1D);
        assertThat(weights.duration()).isZero();
        assertThat(weights.crowd()).isZero();
    }

    @Test
    void buildsOrderedGeometryThroughMiddlePointAndReversedSegment() {
        MapWorkbenchDraftRequest payload = new MapWorkbenchDraftRequest();
        MapWorkbenchDraftRequest.EndpointRefDraft a4 = endpoint("SPOT", "a4", "30.100", "120.100");
        MapWorkbenchDraftRequest.EndpointRefDraft gate = endpoint("ENDPOINT", "gate", "30.200", "120.200");
        MapWorkbenchDraftRequest.EndpointRefDraft canteen = endpoint("SPOT", "canteen", "30.300", "120.300");
        MapWorkbenchDraftRequest.RouteDraft first = route("a4-gate", a4, gate, 120);
        first.setMiddlePoints(List.of(List.of(new BigDecimal("30.150"), new BigDecimal("120.140"))));
        MapWorkbenchDraftRequest.RouteDraft reversed = route("canteen-gate", canteen, gate, 80);
        reversed.setMiddlePoints(List.of(List.of(new BigDecimal("30.260"), new BigDecimal("120.250"))));
        payload.setRouteSegments(List.of(first, reversed));

        ScenicTopologyPathService.PathResult path = service.findShortestPath(
            payload, "SPOT:a4", "SPOT:canteen");
        List<ScenicTopologyPathService.PathCoordinate> geometry = service.pathGeometry(payload, path);

        assertThat(path.routeIds()).containsExactly("a4-gate", "canteen-gate");
        assertThat(geometry).extracting(ScenicTopologyPathService.PathCoordinate::latitude)
            .containsExactly(
                new BigDecimal("30.100"), new BigDecimal("30.150"), new BigDecimal("30.200"),
                new BigDecimal("30.260"), new BigDecimal("30.300"));
        assertThat(geometry).extracting(ScenicTopologyPathService.PathCoordinate::routeId)
            .containsExactly("a4-gate", "a4-gate", "a4-gate", "canteen-gate", "canteen-gate");
    }

    private MapWorkbenchDraftRequest.RouteDraft route(String id,
                                                       MapWorkbenchDraftRequest.EndpointRefDraft from,
                                                       MapWorkbenchDraftRequest.EndpointRefDraft to,
                                                       int lengthMeter) {
        MapWorkbenchDraftRequest.RouteDraft route = new MapWorkbenchDraftRequest.RouteDraft();
        route.setId(id);
        route.setFrom(from);
        route.setTo(to);
        route.setLengthMeter(lengthMeter);
        route.setStatus(1);
        return route;
    }

    private MapWorkbenchDraftRequest.EndpointRefDraft endpoint(String type, String id) {
        MapWorkbenchDraftRequest.EndpointRefDraft endpoint = new MapWorkbenchDraftRequest.EndpointRefDraft();
        endpoint.setType(type);
        endpoint.setId(id);
        return endpoint;
    }

    private MapWorkbenchDraftRequest.EndpointRefDraft endpoint(
        String type, String id, String latitude, String longitude) {
        MapWorkbenchDraftRequest.EndpointRefDraft endpoint = endpoint(type, id);
        endpoint.setPoint(List.of(new BigDecimal(latitude), new BigDecimal(longitude)));
        return endpoint;
    }
}
