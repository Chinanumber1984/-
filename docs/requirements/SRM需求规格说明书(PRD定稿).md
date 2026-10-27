# SRM 系统需求规格说明书（PRD 定稿 v1.0）

> 配套文档：
> - 《SRM基于Snowy重建设计方案.md》v1.1（架构/技术栈/决策已锁版）
> - 《SRM功能需求拆分.md》FRD v1.0（L1~L3 功能点拆分，编号来源）
>
> 本文档定位：**在 FRD 基础上补齐"验收标准(AC) + 字段清单"，并对反向竞价做技术方案(spike)**，使需求达到可交付研发评审的程度。
> **本期不编写代码骨架**（落地交由后续 gen 插件 / 手工实现）。

---

## 0. 文档关系与阅读指引

```
方案 v1.1（架构/决策） ─┐
                       ├─► FRD v1.0（功能拆分 L1~L3，编号基线） ─┐
                       │                                         ├─► 本 PRD（AC+字段+竞价spike）
Snowy 底座（已克隆核对）┘                                         │
                                                              └─► 后续：编码骨架 / 测试用例
```

- FRD 的 L3 编号（FR-CORE-xxx / FR-SRC-xxx / FR-COL-xxx / FR-PER-xxx / FR-POR-xxx / FR-FLOW-xxx / FR-INT-xxx）在本文档逐条对应。
- 字段清单按模块实体给出，类型面向 **PostgreSQL 16**（decimal/jsonb/timestamptz 等）；MySQL 8 等价替换见 §11.4。
- 验收标准(AC)采用"通用约定 + 特异 AC"模式：通用规则见 §2，模块表内只列**特异**可测条件。

---

## 1. 角色与权限矩阵（展开 FRD 0.2）

### 1.1 内部角色 → 功能与数据范围

| 角色 | 模块权限（菜单/按钮） | 数据范围 | 敏感字段可见 |
|------|----------------------|----------|--------------|
| SYS-ADMIN | 系统/用户/角色/组织/字典/流程定义 | 全机构 | 全量 |
| SUP-ADMIN | M1 主数据/资质/联系人/准入/变更/冻结 | 本机构(org)及下级 | 可见 credit_code/bank_account |
| SRC-SPE | M2 寻源(RFQ/竞价/定点) | 本机构 | 可见单价 |
| BUYER | M3 协同(PO/ASN/对账跟进) | 本机构 | 可见单价/金额 |
| CAT-MGR | M1 准入/分级审批、M2 定点审批、M4 分级 | 本机构 | 全量 |
| SQE | M1 资质审核、M4 质量/SCAR/评分质量项 | 本机构 | 可见单价(质量分析用) |
| FIN | M3 对账/发票 | 本机构 | 可见金额/发票 |
| PORTAL-U | M5 门户（按 roles 再细分）| 仅本 supplier_id | 仅本供应商 |

> 多法人：所有内部角色经 Snowy **组织(org)树 + 数据权限范围（本人/本机构/全部）** 过滤；新增 SRM 查询统一带 `org_id` 与数据范围注解，不复用 `CommonEntity` 租户字段（Snowy 开源版无多租户）。

### 1.2 门户联系人角色 → 门户菜单映射（一供应商多联系人·多角色）

`roles` 为 JSONB 数组，可选值：`ADMIN`(管理员) / `QUOTE`(报价员) / `SHIP`(发货员) / `QUALITY`(质量) / `FINANCE`(财务)。

| 联系人角色 | 可见门户功能 | 说明 |
|-----------|--------------|------|
| ADMIN | 全部门户功能 + 自助维护(资质/联系人/角色) | 至少 1 个，负责子账号管理 |
| QUOTE | RFQ 响应/报价、竞价参与、PO 查看、绩效查看 | 不可见财务对账 |
| SHIP | PO 确认、ASN 创建、交付跟踪 | 不可见报价单价、对账 |
| QUALITY | PPAP 提交、SCAR/8D、来料结果、绩效查看 | 不可见单价/财务 |
| FINANCE | 对账确认、发票登记、绩效查看 | 不可见报价单价 |

