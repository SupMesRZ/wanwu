package com.huohuo.scenicaiguide.digitalhuman;

import com.huohuo.scenicaiguide.common.TestTags;
import org.junit.jupiter.api.Tag;

import com.alibaba.nls.client.protocol.NlsClient;
import com.alibaba.nls.client.protocol.OutputFormatEnum;
import com.alibaba.nls.client.protocol.SampleRateEnum;
import com.alibaba.nls.client.protocol.tts.FlowingSpeechSynthesizer;
import com.alibaba.nls.client.protocol.tts.FlowingSpeechSynthesizerListener;
import com.alibaba.nls.client.protocol.tts.FlowingSpeechSynthesizerResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 阿里云 NLS 流式语音合成（FlowingSpeechSynthesizer）测试
 * <p>
 * 验证首包延迟、流式分句合成、音频完整性
 */
@Tag(TestTags.EXTERNAL)
@SpringBootTest
class NlsFlowingTtsTest {

    @Autowired
    private NlsClient nlsClient;

    @Value("${aliyun.nls.app-key}")
    private String appKey;

    private static final Path AUDIO_DIR = Paths.get("src/test/resources/audio");

    /**
     * 讲解词拆分为多片段，模拟 LLM 流式输出
     */
    private static final List<String> SEGMENTS = List.of(
            "当年苏东坡在杭州做官时开浚西湖，",
            "取湖泥葑草筑成，横贯湖南北，",
            "将西湖分成了内外两部分，全长2.8公里。",
            "堤上有映波、锁澜、望山、压堤、东浦、跨虹六桥，古朴美观。",
            "苏堤的美丽不仅在于它在秀丽的西湖中穿越而过，",
            "还因为堤上种满了形态优美的香樟树及其他各种植物，",
            "是一条全年都常绿的长堤。",
            "特别是到了春天，堤上桃花盛开，树发新叶，一派生机盎然的景象，",
            "加之春风和熙，令人心旷神怡。",
            "堤南端建有\"苏东坡纪念馆\"，规模不大，可以免费参观。"
    );

