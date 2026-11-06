package com.huohuo.scenicaiguide.route;

import com.huohuo.scenicaiguide.route.service.RoutePreferenceProvider;
import com.huohuo.scenicaiguide.scenic.service.ScenicTagService;
import com.huohuo.scenicaiguide.system.service.ConfigService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 路线偏好提供者测试
 * <p>
 * 钉住偏好配置的解析口径：空白配置无偏好、分隔符容忍中文标点、
 * 词表故障降级为空——偏好是软提示，缺了不该影响路线功能
 */
class RoutePreferenceProviderTest {

    private final ConfigService configService = mock(ConfigService.class);
    private final ScenicTagService tagService = mock(ScenicTagService.class);
    private final RoutePreferenceProvider provider = new RoutePreferenceProvider(configService, tagService);

    @Test
    void blankConfigMeansNoPreference() {
        when(configService.getString(eq("route-ai-prefer-tags"), anyString())).thenReturn("");

        assertThat(provider.preferredTagNames()).isEmpty();
    }

    @Test
    void configReadFailureMeansNoPreference() {
        when(configService.getString(anyString(), anyString()))
            .thenThrow(new IllegalStateException("Redis down"));

        assertThat(provider.preferredTagNames()).isEmpty();
    }

    @Test
    void codesAreSplitAndResolvedToNames() {
        when(configService.getString(eq("route-ai-prefer-tags"), anyString()))
            .thenReturn("family, history、nature walk");
        when(tagService.resolveNames(anyList()))
            .thenAnswer(invocation -> List.of("亲子休闲", "历史探秘"));

        assertThat(provider.preferredTagNames()).containsExactly("亲子休闲", "历史探秘");
        // 逗号、中文顿号、空白都要能分隔
        verify(tagService).resolveNames(List.of("family", "history", "nature", "walk"));
    }

    @Test
    void tagServiceFailureMeansNoPreference() {
        when(configService.getString(eq("route-ai-prefer-tags"), anyString())).thenReturn("family");
        when(tagService.resolveNames(anyList())).thenThrow(new IllegalStateException("db down"));

        assertThat(provider.preferredTagNames()).isEmpty();
    }
}
