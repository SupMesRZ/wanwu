package com.huohuo.scenicaiguide.qa.service;

import com.huohuo.scenicaiguide.qa.config.UnavailableChatModel;
import com.huohuo.scenicaiguide.qa.tool.ScenicGuideToolFactory;
import com.huohuo.scenicaiguide.system.service.ConfigService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.regex.Pattern;

@Service
public class QaChatStreamService {

    private static final Logger LOGGER = LoggerFactory.getLogger(QaChatStreamService.class);
    private static final Pattern LOW_INFORMATION_PATTERN = Pattern.compile("^[\\d\\p{P}\\p{S}\\s]{1,16}$");
    private static final String FINAL_ANSWER_MARKER = "【最终回答】";

    /**
     * 关闭模型思考模式的指令，属模型方言而非业务逻辑
     *
     * <p>默认 {@code /no_think} 对应当前定档的 Qwen 系列；换用不支持该指令的模型时
     * 应在配置中心置空，否则指令会作为无意义文本混入系统提示词
     */
    private static final String THINK_DIRECTIVE_KEY = "qa-model-think-directive";
    private static final String DEFAULT_THINK_DIRECTIVE = "/no_think";

    private final QaGuideAnswerService guideAnswerService;
    private final ScenicGuideToolFactory toolFactory;
    private final ChatClient chatClient;
    private final MessageChatMemoryAdvisor memoryAdvisor;
    private final ConfigService configService;

    /**
     * ChatClient 与 ChatModel 经 ObjectProvider 注入：无 LLM 密钥的演示部署会把
     * spring.ai.model.chat 设为 none，自动装配不产出真实模型（只注册
     * {@link UnavailableChatModel} 占位），此时问答降级为检索证据直出，
     * 不再具备工具调用与多轮记忆能力
     */
    public QaChatStreamService(QaGuideAnswerService guideAnswerService,
                               ScenicGuideToolFactory toolFactory,
                               ObjectProvider<ChatClient.Builder> chatClientBuilderProvider,
                               ObjectProvider<ChatModel> chatModelProvider,
                               ChatMemory chatMemory,
                               ConfigService configService) {
        this.guideAnswerService = guideAnswerService;
        this.toolFactory = toolFactory;
        ChatClient.Builder builder = chatClientBuilderProvider.getIfAvailable();
        ChatModel chatModel = chatModelProvider.getIfAvailable();
        boolean llmAvailable = builder != null && chatModel != null && !(chatModel instanceof UnavailableChatModel);
        this.chatClient = llmAvailable ? builder.build() : null;
        this.memoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build();
        this.configService = configService;
        if (this.chatClient == null) {
            LOGGER.warn("ChatModel 未装配（spring.ai.model.chat=none），问答降级为检索证据直出");
        }
    }

    public StreamAnswer stream(Long scenicAreaId,
                               String question,
                               String currentSpotName,
                               String personaPrompt) {
        if (isLowInformation(question)) {
            return new StreamAnswer(Flux.just(lowInformationReply(question)), "CLARIFICATION");
        }
        ScenicGuideToolFactory.AgentContext context =
            ScenicGuideToolFactory.AgentContext.publicContext(scenicAreaId, currentSpotName);
        return stream(context, question, personaPrompt, toolFactory.create(scenicAreaId, currentSpotName));
    }

    public StreamAnswer stream(ScenicGuideToolFactory.AgentContext context,
                               String question,
                               String personaPrompt) {
        return stream(context, question, personaPrompt, ignored -> { });
    }

    public StreamAnswer stream(ScenicGuideToolFactory.AgentContext context,
                               String question,
                               String personaPrompt,
                               Consumer<String> progressListener) {
        if (isLowInformation(question)) {
            return new StreamAnswer(Flux.just(lowInformationReply(question)), "CLARIFICATION");
        }
        return stream(context, question, personaPrompt, toolFactory.create(context, progressListener));
    }

