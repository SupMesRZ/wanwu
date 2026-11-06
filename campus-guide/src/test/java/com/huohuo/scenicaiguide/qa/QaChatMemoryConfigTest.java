package com.huohuo.scenicaiguide.qa;

import com.huohuo.scenicaiguide.qa.config.QaChatMemoryConfig;
import com.huohuo.scenicaiguide.qa.config.QaConfigProperties;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class QaChatMemoryConfigTest {

    @Test
    void keepsConfiguredRoundsAndIsolatesGuideSessions() {
        QaConfigProperties properties = new QaConfigProperties();
        properties.setConfig(Map.of("qa-context-max-rounds", "2"));
        ChatMemory memory = new QaChatMemoryConfig(properties).chatMemory();

        memory.add("guide-session:1", new UserMessage("第一轮问题"));
        memory.add("guide-session:1", new AssistantMessage("第一轮回答"));
        memory.add("guide-session:1", new UserMessage("第二轮问题"));
        memory.add("guide-session:1", new AssistantMessage("第二轮回答"));
        memory.add("guide-session:1", new UserMessage("第三轮问题"));
        memory.add("guide-session:1", new AssistantMessage("第三轮回答"));
        memory.add("guide-session:2", new UserMessage("另一个会话"));

        assertThat(memory.get("guide-session:1"))
            .extracting(message -> message.getText())
            .containsExactly("第二轮问题", "第二轮回答", "第三轮问题", "第三轮回答");
        assertThat(memory.get("guide-session:2"))
            .extracting(message -> message.getText())
            .containsExactly("另一个会话");
    }
}
