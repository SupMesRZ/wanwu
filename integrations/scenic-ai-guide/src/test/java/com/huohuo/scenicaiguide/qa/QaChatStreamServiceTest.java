package com.huohuo.scenicaiguide.qa;

import com.huohuo.scenicaiguide.qa.config.UnavailableChatModel;
import com.huohuo.scenicaiguide.qa.service.QaChatStreamService;
import com.huohuo.scenicaiguide.qa.service.QaGuideAnswerService;
import com.huohuo.scenicaiguide.qa.tool.ScenicGuideToolFactory;
import com.huohuo.scenicaiguide.system.service.ConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QaChatStreamServiceTest {

    @Test
    void greetingShouldUseConversationalModel() {
        QaGuideAnswerService answerService = mock(QaGuideAnswerService.class);
        when(answerService.answer(1L, "你好", null)).thenReturn(
            new QaGuideAnswerService.AnswerResult("资料不足", "SAFE_FALLBACK", "NO_EVIDENCE"));
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        when(builder.build()).thenReturn(chatClient);
        ScenicGuideToolFactory toolFactory = mock(ScenicGuideToolFactory.class);
        ScenicGuideToolFactory.ScenicGuideTools tools = mock(ScenicGuideToolFactory.ScenicGuideTools.class);
        when(toolFactory.create(1L, null)).thenReturn(tools);
        when(chatClient.prompt().system(anyString()).user("你好").tools(any()).stream().content())
            .thenReturn(Flux.just("内部分析", "【最终", "回答】你好，", "我是小桃"));

        QaChatStreamService service = new QaChatStreamService(
            answerService, toolFactory, providerOf(builder), providerOf(mock(ChatModel.class)), MessageWindowChatMemory.builder().build(), configService());
        QaChatStreamService.StreamAnswer result = service.stream(1L, "你好", null, "导游小桃");

        assertThat(result.content().collectList().block()).containsExactly("你好，", "我是小桃");
        assertThat(result.evidence()).isEqualTo("AGENT_TOOLS");
        verify(toolFactory).create(1L, null);
    }

    @Test
    void numericInputShouldReturnClarificationWithoutCallingModel() {
        QaGuideAnswerService answerService = mock(QaGuideAnswerService.class);
        when(answerService.answer(1L, "111", null)).thenReturn(
            new QaGuideAnswerService.AnswerResult("资料不足", "SAFE_FALLBACK", "NO_EVIDENCE"));
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        when(builder.build()).thenReturn(mock(ChatClient.class));

        QaChatStreamService service = new QaChatStreamService(
            answerService, mock(ScenicGuideToolFactory.class), providerOf(builder), providerOf(mock(ChatModel.class)),
            MessageWindowChatMemory.builder().build(), configService());
        QaChatStreamService.StreamAnswer result = service.stream(1L, "111", null, "导游小桃");

        assertThat(result.content().blockFirst()).contains("没看懂").contains("111");
        assertThat(result.evidence()).isEqualTo("CLARIFICATION");
        verify(builder).build();
    }

    @Test
    void unknownScenicFactShouldUseAgentWithScenicTools() {
        QaGuideAnswerService answerService = mock(QaGuideAnswerService.class);
        String fallback = "当前景区资料里没有确认信息";
        when(answerService.answer(1L, "古塔几点开放", null)).thenReturn(
            new QaGuideAnswerService.AnswerResult(fallback, "SAFE_FALLBACK", "NO_EVIDENCE"));
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        when(builder.build()).thenReturn(chatClient);
        ScenicGuideToolFactory toolFactory = mock(ScenicGuideToolFactory.class);
        ScenicGuideToolFactory.ScenicGuideTools tools = mock(ScenicGuideToolFactory.ScenicGuideTools.class);
        when(toolFactory.create(1L, null)).thenReturn(tools);
        when(chatClient.prompt().system(anyString()).user("古塔几点开放").tools(any()).stream().content())
            .thenReturn(Flux.just("我先查询景区资料。", "目前没有确认的开放时间。"));

        QaChatStreamService service = new QaChatStreamService(
            answerService, toolFactory, providerOf(builder), providerOf(mock(ChatModel.class)), MessageWindowChatMemory.builder().build(), configService());
        QaChatStreamService.StreamAnswer result = service.stream(1L, "古塔几点开放", null, "导游小桃");

        assertThat(result.content().collectList().block())
            .containsExactly("我先查询景区资料。目前没有确认的开放时间。");
        assertThat(result.evidence()).isEqualTo("AGENT_TOOLS");
        verify(toolFactory).create(1L, null);
    }

    @Test
    void pointToPointQuestionUsesAgentToolResultWithoutModelHallucination() {
        QaGuideAnswerService answerService = mock(QaGuideAnswerService.class);
        when(answerService.answer(1L, "A4到食堂怎么走", null)).thenReturn(
            new QaGuideAnswerService.AnswerResult("请以工具结果为准", "SAFE_FALLBACK", "NO_EVIDENCE"));
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        when(builder.build()).thenReturn(chatClient);
        ScenicGuideToolFactory toolFactory = mock(ScenicGuideToolFactory.class);
        ScenicGuideToolFactory.ScenicGuideTools tools = mock(ScenicGuideToolFactory.ScenicGuideTools.class);
        when(toolFactory.create(1L, null)).thenReturn(tools);
        ScenicGuideToolFactory.PathVisualization path = new ScenicGuideToolFactory.PathVisualization(
            "A4", "食堂", 274, List.of(), List.of("R1"));
        when(tools.pathVisualization()).thenReturn(path);
        when(chatClient.prompt().system(anyString()).user("A4到食堂怎么走").tools(any()).stream().content())
            .thenReturn(Flux.just("【最终回答】从A4到食堂约274米，路线已在地图高亮。"));

        QaChatStreamService service = new QaChatStreamService(
            answerService, toolFactory, providerOf(builder), providerOf(mock(ChatModel.class)), MessageWindowChatMemory.builder().build(), configService());
        QaChatStreamService.StreamAnswer result = service.stream(1L, "A4到食堂怎么走", null, "导游小桃");

        assertThat(result.content().blockFirst())
            .contains("274米")
            .contains("地图高亮");
        assertThat(result.evidence()).isEqualTo("AGENT_TOOLS");
        assertThat(result.pathVisualization()).isSameAs(path);
        verify(toolFactory).create(1L, null);
    }

    @Test
    void routeQuestionUsesStandardAgentToolLoop() {
        QaGuideAnswerService answerService = mock(QaGuideAnswerService.class);
        when(answerService.answer(1L, "换一条，想去食堂", null)).thenReturn(
            new QaGuideAnswerService.AnswerResult("资料不足", "SAFE_FALLBACK", "NO_EVIDENCE"));
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        when(builder.build()).thenReturn(chatClient);
        ScenicGuideToolFactory toolFactory = mock(ScenicGuideToolFactory.class);
        ScenicGuideToolFactory.ScenicGuideTools tools = mock(ScenicGuideToolFactory.ScenicGuideTools.class);
        ScenicGuideToolFactory.AgentContext context = new ScenicGuideToolFactory.AgentContext(
            7L, 1L, null, null, 8L, true);
        when(toolFactory.create(eq(context), any())).thenReturn(tools);
        when(chatClient.prompt().system(anyString()).user("换一条，想去食堂").tools(any())
            .advisors(anyAdvisorSpecConsumer()).stream().content())
            .thenReturn(Flux.just("【最终回答】已生成换线路线，全程620米，预计52分钟。"));

        QaChatStreamService service = new QaChatStreamService(
            answerService, toolFactory, providerOf(builder), providerOf(mock(ChatModel.class)), MessageWindowChatMemory.builder().build(), configService());
        QaChatStreamService.StreamAnswer result = service.stream(context, "换一条，想去食堂", "导游小桃");

        assertThat(result.content().blockFirst()).contains("620米", "52分钟");
        verify(chatClient.prompt()).system(argThat((String prompt) ->
            prompt.contains("inspectScenicMap")
                && prompt.startsWith("/no_think")
                && prompt.contains("orderedSegmentIds")
                && prompt.contains("createTourRoute")
                && prompt.contains("success=false")
                && prompt.contains("再次调用")
                && !prompt.contains("recommendTourRoute")));
    }

    @Test
    void emptyThinkDirectiveKeepsModelDialectOutOfSystemPrompt() {
        QaGuideAnswerService answerService = mock(QaGuideAnswerService.class);
        when(answerService.answer(1L, "古塔在哪", null)).thenReturn(
            new QaGuideAnswerService.AnswerResult("资料不足", "SAFE_FALLBACK", "NO_EVIDENCE"));
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        when(builder.build()).thenReturn(chatClient);
        ScenicGuideToolFactory toolFactory = mock(ScenicGuideToolFactory.class);
        when(toolFactory.create(1L, null)).thenReturn(mock(ScenicGuideToolFactory.ScenicGuideTools.class));
        when(chatClient.prompt().system(anyString()).user("古塔在哪").tools(any()).stream().content())
            .thenReturn(Flux.just("【最终回答】古塔在东门。"));
        // 换用不识别 /no_think 的模型时，配置置空即彻底移除该指令
        ConfigService configService = mock(ConfigService.class);
        when(configService.getString(eq("qa-model-think-directive"), anyString())).thenReturn("");

        QaChatStreamService service = new QaChatStreamService(
            answerService, toolFactory, providerOf(builder), providerOf(mock(ChatModel.class)), MessageWindowChatMemory.builder().build(), configService);
        service.stream(1L, "古塔在哪", null, "导游小桃").content().blockFirst();

        verify(chatClient.prompt()).system(argThat((String prompt) ->
            !prompt.contains("/no_think") && prompt.startsWith("导游小桃")));
    }

    @Test
    void llmAbsentShouldStreamVerifiedContentDirectly() {
        QaGuideAnswerService answerService = mock(QaGuideAnswerService.class);
        when(answerService.answer(1L, "古塔有什么故事", null)).thenReturn(
            new QaGuideAnswerService.AnswerResult("古塔建于明代，相传为镇水患而立。", "KNOWLEDGE", "KB"));
        ChatClient.Builder builder = mock(ChatClient.Builder.class);

        QaChatStreamService service = new QaChatStreamService(
            answerService, mock(ScenicGuideToolFactory.class), providerOf(builder),
            providerOf(new UnavailableChatModel()), MessageWindowChatMemory.builder().build(), configService());
        QaChatStreamService.StreamAnswer result = service.stream(1L, "古塔有什么故事", null, "导游小桃");

        assertThat(result.content().collectList().block())
            .containsExactly("古塔建于明代，相传为镇水患而立。");
        assertThat(result.evidence()).isEqualTo("KB|LLM_OFF");
        verify(builder, never()).build();
    }

    @Test
    void llmAbsentWithNoEvidenceShouldStreamSafeFallback() {
        QaGuideAnswerService answerService = mock(QaGuideAnswerService.class);
        when(answerService.answer(1L, "随便问问", null)).thenReturn(
            new QaGuideAnswerService.AnswerResult("当前景区资料里没有确认信息", "SAFE_FALLBACK", "NO_EVIDENCE"));

        QaChatStreamService service = new QaChatStreamService(
            answerService, mock(ScenicGuideToolFactory.class), providerOf((ChatClient.Builder) null),
            providerOf((ChatModel) null), MessageWindowChatMemory.builder().build(), configService());
        QaChatStreamService.StreamAnswer result = service.stream(1L, "随便问问", null, "导游小桃");

        assertThat(result.content().blockFirst()).isEqualTo("当前景区资料里没有确认信息");
        assertThat(result.evidence()).isEqualTo("LLM_OFF");
    }

    /**
     * 默认配置下保持 /no_think，与当前定档的 Qwen 模型一致
     */
    private static ConfigService configService() {
        ConfigService configService = mock(ConfigService.class);
        when(configService.getString(eq("qa-model-think-directive"), anyString()))
            .thenAnswer(invocation -> invocation.getArgument(1));
        return configService;
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> providerOf(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }

    private static Consumer<ChatClient.AdvisorSpec> anyAdvisorSpecConsumer() {
        return any();
    }
}
