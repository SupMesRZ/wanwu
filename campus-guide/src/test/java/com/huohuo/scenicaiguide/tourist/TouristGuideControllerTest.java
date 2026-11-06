package com.huohuo.scenicaiguide.tourist;

import com.huohuo.scenicaiguide.common.enums.ErrorCode;
import com.huohuo.scenicaiguide.common.exception.BusinessException;
import com.huohuo.scenicaiguide.digitalhuman.service.AsrService;
import com.huohuo.scenicaiguide.digitalhuman.service.DigitalHumanService;
import com.huohuo.scenicaiguide.digitalhuman.service.TtsStreamService;
import com.huohuo.scenicaiguide.qa.service.QaChatStreamService;
import com.huohuo.scenicaiguide.tourist.dto.response.GuideMessageResponse;
import com.huohuo.scenicaiguide.system.security.AuthPrincipal;
import com.huohuo.scenicaiguide.tourist.controller.TouristGuideController;
import com.huohuo.scenicaiguide.tourist.dto.request.GuideMessageRequest;
import com.huohuo.scenicaiguide.tourist.dto.response.GuideStreamEvent;
import com.huohuo.scenicaiguide.tourist.service.TouristGuideService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TouristGuideControllerTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void streamShouldReturnErrorEventForSynchronousBusinessException() {
        TouristGuideService guideService = mock(TouristGuideService.class);
        TouristGuideController controller = new TouristGuideController(
            guideService, mock(DigitalHumanService.class), mock(TtsStreamService.class), mock(AsrService.class));
        AuthPrincipal principal = new AuthPrincipal(
            "anon_device", "TOURIST", "ANONYMOUS", false, null);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, null, List.of()));
        GuideMessageRequest request = new GuideMessageRequest();
        request.setContent("推荐一条路线");
        request.setIntent("ROUTE_RECOMMEND");
        when(guideService.prepareStream(eq("anon_device"), eq("ANONYMOUS"), eq(1L), eq(request), any()))
            .thenThrow(new BusinessException(ErrorCode.FORBIDDEN, "登录后即可生成专属游览路线"));

        List<ServerSentEvent<GuideStreamEvent>> events = controller.messageStream(1L, request)
            .collectList()
            .block();

        assertThat(events).extracting(ServerSentEvent::event).containsExactly("status", "error");
        assertThat(events.getFirst().data().getStatus()).isEqualTo("正在理解你的需求");
        assertThat(events.getLast().data().getError()).isEqualTo("登录后即可生成专属游览路线");
    }

    @Test
    void streamShouldInterleaveTextAudioAndCompletedEvents() {
        TouristGuideService guideService = mock(TouristGuideService.class);
        TtsStreamService ttsStreamService = mock(TtsStreamService.class);
        TouristGuideController controller = new TouristGuideController(
            guideService, mock(DigitalHumanService.class), ttsStreamService, mock(AsrService.class));
        AuthPrincipal principal = new AuthPrincipal(
            "anon_device", "TOURIST", "ANONYMOUS", false, null);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, null, List.of()));
        GuideMessageRequest request = new GuideMessageRequest();
        request.setContent("你好");
        when(guideService.prepareStream(eq("anon_device"), eq("ANONYMOUS"), eq(1L), eq(request), any()))
            .thenAnswer(invocation -> {
                java.util.function.Consumer<String> progress = invocation.getArgument(4);
                progress.accept("正在读取景区地图");
                return TouristGuideService.GuideStreamPlan.streaming(
                    1L, 1L, new QaChatStreamService.StreamAnswer(Flux.just("你好。"), "AGENT_TOOLS"));
            });
        GuideMessageResponse completed = new GuideMessageResponse();
        completed.setAnswer("你好。");
        when(guideService.completeStream(eq(1L), eq(1L), eq("你好。"), eq("AGENT_TOOLS"),
            isNull(), isNull(), isNull(), eq(true))).thenReturn(completed);
        when(ttsStreamService.synthesize(eq(1L), any())).thenReturn(Flux.just(
            new TtsStreamService.SpeechChunk(1, "你好。", "audio", "wav", false, null)));

        List<ServerSentEvent<GuideStreamEvent>> events = controller.messageStream(1L, request)
            .collectList()
            .block();

        assertThat(events).extracting(ServerSentEvent::event)
            .contains("status", "delta", "audio", "completed");
        assertThat(events.stream().map(ServerSentEvent::data)
            .filter(event -> "status".equals(event.getType()))
            .map(GuideStreamEvent::getStatus))
            .contains("正在理解你的需求", "正在读取景区地图");
        GuideStreamEvent audio = events.stream()
            .map(ServerSentEvent::data)
            .filter(event -> "audio".equals(event.getType()))
            .findFirst()
            .orElseThrow();
        assertThat(audio.getSequence()).isEqualTo(1);
        assertThat(audio.getAudioBase64()).isEqualTo("audio");
    }
}
