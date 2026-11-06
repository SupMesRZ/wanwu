package com.huohuo.scenicaiguide.digitalhuman;

import com.huohuo.scenicaiguide.digitalhuman.dto.response.TtsResponse;
import com.huohuo.scenicaiguide.digitalhuman.service.TtsService;
import com.huohuo.scenicaiguide.digitalhuman.service.TtsStreamService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TtsStreamServiceTest {

    @Test
    void splitsModelDeltasIntoOrderedSpeechChunks() {
        TtsService ttsService = mock(TtsService.class);
        when(ttsService.synthesize(1L, "你好！")).thenReturn(audio("audio-1", false));
        when(ttsService.synthesize(1L, "欢迎来到桃花源。")).thenReturn(audio("audio-2", true));
        when(ttsService.synthesize(1L, "接下来我为你介绍路线")).thenReturn(audio("audio-3", false));
        TtsStreamService service = new TtsStreamService(ttsService);

        List<TtsStreamService.SpeechChunk> chunks = service.synthesize(
            1L, Flux.just("你好！欢迎", "来到桃花源。接下来", "我为你介绍路线"))
            .collectList()
            .block();

        assertThat(chunks).extracting(TtsStreamService.SpeechChunk::sequence)
            .containsExactly(1, 2, 3);
        assertThat(chunks).extracting(TtsStreamService.SpeechChunk::text)
            .containsExactly("你好！", "欢迎来到桃花源。", "接下来我为你介绍路线");
        assertThat(chunks).extracting(TtsStreamService.SpeechChunk::audioBase64)
            .containsExactly("audio-1", "audio-2", "audio-3");
        assertThat(chunks.get(1).cached()).isTrue();
        InOrder order = inOrder(ttsService);
        order.verify(ttsService).synthesize(1L, "你好！");
        order.verify(ttsService).synthesize(1L, "欢迎来到桃花源。");
        order.verify(ttsService).synthesize(1L, "接下来我为你介绍路线");
    }

    @Test
    void removesMarkdownBeforeSpeechAndKeepsTextStreamAliveOnChunkFailure() {
        TtsService ttsService = mock(TtsService.class);
        when(ttsService.synthesize(1L, "桃花源 路线 推荐。")).thenThrow(new IllegalStateException("tts unavailable"));
        TtsStreamService service = new TtsStreamService(ttsService);

        List<TtsStreamService.SpeechChunk> chunks = service.synthesize(
            1L, Flux.just("**桃花源** `路线` [推荐](https://example.com)。"))
            .collectList()
            .block();

        assertThat(chunks).singleElement().satisfies(chunk -> {
            assertThat(chunk.text()).isEqualTo("桃花源 路线 推荐。");
            assertThat(chunk.audioBase64()).isNull();
            assertThat(chunk.error()).contains("tts unavailable");
        });
    }

    private TtsResponse audio(String base64, boolean cached) {
        TtsResponse response = new TtsResponse();
        response.setAudioBase64(base64);
        response.setFormat("wav");
        response.setCached(cached);
        return response;
    }
}
