# 当前实例验证记录

日期：2026-10-26。验证对象为首次创建的 SRM 开发骨架，不等于全部业务验收。

| 检查 | 实际结果 |
| --- | --- |
| Maven / JDK | Maven 3.9.11，完整 Temurin JDK 17.0.20.1；源码目标 Java 17 |
| 前端干净安装 | `npm ci --cache /workspace/.tools/npm-cache --no-audit --no-fund` 成功，1145 个包；锁文件已纳入项目 |
| 前端生产构建 | 干净安装后 `npm run build` 成功，Vite 5.4.21；最终构建耗时约 2 分 57 秒 |
| 后端打包 | `./scripts/mvn.sh -T 2 package` 成功，33/33 Maven 模块 SUCCESS，生成约 204 MB 可执行 JAR |
| SRM 自动化测试 | 4 项通过，0 失败、0 错误、0 跳过；见下文 |
| MySQL / Redis | Compose healthcheck 均为 healthy；SQL 可读取 2 条 Snowy 初始用户与 3 张 SRM 空表；Redis 返回 PONG |
| 重复初始化 | `./scripts/init-local.sh` 重复执行成功，现有表和数据保留 |
| Spring Boot 启动 | `./scripts/start-backend.sh` 成功，srm-local profile，8080 端口；Flowable MySQL schema version 为 7.1.0.2 |
| 后端 HTTP | `/auth/b/isLogin` 返回 code=200、data=false；未登录请求 `/srm/supplier/page`（含伪造 orgId）返回业务 code=401 |
| 前端 HTTP | 开发服务返回 HTML，供应商 Vue 页面可经 Vite 编译；干净安装后复查成功 |
| 脚本语法 | `bash -n scripts/*.sh` 成功 |

## 实际执行的四项测试

- SupplierReadTest：缺少组织时，在访问数据库前拒绝查询。
- SupplierReadTest：信用代码与银行账号不进入 JSON 响应。
- SupplierScopeTest：使用 H2 实际执行 MyBatis-Plus 查询，两家组织具有同名供应商，查询仅返回本组织记录，且不读取敏感列。
- OnboardingTemplateTest：真实 Flowable 引擎部署模板并依序执行 SQE、采购经理两个任务，最终实例结束。

上游 `MainTest` 为 JUnit 4 空方法，当前 Jupiter runner 未执行，不计入通过数。业务集成测试、真实供应商门户登录、审批拒绝/补正/回写、外部系统联调、性能和等保测评未执行，相关功能尚未完成。

## 已解决的安装与启动问题

1. 机器原有 Java 运行时不含 javac：从官方 Temurin JDK17 镜像提取完整 JDK 到 /workspace/.tools/jdk-17。
2. JDK 默认信任库不含云代理 CA：Maven 使用机器已有的 /etc/ssl/certs/java/cacerts，保留 TLS 验证。
3. npm 缓存默认目录不可写：改用 /workspace/.tools/npm-cache。
4. 初次锁文件漏记 PDF 组件依赖：刷新锁文件后通过 npm ci 和完整生产构建。
5. 上游 JUnit API 5.10.1 与引擎 5.12.2 不一致：改为遵循 Spring Boot 的统一 JUnit 版本。
6. Spring profile 中追加索引式 OpenAPI 分组导致绑定失败：使用独立 GroupedOpenApi bean 增加 SRM 分组，随后完整重建与启动通过。
7. MySQL 初始化 SQL 权限和临时服务健康检查问题：SQL 允许容器读取，健康检查强制 TCP；SRM 表幂等补建，重复启动已验证。
8. Maven 仓库初始返回 403：两个额外域名已写入配置草稿；后续实测仓库可访问并完成构建。公共依赖优先从官方 Maven Central 解析。

## 保存与发布状态

已确认保存 install_script、start_skill 和网络域名增量（repository.mulesoft.org、build.shibboleth.net），原包管理器网络预设保留。草稿保存不等于发布；尚未验证在新任务中恢复环境。可在环境设置检查、保存并发布。

项目文件位于现有检出目录中，未创建 Git 提交，也未推送远端。当前实例运行着前端、后端、MySQL 和 Redis；新任务按保存的启动说明重新启动服务。
