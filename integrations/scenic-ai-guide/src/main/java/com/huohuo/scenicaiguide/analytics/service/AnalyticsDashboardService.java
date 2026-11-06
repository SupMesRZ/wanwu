package com.huohuo.scenicaiguide.analytics.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.huohuo.scenicaiguide.analytics.dto.response.AnalyticsDashboardResponse;
import com.huohuo.scenicaiguide.analytics.entity.AnalyticsAiUsageDaily;
import com.huohuo.scenicaiguide.analytics.entity.AnalyticsOverviewDaily;
import com.huohuo.scenicaiguide.analytics.entity.AnalyticsRouteDaily;
import com.huohuo.scenicaiguide.analytics.entity.AnalyticsSpotDaily;
import com.huohuo.scenicaiguide.analytics.mapper.AnalyticsAiUsageDailyMapper;
import com.huohuo.scenicaiguide.analytics.mapper.AnalyticsOverviewDailyMapper;
import com.huohuo.scenicaiguide.analytics.mapper.AnalyticsRouteDailyMapper;
import com.huohuo.scenicaiguide.analytics.mapper.AnalyticsSpotDailyMapper;
import com.huohuo.scenicaiguide.digitalhuman.entity.DhTtsCache;
import com.huohuo.scenicaiguide.digitalhuman.mapper.DhTtsCacheMapper;
import com.huohuo.scenicaiguide.route.entity.RouteAiRoute;
import com.huohuo.scenicaiguide.route.mapper.RouteAiRouteMapper;
import com.huohuo.scenicaiguide.system.service.ConfigService;
import com.huohuo.scenicaiguide.tourist.entity.TouristFenceLog;
import com.huohuo.scenicaiguide.tourist.entity.TouristGuideSession;
import com.huohuo.scenicaiguide.tourist.entity.TouristMessage;
import com.huohuo.scenicaiguide.tourist.mapper.TouristFenceLogMapper;
import com.huohuo.scenicaiguide.tourist.mapper.TouristGuideSessionMapper;
import com.huohuo.scenicaiguide.tourist.mapper.TouristMessageMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;

/**
 * 运营数据概览服务
 */
@Service
public class AnalyticsDashboardService {

    private static final Long DEFAULT_SCENIC_AREA_ID = 1L;
    private static final DateTimeFormatter MONTH_DAY_FORMATTER = DateTimeFormatter.ofPattern("MM-dd");
    private static final String SENTIMENT_BATCH_SIZE_KEY = "analytics-sentiment-batch-size";
    private static final int DEFAULT_SENTIMENT_BATCH_SIZE = 100;

    private final AnalyticsOverviewDailyMapper overviewDailyMapper;
    private final AnalyticsAiUsageDailyMapper aiUsageDailyMapper;
    private final AnalyticsSpotDailyMapper spotDailyMapper;
    private final AnalyticsRouteDailyMapper routeDailyMapper;
    private final TouristMessageMapper touristMessageMapper;
    private final TouristGuideSessionMapper guideSessionMapper;
    private final TouristFenceLogMapper fenceLogMapper;
    private final RouteAiRouteMapper aiRouteMapper;
    private final DhTtsCacheMapper ttsCacheMapper;
    private final ConfigService configService;

    @Autowired
    public AnalyticsDashboardService(AnalyticsOverviewDailyMapper overviewDailyMapper,
                                     AnalyticsAiUsageDailyMapper aiUsageDailyMapper,
                                     AnalyticsSpotDailyMapper spotDailyMapper,
                                     AnalyticsRouteDailyMapper routeDailyMapper,
                                     TouristMessageMapper touristMessageMapper,
                                     TouristGuideSessionMapper guideSessionMapper,
                                     TouristFenceLogMapper fenceLogMapper,
                                     RouteAiRouteMapper aiRouteMapper,
                                     DhTtsCacheMapper ttsCacheMapper,
                                     ConfigService configService) {
        this.overviewDailyMapper = overviewDailyMapper;
        this.aiUsageDailyMapper = aiUsageDailyMapper;
        this.spotDailyMapper = spotDailyMapper;
        this.routeDailyMapper = routeDailyMapper;
        this.touristMessageMapper = touristMessageMapper;
        this.guideSessionMapper = guideSessionMapper;
        this.fenceLogMapper = fenceLogMapper;
        this.aiRouteMapper = aiRouteMapper;
        this.ttsCacheMapper = ttsCacheMapper;
        this.configService = configService;
    }

