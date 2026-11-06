package com.huohuo.scenicaiguide.system.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 短信服务定档实现：验证码不走真实短信通道，由登录响应直接回显给操作者
 *
 * <p>用户 2026-07-29 定档：现阶段不接入真实短信服务商，管理员 2FA 与
 * 游客手机号登录的验证码一律在页面展示（P1-17）。本类只在日志记录受理，
 * 全 profile 装配，是 SmsService 的唯一实现。
 * 后续若接入真实服务商，新增实现替换本类并移除响应中的验证码回显
 */
@Slf4j
@Component
public class MockSmsService implements SmsService {

    @Override
    public void sendVerificationCode(String phone, String code) {
        // 验证码由响应回显，这里不重复打印明文，只记录受理事实
        log.info("[display SMS] verification request accepted");
    }
}
