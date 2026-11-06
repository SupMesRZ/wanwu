package com.huohuo.scenicaiguide.digitalhuman.service;

import com.huohuo.scenicaiguide.digitalhuman.dto.response.TtsResponse;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class TtsStreamService {

    private static final int MAX_SEGMENT_LENGTH = 72;

    private final TtsService ttsService;

    public TtsStreamService(TtsService ttsService) {
        this.ttsService = ttsService;
    }

    public Flux<SpeechChunk> synthesize(Long scenicAreaId, Flux<String> textDeltas) {
        return Flux.defer(() -> {
            SpeechTextSegmenter segmenter = new SpeechTextSegmenter();
            AtomicInteger sequence = new AtomicInteger();
            Flux<String> sentences = textDeltas
                .concatMapIterable(segmenter::append)
                .concatWith(Flux.defer(() -> Flux.fromIterable(segmenter.finish())));
            return sentences
                .map(this::speechText)
                .filter(sentence -> !sentence.isBlank())
                .publishOn(Schedulers.boundedElastic())
                .concatMap(sentence -> synthesizeSentence(scenicAreaId, sequence.incrementAndGet(), sentence));
        });
    }

    private Mono<SpeechChunk> synthesizeSentence(Long scenicAreaId, int sequence, String text) {
        return Mono.fromCallable(() -> {
                TtsResponse response = ttsService.synthesize(scenicAreaId, text);
                return new SpeechChunk(sequence, text, response.getAudioBase64(), response.getFormat(),
                    response.isCached(), null);
            })
            .subscribeOn(Schedulers.boundedElastic())
            .onErrorResume(error -> {
                return Mono.just(new SpeechChunk(sequence, text, null, null, false, safeError(error)));
            });
    }

    private String speechText(String value) {
        return value
            .replaceAll("\\[([^]]+)]\\([^)]*\\)", "$1")
            .replaceAll("<[^>]+>", "")
            .replaceAll("[`*_#>~]", "")
            .replaceAll("\\s+", " ")
            .trim();
    }

    private String safeError(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? "TTS 分片合成失败" : message;
    }

    private static final class SpeechTextSegmenter {

        private final StringBuilder buffer = new StringBuilder();

        private List<String> append(String delta) {
            List<String> completed = new ArrayList<>();
            if (delta == null || delta.isEmpty()) {
                return completed;
            }
            for (int index = 0; index < delta.length(); index++) {
                char current = delta.charAt(index);
                buffer.append(current);
                if (isBoundary(current)) {
                    drain(completed, buffer.length());
                } else if (buffer.length() >= MAX_SEGMENT_LENGTH) {
                    int splitAt = preferredSplit();
                    drain(completed, splitAt > 0 ? splitAt : MAX_SEGMENT_LENGTH);
                }
            }
            return completed;
        }

        private List<String> finish() {
            List<String> completed = new ArrayList<>();
            drain(completed, buffer.length());
            return completed;
        }

        private int preferredSplit() {
            for (int index = buffer.length() - 1; index >= MAX_SEGMENT_LENGTH / 2; index--) {
                char current = buffer.charAt(index);
                if (current == '，' || current == ',' || current == '、' || current == '：' || current == ':') {
                    return index + 1;
                }
            }
            return -1;
        }

        private void drain(List<String> completed, int endIndex) {
            if (endIndex <= 0) {
                return;
            }
            String sentence = buffer.substring(0, endIndex).trim();
            buffer.delete(0, endIndex);
            if (!sentence.isBlank()) {
                completed.add(sentence);
            }
        }

        private boolean isBoundary(char value) {
            return value == '。' || value == '！' || value == '？' || value == '!'
                || value == '?' || value == '；' || value == ';' || value == '\n';
        }
    }

    public record SpeechChunk(int sequence,
                              String text,
                              String audioBase64,
                              String format,
                              boolean cached,
                              String error) {
    }
}