    @Transactional(readOnly = true)
    public AnalyticsDashboardResponse getDashboard(String range, LocalDate startDate, LocalDate endDate) {
        DateRange dateRange = resolveDateRange(range, startDate, endDate);
        AnalyticsSummary current = summarizeLive(dateRange);
        AnalyticsSummary previous = summarizeLive(dateRange.previous());
        AiSummary aiSummary = summarizeAiLive(dateRange);

        AnalyticsDashboardResponse response = new AnalyticsDashboardResponse();
        response.setRange(dateRange.label());
        response.setSummary(buildSummary(current));
        response.setKpis(List.of(
            kpi("visitors", "服务人次", formatInteger(current.visitorCount()), trendMeta(current.visitorCount(), previous.visitorCount()), trendType(current.visitorCount(), previous.visitorCount())),
            kpi("sessions", "当前活跃会话", formatInteger(current.activeVisitorCount()), "近 30 分钟", "neutral"),
            kpi("response", "AI 回答消息", formatInteger(aiSummary.chatCalls()), "真实消息记录", "neutral"),
            kpi("fences", "围栏触发次数", formatInteger(current.fenceTriggerCount()), trendMeta(current.fenceTriggerCount(), previous.fenceTriggerCount()), trendType(current.fenceTriggerCount(), previous.fenceTriggerCount()))
        ));
        response.setTrend(buildLiveTrend(dateRange));
        response.setActiveSpots(buildLiveActiveSpots(dateRange));
        response.setAiUsage(buildAiUsage(aiSummary));
        response.setQaSource(buildQaSource(dateRange));
        response.setSentiment(buildLiveSentiment(dateRange));
        response.setRouteHeat(new AnalyticsDashboardResponse.RouteHeat());
        return response;
    }

    private AnalyticsDashboardResponse.Summary buildSummary(AnalyticsSummary summary) {
        AnalyticsDashboardResponse.Summary response = new AnalyticsDashboardResponse.Summary();
        response.setVisitorCount(summary.visitorCount());
        response.setSessionCount(summary.sessionCount());
        response.setActiveVisitorCount(summary.activeVisitorCount());
        response.setMessageCount(summary.messageCount());
        response.setFenceTriggerCount(summary.fenceTriggerCount());
        response.setRouteRequestCount(summary.routeRequestCount());
        response.setAvgResponseMs(summary.avgResponseMs());
        return response;
    }

    private List<AnalyticsOverviewDaily> listOverviewRows(DateRange dateRange) {
        return overviewDailyMapper.selectList(new QueryWrapper<AnalyticsOverviewDaily>()
                .eq("scenic_area_id", DEFAULT_SCENIC_AREA_ID)
                .between("stat_date", dateRange.start(), dateRange.end())
                .orderByAsc("stat_date"));
    }

    private List<AnalyticsAiUsageDaily> listAiRows(DateRange dateRange) {
        return aiUsageDailyMapper.selectList(new QueryWrapper<AnalyticsAiUsageDaily>()
                .eq("scenic_area_id", DEFAULT_SCENIC_AREA_ID)
                .between("stat_date", dateRange.start(), dateRange.end())
                .orderByAsc("stat_date"));
    }

