package com.huohuo.scenicaiguide.qa.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QaChatMemoryConfig {

    private static final int DEFAULT_MAX_ROUNDS = 12;

    private final QaConfigProperties properties;

    public QaChatMemoryConfig(@Qualifier("qaConfigProperties") QaConfigProperties properties) {
        this.properties = properties;
    }

    @Bean
    public ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder()
            .maxMessages(maxRounds() * 2)
            .build();
    }

    private int maxRounds() {
        String configured = properties.get("qa-context-max-rounds");
        if (configured == null || configured.isBlank()) {
            return DEFAULT_MAX_ROUNDS;
        }
        try {
            return Math.max(1, Integer.parseInt(configured.trim()));
        } catch (NumberFormatException ignored) {
            return DEFAULT_MAX_ROUNDS;
        }
    }
}
