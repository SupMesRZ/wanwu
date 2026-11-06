package com.huohuo.scenicaiguide.digitalhuman;

import com.huohuo.scenicaiguide.common.TestTags;
import org.junit.jupiter.api.Tag;

import com.alibaba.nls.client.protocol.InputFormatEnum;
import com.alibaba.nls.client.protocol.NlsClient;
import com.alibaba.nls.client.protocol.OutputFormatEnum;
import com.alibaba.nls.client.protocol.SampleRateEnum;
import com.alibaba.nls.client.protocol.asr.SpeechTranscriber;
import com.alibaba.nls.client.protocol.asr.SpeechTranscriberListener;
import com.alibaba.nls.client.protocol.asr.SpeechTranscriberResponse;
import com.alibaba.nls.client.protocol.tts.SpeechSynthesizer;
import com.alibaba.nls.client.protocol.tts.SpeechSynthesizerListener;
import com.alibaba.nls.client.protocol.tts.SpeechSynthesizerResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 阿里云 NLS 语音合成测试
 * <p>
 * 将景区讲解词合成为 WAV 音频并保存到测试资源目录
 */
@Tag(TestTags.EXTERNAL)
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(org.junit.jupiter.api.MethodOrderer.OrderAnnotation.class)
class NlsAudioTest {

    @Autowired
    private NlsClient nlsClient;

    @Value("${aliyun.nls.app-key}")
    private String appKey;

    /**
     * 苏堤讲解词，用于 TTS 合成测试
     */
    private static final String GUIDE_TEXT = """
            当年苏东坡在杭州做官时开浚西湖，取湖泥葑草筑成，横贯湖南北，将西湖分成了内外两部分，全长2.8公里。
            堤上有映波、锁澜、望山、压堤、东浦、跨虹六桥，古朴美观。
            苏堤的美丽不仅在于它在秀丽的西湖中穿越而过，还因为堤上种满了形态优美的香樟树及其他各种植物，是一条全年都常绿的长堤。
            特别是到了春天，堤上桃花盛开，树发新叶，一派生机盎然的景象，加之春风和熙，令人心旷神怡。
            堤南端建有"苏东坡纪念馆"，规模不大，可以免费参观。
            """;

    /**
     * 音频输出目录：src/test/resources/audio
     */
    private static final Path AUDIO_DIR = Paths.get("src/test/resources/audio");