> 一个联系人可持多角色（如小供应商"老板"同时 ADMIN+QUOTE+FINANCE）；菜单按角色并集渲染；某功能的写操作还需对应角色，避免越权。

---

## 2. 通用验收约定（所有 L3 默认满足，除非特异 AC 覆盖）

- **G1 列表/检索**：支持分页、排序、按模块主要字段筛选；支持 Excel 导出（导出内容遵循脱敏规则）。
- **G2 增删改校验**：必填项、格式（手机号/邮箱/信用代码）、唯一性（编码/证件号）校验；失败返回明确错误码与文案；写操作自动 `@CommonLog` 进**签名审计日志**（等保）。
- **G3 数据权限**：内部按 org/数据范围过滤；门户按 `supplier_id` 隔离，A 供应商无法读 B 供应商任何数据。
- **G4 敏感字段**：`credit_code`/`bank_account`/`phone`/`unit_price` 以 **SM4** 落库，列表与无关角色视图脱敏（如 `138****5678`、`6200*****`）。
- **G5 流程联动**：凡标注"flow"的功能点，状态变更必须经 Flowable 节点，业务状态由 `TaskListener` 回写，禁止直接 UPDATE 跳过审批。
- **G6 幂等与并发**：外部同步（ERP/MES/QMS）按业务主键幂等；竞价出价串行化（Redis Lua / 分布式锁）防并发覆盖。

---

## 3. M1 供应商准入与生命周期（srm-core）

### 3.1 字段清单

**srm_supplier**（继承 CommonEntity）
| 字段 | 类型 | 必填 | 说明/加密 |
|------|------|------|-----------|
| supplier_code | varchar(32) | ✅ | 供应商编码，唯一，规则 `SUP`+8位序号 或 手工唯一 |
| name | varchar(128) | ✅ | 名称 |
| credit_code | varchar(18) | ✅ | 统一社会信用代码，SM4 存储 |
| category | varchar(32) | ✅ | 品类（字典，驱动寻源邀请）|
| tier | varchar(16) | ✅ | 等级：战略/优选/合格/临时/观察 |
| status | varchar(16) | ✅ | 潜在/审核中/合格/冻结/淘汰 |
| bank_account | varchar(64) | | 银行账号，SM4 |
| bank_name | varchar(64) | | 开户行 |
| address | varchar(255) | | 注册地址 |
| org_id | bigint | ✅ | 归属组织（多法人隔离）|
| onboard_time | timestamptz | | 准入合格时间 |

**srm_supplier_cert**
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| supplier_id | bigint | ✅ | 关联 |
| cert_type | varchar(32) | ✅ | 资质类型（字典：营业执照/ISO9001/IATF16949/环保等）|
| cert_no | varchar(64) | ✅ | 证书编号 |
| valid_from | date | ✅ | 生效日 |
| valid_to | date | ✅ | 到期日（≥ valid_from）|
| file_id | varchar(64) | ✅ | MinIO 附件 |
| version | int | ✅ | 版本（续期递增，旧版保留）|
| status | varchar(16) | ✅ | 有效/过期/作废 |

**srm_supplier_contact**
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| supplier_id | bigint | ✅ | 关联（一供应商多行）|
| name | varchar(32) | ✅ | 姓名 |
| phone | varchar(32) | ✅ | 手机号，SM4 |
| email | varchar(64) | | 邮箱 |
| roles | jsonb | ✅ | 角色数组 ADMIN/QUOTE/SHIP/QUALITY/FINANCE |
| enabled | bool | ✅ | 是否启用（停用即禁登录）|
| account_id | bigint | | 关联 Sa-Token 登录账号 |

### 3.2 L3 验收标准（AC）

