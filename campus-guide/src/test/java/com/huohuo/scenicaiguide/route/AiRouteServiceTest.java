package com.huohuo.scenicaiguide.route;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huohuo.scenicaiguide.common.exception.BusinessException;
import com.huohuo.scenicaiguide.route.dto.AiRoutePlanRequest;
import com.huohuo.scenicaiguide.route.dto.AiRouteResponse;
import com.huohuo.scenicaiguide.route.entity.RouteAiRoute;
import com.huohuo.scenicaiguide.route.mapper.RouteAiRouteMapper;
import com.huohuo.scenicaiguide.route.service.AiRouteService;
import com.huohuo.scenicaiguide.scenic.dto.request.MapWorkbenchDraftRequest;
import com.huohuo.scenicaiguide.scenic.service.ScenicTopologyPathService;
import com.huohuo.scenicaiguide.scenic.service.ScenicTopologyService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiRouteServiceTest {

    @Test
    void createsExactAgentSelectedRouteFromEntranceToExit() {
        Fixture fixture = fixture();
        AiRoutePlanRequest plan = plan("ENDPOINT:E1", "ENDPOINT:E2", List.of("R2", "R3", "R4"));

        AiRouteResponse response = fixture.service.createFromAgentPlan(7L, 1L, plan, null);

        assertThat(response.getRouteSegmentIds()).containsExactly("R2", "R3", "R4");
        assertThat(response.getTotalDistanceMeter()).isEqualTo(690);
        assertThat(response.getNodes()).extracting(AiRouteResponse.RouteNode::getName)
            .containsExactly("北门入口", "A1", "A3", "新实验楼出口");
        assertThat(response.getNodes()).extracting(AiRouteResponse.RouteNode::getType)
            .containsExactly("ENDPOINT", "SPOT", "SPOT", "ENDPOINT");
        assertThat(response.getPathPoints()).first().satisfies(point -> {
            assertThat(point.getLatitude()).isEqualByComparingTo("30.1000");
            assertThat(point.getLongitude()).isEqualByComparingTo("120.1000");
        });
        assertThat(response.getPathPoints()).last().satisfies(point -> {
            assertThat(point.getLatitude()).isEqualByComparingTo("30.1030");
            assertThat(point.getLongitude()).isEqualByComparingTo("120.1030");
        });
    }

    @Test
    void keepsLongerSegmentWhenAgentExplicitlySelectsIt() {
        Fixture fixture = fixture();
        AiRoutePlanRequest plan = plan("ENDPOINT:E1", "SPOT:S1", List.of("R2"));

        AiRouteResponse response = fixture.service.createFromAgentPlan(7L, 1L, plan, null);

        assertThat(response.getRouteSegmentIds()).containsExactly("R2");
        assertThat(response.getTotalDistanceMeter()).isEqualTo(240);
    }

    @Test
    void rejectsDisconnectedAgentSegmentSequence() {
        Fixture fixture = fixture();
        AiRoutePlanRequest plan = plan("ENDPOINT:E1", "ENDPOINT:E2", List.of("R2", "R4"));

        assertThatThrownBy(() -> fixture.service.createFromAgentPlan(7L, 1L, plan, null))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("R4")
            .hasMessageContaining("A1");
    }

    @Test
    void rejectsPlanThatDoesNotEndAtRequestedExit() {
        Fixture fixture = fixture();
        AiRoutePlanRequest plan = plan("ENDPOINT:E1", "ENDPOINT:E2", List.of("R2", "R3"));

        assertThatThrownBy(() -> fixture.service.createFromAgentPlan(7L, 1L, plan, null))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("终点")
            .hasMessageContaining("新实验楼出口");
    }

    private AiRoutePlanRequest plan(String startNodeKey, String endNodeKey, List<String> segmentIds) {
        AiRoutePlanRequest plan = new AiRoutePlanRequest();
        plan.setRouteName("校园完整游览路线");
        plan.setStartNodeKey(startNodeKey);
        plan.setEndNodeKey(endNodeKey);
        plan.setOrderedSegmentIds(segmentIds);
        plan.setReason("按游客指定入口和出口生成");
        return plan;
    }

    private Fixture fixture() {
        ScenicTopologyService topologyService = mock(ScenicTopologyService.class);
        RouteAiRouteMapper mapper = mock(RouteAiRouteMapper.class);
        MapWorkbenchDraftRequest payload = new MapWorkbenchDraftRequest();
        List<MapWorkbenchDraftRequest.SpotDraft> spots = List.of(
            spot("S1", "A1", "30.1010", "120.1010"),
            spot("S2", "A3", "30.1020", "120.1020"));
        payload.setSpots(spots);
        payload.setRouteEndpoints(List.of(
            endpointNode("E1", "北门入口", "30.1000", "120.1000"),
            endpointNode("E2", "新实验楼出口", "30.1030", "120.1030")));
        payload.setRouteSegments(List.of(
            segment("R1", "缆车1号线", endpoint("ENDPOINT", "E1"), endpoint("SPOT", "S1"), 100, 3, "CABLE", true),
            segment("R2", "徒步2号线", endpoint("ENDPOINT", "E1"), endpoint("SPOT", "S1"), 240, 5, "WALK", false),
            segment("R3", "校园中轴线", endpoint("SPOT", "S1"), endpoint("SPOT", "S2"), 300, 8, "WALK", false),
            segment("R4", "实验楼出口线", endpoint("SPOT", "S2"), endpoint("ENDPOINT", "E2"), 150, 4, "WALK", false)));
        ScenicTopologyService.PublishedTopology topology =
            new ScenicTopologyService.PublishedTopology(1L, 20L, payload);
        when(topologyService.getPublishedTopology(1L)).thenReturn(topology);
        when(mapper.insert(any(RouteAiRoute.class))).thenAnswer(invocation -> {
            RouteAiRoute route = invocation.getArgument(0);
            route.setId(99L);
            return 1;
        });
        AiRouteService service = new AiRouteService(
            topologyService, new ScenicTopologyPathService(), mapper, new ObjectMapper());
        return new Fixture(service);
    }

    private MapWorkbenchDraftRequest.SpotDraft spot(String id, String name, String lat, String lng) {
        MapWorkbenchDraftRequest.SpotDraft spot = new MapWorkbenchDraftRequest.SpotDraft();
        spot.setId(id);
        spot.setName(name);
        spot.setStatus(1);
        spot.setLabelPoint(List.of(new BigDecimal(lat), new BigDecimal(lng)));
        return spot;
    }

    private MapWorkbenchDraftRequest.EndpointDraft endpointNode(String id, String name, String lat, String lng) {
        MapWorkbenchDraftRequest.EndpointDraft endpoint = new MapWorkbenchDraftRequest.EndpointDraft();
        endpoint.setId(id);
        endpoint.setName(name);
        endpoint.setEndpointType("MAP_POINT");
        endpoint.setStatus(1);
        endpoint.setPoint(List.of(new BigDecimal(lat), new BigDecimal(lng)));
        return endpoint;
    }

    private MapWorkbenchDraftRequest.RouteDraft segment(String id,
                                                         String name,
                                                         MapWorkbenchDraftRequest.EndpointRefDraft from,
                                                         MapWorkbenchDraftRequest.EndpointRefDraft to,
                                                         int length,
                                                         int duration,
                                                         String routeType,
                                                         boolean paid) {
        MapWorkbenchDraftRequest.RouteDraft route = new MapWorkbenchDraftRequest.RouteDraft();
        route.setId(id);
        route.setName(name);
        route.setFrom(from);
        route.setTo(to);
        route.setLengthMeter(length);
        route.setDurationMin(duration);
        route.setRouteType(routeType);
        route.setPaid(paid);
        route.setStatus(1);
        return route;
    }

    private MapWorkbenchDraftRequest.EndpointRefDraft endpoint(String type, String id) {
        MapWorkbenchDraftRequest.EndpointRefDraft endpoint = new MapWorkbenchDraftRequest.EndpointRefDraft();
        endpoint.setType(type);
        endpoint.setId(id);
        return endpoint;
    }

    private record Fixture(AiRouteService service) {
    }
}
