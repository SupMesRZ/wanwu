package com.huohuo.scenicaiguide.qa.tool;

import com.huohuo.scenicaiguide.common.exception.BusinessException;
import com.huohuo.scenicaiguide.qa.service.QaGuideAnswerService;
import com.huohuo.scenicaiguide.route.dto.AiRoutePlanRequest;
import com.huohuo.scenicaiguide.route.dto.AiRouteResponse;
import com.huohuo.scenicaiguide.route.service.AiRouteService;
import com.huohuo.scenicaiguide.route.service.RoutePreferenceProvider;
import com.huohuo.scenicaiguide.route.service.RouteWeightProvider;
import com.huohuo.scenicaiguide.scenic.dto.request.MapWorkbenchDraftRequest;
import com.huohuo.scenicaiguide.scenic.service.ScenicTagService;
import com.huohuo.scenicaiguide.scenic.service.ScenicTopologyPathService;
import com.huohuo.scenicaiguide.scenic.service.ScenicTopologyService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

@Component
public class ScenicGuideToolFactory {

    private static final Set<String> GENERIC_SPOT_WORDS = Set.of("景点", "附近", "有什么", "全部", "推荐", "哪里好玩");
    private static final Set<String> PATH_INTENT_WORDS = Set.of(
        "怎么走", "怎么去", "前往", "导航", "路线", "距离", "多远", "在哪", "到");

    private final ScenicTopologyService topologyService;
    private final ScenicTopologyPathService pathService;
    private final QaGuideAnswerService answerService;
    private final AiRouteService aiRouteService;
    private final RouteWeightProvider weightProvider;
    private final ScenicTagService tagService;
    private final RoutePreferenceProvider preferenceProvider;

    @Autowired
    public ScenicGuideToolFactory(ScenicTopologyService topologyService,
                                  ScenicTopologyPathService pathService,
                                  QaGuideAnswerService answerService,
                                  AiRouteService aiRouteService,
                                  RouteWeightProvider weightProvider,
                                  ScenicTagService tagService,
                                  RoutePreferenceProvider preferenceProvider) {
        this.topologyService = topologyService;
        this.pathService = pathService;
        this.answerService = answerService;
        this.aiRouteService = aiRouteService;
        this.weightProvider = weightProvider;
        this.tagService = tagService;
        this.preferenceProvider = preferenceProvider;
    }

    public ScenicGuideToolFactory(ScenicTopologyService topologyService,
                                  ScenicTopologyPathService pathService,
                                  QaGuideAnswerService answerService,
                                  AiRouteService aiRouteService,
                                  RouteWeightProvider weightProvider,
                                  ScenicTagService tagService) {
        this(topologyService, pathService, answerService, aiRouteService, weightProvider, tagService, null);
    }

    public ScenicGuideToolFactory(ScenicTopologyService topologyService,
                                  ScenicTopologyPathService pathService,
                                  QaGuideAnswerService answerService,
                                  AiRouteService aiRouteService,
                                  RouteWeightProvider weightProvider) {
        this(topologyService, pathService, answerService, aiRouteService, weightProvider, null, null);
    }

    public ScenicGuideToolFactory(ScenicTopologyService topologyService,
                                  ScenicTopologyPathService pathService,
                                  QaGuideAnswerService answerService,
                                  AiRouteService aiRouteService) {
        this(topologyService, pathService, answerService, aiRouteService, null, null);
    }

    public ScenicGuideToolFactory(ScenicTopologyService topologyService,
                                  ScenicTopologyPathService pathService,
                                  QaGuideAnswerService answerService) {
        this(topologyService, pathService, answerService, null, null, null);
    }

    public ScenicGuideTools create(Long scenicAreaId, String currentSpotName) {
        return create(AgentContext.publicContext(scenicAreaId, currentSpotName));
    }

    public ScenicGuideTools create(AgentContext context) {
        return create(context, ignored -> { });
    }

    public ScenicGuideTools create(AgentContext context, Consumer<String> progressListener) {
        return new ScenicGuideTools(context, topologyService, pathService, answerService,
            aiRouteService, weightProvider, tagService, preferenceProvider, progressListener);
    }

    public static final class ScenicGuideTools {

        private final AgentContext context;
        private final ScenicTopologyService topologyService;
        private final ScenicTopologyPathService pathService;
        private final QaGuideAnswerService answerService;
        private final AiRouteService aiRouteService;
        private final RouteWeightProvider weightProvider;
        private final ScenicTagService tagService;
        private final RoutePreferenceProvider preferenceProvider;
        private final Consumer<String> progressListener;
        private AiRouteResponse recommendedRoute;
        private AiRoutePlanRequest routePlan;
        private PathVisualization pathVisualization;

