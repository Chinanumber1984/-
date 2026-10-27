# 基于 Snowy（小诺）的 SRM 系统重建设计方案

> 版本：v1.1（决策已锁版）
> 日期：2026-08-27
> 底座仓库：https://gitee.com/gwxSnowy/snowy （小诺 SnowyAdmin，Apache-2.0）
> 适用：集团型离散/流程制造企业 · 私有化 · 等保三级 · 信创

---

## 0. 结论摘要

| 项 | 决策 |
|----|------|
| 开发底座 | **Snowy 3.x**（Vue3 + AntDesignVue4 + Vite5 + SpringBoot3 + MyBatis-Plus + Sa-Token + 国密）|
| 业务落地 | 在 Snowy 插件体系下新增 4 个 SRM 业务插件 + 1 门户 + 1 集成 |
| 审批引擎 | ✅ **已定：Flowable 7**（新增 snowy-plugin-flow，统一审批中心）|
| 数据库 | ✅ **PostgreSQL 16（推荐）或 MySQL 8**（开源友好；JSONB 利于资质/评分半结构化扩展）|
| 等保/信创 | **直接复用** Snowy 国密 SM2/SM3/SM4 与签名日志；OS/中间件走信创（麒麟/统信 + TongWeb），数据库用 PG/MySQL |
| 供应商门户 | ✅ **前端并入 Snowy 同一工程**（新增 views + 供应商角色，复用 Pinia/路由）；后端 srm-portal 插件 |
| 多法人隔离 | ✅ Snowy **不支持多租户** → 改用 **组织(org) + 数据权限范围** 实现集团多法人隔离（sys 插件已具备）|

> 仓库已克隆至本地 `snowy-src/`，可对照包结构与本方案实施。

---

## 1. 为什么选 Snowy（对照 v0.1 需求）

| SRM 需求点 | Snowy 已有能力 | 处理方式 |
|------------|----------------|----------|
| RBAC（组织/角色/数据权限）| sys 插件：用户/角色/组织/岗位/菜单/按钮 | 直接复用，扩展 SRM 角色 |
| 资质证书文件 | sys 文件 + 对象存储(MinIO) | 复用，存 cert 附件 |
| 资质类型/状态字典 | biz 插件 dict | 复用字典 |
| 准入/定点/冻结审批 | ❌ 开源版无工作流 | 新增 Flowable 插件 |
| 审计不可篡改（等保）| 操作/登录日志 SM2 签名 | 直接复用 |
| 敏感字段加密（银行账号/单价）| 国密 SM4 字段脱敏 | 直接复用 |
| 供应商门户自助 | client / mobile 插件 | 复用 + 扩展 |
| 代码生成提速 | gen 插件 | 用于 SRM CRUD 脚手架 |
| 信创适配 | 国产机型/中间件适配（数据库用 PG/MySQL）| OS/中间件信创，库用开源 |
| 集团多法人隔离 | ❌ Snowy 不支持多租户 | 用 org + 数据权限范围隔离 |

---

## 2. 技术栈与版本（锁定）

| 层 | 选型 | 说明 |
|----|------|------|
| 前端 | Vue3 + AntDesignVue4 + Vite5 + Pinia | Snowy 原生，门户复用同一套 |
| 后端 | SpringBoot 3 + JDK17 + MyBatis-Plus | Snowy 原生 |
| 鉴权 | Sa-Token | Snowy 原生，支持多端 |
| 工具 | Hutool | Snowy 原生 |
| 加解密 | 国密 SM2/SM3/SM4（Snowy 加解密 starter）| 满足等保，免自研 |
| 数据库 | **PostgreSQL 16（推荐）/ MySQL 8** | PG 的 JSONB 便于资质/评分等半结构化扩展；信创库为可选演进 |
| 缓存 | Redis | 会话/字典/竞价实时价缓存 |
| 实时通信 | Spring WebSocket + STOMP | 反向竞价实时出价推送（已确认需要）|
| 工作流 | **Flowable 7**（新增 snowy-plugin-flow）| 审批中心 |
| 定时 | Spring Scheduler / XXL-JOB | 绩效月度计算 |
| 消息 | Snowy 消息 + 企微/钉钉 Webhook | 预警/待办 |
| 构建 | Maven 多模块 | 与 Snowy 一致 |

---

## 3. Snowy 插件体系约定（必须遵循）

- 包根：`vip.xiaonuo`
- 每个插件 = `snowy-plugin-{name}`(实现) + `snowy-plugin-{name}-api`(跨插件接口)
- 业务模块目录规范：`modular/{模块}/entity | mapper | mapping | param | controller | service | service/impl | enums`
- 实体继承 `vip.xiaonuo.common.pojo.CommonEntity`（含 id/租户/创建人/时间/删除标志）
- 控制器返回 `CommonResult`，操作打 `@CommonLog`（自动进签名审计日志）
- 新插件需在 `snowy-web-app/pom.xml` 中作为 `<dependency>` 引入后才会加载
- **多法人隔离**：Snowy 开源版无多租户能力，不依赖 `CommonEntity` 的租户字段；集团多法人隔离通过 **sys 插件的组织(org)树 + 数据权限范围（本人/本机构/全部）** 实现，SRM 查询统一带 `orgId` 过滤与数据范围注解。

