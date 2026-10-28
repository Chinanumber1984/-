# W12–15 本地寻源操作

入口为 http://localhost:18080/ ，原有登录账号和密码文件不变。内部打开“寻源与竞价”；采购经理或 SRC-SPE 可建立询价，SQE 可查看及登记风险。门户 ADMIN/QUOTE 才能报价，SHIP 等角色不能访问。

1. 新建询价，选择同组织、同品类的合格供应商（1–50家），填写物料、数量、交期、币种及截止时间。候选列表支持按名称或编号搜索。保存草稿后可上传私有 PDF/PNG/JPEG 技术规格；上传前选择“规格所属物料”，上传后保存绑定关系，再发布邀请。隐藏的基准价会在普通草稿编辑中保留。
2. 比价策略：供应商在门户填含税单价、交期和运费，提交后新增轮次。截止前可继续报价，旧轮次保留。采购查看比价、绩效与风险并可导出 Excel。默认隐藏内部单价，只有单独的 `/srm/fields/unit-price` 权限可查看。
3. 竞价策略：设开始/截止时间、起始篮子总价、最小降幅与末段顺延分钟。页面显示匿名排名与本方价。已发送但未确认时先“查询原请求回执”，可重发同一请求。缓存缺失或版本不匹配暂停新增竞价，由采购“按已保存记录恢复竞价”；不会清空报价。连接会自动换取新票据重连。
4. 截止后系统自动封盘，有有效报价才可提交定点；无出价显示流标。提交推荐供方和理由；必填资质缺失或过期会阻断提交、批准和发布。无绩效历史须填写例外依据。经理在本页待办审核快照，明确批准无历史例外及高/未知风险；禁止申请人自审。可以驳回、转办其他有权限经理或更换候选并重走审批。
5. 报价、绩效、风险依据变化会阻断审批/发布，保留待办；申请人撤回后重新提交。经理批准后由采购发布。门户只收到本方中选/落选结果；落选意见是否披露在定点申请中配置。

风险输入为带证据编号的人工评估；外部风险源尚未接入。没有评估显示 UNKNOWN。仅有部分月份绩效时显示实际有效月数，不补造历史。

## 升级与恢复

执行 `SRM_REVIEW_OUTPUT=<新目录> bash scripts/build-local-review.sh`，再 `bash scripts/upgrade-local-review.sh <新目录>`。升级脚本校验发布包与源迁移校验和，备份数据库、应用和 runtime 后执行加法迁移。原数据库卷、文件卷、账号及密钥不替换。备份在 `.runtime/upgrades/`，属于私有数据，不能提交 GitHub。不要执行 `docker compose down -v`。

应用失败会恢复上一版文件并保留数据库。数据库故障期间，出价没有成功回执时必须先查询原请求，不能假定成交。数据库恢复后，授权采购可按持久记录恢复竞价缓存；Redis 不是报价事实来源。

## 测试

可串行运行 `bash scripts/verify-sourcing-review.sh`（测试账号按单设备策略登录，不要并行运行共享账号脚本）。单项包括 `node scripts/test-sourcing-api.mjs`、`node scripts/test-sourcing-faults.mjs`、`node scripts/test-sourcing-load.mjs`、`PLAYWRIGHT_BROWSERS_PATH=/tmp/srm-playwright /tmp/srm-browser-venv/bin/python3 scripts/test-sourcing-browser.py`。

WebSocket 测试依赖 `/tmp/srm-sourcing-test` 中的 `ws@8.18.3`，可用 `npm install --cache /tmp/srm-npm-test-cache --prefix /tmp/srm-sourcing-test --no-audit --no-fund ws@8.18.3` 安装。测试通过真实本地网关创建独立测试数据，保留数据，不删除已有业务数据。负载测试50家合成供方的资质状态仅在受约束的测试初始化 SQL 中设置，不构成用户真实准入验收；W3–9的真实准入流程仍单独回归。
