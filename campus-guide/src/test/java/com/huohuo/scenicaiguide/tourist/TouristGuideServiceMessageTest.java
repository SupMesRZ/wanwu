package com.huohuo.scenicaiguide.tourist;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huohuo.scenicaiguide.common.enums.ErrorCode;
import com.huohuo.scenicaiguide.common.exception.BusinessException;
import com.huohuo.scenicaiguide.digitalhuman.dto.response.TtsResponse;
import com.huohuo.scenicaiguide.digitalhuman.entity.DhProfile;
import com.huohuo.scenicaiguide.digitalhuman.service.DigitalHumanService;
import com.huohuo.scenicaiguide.digitalhuman.service.TtsService;
import com.huohuo.scenicaiguide.qa.service.QaGuideAnswerService;
import com.huohuo.scenicaiguide.qa.service.QaChatStreamService;
import com.huohuo.scenicaiguide.qa.tool.ScenicGuideToolFactory;
import com.huohuo.scenicaiguide.route.dto.AiRoutePlanRequest;
import com.huohuo.scenicaiguide.route.dto.AiRouteResponse;
import com.huohuo.scenicaiguide.route.service.AiRouteService;
import com.huohuo.scenicaiguide.scenic.service.ScenicTopologyService;
import com.huohuo.scenicaiguide.tourist.dto.request.GuideMessageRequest;
import com.huohuo.scenicaiguide.tourist.dto.response.GuideMessageResponse;
import com.huohuo.scenicaiguide.tourist.entity.TouristGuideSession;
import com.huohuo.scenicaiguide.tourist.mapper.TouristFenceLogMapper;
import com.huohuo.scenicaiguide.tourist.mapper.TouristGuideSessionMapper;
import com.huohuo.scenicaiguide.tourist.mapper.TouristLocationLogMapper;
import com.huohuo.scenicaiguide.tourist.mapper.TouristMessageMapper;
import com.huohuo.scenicaiguide.tourist.service.TouristGuideService;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TouristGuideServiceMessageTest {

    @Test
    void explicitRouteIntentUsesRouteRecommendationTool() {
        TouristGuideSessionMapper sessionMapper = mock(TouristGuideSessionMapper.class);
        TouristMessageMapper messageMapper = mock(TouristMessageMapper.class);
        TouristLocationLogMapper locationLogMapper = mock(TouristLocationLogMapper.class);
        TouristFenceLogMapper fenceLogMapper = mock(TouristFenceLogMapper.class);
        ScenicTopologyService topologyService = mock(ScenicTopologyService.class);
        QaGuideAnswerService qaGuideAnswerService = mock(QaGuideAnswerService.class);
        AiRouteService aiRouteService = mock(AiRouteService.class);
        DigitalHumanService digitalHumanService = mock(DigitalHumanService.class);
        TtsService ttsService = mock(TtsService.class);
        QaChatStreamService chatStreamService = mock(QaChatStreamService.class);

        TouristGuideSession session = new TouristGuideSession();
        session.setId(1L);
        session.setVisitorSubject("user_7");
        session.setScenicAreaId(1L);
        session.setStatus("ACTIVE");
        when(sessionMapper.selectById(1L)).thenReturn(session);

        AiRouteResponse route = new AiRouteResponse();
        route.setId(11L);
        route.setName("AI 亲子路线");
        route.setRecommendationReason("按亲子偏好推荐");
        route.setNodes(java.util.List.of());
        ScenicGuideToolFactory.ScenicGuideTools tools = mock(ScenicGuideToolFactory.ScenicGuideTools.class);
        when(tools.recommendedRoute()).thenReturn(route);
        when(tools.routePlan()).thenReturn(new AiRoutePlanRequest());
        when(chatStreamService.stream(any(ScenicGuideToolFactory.AgentContext.class),
            eq("我带孩子来玩"), eq("导游人设"), any()))
            .thenReturn(new QaChatStreamService.StreamAnswer(Flux.just("按亲子偏好推荐"), "AGENT_TOOLS", tools));
        DhProfile profile = new DhProfile();
        profile.setPersonaPrompt("导游人设");
        when(digitalHumanService.getOrCreateDefaultProfile()).thenReturn(profile);
        TtsResponse tts = new TtsResponse();
        tts.setAudioBase64("audio");
        String groundedAnswer = "已为你生成「AI 亲子路线」，依次经过已确认地点。路线已按当前发布路网计算，可在地图中查看。";
        when(ttsService.synthesize(1L, groundedAnswer)).thenReturn(tts);

        TouristGuideService service = new TouristGuideService(
            sessionMapper,
            messageMapper,
            locationLogMapper,
            fenceLogMapper,
            topologyService,
            qaGuideAnswerService,
            chatStreamService,
            digitalHumanService,
            ttsService,
            mock(AiRouteService.class),
            new ObjectMapper()
        );
        GuideMessageRequest request = new GuideMessageRequest();
        request.setContent("我带孩子来玩");
        request.setIntent("ROUTE_RECOMMEND");

        GuideMessageResponse response = service.message("user_7", "AUTHENTICATED", 1L, request);

        assertThat(response.getMessageType()).isEqualTo("ROUTE");
        assertThat(response.getRoute().getId()).isEqualTo(11L);
        assertThat(response.getAnswer()).isEqualTo(groundedAnswer);
        assertThat(response.getAudioBase64()).isEqualTo("audio");
        assertThat(response.getAudioStatus()).isEqualTo("READY");
    }

    @Test
    void routeRecommendationStillReturnsTextWhenTtsFails() {
        TouristGuideSessionMapper sessionMapper = mock(TouristGuideSessionMapper.class);
        TouristMessageMapper messageMapper = mock(TouristMessageMapper.class);
        TouristLocationLogMapper locationLogMapper = mock(TouristLocationLogMapper.class);
        TouristFenceLogMapper fenceLogMapper = mock(TouristFenceLogMapper.class);
        ScenicTopologyService topologyService = mock(ScenicTopologyService.class);
        QaGuideAnswerService qaGuideAnswerService = mock(QaGuideAnswerService.class);
        AiRouteService aiRouteService = mock(AiRouteService.class);
        DigitalHumanService digitalHumanService = mock(DigitalHumanService.class);
        TtsService ttsService = mock(TtsService.class);
        QaChatStreamService chatStreamService = mock(QaChatStreamService.class);

        TouristGuideSession session = new TouristGuideSession();
        session.setId(1L);
        session.setVisitorSubject("user_7");
        session.setScenicAreaId(1L);
        session.setStatus("ACTIVE");
        when(sessionMapper.selectById(1L)).thenReturn(session);

        AiRouteResponse route = new AiRouteResponse();
        route.setId(12L);
        route.setName("AI 轻松路线");
        route.setRecommendationReason("按轻松偏好推荐");
        route.setNodes(java.util.List.of());
        ScenicGuideToolFactory.ScenicGuideTools tools = mock(ScenicGuideToolFactory.ScenicGuideTools.class);
        when(tools.recommendedRoute()).thenReturn(route);
        when(tools.routePlan()).thenReturn(new AiRoutePlanRequest());
        when(chatStreamService.stream(any(ScenicGuideToolFactory.AgentContext.class),
            eq("开始游览"), eq("导游人设"), any()))
            .thenReturn(new QaChatStreamService.StreamAnswer(Flux.just("按轻松偏好推荐"), "AGENT_TOOLS", tools));
        DhProfile profile = new DhProfile();
        profile.setPersonaPrompt("导游人设");
        when(digitalHumanService.getOrCreateDefaultProfile()).thenReturn(profile);
        String groundedAnswer = "已为你生成「AI 轻松路线」，依次经过已确认地点。路线已按当前发布路网计算，可在地图中查看。";
        when(ttsService.synthesize(1L, groundedAnswer))
            .thenThrow(new BusinessException(ErrorCode.THIRD_PARTY_ERROR, "阿里云 TTS 合成失败"));

        TouristGuideService service = new TouristGuideService(
            sessionMapper,
            messageMapper,
            locationLogMapper,
            fenceLogMapper,
            topologyService,
            qaGuideAnswerService,
            chatStreamService,
            digitalHumanService,
            ttsService,
            mock(AiRouteService.class),
            new ObjectMapper()
        );
        GuideMessageRequest request = new GuideMessageRequest();
        request.setContent("开始游览");
        request.setIntent("ROUTE_RECOMMEND");

        GuideMessageResponse response = service.message("user_7", "AUTHENTICATED", 1L, request);

        assertThat(response.getMessageType()).isEqualTo("ROUTE");
        assertThat(response.getAnswer()).isEqualTo(groundedAnswer);
        assertThat(response.getRoute().getId()).isEqualTo(12L);
        assertThat(response.getAudioStatus()).isEqualTo("FAILED");
        assertThat(response.getAudioError()).contains("阿里云 TTS 合成失败");
        assertThat(response.getAudioBase64()).isNull();
    }

    @Test
    void rejectsMessageWhenSessionBelongsToAnotherVisitor() {
        TouristGuideService service = serviceWithSession("user_8");
        GuideMessageRequest request = new GuideMessageRequest();
        request.setContent("介绍一下景区");

        assertThatThrownBy(() -> service.message("user_7", "AUTHENTICATED", 1L, request))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("无权访问");
    }

    @Test
    void anonymousRouteRequestIsHandledByAgentPermissionContext() {
        TouristGuideService service = serviceWithSession("anon_device");
        GuideMessageRequest request = new GuideMessageRequest();
        request.setContent("推荐一条路线");
        request.setIntent("ROUTE_RECOMMEND");

        GuideMessageResponse response = service.message("anon_device", "ANONYMOUS", 1L, request);

        assertThat(response.getAnswer()).contains("登录后");
    }

    @Test
    void preparesGroundedStreamingAnswerForOwnedSession() {
        TouristGuideSessionMapper sessionMapper = mock(TouristGuideSessionMapper.class);
        TouristGuideSession session = new TouristGuideSession();
        session.setId(1L);
        session.setVisitorSubject("anon_device");
        session.setScenicAreaId(1L);
        session.setStatus("ACTIVE");
        when(sessionMapper.selectById(1L)).thenReturn(session);

        QaChatStreamService chatStreamService = mock(QaChatStreamService.class);
        when(chatStreamService.stream(any(ScenicGuideToolFactory.AgentContext.class),
            eq("介绍桃花源"), eq("导游人设"), any()))
            .thenReturn(new QaChatStreamService.StreamAnswer(Flux.just("**", "桃花源", "**"), "CHUNK:[1]"));
        DigitalHumanService digitalHumanService = mock(DigitalHumanService.class);
        DhProfile profile = new DhProfile();
        profile.setPersonaPrompt("导游人设");
        when(digitalHumanService.getOrCreateDefaultProfile()).thenReturn(profile);

        TouristGuideService service = new TouristGuideService(
            sessionMapper,
            mock(TouristMessageMapper.class),
            mock(TouristLocationLogMapper.class),
            mock(TouristFenceLogMapper.class),
            mock(ScenicTopologyService.class),
            mock(QaGuideAnswerService.class),
            chatStreamService,
            digitalHumanService,
            mock(TtsService.class),
            mock(AiRouteService.class),
            new ObjectMapper()
        );
        GuideMessageRequest request = new GuideMessageRequest();
        request.setContent("介绍桃花源");

        TouristGuideService.GuideStreamPlan plan = service.prepareStream(
            "anon_device", "ANONYMOUS", 1L, request);

        assertThat(plan.content().collectList().block()).containsExactly("**", "桃花源", "**");
        assertThat(plan.evidence()).isEqualTo("CHUNK:[1]");
        assertThat(plan.isCompleted()).isFalse();
    }

    private TouristGuideService serviceWithSession(String owner) {
        TouristGuideSessionMapper sessionMapper = mock(TouristGuideSessionMapper.class);
        TouristGuideSession session = new TouristGuideSession();
        session.setId(1L);
        session.setVisitorSubject(owner);
        session.setScenicAreaId(1L);
        session.setStatus("ACTIVE");
        when(sessionMapper.selectById(1L)).thenReturn(session);
        DigitalHumanService digitalHumanService = mock(DigitalHumanService.class);
        DhProfile profile = new DhProfile();
        profile.setPersonaPrompt("导游人设");
        when(digitalHumanService.getOrCreateDefaultProfile()).thenReturn(profile);
        QaChatStreamService chatStreamService = mock(QaChatStreamService.class);
        when(chatStreamService.stream(any(ScenicGuideToolFactory.AgentContext.class),
            any(String.class), any(String.class), any()))
            .thenReturn(new QaChatStreamService.StreamAnswer(Flux.just("登录后即可生成专属路线"), "AGENT_TOOLS"));
        return new TouristGuideService(
            sessionMapper,
            mock(TouristMessageMapper.class),
            mock(TouristLocationLogMapper.class),
            mock(TouristFenceLogMapper.class),
            mock(ScenicTopologyService.class),
            mock(QaGuideAnswerService.class),
            chatStreamService,
            digitalHumanService,
            mock(TtsService.class),
            mock(AiRouteService.class),
            new ObjectMapper()
        );
    }
}
