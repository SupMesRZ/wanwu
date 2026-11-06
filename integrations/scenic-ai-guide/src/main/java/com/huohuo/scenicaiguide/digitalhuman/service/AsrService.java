package com.huohuo.scenicaiguide.digitalhuman.service;

import com.alibaba.nls.client.protocol.InputFormatEnum;
import com.alibaba.nls.client.protocol.NlsClient;
import com.alibaba.nls.client.protocol.SampleRateEnum;
import com.alibaba.nls.client.protocol.asr.SpeechTranscriber;
import com.alibaba.nls.client.protocol.asr.SpeechTranscriberListener;
import com.alibaba.nls.client.protocol.asr.SpeechTranscriberResponse;
import com.huohuo.scenicaiguide.common.enums.ErrorCode;
import com.huohuo.scenicaiguide.common.exception.BusinessException;
import com.huohuo.scenicaiguide.config.AliyunNlsConfig;
import com.huohuo.scenicaiguide.system.service.ConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * ASR 语音识别服务（阿里云 NLS 实时识别做一句话识别）
 * <p>
 * 前端按住说话产出 16k 单声道 WAV，整段一次性送入 transcriber。
 * 与 TTS 同一 NlsClient，同一"listener 收集 + latch 等待 + finally close"模式
 */
@Service
public class AsrService {

    /**
     * 语音上限 10MB：16k 16bit 单声道 WAV 约 32KB/s，覆盖 5 分钟以内的一句话
     */
    public static final int MAX_AUDIO_BYTES = 10 * 1024 * 1024;

    private static final Logger LOGGER = LoggerFactory.getLogger(AsrService.class);

    private final ObjectProvider<NlsClient> nlsClientProvider;
    private final AliyunNlsConfig aliyunNlsConfig;
    private final ConfigService configService;

    public AsrService(ObjectProvider<NlsClient> nlsClientProvider, AliyunNlsConfig aliyunNlsConfig, ConfigService configService) {
        this.nlsClientProvider = nlsClientProvider;
        this.aliyunNlsConfig = aliyunNlsConfig;
        this.configService = configService;
    }

    /**
     * 识别一段 16k 单声道 WAV 音频
     *
     * @return 识别文本，可能为空串（没听清），调用方自行决定提示语
     */
    public String transcribe(byte[] audio) {
        validate(audio);
        String provider = configService.getString("dh-asr-provider", "aliyun");
        if ("mock".equalsIgnoreCase(provider)) {
            // mock 供无密钥演示：返回空串，前端按"没听清"提示改用文字输入
            LOGGER.info("ASR mock provider, returning empty transcript");
            return "";
        }
        if (!"aliyun".equalsIgnoreCase(provider)) {
            // 当前只有阿里云实现，配置成别的供应商应该显式失败而不是静默走错通道
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "ASR 未启用或不支持的供应商：" + provider);
        }
        NlsClient nlsClient = nlsClientProvider.getIfAvailable();
        if (nlsClient == null) {
            // 未配置阿里云密钥时 NlsClient 不装配，视同 mock 降级，不拦演示流程
            LOGGER.warn("NLS 密钥未配置，ASR 降级为空识别结果");
            return "";
        }
        CountDownLatch latch = new CountDownLatch(1);
        StringBuilder text = new StringBuilder();
        AtomicReference<String> failure = new AtomicReference<>();

        SpeechTranscriberListener listener = new SpeechTranscriberListener() {
            @Override
            public void onTranscriberStart(SpeechTranscriberResponse response) {
            }

            @Override
            public void onSentenceBegin(SpeechTranscriberResponse response) {
            }

            @Override
            public void onSentenceEnd(SpeechTranscriberResponse response) {
                // SentenceEnd 的 payload "result" 是本句最终识别文本
                if (response.getTransSentenceText() != null) {
                    text.append(response.getTransSentenceText());
                }
            }

            @Override
            public void onTranscriptionResultChange(SpeechTranscriberResponse response) {
            }

            @Override
            public void onTranscriptionComplete(SpeechTranscriberResponse response) {
                latch.countDown();
            }

            @Override
            public void onFail(SpeechTranscriberResponse response) {
                failure.set(response.getStatusText());
                latch.countDown();
            }
        };

        SpeechTranscriber transcriber = null;
        try {
            transcriber = new SpeechTranscriber(nlsClient, listener);
            transcriber.setAppKey(aliyunNlsConfig.getAppKey());
            transcriber.setFormat(InputFormatEnum.WAV);
            transcriber.setSampleRate(SampleRateEnum.SAMPLE_RATE_16K);
            transcriber.setEnableIntermediateResult(false);
            transcriber.setEnablePunctuation(true);
            transcriber.setEnableITN(true);
            transcriber.start(10000);
            transcriber.send(audio);
            transcriber.stop();
            boolean completed = latch.await(15, TimeUnit.SECONDS);
            if (!completed) {
                throw new BusinessException(ErrorCode.THIRD_PARTY_ERROR, "阿里云 ASR 识别超时");
            }
            if (failure.get() != null) {
                throw new BusinessException(ErrorCode.THIRD_PARTY_ERROR, "阿里云 ASR 识别失败：" + failure.get());
            }
            return text.toString().trim();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.THIRD_PARTY_ERROR, "阿里云 ASR 调用失败：" + e.getMessage());
        } finally {
            if (transcriber != null) {
                try {
                    transcriber.close();
                } catch (Exception ignored) {
                    // 忽略关闭失败，避免覆盖识别结果
                }
            }
        }
    }

    /**
     * 入参校验与 NLS 调用分离：校验是可以离线测试的部分
     */
    static void validate(byte[] audio) {
        if (audio == null || audio.length == 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "音频内容为空");
        }
        if (audio.length > MAX_AUDIO_BYTES) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "音频超过 10MB 上限，请缩短录音时长");
        }
        // WAV 头 magic 检查：前端约定送 WAV，格式不对会在 NLS 侧报难以解读的错
        if (audio.length < 12
            || audio[0] != 'R' || audio[1] != 'I' || audio[2] != 'F' || audio[3] != 'F'
            || audio[8] != 'W' || audio[9] != 'A' || audio[10] != 'V' || audio[11] != 'E') {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "音频格式不支持，仅接受 WAV");
        }
    }
}
