# AI 导游数字人 MVP 验收报告

生成时间：2026-06-30

## 1. 验收结论

当前示例景区 MVP 主链路已具备本地可验收条件：

- 游客端 H5 可以匿名启动导览会话，展示数字人，发起问答和路线推荐
- 游客端以已发布地图版本为唯一数据源，路线推荐基于已发布拓扑和预置路线素材组合，不凭空造点造路
- FAQ 优先、知识库检索其次、安全兜底的问答链路已接入游客端和后台预览
- 数字人配置、阿里云 NLS TTS 参数、语音合成与缓存已接入后台和游客端
- 地理围栏自动讲解使用服务端 ENTER、DWELL、EXIT 状态机，具备精度过滤、跳点过滤、稳定停留和冷却去重
- 后台核心页面可维护地图发布、路线模板、QA 资源、数字人配置，并可查看会话、围栏和运营数据

## 2. PRD 验收项

| # | 验收项 | 状态 | 说明 |
|---|---|---|---|
| 1 | 后台管理员可以登录 | 通过 | root 开发账号登录和短信校验可获取 JWT |
| 2 | 后台可以维护并发布线上地图 | 通过 | 地图工作台和地图发布校验页可用，发布检查返回 0 阻塞 |
| 3 | 后台可以维护路线模板 | 通过 | 预置路线页复用地图草稿 payload 维护模板节点 |
| 4 | 后台可以维护 FAQ、知识文本和讲解词 | 通过 | QA 资源页支持 CRUD、重建索引和问答预览 |
| 5 | 后台可以配置数字人基础资料和 TTS 参数 | 通过 | 数字人页加载后端音色、采样率、语速、音量等选项 |
| 6 | 游客可以打开移动端 H5 并看到数字人导游 | 通过 | `/guide` 公共路由不被登录拦截 |
| 7 | 游客可以用文字提问并获得安全回答 | 通过 | 游客消息接口走 FAQ/RAG/安全兜底 |
| 8 | 游客可以获得一条基于偏好的本次会话专属路线 | 通过 | 浏览器实测“开始游览”返回 AI 路线卡片 |
| 9 | 游客位置进入围栏并稳定停留后自动触发讲解 | 通过 | 服务层测试覆盖稳定停留、冷却、退出重置和异常定位 |
| 10 | 数字人可以播放 TTS 语音 | 通过 | 阿里云 NLS TTS 测试通过，游客端支持 audioBase64 播放 |
| 11 | 无知识依据时 AI 不编造景区事实 | 通过 | QA 服务和后台预览使用安全兜底 |
| 12 | 地图未发布、路线素材不足、TTS 不可用等异常有明确提示 | 通过 | 后端业务错误透传到 H5，TTS 失败不阻断文字和路线 |

## 3. 已执行验证

后端：

```bash
mvn -q "-Dtest=AiRouteServiceTest,TouristGuideServiceMessageTest,ScenicMapPublishServiceTest" test
mvn -q test
```

前端：

```bash
npm run build
```

接口巡检：

- `POST /api/v1/system/auth/admin/login`
- `POST /api/v1/system/auth/admin/sms-verify`
- `GET /api/v1/admin/map-drafts`
- `GET /api/v1/admin/map-drafts/{id}/publish-check`
- `GET /api/v1/admin/digital-human`
- `GET /api/v1/admin/digital-human/options`
- `GET /api/v1/admin/routes/ai`
- `GET /api/v1/admin/tourist/sessions`
- `GET /api/v1/admin/tourist/fence-logs`
- `GET /api/v1/admin/analytics/hot-questions`
- `GET /api/v1/admin/analytics/heatmap`
- `POST /api/v1/admin/qa/answer-preview`
- `POST /api/v1/tourist/guide/sessions`
- `GET /api/v1/tourist/scenic-areas/1/map`
- `POST /api/v1/tourist/guide/sessions/{sessionId}/messages`

浏览器实测：

- `http://127.0.0.1:5173/#/guide`
- `http://127.0.0.1:5173/#/scenic/map-publish`
- `http://127.0.0.1:5173/#/qa/faqs`
- `http://127.0.0.1:5173/#/digitalhuman/avatars`

## 4. 验证观察

- 游客端 `/guide` 可匿名打开，首屏展示“小桃导游”和欢迎语
- 点击“开始游览”后生成“桃花源经典轻松路线”路线卡片，接口返回 200
- 地图发布页展示当前 ONLINE 地图，发布校验为可发布，0 个阻塞项
- QA FAQ 页展示真实 FAQ 数据，问答验证面板可用
- 数字人配置页展示后端返回的阿里云音色列表和参数边界
- 浏览器控制台在核心页面验证过程中未出现 error 或 warn

## 5. 已知非阻断项

- 浏览器定位在本地测试环境可能返回定位异常，真实围栏触发已由服务层测试覆盖
- `mvn test` 会执行供应商连通性测试，输出较长，并有 Mockito JDK 21 动态 agent 警告
- Vite 构建存在既有 Rollup 注释警告和大 chunk 警告，不影响当前 MVP 功能
- Qdrant/Embedding 连通性测试可用，但游客问答主链路当前优先使用本地 DB 检索，避免供应商/向量库成为一期阻塞
- 情感分析、词云、系统日志等运营扩展页面不是一期核心闭环，后续可逐步替换占位实现