    private StreamAnswer stream(ScenicGuideToolFactory.AgentContext context,
                                String question,
                                String personaPrompt,
                                ScenicGuideToolFactory.ScenicGuideTools tools) {
        QaGuideAnswerService.AnswerResult source = guideAnswerService.answer(
            context.scenicAreaId(), question, context.currentSpotName());
        if (chatClient == null) {
            // LLM 缺席降级：预检检索到的已确认内容直接作答，AI 路线规划等工具能力不可用
            String evidence = "SAFE_FALLBACK".equals(source.sourceType())
                ? "LLM_OFF"
                : source.evidence() + "|LLM_OFF";
            return new StreamAnswer(Flux.just(source.answer()), evidence);
        }
        String verifiedContext = "SAFE_FALLBACK".equals(source.sourceType())
            ? "预检没有找到已确认资料，请按问题需要调用工具，不得把预检兜底当作景区事实"
            : "已确认参考资料（" + source.evidence() + "）：\n" + source.answer();
        String locationContext = context.currentSpotName() == null || context.currentSpotName().isBlank()
            ? "游客当前没有明确所在景点"
            : "游客当前位于" + context.currentSpotName();
        String routePermission = context.routePlanningAllowed()
            ? "游客已登录。收到路线推荐、规划、必去、避开或换线需求时，必须按以下工具闭环执行："
                + "先调用inspectScenicMap获取完整拓扑；结合游客指定的入口、出口、当前位置、景点和路线偏好，"
                + "选择明确的startNodeKey、endNodeKey及首尾连续的orderedSegmentIds；再调用createTourRoute。"
                + "把小时换算为分钟，例如3小时传180。"
                + "若createTourRoute返回success=false，必须根据errorCode和message修正方案并再次调用，"
                + "不得把失败方案当作已生成路线；只有success=true后才能给出路线结果。"
            : "游客当前为匿名身份，不得生成专属路线；可以介绍公开预置路线，并提示登录后规划。";
        String systemPrompt = thinkDirectivePrefix() + defaultPersona(personaPrompt) + "\n"
            + "你是能使用工具的景区导览智能体。" + locationContext + "。\n"
            + routePermission
            + "涉及景点、设施、历史、开放信息、预置路线或两点算路时，必须先调用对应工具核实。"
            + "对话历史中若已有目的地，而游客当前补充自己所在位置，必须合并两轮信息并调用findShortestPath，"
            + "不得把当前位置当成新的景点咨询。"
            + "工具没有确认的信息要坦诚说明，不得用常识补写景区事实或虚构距离。"
            + "路线回复只能使用createTourRoute成功返回的路线名、距离、时长、节点和路线编号，"
            + "不得补写工具未返回的景点故事或设施信息。"
            + "路线工具只提供当前已发布数据，不得声称已经开始导航。"
            + "最短路径中若出现“地图点”“连接点”等内部节点名称，不要让游客朝这些名称行走，"
            + "只说明起点、终点、确认距离和可达性，并提示路线已在地图上高亮。"
            + "回复先用一句自然的话承接游客需求，再给结论和下一步建议；不要重复整段问题。"
            + "你可以在内部完成分析和工具选择，但对游客的最终正文必须以“" + FINAL_ANSWER_MARKER
            + "”开头，且标记后只能输出导游回答，不得输出分析需求、工具名或调用策略。"
            + "使用简洁自然的中文 Markdown，手机端优先，通常控制在2到6句话。\n"
            + verifiedContext;
        ChatClient.ChatClientRequestSpec requestSpec = chatClient.prompt()
            .system(systemPrompt)
            .user(question.trim())
            .tools(tools);
        if (context.sessionId() != null) {
            requestSpec = requestSpec.advisors(spec -> spec
                .advisors(memoryAdvisor)
                .param(ChatMemory.CONVERSATION_ID, "guide-session:" + context.sessionId()));
        }
        Flux<String> content = requestSpec
            .stream()
            .content()
            .transform(this::finalAnswerOnly)
            .switchIfEmpty(Flux.just(source.answer()))
            .onErrorResume(error -> {
                LOGGER.warn("Scenic agent streaming failed, falling back to verified content: {}", error.getMessage());
                return Flux.just(source.answer());
            });
        String evidence = "SAFE_FALLBACK".equals(source.sourceType())
            ? "AGENT_TOOLS"
            : source.evidence() + "|AGENT_TOOLS";
        return new StreamAnswer(content, evidence, tools);
    }

