package com.huohuo.scenicaiguide.qa;

import com.huohuo.scenicaiguide.common.enums.ErrorCode;
import com.huohuo.scenicaiguide.common.exception.BusinessException;
import com.huohuo.scenicaiguide.qa.service.QaGuideAnswerService;
import com.huohuo.scenicaiguide.qa.tool.ScenicGuideToolFactory;
import com.huohuo.scenicaiguide.route.dto.AiRoutePlanRequest;
import com.huohuo.scenicaiguide.route.dto.AiRouteResponse;
import com.huohuo.scenicaiguide.route.service.AiRouteService;
import com.huohuo.scenicaiguide.route.service.RoutePreferenceProvider;
import com.huohuo.scenicaiguide.route.service.RouteWeightProvider;
import com.huohuo.scenicaiguide.scenic.dto.request.MapWorkbenchDraftRequest;
import com.huohuo.scenicaiguide.scenic.service.ScenicTagService;
import com.huohuo.scenicaiguide.scenic.service.ScenicTopologyPathService;
import com.huohuo.scenicaiguide.scenic.service.ScenicTopologyService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class ScenicGuideToolFactoryTest {

    /** 模拟配置中心下发的算路权重 */
    private static final ScenicTopologyPathService.RouteWeights CONFIGURED_WEIGHTS =
        new ScenicTopologyPathService.RouteWeights(0.5D, 0.3D, 0.2D);

    @Test
    void searchesPublishedSpotsAndFacilities() {
        TestFixture fixture = fixture();

        ScenicGuideToolFactory.ResourceSearchResult result = fixture.tools.searchScenicResources("景点和餐饮");

        assertThat(result.spots()).extracting(ScenicGuideToolFactory.SpotInfo::name)
            .containsExactly("A4", "食堂");
        assertThat(result.facilities()).extracting(ScenicGuideToolFactory.FacilityInfo::name)
            .containsExactly("游客食堂");
    }

    @Test
    void returnsConfirmedKnowledgeInsteadOfInventingFacts() {
        TestFixture fixture = fixture();
        when(fixture.answerService.preview(1L, "A4有什么故事", "A4")).thenReturn(
            new QaGuideAnswerService.AnswerResult("A4 是景区东侧观景点", "FAQ", "FAQ#7"));

        ScenicGuideToolFactory.KnowledgeResult result = fixture.tools.searchScenicKnowledge("A4有什么故事");

        assertThat(result.confirmed()).isTrue();
        assertThat(result.content()).contains("东侧观景点");
        assertThat(result.evidence()).isEqualTo("FAQ#7");
    }

    @Test
    void computesShortestPathBetweenNamedSpots() {
        TestFixture fixture = fixture();
        // 算路必须带上配置权重，且里程与耗时都取算路返回的真实值
        when(fixture.pathService.findShortestPath(
            fixture.topology.payload(), "SPOT:A4", "SPOT:CANTEEN", CONFIGURED_WEIGHTS))
            .thenReturn(new ScenicTopologyPathService.PathResult(
                true, List.of("SPOT:A4", "SPOT:CANTEEN"), List.of("R1"), 274, 9));

        ScenicGuideToolFactory.PathToolResult result = fixture.tools.findShortestPath("A4", "食堂");

        assertThat(result.reachable()).isTrue();
        assertThat(result.totalLengthMeter()).isEqualTo(274);
        assertThat(result.totalDurationMin()).isEqualTo(9);
        assertThat(result.nodes()).containsExactly("A4", "食堂");
        assertThat(result.routeSegments()).containsExactly("A4 到食堂");

        ScenicGuideToolFactory.PathVisualization visualization = fixture.tools
            .resolvePathVisualization("A4到食堂怎么走")
            .orElseThrow();
        assertThat(visualization.totalLengthMeter()).isEqualTo(274);
        assertThat(visualization.totalDurationMin()).isEqualTo(9);
        assertThat(visualization.routeSegmentIds()).containsExactly("R1");
        assertThat(visualization.nodes()).extracting(ScenicGuideToolFactory.PathNode::name)
            .containsExactly("A4", "食堂");
        assertThat(fixture.tools.resolvePathVisualization("A4和食堂哪个好")).isEmpty();
    }

    @Test
    void fallsBackToDistanceOnlyWhenWeightProviderAbsent() {
        ScenicTopologyService topologyService = mock(ScenicTopologyService.class);
        ScenicTopologyPathService pathService = mock(ScenicTopologyPathService.class);
        MapWorkbenchDraftRequest payload = payload();
        ScenicTopologyService.PublishedTopology topology =
            new ScenicTopologyService.PublishedTopology(1L, 9L, payload);
        when(topologyService.getPublishedTopology(1L)).thenReturn(topology);
        when(topologyService.enabledSpots(topology)).thenReturn(payload.getSpots());
        when(pathService.spotKey("A4")).thenReturn("SPOT:A4");
        when(pathService.spotKey("CANTEEN")).thenReturn("SPOT:CANTEEN");
        when(pathService.findShortestPath(payload, "SPOT:A4", "SPOT:CANTEEN",
            ScenicTopologyPathService.RouteWeights.distanceOnly()))
            .thenReturn(new ScenicTopologyPathService.PathResult(
                true, List.of("SPOT:A4", "SPOT:CANTEEN"), List.of("R1"), 274));
        ScenicGuideToolFactory factory = new ScenicGuideToolFactory(
            topologyService, pathService, mock(QaGuideAnswerService.class));

        ScenicGuideToolFactory.PathToolResult result = factory.create(1L, "A4")
            .findShortestPath("A4", "食堂");

        assertThat(result.reachable()).isTrue();
        // 未标注耗时时按步行速度折算：274 / 70 向上取整
        assertThat(result.totalDurationMin()).isEqualTo(4);
    }

    @Test
    void listsPublishedPresetRoutes() {
        TestFixture fixture = fixture();

        List<ScenicGuideToolFactory.PresetRouteInfo> result = fixture.tools.listPresetRoutes("轻松");

        assertThat(result).singleElement().satisfies(route -> {
            assertThat(route.name()).isEqualTo("轻松游");
            assertThat(route.spots()).containsExactly("A4", "食堂");
            assertThat(route.durationMin()).isEqualTo(60);
        });
    }

    @Test
    void exposesCompactMapContextForAgentPlanning() {
        TestFixture fixture = fixture();

        ScenicGuideToolFactory.ScenicMapContext context = fixture.tools.inspectScenicMap();

        assertThat(context.currentSpotName()).isEqualTo("A4");
        assertThat(context.nodes()).extracting(ScenicGuideToolFactory.MapNodeInfo::name)
            .containsExactly("A4", "食堂");
        assertThat(context.connections()).singleElement().satisfies(connection -> {
            assertThat(connection.fromKey()).isEqualTo("SPOT:A4");
            assertThat(connection.fromName()).isEqualTo("A4");
            assertThat(connection.toKey()).isEqualTo("SPOT:CANTEEN");
            assertThat(connection.toName()).isEqualTo("食堂");
            assertThat(connection.lengthMeter()).isEqualTo(274);
        });
    }

    @Test
    void agentRouteToolPassesStructuredConstraintsToRouteService() {
        ScenicTopologyService topologyService = mock(ScenicTopologyService.class);
        ScenicTopologyPathService pathService = mock(ScenicTopologyPathService.class);
        QaGuideAnswerService answerService = mock(QaGuideAnswerService.class);
        AiRouteService routeService = mock(AiRouteService.class);
        AiRouteResponse route = new AiRouteResponse();
        route.setId(18L);
        route.setName("AI 图书馆路线");
        route.setTotalDistanceMeter(630);
        route.setEstimatedDurationMin(55);
        route.setRecommendationReason("包含图书馆并避开食堂");
        route.setRouteSegmentIds(List.of("R1"));
        AiRouteResponse.RouteNode node = new AiRouteResponse.RouteNode();
        node.setName("图书馆");
        route.setNodes(List.of(node));
        when(routeService.createFromAgentPlan(eq(7L), eq(1L), any(AiRoutePlanRequest.class), eq(8L)))
            .thenReturn(route);
        ScenicGuideToolFactory factory = new ScenicGuideToolFactory(
            topologyService, pathService, answerService, routeService);
        List<String> progress = new ArrayList<>();
        ScenicGuideToolFactory.ScenicGuideTools tools = factory.create(
            new ScenicGuideToolFactory.AgentContext(7L, 1L, "A4", "A4", 8L, true), progress::add);

        ScenicGuideToolFactory.RoutePlanToolResult result = tools.createTourRoute(
            new ScenicGuideToolFactory.RoutePlanToolRequest(
                "AI 图书馆路线", "SPOT:A4", "SPOT:CANTEEN", List.of("R1"),
                180, "包含图书馆并避开食堂", true));

        assertThat(result.success()).isTrue();
        assertThat(result.routeName()).isEqualTo("AI 图书馆路线");
        assertThat(tools.recommendedRoute()).isSameAs(route);
        assertThat(tools.routePlan().getStartNodeKey()).isEqualTo("SPOT:A4");
        assertThat(tools.routePlan().getEndNodeKey()).isEqualTo("SPOT:CANTEEN");
        assertThat(tools.routePlan().getOrderedSegmentIds()).containsExactly("R1");
        assertThat(tools.routePlan().getTargetDurationMinutes()).isEqualTo(180);
        assertThat(progress).containsExactly("正在校验并生成校园路线", "路线已生成，正在整理结果");
    }

    @Test
    void routeToolReturnsValidationFailureForAgentRetry() {
        ScenicTopologyService topologyService = mock(ScenicTopologyService.class);
        ScenicTopologyPathService pathService = mock(ScenicTopologyPathService.class);
        QaGuideAnswerService answerService = mock(QaGuideAnswerService.class);
        AiRouteService routeService = mock(AiRouteService.class);
        when(routeService.createFromAgentPlan(eq(7L), eq(1L), any(AiRoutePlanRequest.class), eq(8L)))
            .thenThrow(new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED, "R2与当前节点A1不连续"));
        ScenicGuideToolFactory factory = new ScenicGuideToolFactory(
            topologyService, pathService, answerService, routeService);
        ScenicGuideToolFactory.ScenicGuideTools tools = factory.create(
            new ScenicGuideToolFactory.AgentContext(7L, 1L, "A1", "A1", 8L, true));

        ScenicGuideToolFactory.RoutePlanToolResult result = tools.createTourRoute(
            new ScenicGuideToolFactory.RoutePlanToolRequest(
                "错误路线", "SPOT:A1", "SPOT:A3", List.of("R2"), 120, "测试", true));

        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo("OPERATION_NOT_ALLOWED");
        assertThat(result.message()).contains("R2", "A1", "不连续");
        assertThat(tools.recommendedRoute()).isNull();
    }

    @Test
    void spotSearchExposesResolvedTagNames() {
        ScenicTopologyService topologyService = mock(ScenicTopologyService.class);
        ScenicTopologyPathService pathService = mock(ScenicTopologyPathService.class);
        ScenicTagService tagService = mock(ScenicTagService.class);
        MapWorkbenchDraftRequest payload = payload();
        payload.getSpots().get(0).setTags(List.of("family", "stale-code"));
        ScenicTopologyService.PublishedTopology topology =
            new ScenicTopologyService.PublishedTopology(1L, 9L, payload);
        when(topologyService.getPublishedTopology(1L)).thenReturn(topology);
        when(topologyService.enabledSpots(topology)).thenReturn(payload.getSpots());
        when(tagService.enabledCodeToName()).thenReturn(Map.of("family", "亲子休闲"));
        ScenicGuideToolFactory factory = new ScenicGuideToolFactory(
            topologyService, pathService, mock(QaGuideAnswerService.class), null, null, tagService);

        ScenicGuideToolFactory.ResourceSearchResult result =
            factory.create(1L, "A4").searchScenicResources("A4");

        // 给 LLM 的是解析后的中文名；失效 code 静默丢弃，不混进提示词
        assertThat(result.spots()).singleElement().satisfies(
            spot -> assertThat(spot.tags()).containsExactly("亲子休闲"));
    }

    @Test
    void tagServiceFailureDegradesToEmptyTags() {
        ScenicTopologyService topologyService = mock(ScenicTopologyService.class);
        ScenicTopologyPathService pathService = mock(ScenicTopologyPathService.class);
        ScenicTagService tagService = mock(ScenicTagService.class);
        MapWorkbenchDraftRequest payload = payload();
        payload.getSpots().get(0).setTags(List.of("family"));
        ScenicTopologyService.PublishedTopology topology =
            new ScenicTopologyService.PublishedTopology(1L, 9L, payload);
        when(topologyService.getPublishedTopology(1L)).thenReturn(topology);
        when(topologyService.enabledSpots(topology)).thenReturn(payload.getSpots());
        when(tagService.enabledCodeToName()).thenThrow(new IllegalStateException("db down"));
        ScenicGuideToolFactory factory = new ScenicGuideToolFactory(
            topologyService, pathService, mock(QaGuideAnswerService.class), null, null, tagService);

        ScenicGuideToolFactory.ResourceSearchResult result =
            factory.create(1L, "A4").searchScenicResources("A4");

        // 词表故障不能让 agent 整体不可用，降级为不带标签
        assertThat(result.spots()).singleElement().satisfies(
            spot -> assertThat(spot.tags()).isEmpty());
    }

    @Test
    void preferredTagsAreAppendedToPlanningInstruction() {
        ScenicTopologyService topologyService = mock(ScenicTopologyService.class);
        ScenicTopologyPathService pathService = mock(ScenicTopologyPathService.class);
        RoutePreferenceProvider preferenceProvider = mock(RoutePreferenceProvider.class);
        MapWorkbenchDraftRequest payload = payload();
        ScenicTopologyService.PublishedTopology topology =
            new ScenicTopologyService.PublishedTopology(1L, 9L, payload);
        when(topologyService.getPublishedTopology(1L)).thenReturn(topology);
        when(topologyService.enabledSpots(topology)).thenReturn(payload.getSpots());
        when(preferenceProvider.preferredTagNames()).thenReturn(List.of("亲子休闲", "历史探秘"));
        ScenicGuideToolFactory factory = new ScenicGuideToolFactory(
            topologyService, pathService, mock(QaGuideAnswerService.class), null, null, null,
            preferenceProvider);

        ScenicGuideToolFactory.ScenicMapContext context = factory.create(1L, "A4").inspectScenicMap();

        // 偏好以软提示进入指令，游客明确指定的目的地仍然优先
        assertThat(context.instruction()).contains("亲子休闲", "历史探秘").contains("createTourRoute");
    }

    @Test
    void noPreferenceKeepsBaseInstruction() {
        TestFixture fixture = fixture();

        assertThat(fixture.tools.inspectScenicMap().instruction())
            .isEqualTo("先确定起点key和终点key，再按顺序选择首尾连续的路线编号，最后调用createTourRoute");
    }

    @Test
    void routesToFacilityByAliasViaNearestGraphNode() {
        // 设施不在路网节点上：算路到最近节点，再补一段直线尾程并如实提示
        FacilityFixture fixture = facilityFixture();
        when(fixture.pathService.findShortestPath(
            fixture.payload, "SPOT:A4", "SPOT:CANTEEN",
            ScenicTopologyPathService.RouteWeights.distanceOnly()))
            .thenReturn(new ScenicTopologyPathService.PathResult(
                true, List.of("SPOT:A4", "SPOT:CANTEEN"), List.of("R1"), 274, 9));

        ScenicGuideToolFactory.PathToolResult result = fixture.tools.findShortestPath("A4", "洗手间");

        assertThat(result.reachable()).isTrue();
        // 卫生间离食堂约 11 米（纬度差 0.0001），离 A4 约 122 米，应选食堂作为路网落点
        assertThat(result.nodes()).containsExactly("A4", "食堂", "东侧卫生间");
        assertThat(result.totalLengthMeter()).isEqualTo(274 + 11);
        assertThat(result.totalDurationMin()).isEqualTo(9 + 1);
        assertThat(result.routeSegments()).containsExactly("A4 到食堂");
        assertThat(result.message()).contains("食堂").contains("再步行约11米");
    }

    @Test
    void pathVisualizationExtendsLineToFacility() {
        FacilityFixture fixture = facilityFixture();
        ScenicTopologyPathService.PathResult path = new ScenicTopologyPathService.PathResult(
            true, List.of("SPOT:A4", "SPOT:CANTEEN"), List.of("R1"), 274, 9);
        when(fixture.pathService.findShortestPath(
            fixture.payload, "SPOT:A4", "SPOT:CANTEEN",
            ScenicTopologyPathService.RouteWeights.distanceOnly()))
            .thenReturn(path);
        when(fixture.pathService.pathGeometry(eq(fixture.payload), eq(path))).thenReturn(List.of(
            new ScenicTopologyPathService.PathCoordinate(
                "R1", new BigDecimal("30.1000"), new BigDecimal("120.1000"), 1),
            new ScenicTopologyPathService.PathCoordinate(
                "R1", new BigDecimal("30.1010"), new BigDecimal("120.1020"), 2)));

        ScenicGuideToolFactory.PathVisualization visualization = fixture.tools
            .resolvePathVisualization("从A4怎么去洗手间")
            .orElseThrow();

        assertThat(visualization.toName()).isEqualTo("东侧卫生间");
        assertThat(visualization.nodes()).extracting(ScenicGuideToolFactory.PathNode::name)
            .containsExactly("A4", "食堂", "东侧卫生间");
        // 尾段直线补一个不带路线编号的几何点，前端才能把线画到设施
        assertThat(visualization.pathPoints()).hasSize(3);
        assertThat(visualization.pathPoints().getLast().segmentId()).isNull();
        assertThat(visualization.pathPoints().getLast().sequence()).isEqualTo(3);
        assertThat(visualization.totalLengthMeter()).isEqualTo(274 + 11);
        assertThat(visualization.totalDurationMin()).isEqualTo(9 + 1);
    }

    @Test
    void facilityWithoutCoordinateIsNotADestination() {
        FacilityFixture fixture = facilityFixture();
        fixture.payload.getFacilities().get(1).setPoint(null);

        ScenicGuideToolFactory.PathToolResult result = fixture.tools.findShortestPath("A4", "东侧卫生间");

        // 设施没有坐标就无法算尾程，宁可报找不到也不能编一条直线
        assertThat(result.reachable()).isFalse();
        assertThat(result.message()).contains("没有找到终点");
    }

    private FacilityFixture facilityFixture() {
        ScenicTopologyService topologyService = mock(ScenicTopologyService.class);
        ScenicTopologyPathService pathService = mock(ScenicTopologyPathService.class);
        MapWorkbenchDraftRequest payload = payload();
        MapWorkbenchDraftRequest.FacilityDraft toilet = new MapWorkbenchDraftRequest.FacilityDraft();
        toilet.setId("F2");
        toilet.setName("东侧卫生间");
        toilet.setFacilityType("卫生间");
        toilet.setPoint(List.of(new BigDecimal("30.1011"), new BigDecimal("120.1021")));
        toilet.setStatus(1);
        payload.setFacilities(List.of(payload.getFacilities().getFirst(), toilet));
        ScenicTopologyService.PublishedTopology topology =
            new ScenicTopologyService.PublishedTopology(1L, 9L, payload);
        when(topologyService.getPublishedTopology(1L)).thenReturn(topology);
        when(topologyService.enabledSpots(topology)).thenReturn(payload.getSpots());
        // 简化版 haversine：只按纬度差折算米数，足够区分食堂（11m）和 A4（122m）
        when(topologyService.distanceMeters(any(), any(), any(), any())).thenAnswer(invocation -> {
            BigDecimal fromLat = invocation.getArgument(0);
            BigDecimal toLat = invocation.getArgument(2);
            return Math.abs(fromLat.doubleValue() - toLat.doubleValue()) * 111_000D;
        });
        when(pathService.spotKey("A4")).thenReturn("SPOT:A4");
        when(pathService.spotKey("CANTEEN")).thenReturn("SPOT:CANTEEN");
        ScenicGuideToolFactory factory = new ScenicGuideToolFactory(
            topologyService, pathService, mock(QaGuideAnswerService.class));
        return new FacilityFixture(factory.create(1L, "A4"), pathService, payload);
    }

    private record FacilityFixture(ScenicGuideToolFactory.ScenicGuideTools tools,
                                   ScenicTopologyPathService pathService,
                                   MapWorkbenchDraftRequest payload) {
    }

    private TestFixture fixture() {
        ScenicTopologyService topologyService = mock(ScenicTopologyService.class);
        ScenicTopologyPathService pathService = mock(ScenicTopologyPathService.class);
        QaGuideAnswerService answerService = mock(QaGuideAnswerService.class);
        MapWorkbenchDraftRequest payload = payload();
        ScenicTopologyService.PublishedTopology topology =
            new ScenicTopologyService.PublishedTopology(1L, 9L, payload);
        when(topologyService.getPublishedTopology(1L)).thenReturn(topology);
        when(topologyService.enabledSpots(topology)).thenReturn(payload.getSpots());
        when(pathService.spotKey("A4")).thenReturn("SPOT:A4");
        when(pathService.spotKey("CANTEEN")).thenReturn("SPOT:CANTEEN");
        when(pathService.endpointKey(any(MapWorkbenchDraftRequest.EndpointRefDraft.class)))
            .thenAnswer(invocation -> {
                MapWorkbenchDraftRequest.EndpointRefDraft endpoint = invocation.getArgument(0);
                return endpoint.getType() + ":" + endpoint.getId();
            });
        RouteWeightProvider weightProvider = mock(RouteWeightProvider.class);
        when(weightProvider.currentWeights()).thenReturn(CONFIGURED_WEIGHTS);
        ScenicGuideToolFactory factory = new ScenicGuideToolFactory(
            topologyService, pathService, answerService, null, weightProvider);
        return new TestFixture(factory.create(1L, "A4"), topology, pathService, answerService);
    }

    private MapWorkbenchDraftRequest payload() {
        MapWorkbenchDraftRequest payload = new MapWorkbenchDraftRequest();
        MapWorkbenchDraftRequest.SpotDraft a4 = spot("A4", "A4", "东侧观景点");
        MapWorkbenchDraftRequest.SpotDraft canteen = spot("CANTEEN", "食堂", "景区餐饮点");
        payload.setSpots(List.of(a4, canteen));

        MapWorkbenchDraftRequest.FacilityDraft facility = new MapWorkbenchDraftRequest.FacilityDraft();
        facility.setId("F1");
        facility.setName("游客食堂");
        facility.setFacilityType("餐饮");
        facility.setDescription("提供简餐");
        facility.setStatus(1);
        payload.setFacilities(List.of(facility));

        MapWorkbenchDraftRequest.EndpointRefDraft from = endpoint("SPOT", "A4", "A4");
        MapWorkbenchDraftRequest.EndpointRefDraft to = endpoint("SPOT", "CANTEEN", "食堂");
        MapWorkbenchDraftRequest.RouteDraft segment = new MapWorkbenchDraftRequest.RouteDraft();
        segment.setId("R1");
        segment.setName("A4 到食堂");
        segment.setFrom(from);
        segment.setTo(to);
        segment.setLengthMeter(274);
        segment.setStatus(1);
        payload.setRouteSegments(List.of(segment));

        MapWorkbenchDraftRequest.PresetRouteDraft preset = new MapWorkbenchDraftRequest.PresetRouteDraft();
        preset.setId("P1");
        preset.setName("轻松游");
        preset.setSpotIds(List.of("A4", "CANTEEN"));
        preset.setDurationMin(60);
        preset.setAudience("轻松");
        preset.setRecommendationNote("少走路");
        preset.setStatus(1);
        payload.setPresetRoutes(List.of(preset));
        return payload;
    }

    private MapWorkbenchDraftRequest.SpotDraft spot(String id, String name, String description) {
        MapWorkbenchDraftRequest.SpotDraft spot = new MapWorkbenchDraftRequest.SpotDraft();
        spot.setId(id);
        spot.setName(name);
        spot.setDescription(description);
        spot.setLabelPoint("A4".equals(id)
            ? List.of(new BigDecimal("30.1000"), new BigDecimal("120.1000"))
            : List.of(new BigDecimal("30.1010"), new BigDecimal("120.1020")));
        spot.setStatus(1);
        return spot;
    }

    private MapWorkbenchDraftRequest.EndpointRefDraft endpoint(String type, String id, String name) {
        MapWorkbenchDraftRequest.EndpointRefDraft endpoint = new MapWorkbenchDraftRequest.EndpointRefDraft();
        endpoint.setType(type);
        endpoint.setId(id);
        endpoint.setName(name);
        return endpoint;
    }

    private record TestFixture(ScenicGuideToolFactory.ScenicGuideTools tools,
                               ScenicTopologyService.PublishedTopology topology,
                               ScenicTopologyPathService pathService,
                               QaGuideAnswerService answerService) {
    }
}