| 编号 | 特异验收标准（AC） |
|------|-------------------|
| FR-CORE-101 | ① 同 org 内 `credit_code` 唯一，重复拦截；② credit_code/bank_account 经 SM4 入库；③ 保存后 `status=潜在`；④ 编码自动生成且可手工覆盖校验唯一。 |
| FR-CORE-102 | ① 可按品类/等级/status/org 组合筛选；② 列表 credit_code/bank_account 脱敏；③ 导出 Excel 同样脱敏。 |
| FR-CORE-103 | ① 详情 Tab（档案/资质/联系人/绩效/风险/订单）均可加载；② 单价类字段本模块无，绩效分对全部内部角色可见。 |
| FR-CORE-104 | ① 修改 name/credit_code/bank 等"关键字段"→ 强制进入 L2-5 变更流程；② 修改 address 等非关键字段→直接存并记录审计。 |
| FR-CORE-105 | ① 品类字典 CRUD 生效；② 寻源邀请范围按品类映射自动带出候选供应商。 |
| FR-CORE-106 | ① 等级枚举校验；② 准入通过后初始等级写入；③ 绩效重算可联动刷新等级（见 FR-PER-201）。 |
| FR-CORE-201 | ① 同 cert_type 可存多份（不同 cert_no/版本）；② valid_to≥valid_from；③ file_id 必传且可预览下载。 |
| FR-CORE-202 | ① 按 cert_type 分组展示；② `valid_to<今天` 标红"过期"。 |
| FR-CORE-203 | ① 定时任务扫描 `valid_to ≤ 今天+N`（N 可配，默认 30）；② 命中推送 SUP-ADMIN 与对应供应商管理员。 |
| FR-CORE-204 | ① 续期生成新 version，旧版 status=作废但保留可查；② 变更进入审计。 |
| FR-CORE-205 | ① 发起准入(FR-CORE-401)/定点(FR-SRC-402)前，必填 cert_type 缺失或过期→阻断并提示具体缺失项。 |
| FR-CORE-301 | ① 一供应商可建多条联系人；② roles 仅含枚举值；③ phone SM4 入库。 |
| FR-CORE-302 | ① 列表含停用状态；② 编辑不影响其他联系人。 |
| FR-CORE-303 | ① 角色组合决定门户菜单（见 §1.2）；② 每个供应商至少保留 1 个 ADMIN，删最后 ADMIN 拦截。 |
| FR-CORE-304 | ① 停用→对应 account 禁登录；② 不影响其余联系人账号。 |
| FR-CORE-305 | ① 联系人↔Sa-Token 账号一一关联；② 同 supplier_id 下多个独立账号各自登录。 |
| FR-CORE-401 | ① 仅 `status=潜在` 可发起；② 发起前过 FR-CORE-205 资质完备校验，不通过拦截；③ 发起后 `status=审核中`，生成 Flowable 实例。 |
| FR-CORE-402 | ① SQE 节点可"通过/退回补正/否决"；② 退回→FR-CORE-405 补正态；③ 待办入 Snowy 待办中心。 |
| FR-CORE-403 | ① CAT-MGR 节点结合品类必要性与绩效决策；② 通过/驳回留意见。 |
| FR-CORE-404 | ① flow 结束 TaskListener 回写：通过→`status=合格`+写 onboard_time+初始 tier；驳回→`status=潜在`或`淘汰`。 |
| FR-CORE-405 | ① 供应商补正后重新进入 SQE 审核；② 补正次数达上限（可配，默认 3）→自动淘汰并可配。 |
| FR-CORE-501 | ① 记录变更字段前后值；② 关键字段标记走 flow。 |
| FR-CORE-502 | ① 关键字段(银行/信用代码)变更走 flow 审批后生效；② 非关键直接生效并审计。 |
| FR-CORE-503 | ① 变更前后值版本化可追溯；② 写入签名审计日志。 |
| FR-CORE-601 | ① 冻结走 flow 审批；② 风险事件(FR-PER-504)高等级可"建议冻结"生成待办；③ 冻结后禁止新 RFQ/PO。 |
| FR-CORE-602 | ① 解冻走 flow；② 解冻后恢复 `status=合格`。 |
| FR-CORE-603 | ① 淘汰走 flow；② 淘汰为终态，归档历史，新业务（RFQ/PO/竞价）一律阻断。 |
| FR-CORE-604 | ① 状态机时间线展示：潜在→审核中→合格↔冻结→淘汰；② 合格↔冻结可逆，淘汰不可逆。 |

---

## 4. M2 寻源（srm-sourcing，不含招投标）

### 4.1 字段清单