    @Test
    @DisplayName("首包延迟：单句短文本验证 TTFB")
    void testFirstByteLatency() throws Exception {
        Files.createDirectories(AUDIO_DIR);
        Path outputFile = AUDIO_DIR.resolve("flowing_tts_ttfb.wav");

        AtomicLong ttfb = new AtomicLong(-1);
        AtomicLong startTime = new AtomicLong(System.currentTimeMillis());
        AtomicBoolean hasError = new AtomicBoolean(false);

        FileOutputStream fout = new FileOutputStream(outputFile.toFile());

        FlowingSpeechSynthesizerListener listener = new FlowingSpeechSynthesizerListener() {
            private boolean firstBinary = true;

            @Override
            public void onSynthesisStart(FlowingSpeechSynthesizerResponse response) {
                System.out.println("流式 TTS 开始");
            }

            @Override
            public void onSentenceBegin(FlowingSpeechSynthesizerResponse response) {
                System.out.println("句子开始");
            }

            @Override
            public void onSentenceEnd(FlowingSpeechSynthesizerResponse response) {
                System.out.println("句子结束");
            }

            @Override
            public void onAudioData(ByteBuffer message) {
                try {
                    if (firstBinary) {
                        firstBinary = false;
                        ttfb.set(System.currentTimeMillis() - startTime.get());
                        System.out.println("首包延迟: " + ttfb.get() + " ms");
                    }
                    byte[] bytes = new byte[message.remaining()];
                    message.get(bytes);
                    fout.write(bytes);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onSynthesisComplete(FlowingSpeechSynthesizerResponse response) {
                System.out.println("合成完成");
                closeFile();
            }

            @Override
            public void onFail(FlowingSpeechSynthesizerResponse response) {
                hasError.set(true);
                System.out.println("合成失败: " + response.getStatusText());
                closeFile();
            }

            @Override
            public void onSentenceSynthesis(FlowingSpeechSynthesizerResponse response) {
                // 增量时间戳回调，可选
            }

            private void closeFile() {
                try {
                    fout.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };

        FlowingSpeechSynthesizer synthesizer = new FlowingSpeechSynthesizer(nlsClient, listener);
        try {
            synthesizer.setAppKey(appKey);
            synthesizer.setFormat(OutputFormatEnum.WAV);
            synthesizer.setSampleRate(SampleRateEnum.SAMPLE_RATE_16K);
            synthesizer.setVoice("siyue");
            synthesizer.setVolume(50);
            synthesizer.setSpeechRate(0);
            synthesizer.setPitchRate(0);

            System.out.println("=== 首包延迟测试 ===");
            startTime.set(System.currentTimeMillis());
            synthesizer.start();
            synthesizer.send("欢迎来到西湖断桥");
            synthesizer.stop();

            assertFalse(hasError.get(), "合成不应失败");
            assertTrue(ttfb.get() > 0, "应收到首包音频");
            assertTrue(ttfb.get() < 2000,
                    "首包延迟应小于 2 秒，实际: " + ttfb.get() + " ms");

            long fileSize = Files.size(outputFile);
            assertTrue(fileSize > 1024, "WAV 文件应大于 1KB");

            System.out.println("TTFB: " + ttfb.get() + " ms, 文件大小: " + fileSize + " 字节");

        } finally {
            synthesizer.close();
        }
    }

    @Test
    @DisplayName("多片段流式合成：模拟 LLM 流式输出")
    void testMultiSegmentStreaming() throws Exception {
        Files.createDirectories(AUDIO_DIR);
        Path outputFile = AUDIO_DIR.resolve("flowing_tts_streaming.wav");

        AtomicLong ttfb = new AtomicLong(-1);
        AtomicLong startTime = new AtomicLong(System.currentTimeMillis());
        AtomicBoolean hasError = new AtomicBoolean(false);
        AtomicInteger sentenceBeginCount = new AtomicInteger(0);
        AtomicInteger sentenceEndCount = new AtomicInteger(0);
        AtomicInteger audioDataCount = new AtomicInteger(0);
        AtomicLong totalAudioBytes = new AtomicLong(0);

        FileOutputStream fout = new FileOutputStream(outputFile.toFile());

        FlowingSpeechSynthesizerListener listener = new FlowingSpeechSynthesizerListener() {
            private boolean firstBinary = true;

            @Override
            public void onSynthesisStart(FlowingSpeechSynthesizerResponse response) {
                System.out.println("流式 TTS 开始，session_id: " + response.getTaskId());
            }

            @Override
            public void onSentenceBegin(FlowingSpeechSynthesizerResponse response) {
                sentenceBeginCount.incrementAndGet();
            }

            @Override
            public void onSentenceEnd(FlowingSpeechSynthesizerResponse response) {
                sentenceEndCount.incrementAndGet();
                System.out.println("句子完成 [#" + sentenceEndCount.get() + "]");
            }

            @Override
            public void onAudioData(ByteBuffer message) {
                try {
                    if (firstBinary) {
                        firstBinary = false;
                        ttfb.set(System.currentTimeMillis() - startTime.get());
                        System.out.println("首包延迟: " + ttfb.get() + " ms");
                    }
                    byte[] bytes = new byte[message.remaining()];
                    message.get(bytes);
                    fout.write(bytes);
                    audioDataCount.incrementAndGet();
                    totalAudioBytes.addAndGet(bytes.length);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onSynthesisComplete(FlowingSpeechSynthesizerResponse response) {
                System.out.println("全部合成完成");
                closeFile();
            }

            @Override
            public void onFail(FlowingSpeechSynthesizerResponse response) {
                hasError.set(true);
                System.out.println("合成失败: " + response.getStatusText());
                closeFile();
            }

            @Override
            public void onSentenceSynthesis(FlowingSpeechSynthesizerResponse response) {
                // 可选实现
            }

            private void closeFile() {
                try {
                    fout.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };

        FlowingSpeechSynthesizer synthesizer = new FlowingSpeechSynthesizer(nlsClient, listener);
        try {
            synthesizer.setAppKey(appKey);
            synthesizer.setFormat(OutputFormatEnum.WAV);
            synthesizer.setSampleRate(SampleRateEnum.SAMPLE_RATE_16K);
            synthesizer.setVoice("siyue");
            synthesizer.setVolume(50);
            synthesizer.setSpeechRate(0);
            synthesizer.setPitchRate(0);
            synthesizer.setMinSendIntervalMS(100);

            System.out.println("=== 多片段流式合成测试 ===");
            System.out.println("共 " + SEGMENTS.size() + " 个片段，逐段发送");

            startTime.set(System.currentTimeMillis());
            synthesizer.start();

            // 模拟 LLM 流式输出：逐段发送，每段间隔 200ms
            for (int i = 0; i < SEGMENTS.size(); i++) {
                String segment = SEGMENTS.get(i);
                System.out.println("[发送 " + (i + 1) + "/" + SEGMENTS.size() + "] " +
                        segment.substring(0, Math.min(20, segment.length())) + "...");
                synthesizer.send(segment);
                Thread.sleep(200); // 模拟 LLM 逐段生成间隔
            }

            synthesizer.stop();

            assertFalse(hasError.get(), "合成不应失败");
            assertTrue(ttfb.get() > 0, "应收到首包音频");
            assertTrue(ttfb.get() < 2000,
                    "首包延迟应小于 2 秒，实际: " + ttfb.get() + " ms");
            assertTrue(audioDataCount.get() >= SEGMENTS.size(),
                    "音频回调次数应 >= 片段数，实际: " + audioDataCount.get());
            assertTrue(sentenceBeginCount.get() > 0, "应有句子开始回调");
            assertTrue(sentenceEndCount.get() > 0, "应有句子结束回调");

            long fileSize = Files.size(outputFile);
            assertTrue(fileSize > 10 * 1024, "WAV 文件应大于 10KB，实际: " + fileSize + " 字节");

            System.out.println("=== 流式合成测试通过 ===");
            System.out.println("首包延迟: " + ttfb.get() + " ms");
            System.out.println("音频回调次数: " + audioDataCount.get());
            System.out.println("句子开始/结束: " + sentenceBeginCount.get() + "/" + sentenceEndCount.get());
            System.out.println("总音频字节: " + totalAudioBytes.get());
            System.out.println("文件大小: " + fileSize + " 字节 (" + (fileSize / 1024) + " KB)");

        } finally {
            synthesizer.close();
        }
    }
}