        private ScenicGuideTools(AgentContext context,
                                 ScenicTopologyService topologyService,
                                 ScenicTopologyPathService pathService,
                                 QaGuideAnswerService answerService,
                                 AiRouteService aiRouteService,
                                 RouteWeightProvider weightProvider,
                                 ScenicTagService tagService,
                                 RoutePreferenceProvider preferenceProvider,
                                 Consumer<String> progressListener) {
            this.context = context;
            this.topologyService = topologyService;
            this.pathService = pathService;
            this.answerService = answerService;
            this.aiRouteService = aiRouteService;
            this.weightProvider = weightProvider;
            this.tagService = tagService;
            this.preferenceProvider = preferenceProvider;
            this.progressListener = progressListener;
        }

        /**
         * 启用标签的 code → 名称映射。词表服务未注入或读库失败时降级为空，
         * agent 不能因为词表故障而整体不可用
         */
        private Map<String, String> tagNames() {
            if (tagService == null) {
                return Map.of();
            }
            try {
                return tagService.enabledCodeToName();
            } catch (Exception ignored) {
                return Map.of();
            }
        }

        private List<String> resolveTagNames(MapWorkbenchDraftRequest.SpotDraft spot,
                                             Map<String, String> names) {
            if (spot.getTags() == null || spot.getTags().isEmpty()) {
                return List.of();
            }
            return spot.getTags().stream()
                .map(names::get)
                .filter(Objects::nonNull)
                .toList();
        }

        /**
         * 取配置权重，未注入提供者时回落纯距离
         */
        private ScenicTopologyPathService.RouteWeights weights() {
            return weightProvider == null
                ? ScenicTopologyPathService.RouteWeights.distanceOnly()
                : weightProvider.currentWeights();
        }

        @Tool(description = "获取当前景区已发布的完整拓扑。规划游览路线前必须先调用，使用节点key和路线编号选择有序路线")
        public ScenicMapContext inspectScenicMap() {
            reportProgress("正在读取景区地图");
            ScenicTopologyService.PublishedTopology topology = topology();
            Map<String, String> tagNames = tagNames();
            List<MapNodeInfo> nodes = new java.util.ArrayList<>();
            nodes.addAll(topologyService.enabledSpots(topology).stream()
                .map(spot -> new MapNodeInfo(
                    pathService.spotKey(spot.getId()), spot.getName(), "SPOT", spot.getDescription(),
                    resolveTagNames(spot, tagNames)))
                .toList());
            nodes.addAll(topology.payload().getRouteEndpoints().stream()
                .filter(Objects::nonNull)
                .filter(endpoint -> endpoint.getStatus() == null || endpoint.getStatus() == 1)
                .map(endpoint -> new MapNodeInfo(
                    endpointKey(endpoint), endpoint.getName(), "ENDPOINT", endpoint.getEndpointType(),
                    List.of()))
                .toList());
            List<MapConnectionInfo> connections = topology.payload().getRouteSegments().stream()
                .filter(Objects::nonNull)
                .filter(route -> route.getStatus() == null || route.getStatus() == 1)
                .map(route -> new MapConnectionInfo(
                    route.getId(), route.getName(), pathService.endpointKey(route.getFrom()),
                    endpointName(topology, route.getFrom()), pathService.endpointKey(route.getTo()),
                    endpointName(topology, route.getTo()), route.getLengthMeter(), route.getDurationMin(),
                    route.getRouteType(), route.getPaid(), route.getAiNote(), route.getCrowdLevel()))
                .toList();
            List<PresetRouteInfo> presets = listPresetRoutes("");
            return new ScenicMapContext(
                topology.scenicAreaId(), context.currentSpotId() == null ? null : pathService.spotKey(context.currentSpotId()),
                context.currentSpotName(), List.copyOf(nodes), connections, presets,
                planningInstruction());
        }

        /**
         * 路线规划指令。配置了偏好标签时追加软提示——偏好影响选线倾向，
         * 但不构成硬约束，游客明确指定的目的地永远优先
         */
        private String planningInstruction() {
            String base = "先确定起点key和终点key，再按顺序选择首尾连续的路线编号，最后调用createTourRoute";
            if (preferenceProvider == null) {
                return base;
            }
            List<String> preferred = preferenceProvider.preferredTagNames();
            if (preferred.isEmpty()) {
                return base;
            }
            return base + "；景区希望优先推荐带有这些标签的景点：" + String.join("、", preferred)
                + "，在满足游客需求的前提下优先途经";
        }

