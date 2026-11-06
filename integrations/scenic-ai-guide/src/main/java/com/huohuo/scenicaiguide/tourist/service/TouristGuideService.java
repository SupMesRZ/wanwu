package com.huohuo.scenicaiguide.tourist.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huohuo.scenicaiguide.common.enums.ErrorCode;
import com.huohuo.scenicaiguide.common.exception.BusinessException;
import com.huohuo.scenicaiguide.digitalhuman.dto.response.TtsResponse;
import com.huohuo.scenicaiguide.digitalhuman.service.DigitalHumanService;
import com.huohuo.scenicaiguide.digitalhuman.service.TtsService;
import com.huohuo.scenicaiguide.qa.service.QaGuideAnswerService;
import com.huohuo.scenicaiguide.qa.service.QaChatStreamService;
import com.huohuo.scenicaiguide.qa.tool.ScenicGuideToolFactory;
import com.huohuo.scenicaiguide.route.dto.AiRoutePlanRequest;
import com.huohuo.scenicaiguide.route.dto.AiRouteResponse;
import com.huohuo.scenicaiguide.route.service.AiRouteService;
import com.huohuo.scenicaiguide.scenic.dto.request.MapWorkbenchDraftRequest;
import com.huohuo.scenicaiguide.scenic.service.ScenicTopologyService;
import com.huohuo.scenicaiguide.tourist.dto.request.GuideMessageRequest;
import com.huohuo.scenicaiguide.tourist.dto.request.GuideSessionStartRequest;
import com.huohuo.scenicaiguide.tourist.dto.request.LocationReportRequest;
import com.huohuo.scenicaiguide.tourist.dto.response.GuideHistoryMessageResponse;
import com.huohuo.scenicaiguide.tourist.dto.response.GuideMessageResponse;
import com.huohuo.scenicaiguide.tourist.dto.response.GuideSessionResponse;
import com.huohuo.scenicaiguide.tourist.dto.response.LocationReportResponse;
import com.huohuo.scenicaiguide.tourist.dto.response.RouteProgressResponse;
import com.huohuo.scenicaiguide.tourist.entity.TouristFenceLog;
import com.huohuo.scenicaiguide.tourist.entity.TouristGuideSession;
import com.huohuo.scenicaiguide.tourist.entity.TouristLocationLog;
import com.huohuo.scenicaiguide.tourist.entity.TouristMessage;
import com.huohuo.scenicaiguide.tourist.mapper.TouristFenceLogMapper;
import com.huohuo.scenicaiguide.tourist.mapper.TouristGuideSessionMapper;
import com.huohuo.scenicaiguide.tourist.mapper.TouristLocationLogMapper;
import com.huohuo.scenicaiguide.tourist.mapper.TouristMessageMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import reactor.core.publisher.Flux;

@Service
public class TouristGuideService {

    private static final int MAX_ACCEPTED_ACCURACY_METER = 120;
    private static final double MAX_REASONABLE_SPEED_METER_SECOND = 25D;
    private static final int DWELL_SECONDS = 12;
    private static final int COOLDOWN_MINUTES = 30;
    private static final int MAX_HISTORY_MESSAGES = 200;
    private static final int ARRIVAL_RADIUS_METER = 50;

    private final TouristGuideSessionMapper sessionMapper;
    private final TouristMessageMapper messageMapper;
    private final TouristLocationLogMapper locationLogMapper;
    private final TouristFenceLogMapper fenceLogMapper;
    private final ScenicTopologyService topologyService;
    private final QaGuideAnswerService qaGuideAnswerService;
    private final QaChatStreamService qaChatStreamService;
    private final DigitalHumanService digitalHumanService;
    private final TtsService ttsService;
    private final AiRouteService aiRouteService;
    private final ObjectMapper objectMapper;