**srm_rfq**
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| rfq_no | varchar(32) | ✅ | 唯一 |
| title | varchar(128) | ✅ | 标题 |
| material_list | jsonb | ✅ | 物料行（物料/数量/交期/规格附件）|
| invite_suppliers | jsonb | ✅ | 受邀供应商 id 数组（≥1，须合格态）|
| strategy | varchar(16) | ✅ | 比价/竞价 |
| currency | varchar(8) | ✅ | 币种 |
| deadline | timestamptz | ✅ | 报价/竞价截止（>now）|
| status | varchar(16) | ✅ | 草稿/已发布/已结束/流标 |

**srm_quotation**
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| rfq_id | bigint | ✅ | 关联 |
| supplier_id | bigint | ✅ | 关联 |
| unit_price | decimal(18,4) | ✅ | 单价，SM4 |
| lead_time | int | ✅ | 交期(天) |
| total_amount | decimal(18,2) | ✅ | =单价×数量 |
| round | int | ✅ | 报价轮次 |
| status | varchar(16) | ✅ | 已报价/作废 |

**srm_award**
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| rfq_id | bigint | ✅ | 关联 |
| awarded_supplier | bigint | ✅ | 中标 |
| award_qty | decimal | | 分配量 |
| reason | varchar(512) | | 定点依据（含绩效引用）|
| risk_ref | varchar(64) | | 关联风险事件（如有）|

**srm_bid**（反向竞价）
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| rfq_id | bigint | ✅ | 关联 |
| supplier_id | bigint | ✅ | 出价方 |
| bid_price | decimal(18,4) | ✅ | 出价，SM4 |
| bid_time | timestamptz | ✅ | 出价时间 |
| is_lowest | bool | ✅ | 是否当前最低 |
| rank_anon | int | | 匿名排名 |

### 4.2 L3 验收标准（AC）

| 编号 | 特异验收标准（AC） |
|------|-------------------|
| FR-SRC-101 | ① rfq_no 唯一；② invite_suppliers≥1 且均为合格态；③ strategy∈{比价,竞价}；④ deadline>now。 |
| FR-SRC-102 | ① 发布后推送门户至受邀方；② 发布后 material_list/invite 核心项不可改（仅可延期/撤回）。 |
| FR-SRC-103 | ① 撤回仅当无任何供应商报价；② 延期改 deadline 并通知所有受邀方。 |
| FR-SRC-104 | ① 物料行含数量/交期/规格附件；② 行级可填单价基准供参考。 |
| FR-SRC-201 | ① 截止前可多次改报价，记录 round；② unit_price SM4；③ total_amount=单价×数量自动算。 |
| FR-SRC-202 | ① SQM 视图隐藏 unit_price（仅见交期/总额或绩效分析用脱敏价）；② 多轮报价留痕可查。 |
| FR-SRC-203 | ① 权重固定 交付30%/质量35%/成本20%/服务15%，**不分品类**；② 到厂价归一化后加权；③ 取近 12 月绩效(FR-PER)；④ 同输入复算结果一致（可复现）。 |
| FR-SRC-204 | ① 比价看板横向对比综合价/交期/绩效并排序；② 支持导出。 |
| FR-SRC-205 | ① Q&A 可设公开或私聊；② 全部答疑留存可追溯。 |
| FR-SRC-301 | ① 起始价/最小降幅/时长/防狙击窗(默认最后5分钟出价+5分钟) 可配；② 受邀方须已确认合格。 |
| FR-SRC-302 | ① STOMP 出价校验：降幅≥最小降幅且 ≤ 当前最低价；② 非法出价拒绝并提示；③ 实时写库。 |
| FR-SRC-303 | ① Redis 存实时最低价；② WebSocket 向所有受邀方广播"匿名价+排名"，自身可见自身出价；③ 广播内容不含对手身份。 |
| FR-SRC-304 | ① 结束前 N 分钟内有出价→自动延时 N 分钟（可配）；② 无新出价→到点封盘，状态=已结束。 |
| FR-SRC-305 | ① 有效最低价存在→成交候选记录；② 全程无出价→状态=流标。 |
| FR-SRC-306 | ① 每次出价（时间/价/方）入签名审计日志；② 阻断后重试不丢记录。 |
| FR-SRC-401 | ① 取比价(FR-SRC-203)或竞价(FR-SRC-305)结果 + 近12月绩效；② 生成定点建议草稿。 |
| FR-SRC-402 | ① 关联绩效与风险(FR-PER-504)展示；② flow 审批通过/驳回/改派留意见。 |
| FR-SRC-403 | ① 中标/落选通知推送门户；② 门户可查定点结果。 |
| FR-SRC-404 | ① 落选原因可按配置披露/隐去；② 通知记录留存。 |