    private AnalyticsSummary summarizeLive(DateRange dateRange) {
        LocalDateTime startAt = dateRange.start().atStartOfDay();
        LocalDateTime endAt = dateRange.end().atTime(LocalTime.MAX);
        Map<String, Object> sessionCounts = firstRow(guideSessionMapper.selectMaps(
            new QueryWrapper<TouristGuideSession>()
                .select("COUNT(*) AS session_count", "COUNT(DISTINCT visitor_subject) AS visitor_count")
                .eq("scenic_area_id", DEFAULT_SCENIC_AREA_ID)
                .between("created_at", startAt, endAt)));
        int active = guideSessionMapper.selectCount(new QueryWrapper<TouristGuideSession>()
            .eq("scenic_area_id", DEFAULT_SCENIC_AREA_ID)
            .eq("status", "ACTIVE")
            .ge("updated_at", LocalDateTime.now().minusMinutes(30))).intValue();
        int messages = touristMessageMapper.selectCount(new QueryWrapper<TouristMessage>()
            .between("created_at", startAt, endAt)).intValue();
        int fences = fenceLogMapper.selectCount(new QueryWrapper<TouristFenceLog>()
            .between("reported_at", startAt, endAt)).intValue();
        int routes = aiRouteMapper.selectCount(new QueryWrapper<RouteAiRoute>()
            .eq("scenic_area_id", DEFAULT_SCENIC_AREA_ID)
            .between("created_at", startAt, endAt)).intValue();
        return new AnalyticsSummary(
            intValue(sessionCounts, "visitor_count"), intValue(sessionCounts, "session_count"), active,
            messages, fences, routes, 0, 0, 0, 0, 0);
    }

    private AiSummary summarizeAiLive(DateRange dateRange) {
        LocalDateTime startAt = dateRange.start().atStartOfDay();
        LocalDateTime endAt = dateRange.end().atTime(LocalTime.MAX);
        int chatCalls = touristMessageMapper.selectCount(new QueryWrapper<TouristMessage>()
            .eq("sender", "AGENT")
            .between("created_at", startAt, endAt)).intValue();
        int ragSearches = touristMessageMapper.selectCount(new QueryWrapper<TouristMessage>()
            .eq("sender", "AGENT")
            .like("evidence_text", "CHUNK:")
            .between("created_at", startAt, endAt)).intValue();
        Map<String, Object> tts = firstRow(ttsCacheMapper.selectMaps(new QueryWrapper<DhTtsCache>()
            .select(
                "COALESCE(SUM(CHAR_LENGTH(text_preview) * (COALESCE(hit_count, 0) + 1)), 0) AS tts_chars",
                "COALESCE(SUM(hit_count), 0) AS cache_hits",
                "COALESCE(SUM(COALESCE(hit_count, 0) + 1), 0) AS cache_requests")
            .between("updated_at", startAt, endAt)));
        return new AiSummary(chatCalls, ragSearches, 0, 0, 0,
            intValue(tts, "tts_chars"), BigDecimal.ZERO, intValue(tts, "cache_hits"),
            intValue(tts, "cache_requests"), 0, BigDecimal.ZERO);
    }

    private AnalyticsDashboardResponse.Trend buildLiveTrend(DateRange dateRange) {
        LocalDateTime startAt = dateRange.start().atStartOfDay();
        LocalDateTime endAt = dateRange.end().atTime(LocalTime.MAX);
        List<Map<String, Object>> rows = guideSessionMapper.selectMaps(new QueryWrapper<TouristGuideSession>()
            .select("DATE(created_at) AS stat_date", "COUNT(DISTINCT visitor_subject) AS visitor_count")
            .eq("scenic_area_id", DEFAULT_SCENIC_AREA_ID)
            .between("created_at", startAt, endAt)
            .groupBy("DATE(created_at)")
            .orderByAsc("DATE(created_at)"));
        Map<String, Integer> counts = new HashMap<>();
        for (Map<String, Object> row : rows) {
            counts.put(String.valueOf(mapValue(row, "stat_date")), intValue(row, "visitor_count"));
        }
        AnalyticsDashboardResponse.Trend trend = new AnalyticsDashboardResponse.Trend();
        for (LocalDate date = dateRange.start(); !date.isAfter(dateRange.end()); date = date.plusDays(1)) {
            trend.getLabels().add(date.format(MONTH_DAY_FORMATTER));
            trend.getValues().add(counts.getOrDefault(date.toString(), 0));
        }
        return trend;
    }