    public TouristGuideService(TouristGuideSessionMapper sessionMapper,
                               TouristMessageMapper messageMapper,
                               TouristLocationLogMapper locationLogMapper,
                               TouristFenceLogMapper fenceLogMapper,
                               ScenicTopologyService topologyService,
                               QaGuideAnswerService qaGuideAnswerService,
                               QaChatStreamService qaChatStreamService,
                               DigitalHumanService digitalHumanService,
                               TtsService ttsService,
                               AiRouteService aiRouteService,
                               ObjectMapper objectMapper) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.locationLogMapper = locationLogMapper;
        this.fenceLogMapper = fenceLogMapper;
        this.topologyService = topologyService;
        this.qaGuideAnswerService = qaGuideAnswerService;
        this.qaChatStreamService = qaChatStreamService;
        this.digitalHumanService = digitalHumanService;
        this.ttsService = ttsService;
        this.aiRouteService = aiRouteService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public GuideSessionResponse start(String visitorSubject, String authLevel, GuideSessionStartRequest request) {
        ScenicTopologyService.PublishedTopology topology = topologyService.getDefaultPublishedTopology();
        TouristGuideSession session = new TouristGuideSession();
        session.setSessionNo("GS" + UUID.randomUUID().toString().replace("-", "").substring(0, 18));
        session.setVisitorSubject(visitorSubject);
        session.setScenicAreaId(topology.scenicAreaId());
        session.setStatus("ACTIVE");
        session.setPreferenceJson("{}");
        session.setStartedAt(LocalDateTime.now());
        session.setCreatedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());
        session.setDeleted(0);
        sessionMapper.insert(session);

        var profile = digitalHumanService.getOrCreateDefaultProfile();
        saveMessage(session.getId(), "AGENT", "TEXT", profile.getWelcomeText(), "PROFILE#" + profile.getId(), null, "idle");