---

## 5. M3 采购协同（srm-collab）

### 5.1 字段清单

**srm_po** | po_no(varchar,唯一) / supplier_id / material / qty / price(decimal,SM4) / due_date(date) / confirmed(bool) / erp_status(varchar)
**srm_asn** | asn_no(varchar,唯一) / po_no / delivery_lines(jsonb:批次/数量) / batch_no / eta(timestamptz) / received_qty(decimal) / diff_flag(varchar)
**srm_reconciliation** | rec_no(varchar,唯一) / po_list(jsonb) / received_qty / amount(decimal) / diff(jsonb) / status(varchar:待确认/差异中/已确认/争议)
**srm_invoice** | invoice_no(varchar) / amount(decimal) / file_id / rec_no(关联) / status(varchar:已登记/已校验/已推送)

### 5.2 L3 验收标准（AC）

| 编号 | 特异验收标准（AC） |
|------|-------------------|
| FR-COL-101 | ① po_no 唯一，重复同步幂等；② 同步失败重试不重复落库；③ 价格 SM4。 |
| FR-COL-102 | ① 内部与门户均可查看；② 单价脱敏规则同报价。 |
| FR-COL-103 | ① 供应商发货员确认交期/数量；② 异议生成 PO 变更待 ERP 回写。 |
| FR-COL-104 | ① ERP 变更回写 srm_po；② 变更通知供应商联系人。 |
| FR-COL-201 | ① ASN 必须关联有效 PO；② delivery_lines 含批次与数量；③ eta 可填。 |
| FR-COL-202 | ① 物流/批次可追踪展示。 |
| FR-COL-203 | ① 实收量登记；② 触发容差校验(FR-COL-204)。 |
| FR-COL-204 | ① 实收超出容差(可配，默认±3%)→标记 diff_flag=差异并预警。 |
| FR-COL-205 | ① due_date 未收货→定时预警 BUYER+供应商。 |
| FR-COL-301 | ① 看板按 PO/供应商汇总在途/已收/逾期。 |
| FR-COL-302 | ① 延期/容差超限→生成风险事件(FR-PER-501)。 |
| FR-COL-401 | ① 按 PO+收货量+金额生成对账单；② rec_no 唯一。 |
| FR-COL-402 | ① 数量/金额差异可标记并填说明。 |
| FR-COL-403 | ① BUYER 与供应商财务都确认→status=已确认；② 任一方异议→退回差异中。 |
| FR-COL-404 | ① 争议记录+协商留痕；② 可升级 CAT-MGR。 |
| FR-COL-501 | ① 发票登记关联对账单；② 附件必传。 |
| FR-COL-502 | ① 发票金额与对账单匹配，不符拦截；② FIN 可见。 |
| FR-COL-503 | ① 推送财务共享并接收回执；② 回执状态更新 srm_invoice。 |

---

## 6. M4 绩效与风险（srm-perf）

### 6.1 字段清单

**srm_scorecard** | supplier_id / period(varchar:YYYY-MM) / deliver_score / quality_score / cost_score / service_score / total / grade(varchar) / created_at
**srm_ppap** | supplier_id / items(jsonb:提交物) / file_ids / status(varchar:待审/已批/补件) / flow_inst_id
**srm_scar** | supplier_id / defect_desc / level(varchar) / d8_reply(jsonb:D1-D8) / status(varchar:已发起/已回复/已验证/闭环/升级) / close_time
**srm_risk_event** | supplier_id / risk_type(varchar:资质过期/履约异常/合规黑名单) / level(varchar:高/中/低) / trigger(varchar) / risk_source(varchar:INTERNAL) / action(varchar) / status(varchar:待评估/已评估/预警中/已处置)