        @Tool(description = "查询当前景区已发布地图中的景点和服务设施。回答景点、厕所、餐饮、游客中心、停车等问题前应先调用")
        public ResourceSearchResult searchScenicResources(
            @ToolParam(description = "游客要查找的景点名、设施类型或自然语言关键词") String query) {
            ScenicTopologyService.PublishedTopology topology = topology();
            String keyword = normalize(query);
            boolean listAllSpots = keyword.isBlank() || GENERIC_SPOT_WORDS.stream().anyMatch(keyword::contains);
            Map<String, String> tagNames = tagNames();
            List<SpotInfo> spots = topologyService.enabledSpots(topology).stream()
                .filter(spot -> listAllSpots || matches(keyword, spot.getName(), spot.getDescription()))
                .limit(12)
                .map(spot -> new SpotInfo(spot.getId(), spot.getName(), spot.getDescription(),
                    resolveTagNames(spot, tagNames)))
                .toList();
            List<FacilityInfo> facilities = topology.payload().getFacilities().stream()
                .filter(Objects::nonNull)
                .filter(facility -> facility.getStatus() == null || facility.getStatus() == 1)
                .filter(facility -> keyword.isBlank()
                    || matches(keyword, facility.getName(), facility.getFacilityType(), facility.getDescription()))
                .limit(12)
                .map(facility -> new FacilityInfo(
                    facility.getId(), facility.getName(), facility.getFacilityType(), facility.getDescription()))
                .toList();
            String message = spots.isEmpty() && facilities.isEmpty()
                ? "当前已发布地图中没有匹配的景点或设施"
                : "以下结果来自当前景区已发布地图";
            return new ResourceSearchResult(spots, facilities, message);
        }

        @Tool(description = "检索当前景区已经确认的 FAQ、知识库和讲解词。回答历史故事、开放信息或景点介绍前必须调用")
        public KnowledgeResult searchScenicKnowledge(
            @ToolParam(description = "需要检索的完整游客问题") String question) {
            QaGuideAnswerService.AnswerResult result = answerService.preview(
                context.scenicAreaId(), safeText(question), context.currentSpotName());
            boolean confirmed = !"SAFE_FALLBACK".equals(result.sourceType());
            return new KnowledgeResult(confirmed, result.answer(), result.evidence());
        }

        @Tool(description = "列出当前景区已发布的预置路线，用于比较路线时长、适合人群和途经景点")
        public List<PresetRouteInfo> listPresetRoutes(
            @ToolParam(description = "游客偏好，例如轻松、亲子、历史，可为空") String preference) {
            ScenicTopologyService.PublishedTopology topology = topology();
            String keyword = normalize(preference);
            List<PresetRouteInfo> all = topology.payload().getPresetRoutes().stream()
                .filter(Objects::nonNull)
                .filter(route -> route.getStatus() == null || route.getStatus() == 1)
                .map(route -> new PresetRouteInfo(
                    route.getId(), route.getName(), route.getDurationMin(), route.getAudience(),
                    route.getRecommendationNote(), spotNames(topology, route.getSpotIds())))
                .toList();
            if (keyword.isBlank()) {
                return all.stream().limit(8).toList();
            }
            List<PresetRouteInfo> matched = all.stream()
                .filter(route -> matches(keyword, route.name(), route.audience(), route.recommendationNote()))
                .limit(8)
                .toList();
            return matched.isEmpty() ? all.stream().limit(8).toList() : matched;
        }

        @Tool(description = "严格按起点key、终点key和有序路线编号校验并生成游览路线。失败时根据errorCode和message修正方案后再次调用，成功前不得输出最终路线")
        public RoutePlanToolResult createTourRoute(
            @ToolParam(description = "根据inspectScenicMap结果生成的明确路线方案，路线编号顺序必须首尾连续")
            RoutePlanToolRequest request) {
            reportProgress("正在校验并生成游览路线");
            if (!context.routePlanningAllowed() || context.sessionId() == null) {
                return RoutePlanToolResult.failure("FORBIDDEN", "当前身份不能生成专属路线，请先登录");
            }
            if (request == null) {
                return RoutePlanToolResult.failure("PARAM_INVALID", "路线方案不能为空");
            }
            AiRoutePlanRequest plan = new AiRoutePlanRequest();
            plan.setRouteName(safeText(request.routeName()));
            plan.setStartNodeKey(safeText(request.startNodeKey()));
            plan.setEndNodeKey(safeText(request.endNodeKey()));
            plan.setOrderedSegmentIds(request.orderedSegmentIds() == null ? List.of() : request.orderedSegmentIds());
            plan.setTargetDurationMinutes(request.targetDurationMinutes());
            plan.setReason(safeText(request.reason()));
            plan.setAlternative(Boolean.TRUE.equals(request.alternative()) && context.currentRouteId() != null);
            try {
                recommendedRoute = aiRouteService.createFromAgentPlan(
                    context.sessionId(), context.scenicAreaId(), plan, context.currentRouteId());
                routePlan = plan;
                reportProgress("路线已生成，正在整理结果");
                return RoutePlanToolResult.success(recommendedRoute);
            } catch (BusinessException error) {
                recommendedRoute = null;
                routePlan = null;
                reportProgress("路线方案未通过校验，正在调整");
                return RoutePlanToolResult.failure(error.getCode(), error.getMessage());
            }
        }

