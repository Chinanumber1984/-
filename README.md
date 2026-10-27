# 集团供应商关系管理系统 · SRM

基于真实Snowy v3.7.0源码，Java17 / Vue3 / AntDV4 / Vite5 / MySQL8 / Redis7 / Flowable7。来源与版权见 [PROVENANCE](docs/upstream/PROVENANCE.md)。

当前交付：W1–2基础设计与离线Demo、[W3–6真实档案与门户](docs/workspace/README.md)、[W7–9首版真实准入与生命周期](docs/lifecycle/README.md)。全114项需求的状态见 [backlog](docs/backlog.csv)；完整SRM尚未全部交付。

## 本地启动

需要Docker+Compose、Node20+、Python3、curl、git。首次联网安装并构建：

```bash
./scripts/install.sh
# 分别在两个终端启动
./scripts/start-backend.sh
./scripts/start-workspace.sh
```

后端8080，真实工作台5175。原生Snowy前端可用 `./scripts/start-frontend.sh` 启动于5173，首页待办卡片与SRM菜单接入同一工作台。W1–2离线演示保留在 `demo/SRM演示.html`，其浏览器内审批与真实Flowable相互独立。

本地开发账号：

```bash
node scripts/prepare-workspace-access.mjs
node scripts/prepare-approval-access.mjs
```

发起人登录信息在 `.runtime/workspace-access.md`；独立SQE、采购经理、流程管理员在 `.runtime/approval-access.md`。脚本不重置已有账号或密码。门户联系人可由内部管理员开通独立账号，也可通过内部管理员生成的单次注册邀请自主注册。原有验证码策略保留。

数据库、加密与存储凭证首次生成于被Git忽略、权限600的 `.runtime/` 文件。不要提交、打印或用于生产。MySQL/Redis/MinIO仅绑定本机13306/16379/19000。MinIO从官方固定源码构建，不依赖镜像仓库登录；本机使用独立私有桶，下载受供应商与组织权限约束。

启动会保留数据库卷，备份并执行Flyway V1–V5迁移；已有未知表结构或无法确定的敏感数据不会被猜测转换。Flowable建表自动升级仅限srm-local开发配置；生产必须单独审核升级和密钥注入。

## 验证

启动真实后端与工作台后，在开发环境按顺序执行：

```bash
./scripts/mvn.sh -T 2 test
./scripts/test-foundation-db.sh
node scripts/test-workspace-api.mjs
python scripts/test-workspace-browser.py
node scripts/test-lifecycle-api.mjs
python scripts/test-lifecycle-browser.py
```

浏览器测试需要Playwright及Chromium。优先使用 `/usr/bin/chromium`，可用 `SRM_CHROMIUM_PATH` 指定，或使用Playwright安装的浏览器。重跑整套前可执行 `node scripts/cleanup-workspace-fixture.mjs`，仅清理脚本所属的测试组织与记录；不要在生产环境运行验收夹具。报告与截图见 `docs/workspace/`、`docs/lifecycle/`、`workspace/`。新增CI工作流已编写，尚未在远端执行。

## 当前边界

真实准入、补正重提、驳回、关键资料变更、冻结/解冻/淘汰、领取/转办/加签、超时催办与站内消息已接入。流程管理支持受控BPMN上传和版本挂起，不允许任意脚本/服务任务。企微/钉钉适配器已有持久化重试，但未配置真实Webhook，不能宣称外部消息送达已验收。

绩效、寻源竞价、采购协同、质量风险和ERP/MES/QMS/财务真实集成尚未交付。冻结/淘汰提供新增业务阻断API，后续业务入口必须调用并再次验收。等保、生产密钥轮换、审计长期保存、性能压测和信创适配需要独立验证。

需求基线在 `docs/requirements/`，排期见 [DEVELOPMENT_PLAN](docs/DEVELOPMENT_PLAN.md)，跨阶段未完成AC保持PARTIAL。

用户电脑本地验收：[启动说明](deploy/review/README.md) · [入口修复与实测](docs/review/README.md)。先完整解压验收包并启动，再打开本机18080入口；不是云环境预览地址。

无法下载构件时可从源码生成本地验收包：在WSL、Docker可访问的前提下执行 `bash scripts/build-local-review.sh`，然后执行 `bash artifacts/SRM-local-review/start.sh`。镜像和Maven/npm依赖首次下载需联网，宿主机仅需Git、Python3和Docker；完整交接见 docs/review/LOCAL_CODEX_HANDOFF.md。