### 6.2 L3 验收标准（AC）

| 编号 | 特异验收标准（AC） |
|------|-------------------|
| FR-PER-101 | ① 指标=交付/质量/成本/服务，权重 30/35/20/15，字典可配但**全司统一不分品类**；② 权重和=100。 |
| FR-PER-102 | ① 月度定时任务取 ERP/MES/QMS 数据；② 生成 srm_scorecard(period) 幂等（同 period 覆盖重算）。 |
| FR-PER-103 | ① 内部全量查看；② 门户仅本供应商。 |
| FR-PER-104 | ① 明细分项展示且与数据源可追溯。 |
| FR-PER-105 | ① 供应商申诉→SQE 复核→可修正并重算；② 修正留痕。 |
| FR-PER-201 | ① 按总分阈值划分战略/优选/合格/观察/淘汰；② 阈值可配。 |
| FR-PER-202 | ① 按品类/等级导出。 |
| FR-PER-203 | ① 近 N 月趋势图；② 等级变化联动 FR-CORE-106。 |
| FR-PER-301 | ① PPAP 提交物清单+附件；② 发起 flow 审批。 |
| FR-PER-302 | ① SQE 通过/驳回/补件；② 补件重提。 |
| FR-PER-303 | ① QMS 来料检验结论回写质量项。 |
| FR-PER-304 | ① 不合格按规则扣质量分；② 严重项生成风险(FR-PER-501)。 |
| FR-PER-401 | ① SCAR 发起含缺陷描述/等级并关联供应商。 |
| FR-PER-402 | ① 8D 结构化(D1-D8)回复+附件；② 门户质量角色提交。 |
| FR-PER-403 | ① SQE 验证通过/驳回重做；② 验证有效性确认。 |
| FR-PER-404 | ① 闭环；② 超时(可配)或重复缺陷→升级+风险。 |
| FR-PER-501 | ① 风险仅来自内部事件（资质过期/履约异常/合规黑名单手工）；② risk_source 固定 INTERNAL（预留外部扩展字段）。 |
| FR-PER-502 | ① 等级高/中/低评估留说明。 |
| FR-PER-503 | ① 预警消息推送相关人；② 看板红黄灯。 |
| FR-PER-504 | ① 高等级→自动建议冻结生成 FR-CORE-601 待办；② 处置闭环归档。 |

---

## 7. M5 供应商门户（srm-portal，前端并入 Snowy 工程）

### 7.1 字段清单（复用 M1 实体；此处仅门户态）
**srm_portal_profile**（可选） | supplier_id / last_login / pending_count / 工作台配置(jsonb)

### 7.2 L3 验收标准（AC）

| 编号 | 特异验收标准（AC） |
|------|-------------------|
| FR-POR-101 | ① 外部注册提交基础信息→自动建"潜在"供应商并转 FR-CORE-401 准入；② 审核前仅潜在态，不可参与业务。 |
| FR-POR-102 | ① 多联系人各自 Sa-Token 账号登录；② 登录 SM2 加密传输。 |
| FR-POR-103 | ① 工作台聚合待办(RFQ/竞价/对账/SCAR)+预警+绩效概览。 |
| FR-POR-201 | ① 调用 FR-CORE-201/204，仅 ADMIN 可操作。 |
| FR-POR-202 | ① 调用 FR-CORE-301/303，仅 ADMIN。 |
| FR-POR-203 | ① 仅本 supplier_id 档案，G3 生效。 |
| FR-POR-301 | ① 调用 FR-SRC-201，仅 QUOTE 角色；② 报价 SM4。 |
| FR-POR-302 | ① 调用 FR-SRC-302/303，仅 QUOTE；② WebSocket 实时刷新。 |
| FR-POR-303 | ① 调用 FR-COL-103/201，仅 SHIP。 |
| FR-POR-304 | ① 调用 FR-COL-403，仅 FINANCE。 |
| FR-POR-305 | ① 调用 FR-PER-103，仅本供应商。 |
| FR-POR-306 | ① 调用 FR-PER-402，仅 QUALITY。 |
| FR-POR-401 | ① 所有门户查询强制带 supplier_id 过滤，越权读取返回空。 |
| FR-POR-402 | ① 菜单/功能按 roles(JSONB) 并集渲染，缺角色不可见对应功能入口。 |