    private List<AnalyticsDashboardResponse.ActiveSpot> buildLiveActiveSpots(DateRange dateRange) {
        LocalDateTime startAt = dateRange.start().atStartOfDay();
        LocalDateTime endAt = dateRange.end().atTime(LocalTime.MAX);
        return fenceLogMapper.selectMaps(new QueryWrapper<TouristFenceLog>()
                .select("COALESCE(spot_name, spot_id) AS spot_name", "COUNT(*) AS trigger_count")
                .between("reported_at", startAt, endAt)
                .groupBy("spot_id", "spot_name")
                .orderByDesc("trigger_count")
                .last("LIMIT 5"))
            .stream()
            .map(row -> {
                AnalyticsDashboardResponse.ActiveSpot spot = new AnalyticsDashboardResponse.ActiveSpot();
                spot.setName(stringValue(row, "spot_name"));
                spot.setValue(intValue(row, "trigger_count"));
                return spot;
            })
            .toList();
    }

    private AnalyticsDashboardResponse.Sentiment buildLiveSentiment(DateRange dateRange) {
        LocalDateTime startAt = dateRange.start().atStartOfDay();
        LocalDateTime endAt = dateRange.end().atTime(LocalTime.MAX);
        // 正则粗分只看最新一批消息，条数由 analytics-sentiment-batch-size 控制，
        // 避免大区间全表扫描；LLM 情感分类留作后续增强
        List<Map<String, Object>> rows = touristMessageMapper.selectMaps(new QueryWrapper<TouristMessage>()
            .select("content")
            .eq("sender", "TOURIST")
            .between("created_at", startAt, endAt)
            .orderByDesc("id")
            .last("LIMIT " + sentimentBatchSize()));
        int positive = 0;
        int neutral = 0;
        int confused = 0;
        int negative = 0;
        for (Map<String, Object> row : rows) {
            String content = stringValue(row, "content");
            if (content.matches(".*(投诉|不好|太差|失望|生气).*")) {
                negative++;
            } else if (content.matches(".*(哪里|怎么|找不到|迷路|路线|为何|为什么|[?？]).*")) {
                confused++;
            } else if (content.matches(".*(谢谢|喜欢|不错|满意|很好|开心).*")) {
                positive++;
            } else {
                neutral++;
            }
        }
        int total = positive + neutral + confused + negative;
        AnalyticsDashboardResponse.Sentiment sentiment = new AnalyticsDashboardResponse.Sentiment();
        if (total == 0) {
            return sentiment;
        }
        sentiment.setPositive(percentValue(positive, total));
        sentiment.setNeutral(percentValue(neutral, total));
        sentiment.setConfused(percentValue(confused, total));
        sentiment.setNegative(Math.max(0, 100 - sentiment.getPositive() - sentiment.getNeutral() - sentiment.getConfused()));
        return sentiment;
    }

    private Map<String, Object> firstRow(List<Map<String, Object>> rows) {
        return rows == null || rows.isEmpty() ? Map.of() : rows.getFirst();
    }

    private int sentimentBatchSize() {
        try {
            return Math.max(1, configService.getInt(SENTIMENT_BATCH_SIZE_KEY, DEFAULT_SENTIMENT_BATCH_SIZE));
        } catch (Exception ignored) {
            return DEFAULT_SENTIMENT_BATCH_SIZE;
        }
    }

    private AnalyticsSummary summarize(List<AnalyticsOverviewDaily> rows) {
        int visitorCount = rows.stream().map(AnalyticsOverviewDaily::getVisitorCount).filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
        int sessionCount = rows.stream().map(AnalyticsOverviewDaily::getSessionCount).filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
        int activeVisitorCount = rows.stream().map(AnalyticsOverviewDaily::getActiveVisitorCount).filter(Objects::nonNull).mapToInt(Integer::intValue).max().orElse(0);
        int messageCount = rows.stream().map(AnalyticsOverviewDaily::getMessageCount).filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
        int fenceTriggerCount = rows.stream().map(AnalyticsOverviewDaily::getFenceTriggerCount).filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
        int routeRequestCount = rows.stream().map(AnalyticsOverviewDaily::getRouteRequestCount).filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
        int positive = rows.stream().map(AnalyticsOverviewDaily::getPositiveMessageCount).filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
        int neutral = rows.stream().map(AnalyticsOverviewDaily::getNeutralMessageCount).filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
        int negative = rows.stream().map(AnalyticsOverviewDaily::getNegativeMessageCount).filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
        int complaint = rows.stream().map(AnalyticsOverviewDaily::getComplaintMessageCount).filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
        int weightedResponse = weightedAverage(rows);
        return new AnalyticsSummary(visitorCount, sessionCount, activeVisitorCount, messageCount, fenceTriggerCount,
            routeRequestCount, weightedResponse, positive, neutral, negative, complaint);
    }

