# 本地 Codex 交接：WSL Ubuntu / Windows

## 用户目标与开始条件

用户要求由本地Codex实际完成部署与验证，而不是仅提供教程。当前环境是Windows的WSL Ubuntu，Docker/Compose已安装；此前Docker socket权限被拒绝。W10–11必须等用户明确确认验收完成后才开始，当前禁止自动进入。

## 代码与部署包的位置

云端源码原位置为 `/workspace/-`，这个路径不属于用户电脑，不能直接拿它当作WSL路径。源仓库身份为 `Chinanumber1984/-`，代码交付改为GitHub独立分支 codex/srm-local-delivery-20261027；以 git ls-remote 确认该分支实际存在后再拉取，不使用空的main分支。

用户下载的 `SRM-local-review.zip` 是已编译的真实前后端部署包；`SRM-project.zip` 是完整开发源码，排除了运行凭证、数据库备份、依赖缓存与构建目录。

请先在用户指定目录 `/mnt/d/SRM`、`/mnt/d/srm系统` 和 Windows Downloads 目录中有针对性地查找这两个ZIP。只查找交付文件，不读取无关个人文件。找到后将验收包解压到 `/mnt/d/SRM/SRM-local-review`，源码解压到 `/mnt/d/SRM/SRM-project`。不要覆盖用户已有文件、runtime、密钥或数据库。

如果ZIP尚未下载，明确告诉用户缺少哪个文件；不要把需求文档当成部署代码，也不要声称空GitHub仓库包含实现。

## Docker权限

先执行 `id`、`stat /var/run/docker.sock`、`docker info`。如果仍被拒绝，不能通过chmod 666或绕过环境权限解决。需要用户在本地Ubuntu终端亲自授权Docker组，然后从具备新组权限的终端重新进入Codex。Windows Docker Desktop场景还要确认已启用该Ubuntu的WSL Integration。

## 部署与检查

拿到Docker权限后，在解压后的验收包根目录执行 `bash start.sh`。镜像固定digest，脚本生成本机独立账号和SM4/HMAC密钥；不会导入云端凭证或清空旧数据。包内真实MySQL8、Redis7、Java17后端、构建后前端和五类Flowable流程，附件用私有本地Docker卷。

当前电脑入口为 `http://localhost:18080`。这是在用户电脑启动后才成立的地址，不是云端公开网址。默认只绑定本机，数据库、Redis和后端没有直接对外端口。账号首次启动后保存在验收包 `runtime/登录账号.txt`，不要把密码、密钥或账号文件内容复制到聊天或日志。

必须用实际入口验证页面与登录、建档、联系人、有效资质上传、SQE和经理两级审批、刷新后合格状态、中文品类名称；服务端health或PID不能代替浏览器验收。若本地执行环境没有浏览器能力，应明确区分curl/API检查与浏览器未验证，不要冒称通过。

确认Windows侧也能访问；必要时检查WSL localhost转发、防火墙和端口占用，不要直接关闭系统防火墙。停止使用 `bash stop.sh`，不要执行删除数据卷的命令。

## 已有验证与实际边界

云端用独立Linux Docker环境对该验收包通过13项浏览器检查，另验证实际停止/启动后业务行、账号、密钥和附件SHA256不变；修正了首次初始化SQL中文字符集问题。证据见 `docs/review/verification.json`。这不等同已在用户Windows/WSL中部署成功，需要本地重新验证。

W3–6与W7–9首版范围详见 docs/workspace、docs/lifecycle 和 docs/backlog.csv。绩效等后续领域尚未实现；外部Webhook未配置；不是生产部署或等保验收。源码中的 scripts/install.sh 等云开发安装脚本包含云工作区路径，当前本地验收优先用独立预编译包 start.sh，不要直接将云脚本套到WSL。

## 无需下载构件的源码部署

用户的聊天构件下载不可用时，直接拉取交付分支。在WSL的Linux目录（例如 ~/projects/srm）工作，优先于/mnt/d，以避免依赖安装的性能和权限问题。取得Docker访问权限后执行 `bash scripts/build-local-review.sh`，此脚本在临时目录中用官方Maven/Node容器构建真实后端及前端，不修改用户原有node_modules或运行数据。然后执行 `bash artifacts/SRM-local-review/start.sh`。首次需联网下载镜像和依赖。若存在旧验收包，保留并启动它，不覆盖runtime。
