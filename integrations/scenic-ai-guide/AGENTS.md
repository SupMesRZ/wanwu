# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

---

## 协作约束（必读）

**注释风格**：代码注释句尾不要带句号

**修改代码前必须向用户说明**：
1. 打算修改哪些文件
2. 修改的内容和原因
3. 预期的影响

简单修改（单文件、几行）一句话说明即可。复杂修改（多文件、重构）详细说明修改计划，等待用户确认后再执行。

---

## 常用命令

```bash
# 构建项目
mvn clean compile

# 运行测试
mvn test

# 运行单个测试类
mvn test -Dtest=ClassName

# 运行单个测试方法
mvn test -Dtest=ClassName#methodName

# 打包
mvn clean package

# 启动应用
mvn spring-boot:run

# 跳过测试打包
mvn clean package -DskipTests
```

**开发环境**：JDK 21，Maven。IDE 推荐 IntelliJ IDEA。

---

## 项目概述

景区AI数字人导览系统。游客通过手机与AI数字人语音/文字交互，获取景区讲解、路线推荐；管理后台维护知识库、数字人形象、查看运营数据。

**技术栈**：Spring Boot 3.5.14 + JDK 21 + MyBatis-Plus 3.5.11 + Spring AI 1.0.7 + Redis + Qdrant + MySQL + 阿里云OSS。

---

## 高层架构

### 业务域（Domain）划分

按业务域分包，7个域 + common + config：

| 域 | 包名 | 职责 |
|------|------|------|
| **数字人交互** | `digitalhuman` | ASR/TTS、数字人形象/声音/动作 |
| **智能问答** | `qa` | RAG检索、LLM调用、讲解词管理、知识库 |
| **景区资产** | `scenic` | 景区/景点/设施/标签、坐标、手绘地图、围栏、路线节点+连线 |
| **路线导览** | `route` | 预制路线、AI路线推荐、Dijkstra路径、拓扑导览、算路子agent |
| **游客会话** | `tourist` | 游客身份、会话、消息、位置追踪、围栏触发判定（导览业务状态管理） |
| **运营分析** | `analytics` | 数据统计、词云、情感分析、报告（只读不写） |
| **系统支撑** | `system` | RBAC权限、JWT登录、操作日志、接口限流 |

**关键设计原则**：
- 系统支撑域只提供通用基础设施，不拥有业务配置语义。业务域负责定义自己的配置项、默认值、校验规则和使用语义；配置存储、读取、缓存刷新、审计和后台 CRUD 入口由 system 域统一配置中心提供。普通配置 CRUD 不在各业务域复制接口，只有包含领域动作的配置操作才由业务域暴露专用接口
- 游客会话域管理导览业务状态（导航、节点、围栏、播报），不是万能编排入口。`session/{id}/message` 是"导览中消息"入口，负责消息持久化和会话状态关联。AI 问答核心逻辑由 qa 域直接处理，TTS 由 digitalhuman 域直接暴露。AI 对话上下文由 ChatMemory 独立维护，当 AI 需要导览状态时，通过 Tool 查询 Guide Session
- 运营分析域只读取业务数据，不反向修改。

### 核心业务流程

1. **智能问答**：游客问题 → 问答域（FAQ命中?直接返回 : RAG检索 → LLM生成）→ 数字人域TTS播报。AI 上下文由 ChatMemory 独立维护，需要导览状态时通过 Tool 查询 Guide Session
2. **地理围栏自动讲解**：位置上报 → 会话域（Haversine+状态机）→ 问答域获取讲解词 → 数字人域播报
3. **点对点路径**：游客指定目的地 → 路线域调用算路子agent（Dijkstra）→ 手绘地图高亮Polyline
4. **AI游园路线**：问答域识别意图 → 调用算路子agent → LLM润色 → 保存AI路线 → 拓扑导览推进

---

## 包结构

```
com.huohuo.scenicaiguide
  ├─ common
  │   ├─ api           # ApiResponse<T>, PageResponse<T>
  │   ├─ enums         # ErrorCode
  │   ├─ exception     # BusinessException, GlobalExceptionHandler
  │   └─ redis         # RedisDomain, RedisFeature, RedisUtils
  ├─ config            # Spring/MyBatis-Plus/Redis/Qdrant配置
  ├─ {domain}          # 7个业务域，每个域内部：controller/service/mapper/entity
  │   ├─ controller
  │   ├─ service
  │   ├─ mapper
  │   └─ entity
```

各域开发时再按需创建 `dto.request` / `dto.response`。

---

## 接口规范

### 统一响应

所有接口返回 `ApiResponse<T>`：

```json
{ "code": "OK", "message": "success", "data": {} }
```

- `code="OK"` 表示成功，其他值为 `ErrorCode` 枚举名（全大写下划线分隔）
- 失败时 `data` 为 `null`
- 分页接口 `data` 为 `PageResponse<T>`：`records/page/pageSize/total/totalPages`

### URL前缀

