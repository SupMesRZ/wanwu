package com.huohuo.scenicaiguide.route.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class AiRouteResponse {

    private Long id;

    private String name;

    private String routeType;

    private Integer estimatedDurationMin;

    private Integer totalDistanceMeter;

    private String recommendationReason;

    private List<RouteNode> nodes = new ArrayList<>();

    private List<RoutePoint> points = new ArrayList<>();

    private List<String> routeSegmentIds = new ArrayList<>();

    private List<RoutePathPoint> pathPoints = new ArrayList<>();

    @Data
    public static class RouteNode {
        private String id;
        private String name;
        private String type;
        private String description;
        private BigDecimal latitude;
        private BigDecimal longitude;
        private Integer sequence;
    }

    @Data
    public static class RoutePoint {
        private String spotId;
        private String spotName;
        private BigDecimal latitude;
        private BigDecimal longitude;
        private Integer sequence;
    }

    @Data
    public static class RoutePathPoint {
        private String segmentId;
        private BigDecimal latitude;
        private BigDecimal longitude;
        private Integer sequence;
    }
}
