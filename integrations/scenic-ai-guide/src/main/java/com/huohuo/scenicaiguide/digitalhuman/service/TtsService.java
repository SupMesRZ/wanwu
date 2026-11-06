package com.huohuo.scenicaiguide.digitalhuman.service;

import com.alibaba.nls.client.protocol.NlsClient;
import com.alibaba.nls.client.protocol.OutputFormatEnum;
import com.alibaba.nls.client.protocol.SampleRateEnum;
import com.alibaba.nls.client.protocol.tts.SpeechSynthesizer;
import com.alibaba.nls.client.protocol.tts.SpeechSynthesizerListener;
import com.alibaba.nls.client.protocol.tts.SpeechSynthesizerResponse;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huohuo.scenicaiguide.common.api.PageResponse;
import com.huohuo.scenicaiguide.common.enums.ErrorCode;
import com.huohuo.scenicaiguide.common.exception.BusinessException;
import com.huohuo.scenicaiguide.config.AliyunNlsConfig;
import com.huohuo.scenicaiguide.digitalhuman.dto.response.TtsCacheResponse;
import com.huohuo.scenicaiguide.digitalhuman.dto.response.TtsResponse;
import com.huohuo.scenicaiguide.digitalhuman.entity.DhProfile;
import com.huohuo.scenicaiguide.digitalhuman.entity.DhTtsCache;
import com.huohuo.scenicaiguide.digitalhuman.mapper.DhTtsCacheMapper;
import com.huohuo.scenicaiguide.digitalhuman.support.SilentWavGenerator;
import com.huohuo.scenicaiguide.system.service.ConfigService;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class TtsService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TtsService.class);

    private final DhTtsCacheMapper ttsCacheMapper;
    private final DigitalHumanService digitalHumanService;
    private final ObjectProvider<NlsClient> nlsClientProvider;
    private final AliyunNlsConfig aliyunNlsConfig;
    private final ConfigService configService;

    public TtsService(DhTtsCacheMapper ttsCacheMapper,
                      DigitalHumanService digitalHumanService,
                      ObjectProvider<NlsClient> nlsClientProvider,
                      AliyunNlsConfig aliyunNlsConfig,
                      ConfigService configService) {
        this.ttsCacheMapper = ttsCacheMapper;
        this.digitalHumanService = digitalHumanService;
        this.nlsClientProvider = nlsClientProvider;
        this.aliyunNlsConfig = aliyunNlsConfig;
        this.configService = configService;
    }

    public TtsResponse synthesize(Long scenicAreaId, String text) {
        DhProfile profile = digitalHumanService.getOrCreateDefaultProfile();
        String hash = DigestUtils.sha256Hex(text + "|" + profile.getVoiceCode() + "|" + profile.getOutputFormat()
            + "|" + profile.getSampleRate() + "|" + profile.getSpeechRate() + "|" + profile.getPitchRate()
            + "|" + profile.getVolume());

        DhTtsCache cached = ttsCacheMapper.selectList(new QueryWrapper<DhTtsCache>()
                .eq("text_hash", hash)
                .gt("expire_at", LocalDateTime.now())
                .last("LIMIT 1"))
            .stream()
            .findFirst()
            .orElse(null);
        if (cached != null) {
            cached.setHitCount((cached.getHitCount() == null ? 0 : cached.getHitCount()) + 1);
            cached.setUpdatedAt(LocalDateTime.now());
            ttsCacheMapper.updateById(cached);
            return toResponse(cached, true);
        }

        boolean silent = useSilentFallback();
        DhTtsCache entity = new DhTtsCache();
        entity.setScenicAreaId(scenicAreaId);
        entity.setTextHash(hash);
        entity.setTextPreview(text.substring(0, Math.min(120, text.length())));
        entity.setVoiceCode(profile.getVoiceCode());
        // 静音降级产物固定是 WAV，格式字段必须如实反映，否则前端按 mp3 解码会播放失败
        entity.setOutputFormat(silent ? "wav" : profile.getOutputFormat());
        entity.setSampleRate(profile.getSampleRate());
        entity.setAudioUrl(null);
        entity.setAudioBase64(silent
            ? Base64.getEncoder().encodeToString(SilentWavGenerator.generate(text))
            : synthesizeAudio(text, profile));
        entity.setHitCount(0);
        entity.setExpireAt(LocalDateTime.now().plusHours(cacheTtlHours()));
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setDeleted(0);
        // upsert 而不是先查后插：过期或逻辑删除的同 hash 历史行会被原地复活，
        // 否则唯一键 uk_dh_tts_hash 会让重新合成直接报主键冲突
        ttsCacheMapper.upsertByHash(entity);
        return toResponse(entity, false);
    }

    public PageResponse<TtsCacheResponse> listCaches(String keyword, String voiceCode, long page, long pageSize) {
        QueryWrapper<DhTtsCache> wrapper = new QueryWrapper<DhTtsCache>()
            .orderByDesc("updated_at");
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like("text_preview", keyword.trim());
        }
        if (voiceCode != null && !voiceCode.isBlank()) {
            wrapper.eq("voice_code", voiceCode.trim());
        }
        Page<DhTtsCache> result = ttsCacheMapper.selectPage(new Page<>(page, pageSize), wrapper);
        List<TtsCacheResponse> records = result.getRecords().stream()
            .map(TtsCacheResponse::from)
            .toList();
        return PageResponse.of(records, result.getCurrent(), result.getSize(), result.getTotal());
    }

    public TtsResponse getCachedAudio(Long id) {
        DhTtsCache cache = ttsCacheMapper.selectById(id);
        if (cache == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "TTS 缓存不存在");
        }
        return toResponse(cache, true);
    }

    public void deleteCache(Long id) {
        DhTtsCache cache = ttsCacheMapper.selectById(id);
        if (cache == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "TTS 缓存不存在");
        }
        ttsCacheMapper.deleteById(id);
    }

    /**
     * 是否走静音降级：provider 显式配置 mock，或未配置 NLS 密钥导致客户端未装配
     */
    private boolean useSilentFallback() {
        String provider = configService.getString("dh-tts-provider", "aliyun");
        if ("mock".equalsIgnoreCase(provider)) {
            return true;
        }
        if (!"aliyun".equalsIgnoreCase(provider)) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "TTS 不支持的供应商：" + provider);
        }
        boolean missing = nlsClientProvider.getIfAvailable() == null;
        if (missing) {
            LOGGER.warn("NLS 密钥未配置，TTS 降级为静音 WAV");
        }
        return missing;
    }

    private String synthesizeAudio(String text, DhProfile profile) {
        NlsClient nlsClient = nlsClientProvider.getObject();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> failure = new AtomicReference<>();
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        SpeechSynthesizerListener listener = new SpeechSynthesizerListener() {
            @Override
            public void onMessage(ByteBuffer message) {
                byte[] bytes = new byte[message.remaining()];
                message.get(bytes);
                output.write(bytes, 0, bytes.length);
            }

            @Override
            public void onComplete(SpeechSynthesizerResponse response) {
                latch.countDown();
            }

            @Override
            public void onFail(SpeechSynthesizerResponse response) {
                failure.set(response.getStatusText());
                latch.countDown();
            }
        };

        SpeechSynthesizer synthesizer = null;
        try {
            synthesizer = new SpeechSynthesizer(nlsClient, listener);
            synthesizer.setAppKey(aliyunNlsConfig.getAppKey());
            synthesizer.setVoice(profile.getVoiceCode());
            synthesizer.setFormat(resolveFormat(profile.getOutputFormat()));
            synthesizer.setSampleRate(resolveSampleRate(profile.getSampleRate()));
            synthesizer.setVolume(profile.getVolume());
            synthesizer.setSpeechRate(profile.getSpeechRate());
            synthesizer.setPitchRate(profile.getPitchRate());
            synthesizer.setText(text);
            synthesizer.start();
            synthesizer.waitForComplete(60000);
            boolean completed = latch.await(10, TimeUnit.SECONDS);
            if (!completed) {
                throw new BusinessException(ErrorCode.THIRD_PARTY_ERROR, "阿里云 TTS 合成超时");
            }
            if (failure.get() != null) {
                throw new BusinessException(ErrorCode.THIRD_PARTY_ERROR, "阿里云 TTS 合成失败：" + failure.get());
            }
            byte[] bytes = output.toByteArray();
            if (bytes.length == 0) {
                throw new BusinessException(ErrorCode.THIRD_PARTY_ERROR, "阿里云 TTS 未返回音频");
            }
            return Base64.getEncoder().encodeToString(bytes);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.THIRD_PARTY_ERROR, "阿里云 TTS 调用失败：" + e.getMessage());
        } finally {
            if (synthesizer != null) {
                try {
                    synthesizer.close();
                } catch (Exception ignored) {
                    // 忽略关闭失败，避免覆盖合成结果
                }
            }
        }
    }

    private OutputFormatEnum resolveFormat(String format) {
        return switch (format == null ? "wav" : format.toLowerCase()) {
            case "mp3" -> OutputFormatEnum.MP3;
            case "pcm" -> OutputFormatEnum.PCM;
            default -> OutputFormatEnum.WAV;
        };
    }

    private SampleRateEnum resolveSampleRate(Integer sampleRate) {
        return switch (sampleRate == null ? 16000 : sampleRate) {
            case 8000 -> SampleRateEnum.SAMPLE_RATE_8K;
            case 24000 -> SampleRateEnum.SAMPLE_RATE_24K;
            case 48000 -> SampleRateEnum.SAMPLE_RATE_48K;
            default -> SampleRateEnum.SAMPLE_RATE_16K;
        };
    }

    private TtsResponse toResponse(DhTtsCache cache, boolean cached) {
        TtsResponse response = new TtsResponse();
        response.setAudioUrl(cache.getAudioUrl());
        response.setAudioBase64(cache.getAudioBase64());
        response.setFormat(cache.getOutputFormat());
        response.setVoiceCode(cache.getVoiceCode());
        response.setCached(cached);
        return response;
    }

    private long cacheTtlHours() {
        return Math.clamp(configService.getLong("dh-tts-cache-ttl-hours", 72), 1, 24L * 365);
    }
}