| 前缀 | 说明 | 认证 |
|------|------|------|
| `/api/v1/tourist/**` | 游客端 | 可匿名，部分需 Bearer；匿名游客通过 `session/init` 获取匿名 token 后访问需身份关联的接口 |
| `/api/v1/admin/**` | 管理后台 | JWT Bearer Token |
| `/api/v1/system/**` | 系统公共 | 视接口而定 |

### HTTP方法

- `GET` 查询，`POST` 创建，`PUT` 全量更新，`PATCH` 局部更新/状态变更，`DELETE` 逻辑删除
- 非CRUD动作：`POST /{resource}/{id}/{verb}`

### 分页参数

`page`（从1开始，默认1）、`pageSize`（默认20，最大100）。

---

## 数据库约定

- **MyBatis-Plus**：实体类用 `@TableName`、`@TableId`、`@TableField`。Mapper继承 `BaseMapper<T>`。Service继承 `IService<T>` / `ServiceImpl`。
- **逻辑删除**：全局字段 `deleted`（TINYINT），已删除=1，未删除=0。MyBatis-Plus全局配置自动处理。
- **表名前缀**：`{域标识}_{表名}`，如 `scenic_area`、`qa_faq`、`route_node`。
- **统一字段**：`id`（BIGINT AUTO_INCREMENT）、`created_at`、`updated_at`、`deleted`。
- **状态字段分类**：`status` 不做一刀切类型统一，按语义分类并保持同一语义同一接口类型。资源启停用状态使用 `TINYINT/Integer` 的 `1/0`；流程、任务、封禁等多状态使用 `VARCHAR/String` 枚举；任务派生状态优先使用语义字段名（如 `vector_status`）避免和资源启停用混淆。游客端只返回启用资源时可省略资源 `status`，不把同名 `status` 转成字符串展示
- **时间格式**：数据库存储和页面显示默认使用 `yyyy-MM-dd HH:mm:ss`，接口传输使用 ISO 8601（如 `2026-06-04T10:00:00`），Java 用 `LocalDateTime`
- **Redis key 格式**：`scenic-ai-guide:{domain}:{feature}[:suffix]`，`scenic-ai-guide` 是项目级命名空间，`domain` 由 common 层 `RedisDomain` 统一维护，`feature` 由各业务域自己的 `RedisFeature` 枚举维护，suffix 用于具体对象标识或细分操作。业务分层口径是 `{domain}:{feature}`，这是当前代码和测试采用的定档约定

---

## 关键配置

### Spring AI（OpenAI 兼容接口）

配置在 `application.yaml`，chat 和 embedding 分别使用独立 API Key 和端点：

```yaml
spring:
  ai:
    model:
      chat: openai
      embedding: openai
      audio:
        speech: none
        transcription: none
      image: none
      moderation: none
    openai:
      # 注意：不要带 /v1 后缀，Spring AI 内部会默认拼接 /v1/chat/completions
      base-url: https://llm-ty8i6j9dj1kd3f2z.cn-beijing.maas.aliyuncs.com/compatible-mode
      chat:
        api-key: ${API_KEY_CHAT}
        options:
          model: qwen3.6-flash
          temperature: 0.7
      embedding:
        # 百炼 OpenAI 兼容接口这里不带 /v1，Spring AI 默认追加 /v1/embeddings
        base-url: https://dashscope.aliyuncs.com/compatible-mode
        api-key: ${API_KEY_EMBEDDING}
        options:
          model: text-embedding-v4
```

使用 `spring-ai-starter-model-openai` 配合 OpenAI 兼容接口。chat 走阿里云北京 MaaS `qwen3.6-flash`，embedding 走阿里云百炼文本向量模型。`spring.ai.model.*` 显式启用/禁用各模型类型的自动装配，避免未配置时创建失败。

**⚠️ base-url 路径拼接坑**：Spring AI `OpenAiApi` 默认 `completionsPath = "/v1/chat/completions"`、`embeddingsPath = "/v1/embeddings"`，这些路径会和 `base-url` 拼接。如果 base-url 已带 `/v1`，请求路径会双写（如 `/v1/v1/chat/completions`）导致 404。不同平台的 base-url 设计不同，需按需调整：
- 北京 MaaS Chat：base-url 去掉用户端点末尾的 `/v1`，让 Spring AI 自动拼接 `/v1/chat/completions`
- 阿里云百炼当前采用已验证写法：base-url 不带 `/v1`（`https://.../compatible-mode`），让 Spring AI 自动拼接 `/v1/embeddings`
- 如果后续改成带 `/v1` 的百炼 base-url（`https://.../compatible-mode/v1`），必须在 embedding 下显式覆盖 `embeddings-path: /embeddings` 去掉重复的 `/v1`
- `qwen3-vl-embedding` 不适用于百炼 OpenAI 兼容文本 embedding 接口，当前定档模型为 `text-embedding-v4`

### MyBatis-Plus

配置在 `application.yaml`：
```yaml
mybatis-plus:
  mapper-locations: classpath*:mapper/**/*.xml
  configuration:
    map-underscore-to-camel-case: true
  global-config:
    db-config:
      logic-delete-field: deleted
      logic-delete-value: 1
      logic-not-delete-value: 0
```

### 
