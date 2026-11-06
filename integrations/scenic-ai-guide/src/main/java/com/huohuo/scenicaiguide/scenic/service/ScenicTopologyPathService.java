package com.huohuo.scenicaiguide.scenic.service;

import com.huohuo.scenicaiguide.scenic.dto.request.MapWorkbenchDraftRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;

@Service
public class ScenicTopologyPathService {

    /** 步行速度，与管理端地图工作台自动估算耗时的换算口径保持一致 */
    static final int WALK_METERS_PER_MINUTE = 70;

    private static final int MIN_CROWD_LEVEL = 1;
    private static final int MAX_CROWD_LEVEL = 5;

    /**
     * 按纯距离寻路，用于只关心可达性的场景
     */
    public PathResult findShortestPath(MapWorkbenchDraftRequest payload, String fromKey, String toKey) {
        return findShortestPath(payload, fromKey, toKey, RouteWeights.distanceOnly());
    }

    /**
     * 按加权代价寻路
     *
     * <p>代价用于选路，里程和耗时另行沿选中路段累加，两者必须分离：
     * 掺入耗时与拥挤度后的代价不再是真实米数，直接上报会让导览对游客说错距离
     */
    public PathResult findShortestPath(MapWorkbenchDraftRequest payload,
                                       String fromKey,
                                       String toKey,
                                       RouteWeights weights) {
        if (payload == null || blank(fromKey) || blank(toKey)) {
            return PathResult.unreachable();
        }
        if (fromKey.equals(toKey)) {
            return new PathResult(true, List.of(fromKey), List.of(), 0, 0);
        }

        Map<String, List<Edge>> graph = buildGraph(payload, weights == null ? RouteWeights.distanceOnly() : weights);
        Map<String, Integer> distances = new HashMap<>();
        Map<String, PreviousStep> previous = new HashMap<>();
        PriorityQueue<NodeDistance> queue = new PriorityQueue<>(Comparator.comparingInt(NodeDistance::distance));
        distances.put(fromKey, 0);
        queue.offer(new NodeDistance(fromKey, 0));

        while (!queue.isEmpty()) {
            NodeDistance current = queue.poll();
            if (current.distance() != distances.getOrDefault(current.nodeKey(), Integer.MAX_VALUE)) {
                continue;
            }
            if (current.nodeKey().equals(toKey)) {
                break;
            }
            for (Edge edge : graph.getOrDefault(current.nodeKey(), List.of())) {
                int nextDistance = safeAdd(current.distance(), edge.weight());
                if (nextDistance < distances.getOrDefault(edge.toKey(), Integer.MAX_VALUE)) {
                    distances.put(edge.toKey(), nextDistance);
                    previous.put(edge.toKey(), new PreviousStep(current.nodeKey(), edge.routeId()));
                    queue.offer(new NodeDistance(edge.toKey(), nextDistance));
                }
            }
        }

        if (distances.get(toKey) == null) {
            return PathResult.unreachable();
        }
        return buildResult(payload, fromKey, toKey, previous);
    }

    public boolean reachable(MapWorkbenchDraftRequest payload, String fromKey, String toKey) {
        return findShortestPath(payload, fromKey, toKey).reachable();
    }

    public String endpointKey(MapWorkbenchDraftRequest.EndpointRefDraft ref) {
        if (ref == null || blank(ref.getType()) || blank(ref.getId())) {
            return "";
        }
        return ref.getType().trim().toUpperCase(Locale.ROOT) + ":" + ref.getId().trim();
    }

    public String spotKey(String spotId) {
        return blank(spotId) ? "" : "SPOT:" + spotId.trim();
    }

    public List<PathCoordinate> pathGeometry(MapWorkbenchDraftRequest payload, PathResult path) {
        if (payload == null || path == null || !path.reachable() || path.routeIds().isEmpty()) {
            return List.of();
        }
        List<PathCoordinate> geometry = new ArrayList<>();
        for (int index = 0; index < path.routeIds().size(); index++) {
            String routeId = path.routeIds().get(index);
            String fromKey = path.nodeKeys().get(index);
            MapWorkbenchDraftRequest.RouteDraft route = payload.getRouteSegments().stream()
                .filter(candidate -> candidate != null && routeId.equals(candidate.getId()))
                .findFirst()
                .orElse(null);
            if (route == null) {
                continue;
            }
            boolean forward = fromKey.equals(endpointKey(route.getFrom()));
            List<Coordinate> segmentPoints = segmentGeometry(payload, route);
            if (!forward) {
                Collections.reverse(segmentPoints);
            }
            for (Coordinate point : segmentPoints) {
                PathCoordinate coordinate = new PathCoordinate(
                    routeId, point.latitude(), point.longitude(), geometry.size() + 1);
                if (geometry.isEmpty() || !geometry.getLast().samePosition(coordinate)) {
                    geometry.add(coordinate);
                }
            }
        }
        return List.copyOf(geometry);
    }