    private int weightedAverage(List<AnalyticsOverviewDaily> rows) {
        int weight = rows.stream().map(AnalyticsOverviewDaily::getMessageCount).filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
        if (weight <= 0) {
            return 0;
        }
        int total = rows.stream()
            .filter(row -> row.getAvgResponseMs() != null && row.getMessageCount() != null)
            .mapToInt(row -> row.getAvgResponseMs() * row.getMessageCount())
            .sum();
        return total / weight;
    }

    private AiSummary summarizeAi(List<AnalyticsAiUsageDaily> rows) {
        int chatCalls = rows.stream().map(AnalyticsAiUsageDaily::getChatCallCount).filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
        int ragSearches = rows.stream().map(AnalyticsAiUsageDaily::getRagSearchCount).filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
        long promptTokens = rows.stream().map(AnalyticsAiUsageDaily::getPromptTokens).filter(Objects::nonNull).mapToLong(Long::longValue).sum();
        long completionTokens = rows.stream().map(AnalyticsAiUsageDaily::getCompletionTokens).filter(Objects::nonNull).mapToLong(Long::longValue).sum();
        long totalTokens = rows.stream().map(AnalyticsAiUsageDaily::getTotalTokens).filter(Objects::nonNull).mapToLong(Long::longValue).sum();
        int ttsChars = rows.stream().map(AnalyticsAiUsageDaily::getTtsCharCount).filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
        BigDecimal asrSeconds = rows.stream().map(AnalyticsAiUsageDaily::getAsrDurationSeconds).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        int ttsCacheHits = rows.stream().map(AnalyticsAiUsageDaily::getTtsCacheHitCount).filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
        int ttsCacheRequests = rows.stream().map(AnalyticsAiUsageDaily::getTtsCacheRequestCount).filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
        int p95 = rows.stream().map(AnalyticsAiUsageDaily::getP95FirstTokenMs).filter(Objects::nonNull).mapToInt(Integer::intValue).max().orElse(0);
        BigDecimal cost = rows.stream().map(AnalyticsAiUsageDaily::getEstimatedCost).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new AiSummary(chatCalls, ragSearches, promptTokens, completionTokens, totalTokens, ttsChars,
            asrSeconds, ttsCacheHits, ttsCacheRequests, p95, cost);
    }

    private AnalyticsDashboardResponse.KpiCard kpi(String key, String label, String value, String meta, String trendType) {
        AnalyticsDashboardResponse.KpiCard card = new AnalyticsDashboardResponse.KpiCard();
        card.setKey(key);
        card.setLabel(label);
        card.setValue(value);
        card.setMeta(meta);
        card.setTrendType(trendType);
        return card;
    }

    private AnalyticsDashboardResponse.Trend buildTrend(List<AnalyticsOverviewDaily> rows) {
        AnalyticsDashboardResponse.Trend trend = new AnalyticsDashboardResponse.Trend();
        for (AnalyticsOverviewDaily row : rows) {
            trend.getLabels().add(row.getStatDate().format(MONTH_DAY_FORMATTER));
            trend.getValues().add(defaultInt(row.getVisitorCount()));
        }
        return trend;
    }

