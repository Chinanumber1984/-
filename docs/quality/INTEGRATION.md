# 规范化质量接口与真实厂商待办

接口文档：`openapi.json`。内部管理使用当前登录令牌；门户质量提交仅绑定本方。本文不包含任何密钥。

外部事件入口：`POST /srm-integration/v1/ingress/{connectorId}`。经本机网关访问时前加 `/api`。连接配置在服务器决定组织、系统和SANDBOX/LIVE，不能用报文指定身份范围。QMS供方外部键必须先通过 `/srm/v1/quality/source-mappings` 管理接口映射；ERP绩效外部键复用采购协同的ERP映射。

认证头：`X-SRM-Timestamp`（UTC毫秒）、`X-SRM-Nonce`（16～64字符，每次请求新值）、`X-SRM-Signature`（HMAC-SHA256十六进制）。签名串依次为method、原始path、timestamp、nonce、原始UTF-8 body的SHA256，以换行连接，无末尾换行。时效±5分钟；传输nonce不可复用。连接密钥从私有配置取得，不贴入聊天或日志。

事件外层：`eventId`、`resource`、`businessKey`、`version`、`occurredAt`、`data`。版本从1递增。同事件重试保持内容与eventId，换新nonce；同版本相同内容可幂等去重；未来版本等待前序，内容矛盾留冲突。HTTP接收不等于业务已入账，应检查返回的 `data.status`；持久入站／出站与人工重试可在集成监控查到。

QMS连接只允许 `QMS_INSPECTION`：

```json
{
  "eventId": "vendor-inspection-0001",
  "resource": "QMS_INSPECTION",
  "businessKey": "inspection-0001",
  "version": 1,
  "occurredAt": 1793145600000,
  "data": {
    "supplierKey": "external-supplier-key",
    "period": "2026-10",
    "result": "FAIL",
    "severity": "SEVERE",
    "sourceRef": "QMS-report-0001",
    "description": "来料检验结论和证据引用"
  }
}
```

`result`为PASS/FAIL，`severity`为MINOR/MAJOR/SEVERE；月份不可在未来。严重不合格生成内部风险待评估，不能自动直接冻结。来源纠正用同businessKey的新版本；不能改绑供应商或月份。当前有效检验的扣分规则及版本保留，历史版本在持久入站日记中追溯。

ERP连接允许 `ERP_PERFORMANCE`，businessKey必须为 `supplierKey:period`：

```json
{
  "eventId": "vendor-performance-0001",
  "resource": "ERP_PERFORMANCE",
  "businessKey": "external-supplier-key:2026-10",
  "version": 1,
  "occurredAt": 1793145600000,
  "data": {
    "supplierKey": "external-supplier-key",
    "period": "2026-10",
    "scores": [95, 90, 95, 95],
    "sourceRef": "ERP-period-ledger-0001"
  }
}
```

scores按交付／质量基准／成本／服务排列。发布时质量项由同模式QMS当前检验按 `max(0,100−扣分合计)` 重算；其他三项来自ERP。缺少任一来源不生成虚构评分。预览返回basisHash，发布必须引用当前hash；重试相同依据不重复生成输入。待复核申诉会阻断覆盖评分。每月定时汇总上月来源，单个供方失败不回滚其他供方；原绩效计算任务继续保留版本、模板及申诉约束。

SANDBOX来源明确标记 `SANDBOX_ERP_QMS`，评分不联动主档分级；LIVE使用 `ERP_QMS`，按原绩效规则联动分级。不同模式不能混合当作真实数据。

本机 `collab-sandbox` 没有宿主机端口；签名、回调、事件去重账本和故障重试都持久化。真实厂商字段转换、检验批口径、认证网关、网络地址、质量规则确认及真实数据端到端仍需各系统负责人提供资料后复验，当前不作为已完成的真实联调。