        private void reportProgress(String message) {
            progressListener.accept(message);
        }

        @Tool(description = "使用当前景区已发布路网计算两点之间的最短可达路径，终点可以是景点或服务设施（洗手间、餐饮、游客中心等）。不得根据直线距离猜测路线")
        public PathToolResult findShortestPath(
            @ToolParam(description = "起点景点名称，游客说当前位置时使用当前位置") String fromSpotName,
            @ToolParam(description = "终点景点或服务设施名称") String toSpotName) {
            ScenicTopologyService.PublishedTopology topology = topology();
            MapWorkbenchDraftRequest.SpotDraft from = resolveSpot(topology, fromSpotName, true);
            ResolvedTarget to = resolveDestination(topology, toSpotName);
            if (from == null || to == null) {
                return new PathToolResult(false, 0, 0, List.of(), List.of(),
                    from == null ? "没有找到起点景点" : "没有找到终点景点或设施");
            }
            ScenicTopologyPathService.PathResult path = pathService.findShortestPath(
                topology.payload(), pathService.spotKey(from.getId()), to.key(), weights());
            if (!path.reachable()) {
                return new PathToolResult(false, 0, 0, List.of(from.getName(), to.name()), List.of(),
                    "当前已发布路网中两地点不可达");
            }
            List<String> nodes = new java.util.ArrayList<>(path.nodeKeys().stream()
                .map(key -> nodeName(topology, key))
                .toList());
            List<String> segments = path.routeIds().stream()
                .map(id -> routeName(topology, id))
                .toList();
            int totalLength = path.totalLengthMeter();
            int totalDuration = path.totalDurationMin();
            String message = "路径来自当前景区已发布路网";
            if (to.facility()) {
                // 设施不是路网节点，算路到最近节点后补一段直线步行
                nodes.add(to.name());
                totalLength += to.tailMeters();
                totalDuration += (int) Math.ceil(to.tailMeters() / 75D);
                message = "设施不在路网节点上，先按路网到最近的「" + nodeName(topology, to.key())
                    + "」，再步行约" + to.tailMeters() + "米";
            }
            pathVisualization = buildPathVisualization(topology, from, to, path);
            return new PathToolResult(true, totalLength, totalDuration, nodes, segments, message);
        }

        public Optional<PathVisualization> resolvePathVisualization(String question) {
            String normalizedQuestion = normalize(question);
            if (PATH_INTENT_WORDS.stream().noneMatch(normalizedQuestion::contains)) {
                return Optional.empty();
            }
            ScenicTopologyService.PublishedTopology topology = topology();
            List<ResolvedTarget> mentioned = mentionedTargets(topology, normalizedQuestion);
            MapWorkbenchDraftRequest.SpotDraft from = null;
            ResolvedTarget to = null;
            for (ResolvedTarget target : mentioned) {
                if (from == null && target.spot() != null) {
                    from = target.spot();
                    continue;
                }
                to = target;
                break;
            }
            if (to == null) {
                return Optional.empty();
            }
            if (from == null) {
                from = resolveSpot(topology, context.currentSpotName(), false);
            }
            if (from == null || Objects.equals(pathService.spotKey(from.getId()), to.key())) {
                return Optional.empty();
            }
            ScenicTopologyPathService.PathResult path = pathService.findShortestPath(
                topology.payload(), pathService.spotKey(from.getId()), to.key(), weights());
            if (!path.reachable()) {
                return Optional.empty();
            }
            pathVisualization = buildPathVisualization(topology, from, to, path);
            return Optional.ofNullable(pathVisualization);
        }

        private ScenicTopologyService.PublishedTopology topology() {
            return topologyService.getPublishedTopology(context.scenicAreaId());
        }

        /**
         * 游客口语与后台设施类型的同义词。词表很短，靠它把"厕所"接到"卫生间"上
         */
        private static final Map<String, String> FACILITY_ALIASES = Map.of(
            "洗手间", "卫生间",
            "厕所", "卫生间",
            "wc", "卫生间",
            "餐厅", "餐饮",
            "吃饭", "餐饮");

