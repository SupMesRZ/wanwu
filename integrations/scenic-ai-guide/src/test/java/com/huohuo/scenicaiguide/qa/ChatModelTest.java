package com.huohuo.scenicaiguide.qa;

import com.huohuo.scenicaiguide.common.TestTags;
import org.junit.jupiter.api.Tag;

import org.junit.jupiter.api.*;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Spring AI ChatModel 测试
 * <p>
 * 单轮对话：验证 ChatModel 连通性
 * 多轮对话：预置三轮，验证上下文连贯性
 */
@Tag(TestTags.EXTERNAL)
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ChatModelTest {

    @Autowired
    private ChatModel chatModel;

    // 多轮对话历史，用 Message 接口统一存储 System/User/Assistant
    private final List<Message> history = new ArrayList<>();

    @Test
    @Order(1)
    @DisplayName("单轮对话：验证 ChatModel 连通性")
    void testSingleRound() {
        Prompt prompt = new Prompt(List.of(
                new SystemMessage("你是景区AI导览员，热情专业地回答游客问题"),
                new UserMessage("断桥有什么历史故事？")
        ));

        ChatResponse response = chatModel.call(prompt);

        assertNotNull(response);
        assertNotNull(response.getResult());
        assertNotNull(response.getResult().getOutput());

        String text = response.getResult().getOutput().getText();
        assertNotNull(text);
        assertFalse(text.isBlank());

        System.out.println("\n=== 单轮对话测试结果 ===");
        System.out.println("AI回复: " + text);
    }

    @Test
    @Order(2)
    @DisplayName("多轮对话：预置三轮上下文验证")
    void testMultiRound() {
        // 系统指令
        history.add(new SystemMessage("你是景区AI导览员，热情专业地回答游客问题"));

        // 第一轮：基础问答
        history.add(new UserMessage("断桥有什么历史故事？"));
        String round1 = callAndAssert(history);
        history.add(new AssistantMessage(round1));
        System.out.println("\n[Round 1] 游客: 断桥有什么历史故事？");
        System.out.println("[Round 1] AI: " + round1.substring(0, Math.min(80, round1.length())) + "...");

        // 第二轮：追问（依赖第一轮上下文）
        history.add(new UserMessage("我上个问题问你的是哪个景点？我忘了"));
        String round2 = callAndAssert(history);
        history.add(new AssistantMessage(round2));
        System.out.println("\n[Round 2] 游客: 我上个问题问你的是哪个景点？我忘了");
        System.out.println("[Round 2] AI: " + round2.substring(0, Math.min(120, round2.length())) + "...");

        // 第三轮：路线推荐（验证上下文连贯性）
        history.add(new UserMessage("输出我上面问过的俩问题"));
        String round3 = callAndAssert(history);
        System.out.println("\n[Round 3] 游客: 推荐一条去断桥的路线");
        System.out.println("[Round 3] AI: " + round3);
    }

    @Test
    @Order(3)
    @DisplayName("流式输出：在已有上下文中逐 token 接收")
    void testStreamWithContext() {
        // 重建前三轮对话历史（简化版，不需要真实调用，只需构造上下文）
        List<Message> ctx = new ArrayList<>();
        ctx.add(new SystemMessage("你是景区AI导览员，热情专业地回答游客问题"));
        ctx.add(new UserMessage("断桥有什么历史故事？"));
        ctx.add(new AssistantMessage("断桥是西湖十景之一，位于白堤东端，是白娘子与许仙传说中相会的地方"));
        ctx.add(new UserMessage("我上个问题问你的是哪个景点？我忘了"));
        ctx.add(new AssistantMessage("您上个问题问的是断桥"));
        ctx.add(new UserMessage("输出我上面问过的俩问题"));
        ctx.add(new AssistantMessage("您问过：1. 断桥有什么历史故事？ 2. 我上个问题问你的是哪个景点？我忘了"));

        // 第四轮：流式输出，验证在上下文中能正常逐 token 返回
        ctx.add(new UserMessage("断桥和雷峰塔有什么关系？"));

        // 使用 StreamingChatModel#stream(Message...) 便利方法
        // 内部已用 Optional.ofNullable(response.getResult())... 做空安全处理，直接得到 Flux<String>
        System.out.println("\n=== 流式输出开始 ===");

        List<String> tokens = chatModel.stream(ctx.toArray(new Message[0]))
                .doOnNext(System.out::print)
                .collectList()
                .block(Duration.ofSeconds(60));

        System.out.println("\n=== 流式输出结束 ===");

        assertNotNull(tokens);
        assertFalse(tokens.isEmpty(), "流式响应应至少返回一个 token");

        String fullText = String.join("", tokens);
        assertFalse(fullText.isBlank(), "流式输出拼接后的完整文本不应为空");
        System.out.println("\n[流式完整文本长度: " + fullText.length() + " 字]");
    }

    /**
     * 调用 ChatModel 并断言响应非空
     */
    private String callAndAssert(List<Message> messages) {
        Prompt prompt = new Prompt(new ArrayList<>(messages));
        ChatResponse response = chatModel.call(prompt);
        assertNotNull(response.getResult());
        assertNotNull(response.getResult().getOutput());
        String text = response.getResult().getOutput().getText();
        assertNotNull(text);
        assertFalse(text.isBlank());
        return text;
    }
}
