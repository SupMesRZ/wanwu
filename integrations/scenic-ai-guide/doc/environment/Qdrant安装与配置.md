# Qdrant 本地部署指南

## 前置条件

- 已安装 Docker Desktop（Windows/macOS）或 Docker Engine（Linux）
- 能访问 Docker Hub（需要梯子）

检查 Docker 是否可用：

| 检查维度 | 命令 | 作用 |
| :-- | :-- | :-- |
| 服务状态 | `docker version` | 确认客户端和守护进程都在运行 |
| 容器列表 | `docker ps -a` | 查看所有容器（运行中 + 已停止） |

## 首次部署（只执行一次）

以下命令会**创建并启动**一个命名容器，数据持久化到 Docker 卷：

```bash
docker run -d --name qdrant -p 6333:6333 -p 6334:6334 -v qdrant_storage:/qdrant/storage qdrant/qdrant
```

参数说明：

| 参数 | 说明 |
| :-- | :-- |
| `-d` | 后台运行 |
| `--name qdrant` | **给容器命名**，后续用 `docker start qdrant` 启动，不会重复创建 |
| `-p 6333:6333` | REST API 和 Web Dashboard 端口 |
| `-p 6334:6334` | gRPC 端口（Java 客户端使用） |
| `-v qdrant_storage:/qdrant/storage` | 数据持久化，容器删除后数据不丢失 |

**注意**：`docker run` 每次都会创建**新容器**。如果反复执行，Docker Desktop 里会堆满 qdrant 容器。首次创建后，后续只应该用 `docker start`

## 日常启停（复用已有容器）

```bash
# 启动（只启动，不创建新容器）
docker start qdrant

# 停止
docker stop qdrant

# 查看日志
docker logs qdrant

# 进入容器内部
docker exec -it qdrant /bin/sh
```

## 验证服务

容器启动后访问：

- Web Dashboard：浏览器打开 `http://localhost:6333/dashboard`
- REST API（PowerShell）：`curl.exe http://localhost:6333`
- REST API（bash）：`curl http://localhost:6333`

Java 客户端连接配置见 `application.yaml`：

```yaml
qdrant:
  host: localhost
  port: 6334          # gRPC 端口
  collection-name: scenic_knowledge
```

## 核心概念：Collection

Qdrant 是向量数据库，内部结构分两层：

```
Qdrant 实例（一个 Docker 容器）
├── Collection: scenic_knowledge        ← application.yaml 里配置的这个
│   ├── 向量点 1: [0.1, 0.2, ...] + payload {title: "断桥", content: "..."}
│   ├── 向量点 2: [0.3, 0.4, ...] + payload {title: "雷峰塔", content: "..."}
│   └── ...
└── Collection: another_collection      ← 可以有多个，互不影响
```

| 概念 | 类比 | 说明 |
| :-- | :-- | :-- |
| **Collection** | MySQL 的表 | 一组向量的集合，创建时指定维度（如 1536 维）和距离算法（如 Cosine） |
| **Point** | MySQL 的行 | 一条向量记录，包含 `id` + `vector` + `payload`（元数据） |

`collection-name: scenic_knowledge` 就是 Java 客户端操作向量时指定的"表名"：

```java
// 写入向量
qdrantClient.upsert("scenic_knowledge", points);

// 检索相似向量（RAG 核心操作）
qdrantClient.search("scenic_knowledge", queryVector);
```

Collection 不会自动创建，需要在 Java 代码中初始化时判断存在性，不存在则创建。

## 常见问题

### 1. 重复创建了很多容器

症状：Docker Desktop 里出现多个 `qdrant/qdrant` 容器

解决：

```powershell
# 停止并删除所有 qdrant 容器（数据在卷里，不会丢）
docker ps -aq --filter "ancestor=qdrant/qdrant" | ForEach-Object { docker stop $_ }
docker ps -aq --filter "ancestor=qdrant/qdrant" | ForEach-Object { docker rm $_ }

# 然后按「首次部署」重新创建一个有名字的容器
```

### 2. 数据丢失了

原因：没有加 `-v` 参数，容器删除后数据随容器一起消失

解决：重新用带 `-v` 参数的 `docker run` 创建容器

### 3. 端口被占用

```bash
# 查看 6333/6334 被谁占用
docker ps

# 停止占用端口的容器
docker stop <容器名>

# 或者换端口映射：-p 16333:6333 -p 16334:6334
```

## 参考

- [Qdrant 官方快速开始](https://qdrant.org.cn/documentation/quickstart/)