    private List<AnalyticsDashboardResponse.ActiveSpot> buildActiveSpots(DateRange dateRange) {
        return spotDailyMapper.selectList(new QueryWrapper<AnalyticsSpotDaily>()
                .eq("scenic_area_id", DEFAULT_SCENIC_AREA_ID)
                .between("stat_date", dateRange.start(), dateRange.end())
                .orderByDesc("fence_trigger_count"))
            .stream()
            .collect(java.util.stream.Collectors.groupingBy(
                AnalyticsSpotDaily::getSpotName,
                java.util.stream.Collectors.summingInt(row -> defaultInt(row.getFenceTriggerCount()))))
            .entrySet()
            .stream()
            .sorted(java.util.Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(5)
            .map(entry -> {
                AnalyticsDashboardResponse.ActiveSpot spot = new AnalyticsDashboardResponse.ActiveSpot();
                spot.setName(entry.getKey());
                spot.setValue(entry.getValue());
                return spot;
            })
            .toList();
    }

    private AnalyticsDashboardResponse.AiUsage buildAiUsage(AiSummary summary) {
        AnalyticsDashboardResponse.AiUsage usage = new AnalyticsDashboardResponse.AiUsage();
        usage.setTotalTokens(summary.totalTokens());
        usage.setPromptTokens(summary.promptTokens());
        usage.setCompletionTokens(summary.completionTokens());
        usage.setEstimatedCost(summary.estimatedCost().setScale(2, RoundingMode.HALF_UP));
        usage.setChatCalls(summary.chatCalls());
        usage.setRagSearches(summary.ragSearches());
        usage.setTtsChars(summary.ttsChars());
        usage.setAsrMinutes(summary.asrSeconds().divide(BigDecimal.valueOf(60), 1, RoundingMode.HALF_UP));
        usage.setTtsCacheHits(summary.ttsCacheHits());
        usage.setTtsCacheRate(summary.ttsCacheRequests() == 0 ? 0 : summary.ttsCacheHits() * 100 / summary.ttsCacheRequests());
        usage.setP95FirstTokenSeconds(BigDecimal.valueOf(summary.p95FirstTokenMs()).divide(BigDecimal.valueOf(1000), 2, RoundingMode.HALF_UP));
        return usage;
    }

    private AnalyticsDashboardResponse.QaSource buildQaSource(DateRange dateRange) {
        LocalDateTime startAt = dateRange.start().atStartOfDay();
        LocalDateTime endAt = dateRange.end().atTime(LocalTime.MAX);
        List<Map<String, Object>> rows = touristMessageMapper.selectMaps(new QueryWrapper<TouristMessage>()
            .select("evidence_text AS evidence", "COUNT(*) AS source_count")
            .eq("sender", "AGENT")
            .eq("message_type", "TEXT")
            .between("created_at", startAt, endAt)
            .groupBy("evidence_text"));
        int faq = 0;
        int rag = 0;
        int fallback = 0;
        int guideScript = 0;
        int other = 0;
        for (Map<String, Object> row : rows) {
            String evidence = stringValue(row, "evidence");
            int count = intValue(row, "source_count");
            if (evidence.startsWith("FAQ#")) {
                faq += count;
            } else if (evidence.startsWith("CHUNK:")) {
                rag += count;
            } else if (evidence.startsWith("NO_")) {
                fallback += count;
            } else if (evidence.startsWith("GUIDE_SCRIPT#")) {
                guideScript += count;
            } else {
                other += count;
            }
        }

        int total = faq + rag + fallback + guideScript + other;
        AnalyticsDashboardResponse.QaSource qaSource = new AnalyticsDashboardResponse.QaSource();
        if (total == 0) {
            return qaSource;
        }
        qaSource.setFaq(percentValue(faq, total));
        qaSource.setRag(percentValue(rag, total));
        qaSource.setFallback(percentValue(fallback, total));
        qaSource.setGuideScript(percentValue(guideScript, total));
        qaSource.setOther(Math.max(0, 100 - qaSource.getFaq() - qaSource.getRag()
            - qaSource.getFallback() - qaSource.getGuideScript()));
        return qaSource;
    }

    private AnalyticsDashboardResponse.Sentiment buildSentiment(AnalyticsSummary summary) {
        int total = summary.positiveMessageCount() + summary.neutralMessageCount() + summary.negativeMessageCount() + summary.complaintMessageCount();
        AnalyticsDashboardResponse.Sentiment sentiment = new AnalyticsDashboardResponse.Sentiment();
        if (total == 0) {
            return sentiment;
        }
        sentiment.setPositive(summary.positiveMessageCount() * 100 / total);
        sentiment.setNeutral(summary.neutralMessageCount() * 100 / total);
        sentiment.setConfused(summary.negativeMessageCount() * 100 / total);
        sentiment.setNegative(Math.max(0, 100 - sentiment.getPositive() - sentiment.getNeutral() - sentiment.getConfused()));
        return sentiment;
    }

    private AnalyticsDashboardResponse.RouteHeat buildRouteHeat(DateRange dateRange) {
        List<AnalyticsRouteDaily> routes = routeDailyMapper.selectList(new QueryWrapper<AnalyticsRouteDaily>()
                .eq("scenic_area_id", DEFAULT_SCENIC_AREA_ID)
                .between("stat_date", dateRange.start(), dateRange.end())
                .orderByAsc("id"));
        AnalyticsDashboardResponse.RouteHeat heat = new AnalyticsDashboardResponse.RouteHeat();
        heat.setEdges(routes.stream()
            .filter(route -> route.getDisplayPath() != null)
            .map(route -> {
                AnalyticsDashboardResponse.RouteEdge edge = new AnalyticsDashboardResponse.RouteEdge();
                edge.setId(route.getRouteId());
                edge.setPath(route.getDisplayPath());
                edge.setLevel(heatLevel(route.getHeatLevel()));
                edge.setWidth(defaultInt(route.getDisplayWidth(), 8));
                return edge;
            })
            .toList());
        heat.setNodes(List.of(
            routeNode("east", "东入口", "入口", 90, 250, "entry"),
            routeNode("folk", "民俗馆", spotMeta("民俗馆", dateRange), 260, 180, "spot"),
            routeNode("bridge", "望月桥", spotMeta("望月桥", dateRange), 450, 140, "hot"),
            routeNode("rest", "餐饮休息区", "服务", 570, 245, "service"),
            routeNode("academy", "云山书院", spotMeta("云山书院", dateRange), 700, 128, "spot"),
            routeNode("view", "观景台", spotMeta("观景台", dateRange), 880, 205, "spot"),
            routeNode("west", "西出口", "出口", 980, 100, "entry")
        ));
        return heat;
    }

    private AnalyticsDashboardResponse.RouteNode routeNode(String id, String name, String meta, int x, int y, String type) {
        AnalyticsDashboardResponse.RouteNode node = new AnalyticsDashboardResponse.RouteNode();
        node.setId(id);
        node.setName(name);
        node.setMeta(meta);
        node.setX(x);
        node.setY(y);
        node.setType(type);
        return node;
    }

    private String spotMeta(String spotName, DateRange dateRange) {
        Integer count = spotDailyMapper.selectList(new QueryWrapper<AnalyticsSpotDaily>()
                .eq("scenic_area_id", DEFAULT_SCENIC_AREA_ID)
                .eq("spot_name", spotName)
                .between("stat_date", dateRange.start(), dateRange.end()))
            .stream()
            .map(AnalyticsSpotDaily::getFenceTriggerCount)
            .filter(Objects::nonNull)
            .reduce(0, Integer::sum);
        return String.valueOf(count);
    }

    private String heatLevel(Integer heatLevel) {
        int value = defaultInt(heatLevel);
        if (value >= 3) {
            return "hot";
        }
        if (value == 2) {
            return "warm";
        }
        return "cool";
    }

    private DateRange resolveDateRange(String range, LocalDate startDate, LocalDate endDate) {
        LocalDate today = latestStatDate();
        String normalized = range == null ? "today" : range.toLowerCase(Locale.ROOT);
        if ("custom".equals(normalized) && startDate != null && endDate != null && !startDate.isAfter(endDate)) {
            return new DateRange("custom", startDate, endDate);
        }
        if ("month".equals(normalized) || "近30天".equals(range)) {
            return new DateRange("month", today.minusDays(29), today);
        }
        if ("week".equals(normalized) || "近7天".equals(range)) {
            return new DateRange("week", today.minusDays(6), today);
        }
        return new DateRange("today", today, today);
    }

    private LocalDate latestStatDate() {
        AnalyticsOverviewDaily latest = overviewDailyMapper.selectList(new QueryWrapper<AnalyticsOverviewDaily>()
                .eq("scenic_area_id", DEFAULT_SCENIC_AREA_ID)
                .orderByDesc("stat_date")
                .last("LIMIT 1"))
            .stream()
            .findFirst()
            .orElse(null);
        TouristMessage latestMessage = touristMessageMapper.selectList(new QueryWrapper<TouristMessage>()
                .orderByDesc("created_at")
                .last("LIMIT 1"))
            .stream()
            .findFirst()
            .orElse(null);
        LocalDate overviewDate = latest == null ? null : latest.getStatDate();
        LocalDate messageDate = latestMessage == null || latestMessage.getCreatedAt() == null
            ? null
            : latestMessage.getCreatedAt().toLocalDate();
        if (overviewDate == null) {
            return messageDate == null ? LocalDate.now() : messageDate;
        }
        if (messageDate == null) {
            return overviewDate;
        }
        return messageDate.isAfter(overviewDate) ? messageDate : overviewDate;
    }

    private String trendMeta(int current, int previous) {
        if (previous == 0) {
            return current == 0 ? "持平" : "+100%";
        }
        int rate = BigDecimal.valueOf(current - previous)
            .multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(previous), 0, RoundingMode.HALF_UP)
            .intValue();
        return (rate >= 0 ? "+" : "") + rate + "%";
    }