        /**
         * 解析目的地：先按景点精确/模糊匹配，再按设施名称、类型或口语别名匹配。
         * 设施不在路网里，解析结果是"最近路网节点 + 尾段直线距离"
         */
        private ResolvedTarget resolveDestination(
            ScenicTopologyService.PublishedTopology topology, String name) {
            MapWorkbenchDraftRequest.SpotDraft spot = resolveSpot(topology, name, false);
            if (spot != null) {
                return new ResolvedTarget(pathService.spotKey(spot.getId()), spot.getName(), false,
                    coordinate(spot.getLabelPoint(), spot.getLatitude(), spot.getLongitude()), 0, spot);
            }
            String normalized = normalize(name);
            if (normalized.isBlank()) {
                return null;
            }
            MapWorkbenchDraftRequest.FacilityDraft facility = topology.payload().getFacilities().stream()
                .filter(Objects::nonNull)
                .filter(item -> item.getStatus() == null || item.getStatus() == 1)
                .filter(item -> facilityMatches(normalized, item))
                .findFirst()
                .orElse(null);
            if (facility == null) {
                return null;
            }
            Coordinate target = coordinate(facility.getPoint(), facility.getLatitude(), facility.getLongitude());
            if (target == null) {
                return null;
            }
            NearestNode nearest = nearestGraphNode(topology, target);
            if (nearest == null) {
                return null;
            }
            return new ResolvedTarget(nearest.key(), facility.getName(), true, target,
                nearest.distanceMeters(), null);
        }

        private boolean facilityMatches(String normalized, MapWorkbenchDraftRequest.FacilityDraft facility) {
            String name = normalize(facility.getName());
            if (!name.isBlank() && (name.equals(normalized) || name.contains(normalized))) {
                return true;
            }
            String type = normalize(facility.getFacilityType());
            if (!type.isBlank() && (type.equals(normalized) || type.contains(normalized)
                || normalized.contains(type))) {
                return true;
            }
            String alias = FACILITY_ALIASES.get(normalized);
            return alias != null && type.contains(normalize(alias));
        }

        /**
         * 找离给定坐标最近的路网节点（景点标注点或路线端点）
         */
        private NearestNode nearestGraphNode(
            ScenicTopologyService.PublishedTopology topology, Coordinate target) {
            NearestNode nearest = null;
            for (MapWorkbenchDraftRequest.SpotDraft spot : topologyService.enabledSpots(topology)) {
                Coordinate point = coordinate(spot.getLabelPoint(), spot.getLatitude(), spot.getLongitude());
                if (point == null) {
                    continue;
                }
                int distance = distanceMeters(target, point);
                if (nearest == null || distance < nearest.distanceMeters()) {
                    nearest = new NearestNode(pathService.spotKey(spot.getId()), distance);
                }
            }
            for (MapWorkbenchDraftRequest.EndpointDraft endpoint : topology.payload().getRouteEndpoints()) {
                if (endpoint == null || endpoint.getStatus() != null && endpoint.getStatus() == 0) {
                    continue;
                }
                Coordinate point = coordinate(endpoint.getPoint(), endpoint.getLatitude(), endpoint.getLongitude());
                if (point == null) {
                    continue;
                }
                MapWorkbenchDraftRequest.EndpointRefDraft ref = new MapWorkbenchDraftRequest.EndpointRefDraft();
                ref.setType("ENDPOINT");
                ref.setId(endpoint.getId());
                int distance = distanceMeters(target, point);
                if (nearest == null || distance < nearest.distanceMeters()) {
                    nearest = new NearestNode(pathService.endpointKey(ref), distance);
                }
            }
            return nearest;
        }

        private int distanceMeters(Coordinate from, Coordinate to) {
            return (int) Math.round(topologyService.distanceMeters(
                from.latitude(), from.longitude(), to.latitude(), to.longitude()));
        }