---

## 4. 新增插件规划

```
snowy-plugin/
  ├─ snowy-plugin-srm-core       # M1 供应商主数据 + 准入生命周期
  ├─ snowy-plugin-srm-sourcing   # M2 寻源（RFQ/比价/竞价/定点）
  ├─ snowy-plugin-srm-collab     # M3 采购协同（PO/ASN/对账/发票）
  ├─ snowy-plugin-srm-perf       # M4 绩效评分卡 + 质量协同 + 风险
  ├─ snowy-plugin-srm-portal     # 供应商门户后端（api+impl）：含联系人/多角色管理
  ├─ snowy-plugin-srm-integration# ERP/MES/QMS/财务/OA 适配器
  └─ snowy-plugin-flow           # Flowable 审批引擎（新增）
snowy-plugin-api/
  ├─ snowy-plugin-srm-core-api …（同上成对）
  └─ snowy-plugin-flow-api
```

### 4.1 srm-core 目录示例（其他插件同构）
```
snowy-plugin-srm-core/src/main/java/vip/xiaonuo/srm/modular/
  ├─ supplier/        entity(SrmSupplier) mapper service controller param enums
  ├─ cert/            资质证书
  ├─ onboard/         准入审批流程编排（调用 flow）
  └─ lifecycle/       冻结/解冻/淘汰
```

---

## 5. 四大模块在 Snowy 上的落地映射

> 功能需求细粒度拆分（L2/L3 功能点 + 用例 + 角色 + 统计矩阵）见独立文档 **《SRM功能需求拆分.md》**，本节仅保留架构级映射。

### M1 供应商准入与生命周期（srm-core）
- 主数据 `SrmSupplier extends CommonEntity`，状态机：潜在→审核中→合格→冻结/淘汰
- 资质 `SrmSupplierCert`：类型走 biz 字典；附件走 Snowy 文件；到期预警用 Spring Scheduler + 消息
- 准入审批：Flowable 流程（注册→SQE资质审核→采购经理准入），待办回写 Snowy 待办中心
- 银行账号/统一信用代码：SM4 脱敏（复用 Snowy 字段加密）

### M2 寻源（srm-sourcing，不含招投标）
- `SrmRfq` / `SrmQuotation`：CRUD 由 gen 插件生成
- 询比价：服务端按**全局统一权重**（交付30%/质量35%/成本20%/服务15%，不分品类）计算综合到厂价
- 反向竞价（**已确认需要**）：Redis 缓存实时最低价 + **WebSocket/STOMP** 推送所有受邀供应商；结束前 N 分钟出价自动延时（防狙击）；出价明细入审计日志
- 定点：`SrmAward` 关联 Flowable 审批 + 关联近 12 月绩效（调 srm-perf 接口）
- 单价字段对 SQM 角色隐藏（数据权限 + SM4）

### M3 采购协同（srm-collab）
- `SrmPo`(来自 ERP 同步) / `SrmAsn`(供应商发货) / `SrmReconciliation` / `SrmInvoice`
- 订单确认、ASN、对账：走门户（srm-portal）
- 收货量容差、逾期预警：规则 + 定时任务
- 发票协同：推送财务共享（srm-integration）

### M4 绩效与风险（srm-perf）
- `SrmScorecard` 月度定时计算（交付30%/质量35%/成本20%/服务15%，权重字典可配）
- 质量协同：PPAP 提交审批（flow）、SCAR/8D 闭环（`SrmScar` 状态机）
- 风险（**暂不对接外部数据源**）：`SrmRiskEvent` 仅来自内部事件——资质过期、履约异常（连续延期/重大质量）、合规黑名单（手工录入）；触发预警 + 联动 M1 冻结。外部工商/征信数据留作后续可选演进
- 评分卡/SCAR 对供应商可见自身数据（门户数据权限隔离）

---

## 6. 关键数据模型（表前缀 `srm_`，均继承 CommonEntity）

| 表 | 核心字段 |
|----|----------|
| srm_supplier | supplier_code, name, credit_code, category, tier, status, bank_account(加密) |
| srm_supplier_cert | supplier_id, cert_type(字典), cert_no, valid_from, valid_to, file_id |
| srm_supplier_contact | contact_id, supplier_id, name, phone(加密), email, roles(JSONB) |
| srm_rfq | rfq_no, title, material_list, invite_suppliers, deadline, strategy |
| srm_quotation | rfq_id, supplier_id, unit_price(加密), lead_time, total_amount |
| srm_award | rfq_id, awarded_supplier, award_qty, reason, risk_ref |
| srm_po | po_no, supplier_id, material, qty, price, due_date, confirmed |
| srm_asn | asn_no, po_no, delivery_lines, batch_no, eta |
| srm_reconciliation | rec_no, po_list, received_qty, amount, diff, status |
| srm_scorecard | supplier_id, period, deliver_score, quality_score, cost_score, service_score, total, grade |
| srm_scar | supplier_id, defect_desc, d8_reply, status, close_time |
| srm_risk_event | supplier_id, risk_type, level, trigger, action, status |

