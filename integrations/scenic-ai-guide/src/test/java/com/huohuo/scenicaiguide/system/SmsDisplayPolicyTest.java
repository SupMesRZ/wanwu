package com.huohuo.scenicaiguide.system;

import com.huohuo.scenicaiguide.system.service.MockSmsService;
import com.huohuo.scenicaiguide.system.service.SmsService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * 短信"验证码前端展示"定档守卫（P1-17，用户 2026-07-29 定档）
 *
 * <p>现阶段不接真实短信服务商，MockSmsService 是唯一实现且全 profile 装配，
 * 验证码随登录响应回显。守卫两件事：实现不被重新加回 profile 限制
 * （否则非 dev 环境容器缺 SmsService 起不来），受理逻辑不产生副作用
 */
class SmsDisplayPolicyTest {

    @Test
    void soleImplementationIsAssembledInEveryProfile() {
        Profile profile = MockSmsService.class.getAnnotation(Profile.class);

        assertThat(profile).as("定档实现必须全 profile 装配，不得带 profile 限制").isNull();
    }

    @Test
    void implementationAcceptsWithoutSideEffect() {
        SmsService service = new MockSmsService();

        assertThatNoException()
            .isThrownBy(() -> service.sendVerificationCode("13800138000", "123456"));
    }
}