        /**
         * 问题里按出现顺序提到的景点与设施
         */
        private List<ResolvedTarget> mentionedTargets(
            ScenicTopologyService.PublishedTopology topology, String normalizedQuestion) {
            List<ResolvedTarget> targets = new java.util.ArrayList<>();
            for (MapWorkbenchDraftRequest.SpotDraft spot : topologyService.enabledSpots(topology)) {
                String name = normalize(spot.getName());
                if (!name.isBlank() && normalizedQuestion.contains(name)) {
                    targets.add(new ResolvedTarget(pathService.spotKey(spot.getId()), spot.getName(), false,
                        coordinate(spot.getLabelPoint(), spot.getLatitude(), spot.getLongitude()), 0, spot));
                }
            }
            for (MapWorkbenchDraftRequest.FacilityDraft facility : topology.payload().getFacilities()) {
                if (facility == null || facility.getStatus() != null && facility.getStatus() == 0) {
                    continue;
                }
                boolean mentioned = Stream.concat(
                        Stream.of(normalize(facility.getName()), normalize(facility.getFacilityType())),
                        FACILITY_ALIASES.entrySet().stream()
                            .filter(entry -> entry.getValue().equals(normalize(facility.getFacilityType())))
                            .map(Map.Entry::getKey))
                    .anyMatch(candidate -> !candidate.isBlank() && normalizedQuestion.contains(candidate));
                if (mentioned) {
                    ResolvedTarget target = resolveDestination(topology, facility.getName());
                    if (target != null) {
                        targets.add(target);
                    }
                }
            }
            targets.sort(Comparator.comparingInt(target -> mentionIndex(normalizedQuestion, target)));
            return targets;
        }

        private int mentionIndex(String question, ResolvedTarget target) {
            int index = question.indexOf(normalize(target.name()));
            return index >= 0 ? index : Integer.MAX_VALUE;
        }

        private MapWorkbenchDraftRequest.SpotDraft resolveSpot(
            ScenicTopologyService.PublishedTopology topology, String value, boolean allowCurrent) {
            String name = safeText(value);
            if (allowCurrent && (name.isBlank() || name.contains("当前位置") || name.equals("这里"))) {
                name = safeText(context.currentSpotName());
            }
            String normalized = normalize(name);
            if (normalized.isBlank()) {
                return null;
            }
            return topologyService.enabledSpots(topology).stream()
                .filter(spot -> normalize(spot.getName()).equals(normalized))
                .findFirst()
                .orElseGet(() -> topologyService.enabledSpots(topology).stream()
                    .filter(spot -> normalize(spot.getName()).contains(normalized)
                        || normalized.contains(normalize(spot.getName())))
                    .findFirst()
                    .orElse(null));
        }

        private List<String> spotNames(ScenicTopologyService.PublishedTopology topology, List<String> spotIds) {
            if (spotIds == null) {
                return List.of();
            }
            return spotIds.stream().map(id -> topology.payload().getSpots().stream()
                    .filter(spot -> Objects.equals(spot.getId(), id))
                    .map(MapWorkbenchDraftRequest.SpotDraft::getName)
                    .findFirst()
                    .orElse("未知景点"))
                .toList();
        }

        private String nodeName(ScenicTopologyService.PublishedTopology topology, String key) {
            if (key != null && key.startsWith("SPOT:")) {
                String id = key.substring("SPOT:".length());
                return topology.payload().getSpots().stream()
                    .filter(spot -> Objects.equals(spot.getId(), id))
                    .map(MapWorkbenchDraftRequest.SpotDraft::getName)
                    .findFirst()
                    .orElse(key);
            }
            return topology.payload().getRouteEndpoints().stream()
                .filter(endpoint -> pathService.endpointKey(toRef(endpoint)).equals(key))
                .map(MapWorkbenchDraftRequest.EndpointDraft::getName)
                .findFirst()
                .orElse(key);
        }

        private Coordinate coordinate(List<BigDecimal> point, BigDecimal latitude, BigDecimal longitude) {
            if (point != null && point.size() >= 2 && point.get(0) != null && point.get(1) != null) {
                return new Coordinate(point.get(0), point.get(1));
            }
            return latitude == null || longitude == null ? null : new Coordinate(latitude, longitude);
        }

        private MapWorkbenchDraftRequest.EndpointRefDraft toRef(MapWorkbenchDraftRequest.EndpointDraft endpoint) {
            MapWorkbenchDraftRequest.EndpointRefDraft ref = new MapWorkbenchDraftRequest.EndpointRefDraft();
            ref.setType(endpoint.getEndpointType());
            ref.setId(endpoint.getId());
            return ref;
        }

        private String endpointKey(MapWorkbenchDraftRequest.EndpointDraft endpoint) {
            MapWorkbenchDraftRequest.EndpointRefDraft ref = new MapWorkbenchDraftRequest.EndpointRefDraft();
            ref.setType("ENDPOINT");
            ref.setId(endpoint.getId());
            return pathService.endpointKey(ref);
        }

        private String routeName(ScenicTopologyService.PublishedTopology topology, String routeId) {
            return topology.payload().getRouteSegments().stream()
                .filter(route -> Objects.equals(route.getId(), routeId))
                .map(route -> route.getName() == null || route.getName().isBlank() ? routeId : route.getName())
                .findFirst()
                .orElse(routeId);
        }

