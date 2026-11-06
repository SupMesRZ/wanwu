package com.huohuo.scenicaiguide.qa.config;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

/**
 * 无 LLM 部署的占位 ChatModel
 *
 * <p>spring.ai.model.chat=none 时 OpenAI 自动装配退出，但 Spring AI 的
 * ChatClient 自动装配仍强制要求容器里存在一个 ChatModel，否则上下文启动失败。
 * 本类仅充当该占位，任何真实调用都会显式报错；
 * QaChatStreamService 通过 instanceof 识别并走检索证据直出，不会真正调到这里
 */
public class UnavailableChatModel implements ChatModel {

    private static final String MESSAGE = "LLM 未配置（spring.ai.model.chat=none），该部署不支持生成式问答";

    @Override
    public ChatResponse call(Prompt prompt) {
        throw new UnsupportedOperationException(MESSAGE);
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return Flux.error(new UnsupportedOperationException(MESSAGE));
    }
}
