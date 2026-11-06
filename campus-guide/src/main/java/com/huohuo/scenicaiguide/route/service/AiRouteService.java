package com.huohuo.scenicaiguide.route.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huohuo.scenicaiguide.common.enums.ErrorCode;
import com.huohuo.scenicaiguide.common.exception.BusinessException;
import com.huohuo.scenicaiguide.route.dto.AiRoutePlanRequest;
import com.huohuo.scenicaiguide.route.dto.AiRouteResponse;
import com.huohuo.scenicaiguide.route.entity.RouteAiRoute;
import com.huohuo.scenicaiguide.route.mapper.RouteAiRouteMapper;
import com.huohuo.scenicaiguide.scenic.dto.request.MapWorkbenchDraftRequest;
import com.huohuo.scenicaiguide.scenic.service.ScenicTopologyPathService;
import com.huohuo.scenicaiguide.scenic.service.ScenicTopologyService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class AiRouteService {

    private static final int MAX_SEGMENTS = 40;
    private static final int DEFAULT_VISIT_MINUTES = 12;

    private final ScenicTopologyService topologyService;
    private final ScenicTopologyPathService pathService;
    private final RouteAiRouteMapper aiRouteMapper;
    private final ObjectMapper objectMapper;

    public AiRouteService(ScenicTopologyService topologyService,
                          ScenicTopologyPathService pathService,
                          RouteAiRouteMapper aiRouteMapper,
                          ObjectMapper objectMapper) {
        this.topologyService = topologyService;
        this.pathService = pathService;
        this.aiRouteMapper = aiRouteMapper;
        this.objectMapper = objectMapper;
    }

    public AiRouteResponse createFromAgentPlan(Long sessionId,
                                               Long scenicAreaId,
                                               AiRoutePlanRequest request,
                                               Long currentRouteId) {
        ScenicTopologyService.PublishedTopology topology = topologyService.getPublishedTopology(scenicAreaId);
        AiRoutePlanRequest plan = requirePlan(request);
        Map<String, NodeRef> nodes = mapNodes(topology.payload());
        Map<String, MapWorkbenchDraftRequest.RouteDraft> segments = mapSegments(topology.payload());
        String startKey = normalizeNodeKey(plan.getStartNodeKey());
        String endKey = normalizeNodeKey(plan.getEndNodeKey());
        NodeRef start = requireNode(nodes, startKey, "起点");
        NodeRef end = requireNode(nodes, endKey, "终点");
        List<String> segmentIds = safeSegmentIds(plan.getOrderedSegmentIds());
        if (Boolean.TRUE.equals(plan.getAlternative())
            && segmentIds.equals(currentRouteSegmentIds(currentRouteId))) {
            throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED, "新路线不能与当前路线完全相同");
        }

        RouteTraversal traversal = traverse(start, end, segmentIds, segments, nodes);
        int estimatedDuration = estimateDuration(traversal);
        Integer targetDuration = plan.getTargetDurationMinutes();
        if (targetDuration != null && targetDuration > 0 && estimatedDuration > targetDuration) {
            throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED,
                "路线预计" + estimatedDuration + "分钟，超过目标" + targetDuration + "分钟");
        }
        return saveRoute(sessionId, topology, plan, traversal, estimatedDuration);
    }

    /**
     * 读取已保存的 AI 路线（节点 + 路段），供导览推进与详情展示
     * 路线不存在或快照损坏时返回 null，调用方按"没有路线"降级
     */
    public AiRouteResponse loadRoute(Long routeId) {
        if (routeId == null) {
            return null;
        }
        RouteAiRoute entity = aiRouteMapper.selectById(routeId);
        if (entity == null) {
            return null;
        }
        List<AiRouteResponse.RouteNode> nodes = readJson(
            entity.getNodeJson(), new TypeReference<List<AiRouteResponse.RouteNode>>() {});
        if (nodes == null) {
            return null;
        }
        List<String> segmentIds = readJson(entity.getSegmentJson(), new TypeReference<List<String>>() {});
        AiRouteResponse response = new AiRouteResponse();
        response.setId(entity.getId());
        response.setName(entity.getName());
        response.setRouteType(entity.getRouteType());
        response.setEstimatedDurationMin(entity.getEstimatedDurationMin());
        response.setRecommendationReason(entity.getRecommendationReason());
        response.setNodes(nodes);
        response.setRouteSegmentIds(segmentIds == null ? List.of() : segmentIds);
        return response;
    }

    private <T> T readJson(String json, TypeReference<T> type) {
        if (!hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException ignored) {
            return null;
        }
    }

    private AiRoutePlanRequest requirePlan(AiRoutePlanRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "路线方案不能为空");
        }
        return request;
    }

    private NodeRef requireNode(Map<String, NodeRef> nodes, String key, String label) {
        NodeRef node = nodes.get(key);
        if (node == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, label + "节点不存在：" + key);
        }
        return node;
    }

    private List<String> safeSegmentIds(List<String> values) {
        if (values == null || values.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "路线至少需要一条路线编号");
        }
        List<String> ids = values.stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .toList();
        if (ids.isEmpty() || ids.size() > MAX_SEGMENTS) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "路线编号数量必须在1到" + MAX_SEGMENTS + "之间");
        }
        return ids;
    }

    private RouteTraversal traverse(NodeRef start,
                                    NodeRef end,
                                    List<String> segmentIds,
                                    Map<String, MapWorkbenchDraftRequest.RouteDraft> segments,
                                    Map<String, NodeRef> nodes) {
        List<NodeRef> orderedNodes = new ArrayList<>();
        List<TraversedSegment> orderedSegments = new ArrayList<>();
        orderedNodes.add(start);
        NodeRef current = start;
        int totalDistance = 0;
        int travelMinutes = 0;
        for (String segmentId : segmentIds) {
            MapWorkbenchDraftRequest.RouteDraft segment = segments.get(segmentId);
            if (segment == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "路线编号不存在或未启用：" + segmentId);
            }
            String fromKey = pathService.endpointKey(segment.getFrom());
            String toKey = pathService.endpointKey(segment.getTo());
            String nextKey;
            if (current.key().equals(fromKey)) {
                nextKey = toKey;
            } else if (current.key().equals(toKey)) {
                nextKey = fromKey;
            } else {
                throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED,
                    "路线" + segmentId + "与当前节点" + current.name() + "不连续");
            }
            NodeRef next = nodes.get(nextKey);
            if (next == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "路线" + segmentId + "连接了不存在的节点：" + nextKey);
            }
            int length = positive(segment.getLengthMeter(), 1);
            int duration = positive(segment.getDurationMin(), Math.max(1, (int) Math.ceil(length / 75D)));
            orderedSegments.add(new TraversedSegment(segment, current.key(), next.key(), length, duration));
            orderedNodes.add(next);
            totalDistance += length;
            travelMinutes += duration;
            current = next;
        }
        if (!current.key().equals(end.key())) {
            throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED,
                "路线实际终点是" + current.name() + "，不是指定终点" + end.name());
        }
        return new RouteTraversal(List.copyOf(orderedNodes), List.copyOf(orderedSegments), totalDistance, travelMinutes);
    }

    private int estimateDuration(RouteTraversal traversal) {
        long visitedSpots = traversal.nodes().stream()
            .filter(node -> "SPOT".equals(node.type()))
            .map(NodeRef::key)
            .distinct()
            .count();
        return Math.max(1, traversal.travelMinutes() + Math.toIntExact(visitedSpots) * DEFAULT_VISIT_MINUTES);
    }

    private AiRouteResponse saveRoute(Long sessionId,
                                      ScenicTopologyService.PublishedTopology topology,
                                      AiRoutePlanRequest plan,
                                      RouteTraversal traversal,
                                      int estimatedDuration) {
        List<AiRouteResponse.RouteNode> routeNodes = toRouteNodes(traversal.nodes());
        List<String> segmentIds = traversal.segments().stream()
            .map(item -> item.segment().getId())
            .toList();
        String routeName = hasText(plan.getRouteName()) ? plan.getRouteName().trim()
            : Boolean.TRUE.equals(plan.getAlternative()) ? "AI 备选校园路线" : "AI 智能校园路线";
        String reason = hasText(plan.getReason()) ? plan.getReason().trim()
            : "路线由校园助手根据已发布路网选择，并通过连通性校验";

        RouteAiRoute entity = new RouteAiRoute();
        entity.setSessionId(sessionId);
        entity.setScenicAreaId(topology.scenicAreaId());
        entity.setSourceDraftId(topology.draftId());
        entity.setName(routeName);
        entity.setRouteType("AI_AGENT");
        entity.setPreferenceJson(writeJson(plan));
        entity.setNodeJson(writeJson(routeNodes));
        entity.setSegmentJson(writeJson(segmentIds));
        entity.setEstimatedDurationMin(estimatedDuration);
        entity.setRecommendationReason(reason + "。全程约" + traversal.totalDistance() + "米，预计"
            + estimatedDuration + "分钟");
        entity.setStatus("ACTIVE");
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setDeleted(0);
        aiRouteMapper.insert(entity);

        AiRouteResponse response = new AiRouteResponse();
        response.setId(entity.getId());
        response.setName(entity.getName());
        response.setRouteType(entity.getRouteType());
        response.setEstimatedDurationMin(estimatedDuration);
        response.setTotalDistanceMeter(traversal.totalDistance());
        response.setRecommendationReason(entity.getRecommendationReason());
        response.setNodes(routeNodes);
        response.setPoints(routeNodes.stream()
            .filter(node -> "SPOT".equals(node.getType()))
            .map(this::toPoint)
            .toList());
        response.setRouteSegmentIds(segmentIds);
        response.setPathPoints(toPathPoints(topology.payload(), traversal.segments()));
        return response;
    }

    private List<AiRouteResponse.RouteNode> toRouteNodes(List<NodeRef> nodes) {
        List<AiRouteResponse.RouteNode> result = new ArrayList<>();
        for (int index = 0; index < nodes.size(); index++) {
            NodeRef source = nodes.get(index);
            AiRouteResponse.RouteNode node = new AiRouteResponse.RouteNode();
            node.setId(source.id());
            node.setName(source.name());
            node.setType(source.type());
            node.setDescription(source.description());
            node.setLatitude(source.latitude());
            node.setLongitude(source.longitude());
            node.setSequence(index + 1);
            result.add(node);
        }
        return List.copyOf(result);
    }

    private AiRouteResponse.RoutePoint toPoint(AiRouteResponse.RouteNode node) {
        AiRouteResponse.RoutePoint point = new AiRouteResponse.RoutePoint();
        point.setSpotId(node.getId());
        point.setSpotName(node.getName());
        point.setLatitude(node.getLatitude());
        point.setLongitude(node.getLongitude());
        point.setSequence(node.getSequence());
        return point;
    }

    private List<AiRouteResponse.RoutePathPoint> toPathPoints(
        MapWorkbenchDraftRequest payload, List<TraversedSegment> segments) {
        List<AiRouteResponse.RoutePathPoint> result = new ArrayList<>();
        for (TraversedSegment traversed : segments) {
            ScenicTopologyPathService.PathResult path = new ScenicTopologyPathService.PathResult(
                true, List.of(traversed.fromKey(), traversed.toKey()),
                List.of(traversed.segment().getId()), traversed.length());
            for (ScenicTopologyPathService.PathCoordinate coordinate : pathService.pathGeometry(payload, path)) {
                if (!result.isEmpty() && samePosition(result.getLast(), coordinate)) {
                    continue;
                }
                AiRouteResponse.RoutePathPoint point = new AiRouteResponse.RoutePathPoint();
                point.setSegmentId(coordinate.routeId());
                point.setLatitude(coordinate.latitude());
                point.setLongitude(coordinate.longitude());
                point.setSequence(result.size() + 1);
                result.add(point);
            }
        }
        return List.copyOf(result);
    }

    private boolean samePosition(AiRouteResponse.RoutePathPoint left,
                                 ScenicTopologyPathService.PathCoordinate right) {
        return left.getLatitude() != null && left.getLongitude() != null
            && left.getLatitude().compareTo(right.latitude()) == 0
            && left.getLongitude().compareTo(right.longitude()) == 0;
    }

    private Map<String, NodeRef> mapNodes(MapWorkbenchDraftRequest payload) {
        Map<String, NodeRef> nodes = new LinkedHashMap<>();
        for (MapWorkbenchDraftRequest.SpotDraft spot : payload.getSpots()) {
            if (spot == null || spot.getStatus() != null && spot.getStatus() == 0 || !hasText(spot.getId())) {
                continue;
            }
            Coordinate coordinate = coordinate(spot.getLabelPoint(), spot.getLatitude(), spot.getLongitude());
            nodes.put(pathService.spotKey(spot.getId()), new NodeRef(
                pathService.spotKey(spot.getId()), spot.getId(), spot.getName(), "SPOT", spot.getDescription(),
                coordinate == null ? null : coordinate.latitude(), coordinate == null ? null : coordinate.longitude()));
        }
        for (MapWorkbenchDraftRequest.EndpointDraft endpoint : payload.getRouteEndpoints()) {
            if (endpoint == null || endpoint.getStatus() != null && endpoint.getStatus() == 0
                || !hasText(endpoint.getId())) {
                continue;
            }
            MapWorkbenchDraftRequest.EndpointRefDraft ref = new MapWorkbenchDraftRequest.EndpointRefDraft();
            ref.setType("ENDPOINT");
            ref.setId(endpoint.getId());
            Coordinate coordinate = coordinate(endpoint.getPoint(), endpoint.getLatitude(), endpoint.getLongitude());
            String key = pathService.endpointKey(ref);
            nodes.put(key, new NodeRef(key, endpoint.getId(), endpoint.getName(), "ENDPOINT", endpoint.getEndpointType(),
                coordinate == null ? null : coordinate.latitude(), coordinate == null ? null : coordinate.longitude()));
        }
        return nodes;
    }

    private Map<String, MapWorkbenchDraftRequest.RouteDraft> mapSegments(MapWorkbenchDraftRequest payload) {
        Map<String, MapWorkbenchDraftRequest.RouteDraft> segments = new LinkedHashMap<>();
        for (MapWorkbenchDraftRequest.RouteDraft segment : payload.getRouteSegments()) {
            if (segment != null && hasText(segment.getId())
                && (segment.getStatus() == null || segment.getStatus() == 1)) {
                segments.put(segment.getId(), segment);
            }
        }
        return segments;
    }

    private List<String> currentRouteSegmentIds(Long currentRouteId) {
        if (currentRouteId == null) {
            return List.of();
        }
        RouteAiRoute current = aiRouteMapper.selectById(currentRouteId);
        if (current == null || !hasText(current.getSegmentJson())) {
            return List.of();
        }
        try {
            return objectMapper.readValue(current.getSegmentJson(), new TypeReference<List<String>>() {});
        } catch (JsonProcessingException ignored) {
            return List.of();
        }
    }

    private Coordinate coordinate(List<BigDecimal> point, BigDecimal latitude, BigDecimal longitude) {
        if (point != null && point.size() >= 2 && point.get(0) != null && point.get(1) != null) {
            return new Coordinate(point.get(0), point.get(1));
        }
        return latitude == null || longitude == null ? null : new Coordinate(latitude, longitude);
    }

    private String normalizeNodeKey(String value) {
        if (!hasText(value)) {
            return "";
        }
        String trimmed = value.trim();
        int separator = trimmed.indexOf(':');
        if (separator < 1 || separator == trimmed.length() - 1) {
            return trimmed;
        }
        return trimmed.substring(0, separator).toUpperCase() + trimmed.substring(separator);
    }

    private int positive(Integer value, int fallback) {
        return value != null && value > 0 ? value : fallback;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException error) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "路线数据格式错误");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record Coordinate(BigDecimal latitude, BigDecimal longitude) {
    }

    private record NodeRef(String key,
                           String id,
                           String name,
                           String type,
                           String description,
                           BigDecimal latitude,
                           BigDecimal longitude) {
    }

    private record TraversedSegment(MapWorkbenchDraftRequest.RouteDraft segment,
                                    String fromKey,
                                    String toKey,
                                    int length,
                                    int duration) {
    }

    private record RouteTraversal(List<NodeRef> nodes,
                                  List<TraversedSegment> segments,
                                  int totalDistance,
                                  int travelMinutes) {
    }
}
