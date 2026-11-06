package com.huohuo.scenicaiguide.analytics.controller;

import com.huohuo.scenicaiguide.analytics.dto.response.AnalyticsDashboardResponse;
import com.huohuo.scenicaiguide.analytics.dto.response.FenceStatsResponse;
import com.huohuo.scenicaiguide.analytics.dto.response.HeatmapSpotResponse;
import com.huohuo.scenicaiguide.analytics.dto.response.HotQuestionResponse;
import com.huohuo.scenicaiguide.analytics.dto.response.RouteStatsResponse;
import com.huohuo.scenicaiguide.analytics.dto.response.WordCloudResponse;
import com.huohuo.scenicaiguide.analytics.service.AnalyticsDashboardService;
import com.huohuo.scenicaiguide.analytics.service.AnalyticsLiveService;
import com.huohuo.scenicaiguide.analytics.service.AnalyticsWordCloudService;
import com.huohuo.scenicaiguide.common.api.ApiResponse;
import com.huohuo.scenicaiguide.common.api.PageResponse;
import com.huohuo.scenicaiguide.system.security.PermissionCode;
import com.huohuo.scenicaiguide.system.security.RequireAdmin;
import com.huohuo.scenicaiguide.system.security.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Tag(name = "运营分析", description = "后台运营数据概览")
@RestController
@RequestMapping("/api/v1/admin/analytics")
@RequireAdmin
@RequirePermission(PermissionCode.ANALYTICS_READ)
public class AnalyticsDashboardController {

    private final AnalyticsDashboardService analyticsDashboardService;
    private final AnalyticsLiveService analyticsLiveService;
    private final AnalyticsWordCloudService wordCloudService;

    @Autowired
    public AnalyticsDashboardController(AnalyticsDashboardService analyticsDashboardService,
                                        AnalyticsLiveService analyticsLiveService,
                                        AnalyticsWordCloudService wordCloudService) {
        this.analyticsDashboardService = analyticsDashboardService;
        this.analyticsLiveService = analyticsLiveService;
        this.wordCloudService = wordCloudService;
    }

    @Operation(summary = "查询运营数据概览")
    @GetMapping("/dashboard")
    public ApiResponse<AnalyticsDashboardResponse> getDashboard(
            @RequestParam(defaultValue = "today") String range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ApiResponse.ok(analyticsDashboardService.getDashboard(range, startDate, endDate));
    }

    @Operation(summary = "查询热门问题")
    @GetMapping("/hot-questions")
    public ApiResponse<PageResponse<HotQuestionResponse>> listHotQuestions(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "month") String range,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.ok(analyticsLiveService.listHotQuestions(keyword, range, page, pageSize));
    }

    @Operation(summary = "查询游客提问词云")
    @GetMapping("/word-cloud")
    public ApiResponse<WordCloudResponse> getWordCloud(
            @RequestParam(defaultValue = "today") String range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ApiResponse.ok(wordCloudService.wordCloud(range, startDate, endDate));
    }

    @Operation(summary = "查询游客热力点")
    @GetMapping("/heatmap")
    public ApiResponse<PageResponse<HeatmapSpotResponse>> listHeatmap(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "month") String range,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.ok(analyticsLiveService.listHeatmap(keyword, range, page, pageSize));
    }

    @Operation(summary = "查询路线统计")
    @GetMapping("/route-stats")
    public ApiResponse<PageResponse<RouteStatsResponse>> listRouteStats(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "month") String range,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.ok(analyticsLiveService.listRouteStats(keyword, range, page, pageSize));
    }

    @Operation(summary = "查询围栏统计")
    @GetMapping("/fence-stats")
    public ApiResponse<PageResponse<FenceStatsResponse>> listFenceStats(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "month") String range,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.ok(analyticsLiveService.listFenceStats(keyword, range, page, pageSize));
    }
}
