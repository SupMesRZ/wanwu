package com.huohuo.scenicaiguide.tourist;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huohuo.scenicaiguide.digitalhuman.dto.response.TtsResponse;
import com.huohuo.scenicaiguide.digitalhuman.service.DigitalHumanService;
import com.huohuo.scenicaiguide.digitalhuman.service.TtsService;
import com.huohuo.scenicaiguide.qa.service.QaChatStreamService;
import com.huohuo.scenicaiguide.qa.service.QaGuideAnswerService;
import com.huohuo.scenicaiguide.route.dto.AiRouteResponse;
import com.huohuo.scenicaiguide.route.service.AiRouteService;
import com.huohuo.scenicaiguide.scenic.dto.request.MapWorkbenchDraftRequest;
import com.huohuo.scenicaiguide.scenic.service.ScenicTopologyService;
import com.huohuo.scenicaiguide.tourist.dto.request.LocationReportRequest;
import com.huohuo.scenicaiguide.tourist.dto.response.LocationReportResponse;
import com.huohuo.scenicaiguide.tourist.entity.TouristFenceLog;
import com.huohuo.scenicaiguide.tourist.entity.TouristGuideSession;
import com.huohuo.scenicaiguide.tourist.entity.TouristMessage;
import com.huohuo.scenicaiguide.tourist.mapper.TouristFenceLogMapper;
import com.huohuo.scenicaiguide.tourist.mapper.TouristGuideSessionMapper;
import com.huohuo.scenicaiguide.tourist.mapper.TouristLocationLogMapper;
import com.huohuo.scenicaiguide.tourist.mapper.TouristMessageMapper;
import com.huohuo.scenicaiguide.tourist.service.TouristGuideService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TouristGuideServiceRouteProgressTest {

    private static final BigDecimal LAT_A = new BigDecimal("30.1000000");
    private static final BigDecimal LNG_A = new BigDecimal("120.1000000");
    private static final BigDecimal LAT_B = new BigDecimal("30.1010000");
    private static final BigDecimal LNG_B = new BigDecimal("120.1020000");
    private static final BigDecimal LAT_E = new BigDecimal("30.1020000");
    private static final BigDecimal LNG_E = new BigDecimal("120.1040000");

    @Test
    void arrivalAtNextSpotAdvancesCursorAndNarrates() {
        TestFixture fixture = new TestFixture(route(List.of(
            node("s1", "入口", "SPOT", 1, LAT_A, LNG_A),
            node("s2", "书院", "SPOT", 2, LAT_B, LNG_B),
            node("s3", "茶亭", "SPOT", 3, LAT_E, LNG_E))));

        LocationReportResponse response = fixture.report(fixture.location(LocalDateTime.now(), LAT_B, LNG_B));

        assertThat(response.getGuideMessage()).isNotNull();
        assertThat(response.getGuideMessage().getAnswer())
            .startsWith("已抵达「书院」，这是「测试路线」第 2 站。")
            .contains("讲解词");
        assertThat(response.getRouteProgress().getCurrentSequence()).isEqualTo(2);
        assertThat(response.getRouteProgress().getArrivedNodeName()).isEqualTo("书院");
        assertThat(response.getRouteProgress().getNextNodeName()).isEqualTo("茶亭");
        assertThat(response.getRouteProgress().isFinished()).isFalse();
        assertThat(fixture.session.getRouteNodeSeq()).isEqualTo(2);
        // 到站播报要补 TRIGGERED 围栏日志，否则停留判定会把同一景点再讲一遍
        assertThat(fixture.fenceLogs).anyMatch(log -> "ROUTE_ARRIVAL".equals(log.getTriggerType())
            && "TRIGGERED".equals(log.getBroadcastStatus()) && "s2".equals(log.getSpotId()));
    }

    @Test
    void endpointNodeAdvancesSilently() {
        TestFixture fixture = new TestFixture(route(List.of(
            node("s1", "入口", "SPOT", 1, LAT_A, LNG_A),
            node("e1", "岔路口", "ENDPOINT", 2, LAT_B, LNG_B),
            node("s2", "书院", "SPOT", 3, LAT_E, LNG_E))));

        LocationReportResponse response = fixture.report(fixture.location(LocalDateTime.now(), LAT_B, LNG_B));

        // 途经点不是讲解对象，只推进游标
        assertThat(response.getGuideMessage()).isNull();
        assertThat(response.getRouteProgress().getCurrentSequence()).isEqualTo(2);
        assertThat(response.getRouteProgress().getNextNodeName()).isEqualTo("书院");
        assertThat(fixture.fenceLogs).noneMatch(log -> "TRIGGERED".equals(log.getBroadcastStatus()));
    }

    @Test
    void arrivalWithinCooldownAdvancesWithoutNarration() {
        TestFixture fixture = new TestFixture(route(List.of(
            node("s1", "入口", "SPOT", 1, LAT_A, LNG_A),
            node("s2", "书院", "SPOT", 2, LAT_B, LNG_B))));
        TouristFenceLog recent = new TouristFenceLog();
        recent.setSpotId("s2");
        recent.setBroadcastStatus("TRIGGERED");
        recent.setReportedAt(LocalDateTime.now().minusMinutes(10));
        fixture.fenceLogs.add(recent);

        LocationReportResponse response = fixture.report(fixture.location(LocalDateTime.now(), LAT_B, LNG_B));

        // 冷却期内游标照样推进，只是不再讲一遍
        assertThat(response.getGuideMessage()).isNull();
        assertThat(response.getRouteProgress().getCurrentSequence()).isEqualTo(2);
        assertThat(response.getRouteProgress().isFinished()).isTrue();
        assertThat(fixture.savedMessages).isEmpty();
    }

    @Test
    void routeArrivalSuppressesLaterFenceDwellNarration() {
        TestFixture fixture = new TestFixture(route(List.of(
            node("s1", "入口", "SPOT", 1, LAT_A, LNG_A),
            node("s2", "书院", "SPOT", 2, LAT_B, LNG_B))));
        LocalDateTime now = LocalDateTime.now();

        LocationReportResponse arrival = fixture.report(fixture.location(now, LAT_B, LNG_B));
        LocationReportResponse dwell = fixture.report(fixture.location(now.plusSeconds(13), LAT_B, LNG_B));

        assertThat(arrival.getGuideMessage()).isNotNull();
        // 到站已讲过，停留触发的围栏判定进入冷却，不重复播报
        assertThat(dwell.getGuideMessage()).isNull();
        assertThat(dwell.getState()).isEqualTo("COOLDOWN");
        assertThat(fixture.savedMessages).hasSize(1);
    }

    @Test
    void noActiveRouteYieldsNoProgress() {
        TestFixture fixture = new TestFixture(null);

        LocationReportResponse response = fixture.report(fixture.location(LocalDateTime.now(), LAT_B, LNG_B));

        assertThat(response.getRouteProgress()).isNull();
    }

    @Test
    void newRouteResetsCursorToStartNode() {
        TestFixture fixture = new TestFixture(null);
        fixture.session.setRouteNodeSeq(5);
        AiRouteResponse recommended = route(List.of(
            node("s1", "入口", "SPOT", 1, LAT_A, LNG_A),
            node("s2", "书院", "SPOT", 2, LAT_B, LNG_B)));
        recommended.setId(42L);

        fixture.service.completeStream(1L, 1L, "答案", "EVIDENCE", null, recommended,
            new com.huohuo.scenicaiguide.route.dto.AiRoutePlanRequest(), false);

        assertThat(fixture.session.getCurrentRouteId()).isEqualTo(42L);
        assertThat(fixture.session.getRouteNodeSeq()).isEqualTo(1);
    }

    private static AiRouteResponse route(List<AiRouteResponse.RouteNode> nodes) {
        AiRouteResponse route = new AiRouteResponse();
        route.setId(9L);
        route.setName("测试路线");
        route.setNodes(nodes);
        return route;
    }

    private static AiRouteResponse.RouteNode node(String id, String name, String type, int sequence,
                                                  BigDecimal lat, BigDecimal lng) {
        AiRouteResponse.RouteNode node = new AiRouteResponse.RouteNode();
        node.setId(id);
        node.setName(name);
        node.setType(type);
        node.setDescription(name + "讲解描述");
        node.setSequence(sequence);
        node.setLatitude(lat);
        node.setLongitude(lng);
        return node;
    }

    private static class TestFixture {
        final TouristGuideSession session;
        final List<TouristFenceLog> fenceLogs = new ArrayList<>();
        final List<TouristMessage> savedMessages = new ArrayList<>();
        final TouristGuideService service;

        TestFixture(AiRouteResponse activeRoute) {
            TouristGuideSessionMapper sessionMapper = mock(TouristGuideSessionMapper.class);
            TouristMessageMapper messageMapper = mock(TouristMessageMapper.class);
            TouristFenceLogMapper fenceLogMapper = mock(TouristFenceLogMapper.class);
            ScenicTopologyService topologyService = mock(ScenicTopologyService.class);
            QaGuideAnswerService qaGuideAnswerService = mock(QaGuideAnswerService.class);
            AiRouteService aiRouteService = mock(AiRouteService.class);
            TtsService ttsService = mock(TtsService.class);

            session = new TouristGuideSession();
            session.setId(1L);
            session.setVisitorSubject("user_7");
            session.setScenicAreaId(1L);
            session.setStatus("ACTIVE");
            if (activeRoute != null) {
                session.setCurrentRouteId(activeRoute.getId());
                session.setRouteNodeSeq(1);
                when(aiRouteService.loadRoute(activeRoute.getId())).thenReturn(activeRoute);
            }
            when(sessionMapper.selectById(1L)).thenReturn(session);
            when(sessionMapper.updateById(any(TouristGuideSession.class))).thenAnswer(invocation -> {
                TouristGuideSession updated = invocation.getArgument(0);
                session.setLastLocationLat(updated.getLastLocationLat());
                session.setLastLocationLng(updated.getLastLocationLng());
                session.setLastLocationAt(updated.getLastLocationAt());
                session.setCurrentSpotId(updated.getCurrentSpotId());
                session.setCurrentRouteId(updated.getCurrentRouteId());
                session.setRouteNodeSeq(updated.getRouteNodeSeq());
                session.setUpdatedAt(updated.getUpdatedAt());
                return 1;
            });
            when(messageMapper.insert(any(TouristMessage.class))).thenAnswer(invocation -> {
                savedMessages.add(invocation.getArgument(0));
                return 1;
            });
            when(fenceLogMapper.insert(any(TouristFenceLog.class))).thenAnswer(invocation -> {
                fenceLogs.add(invocation.getArgument(0));
                return 1;
            });
            when(fenceLogMapper.selectList(any())).thenAnswer(invocation -> filterFenceLogs(invocation.getArgument(0)));

            MapWorkbenchDraftRequest.SpotDraft spot = new MapWorkbenchDraftRequest.SpotDraft();
            spot.setId("s2");
            spot.setName("书院");
            spot.setDescription("书院讲解描述");
            spot.setRadius(50);
            spot.setStatus(1);
            spot.setLabelPoint(List.of(LAT_B, LNG_B));
            ScenicTopologyService.PublishedTopology topology =
                new ScenicTopologyService.PublishedTopology(1L, 1L, new MapWorkbenchDraftRequest());
            when(topologyService.getPublishedTopology(1L)).thenReturn(topology);
            when(topologyService.nearestSpot(eq(topology), any(), any())).thenReturn(spot);
            when(topologyService.resolvePoint(spot)).thenReturn(spot.getLabelPoint());
            // 简化版距离：经纬度差折算米数，落在上报点 50 米抵达半径内才算到站
            when(topologyService.distanceMeters(any(), any(), any(), any())).thenAnswer(invocation -> {
                BigDecimal lat1 = invocation.getArgument(0);
                BigDecimal lng1 = invocation.getArgument(1);
                BigDecimal lat2 = invocation.getArgument(2);
                BigDecimal lng2 = invocation.getArgument(3);
                return (Math.abs(lat1.doubleValue() - lat2.doubleValue())
                    + Math.abs(lng1.doubleValue() - lng2.doubleValue())) * 111_000D;
            });

            when(qaGuideAnswerService.guideScript(eq(1L), anyString(), anyString()))
                .thenAnswer(invocation -> new QaGuideAnswerService.AnswerResult(
                    "讲解词", "GUIDE_SCRIPT", "GUIDE_SCRIPT#1"));
            TtsResponse tts = new TtsResponse();
            tts.setAudioBase64("audio");
            when(ttsService.synthesize(eq(1L), anyString())).thenReturn(tts);

            service = new TouristGuideService(
                sessionMapper,
                messageMapper,
                mock(TouristLocationLogMapper.class),
                fenceLogMapper,
                topologyService,
                qaGuideAnswerService,
                mock(QaChatStreamService.class),
                mock(DigitalHumanService.class),
                ttsService,
                aiRouteService,
                new ObjectMapper()
            );
        }

        LocationReportRequest location(LocalDateTime reportedAt, BigDecimal lat, BigDecimal lng) {
            LocationReportRequest request = new LocationReportRequest();
            request.setLatitude(lat);
            request.setLongitude(lng);
            request.setAccuracyMeter(15);
            request.setReportedAt(reportedAt);
            return request;
        }

        LocationReportResponse report(LocationReportRequest request) {
            return service.reportLocation("user_7", 1L, request);
        }

        private List<TouristFenceLog> filterFenceLogs(Object wrapper) {
            String query = wrapper instanceof QueryWrapper<?> queryWrapper ? queryWrapper.getSqlSegment() : String.valueOf(wrapper);
            if (query.contains("broadcast_status")) {
                return fenceLogs.stream()
                    .filter(log -> "TRIGGERED".equals(log.getBroadcastStatus()))
                    .sorted((left, right) -> right.getReportedAt().compareTo(left.getReportedAt()))
                    .limit(1)
                    .toList();
            }
            if (query.contains("trigger_type")) {
                return fenceLogs.stream()
                    .filter(log -> "EXIT".equals(log.getTriggerType()))
                    .sorted((left, right) -> right.getReportedAt().compareTo(left.getReportedAt()))
                    .limit(1)
                    .toList();
            }
            if (query.contains("IN")) {
                return fenceLogs.stream()
                    .filter(log -> List.of("ENTERING", "INSIDE", "COOLDOWN").contains(log.getTriggerState()))
                    .sorted((left, right) -> left.getReportedAt().compareTo(right.getReportedAt()))
                    .limit(1)
                    .toList();
            }
            return fenceLogs.stream()
                .sorted((left, right) -> right.getReportedAt().compareTo(left.getReportedAt()))
                .limit(1)
                .toList();
        }
    }
}