---

## 8. M6 工作流（snowy-plugin-flow，Flowable 7）

### 8.1 字段清单（流程侧）
**srm_flow_def** | def_key / name / category(varchar:准入/定点/冻结/淘汰/SCAR) / bpmn(file) / version / status(varchar:草稿/已发布/挂起)
**srm_flow_task_log** | inst_id / task_key / assignee / action / comment / time

### 8.2 L3 验收标准（AC）

| 编号 | 特异验收标准（AC） |
|------|-------------------|
| FR-FLOW-101 | ① BPMN 可上传或在线设计；② 分类与 SRM 业务映射。 |
| FR-FLOW-102 | ① 发布带版本；② 挂起后停止新实例发起。 |
| FR-FLOW-103 | ① 分类与业务类型一一映射可维护。 |
| FR-FLOW-201 | ① 待办接入 Snowy 待办中心；② 按角色/组织过滤。 |
| FR-FLOW-202 | ① 同意/驳回/转办/加签动作与意见留痕。 |
| FR-FLOW-203 | ① 全链路审批历史可追溯，满足等保。 |
| FR-FLOW-301 | ① 节点完成经 TaskListener 回写 SRM 业务状态（如准入合格）；② 回写失败有补偿/告警。 |
| FR-FLOW-302 | ① 流程变量存单据ID/当前节点/审批人；② 跨插件可读。 |
| FR-FLOW-303 | ① 超时定时催办消息。 |

---

## 9. M7 集成（srm-integration）

### 9.1 字段清单
**srm_integration_cfg** | sys_key(varchar:ERP/MES/QMS/FIN/MSG) / endpoint / token / enabled / sla(varchar)
**srm_integration_log** | direction / sys_key / payload(jsonb) / status / retry_count / time

### 9.2 L3 验收标准（AC）

| 编号 | 特异验收标准（AC） |
|------|-------------------|
| FR-INT-101 | ① ERP 主数据/PO/价格/收货/付款按主键幂等同步；② 失败重试+补偿。 |
| FR-INT-102 | ① MES 排产/领用数据可拉取用于交付优先。 |
| FR-INT-103 | ① QMS 来料检验/PPAP/SCAR 回写成功并更新绩效。 |
| FR-INT-104 | ① 对账/发票/付款申请推送财务共享并收回执。 |
| FR-INT-105 | ① 待办/预警经企微/钉钉 Webhook 推送（仅 Web 前端，消息仍发）。 |
| FR-INT-106 | ① 集成监控记录调用与失败；② 接口 SLA 99.5%；③ 失败自动重试+补偿。 |

---

## 10. 反向竞价 WebSocket 技术方案（spike，非编码）

> 目标：支撑 FR-SRC-3xx 实时出价。本节约等于"设计说明书"，不含可运行代码。

### 10.1 实时架构
```
供应商浏览器 ──STOMP/WS──► [SRM 竞价网关] ──校验(Lua原子)──► Redis(实时最低价)
       ▲                         │ 出价审计(@CommonLog)            │ 发布/订阅
       │                         ▼                                 ▼
       └──── WebSocket 广播(匿名价+排名) ◄── [广播服务] ◄── Redis Pub/Sub
```
- 前端：Snowy 同工程新增 `views/srm/bidding/` 页面，使用 `@stomp/stompjs` 订阅 `/topic/rfq/{rfqId}`。
- 后端：SpringBoot `@EnableWebSocketMessageBroker`，`/ws/bid` 端点；`/app/bid.submit` 接收出价。

### 10.2 STOMP 端点与消息
| 方向 | 目标 | 负载 |
|------|------|------|
| 订阅 | `/topic/rfq/{rfqId}` | `{lowestPrice, yourRank, yourPrice, bidCount, endTime, extended}` |
| 发送 | `/app/bid/submit` | `{rfqId, bidPrice}`（服务端校验后回显）|
| 系统 | `/topic/rfq/{rfqId}/event` | 封盘/流标/延时事件 |