        public AiRouteResponse recommendedRoute() {
            return recommendedRoute;
        }

        public AiRoutePlanRequest routePlan() {
            return routePlan;
        }

        public PathVisualization pathVisualization() {
            return pathVisualization;
        }

        private PathVisualization buildPathVisualization(
            ScenicTopologyService.PublishedTopology topology,
            MapWorkbenchDraftRequest.SpotDraft from,
            ResolvedTarget to,
            ScenicTopologyPathService.PathResult path) {
            Coordinate fromCoordinate = coordinate(from.getLabelPoint(), from.getLatitude(), from.getLongitude());
            Coordinate endCoordinate = to.facility()
                ? nodeCoordinate(topology, to.key())
                : to.coordinate();
            if (fromCoordinate == null || endCoordinate == null || to.facility() && to.coordinate() == null) {
                return null;
            }
            List<PathNode> nodes = new java.util.ArrayList<>(List.of(
                new PathNode(pathService.spotKey(from.getId()), from.getName(), fromCoordinate.latitude(),
                    fromCoordinate.longitude(), 1),
                new PathNode(to.key(), to.facility() ? nodeName(topology, to.key()) : to.name(),
                    endCoordinate.latitude(), endCoordinate.longitude(), 2)));
            List<PathPoint> points = new java.util.ArrayList<>(pathService.pathGeometry(topology.payload(), path).stream()
                .map(point -> new PathPoint(point.routeId(), point.latitude(), point.longitude(), point.sequence()))
                .toList());
            int totalLength = path.totalLengthMeter();
            int totalDuration = path.totalDurationMin();
            if (to.facility()) {
                // 尾段直线不进路线编号，只补终点节点和几何点，让前端把线画到设施位置
                nodes.add(new PathNode("FACILITY:" + to.name(), to.name(), to.coordinate().latitude(),
                    to.coordinate().longitude(), nodes.size() + 1));
                int nextSequence = points.stream().mapToInt(PathPoint::sequence).max().orElse(0) + 1;
                points.add(new PathPoint(null, to.coordinate().latitude(), to.coordinate().longitude(), nextSequence));
                totalLength += to.tailMeters();
                totalDuration += (int) Math.ceil(to.tailMeters() / 75D);
            }
            return new PathVisualization(from.getName(), to.name(), totalLength, totalDuration,
                List.copyOf(nodes), path.routeIds(), List.copyOf(points));
        }

        /**
         * 路网节点（景点或路线端点）的坐标
         */
        private Coordinate nodeCoordinate(ScenicTopologyService.PublishedTopology topology, String key) {
            if (key != null && key.startsWith("SPOT:")) {
                String id = key.substring("SPOT:".length());
                return topology.payload().getSpots().stream()
                    .filter(spot -> Objects.equals(spot.getId(), id))
                    .map(spot -> coordinate(spot.getLabelPoint(), spot.getLatitude(), spot.getLongitude()))
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
            }
            return topology.payload().getRouteEndpoints().stream()
                .filter(endpoint -> endpointKey(endpoint).equals(key))
                .map(endpoint -> coordinate(endpoint.getPoint(), endpoint.getLatitude(), endpoint.getLongitude()))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        }

        private String endpointName(ScenicTopologyService.PublishedTopology topology,
                                    MapWorkbenchDraftRequest.EndpointRefDraft ref) {
            if (ref == null) {
                return "未知节点";
            }
            if (ref.getName() != null && !ref.getName().isBlank()) {
                return ref.getName();
            }
            if ("SPOT".equalsIgnoreCase(ref.getType())) {
                return topology.payload().getSpots().stream()
                    .filter(spot -> Objects.equals(spot.getId(), ref.getId()))
                    .map(MapWorkbenchDraftRequest.SpotDraft::getName)
                    .findFirst()
                    .orElse(ref.getId());
            }
            return topology.payload().getRouteEndpoints().stream()
                .filter(endpoint -> Objects.equals(endpoint.getId(), ref.getId()))
                .map(MapWorkbenchDraftRequest.EndpointDraft::getName)
                .findFirst()
                .orElse(ref.getId());
        }

        private boolean matches(String keyword, String... values) {
            for (String value : values) {
                String normalized = normalize(value);
                if (!normalized.isBlank() && (normalized.contains(keyword) || keyword.contains(normalized))) {
                    return true;
                }
            }
            return false;
        }

        private String normalize(String value) {
            return safeText(value).replaceAll("[\\s，。！？、,.;；：:]", "").toLowerCase(Locale.ROOT);
        }

        private String safeText(String value) {
            return value == null ? "" : value.trim();
        }