    @Test
    @DisplayName("TTS：合成景区讲解词并保存为 WAV")
    void testTtsSynthesisToWav() throws Exception {
        // 确保输出目录存在
        Files.createDirectories(AUDIO_DIR);
        Path outputFile = AUDIO_DIR.resolve("sudi_guide.wav");

        CountDownLatch completeLatch = new CountDownLatch(1);
        AtomicBoolean hasError = new AtomicBoolean(false);
        AtomicLong firstLatency = new AtomicLong(-1);

        // 记录开始时间（用于计算首包延迟）
        AtomicLong startTime = new AtomicLong(System.currentTimeMillis());

        // 流式写入文件（参考官方 demo）
        FileOutputStream fout = new FileOutputStream(outputFile.toFile());

        SpeechSynthesizerListener listener = new SpeechSynthesizerListener() {
            private boolean firstBinary = true;

            @Override
            public void onMessage(ByteBuffer message) {
                try {
                    if (firstBinary) {
                        firstBinary = false;
                        firstLatency.set(System.currentTimeMillis());
                        System.out.println("首包延迟: " + (System.currentTimeMillis() - startTime.get()) + " ms");
                    }
                    byte[] bytes = new byte[message.remaining()];
                    message.get(bytes);
                    fout.write(bytes);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onComplete(SpeechSynthesizerResponse response) {
                closeFile();
                System.out.println("TTS 完成: " + response.getName() + ", status: " + response.getStatus());
                completeLatch.countDown();
            }

            @Override
            public void onFail(SpeechSynthesizerResponse response) {
                hasError.set(true);
                closeFile();
                System.out.println("TTS 失败: " + response.getStatusText());
                completeLatch.countDown();
            }

            private void closeFile() {
                try {
                    fout.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };

        SpeechSynthesizer synthesizer = new SpeechSynthesizer(nlsClient, listener);
        try {
            synthesizer.setAppKey(appKey);
            synthesizer.setFormat(OutputFormatEnum.WAV);
            synthesizer.setSampleRate(SampleRateEnum.SAMPLE_RATE_16K);
            synthesizer.setVoice("siyue");
            synthesizer.setText(GUIDE_TEXT);
            synthesizer.setVolume(50);
            synthesizer.setSpeechRate(0);
            synthesizer.setPitchRate(0);

            System.out.println("=== TTS 合成开始 ===");
            System.out.println("文本长度: " + GUIDE_TEXT.length() + " 字");
            System.out.println("输出文件: " + outputFile.toAbsolutePath());

            synthesizer.start();
            synthesizer.waitForComplete(60000); // 长文本给 60 秒超时

            // 等待回调完成（确保文件已关闭）
            boolean completed = completeLatch.await(10, TimeUnit.SECONDS);
            assertTrue(completed, "TTS 回调应在 10 秒内完成");
            assertFalse(hasError.get(), "TTS 不应失败");

            // 断言文件
            assertTrue(Files.exists(outputFile), "WAV 文件应已生成");
            long fileSize = Files.size(outputFile);
            assertTrue(fileSize > 1024,
                    "WAV 文件应大于 1KB，实际: " + fileSize + " 字节");

            System.out.println("=== TTS 合成成功 ===");
            System.out.println("文件大小: " + fileSize + " 字节 (" + (fileSize / 1024) + " KB)");
            if (firstLatency.get() > 0) {
                System.out.println("首包延迟: " + (firstLatency.get() - startTime.get()) + " ms");
            }

        } finally {
            synthesizer.close();
        }
    }

    @Test
    @Order(2)
    @DisplayName("ASR：实时识别 TTS 合成的 WAV 音频")
    void testAsrTranscription() throws Exception {
        Path audioFile = AUDIO_DIR.resolve("sudi_guide.wav");
        assertTrue(Files.exists(audioFile), "TTS 音频文件应先于 ASR 测试生成");

        CountDownLatch completeLatch = new CountDownLatch(1);
        AtomicReference<String> finalResult = new AtomicReference<>();
        AtomicReference<String> failReason = new AtomicReference<>();

        SpeechTranscriberListener listener = new SpeechTranscriberListener() {
            @Override
            public void onTranscriberStart(SpeechTranscriberResponse response) {
                System.out.println("ASR 开始，task_id: " + response.getTaskId());
            }

            @Override
            public void onSentenceBegin(SpeechTranscriberResponse response) {
                System.out.println("句子开始检测");
            }

            @Override
            public void onSentenceEnd(SpeechTranscriberResponse response) {
                String text = response.getTransSentenceText();
                finalResult.set(text);
                System.out.println("句子识别完成: " + text);
            }

            @Override
            public void onTranscriptionResultChange(SpeechTranscriberResponse response) {
                System.out.println("中间结果: " + response.getTransSentenceText());
            }

            @Override
            public void onTranscriptionComplete(SpeechTranscriberResponse response) {
                System.out.println("ASR 识别全部完成");
                completeLatch.countDown();
            }

            @Override
            public void onFail(SpeechTranscriberResponse response) {
                failReason.set(response.getStatusText());
                System.out.println("ASR 失败: " + response.getStatusText());
                completeLatch.countDown();
            }
        };

        // 使用第一个构造函数（不传 token），然后 setAppKey
        SpeechTranscriber transcriber = new SpeechTranscriber(nlsClient, listener);
        try {
            transcriber.setAppKey(appKey);
            transcriber.setFormat(InputFormatEnum.PCM);
            transcriber.setSampleRate(SampleRateEnum.SAMPLE_RATE_16K);
            transcriber.setEnableIntermediateResult(true);
            transcriber.setEnablePunctuation(true);
            transcriber.setEnableITN(false);

            System.out.println("=== ASR 识别开始 ===");
            transcriber.start();

            // 读取 WAV 文件，跳过 44 字节标准 WAV 头后发送 PCM 数据
            try (FileInputStream fis = new FileInputStream(audioFile.toFile())) {
                long skipped = fis.skip(44);
                System.out.println("已跳过 WAV 头: " + skipped + " 字节");

                byte[] buffer = new byte[3200];
                int len;
                while ((len = fis.read(buffer)) > 0) {
                    transcriber.send(buffer, len);
                    Thread.sleep(getSleepDelta(len, 16000));
                }
            }

            transcriber.stop();

            // 等待回调完成（避免竞态）
            boolean completed = completeLatch.await(30, TimeUnit.SECONDS);
            assertTrue(completed, "ASR 回调应在 30 秒内完成");
            assertNull(failReason.get(), "ASR 不应失败: " + failReason.get());

            String result = finalResult.get();
            assertNotNull(result, "识别结果不应为空");
            assertFalse(result.isBlank(), "识别结果不应为空白");

            // 闭环断言：识别结果应包含原始文本中的关键词
            boolean containsKeyword = result.contains("西湖") || result.contains("断桥") || result.contains("苏堤");
            assertTrue(containsKeyword,
                    "识别结果应包含关键词，原始文本包含'西湖/断桥/苏堤'，识别结果: " + result);

            System.out.println("=== 闭环验证通过 ===");
            System.out.println("识别结果: " + result);

        } finally {
            transcriber.close();
        }
    }

    /**
     * 根据数据包大小和采样率计算发送间隔（毫秒），模拟实时音频流
     */
    private static int getSleepDelta(int dataSize, int sampleRate) {
        // 16 位采样、单通道
        return (dataSize * 10 * 8000) / (160 * sampleRate);
    }
}
