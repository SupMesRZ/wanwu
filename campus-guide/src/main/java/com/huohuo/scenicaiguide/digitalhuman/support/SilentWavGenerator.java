package com.huohuo.scenicaiguide.digitalhuman.support;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * 静音 WAV 生成器：无 TTS 密钥的演示部署里让语音链路端到端可跑
 *
 * <p>产出 16kHz 16bit 单声道 PCM 静音，时长按文本长度估算，
 * 前端照常走"音频 + 字幕"播放流程，听到的只是安静
 */
public final class SilentWavGenerator {

    private static final int SAMPLE_RATE = 16000;
    private static final int BYTES_PER_SAMPLE = 2;
    /** 每个字符估算 90ms 时长，接近中文 TTS 语速 */
    private static final int MILLIS_PER_CHAR = 90;
    private static final int MIN_MILLIS = 600;
    private static final int MAX_MILLIS = 6000;

    private SilentWavGenerator() {
    }

    /**
     * 按文本长度生成静音 WAV 字节
     */
    public static byte[] generate(String text) {
        int length = text == null ? 0 : text.length();
        int millis = Math.clamp((long) length * MILLIS_PER_CHAR, MIN_MILLIS, MAX_MILLIS);
        int dataSize = SAMPLE_RATE * BYTES_PER_SAMPLE * millis / 1000;

        ByteArrayOutputStream out = new ByteArrayOutputStream(44 + dataSize);
        ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        header.put(new byte[]{'R', 'I', 'F', 'F'});
        header.putInt(36 + dataSize);
        header.put(new byte[]{'W', 'A', 'V', 'E'});
        header.put(new byte[]{'f', 'm', 't', ' '});
        header.putInt(16);
        header.putShort((short) 1);
        header.putShort((short) 1);
        header.putInt(SAMPLE_RATE);
        header.putInt(SAMPLE_RATE * BYTES_PER_SAMPLE);
        header.putShort((short) BYTES_PER_SAMPLE);
        header.putShort((short) 16);
        header.put(new byte[]{'d', 'a', 't', 'a'});
        header.putInt(dataSize);
        out.writeBytes(header.array());
        out.writeBytes(new byte[dataSize]);
        return out.toByteArray();
    }
}
