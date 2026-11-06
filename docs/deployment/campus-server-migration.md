# 河小智服务器部署与数据同步

服务器：Ubuntu 20.04 / amd64，校园网地址 `10.191.19.26`。部署目录为 `/data/hexiaozhi`，沿用笔记本的修改版前端、后端程序及完整服务配置。

## 启动与查看状态

服务器使用已经解析并调整路径的 `compose.runtime.json`，包含万悟平台和本体平台共 36 个服务。初始化任务执行成功后退出，其余服务持续运行。该文件和 `.env*` 含有运行凭据，应保持权限 `600`，不提交到仓库。

```bash
cd /data/hexiaozhi
docker compose -p hexiaozhi -f compose.runtime.json up -d --pull never
docker compose -p hexiaozhi -f compose.runtime.json ps -a
docker compose -p hexiaozhi -f compose.runtime.json logs --tail 100 nginx bff-service
```

完整镜像由笔记本导出并在服务器加载，启动不依赖远程拉取。校园修改通过服务器本地的 `bin/amd64`、`configs` 和 `web/dist` 挂载。修改版工作流程序位于 `bin/amd64/workflow-wanwu`。

## 本次迁移内容

停止源容器后，导出全部 12 个持久化卷及挂载文件，再恢复笔记本服务。目标服务器先停止服务并备份，再导入这一份一致的数据副本。

同步范围包括 MySQL 全部业务数据库、Redis、Kafka、MinIO 文件、Elasticsearch 索引与证书、共享目录、本体存储、平台 RSA 密钥、模型配置及加密设置、助手发布版本和工作流。

为适配服务器访问，工作流画布中原来指向笔记本 `localhost:8081` 的图标地址已改为同源路径，外部访问地址和挂载路径已调整为服务器配置。

数据核对结果：273 张业务表记录数一致；四个账号及密码状态、角色权限、模型配置、知识库及问答内容、助手和工作流配置逐项一致；10 个 Elasticsearch 索引的文档数量一致。知识库总计 14 个、文档 7 份、问答 72 条。管理员真实登录、学生/教师/教务助手权限、学生账号的图书馆知识问答，以及工作流和本体页面入口均已通过检查。

数据库和文件仍使用 Docker 专用持久化卷，实际目录可用 `docker volume inspect` 查看；挂载文件及备份位于 `/data/hexiaozhi`。不要运行 `docker compose down -v`，该命令会删除业务数据卷。

## 备份与再次同步

同步前的服务器备份位于 `/data/hexiaozhi/backup/before-laptop-sync-*`。`backup/latest-laptop-sync-backup` 记录本次备份目录，包含持久化卷、配置与程序文件，以及当时运行的容器清单。

本次为一次性同步。笔记本和服务器之后各自保存新增数据，不会自动双向同步。再次迁移数据库时需要重新停止写入、备份目标数据，并一起迁移对应文件和索引。

## 访问地址与域名

校园网访问入口为 `http://10.191.19.26:8081/aibase/login`；同时提供 HTTP 80 端口，便于学校配置域名入口。

学校 HTTPS 域名入口已接通：`https://agent.hbu.edu.cn/aibase/login`。域名首页、前端脚本和公开基础接口已验证返回 HTTP 200。Nginx 使用 `absolute_redirect off` 返回相对跳转，保留网关提供的 HTTPS 协议和域名，避免跳转到内部 `8081` 端口。

计划域名 `hexiaozhi.hbu.edu.cn` 不属于当前已验证的入口；如需另行启用，仍需学校配置。

该服务器只有 4 核、8GB 内存，低于项目完整部署的资源参考。演示前应检查容器健康状态与内存。

## 2026-09-17 登录图片与 502 修复

服务器内核日志确认内存耗尽，Elasticsearch 被反复杀掉；原有 4GB Swap 已用满。同时 Nginx 日志记录了后台容器名称解析失败，导致登录配置和验证码请求返回 502。

已检查并重新加载 Nginx；在 `/data/hexiaozhi/hexiaozhi-extra.swap` 新增 8GB Swap，并写入 `/etc/fstab`，总 Swap 约 12GB。Elasticsearch 的 JVM 堆调整为 1GB、容器内存上限调整为 2GB。本体计算服务的初始堆调整为 256MB，最大堆仍为 2GB，保留查询内存配置和全部服务。

以上运行参数已保存到服务器的 `compose.runtime.json` 和本体计算服务的 `jvm.config`，正常启动命令会沿用。修改前的配置及 `/etc/fstab` 备份位于 `/data/hexiaozhi/backup/resource-fix-20260917-091126`。修复脚本为 `/data/hexiaozhi/hbu-resource-fix.py`；不要用笔记本原配置覆盖服务器的资源调整。

验证结果：登录页面、课程页面、登录背景图、Logo、验证码及前端 JS/CSS 均返回 200；直接访问学校公网 HTTPS 入口的登录配置接口返回 200；学生身份下的课程、今日课程和首页数据接口返回业务成功；已发布图书馆知识问答通过检查。Elasticsearch 恢复健康，检查时重启次数为 0，Nginx 修复后的日志未出现新的 DNS 错误。

## 2026-09-17 清理学生请假记录

按用户要求，已删除服务器 `student` 账号（用户 4、组织 3）的 5 条请假记录。删除前的 SQL 数据和后台程序备份位于 `/data/hexiaozhi/backup/clear-student-leaves-20260917-100605`。其他账号的记录未删除，笔记本数据库未同步清理。

后台已更新，去掉请假记录为空时自动返回两条示例记录的逻辑；请假列表现在返回实际申请，无申请时返回空数组。请假列表、课程、今日课程和首页接口已用学生身份验证成功。
