package com.huohuo.scenicaiguide.digitalhuman;

import com.huohuo.scenicaiguide.digitalhuman.support.SilentWavGenerator;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.assertj.core.api.Assertions.assertThat;

class SilentWavGeneratorTest {

    @Test
    void shortTextShouldProduceMinDurationSilentWav() {
        byte[] wav = SilentWavGenerator.generate("你好");

        // 最短 600ms：16000Hz * 2B * 0.6s = 19200 数据字节 + 44 头
        assertThat(wav).hasSize(44 + 19200);
        assertThat(wav).startsWith(new byte[]{'R', 'I', 'F', 'F'});
        assertThat(new String(wav, 8, 4)).isEqualTo("WAVE");
        ByteBuffer header = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
        assertThat(header.getInt(4)).isEqualTo(wav.length - 8);
        assertThat(header.getInt(24)).isEqualTo(16000);
        assertThat(header.getInt(40)).isEqualTo(19200);
    }

    @Test
    void nullTextShouldFallBackToMinDuration() {
        assertThat(SilentWavGenerator.generate(null)).hasSize(44 + 19200);
    }

    @Test
    void longTextShouldClampToMaxDuration() {
        byte[] wav = SilentWavGenerator.generate("景".repeat(500));

        // 最长 6000ms：16000Hz * 2B * 6s = 192000 数据字节
        assertThat(wav).hasSize(44 + 192000);
    }

    @Test
    void durationShouldScaleWithTextLength() {
        byte[] shortWav = SilentWavGenerator.generate("你好");
        byte[] mediumWav = SilentWavGenerator.generate("欢迎来到美丽的景区参观游览");

        assertThat(mediumWav.length).isGreaterThan(shortWav.length);
    }
}