    /**
     * 取思考模式指令并拼成提示词前缀，配置为空表示不下发任何模型方言指令
     */
    private String thinkDirectivePrefix() {
        String directive = configService.getString(THINK_DIRECTIVE_KEY, DEFAULT_THINK_DIRECTIVE);
        return directive == null || directive.isBlank() ? "" : directive.trim() + "\n";
    }

    private Flux<String> finalAnswerOnly(Flux<String> source) {
        return Flux.defer(() -> {
            StringBuilder pending = new StringBuilder();
            AtomicBoolean finalStarted = new AtomicBoolean(false);
            Flux<String> visible = source.handle((chunk, sink) -> {
                if (finalStarted.get()) {
                    sink.next(chunk);
                    return;
                }
                pending.append(chunk);
                int markerIndex = pending.indexOf(FINAL_ANSWER_MARKER);
                if (markerIndex < 0) {
                    return;
                }
                finalStarted.set(true);
                String firstVisible = pending.substring(markerIndex + FINAL_ANSWER_MARKER.length()).stripLeading();
                pending.setLength(0);
                if (!firstVisible.isEmpty()) {
                    sink.next(firstVisible);
                }
            });
            Flux<String> fallback = Flux.defer(() -> {
                if (finalStarted.get()) {
                    return Flux.empty();
                }
                String cleaned = stripInternalPlanning(pending.toString());
                return cleaned.isBlank() ? Flux.empty() : Flux.just(cleaned);
            });
            return visible.concatWith(fallback);
        });
    }

    private String stripInternalPlanning(String content) {
        String value = content == null ? "" : content.trim();
        if (!value.contains("分析需求") && !value.contains("工具调用策略") && !value.contains("执行调用")) {
            return value;
        }
        int lastParagraph = value.lastIndexOf("\n\n");
        return lastParagraph < 0 ? value : value.substring(lastParagraph + 2).trim();
    }

    private boolean isLowInformation(String question) {
        String normalized = normalize(question);
        return normalized.isBlank() || LOW_INFORMATION_PATTERN.matcher(normalized).matches();
    }

    private String lowInformationReply(String question) {
        String value = question == null ? "" : question.trim();
        if (value.isBlank()) {
            return "我还没有收到具体问题。你可以问景点故事、厕所餐饮位置，或者让我帮你规划游览路线。";
        }
        return "我还没看懂“" + value + "”想表达什么。请补充景点名称或需求，"
            + "例如“附近有什么景点”或“厕所在哪里”。";
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String defaultPersona(String personaPrompt) {
        if (personaPrompt == null || personaPrompt.isBlank()) {
            return "你是景区 AI 导游小桃";
        }
        return personaPrompt.trim();
    }

    public record StreamAnswer(Flux<String> content,
                               String evidence,
                               ScenicGuideToolFactory.ScenicGuideTools tools) {

        public StreamAnswer(Flux<String> content, String evidence) {
            this(content, evidence, null);
        }

        public ScenicGuideToolFactory.PathVisualization pathVisualization() {
            return tools == null ? null : tools.pathVisualization();
        }

        public com.huohuo.scenicaiguide.route.dto.AiRouteResponse recommendedRoute() {
            return tools == null ? null : tools.recommendedRoute();
        }

        public com.huohuo.scenicaiguide.route.dto.AiRoutePlanRequest routePlan() {
            return tools == null ? null : tools.routePlan();
        }
    }
}
