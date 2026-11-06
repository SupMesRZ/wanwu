package com.huohuo.scenicaiguide.digitalhuman;

import com.alibaba.nls.client.protocol.NlsClient;
import com.huohuo.scenicaiguide.config.AliyunNlsConfig;
import com.huohuo.scenicaiguide.digitalhuman.dto.response.TtsResponse;
import com.huohuo.scenicaiguide.digitalhuman.entity.DhProfile;
import com.huohuo.scenicaiguide.digitalhuman.entity.DhTtsCache;
import com.huohuo.scenicaiguide.digitalhuman.mapper.DhTtsCacheMapper;
import com.huohuo.scenicaiguide.digitalhuman.service.DigitalHumanService;
import com.huohuo.scenicaiguide.digitalhuman.service.TtsService;
import com.huohuo.scenicaiguide.system.service.ConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TtsServiceTest {

    @Test
    void returnsCachedAudioUsingCachedVoiceAndFormat() {
        DhTtsCacheMapper cacheMapper = mock(DhTtsCacheMapper.class);
        DigitalHumanService profileService = mock(DigitalHumanService.class);
        ConfigService configService = mock(ConfigService.class);
        DhProfile profile = profile();
        DhTtsCache cache = new DhTtsCache();
        cache.setAudioBase64("cached-audio");
        cache.setOutputFormat("mp3");
        cache.setVoiceCode("xiaoyun");
        cache.setHitCount(3);
        cache.setExpireAt(LocalDateTime.now().plusHours(1));
        when(profileService.getOrCreateDefaultProfile()).thenReturn(profile);
        when(cacheMapper.selectList(any())).thenReturn(List.of(cache));

        TtsResponse response = service(cacheMapper, profileService, configService).synthesize(1L, "你好");

        assertThat(response.isCached()).isTrue();
        assertThat(response.getAudioBase64()).isEqualTo("cached-audio");
        assertThat(response.getFormat()).isEqualTo("mp3");
        assertThat(response.getVoiceCode()).isEqualTo("xiaoyun");
        assertThat(cache.getHitCount()).isEqualTo(4);
        verify(cacheMapper).updateById(cache);
        verify(cacheMapper, never()).insert(any(DhTtsCache.class));
    }

    @Test
    void mockProviderShouldSynthesizeSilentWav() {
        DhTtsCacheMapper cacheMapper = mock(DhTtsCacheMapper.class);
        DigitalHumanService profileService = mock(DigitalHumanService.class);
        ConfigService configService = mock(ConfigService.class);
        when(profileService.getOrCreateDefaultProfile()).thenReturn(profile());
        when(cacheMapper.selectList(any())).thenReturn(List.of());
        when(configService.getString("dh-tts-provider", "aliyun")).thenReturn("mock");

        TtsResponse response = service(cacheMapper, profileService, configService, null)
            .synthesize(1L, "你好，欢迎来到景区");

        assertThat(response.isCached()).isFalse();
        assertThat(response.getFormat()).isEqualTo("wav");
        byte[] audio = Base64.getDecoder().decode(response.getAudioBase64());
        assertThat(audio).startsWith(new byte[]{'R', 'I', 'F', 'F'});
        verify(cacheMapper).upsertByHash(any(DhTtsCache.class));
    }

    @Test
    void missingNlsClientShouldDegradeToSilentWav() {
        DhTtsCacheMapper cacheMapper = mock(DhTtsCacheMapper.class);
        DigitalHumanService profileService = mock(DigitalHumanService.class);
        ConfigService configService = mock(ConfigService.class);
        when(profileService.getOrCreateDefaultProfile()).thenReturn(profile());
        when(cacheMapper.selectList(any())).thenReturn(List.of());
        when(configService.getString("dh-tts-provider", "aliyun")).thenReturn("aliyun");

        TtsResponse response = service(cacheMapper, profileService, configService, null)
            .synthesize(1L, "降级播报");

        assertThat(response.getFormat()).isEqualTo("wav");
        byte[] audio = Base64.getDecoder().decode(response.getAudioBase64());
        assertThat(audio).startsWith(new byte[]{'R', 'I', 'F', 'F'});
    }

    private TtsService service(DhTtsCacheMapper cacheMapper, DigitalHumanService profileService, ConfigService configService) {
        return service(cacheMapper, profileService, configService, mock(NlsClient.class));
    }

    private TtsService service(DhTtsCacheMapper cacheMapper, DigitalHumanService profileService,
                               ConfigService configService, NlsClient nlsClient) {
        return new TtsService(cacheMapper, profileService, providerOf(nlsClient),
            mock(AliyunNlsConfig.class), configService);
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<NlsClient> providerOf(NlsClient nlsClient) {
        ObjectProvider<NlsClient> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(nlsClient);
        return provider;
    }

    private DhProfile profile() {
        DhProfile profile = new DhProfile();
        profile.setVoiceCode("siyue");
        profile.setOutputFormat("wav");
        profile.setSampleRate(16000);
        profile.setSpeechRate(0);
        profile.setPitchRate(0);
        profile.setVolume(50);
        return profile;
    }
}
