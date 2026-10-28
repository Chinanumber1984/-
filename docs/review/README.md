# 本地验收入口修复

原问题：云端回环服务运行正常，但当前云环境没有公开预览/端口转发能力，用户电脑不能访问云端localhost。此前只验证云内服务不足以交付访问入口。用户现选择在自己电脑运行。

已提供独立Docker本地验收包：已编译前后端、MySQL8/Redis7、私有文件卷、单一访问入口、随机本机账号/密钥、自动准备五类流程，附Windows与macOS/Linux启动脚本。只发布本机18080端口，不公开数据库和后端。与云端开发库相互独立。

下载包：artifacts/SRM-local-review.zip；SHA256旁文件。启动说明见deploy/review/README.md。用户必须完整解压并在自己的电脑启动，之后在同一电脑打开localhost:18080；这不是云端公开网址。

scripts/test-review-package.py通过实际打包目录启动的服务，在同一个入口验证13项行为（包括首次建库中文品类名称），包括随机账号登录、真实建档、私有附件上传、Flowable两级审批、切换账号后及刷新后的合格状态，以及未登录、异常Host和私有文件访问拒绝。证据见verification.json及workspace/本地验收包实测.png。Windows/macOS宿主脚本未在本机执行，不宣称已验证用户电脑的Docker安装或网络。

重复启动已保留既有账号、密钥和数据库；包内不包含runtime、云端凭证或测试数据。镜像固定为本次使用的官方digest。新增gateway只提供构建产物与固定私有后端反向代理，不提供源码/任意路径文件服务。

重新生成时不要删除已使用验收包的runtime或数据库卷。scripts/package-review.py会保留既有目录并拒绝覆盖，需要保留旧包后另行准备新版本。

**2026-10-27用户反馈初步验证无大问题并明确批准开始W10–11。绩效前置首版已按此授权部署，见 ../performance/README.md。**

## GitHub源码交付

聊天构件下载不可用，已改为在 Chinanumber1984/- 的 codex/srm-local-delivery-20261027 分支交付真实源码。`scripts/build-local-review.sh` 可从克隆后的源码生成可启动验收包。干净副本的容器Maven构建（17项Java测试）、前端npm ci/构建、独立环境启动及13项浏览器检查均通过；验证使用已有依赖缓存，不冒称冷网络首次下载或用户Windows/WSL已经验证。证据见 source-build-verification.json。

## W23–24本地总体验收准备

P8隔离恢复、四账号浏览器/API、迁移哈希、10k列表性能和Redis/后端重启证据在 `P8-*-verification.json`。业务签收矩阵和本地试点手册见 [pilot](../pilot/README.md)。共114个编号、84项标记已实现、30项PARTIAL；114项当前均待业务逐项按PRD特异AC和G1–G6验收。P8自动验证不关闭需求或替代业务签字。
