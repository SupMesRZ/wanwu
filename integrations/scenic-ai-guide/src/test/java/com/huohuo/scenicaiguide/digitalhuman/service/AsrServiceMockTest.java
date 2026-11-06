package com.huohuo.scenicaiguide.digitalhuman.service;

import com.alibaba.nls.client.protocol.NlsClient;
import com.huohuo.scenicaiguide.config.AliyunNlsConfig;
import com.huohuo.scenicaiguide.system.service.ConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * ASR mock / 降级路径测试（真实阿里云调用由外部标签测试覆盖）
 */
class AsrServiceMockTest {

    private static final byte[] VALID_WAV = validWav();

    @Test
    void mockProviderShouldReturnEmptyTranscript() {
        ConfigService configService = mock(ConfigService.class);
        when(configService.getString("dh-asr-provider", "aliyun")).thenReturn("mock");

        String text = new AsrService(providerOf(mock(NlsClient.class)), mock(AliyunNlsConfig.class), configService)
            .transcribe(VALID_WAV);

        assertThat(text).isEmpty();
    }

    @Test
    void missingNlsClientShouldDegradeToEmptyTranscript() {
        ConfigService configService = mock(ConfigService.class);
        when(configService.getString("dh-asr-provider", "aliyun")).thenReturn("aliyun");

        String text = new AsrService(providerOf(null), mock(AliyunNlsConfig.class), configService)
            .transcribe(VALID_WAV);

        assertThat(text).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<NlsClient> providerOf(NlsClient nlsClient) {
        ObjectProvider<NlsClient> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(nlsClient);
        return provider;
    }

    private static byte[] validWav() {
        byte[] audio = new byte[64];
        System.arraycopy(new byte[]{'R', 'I', 'F', 'F'}, 0, audio, 0, 4);
        System.arraycopy(new byte[]{'W', 'A', 'V', 'E'}, 0, audio, 8, 4);
        return audio;
    }
}
