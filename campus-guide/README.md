# 河北大学七一路校区导航

面向河北大学七一路校区的新生与访客导航系统。用户可通过手机查看高德校园地图、当前位置、自建校内路线，并与 AI 校园助手交互；管理后台用于维护校园地点、设施、路线和知识库。

## 技术栈

- **后端**：Spring Boot 3.5 + JDK 21 + MyBatis-Plus + Spring AI（OpenAI 兼容接口）
- **前端**：Vue 3 + Vite（`front/`）
- **中间件**：MySQL 8、Redis 7、Qdrant（可选，向量检索增强）
- **云服务**：阿里云 OSS、NLS（ASR/TTS）、MaaS / 百炼（LLM 与 embedding）

## 环境要求

- JDK 21、Maven 3.8+
- Node.js 18+
- Docker（本地起 Redis 用）
- 可用的 MySQL 8 实例

## 快速开始

> **Docker Hub 拉取超时**：本机若直连 docker.io 不通，可经镜像源拉取后重打标签，如
> `docker pull docker.m.daocloud.io/library/mysql:8.0 && docker tag docker.m.daocloud.io/library/mysql:8.0 mysql:8.0`

### 1. 准备中间件

```bash
# Redis（本地开发）
docker run -d --name scenic-redis -p 6379:6379 redis:7.4-alpine

# MySQL：建库后执行初始化脚本
mysql -u <user> -p < sql/init.sql
```

### 2. 配置环境变量

参考 `.env.example`。最少需要：

- `DB_URL` / `DB_USERNAME` / `DB_PASSWORD`
- `API_KEY_CHAT`、`API_KEY_EMBEDDING`（无默认值，缺失则启动失败；只想无密钥演示见下节）
- 语音与地图功能另需 `ALIYUN_ACCESS_KEY_ID` / `ALIYUN_ACCESS_KEY_SECRET` / `NLS_APP_KEY` / `VITE_GAODE_API_KEY` / `VITE_GAODE_SECRET`

默认 profile 为 `dev`。短信验证码不接真实服务商（定档），所有环境下都随登录响应前端回显；生产部署必须显式设置 `JWT_SECRET`。

### 2.1 无密钥演示部署（可选）

不配任何云密钥也能启动并演示主流程，最小依赖只有 MySQL + Redis：

```bash
APP_AI_CHAT_MODEL=none APP_AI_EMBEDDING_MODEL=none mvn spring-boot:run
```

- 问答降级为检索证据直出（FAQ / 知识库内容原样作答，响应 evidence 标 `LLM_OFF`），AI 路线规划与多轮记忆不可用
- 不配 `ALIYUN_ACCESS_KEY_ID` / `ALIYUN_ACCESS_KEY_SECRET` 时，TTS 自动降级为本地生成的静音 WAV（前端字幕照常显示），ASR 返回空（前端提示没听清）；有密钥但想省配额时，可在配置中心把 `dh-tts-provider` / `dh-asr-provider` 置为 `mock` 显式强制
- 降级只发生在"密钥从未配置"时；密钥配了但调用失败仍会显式报错，不会静默吞故障

### 3. 启动后端（端口 8086）

```bash
mvn spring-boot:run
```

### 4. 启动前端（端口 5173，代理到 8086）

```bash
cd front
cp .env.example .env.local
npm install
npm run dev
```

后台地图工作台和用户端校园地图使用高德 JSAPI 2.0。请在 `front/.env.local` 填写 `VITE_GAODE_API_KEY` 和 `VITE_GAODE_SECRET`；该文件已被 Git 忽略。正式生产环境应将安全密钥改为服务端代理配置。

## 测试与构建

CI：仓库带 Gitee Go 流水线 `.workflow/ci.yml`（master 推送触发）——后端 `mvn -B clean test`（none 模式，不需要云密钥；集成层需构建环境有 Docker），前端 `npm ci && npm run build`。


```bash
# 后端测试（需要 Docker：集成层用 Testcontainers 自包含 MySQL 与 Redis）
mvn test

# 含真实云服务调用（NLS/OSS/LLM/Qdrant）的外部测试
mvn test -Pexternal-tests

# 后端打包
mvn clean package -DskipTests

# 前端构建
cd front && npm run build
```

## 可选：向量检索增强

知识库检索默认是关键词 + n-gram 评分，不依赖 Qdrant。将配置 `qa-vector-enabled` 置为 `true`（配置中心 qa 域）后，reindex 会真实写入 Qdrant，检索变为"向量召回 + 关键词召回"合并，任一级故障自动降级纯关键词。需要 `QDRANT_*` 环境变量与 `API_KEY_EMBEDDING` 可用。

## 文档

| 文档 | 内容 |
|------|------|
| `doc/产品架构与功能设计.md` | 架构主文档：产品定位、域职责、功能全景、关键设计裁定 |
| `doc/商用化路线图.md` | P0/P1/P2 实施计划与进度（活文档） |
| `doc/项目现状评审报告.md` | 功能完成度矩阵、缺陷清单、前后端契约核对 |
| `doc/接口规范.md` | 统一响应格式、错误码、URL 与分页规范 |
| `doc/系统架构设计.md` | 7 个业务域详细设计与协作总图 |
| `CLAUDE.md` | 面向 AI 协作者的仓库导引与约定 |

## 许可

内部项目，暂未选择开源许可