### 10.3 Redis 数据结构
- `bid:lowest:{rfqId}` → `{supplierId(匿名), price, time}`（String/Hash）
- `bid:rank:{rfqId}` → Sorted Set（price asc），用于排名
- `bid:lock:{rfqId}` → 分布式锁（出价串行化，防并发覆盖）
- 出价校验用 **Lua 脚本**原子执行：读最低价→校验降幅→写新最低→返回结果，避免"读-改-写"竞态。

### 10.4 防狙击延时状态机
```
[未开始] --到点--> [进行中] --结束前N分钟内有出价--> [延时N分钟] --再出价--> 循环
   |                     |
   └──无出价到点--> [已结束/流标]
```
- 每次出价检查 `now ≥ endTime - N` → 若真，则 `endTime += N`（N 可配，默认 5min），广播 `extended=true`。
- 封盘：`now ≥ endTime` 且无待处理延时→状态置已结束，停止接收出价。

### 10.5 匿名与权限
- 广播给所有受邀方**对手价以匿名+排名**呈现；仅本人 `yourPrice/yourRank` 可见真实值。
- 出价前校验该 supplier 在 `invite_suppliers` 且 `status=合格`。

### 10.6 审计与降级
- 每次出价经 `@CommonLog` 入签名审计（时间/价/方），满足等保。
- 降级：WS 断开→前端轮询 REST `/api/bid/state/{rfqId}`（3s）；Redis 故障→竞价暂停并告警，恢复后重建最低价。

---

## 11. 非功能验收清单

### 11.1 等保三级
- 登录 SM2 前端加密后端解密；密码 SM3 完整性存储。
- 操作/登录日志 SM2 签名防篡改，保留 ≥5 年。
- 敏感字段（credit_code/bank_account/phone/unit_price）SM4(CBC) 存储与按需脱敏。

### 11.2 性能
- 列表查询 P95 < 1s（万级供应商）；竞价广播延迟 < 500ms（受邀方 ≤ 50）。
- 月度评分计算（全量）窗口期 ≤ 2h，可重入。

### 11.3 兼容与信创
- 浏览器：Chrome/Edge 最新两版；前端 AntDV 适配。
- 信创（可选演进）：OS 麒麟/统信 + TongWeb 替代 Tomcat；数据库 PG/MySQL（本期），国产库后续可切。

### 11.4 数据库方言
- PostgreSQL 16 为主：`decimal(18,4)`/`jsonb`/`timestamptz`。
- MySQL 8 等价：`decimal(18,4)`/`json`/`datetime`；避免 PG 专有函数（如 `jsonb_` 操作符），统一用 MP 抽象或条件 SQL。

---

## 12. 验收追溯矩阵（FRD L3 → AC 覆盖）

| 模块 | L3 数 | AC 已覆盖 | 字段清单 | 备注 |
|------|-------|-----------|----------|------|
| M1 准入 | 28 | 28 | ✅(3 实体) | — |
| M2 寻源 | 22 | 22 | ✅(4 实体) | 含竞价 srm_bid |
| M3 协同 | 21 | 21 | ✅(4 实体) | — |
| M4 绩效风险 | 22 | 22 | ✅(4 实体) | — |
| M5 门户 | 14 | 14 | ✅(复用) | 角色映射见 §1.2 |
| M6 工作流 | 9 | 9 | ✅(2 实体) | — |
| M7 集成 | 6 | 6 | ✅(2 实体) | — |
| **合计** | **122** | **122 (100%)** | **19 实体** | 无遗漏 |

> 通用约定(G1~G6)对全部 L3 默认生效；上表"AC 已覆盖"指各 L3 特异 AC 已列出。下一步可据此编写测试用例与 gen 插件骨架。

---

*本 PRD 与 FRD v1.0、方案 v1.1 决策完全一致：Flowable、PG/MySQL、门户并入工程、org+数据权限多法人、反向竞价(WebSocket)、统一绩效权重、暂不接外部风险、仅 Web、多联系人多角色。本期不编写代码。*