        GuideSessionResponse response = new GuideSessionResponse();
        response.setSessionId(session.getId());
        response.setSessionNo(session.getSessionNo());
        response.setScenicAreaId(session.getScenicAreaId());
        response.setStatus(session.getStatus());
        response.setWelcomeText(profile.getWelcomeText());
        response.setDigitalHuman(digitalHumanService.getDefaultProfileResponse());
        response.setAuthLevel(authLevel);
        response.setCanRecommendRoute(!"ANONYMOUS".equals(authLevel));
        response.setCanUseLiveGuide(!"ANONYMOUS".equals(authLevel));
        return response;
    }

    /**
     * 恢复已存在的会话，供游客端刷新后复用同一会话
     *
     * <p>与 {@link #start} 的区别是不新建会话、不写欢迎语，只重建响应体
     */
    public GuideSessionResponse resume(String visitorSubject, String authLevel, Long sessionId) {
        TouristGuideSession session = getOwnedSession(sessionId, visitorSubject);
        var profile = digitalHumanService.getOrCreateDefaultProfile();

        GuideSessionResponse response = new GuideSessionResponse();
        response.setSessionId(session.getId());
        response.setSessionNo(session.getSessionNo());
        response.setScenicAreaId(session.getScenicAreaId());
        response.setStatus(session.getStatus());
        response.setWelcomeText(profile.getWelcomeText());
        response.setDigitalHuman(digitalHumanService.getDefaultProfileResponse());
        response.setAuthLevel(authLevel);
        response.setCanRecommendRoute(!"ANONYMOUS".equals(authLevel));
        response.setCanUseLiveGuide(!"ANONYMOUS".equals(authLevel));
        return response;
    }

    /**
     * 读取会话历史消息，供游客端刷新后恢复对话
     *
     * <p>按时间正序返回最近 {@link #MAX_HISTORY_MESSAGES} 条，音频不回放，只恢复文字
     */
    public List<GuideHistoryMessageResponse> history(String visitorSubject, Long sessionId) {
        TouristGuideSession session = getOwnedSession(sessionId, visitorSubject);
        List<TouristMessage> messages = messageMapper.selectList(
            new QueryWrapper<TouristMessage>()
                .eq("session_id", session.getId())
                .orderByDesc("created_at")
                .orderByDesc("id")
                .last("LIMIT " + MAX_HISTORY_MESSAGES));
        List<GuideHistoryMessageResponse> result = messages.stream().map(message -> {
            GuideHistoryMessageResponse item = new GuideHistoryMessageResponse();
            item.setId(message.getId());
            item.setSender(message.getSender());
            item.setMessageType(message.getMessageType());
            item.setContent(message.getContent());
            item.setEvidence(message.getEvidenceText());
            item.setActionState(message.getActionState());
            item.setCreatedAt(message.getCreatedAt());
            return item;
        }).collect(Collectors.toCollection(ArrayList::new));
        Collections.reverse(result);
        return result;
    }

    @Transactional
    public GuideMessageResponse message(String visitorSubject,
                                        String authLevel,
                                        Long sessionId,
                                        GuideMessageRequest request) {
        GuideStreamPlan plan = prepareStream(visitorSubject, authLevel, sessionId, request);
        if (plan.isCompleted()) {
            return plan.completedMessage();
        }
        String answer = String.join("", plan.content().collectList().blockOptional().orElse(List.of()));
        return completeStream(
            plan.sessionId(), plan.scenicAreaId(), answer, plan.evidence(), plan.pathVisualization(),
            plan.recommendedRoute(), plan.routePlan(), false);
    }

    @Transactional
    public GuideStreamPlan prepareStream(String visitorSubject,
                                         String authLevel,
                                         Long sessionId,
                                         GuideMessageRequest request) {
        return prepareStream(visitorSubject, authLevel, sessionId, request, ignored -> { });
    }

    @Transactional
    public GuideStreamPlan prepareStream(String visitorSubject,
                                         String authLevel,
                                         Long sessionId,
                                         GuideMessageRequest request,
                                         Consumer<String> progressListener) {
        TouristGuideSession session = getOwnedSession(sessionId, visitorSubject);
        saveMessage(sessionId, "TOURIST", "TEXT", request.getContent(), null, null, "listening");
        String currentSpotName = resolveCurrentSpotName(session);
        String personaPrompt = digitalHumanService.getOrCreateDefaultProfile().getPersonaPrompt();
        ScenicGuideToolFactory.AgentContext agentContext = new ScenicGuideToolFactory.AgentContext(
            session.getId(), session.getScenicAreaId(), currentSpotName, session.getCurrentSpotId(),
            session.getCurrentRouteId(), !"ANONYMOUS".equals(authLevel));
        QaChatStreamService.StreamAnswer answer = qaChatStreamService.stream(
            agentContext, request.getContent().trim(), personaPrompt, progressListener);
        return GuideStreamPlan.streaming(sessionId, session.getScenicAreaId(), answer);
    }

    @Transactional
    public GuideMessageResponse completeStream(Long sessionId,
                                               Long scenicAreaId,
                                               String answer,
                                               String evidence,
                                               ScenicGuideToolFactory.PathVisualization pathVisualization,
                                               AiRouteResponse recommendedRoute,
                                               AiRoutePlanRequest routePlan,
                                               boolean speechStreamed) {
        String safeAnswer = answer == null || answer.isBlank()
            ? "我暂时没有生成有效回答，请换个问法再试。"
            : answer.trim();
        if (recommendedRoute != null) {
            safeAnswer = groundedRouteAnswer(recommendedRoute);
        }
        TtsResult tts = speechStreamed ? null : safeSynthesize(scenicAreaId, safeAnswer);
        AiRouteResponse route = recommendedRoute == null ? buildPathRoute(pathVisualization) : recommendedRoute;
        String effectiveEvidence = recommendedRoute == null
            ? evidence
            : "AI_ROUTE#" + recommendedRoute.getId() + "|" + evidence;
        if (recommendedRoute != null) {
            TouristGuideSession session = getSession(sessionId);
            session.setCurrentRouteId(recommendedRoute.getId());
            // 新路线从起点站开始跟随，游标指向起点等游客出发
            session.setRouteNodeSeq(1);
            session.setPreferenceJson(writeJson(routePlan));
            session.setUpdatedAt(LocalDateTime.now());
            sessionMapper.updateById(session);
        }
        String messageType = route == null ? "TEXT" : "ROUTE";
        saveMessage(sessionId, "AGENT", messageType, safeAnswer, effectiveEvidence,
            tts == null ? null : tts.getAudioUrl(), "speaking");
        return buildMessage(safeAnswer, messageType, "speaking", tts, effectiveEvidence, route);
    }

    @Transactional
    public LocationReportResponse reportLocation(String visitorSubject,
                                                 Long sessionId,
                                                 LocationReportRequest request) {
        TouristGuideSession session = getOwnedSession(sessionId, visitorSubject);
        LocalDateTime reportedAt = request.getReportedAt() == null ? LocalDateTime.now() : request.getReportedAt();
        LocationReportResponse response = new LocationReportResponse();

        String rejectReason = rejectReason(session, request, reportedAt);
        saveLocation(sessionId, request, reportedAt, rejectReason);
        if (rejectReason != null) {
            response.setAccepted(false);
            response.setState("REJECTED");
            return response;
        }

        session.setLastLocationLat(request.getLatitude());
        session.setLastLocationLng(request.getLongitude());
        session.setLastLocationAt(reportedAt);
        session.setUpdatedAt(LocalDateTime.now());
        sessionMapper.updateById(session);

        response.setAccepted(true);
        ScenicTopologyService.PublishedTopology topology = topologyService.getPublishedTopology(session.getScenicAreaId());
        MapWorkbenchDraftRequest.SpotDraft nearest = topologyService.nearestSpot(topology, request.getLatitude(), request.getLongitude());
        if (nearest == null) {
            response.setState("OUTSIDE");
            return response;
        }

        FenceDecision decision = evaluateFence(sessionId, nearest, request, reportedAt);
        response.setState(decision.state());
        response.setTriggeredSpotId(decision.triggered() ? nearest.getId() : null);
        response.setTriggeredSpotName(decision.triggered() ? nearest.getName() : null);
        saveFenceLog(sessionId, nearest, request, reportedAt, decision);
        if (decision.triggered()) {
            session.setCurrentSpotId(nearest.getId());
            session.setUpdatedAt(LocalDateTime.now());
            sessionMapper.updateById(session);
            QaGuideAnswerService.AnswerResult answer = qaGuideAnswerService.guideScript(
                session.getScenicAreaId(), nearest.getName(), nearest.getDescription());
            TtsResult tts = safeSynthesize(session.getScenicAreaId(), answer.answer());
            saveMessage(sessionId, "AGENT", "GUIDE", answer.answer(), answer.evidence(), tts.getAudioUrl(), "speaking");
            response.setGuideMessage(buildMessage(answer.answer(), "GUIDE", "speaking", tts, answer.evidence(), null));
        }
        advanceRouteProgress(session, nearest, request, reportedAt, decision, response);
        return response;
    }

    /**
     * 路线跟随推进。游客位置进入游标之后任一节点的抵达半径时，把游标推到最远的那个节点：
     * 途经点（ENDPOINT）静默越过，景点节点触发到站讲解。讲解与围栏共用冷却——
     * 到站播报会补一条 TRIGGERED 围栏日志，防止随后的停留判定把同一景点再讲一遍
     */
    private void advanceRouteProgress(TouristGuideSession session,
                                      MapWorkbenchDraftRequest.SpotDraft nearest,
                                      LocationReportRequest request,
                                      LocalDateTime reportedAt,
                                      FenceDecision decision,
                                      LocationReportResponse response) {
        if (session.getCurrentRouteId() == null) {
            return;
        }
        AiRouteResponse route = safeLoadRoute(session.getCurrentRouteId());
        if (route == null || route.getNodes() == null || route.getNodes().isEmpty()) {
            return;
        }
        int cursor = session.getRouteNodeSeq() == null ? 0 : session.getRouteNodeSeq();
        AiRouteResponse.RouteNode reached = null;
        for (AiRouteResponse.RouteNode node : route.getNodes()) {
            if (node.getSequence() == null || node.getSequence() <= cursor
                || node.getLatitude() == null || node.getLongitude() == null) {
                continue;
            }
            double distance = topologyService.distanceMeters(
                request.getLatitude(), request.getLongitude(), node.getLatitude(), node.getLongitude());
            if (distance <= ARRIVAL_RADIUS_METER
                && (reached == null || node.getSequence() > reached.getSequence())) {
                reached = node;
            }
        }
        if (reached != null) {
            cursor = reached.getSequence();
            session.setRouteNodeSeq(cursor);
            session.setUpdatedAt(LocalDateTime.now());
            sessionMapper.updateById(session);
            boolean fenceAlreadyNarrated = decision.triggered() && nearest != null
                && nearest.getId().equals(reached.getId());
            if ("SPOT".equals(reached.getType()) && !fenceAlreadyNarrated) {
                narrateRouteArrival(session, route, reached, request, reportedAt, response);
            }
        }
        response.setRouteProgress(buildRouteProgress(route, cursor, reached));
    }

    private void narrateRouteArrival(TouristGuideSession session,
                                     AiRouteResponse route,
                                     AiRouteResponse.RouteNode node,
                                     LocationReportRequest request,
                                     LocalDateTime reportedAt,
                                     LocationReportResponse response) {
        TouristFenceLog lastTriggered = lastTriggered(session.getId(), node.getId());
        if (lastTriggered != null
            && Duration.between(lastTriggered.getReportedAt(), reportedAt).toMinutes() < COOLDOWN_MINUTES) {
            return;
        }
        QaGuideAnswerService.AnswerResult answer = qaGuideAnswerService.guideScript(
            session.getScenicAreaId(), node.getName(), node.getDescription());
        String arrival = "已抵达「" + node.getName() + "」，这是「" + route.getName() + "」第 "
            + node.getSequence() + " 站。";
        String content = arrival + answer.answer();
        TtsResult tts = safeSynthesize(session.getScenicAreaId(), content);
        saveMessage(session.getId(), "AGENT", "GUIDE", content, answer.evidence(), tts.getAudioUrl(), "speaking");
        MapWorkbenchDraftRequest.SpotDraft spotRef = new MapWorkbenchDraftRequest.SpotDraft();
        spotRef.setId(node.getId());
        spotRef.setName(node.getName());
        saveFenceLog(session.getId(), spotRef, request, reportedAt,
            new FenceDecision("INSIDE", "ROUTE_ARRIVAL", true, "路线到站自动讲解"));
        response.setGuideMessage(buildMessage(content, "GUIDE", "speaking", tts, answer.evidence(), null));
    }

    private RouteProgressResponse buildRouteProgress(AiRouteResponse route, int cursor,
                                                     AiRouteResponse.RouteNode arrived) {
        RouteProgressResponse progress = new RouteProgressResponse();
        progress.setRouteId(route.getId());
        progress.setRouteName(route.getName());
        progress.setTotalNodes(route.getNodes().size());
        progress.setCurrentSequence(cursor);
        progress.setArrivedNodeName(arrived == null ? null : arrived.getName());
        AiRouteResponse.RouteNode next = route.getNodes().stream()
            .filter(node -> node.getSequence() != null && node.getSequence() > cursor)
            .findFirst()
            .orElse(null);
        progress.setNextNodeName(next == null ? null : next.getName());
        progress.setFinished(next == null && cursor > 0);
        return progress;
    }

    private AiRouteResponse safeLoadRoute(Long routeId) {
        try {
            return aiRouteService.loadRoute(routeId);
        } catch (Exception ignored) {
            return null;
        }
    }

    private FenceDecision evaluateFence(Long sessionId,
                                        MapWorkbenchDraftRequest.SpotDraft spot,
                                        LocationReportRequest request,
                                        LocalDateTime reportedAt) {
        List<BigDecimal> point = topologyService.resolvePoint(spot);
        if (point == null) {
            return new FenceDecision("OUTSIDE", "SKIP", false, "景点没有有效坐标");
        }
        int radius = spot.getRadius() == null ? 50 : Math.max(spot.getRadius(), 20);
        double distance = topologyService.distanceMeters(request.getLatitude(), request.getLongitude(), point.get(0), point.get(1));
        if (distance > radius) {
            if (isCurrentlyInside(sessionId, spot.getId())) {
                return new FenceDecision("OUTSIDE", "EXIT", false, "距离围栏中心 " + Math.round(distance) + " 米，已离开围栏");
            }
            return new FenceDecision("OUTSIDE", "SKIP", false, "距离围栏中心 " + Math.round(distance) + " 米");
        }
        TouristFenceLog lastTriggered = lastTriggered(sessionId, spot.getId());
        if (lastTriggered != null && Duration.between(lastTriggered.getReportedAt(), reportedAt).toMinutes() < COOLDOWN_MINUTES) {
            return new FenceDecision("COOLDOWN", "SKIP", false, "景点讲解冷却中");
        }
        TouristFenceLog entering = lastEntering(sessionId, spot.getId());
        if (entering == null || Duration.between(entering.getReportedAt(), reportedAt).getSeconds() < DWELL_SECONDS) {
            return new FenceDecision("ENTERING", "ENTER", false, "进入围栏，等待稳定停留");
        }
        return new FenceDecision("INSIDE", "DWELL", true, "稳定停留触发讲解");
    }

    private boolean isCurrentlyInside(Long sessionId, String spotId) {
        TouristFenceLog last = fenceLogMapper.selectList(new QueryWrapper<TouristFenceLog>()
                .eq("session_id", sessionId)
                .eq("spot_id", spotId)
                .orderByDesc("reported_at")
                .last("LIMIT 1"))
            .stream()
            .findFirst()
            .orElse(null);
        return last != null && !"EXIT".equals(last.getTriggerType());
    }

    private TouristFenceLog lastTriggered(Long sessionId, String spotId) {
        return fenceLogMapper.selectList(new QueryWrapper<TouristFenceLog>()
                .eq("session_id", sessionId)
                .eq("spot_id", spotId)
                .eq("broadcast_status", "TRIGGERED")
                .orderByDesc("reported_at")
                .last("LIMIT 1"))
            .stream()
            .findFirst()
            .orElse(null);
    }

    private TouristFenceLog lastEntering(Long sessionId, String spotId) {
        TouristFenceLog lastExit = fenceLogMapper.selectList(new QueryWrapper<TouristFenceLog>()
                .eq("session_id", sessionId)
                .eq("spot_id", spotId)
                .eq("trigger_type", "EXIT")
                .orderByDesc("reported_at")
                .last("LIMIT 1"))
            .stream()
            .findFirst()
            .orElse(null);
        QueryWrapper<TouristFenceLog> wrapper = new QueryWrapper<TouristFenceLog>()
                .eq("session_id", sessionId)
                .eq("spot_id", spotId)
                .in("trigger_state", List.of("ENTERING", "INSIDE", "COOLDOWN"));
        if (lastExit != null) {
            wrapper.gt("reported_at", lastExit.getReportedAt());
        }
        return fenceLogMapper.selectList(wrapper.orderByAsc("reported_at").last("LIMIT 1"))
            .stream()
            .findFirst()
            .orElse(null);
    }

    private String rejectReason(TouristGuideSession session, LocationReportRequest request, LocalDateTime reportedAt) {
        if (request.getAccuracyMeter() != null && request.getAccuracyMeter() > MAX_ACCEPTED_ACCURACY_METER) {
            return "定位精度不足";
        }
        if (reportedAt.isBefore(LocalDateTime.now().minusMinutes(5))) {
            return "定位时间过旧";
        }
        if (reportedAt.isAfter(LocalDateTime.now().plusMinutes(2))) {
            return "定位时间异常";
        }
        if (isLocationJump(session, request, reportedAt)) {
            return "定位跳点异常";
        }
        return null;
    }

    private boolean isLocationJump(TouristGuideSession session, LocationReportRequest request, LocalDateTime reportedAt) {
        if (session.getLastLocationLat() == null || session.getLastLocationLng() == null || session.getLastLocationAt() == null) {
            return false;
        }
        long seconds = Duration.between(session.getLastLocationAt(), reportedAt).getSeconds();
        if (seconds <= 0 || seconds > 300) {
            return false;
        }
        double distance = topologyService.distanceMeters(
            session.getLastLocationLat(), session.getLastLocationLng(), request.getLatitude(), request.getLongitude());
        return distance / seconds > MAX_REASONABLE_SPEED_METER_SECOND;
    }

    private void saveLocation(Long sessionId, LocationReportRequest request, LocalDateTime reportedAt, String rejectReason) {
        TouristLocationLog log = new TouristLocationLog();
        log.setSessionId(sessionId);
        log.setLongitude(request.getLongitude());
        log.setLatitude(request.getLatitude());
        log.setAccuracyMeter(request.getAccuracyMeter());
        log.setSpeedMeterSecond(request.getSpeedMeterSecond());
        log.setReportedAt(reportedAt);
        log.setAccepted(rejectReason == null ? 1 : 0);
        log.setRejectReason(rejectReason);
        log.setCreatedAt(LocalDateTime.now());
        log.setDeleted(0);
        locationLogMapper.insert(log);
    }

    private void saveFenceLog(Long sessionId,
                              MapWorkbenchDraftRequest.SpotDraft spot,
                              LocationReportRequest request,
                              LocalDateTime reportedAt,
                              FenceDecision decision) {
        TouristFenceLog log = new TouristFenceLog();
        log.setSessionId(sessionId);
        log.setSpotId(spot.getId());
        log.setSpotName(spot.getName());
        log.setTriggerState(decision.state());
        log.setTriggerType(decision.triggerType());
        log.setLongitude(request.getLongitude());
        log.setLatitude(request.getLatitude());
        log.setAccuracyMeter(request.getAccuracyMeter());
        log.setDetail(decision.detail());
        log.setBroadcastStatus(decision.triggered() ? "TRIGGERED" : "SKIPPED");
        log.setReportedAt(reportedAt);
        log.setCreatedAt(LocalDateTime.now());
        log.setDeleted(0);
        fenceLogMapper.insert(log);
    }

    private void saveMessage(Long sessionId, String sender, String type, String content,
                             String evidence, String audioUrl, String actionState) {
        TouristMessage message = new TouristMessage();
        message.setSessionId(sessionId);
        message.setSender(sender);
        message.setMessageType(type);
        message.setContent(content);
        message.setEvidenceText(evidence);
        message.setAudioUrl(audioUrl);
        message.setActionState(actionState);
        message.setCreatedAt(LocalDateTime.now());
        message.setDeleted(0);
        messageMapper.insert(message);
    }

    private GuideMessageResponse buildMessage(String answer, String type, String state,
                                              TtsResult tts, String evidence, AiRouteResponse route) {
        GuideMessageResponse response = new GuideMessageResponse();
        response.setAnswer(answer);
        response.setMessageType(type);
        response.setActionState(state);
        response.setAudioUrl(tts == null ? null : tts.getAudioUrl());
        response.setAudioBase64(tts == null ? null : tts.getAudioBase64());
        response.setAudioStatus(tts == null ? "SKIPPED" : tts.status());
        response.setAudioError(tts == null ? null : tts.error());
        response.setEvidence(evidence);
        response.setRoute(route);
        return response;
    }

    private String groundedRouteAnswer(AiRouteResponse route) {
        String spots = route.getNodes().stream()
            .map(AiRouteResponse.RouteNode::getName)
            .filter(name -> name != null && !name.isBlank())
            .reduce((left, right) -> left + "、" + right)
            .orElse("已确认景点");
        String distance = route.getTotalDistanceMeter() == null ? "" : "，全程约" + route.getTotalDistanceMeter() + "米";
        String duration = route.getEstimatedDurationMin() == null ? "" : "，预计" + route.getEstimatedDurationMin() + "分钟";
        return "已为你生成「" + route.getName() + "」" + distance + duration + "，依次经过" + spots
            + "。路线已按当前发布路网计算，可在地图中查看。";
    }

    private AiRouteResponse buildPathRoute(ScenicGuideToolFactory.PathVisualization path) {
        if (path == null) {
            return null;
        }
        AiRouteResponse response = new AiRouteResponse();
        response.setName(path.fromName() + " → " + path.toName());
        response.setRouteType("POINT_TO_POINT");
        response.setTotalDistanceMeter(path.totalLengthMeter());
        // 耗时由算路沿选中路段累加真实标注得到，这里不再按里程二次估算
        response.setEstimatedDurationMin(Math.max(1, path.totalDurationMin()));
        response.setRecommendationReason("已按当前发布路网计算 " + path.totalLengthMeter() + " 米最短路径");
        response.setRouteSegmentIds(path.routeSegmentIds());
        response.setPathPoints(path.pathPoints().stream().map(point -> {
            AiRouteResponse.RoutePathPoint routePoint = new AiRouteResponse.RoutePathPoint();
            routePoint.setSegmentId(point.segmentId());
            routePoint.setLatitude(point.latitude());
            routePoint.setLongitude(point.longitude());
            routePoint.setSequence(point.sequence());
            return routePoint;
        }).toList());
        response.setNodes(path.nodes().stream().map(node -> {
            AiRouteResponse.RouteNode routeNode = new AiRouteResponse.RouteNode();
            routeNode.setId(node.id());
            routeNode.setName(node.name());
            routeNode.setType("PATH_NODE");
            routeNode.setLatitude(node.latitude());
            routeNode.setLongitude(node.longitude());
            routeNode.setSequence(node.sequence());
            return routeNode;
        }).toList());
        return response;
    }

    private TtsResult safeSynthesize(Long scenicAreaId, String text) {
        try {
            return TtsResult.success(ttsService.synthesize(scenicAreaId, text));
        } catch (BusinessException e) {
            return TtsResult.failed(e.getMessage());
        }
    }

    private TouristGuideSession getSession(Long sessionId) {
        TouristGuideSession session = sessionMapper.selectById(sessionId);
        if (session == null || !"ACTIVE".equals(session.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "导览会话不存在或已结束");
        }
        return session;
    }

    private TouristGuideSession getOwnedSession(Long sessionId, String visitorSubject) {
        TouristGuideSession session = getSession(sessionId);
        if (visitorSubject == null || !visitorSubject.equals(session.getVisitorSubject())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问该导览会话");
        }
        return session;
    }

    private String resolveCurrentSpotName(TouristGuideSession session) {
        if (session.getCurrentSpotId() == null) {
            return null;
        }
        ScenicTopologyService.PublishedTopology topology = topologyService.getPublishedTopology(session.getScenicAreaId());
        return topologyService.findSpot(topology, session.getCurrentSpotId()).getName();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? new Object() : value);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "数据格式错误");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record FenceDecision(String state, String triggerType, boolean triggered, String detail) {
    }

    public record GuideStreamPlan(Long sessionId,
                                  Long scenicAreaId,
                                  Flux<String> content,
                                  String evidence,
                                  QaChatStreamService.StreamAnswer agentAnswer,
                                  GuideMessageResponse completedMessage) {

        public static GuideStreamPlan streaming(Long sessionId,
                                                 Long scenicAreaId,
                                                 QaChatStreamService.StreamAnswer answer) {
            return new GuideStreamPlan(
                sessionId, scenicAreaId, answer.content(), answer.evidence(), answer, null);
        }

        public static GuideStreamPlan completed(Long sessionId,
                                                 Long scenicAreaId,
                                                 GuideMessageResponse message) {
            return new GuideStreamPlan(sessionId, scenicAreaId, Flux.empty(), null, null, message);
        }

        public ScenicGuideToolFactory.PathVisualization pathVisualization() {
            return agentAnswer == null ? null : agentAnswer.pathVisualization();
        }

        public AiRouteResponse recommendedRoute() {
            return agentAnswer == null ? null : agentAnswer.recommendedRoute();
        }

        public AiRoutePlanRequest routePlan() {
            return agentAnswer == null ? null : agentAnswer.routePlan();
        }

        public boolean isCompleted() {
            return completedMessage != null;
        }
    }

    private record TtsResult(TtsResponse response, String status, String error) {
        static TtsResult success(TtsResponse response) {
            return new TtsResult(response, "READY", null);
        }

        static TtsResult failed(String error) {
            return new TtsResult(null, "FAILED", error);
        }

        String getAudioUrl() {
            return response == null ? null : response.getAudioUrl();
        }

        String getAudioBase64() {
            return response == null ? null : response.getAudioBase64();
        }
    }
}
