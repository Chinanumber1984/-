# 集团供应商关系管理系统 · SRM

基于真实Snowy v3.7.0源码，Java17 / Vue3 / AntDV4 / Vite5 / MySQL8 / Redis7 / Flowable7。来源与版权见 [PROVENANCE](docs/upstream/PROVENANCE.md)。

当前交付：W1–2基础设计与离线Demo、[W3–6真实档案与门户](docs/workspace/README.md)、[W7–9首版真实准入与生命周期](docs/lifecycle/README.md)、[W10–11绩效前置](docs/performance/README.md)。另已交付 [W12–15寻源与竞价](docs/sourcing/README.md)、[W16–19采购协同本机版](docs/collaboration/README.md)、[W20–22质量风险本机版](docs/quality/README.md)。全114项需求的状态见 [backlog](docs/backlog.csv)；真实外部联调与总体验收尚未全部完成。

## 本地启动

需要Docker+Compose、Node20+、Python3、curl、git。首次联网安装并构建：

```bash
./scripts/install.sh
# 分别在两个终端启动
./scripts/start-backend.sh
./scripts/start-workspace.sh
```

后端8080，真实工作台5175。原生Snowy前端可用 `./scripts/start-frontend.sh` 启动于5173，首页待办卡片与SRM菜单接入同一工作台。W1–2离线演示保留在 `demo/SRM演示.html`，其浏览器内审批与真实Flowable相互独立。

当前本机验收实例只启用四个账号（原验收账号停用，历史记录保留）：

| 账号 | 登录入口 | 用途 |
| --- | --- | --- |
| lijunhua | http://localhost:18080/?internal=1 | 内部测试、建档、发布寻源、发起审批 |
| admin | http://localhost:18080/?internal=1 | 系统管理、流程管理及审批 |
| lixiaolong | http://localhost:18080/?portal=1 | 受邀供应商报价与业务测试 |
| gysadmin | http://localhost:18080/?portal=1 | 本供应商资料、资质及联系人管理 |

密码统一在 `artifacts/SRM-local-review/runtime/登录账号.txt` 本机查看，不在聊天、日志或Git中公开。两个门户账号绑定到用户当前寻源邀请的供应商，门户登录后默认打开寻源列表。发布寻源不会自动创建登录用户；新增供应商的门户账号由内部管理员在联系人中开通。

四账号的配置、权限和验证说明见 [FOUR_ACCOUNT_PROFILE.md](docs/review/FOUR_ACCOUNT_PROFILE.md)。`node scripts/configure-review-accounts.mjs` 对当前已配置实例仅检查，不重置密码或生成额外用户。完整验收脚本在四账号模式下使用 `node scripts/test-four-account-review.mjs`，不再创建早期批量验收账号。

旧 `.runtime/workspace-access.md`、`.runtime/approval-access.md` 和历史验证报告中的账号属于归档记录，不再是本机登录清单。W1–2开发初始化流程与本机验收包的四账号配置独立，请以当前 `runtime/登录账号.txt` 为准。

数据库、加密与存储凭证首次生成于被Git忽略、权限600的 `.runtime/` 文件。不要提交、打印或用于生产。MySQL/Redis/MinIO仅绑定本机13306/16379/19000。MinIO从官方固定源码构建，不依赖镜像仓库登录；本机使用独立私有桶，下载受供应商与组织权限约束。

迁移脚本已扩展至V9；开发实例使用Flyway，本机验收包使用保留runtime和数据卷的原位升级脚本。升级前备份，遇到未知表结构或密钥不匹配时停止，不猜测转换。Flowable建表自动升级仅限srm-local开发配置；生产必须单独审核升级和密钥注入。

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

浏览器测试需要Playwright及Chromium。优先使用 `/usr/bin/chromium`，可用 `SRM_CHROMIUM_PATH` 指定，或使用Playwright安装的浏览器。重跑整套前可执行 `node scripts/cleanup-workspace-fixture.mjs`，仅清理脚本所属的测试组织与记录；不要在生产环境运行验收夹具。报告与截图见 `docs/workspace/`、`docs/lifecycle/`、`workspace/`。新增CI工作流已编写，尚未核验远端执行结果。

## 当前边界

真实准入、补正重提、驳回、关键资料变更、冻结/解冻/淘汰、领取/转办/加签、超时催办与站内消息已接入。流程管理支持受控BPMN上传和版本挂起，不允许任意脚本/服务任务。企微/钉钉适配器已有持久化重试，但未配置真实Webhook，不能宣称外部消息送达已验收。

绩效已交付统一模板、历史/沙箱导入、月度评分、分级趋势、导出和门户申诉复核。寻源竞价、采购协同、质量风险和ERP/MES/QMS/财务真实集成尚未交付。冻结/淘汰提供新增业务阻断API，后续业务入口必须调用并再次验收。等保、生产密钥轮换、审计长期保存、性能压测和信创适配需要独立验证。

需求基线在 `docs/requirements/`，排期见 [DEVELOPMENT_PLAN](docs/DEVELOPMENT_PLAN.md)，跨阶段未完成AC保持PARTIAL。

用户电脑本地验收：[启动说明](deploy/review/README.md) · [入口修复与实测](docs/review/README.md)。先完整解压验收包并启动，再打开本机18080入口；不是云环境预览地址。

无法下载构件时可从源码生成本地验收包：在WSL、Docker可访问的前提下执行 `bash scripts/build-local-review.sh`，然后执行 `bash artifacts/SRM-local-review/start.sh`。镜像和Maven/npm依赖首次下载需联网，宿主机仅需Git、Python3和Docker；完整交接见 docs/review/LOCAL_CODEX_HANDOFF.md。

## 阶段代码同步

按用户要求，每个阶段完成验证后，及时提交源码、验证证据和验收文档，并推送至GitHub交付分支 `codex/srm-local-delivery-20261027`。数据库备份、运行凭证、真实密码、私有附件及构建产物不上传。实现状态和真实联调／业务签收状态分开记录。
