package com.huohuo.scenicaiguide.digitalhuman.service;

import com.huohuo.scenicaiguide.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ASR 入参校验测试
 * <p>
 * NLS 交互部分依赖真实云服务，不进单测；能离线钉住的是入参防线——
 * 空音频、超大音频、非 WAV 都应该在调用云之前就被拦下，
 * 否则每条坏请求都是一次浪费的云调用和一段难以解读的 NLS 报错
 */
class AsrServiceValidationTest {

    @Test
    void emptyAudioIsRejected() {
        assertThatThrownBy(() -> AsrService.validate(null))
            .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> AsrService.validate(new byte[0]))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void oversizedAudioIsRejected() {
        assertThatThrownBy(() -> AsrService.validate(new byte[AsrService.MAX_AUDIO_BYTES + 1]))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("10MB");
    }

    @Test
    void nonWavAudioIsRejected() {
        byte[] webm = new byte[100];
        // webm 的 EBML 头不是 RIFF，必须在本地拦下而不是丢给 NLS
        assertThatThrownBy(() -> AsrService.validate(webm))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("WAV");
    }

    @Test
    void validWavHeaderPasses() {
        byte[] wav = new byte[100];
        byte[] riff = "RIFF".getBytes();
        byte[] wave = "WAVE".getBytes();
        System.arraycopy(riff, 0, wav, 0, 4);
        System.arraycopy(wave, 0, wav, 8, 4);

        assertThatCode(() -> AsrService.validate(wav)).doesNotThrowAnyException();
    }
}
