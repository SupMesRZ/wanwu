package com.huohuo.scenicaiguide.tourist;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.huohuo.scenicaiguide.digitalhuman.dto.response.TtsResponse;
import com.huohuo.scenicaiguide.digitalhuman.service.DigitalHumanService;
import com.huohuo.scenicaiguide.digitalhuman.service.TtsService;
import com.huohuo.scenicaiguide.qa.service.QaGuideAnswerService;
import com.huohuo.scenicaiguide.qa.service.QaChatStreamService;
import com.huohuo.scenicaiguide.route.service.AiRouteService;
import com.huohuo.scenicaiguide.scenic.dto.request.MapWorkbenchDraftRequest;
import com.huohuo.scenicaiguide.scenic.service.ScenicTopologyService;
import com.huohuo.scenicaiguide.tourist.dto.request.LocationReportRequest;
import com.huohuo.scenicaiguide.tourist.dto.response.LocationReportResponse;
import com.huohuo.scenicaiguide.tourist.entity.TouristFenceLog;
import com.huohuo.scenicaiguide.tourist.entity.TouristGuideSession;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TouristGuideServiceFenceTest {

    @Test
    void dwellTriggersOnceAndCooldownPreventsRepeat() {
        TestFixture fixture = new TestFixture();
        LocalDateTime now = LocalDateTime.now();

        LocationReportResponse entering = fixture.report(fixture.location(now, 38.8819866, 115.5662597));
        LocationReportResponse dwell = fixture.report(fixture.location(now.plusSeconds(13), 38.8819866, 115.5662597));
        LocationReportResponse cooldown = fixture.report(fixture.location(now.plusSeconds(20), 38.8819866, 115.5662597));

        assertThat(entering.getState()).isEqualTo("ENTERING");
        assertThat(entering.getGuideMessage()).isNull();
        assertThat(dwell.getState()).isEqualTo("INSIDE");
        assertThat(dwell.getGuideMessage().getMessageType()).isEqualTo("GUIDE");
        assertThat(cooldown.getState()).isEqualTo("COOLDOWN");
        assertThat(cooldown.getGuideMessage()).isNull();
    }

    @Test
    void exitResetsDwellWindowBeforeNextTrigger() {
        TestFixture fixture = new TestFixture();
        LocalDateTime now = LocalDateTime.now();

        LocationReportResponse entering = fixture.report(fixture.location(now, 38.8819866, 115.5662597));
        LocationReportResponse outside = fixture.report(fixture.location(now.plusSeconds(10), 38.8829, 115.5675));
        LocationReportResponse reentering = fixture.report(fixture.location(now.plusSeconds(20), 38.8819866, 115.5662597));
        LocationReportResponse dwell = fixture.report(fixture.location(now.plusSeconds(33), 38.8819866, 115.5662597));

        assertThat(entering.getState()).isEqualTo("ENTERING");
        assertThat(outside.getState()).isEqualTo("OUTSIDE");
        assertThat(reentering.getState()).isEqualTo("ENTERING");
        assertThat(dwell.getState()).isEqualTo("INSIDE");
        assertThat(dwell.getGuideMessage()).isNotNull();
    }

    @Test
    void futureLocationTimeIsRejected() {
        TestFixture fixture = new TestFixture();

        LocationReportResponse response = fixture.report(
            fixture.location(LocalDateTime.now().plusMinutes(10), 38.8819866, 115.5662597));

        assertThat(response.isAccepted()).isFalse();
        assertThat(response.getState()).isEqualTo("REJECTED");
    }

    @Test
    void unreasonableLocationJumpIsRejectedBeforeFenceDecision() {
        TestFixture fixture = new TestFixture();
        LocalDateTime now = LocalDateTime.now();

        LocationReportResponse first = fixture.report(
            fixture.location(now, 38.8819866, 115.5662597));
        LocationReportResponse jumped = fixture.report(
            fixture.location(now.plusSeconds(5), 38.8919866, 115.5862597));

        assertThat(first.isAccepted()).isTrue();
        assertThat(jumped.isAccepted()).isFalse();
        assertThat(jumped.getState()).isEqualTo("REJECTED");
    }

    private static class TestFixture {
        final TouristFenceLogMapper fenceLogMapper = mock(TouristFenceLogMapper.class);
        final TouristGuideService service;

        TestFixture() {
            TouristGuideSessionMapper sessionMapper = mock(TouristGuideSessionMapper.class);
            TouristMessageMapper messageMapper = mock(TouristMessageMapper.class);
            TouristLocationLogMapper locationLogMapper = mock(TouristLocationLogMapper.class);
            ScenicTopologyService topologyService = mock(ScenicTopologyService.class);
            QaGuideAnswerService qaGuideAnswerService = mock(QaGuideAnswerService.class);
            AiRouteService aiRouteService = mock(AiRouteService.class);
            DigitalHumanService digitalHumanService = mock(DigitalHumanService.class);
            TtsService ttsService = mock(TtsService.class);

            TouristGuideSession session = new TouristGuideSession();
            session.setId(1L);
            session.setVisitorSubject("user_7");
            session.setScenicAreaId(1L);
            session.setStatus("ACTIVE");
            when(sessionMapper.selectById(1L)).thenReturn(session);
            when(sessionMapper.updateById(any(TouristGuideSession.class))).thenAnswer(invocation -> {
                TouristGuideSession updated = invocation.getArgument(0);
                session.setLastLocationLat(updated.getLastLocationLat());
                session.setLastLocationLng(updated.getLastLocationLng());
                session.setLastLocationAt(updated.getLastLocationAt());
                session.setCurrentSpotId(updated.getCurrentSpotId());
                session.setUpdatedAt(updated.getUpdatedAt());
                return 1;
            });

            MapWorkbenchDraftRequest.SpotDraft spot = new MapWorkbenchDraftRequest.SpotDraft();
            spot.setId("spot_1");
            spot.setName("图书馆");
            spot.setDescription("图书馆是游客自动讲解点");
            spot.setRadius(50);
            spot.setStatus(1);
            spot.setLabelPoint(List.of(new BigDecimal("38.8819866"), new BigDecimal("115.5662597")));

            ScenicTopologyService.PublishedTopology topology = new ScenicTopologyService.PublishedTopology(1L, 1L, new MapWorkbenchDraftRequest());
            when(topologyService.getPublishedTopology(1L)).thenReturn(topology);
            when(topologyService.nearestSpot(eq(topology), any(), any())).thenReturn(spot);
            when(topologyService.resolvePoint(spot)).thenReturn(spot.getLabelPoint());
            when(topologyService.distanceMeters(any(), any(), any(), any())).thenAnswer(invocation -> {
                BigDecimal lat1 = invocation.getArgument(0);
                BigDecimal lng1 = invocation.getArgument(1);
                BigDecimal lat2 = invocation.getArgument(2);
                BigDecimal lng2 = invocation.getArgument(3);
                return lat1.doubleValue() > 38.8825 || lng1.doubleValue() > 115.567
                    || lat2.doubleValue() > 38.8825 || lng2.doubleValue() > 115.567
                    ? 160D
                    : 0D;
            });

            when(qaGuideAnswerService.guideScript(1L, "图书馆", "图书馆是游客自动讲解点"))
                .thenReturn(new QaGuideAnswerService.AnswerResult("讲解词", "GUIDE_SCRIPT", "GUIDE_SCRIPT#1"));
            TtsResponse tts = new TtsResponse();
            tts.setAudioBase64("audio");
            when(ttsService.synthesize(1L, "讲解词")).thenReturn(tts);

            List<TouristFenceLog> fenceLogs = new ArrayList<>();
            when(fenceLogMapper.insert(any(TouristFenceLog.class))).thenAnswer(invocation -> {
                TouristFenceLog log = invocation.getArgument(0);
                fenceLogs.add(log);
                return 1;
            });
            when(fenceLogMapper.selectList(any())).thenAnswer(invocation -> filterFenceLogs(fenceLogs, invocation.getArgument(0)));

            service = new TouristGuideService(
                sessionMapper,
                messageMapper,
                locationLogMapper,
                fenceLogMapper,
                topologyService,
                qaGuideAnswerService,
                mock(QaChatStreamService.class),
                digitalHumanService,
                ttsService,
                aiRouteService,
                new ObjectMapper()
            );
        }

        LocationReportRequest location(LocalDateTime reportedAt, double lat, double lng) {
            LocationReportRequest request = new LocationReportRequest();
            request.setLatitude(BigDecimal.valueOf(lat));
            request.setLongitude(BigDecimal.valueOf(lng));
            request.setAccuracyMeter(15);
            request.setReportedAt(reportedAt);
            return request;
        }

        LocationReportResponse report(LocationReportRequest request) {
            return service.reportLocation("user_7", 1L, request);
        }

        private List<TouristFenceLog> filterFenceLogs(List<TouristFenceLog> logs, Object wrapper) {
            String query = wrapper instanceof QueryWrapper<?> queryWrapper ? queryWrapper.getSqlSegment() : String.valueOf(wrapper);
            if (query.contains("broadcast_status")) {
                return logs.stream()
                    .filter(log -> "TRIGGERED".equals(log.getBroadcastStatus()))
                    .sorted((left, right) -> right.getReportedAt().compareTo(left.getReportedAt()))
                    .limit(1)
                    .toList();
            }
            if (query.contains("trigger_type")) {
                return logs.stream()
                    .filter(log -> "EXIT".equals(log.getTriggerType()))
                    .sorted((left, right) -> right.getReportedAt().compareTo(left.getReportedAt()))
                    .limit(1)
                    .toList();
            }
            if (query.contains("IN")) {
                LocalDateTime lastExitAt = logs.stream()
                    .filter(log -> "EXIT".equals(log.getTriggerType()))
                    .map(TouristFenceLog::getReportedAt)
                    .max(LocalDateTime::compareTo)
                    .orElse(null);
                return logs.stream()
                    .filter(log -> List.of("ENTERING", "INSIDE", "COOLDOWN").contains(log.getTriggerState()))
                    .filter(log -> lastExitAt == null || log.getReportedAt().isAfter(lastExitAt))
                    .sorted((left, right) -> left.getReportedAt().compareTo(right.getReportedAt()))
                    .limit(1)
                    .toList();
            }
            return logs.stream()
                .sorted((left, right) -> right.getReportedAt().compareTo(left.getReportedAt()))
                .limit(1)
                .toList();
        }
    }
}