    private String trendType(int current, int previous) {
        if (current < previous) {
            return "down";
        }
        if (current == previous) {
            return "neutral";
        }
        return "up";
    }

    private String formatInteger(int value) {
        return String.format("%,d", value);
    }

    private String formatSeconds(int ms) {
        return BigDecimal.valueOf(ms)
            .divide(BigDecimal.valueOf(1000), 2, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString() + "s";
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private int defaultInt(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }

    private int percentValue(int value, int total) {
        if (total <= 0) {
            return 0;
        }
        return BigDecimal.valueOf(value)
            .multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(total), 0, RoundingMode.HALF_UP)
            .intValue();
    }

    private String stringValue(Map<String, Object> row, String key) {
        Object value = mapValue(row, key);
        return value == null ? "" : String.valueOf(value);
    }

    private int intValue(Map<String, Object> row, String key) {
        Object value = mapValue(row, key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return 0;
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private Object mapValue(Map<String, Object> row, String key) {
        if (row.containsKey(key)) {
            return row.get(key);
        }
        String camel = toCamelCase(key);
        if (row.containsKey(camel)) {
            return row.get(camel);
        }
        return row.get(key.toUpperCase(Locale.ROOT));
    }

    private String toCamelCase(String key) {
        StringBuilder result = new StringBuilder();
        boolean upperNext = false;
        for (char ch : key.toCharArray()) {
            if (ch == '_') {
                upperNext = true;
                continue;
            }
            result.append(upperNext ? Character.toUpperCase(ch) : ch);
            upperNext = false;
        }
        return result.toString();
    }

    private record DateRange(String label, LocalDate start, LocalDate end) {

        DateRange previous() {
            long days = java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1;
            LocalDate previousEnd = start.minusDays(1);
            return new DateRange(label + "-previous", previousEnd.minusDays(days - 1), previousEnd);
        }
    }

    private record AnalyticsSummary(int visitorCount,
                                    int sessionCount,
                                    int activeVisitorCount,
                                    int messageCount,
                                    int fenceTriggerCount,
                                    int routeRequestCount,
                                    int avgResponseMs,
                                    int positiveMessageCount,
                                    int neutralMessageCount,
                                    int negativeMessageCount,
                                    int complaintMessageCount) {
    }

    private record AiSummary(int chatCalls,
                             int ragSearches,
                             long promptTokens,
                             long completionTokens,
                             long totalTokens,
                             int ttsChars,
                             BigDecimal asrSeconds,
                             int ttsCacheHits,
                             int ttsCacheRequests,
                             int p95FirstTokenMs,
                             BigDecimal estimatedCost) {
    }
}