    private Map<String, List<Edge>> buildGraph(MapWorkbenchDraftRequest payload, RouteWeights weights) {
        Map<String, List<Edge>> graph = new HashMap<>();
        for (MapWorkbenchDraftRequest.RouteDraft route : payload.getRouteSegments()) {
            if (route == null || route.getStatus() != null && route.getStatus() == 0) {
                continue;
            }
            String from = endpointKey(route.getFrom());
            String to = endpointKey(route.getTo());
            if (blank(from) || blank(to)) {
                continue;
            }
            int cost = routeCost(route, weights);
            addEdge(graph, from, to, route.getId(), cost);
            addEdge(graph, to, from, route.getId(), cost);
        }
        return graph;
    }

    private void addEdge(Map<String, List<Edge>> graph, String from, String to, String routeId, int cost) {
        graph.computeIfAbsent(from, ignored -> new ArrayList<>()).add(new Edge(to, routeId, cost));
    }

    /**
     * 把三项异构指标折算到"等效米"再按权重混合
     *
     * <p>耗时按步行 {@value #WALK_METERS_PER_MINUTE} 米/分钟折算，与管理端地图工作台
     * 自动估算耗时用的换算口径一致；拥挤度 1~5 折算成 0~100% 的里程惩罚，
     * 即最拥挤路段的拥挤项等于自身长度。代价至少为 1，避免零权路段破坏 Dijkstra
     */
    private int routeCost(MapWorkbenchDraftRequest.RouteDraft route, RouteWeights weights) {
        int lengthMeter = routeLength(route);
        double durationEquivalent = route.getDurationMin() != null && route.getDurationMin() > 0
            ? route.getDurationMin() * (double) WALK_METERS_PER_MINUTE
            : lengthMeter;
        double crowdPenalty = lengthMeter * crowdFactor(route.getCrowdLevel());
        double cost = weights.distance() * lengthMeter
            + weights.duration() * durationEquivalent
            + weights.crowd() * crowdPenalty;
        return Math.max(1, (int) Math.round(cost));
    }

    /**
     * 拥挤度 1 表示通畅不加惩罚，5 表示最挤，落在 [0,1]
     */
    private double crowdFactor(Integer crowdLevel) {
        if (crowdLevel == null || crowdLevel <= MIN_CROWD_LEVEL) {
            return 0D;
        }
        int capped = Math.min(crowdLevel, MAX_CROWD_LEVEL);
        return (capped - MIN_CROWD_LEVEL) / (double) (MAX_CROWD_LEVEL - MIN_CROWD_LEVEL);
    }

    private int routeLength(MapWorkbenchDraftRequest.RouteDraft route) {
        return route.getLengthMeter() != null && route.getLengthMeter() > 0 ? route.getLengthMeter() : 1;
    }

    private int routeDuration(MapWorkbenchDraftRequest.RouteDraft route) {
        if (route.getDurationMin() != null && route.getDurationMin() > 0) {
            return route.getDurationMin();
        }
        return (int) Math.ceil(routeLength(route) / (double) WALK_METERS_PER_MINUTE);
    }

    private List<Coordinate> segmentGeometry(MapWorkbenchDraftRequest payload,
                                             MapWorkbenchDraftRequest.RouteDraft route) {
        List<Coordinate> points = new ArrayList<>();
        addCoordinate(points, endpointCoordinate(payload, route.getFrom()));
        for (List<BigDecimal> middlePoint : route.getMiddlePoints() == null ? List.<List<BigDecimal>>of() : route.getMiddlePoints()) {
            addCoordinate(points, coordinate(middlePoint, null, null));
        }
        addCoordinate(points, endpointCoordinate(payload, route.getTo()));
        return points;
    }

    private Coordinate endpointCoordinate(MapWorkbenchDraftRequest payload,
                                          MapWorkbenchDraftRequest.EndpointRefDraft endpoint) {
        if (endpoint == null) {
            return null;
        }
        Coordinate direct = coordinate(endpoint.getPoint(), endpoint.getLatitude(), endpoint.getLongitude());
        if (direct != null) {
            return direct;
        }
        String key = endpointKey(endpoint);
        if (key.startsWith("SPOT:")) {
            String spotId = key.substring("SPOT:".length());
            return payload.getSpots().stream()
                .filter(spot -> spot != null && spotId.equals(spot.getId()))
                .map(spot -> coordinate(spot.getLabelPoint(), spot.getLatitude(), spot.getLongitude()))
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
        }
        return payload.getRouteEndpoints().stream()
            .filter(candidate -> candidate != null && key.equals(endpointKey(toRef(candidate))))
            .map(candidate -> coordinate(candidate.getPoint(), candidate.getLatitude(), candidate.getLongitude()))
            .filter(java.util.Objects::nonNull)
            .findFirst()
            .orElse(null);
    }

