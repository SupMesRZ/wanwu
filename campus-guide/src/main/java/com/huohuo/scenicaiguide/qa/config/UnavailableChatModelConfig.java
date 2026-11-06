package com.huohuo.scenicaiguide.qa.config;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 无 LLM 演示部署（spring.ai.model.chat=none）时注册占位 ChatModel，
 * 让 ChatClient 自动装配不缺依赖；正常部署下本配置不生效
 */
@Configuration
@ConditionalOnProperty(prefix = "spring.ai.model", name = "chat", havingValue = "none")
public class UnavailableChatModelConfig {

    @Bean
    public ChatModel unavailableChatModel() {
        return new UnavailableChatModel();
    }
}
