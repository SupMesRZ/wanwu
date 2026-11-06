package com.huohuo.scenicaiguide.digitalhuman;

import com.huohuo.scenicaiguide.common.TestTags;
import org.junit.jupiter.api.Tag;

import com.alibaba.nls.client.protocol.NlsClient;
import com.alibaba.nls.client.protocol.OutputFormatEnum;
import com.alibaba.nls.client.protocol.SampleRateEnum;
import com.alibaba.nls.client.protocol.tts.StreamInputTts;
import com.alibaba.nls.client.protocol.tts.StreamInputTtsListener;
import com.alibaba.nls.client.protocol.tts.StreamInputTtsResponse;
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
 * 阿里云 NLS 实时语音合成（流式输入 TTS）测试
 * <p>
 * 将讲解词分句流式输入，实时获取音频并保存为 WAV
 */
@Tag(TestTags.EXTERNAL)
@SpringBootTest
class NlsStreamingTtsTest {

    @Autowired
    private NlsClient nlsClient;

    @Value("${aliyun.nls.app-key}")
    private String appKey;

    /**
     * 音频输出目录
     */
    private static final Path AUDIO_DIR = Paths.get("src/test/resources/audio");

    /**
     * 讲解词拆分为多句，模拟 LLM 流式输出
     */
    private static final List<String> SENTENCES = List.of(
            "当年苏东坡在杭州做官时开浚西湖，取湖泥葑草筑成，横贯湖南北，将西湖分成了内外两部分，全长2.8公里。",
            "堤上有映波、锁澜、望山、压堤、东浦、跨虹六桥，古朴美观。",
            "苏堤的美丽不仅在于它在秀丽的西湖中穿越而过，还因为堤上种满了形态优美的香樟树及其他各种植物，是一条全年都常绿的长堤。",
            "特别是到了春天，堤上桃花盛开，树发新叶，一派生机盎然的景象，加之春风和熙，令人心旷神怡。",
            "堤南端建有\"苏东坡纪念馆\"，规模不大，可以免费参观。"
    );

    @Test
    @DisplayName("流式 TTS：分句输入实时合成并保存为 WAV")
    void testStreamingTts() throws Exception {
        Files.createDirectories(AUDIO_DIR);
        Path outputFile = AUDIO_DIR.resolve("sudi_streaming.wav");

        AtomicBoolean hasError = new AtomicBoolean(false);
        AtomicInteger sentenceCount = new AtomicInteger(0);
        AtomicLong firstLatency = new AtomicLong(-1);
        AtomicLong startTime = new AtomicLong(System.currentTimeMillis());

        FileOutputStream fout = new FileOutputStream(outputFile.toFile());

        StreamInputTtsListener listener = new StreamInputTtsListener() {
            private boolean firstBinary = true;

            @Override
            public void onSynthesisStart(StreamInputTtsResponse response) {
                System.out.println("流式 TTS 开始，session_id: " + response.getSessionId());
            }

            @Override
            public void onSentenceBegin(StreamInputTtsResponse response) {
                System.out.println("句子合成开始 [#" + sentenceCount.incrementAndGet() + "]");
            }

            @Override
            public void onAudioData(ByteBuffer message) {
                try {
                    if (firstBinary) {
                        firstBinary = false;
                        firstLatency.set(System.currentTimeMillis());
                        System.out.println("首包延迟: " + (firstLatency.get() - startTime.get()) + " ms");
                    }
                    byte[] bytes = new byte[message.remaining()];
                    message.get(bytes);
                    fout.write(bytes);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onSentenceEnd(StreamInputTtsResponse response) {
                System.out.println("句子合成完成");
            }

            @Override
            public void onSynthesisComplete(StreamInputTtsResponse response) {
                System.out.println("流式 TTS 全部完成，status: " + response.getStatus());
                closeFile();
            }

            @Override
            public void onFail(StreamInputTtsResponse response) {
                hasError.set(true);
                System.out.println("流式 TTS 失败: " + response.getStatusText());
                closeFile();
            }

            @Override
            public void onSentenceSynthesis(StreamInputTtsResponse response) {
                // 单句合成回调，可选实现
            }

            private void closeFile() {
                try {
                    fout.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };

        // 使用第一个构造函数（不传 token），然后 setAppKey
        StreamInputTts tts = new StreamInputTts(nlsClient, listener);
        try {
            tts.setAppKey(appKey);
            tts.setVoice("siyue");
            tts.setFormat(OutputFormatEnum.WAV);
            tts.setSampleRate(SampleRateEnum.SAMPLE_RATE_16K);
            tts.setVolume(50);
            tts.setSpeechRate(0);
            tts.setPitchRate(0);

            System.out.println("=== 流式 TTS 开始 ===");
            System.out.println("共 " + SENTENCES.size() + " 句，逐句流式输入");

            // 启动流式合成
            tts.startStreamInputTts();

            // 逐句发送文本
            for (int i = 0; i < SENTENCES.size(); i++) {
                String sentence = SENTENCES.get(i);
                System.out.println("[发送第 " + (i + 1) + "/" + SENTENCES.size() + " 句] " +
                        sentence.substring(0, Math.min(30, sentence.length())) + "...");
                tts.sendStreamInputTts(sentence);
            }

            // 通知服务端文本输入完毕
            tts.stopStreamInputTts();

            // 等待全部合成完成
            tts.waitForComplete();

            assertFalse(hasError.get(), "流式 TTS 不应失败");

            // 断言输出文件
            assertTrue(Files.exists(outputFile), "WAV 文件应已生成");
            long fileSize = Files.size(outputFile);
            assertTrue(fileSize > 1024,
                    "WAV 文件应大于 1KB，实际: " + fileSize + " 字节");

            System.out.println("=== 流式 TTS 合成成功 ===");
            System.out.println("合成句数: " + sentenceCount.get());
            System.out.println("文件大小: " + fileSize + " 字节 (" + (fileSize / 1024) + " KB)");
            if (firstLatency.get() > 0) {
                System.out.println("首包延迟: " + (firstLatency.get() - startTime.get()) + " ms");
            }

        } finally {
            tts.close();
        }
    }
}