    private MapWorkbenchDraftRequest.EndpointRefDraft toRef(MapWorkbenchDraftRequest.EndpointDraft endpoint) {
        MapWorkbenchDraftRequest.EndpointRefDraft ref = new MapWorkbenchDraftRequest.EndpointRefDraft();
        ref.setType("ENDPOINT");
        ref.setId(endpoint.getId());
        return ref;
    }

    private Coordinate coordinate(List<BigDecimal> point, BigDecimal latitude, BigDecimal longitude) {
        if (point != null && point.size() >= 2 && point.get(0) != null && point.get(1) != null) {
            return new Coordinate(point.get(0), point.get(1));
        }
        return latitude == null || longitude == null ? null : new Coordinate(latitude, longitude);
    }

    private void addCoordinate(List<Coordinate> points, Coordinate coordinate) {
        if (coordinate != null && (points.isEmpty() || !points.getLast().samePosition(coordinate))) {
            points.add(coordinate);
        }
    }

    /**
     * 回溯选中路径，并沿路累加真实里程与耗时
     *
     * <p>不能复用 Dijkstra 的累计代价：代价掺了耗时与拥挤度权重，不是米数
     */
    private PathResult buildResult(MapWorkbenchDraftRequest payload,
                                   String fromKey,
                                   String toKey,
                                   Map<String, PreviousStep> previous) {
        List<String> nodeKeys = new ArrayList<>();
        List<String> routeIds = new ArrayList<>();
        String current = toKey;
        nodeKeys.add(current);
        while (!current.equals(fromKey)) {
            PreviousStep step = previous.get(current);
            if (step == null) {
                return PathResult.unreachable();
            }
            routeIds.add(step.routeId());
            current = step.fromKey();
            nodeKeys.add(current);
        }
        Collections.reverse(nodeKeys);
        Collections.reverse(routeIds);

        Map<String, MapWorkbenchDraftRequest.RouteDraft> byId = new HashMap<>();
        for (MapWorkbenchDraftRequest.RouteDraft route : payload.getRouteSegments()) {
            if (route != null && !blank(route.getId())) {
                byId.put(route.getId(), route);
            }
        }
        int totalLength = 0;
        int totalDuration = 0;
        for (String routeId : routeIds) {
            MapWorkbenchDraftRequest.RouteDraft route = byId.get(routeId);
            if (route == null) {
                continue;
            }
            totalLength = safeAdd(totalLength, routeLength(route));
            totalDuration = safeAdd(totalDuration, routeDuration(route));
        }
        return new PathResult(true, List.copyOf(nodeKeys), List.copyOf(routeIds), totalLength, totalDuration);
    }

    private int safeAdd(int left, int right) {
        return left > Integer.MAX_VALUE - right ? Integer.MAX_VALUE : left + right;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public record PathResult(boolean reachable,
                             List<String> nodeKeys,
                             List<String> routeIds,
                             int totalLengthMeter,
                             int totalDurationMin) {

        /**
         * 未标注耗时的调用方按步行速度折算，避免各处重复估算
         */
        public PathResult(boolean reachable, List<String> nodeKeys, List<String> routeIds, int totalLengthMeter) {
            this(reachable, nodeKeys, routeIds, totalLengthMeter,
                totalLengthMeter <= 0 ? 0 : (int) Math.ceil(totalLengthMeter / (double) WALK_METERS_PER_MINUTE));
        }

        private static PathResult unreachable() {
            return new PathResult(false, List.of(), List.of(), 0, 0);
        }
    }

    /**
     * 算路权重，取值来自 route 域配置中心
     *
     * <p>本记录只承载数值不读配置，保证 scenic 域不反向依赖 route 域
     */
    public record RouteWeights(double distance, double duration, double crowd) {

        public RouteWeights {
            distance = Math.max(0D, distance);
            duration = Math.max(0D, duration);
            crowd = Math.max(0D, crowd);
            if (distance + duration + crowd <= 0D) {
                // 三项权重全为 0 会让所有路段代价相等，退化成"按路段数最少"，这里回落到纯距离
                distance = 1D;
            }
        }

        public static RouteWeights distanceOnly() {
            return new RouteWeights(1D, 0D, 0D);
        }
    }

    public record PathCoordinate(String routeId,
                                 BigDecimal latitude,
                                 BigDecimal longitude,
                                 int sequence) {

        private boolean samePosition(PathCoordinate other) {
            return latitude.compareTo(other.latitude) == 0 && longitude.compareTo(other.longitude) == 0;
        }
    }

    private record Coordinate(BigDecimal latitude, BigDecimal longitude) {

        private boolean samePosition(Coordinate other) {
            return latitude.compareTo(other.latitude) == 0 && longitude.compareTo(other.longitude) == 0;
        }
    }

    private record Edge(String toKey, String routeId, int weight) {
    }

    private record NodeDistance(String nodeKey, int distance) {
    }

    private record PreviousStep(String fromKey, String routeId) {
    }
}
