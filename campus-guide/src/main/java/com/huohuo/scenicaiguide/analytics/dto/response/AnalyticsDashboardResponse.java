package com.huohuo.scenicaiguide.analytics.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 运营数据概览响应
 */
@Data
public class AnalyticsDashboardResponse {

    private String range;

    private Summary summary = new Summary();

    private List<KpiCard> kpis = new ArrayList<>();

    private Trend trend = new Trend();

    private List<ActiveSpot> activeSpots = new ArrayList<>();

    private AiUsage aiUsage = new AiUsage();

    private QaSource qaSource = new QaSource();

    private Sentiment sentiment = new Sentiment();

    private RouteHeat routeHeat = new RouteHeat();

    @Data
    public static class Summary {

        private Integer visitorCount = 0;

        private Integer sessionCount = 0;

        private Integer activeVisitorCount = 0;

        private Integer messageCount = 0;

        private Integer fenceTriggerCount = 0;

        private Integer routeRequestCount = 0;

        private Integer avgResponseMs = 0;
    }

    @Data
    public static class KpiCard {

        private String key;

        private String label;

        private String value;

        private String meta;

        private String trendType;
    }

    @Data
    public static class Trend {

        private List<String> labels = new ArrayList<>();

        private List<Integer> values = new ArrayList<>();
    }

    @Data
    public static class ActiveSpot {

        private String name;

        private Integer value;
    }

    @Data
    public static class AiUsage {

        private Long totalTokens = 0L;

        private Long promptTokens = 0L;

        private Long completionTokens = 0L;

        private BigDecimal estimatedCost = BigDecimal.ZERO;

        private Integer chatCalls = 0;

        private Integer ragSearches = 0;

        private Integer ttsChars = 0;

        private BigDecimal asrMinutes = BigDecimal.ZERO;

        private Integer ttsCacheHits = 0;

        private Integer ttsCacheRate = 0;

        private BigDecimal p95FirstTokenSeconds = BigDecimal.ZERO;
    }

    @Data
    public static class QaSource {

        private Integer faq = 0;

        private Integer rag = 0;

        private Integer fallback = 0;

        private Integer guideScript = 0;

        private Integer other = 0;
    }

    @Data
    public static class Sentiment {

        private Integer positive = 0;

        private Integer neutral = 0;

        private Integer confused = 0;

        private Integer negative = 0;
    }

    @Data
    public static class RouteHeat {

        private List<RouteNode> nodes = new ArrayList<>();

        private List<RouteEdge> edges = new ArrayList<>();
    }

    @Data
    public static class RouteNode {

        private String id;

        private String name;

        private String meta;

        private Integer x;

        private Integer y;

        private String type;
    }

    @Data
    public static class RouteEdge {

        private String id;

        private String path;

        private String level;

        private Integer width;
    }
}
