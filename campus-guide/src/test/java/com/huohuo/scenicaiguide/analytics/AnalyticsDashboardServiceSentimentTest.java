package com.huohuo.scenicaiguide.analytics;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.huohuo.scenicaiguide.analytics.mapper.AnalyticsAiUsageDailyMapper;
import com.huohuo.scenicaiguide.analytics.mapper.AnalyticsOverviewDailyMapper;
import com.huohuo.scenicaiguide.analytics.mapper.AnalyticsRouteDailyMapper;
import com.huohuo.scenicaiguide.analytics.mapper.AnalyticsSpotDailyMapper;
import com.huohuo.scenicaiguide.analytics.service.AnalyticsDashboardService;
import com.huohuo.scenicaiguide.digitalhuman.mapper.DhTtsCacheMapper;
import com.huohuo.scenicaiguide.route.mapper.RouteAiRouteMapper;
import com.huohuo.scenicaiguide.system.service.ConfigService;
import com.huohuo.scenicaiguide.tourist.mapper.TouristFenceLogMapper;
import com.huohuo.scenicaiguide.tourist.mapper.TouristGuideSessionMapper;
import com.huohuo.scenicaiguide.tourist.mapper.TouristMessageMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalyticsDashboardServiceSentimentTest {

    @Test
    void sentimentSampleIsCappedByConfiguredBatchSize() {
        TouristMessageMapper messageMapper = mock(TouristMessageMapper.class);
        ConfigService configService = mock(ConfigService.class);
        AnalyticsDashboardService service = service(messageMapper, configService);
        when(configService.getInt("analytics-sentiment-batch-size", 100)).thenReturn(2);
        when(messageMapper.selectMaps(any())).thenReturn(List.of());

        service.getDashboard("today", null, null);

        // 情感分析只扫最新一批消息，条数由配置中心控制，避免大区间全表扫描
        @SuppressWarnings({"rawtypes", "unchecked"})
        ArgumentCaptor<Wrapper> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(messageMapper, org.mockito.Mockito.atLeastOnce()).selectMaps(captor.capture());
        assertThat(captor.getAllValues())
            .anyMatch(wrapper -> wrapper.getSqlSegment().contains("LIMIT 2"));
    }

    @Test
    void configFailureFallsBackToDefaultBatchSize() {
        TouristMessageMapper messageMapper = mock(TouristMessageMapper.class);
        ConfigService configService = mock(ConfigService.class);
        AnalyticsDashboardService service = service(messageMapper, configService);
        when(configService.getInt(anyString(), anyInt())).thenThrow(new RuntimeException("配置中心不可用"));
        when(messageMapper.selectMaps(any())).thenReturn(List.of());

        service.getDashboard("today", null, null);

        @SuppressWarnings({"rawtypes", "unchecked"})
        ArgumentCaptor<Wrapper> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(messageMapper, org.mockito.Mockito.atLeastOnce()).selectMaps(captor.capture());
        assertThat(captor.getAllValues())
            .anyMatch(wrapper -> wrapper.getSqlSegment().contains("LIMIT 100"));
    }

    private static AnalyticsDashboardService service(TouristMessageMapper messageMapper,
                                                     ConfigService configService) {
        AnalyticsOverviewDailyMapper overviewDailyMapper = mock(AnalyticsOverviewDailyMapper.class);
        TouristGuideSessionMapper guideSessionMapper = mock(TouristGuideSessionMapper.class);
        TouristFenceLogMapper fenceLogMapper = mock(TouristFenceLogMapper.class);
        RouteAiRouteMapper aiRouteMapper = mock(RouteAiRouteMapper.class);
        DhTtsCacheMapper ttsCacheMapper = mock(DhTtsCacheMapper.class);
        when(guideSessionMapper.selectMaps(any())).thenReturn(List.of());
        when(guideSessionMapper.selectCount(any())).thenReturn(0L);
        when(messageMapper.selectCount(any())).thenReturn(0L);
        when(messageMapper.selectList(any())).thenReturn(List.of());
        when(fenceLogMapper.selectCount(any())).thenReturn(0L);
        when(fenceLogMapper.selectMaps(any())).thenReturn(List.of());
        when(aiRouteMapper.selectCount(any())).thenReturn(0L);
        when(ttsCacheMapper.selectMaps(any())).thenReturn(List.of());
        when(overviewDailyMapper.selectList(any())).thenReturn(List.of());
        return new AnalyticsDashboardService(
            overviewDailyMapper,
            mock(AnalyticsAiUsageDailyMapper.class),
            mock(AnalyticsSpotDailyMapper.class),
            mock(AnalyticsRouteDailyMapper.class),
            messageMapper,
            guideSessionMapper,
            fenceLogMapper,
            aiRouteMapper,
            ttsCacheMapper,
            configService
        );
    }
}
