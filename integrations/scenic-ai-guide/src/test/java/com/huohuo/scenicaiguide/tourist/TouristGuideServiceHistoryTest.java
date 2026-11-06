package com.huohuo.scenicaiguide.tourist;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huohuo.scenicaiguide.common.exception.BusinessException;
import com.huohuo.scenicaiguide.digitalhuman.dto.response.DhProfileResponse;
import com.huohuo.scenicaiguide.digitalhuman.entity.DhProfile;
import com.huohuo.scenicaiguide.digitalhuman.service.DigitalHumanService;
import com.huohuo.scenicaiguide.digitalhuman.service.TtsService;
import com.huohuo.scenicaiguide.qa.service.QaChatStreamService;
import com.huohuo.scenicaiguide.route.service.AiRouteService;
import com.huohuo.scenicaiguide.qa.service.QaGuideAnswerService;
import com.huohuo.scenicaiguide.scenic.service.ScenicTopologyService;
import com.huohuo.scenicaiguide.tourist.dto.response.GuideHistoryMessageResponse;
import com.huohuo.scenicaiguide.tourist.dto.response.GuideSessionResponse;
import com.huohuo.scenicaiguide.tourist.entity.TouristGuideSession;
import com.huohuo.scenicaiguide.tourist.entity.TouristMessage;
import com.huohuo.scenicaiguide.tourist.mapper.TouristFenceLogMapper;
import com.huohuo.scenicaiguide.tourist.mapper.TouristGuideSessionMapper;
import com.huohuo.scenicaiguide.tourist.mapper.TouristLocationLogMapper;
import com.huohuo.scenicaiguide.tourist.mapper.TouristMessageMapper;
import com.huohuo.scenicaiguide.tourist.service.TouristGuideService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 会话恢复与历史消息回放测试
 */
class TouristGuideServiceHistoryTest {

    private final TouristGuideSessionMapper sessionMapper = mock(TouristGuideSessionMapper.class);
    private final TouristMessageMapper messageMapper = mock(TouristMessageMapper.class);
    private final DigitalHumanService digitalHumanService = mock(DigitalHumanService.class);

    @Test
    void historyReturnsMessagesInChronologicalOrderWithoutAudio() {
        givenSessionOwnedBy("anon_device");
        // mapper 按倒序取最近 N 条，服务负责翻回正序
        when(messageMapper.selectList(any())).thenReturn(List.of(
            message(2L, "TOURIST", "TEXT", "介绍一下桃花源", "audio-2.mp3"),
            message(1L, "AGENT", "TEXT", "欢迎来到景区", "audio-1.mp3")));

        List<GuideHistoryMessageResponse> history = service().history("anon_device", 1L);

        assertThat(history).extracting(GuideHistoryMessageResponse::getId).containsExactly(1L, 2L);
        assertThat(history).extracting(GuideHistoryMessageResponse::getSender)
            .containsExactly("AGENT", "TOURIST");
        assertThat(history.get(0).getContent()).isEqualTo("欢迎来到景区");
        assertThat(history.get(0).getEvidence()).isEqualTo("CHUNK:[1]");
        assertThat(history.get(0).getActionState()).isEqualTo("idle");
        assertThat(history.get(0).getCreatedAt()).isNotNull();
    }

    @Test
    void historyRejectsSessionOwnedByAnotherVisitor() {
        givenSessionOwnedBy("user_8");

        assertThatThrownBy(() -> service().history("user_7", 1L))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("无权访问");
    }

    @Test
    void resumeRebuildsSessionWithoutCreatingNewOneOrWelcomeMessage() {
        givenSessionOwnedBy("user_8");

        GuideSessionResponse response = service().resume("user_8", "AUTHENTICATED", 1L);

        assertThat(response.getSessionId()).isEqualTo(1L);
        assertThat(response.getScenicAreaId()).isEqualTo(1L);
        assertThat(response.getStatus()).isEqualTo("ACTIVE");
        assertThat(response.getWelcomeText()).isEqualTo("欢迎来到景区");
        assertThat(response.getAuthLevel()).isEqualTo("AUTHENTICATED");
        assertThat(response.isCanRecommendRoute()).isTrue();
        verify(sessionMapper, never()).insert(any(TouristGuideSession.class));
        verify(messageMapper, never()).insert(any(TouristMessage.class));
    }

    @Test
    void resumeRejectsSessionOwnedByAnotherVisitor() {
        givenSessionOwnedBy("user_8");

        assertThatThrownBy(() -> service().resume("user_7", "AUTHENTICATED", 1L))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("无权访问");
    }

    private void givenSessionOwnedBy(String owner) {
        TouristGuideSession session = new TouristGuideSession();
        session.setId(1L);
        session.setSessionNo("GS0001");
        session.setVisitorSubject(owner);
        session.setScenicAreaId(1L);
        session.setStatus("ACTIVE");
        when(sessionMapper.selectById(1L)).thenReturn(session);

        DhProfile profile = new DhProfile();
        profile.setWelcomeText("欢迎来到景区");
        when(digitalHumanService.getOrCreateDefaultProfile()).thenReturn(profile);
        when(digitalHumanService.getDefaultProfileResponse()).thenReturn(new DhProfileResponse());
    }

    private TouristMessage message(Long id, String sender, String type, String content, String audioUrl) {
        TouristMessage message = new TouristMessage();
        message.setId(id);
        message.setSessionId(1L);
        message.setSender(sender);
        message.setMessageType(type);
        message.setContent(content);
        message.setEvidenceText("CHUNK:[1]");
        message.setAudioUrl(audioUrl);
        message.setActionState("idle");
        message.setCreatedAt(LocalDateTime.now());
        return message;
    }

    private TouristGuideService service() {
        return new TouristGuideService(
            sessionMapper,
            messageMapper,
            mock(TouristLocationLogMapper.class),
            mock(TouristFenceLogMapper.class),
            mock(ScenicTopologyService.class),
            mock(QaGuideAnswerService.class),
            mock(QaChatStreamService.class),
            digitalHumanService,
            mock(TtsService.class),
            mock(AiRouteService.class),
            new ObjectMapper()
        );
    }
}