        private record Coordinate(BigDecimal latitude, BigDecimal longitude) {
        }

        /**
         * 算路目的地。景点本身在路网里；设施解析为最近路网节点加尾段直线距离
         */
        private record ResolvedTarget(String key, String name, boolean facility,
                                      Coordinate coordinate, int tailMeters,
                                      MapWorkbenchDraftRequest.SpotDraft spot) {
        }

        private record NearestNode(String key, int distanceMeters) {
        }
    }

    public record ResourceSearchResult(List<SpotInfo> spots,
                                       List<FacilityInfo> facilities,
                                       String message) {
    }

    public record SpotInfo(String id, String name, String description, List<String> tags) {
    }

    public record FacilityInfo(String id, String name, String type, String description) {
    }

    public record KnowledgeResult(boolean confirmed, String content, String evidence) {
    }

    public record PresetRouteInfo(String id,
                                  String name,
                                  Integer durationMin,
                                  String audience,
                                  String recommendationNote,
                                  List<String> spots) {
    }

    public record PathToolResult(boolean reachable,
                                 int totalLengthMeter,
                                 int totalDurationMin,
                                 List<String> nodes,
                                 List<String> routeSegments,
                                 String message) {
    }

    public record PathVisualization(String fromName,
                                    String toName,
                                    int totalLengthMeter,
                                    int totalDurationMin,
                                    List<PathNode> nodes,
                                    List<String> routeSegmentIds,
                                    List<PathPoint> pathPoints) {

        public PathVisualization(String fromName,
                                 String toName,
                                 int totalLengthMeter,
                                 List<PathNode> nodes,
                                 List<String> routeSegmentIds) {
            this(fromName, toName, totalLengthMeter, walkMinutes(totalLengthMeter),
                nodes, routeSegmentIds, List.of());
        }

        private static int walkMinutes(int totalLengthMeter) {
            return totalLengthMeter <= 0 ? 0 : (int) Math.ceil(totalLengthMeter / 70D);
        }
    }

    public record PathNode(String id,
                           String name,
                           BigDecimal latitude,
                           BigDecimal longitude,
                           int sequence) {
    }

    public record PathPoint(String segmentId,
                            BigDecimal latitude,
                            BigDecimal longitude,
                            int sequence) {
    }

    public record ScenicMapContext(Long scenicAreaId,
                                   String currentNodeKey,
                                   String currentSpotName,
                                   List<MapNodeInfo> nodes,
                                   List<MapConnectionInfo> connections,
                                   List<PresetRouteInfo> presetRoutes,
                                   String instruction) {
    }

    public record MapNodeInfo(String key,
                              String name,
                              String type,
                              String description,
                              List<String> tags) {
    }

    public record MapConnectionInfo(String id,
                                    String name,
                                    String fromKey,
                                    String fromName,
                                    String toKey,
                                    String toName,
                                    Integer lengthMeter,
                                    Integer durationMinutes,
                                    String routeType,
                                    Boolean paid,
                                    String aiNote,
                                    Integer crowdLevel) {
    }

    public record RoutePlanToolRequest(String routeName,
                                       String startNodeKey,
                                       String endNodeKey,
                                       List<String> orderedSegmentIds,
                                       Integer targetDurationMinutes,
                                       String reason,
                                       Boolean alternative) {
    }

    public record RoutePlanToolResult(boolean success,
                                      String errorCode,
                                      String message,
                                      Long routeId,
                                      String routeName,
                                      int totalDistanceMeter,
                                      int estimatedDurationMin,
                                      List<String> nodes,
                                      List<String> segmentIds) {

        private static RoutePlanToolResult success(AiRouteResponse route) {
            return new RoutePlanToolResult(
                true, null, route.getRecommendationReason(), route.getId(), route.getName(),
                route.getTotalDistanceMeter() == null ? 0 : route.getTotalDistanceMeter(),
                route.getEstimatedDurationMin() == null ? 0 : route.getEstimatedDurationMin(),
                route.getNodes().stream().map(AiRouteResponse.RouteNode::getName).toList(),
                route.getRouteSegmentIds());
        }

        private static RoutePlanToolResult failure(String errorCode, String message) {
            return new RoutePlanToolResult(
                false, errorCode, message, null, null, 0, 0, List.of(), List.of());
        }
    }

    public record AgentContext(Long sessionId,
                               Long scenicAreaId,
                               String currentSpotName,
                               String currentSpotId,
                               Long currentRouteId,
                               boolean routePlanningAllowed) {

        public static AgentContext publicContext(Long scenicAreaId, String currentSpotName) {
            return new AgentContext(null, scenicAreaId, currentSpotName, null, null, false);
        }
    }
}
