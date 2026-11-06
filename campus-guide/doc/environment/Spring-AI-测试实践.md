# Spring AI 测试实践

记录项目接入 Spring AI 1.0.7 过程中踩过的坑和验证通过的写法。

---

## 1. 单轮对话

```java
Prompt prompt = new Prompt(List.of(
    new SystemMessage("你是景区AI导览员，热情专业地回答游客问题"),
    new UserMessage("断桥有什么历史故事？")
));

ChatResponse response = chatModel.call(prompt);
String text = response.getResult().getOutput().getText();
```

**要点**：
- `call(Prompt)` 返回完整响应，`getResult().getOutput().getText()` 提取文本
- `getContent()` 方法不存在，Spring AI 1.0.7 用的是 `getText()`

---

## 2. 多轮对话

```java
List<Message> history = new ArrayList<>();
history.add(new SystemMessage("..."));
history.add(new UserMessage("断桥有什么历史故事？"));

// 第一轮
ChatResponse r1 = chatModel.call(new Prompt(new ArrayList<>(history)));
history.add(new AssistantMessage(r1.getResult().getOutput().getText()));

// 第二轮（依赖上文）
history.add(new UserMessage("我上个问题问你的是哪个景点？我忘了"));
ChatResponse r2 = chatModel.call(new Prompt(new ArrayList<>(history)));
```

**要点**：
- 每次调用前必须 `new ArrayList<>(history)` 复制列表，否则 `Prompt` 内部可能修改原列表
- `AssistantMessage` 用于把 AI 回复加入历史，让下一轮能引用
- 测试时构造追问来验证上下文记忆（如"我上个问题问的是哪个景点"），比主题延续更有测试价值

---

## 3. 流式输出

**推荐用法**（官方便利方法，已做空安全处理）：

```java
// 直接拿到 Flux<String>，无需手动处理 null
List<String> tokens = chatModel.stream(ctx.toArray(new Message[0]))
    .doOnNext(token -> System.out.print(token))
    .collectList()
    .block(Duration.ofSeconds(60));

String fullText = String.join("", tokens);
```

**底层用法**（需要手动处理 null）：

```java
// 部分 chunk 的 getResult() 为 null（如仅含 finish_reason 的结束帧）
flux.doOnNext(response -> {
    String text = Optional.ofNullable(response.getResult())
        .map(Generation::getOutput)
        .map(AssistantMessage::getText)
        .orElse("");
    // ...
});
```

**要点**：
- `StreamingChatModel#stream(Message...)` 返回 `Flux<String>`，内部已用 `Optional.ofNullable(response.getResult())` 做空安全处理
- `ChatResponse.getResult()` 可能为 null（`generations` 为空时返回 null），这是官方设计，不是 bug
- `OpenAiChatModel.internalStream()` 在 chunk 解析异常时会返回 `new ChatResponse(List.of())`

---

## 4. base-url 路径拼接坑

Spring AI `OpenAiApi` 默认路径：
- chat: `completionsPath = "/v1/chat/completions"`
- embedding: `embeddingsPath = "/v1/embeddings"`

这些路径会和 `base-url` 拼接，如果 base-url 已带 `/v1`，请求路径会双写：

| 平台 | base-url 写法 | 结果路径 | 是否正确 |
|------|--------------|---------|---------|
| 小米 Mimo | `https://.../`（不带 `/v1`） | `/v1/chat/completions` | ✅ |
| 阿里云百炼 | `https://.../compatible-mode/v1`（带 `/v1`） | `/v1/v1/embeddings` | ❌ |

**阿里云百炼 embedding 修复**：显式覆盖 `embeddings-path`

```yaml
spring:
  ai:
    openai:
      embedding:
        base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
        embeddings-path: /embeddings  # 去掉重复的 /v1
```

---

## 5. 环境变量配置（Windows）

在 PowerShell 中设置（当前会话有效）：

```powershell
$env:API_KEY_CHAT = "sk-xxx"
$env:API_KEY_EMBEDDING = "sk-yyy"
```

在系统环境变量中设置（永久有效）：

```powershell
[Environment]::SetEnvironmentVariable("API_KEY_CHAT", "sk-xxx", "User")
```

IDEA 中运行测试时从环境变量读取，不需要硬编码到配置文件。
