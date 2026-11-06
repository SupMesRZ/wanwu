package com.huohuo.scenicaiguide.analytics.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huohuo.scenicaiguide.analytics.dto.response.FenceStatsResponse;
import com.huohuo.scenicaiguide.analytics.dto.response.HeatmapSpotResponse;
import com.huohuo.scenicaiguide.analytics.dto.response.HotQuestionResponse;
import com.huohuo.scenicaiguide.analytics.dto.response.RouteStatsResponse;
import com.huohuo.scenicaiguide.common.api.PageResponse;
import com.huohuo.scenicaiguide.route.entity.RouteAiRoute;
import com.huohuo.scenicaiguide.route.mapper.RouteAiRouteMapper;
import com.huohuo.scenicaiguide.tourist.entity.TouristFenceLog;
import com.huohuo.scenicaiguide.tourist.entity.TouristLocationLog;
import com.huohuo.scenicaiguide.tourist.entity.TouristMessage;
import com.huohuo.scenicaiguide.tourist.mapper.TouristFenceLogMapper;
import com.huohuo.scenicaiguide.tourist.mapper.TouristLocationLogMapper;
import com.huohuo.scenicaiguide.tourist.mapper.TouristMessageMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class AnalyticsLiveService {

    private final TouristMessageMapper messageMapper;
    private final TouristLocationLogMapper locationLogMapper;
    private final TouristFenceLogMapper fenceLogMapper;
    private final RouteAiRouteMapper aiRouteMapper;

    public AnalyticsLiveService(TouristMessageMapper messageMapper,
                                TouristLocationLogMapper locationLogMapper,
                                TouristFenceLogMapper fenceLogMapper,
                                RouteAiRouteMapper aiRouteMapper) {
        this.messageMapper = messageMapper;
        this.locationLogMapper = locationLogMapper;
        this.fenceLogMapper = fenceLogMapper;
        this.aiRouteMapper = aiRouteMapper;
    }

    @Transactional(readOnly = true)
    public PageResponse<HotQuestionResponse> listHotQuestions(String keyword, String range, long page, long pageSize) {
        DateTimeRange dateRange = resolveDateTimeRange(range);
        QueryWrapper<TouristMessage> wrapper = new QueryWrapper<TouristMessage>()
            .select("content AS question", "COUNT(*) AS question_count", "MAX(created_at) AS latest_at")
            .eq("sender", "TOURIST")
            .between("created_at", dateRange.start(), dateRange.end())
            .groupBy("content")
            .orderByDesc("question_count")
            .orderByDesc("latest_at");
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like("content", keyword.trim());
        }
        Page<Map<String, Object>> result = messageMapper.selectMapsPage(new Page<>(page, pageSize), wrapper);
        AtomicInteger rank = rankCounter(result.offset());
        List<HotQuestionResponse> records = result.getRecords().stream()
            .map(row -> toHotQuestion(row, rank.getAndIncrement()))
            .toList();
        return PageResponse.of(records, result.getCurrent(), result.getSize(), result.getTotal());
    }

    @Transactional(readOnly = true)
    public PageResponse<HeatmapSpotResponse> listHeatmap(String keyword, String range, long page, long pageSize) {
        DateTimeRange dateRange = resolveDateTimeRange(range);
        QueryWrapper<TouristLocationLog> wrapper = new QueryWrapper<TouristLocationLog>()
            .select(
                "ROUND(longitude, 4) AS longitude",
                "ROUND(latitude, 4) AS latitude",
                "COUNT(DISTINCT session_id) AS visitor_count",
                "AVG(accuracy_meter) AS avg_accuracy_meter",
                "MIN(reported_at) AS first_reported_at",
                "MAX(reported_at) AS latest_reported_at")
            .eq("accepted", 1)
            .between("reported_at", dateRange.start(), dateRange.end())
            .groupBy("ROUND(longitude, 4)", "ROUND(latitude, 4)")
            .orderByDesc("visitor_count")
            .orderByDesc("latest_reported_at");
        if (keyword != null && !keyword.isBlank()) {
            wrapper.apply("CONCAT(ROUND(longitude, 4), ',', ROUND(latitude, 4)) LIKE {0}", "%" + keyword.trim() + "%");
        }
        Page<Map<String, Object>> result = locationLogMapper.selectMapsPage(new Page<>(page, pageSize), wrapper);
        AtomicInteger rank = rankCounter(result.offset());
        List<HeatmapSpotResponse> records = result.getRecords().stream()
            .map(row -> toHeatmapSpot(row, rank.getAndIncrement()))
            .toList();
        return PageResponse.of(records, result.getCurrent(), result.getSize(), result.getTotal());
    }

    @Transactional(readOnly = true)
    public PageResponse<RouteStatsResponse> listRouteStats(String keyword, String range, long page, long pageSize) {
        DateTimeRange dateRange = resolveDateTimeRange(range);
        QueryWrapper<RouteAiRoute> wrapper = new QueryWrapper<RouteAiRoute>()
            .select(
                "name AS route_name",
                "COUNT(*) AS selected_count",
                "SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END) AS completed_count",
                "AVG(estimated_duration_min) AS avg_duration_min",
                "MAX(created_at) AS latest_created_at")
            .between("created_at", dateRange.start(), dateRange.end())
            .groupBy("name")
            .orderByDesc("selected_count")
            .orderByDesc("latest_created_at");
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like("name", keyword.trim());
        }
        Page<Map<String, Object>> result = aiRouteMapper.selectMapsPage(new Page<>(page, pageSize), wrapper);
        AtomicInteger rank = rankCounter(result.offset());
        List<RouteStatsResponse> records = result.getRecords().stream()
            .map(row -> toRouteStats(row, rank.getAndIncrement()))
            .toList();
        return PageResponse.of(records, result.getCurrent(), result.getSize(), result.getTotal());
    }

    @Transactional(readOnly = true)
    public PageResponse<FenceStatsResponse> listFenceStats(String keyword, String range, long page, long pageSize) {
        DateTimeRange dateRange = resolveDateTimeRange(range);
        QueryWrapper<TouristFenceLog> wrapper = new QueryWrapper<TouristFenceLog>()
            .select(
                "spot_id",
                "COALESCE(spot_name, spot_id) AS spot_name",
                "COUNT(*) AS trigger_count",
                "SUM(CASE WHEN broadcast_status = 'TRIGGERED' THEN 1 ELSE 0 END) AS broadcast_count",
                "COUNT(*) - COUNT(DISTINCT session_id) AS repeat_count",
                "MAX(reported_at) AS latest_triggered_at")
            .between("reported_at", dateRange.start(), dateRange.end())
            .groupBy("spot_id", "spot_name")
            .orderByDesc("trigger_count")
            .orderByDesc("latest_triggered_at");
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(item -> item.like("spot_name", keyword.trim()).or().like("spot_id", keyword.trim()));
        }
        Page<Map<String, Object>> result = fenceLogMapper.selectMapsPage(new Page<>(page, pageSize), wrapper);
        AtomicInteger rank = rankCounter(result.offset());
        List<FenceStatsResponse> records = result.getRecords().stream()
            .map(row -> toFenceStats(row, rank.getAndIncrement()))
            .toList();
        return PageResponse.of(records, result.getCurrent(), result.getSize(), result.getTotal());
    }

    private HotQuestionResponse toHotQuestion(Map<String, Object> row, int rank) {
        HotQuestionResponse response = new HotQuestionResponse();
        response.setRank(rank);
        response.setQuestion(stringValue(row, "question"));
        response.setCount(longValue(row, "question_count"));
        response.setSource("游客提问");
        response.setLatestAt(dateTimeValue(row, "latest_at"));
        return response;
    }

    private HeatmapSpotResponse toHeatmapSpot(Map<String, Object> row, int rank) {
        BigDecimal longitude = decimalValue(row, "longitude");
        BigDecimal latitude = decimalValue(row, "latitude");
        long visitors = longValue(row, "visitor_count");
        HeatmapSpotResponse response = new HeatmapSpotResponse();
        response.setRank(rank);
        response.setLongitude(longitude);
        response.setLatitude(latitude);
        response.setArea(latitude + "," + longitude);
        response.setVisitors(visitors);
        response.setAvgAccuracyMeter(decimalValue(row, "avg_accuracy_meter").setScale(0, RoundingMode.HALF_UP).intValue());
        response.setCrowdLevel(crowdLevel(visitors));
        response.setFirstReportedAt(dateTimeValue(row, "first_reported_at"));
        response.setLatestReportedAt(dateTimeValue(row, "latest_reported_at"));
        return response;
    }

    private RouteStatsResponse toRouteStats(Map<String, Object> row, int rank) {
        long selected = longValue(row, "selected_count");
        long completed = longValue(row, "completed_count");
        RouteStatsResponse response = new RouteStatsResponse();
        response.setRank(rank);
        response.setName(stringValue(row, "route_name"));
        response.setSelectedCount(selected);
        response.setCompletedCount(completed);
        response.setCompletionRate(percent(completed, selected));
        response.setAvgDurationMin(decimalValue(row, "avg_duration_min").setScale(0, RoundingMode.HALF_UP).intValue());
        response.setLatestCreatedAt(dateTimeValue(row, "latest_created_at"));
        return response;
    }

    private FenceStatsResponse toFenceStats(Map<String, Object> row, int rank) {
        long trigger = longValue(row, "trigger_count");
        long broadcast = longValue(row, "broadcast_count");
        long repeat = longValue(row, "repeat_count");
        FenceStatsResponse response = new FenceStatsResponse();
        response.setRank(rank);
        response.setSpotId(stringValue(row, "spot_id"));
        response.setName(stringValue(row, "spot_name"));
        response.setTriggerCount(trigger);
        response.setBroadcastCount(broadcast);
        response.setBroadcastRate(percent(broadcast, trigger));
        response.setRepeatCount(repeat);
        response.setRepeatRate(percent(repeat, trigger));
        response.setLatestTriggeredAt(dateTimeValue(row, "latest_triggered_at"));
        return response;
    }

    private DateTimeRange resolveDateTimeRange(String range) {
        LocalDate today = LocalDate.now();
        String normalized = range == null ? "month" : range.trim().toLowerCase();
        LocalDate start = switch (normalized) {
            case "today" -> today;
            case "week" -> today.minusDays(6);
            default -> today.minusDays(29);
        };
        return new DateTimeRange(start.atStartOfDay(), today.atTime(LocalTime.MAX));
    }

    private AtomicInteger rankCounter(long offset) {
        return new AtomicInteger((int) offset + 1);
    }

    private String crowdLevel(long visitors) {
        if (visitors >= 50) {
            return "高";
        }
        if (visitors >= 10) {
            return "中";
        }
        return "低";
    }

    private String percent(long numerator, long denominator) {
        if (denominator <= 0) {
            return "0%";
        }
        return BigDecimal.valueOf(numerator)
            .multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(denominator), 0, RoundingMode.HALF_UP)
            .toPlainString() + "%";
    }

    private String stringValue(Map<String, Object> row, String key) {
        Object value = value(row, key);
        return value == null ? "" : String.valueOf(value);
    }

    private long longValue(Map<String, Object> row, String key) {
        Object value = value(row, key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return 0L;
        }
        return Long.parseLong(String.valueOf(value));
    }

    private BigDecimal decimalValue(Map<String, Object> row, String key) {
        Object value = value(row, key);
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(String.valueOf(value));
    }

    private LocalDateTime dateTimeValue(Map<String, Object> row, String key) {
        Object value = value(row, key);
        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return LocalDateTime.parse(String.valueOf(value).replace(" ", "T"));
    }

    private Object value(Map<String, Object> row, String key) {
        if (row.containsKey(key)) {
            return row.get(key);
        }
        String camel = toCamelCase(key);
        if (row.containsKey(camel)) {
            return row.get(camel);
        }
        return row.get(key.toUpperCase());
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

    private record DateTimeRange(LocalDateTime start, LocalDateTime end) {
    }
}
