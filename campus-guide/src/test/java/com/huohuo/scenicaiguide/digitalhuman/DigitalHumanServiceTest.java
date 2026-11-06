package com.huohuo.scenicaiguide.digitalhuman;

import com.huohuo.scenicaiguide.common.exception.BusinessException;
import com.huohuo.scenicaiguide.digitalhuman.dto.request.DhProfileRequest;
import com.huohuo.scenicaiguide.digitalhuman.dto.response.DhTtsOptionResponse;
import com.huohuo.scenicaiguide.digitalhuman.entity.DhProfile;
import com.huohuo.scenicaiguide.digitalhuman.mapper.DhProfileMapper;
import com.huohuo.scenicaiguide.digitalhuman.service.DigitalHumanService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DigitalHumanServiceTest {

    @Test
    void exposesAliyunTtsOptionsFromBackendValidationSource() {
        DigitalHumanService service = new DigitalHumanService(mock(DhProfileMapper.class));

        DhTtsOptionResponse options = service.getTtsOptions();

        assertThat(options.getVoices()).extracting(DhTtsOptionResponse.OptionItem::getValue)
            .containsExactly("siyue", "xiaoyun", "xiaogang", "aixia", "aishan");
        assertThat(options.getOutputFormats()).containsExactly("wav", "mp3", "pcm");
        assertThat(options.getSampleRates()).containsExactly(8000, 16000, 24000, 48000);
        assertThat(options.getSpeechRate().getMin()).isEqualTo(-500);
        assertThat(options.getSpeechRate().getMax()).isEqualTo(500);
        assertThat(options.getVolume().getDefaultValue()).isEqualTo(50);
    }

    @Test
    void rejectsVoiceOutsideAliyunOptionList() {
        DhProfileMapper mapper = mock(DhProfileMapper.class);
        List<DhProfile> profiles = new ArrayList<>();
        DhProfile profile = profile();
        profiles.add(profile);
        when(mapper.selectList(any())).thenReturn(profiles);

        DigitalHumanService service = new DigitalHumanService(mapper);
        DhProfileRequest request = request();
        request.setVoiceCode("made_up_voice");

        assertThatThrownBy(() -> service.updateDefaultProfile(request))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("不支持的阿里云音色");
    }

    @Test
    void repairsMissingHaruActionsOnExistingProfile() {
        DhProfileMapper mapper = mock(DhProfileMapper.class);
        DhProfile profile = profile();
        profile.setAvatarUrl("/digital-human/haru/Haru.model3.json");
        when(mapper.selectList(any())).thenReturn(List.of(profile));

        new DigitalHumanService(mapper).getOrCreateDefaultProfile();

        assertThat(profile.getActionIdleUrl()).endsWith("haru_g_idle.motion3.json");
        assertThat(profile.getActionListeningUrl()).endsWith("haru_g_m15.motion3.json");
        assertThat(profile.getActionThinkingUrl()).endsWith("haru_g_m06.motion3.json");
        assertThat(profile.getActionSpeakingUrl()).endsWith("haru_g_m26.motion3.json");
        verify(mapper).updateById(profile);
    }

    private DhProfile profile() {
        DhProfile profile = new DhProfile();
        profile.setId(1L);
        profile.setScenicAreaId(1L);
        profile.setName("小桃导游");
        profile.setStatus(1);
        return profile;
    }

    private DhProfileRequest request() {
        DhProfileRequest request = new DhProfileRequest();
        request.setName("小桃导游");
        request.setVoiceCode("siyue");
        request.setOutputFormat("wav");
        request.setSampleRate(16000);
        request.setSpeechRate(0);
        request.setPitchRate(0);
        request.setVolume(50);
        request.setStatus(1);
        return request;
    }
}