---

## 7. 国密 / 等保 / 信创 适配（直接继承 Snowy，零自研）

- **等保三级**：登录 SM2 前端加密后端解密；密码 SM3 完整性存储；操作/登录日志 SM2 签名防篡改；敏感字段（银行账号、单价）SM4(CBC) 脱敏 —— 全部由 Snowy 加解密 starter 提供。
- **信创（OS/中间件层）**：
  - 数据库：采用 **PostgreSQL / MySQL**（开源，非国产库；若后续强国产化再切换达梦/金仓）
  - 中间件：东方通 TongWeb 替代 Tomcat；Redis 可用信创替代
  - 操作系统/CPU：麒麟 V10 / 统信 UOS + 鲲鹏/海光（ARM/x86）
- **审计**：所有 SRM 写操作加 `@CommonLog`，自动进入签名日志，保留 ≥5 年。

---

## 8. 工作流方案（已定：Flowable）

开源 Snowy 不含工作流引擎，确定引入 **Flowable 7**（与 SpringBoot3 兼容），新增 `snowy-plugin-flow`：

- 统一审批中心，BPMN 流程定义驱动：供应商准入、资质审核、寻源定点、冻结/淘汰、SCAR 验证
- 待办/已办接入 Snowy 待办中心；审批动作经 Flowable `TaskListener` 联动 SRM 业务状态（如准入通过→供应商置"合格"）
- 流程变量存供应商/单据 ID、当前节点、审批人；历史留痕满足等保审计
- 若 Snowy 官方提供 Flowable 插件，可直接引入以减少自研量；否则自搭 `snowy-plugin-flow`（Flowable 7 + MP 表结构并入 Snowy 数据源）

---

## 9. 集成方案（srm-integration）

| 系统 | 方式 | 内容 |
|------|------|------|
| ERP(用友/金蝶/SAP) | Feign/REST + API 网关 | 主数据、PO、价格、收货、付款 |
| MES | REST | 排产/领用（交付优先）|
| QMS | REST | 来料检验、PPAP、SCAR 回写 |
| 财务共享 | REST | 对账、发票、付款申请 |
| OA | 待办回调 | 审批流（如复用既有 OA）|
| 消息(企微/钉钉) | Webhook | 预警、待办 |

规范：统一网关 + Token；异步走 MQ（Snowy 支持 RocketMQ/RabbitMQ）；失败重试 + 补偿；接口 SLA 99.5%。

---

## 10. 实施路线（按插件分批，复用 Snowy 基建）

1. **一期**：snowy-plugin-flow + srm-core + srm-portal + 等保/信创基座（复用 Snowy 鉴权/日志/加密）
2. **二期**：srm-sourcing + srm-collab（PO/ASN/对账）
3. **三期**：srm-perf（评分卡/SCAR/风险）+ srm-integration（ERP/MES/QMS）+ BI 看板

---

## 11. 与 v0.1 PRD 的差异

| 维度 | v0.1（纯需求）| 本方案（基于 Snowy）|
|------|---------------|----------------------|
| 技术底座 | 未定 | Snowy 3.x 锁定，省 RBAC/日志/加密 |
| 审批引擎 | 提及 OA | 明确补 Flowable 插件 |
| 门户 | 单独描述 | 前端并入 Snowy 同一工程（新增 views + 供应商角色）|
| 等保/信创 | 写要求 | 明确复用国密 starter + OS/中间件信创（库用 PG/MySQL）|
| 多法人隔离 | 未涉及 | Snowy 无多租户 → org + 数据权限范围实现 |
| 开发效率 | — | gen 插件生成 SRM CRUD 脚手架 |
| 风险 | 事后发现 | 风险事件联动冻结（跨插件调用）|

---

## 12. 本次已确认决策

1. ✅ 工作流：**Flowable 7**
2. ✅ 数据库：**PostgreSQL（推荐）或 MySQL 8**
3. ✅ 门户前端：**并入 Snowy 同一前端工程**（新增视图 + 供应商角色，非独立工程）
4. ✅ 多法人隔离：Snowy **不支持多租户** → 改用 **组织(org) + 数据权限范围**

## 13. 本次新增确认决策（v1.1 已锁版）

1. ✅ **反向竞价**：需要，上 WebSocket/STOMP 实时出价（架构已含）
2. ✅ **绩效权重**：全司统一默认权重，**不分品类**差异化
3. ✅ **外部风险数据**：暂不对接，风险仅内部事件驱动
4. ✅ **移动端**：暂不需要，仅 Web
5. ✅ **门户子账号**：一个供应商支持**多个联系人 + 多角色**（报价员/发货员/质量对接等），数据权限限定自身供应商

> 方案已锁版，下一步可进入任一插件（建议从 `snowy-plugin-flow` + `srm-core` 起步）的代码骨架生成。

---
*本方案建立在 Snowy 3.x 实际代码结构之上（已克隆核对插件/包约定）。下一步可进入任一插件的代码骨架生成。*
