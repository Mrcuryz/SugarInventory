# M1.4 模块 MCP 工具详细设计

本文档用于 M1.4 后续只读分析工具的详细设计。M1.4b 五个化验查询与质量分析工具、M1.4c 五个二维码 / 托盘生命周期工具已按只读链路落地；M1.4d-f 条目仍为设计，不新增写能力。

相关基础已经具备：

- `get_inventory_distribution` 已验证 Java 聚合、MCP Tool、Python planner、safe adapter、前端卡片和 E2E 链路。
- Agent Tool 审计、流式结果分类、工具错误与业务空结果区分已建立。
- `agent_message_review` 可记录失败、低置信度成功、用户纠正、实际工具路径和证据摘要。
- LLM Wiki Lite / Tool Capability Registry 已定义当前工具选择边界、写操作拒绝规则和受控只读能力。

## 1. 全局设计原则

### 1.1 工具边界

M1.4 只允许新增 L1 只读工具，少量统计类可标为 L1/L2 但仍不得写业务表。所有工具必须满足：

- Java 后端负责业务数据、权限、审计、事务边界和聚合口径。
- Python runtime 只通过 Java internal agent gateway 调用白名单工具。
- Python runtime 由主 Agent 做意图与最终回答，并通过 handoff Router 把任务交给只看到本模块工具的最小权限专家 Agent。
- Python 不直连数据库，不复制 Java Mapper，不拼接页面 REST。
- 不新增任意 SQL、任意 HTTP 代理、任意数据库访问工具。
- 不新增入库、出库、调拨、作废、恢复、删除、导入、确认执行能力。
- 模型不得猜 `productId`、`warehouseId`、`inventoryId`、`assayId`、`orderId`。
- 普通 UI 不展示内部 ID、`toolName`、`SUCCESS`、raw JSON、token、Authorization、堆栈或 chain-of-thought。

### 1.2 工具形态

每个 M1.4 工具都按以下结构实现：

1. Java 只读 read model service / aggregate service。
2. Java DTO 字段白名单和权限校验。
3. Internal agent gateway 白名单工具。
4. warehouse-mcp schema / ToolCallback。
5. Python tool schema、argument validation、planner 路由、safe adapter。
6. 前端业务文本、卡片或表格展示。
7. Agent tool audit、runtime audit、review evidence 摘要。
8. Java / Python / E2E 或手工验收测试。

### 1.3 共用输入约定

所有范围型工具优先使用受控 scope，而不是散落 ID 字段。

```json
{
  "productScope": {
    "type": "SINGLE_PRODUCT | EXACT_PRODUCT_NAME_GROUP | PRODUCT_TYPE_GROUP | ALL",
    "productId": 84,
    "productName": "黄冰糖",
    "productType": "黄冰糖"
  },
  "warehouseScope": {
    "type": "SINGLE_WAREHOUSE | ALL",
    "warehouseId": 8
  },
  "dateRange": {
    "type": "EXACT | LAST_DAYS | RANGE",
    "date": "2026-07-09",
    "days": 7,
    "from": "2026-07-01",
    "to": "2026-07-09"
  },
  "limit": 20
}
```

来源规则：

- `SINGLE_PRODUCT.productId` 只能来自 `resolve_products`、HITL 候选选择或 structured state。
- `SINGLE_WAREHOUSE.warehouseId` 只能来自 `resolve_warehouses`、HITL 候选选择或 structured state。
- `ALL` 只在用户明确说“全部产品 / 所有产品 / 全部库位”或查询语义天然为全局统计时允许。
- 产品名称组、产品大类必须来自 resolver 的结构化候选或明确用户表达，不由模型自由构造。

### 1.4 共用输出约定

所有工具输出必须同时支持机器处理和用户安全展示。

```json
{
  "scopeLabel": "黄冰糖（袋）",
  "summaryText": "最近7天共有 3 条不合格化验记录。",
  "isEmpty": false,
  "groups": [],
  "records": [],
  "riskLabels": [],
  "notes": [],
  "nextActions": []
}
```

禁止输出到普通 UI：

- 内部 ID；
- raw DB 行；
- raw JSON；
- Java/Python 异常堆栈；
- token / Authorization；
- toolName / SUCCESS。

### 1.5 审计和 Review 接入

每个工具必须记录：

- `toolName`、`riskLevel`、`durationMs`、`resultCode`、`errorCode`；
- 脱敏 request summary；
- 脱敏 response summary；
- `messageId`，用于关联 `agent_message_review_evidence`；
- 空结果与工具失败必须分开。

review evidence 推荐摘要：

- “本轮调用 `query_assay_abnormalities`，返回 0 条，过滤条件为最近7天、明确不合格。”
- “本轮只调用 resolver，未调用主查询工具。”
- “工具返回非空，但 safe adapter 输出空结果。”

### 1.6 LLM Wiki / Planner 接入

每个新增工具需要同步 Tool Capability Registry，至少写清：

- `can_answer`；
- `cannot_answer`；
- `use_when`；
- `do_not_use_when`；
- `required_slots`；
- `resolver_for` / `main_query_for`；
- 正例和反例；
- safe result summary。

Planner 必须先生成安全意图快照，不能直接让模型自由选择 ID 或自由生成工具参数。

## 2. 推荐实施批次

### 第一批：M1.4b 化验查询与质量分析

优先级最高。原因：

- 库存分布已经能暴露“无化验 / 不合格 / 无标准”等风险，但缺少后续解释和明细工具。
- 用户已经会从库存分布卡片进入“去补充”或追问质量原因。
- 质量风险直接影响仓库可信度。

推荐顺序：

1. `query_assay_records`（已实现，M1.4b）
2. `query_assay_abnormalities`（已实现，M1.4b）
3. `query_products_without_recent_assay`（已实现，M1.4b）
4. `get_assay_report_detail`（已实现，M1.4b）
5. `query_assay_standard_coverage`（已实现，M1.4b；第一版支持 `PRODUCT_WITHOUT_STANDARD`）

### 第二批：M1.4c 二维码 / 托盘生命周期分析

优先级次高。原因：

- 解决“码打印了但没入库”“托盘状态是否可信”“作废码是否被扫”等仓库可信度问题。
- 可复用已有 `get_pallet_status` 和 PalletCode 资产。

推荐顺序：

1. `query_qr_code_lifecycle`
2. `query_printed_not_inbound_codes`
3. `query_pallet_anomalies`
4. `query_qr_batch_inbound_completion`
5. `query_pallet_flow_records`

状态：已实现并加入 internal agent gateway 白名单、`warehouse-mcp` 工具注册和 Python Agent planner。实现顺序按可复用资产调整为生命周期摘要、打印未入库、异常、流转记录、批次完成率；五个工具均为只读 L1 或 L1/L2。

第一版口径已固定为：打印来自 `production_order_label_batch.printed_at`；入库由输出码库存关联、入库时间或 `INSTOCK` 状态判定。当前没有独立二维码扫描日志，因此 `VOID_CODE_SCANNED` 只说明证据缺口，不生成“未发现异常”的结论。

### 第三批：M1.4a 后续库存明细与库龄

推荐顺序：

1. `query_inventory_records`
2. `query_inventory_without_assay`
3. `query_inventory_ageing`

注意：`query_inventory_without_assay` 可被化验模块覆盖，是否独立成工具取决于 UI 是否需要库存明细下钻。

### 第四批：M1.4d / e / f

- M1.4d 库位健康分析：容量、混放、库位操作。
- M1.4e 生产订单与标签闭环。
- M1.4f 操作审计和 Agent 审查分析。

这些模块更依赖业务口径确认，建议在化验和二维码工具稳定后推进。

## 3. M1.4b 化验查询与质量分析

### 3.1 `query_assay_records`

状态：已实现，M1.4b。

风险等级：L1。

用途：按产品范围、日期范围、判定状态查询化验记录列表和摘要。

典型问题：

- 黄冰糖最近一次化验是什么时候？
- 黄冰糖最近 30 天化验记录有哪些？
- 今天有哪些化验记录？
- 某产品最近一次合格 / 不合格记录是什么？

输入：

```json
{
  "productScope": {"type": "SINGLE_PRODUCT", "productId": 84},
  "dateRange": {"type": "LAST_DAYS", "days": 30},
  "judgeStatus": "ANY | PASS | FAILED | NO_STANDARD | MULTIPLE_CANDIDATES",
  "sortBy": "sampleDate",
  "sortDirection": "DESC",
  "page": 1,
  "size": 20
}
```

输入规则：

- 产品不明确时必须先 `resolve_products` 或 HITL。
- 用户问“今天有哪些化验记录”可使用 `productScope=ALL`。
- “最近一次”默认按 `sampleDate DESC, createdAt DESC`，需要在回答中说明口径。
- `size` 默认 20，最大 100。

输出：

```json
{
  "scopeLabel": "黄冰糖（袋）",
  "dateRangeLabel": "最近30天",
  "total": 6,
  "latestSampleDate": "2026-07-08",
  "summaryText": "最近30天共有 6 条化验记录，其中 5 条合格，1 条不合格。",
  "records": [
    {
      "recordLabel": "2026-07-08 黄冰糖（袋）化验",
      "productLabel": "黄冰糖（袋）",
      "sampleDate": "2026-07-08",
      "judgeLabel": "合格",
      "failedMetricText": "",
      "standardLabel": "白砂糖标准 v2",
      "actionHint": "可查看详情"
    }
  ],
  "notes": ["最近一次按采样日期排序。"]
}
```

Java 资产：

- `AssayController` 列表查询和详情查询。
- `AssayMapper`。
- `AssayGroupController` / 标准关系用于安全标准展示。
- 当前实现新增 `POST /api/assay/records/query`、`AssayRecordsService`、`AssayRecordsMapper`，不复用页面查询计数口径。
- 当前实现已接入 internal agent gateway 白名单、warehouse-mcp `query_assay_records`、Python planner / safe answer。

Planner 规则：

- “最近一次化验” -> `query_assay_records(size=1, sort=sampleDate desc)`。
- “今天化验记录” -> `dateRange=EXACT(today)`。
- “最近 N 天化验” -> `dateRange=LAST_DAYS(N)`。
- “是否合格”若是单日期问题，可继续使用现有 `get_assay_status`；若是范围问题，使用本工具。

前端展示：

- 列表卡片最多展示 5 条，超过时提示“还有 N 条”。
- 字段：日期、产品、结论、异常指标、标准。
- 不展示 `assayId`，详情按钮使用 opaque `recordRef` 或后端 pending state。

测试：

- 产品未确认时不调用工具。
- 最近一次按 sampleDate 排序。
- 空结果显示“未查询到化验记录”，不显示工具失败。
- 工具失败显示“只读化验工具调用失败”。

### 3.2 `get_assay_report_detail`

状态：已实现，M1.4b。

风险等级：L1。

用途：查看单条化验报告的指标、判定、标准和异常原因。

典型问题：

- 这份化验报告详情是什么？
- 最近一次化验哪些指标不合格？
- 刚才那条无标准的原因是什么？

输入：

```json
{
  "reportRef": "opaque_report_ref",
  "includeMetrics": true,
  "includeStandardSnapshot": true
}
```

输入规则：

- 普通用户不传 `assayId`。
- `reportRef` 来自 `query_assay_records`、`query_assay_abnormalities` 或 HITL pending state。
- 管理员调试可在安全摘要中看到 ref，不展示内部 ID。

输出：

```json
{
  "reportLabel": "2026-07-08 黄冰糖（袋）化验",
  "productLabel": "黄冰糖（袋）",
  "sampleDate": "2026-07-08",
  "judgeLabel": "不合格",
  "standardLabel": "黄冰糖标准 v3",
  "metrics": [
    {
      "metricName": "色值",
      "actualValueText": "120",
      "standardRangeText": "≤ 100",
      "resultLabel": "不合格"
    }
  ],
  "summaryText": "本次化验不合格，主要异常指标为色值。"
}
```

Java 资产：

- `AssayController.getDetail`。
- 标准快照解析逻辑。

Planner 规则：

- 有最近一条 `lastAssayRecordRef` 时，“详情 / 哪些指标”可直接调用。
- 没有上下文时，先让用户选择报告或先查记录。

前端展示：

- 指标表格卡片。
- 异常指标用业务标签，不展示 raw metric JSON。

测试：

- 没有 `reportRef` 不调用详情工具。
- `reportRef` 过期或不属于当前 session 时拒绝。
- 无标准、多候选、不合格三种结果正常展示。

### 3.3 `query_assay_abnormalities`

状态：已实现，M1.4b。

风险等级：L1。

用途：查询一段时间内质量异常记录，支持不合格、无标准、多候选、无化验风险的分层统计。

典型问题：

- 最近 7 天有哪些不合格？
- 最近 90 天哪些产品有质量异常？
- 哪些化验记录无标准？
- 最近有哪些指标越界？

输入：

```json
{
  "productScope": {"type": "ALL"},
  "dateRange": {"type": "LAST_DAYS", "days": 90},
  "abnormalTypes": ["FAILED", "NO_STANDARD", "MULTIPLE_CANDIDATES"],
  "groupBy": "product | date | abnormal_type | metric",
  "limit": 50
}
```

口径：

- `FAILED`：已有化验且明确不合格。
- `NO_STANDARD`：有化验但无可用标准，不能判断合格/不合格。
- `MULTIPLE_CANDIDATES`：命中多个标准或判定候选，需要人工确认。
- `NO_ASSAY` 不属于化验记录异常，应由 `query_products_without_recent_assay` 或库存风险查询处理。
- “异常库存 / 质量风险库存”可以包含 `FAILED + NO_STANDARD + NO_ASSAY`，但必须按标签说明，不能混称为不合格。

输出：

```json
{
  "scopeLabel": "全部产品",
  "dateRangeLabel": "最近90天",
  "summaryText": "最近90天发现 4 条质量异常，其中 2 条不合格、2 条无标准。",
  "groups": [
    {
      "groupLabel": "黄冰糖（袋）",
      "failedCount": 1,
      "noStandardCount": 1,
      "latestSampleDate": "2026-07-08",
      "riskLabels": ["存在不合格化验", "存在无标准化验"]
    }
  ],
  "records": []
}
```

Planner 规则：

- “不合格 / 未通过 / 检测失败” -> `abnormalTypes=["FAILED"]`。
- “无标准” -> `abnormalTypes=["NO_STANDARD"]`。
- “质量异常 / 风险” -> `["FAILED","NO_STANDARD","MULTIPLE_CANDIDATES"]`，如用户明确包含无化验，再联动 `query_products_without_recent_assay`。

前端展示：

- 优先分组卡片，异常类型汇总放卡片顶部。
- 不要每行重复同一风险文案；行内只保留关键标签。

测试：

- 最近 7 / 90 / 180 天日期转换正确。
- 不把 `NO_ASSAY` 算作 `FAILED`。
- 空结果回答带过滤条件：“未查询到最近90天明确不合格化验记录”。

### 3.4 `query_products_without_recent_assay`

状态：已实现，M1.4b。

风险等级：L1/L2，只读统计。

用途：查询当前在库或指定产品范围内，最近 N 天没有有效化验记录的产品 / 托盘 / 库存分组。

典型问题：

- 今天哪些产品没有化验？
- 最近 7 天哪些在库产品没有化验？
- 1号库位有哪些库存缺化验？
- 黄冰糖哪些托盘需要补化验？

输入：

```json
{
  "productScope": {"type": "ALL"},
  "warehouseScope": {"type": "ALL"},
  "population": "CURRENT_INVENTORY | ACTIVE_PRODUCTS | TODAY_INBOUND",
  "dateRange": {"type": "LAST_DAYS", "days": 7},
  "groupBy": "product | warehouse | product_warehouse",
  "limit": 50
}
```

口径：

- 默认全集为 `CURRENT_INVENTORY`，即当前在库库存。
- “今天哪些产品没有化验”如果没有更多上下文，解释为“当前在库产品中今天没有有效化验记录”。
- 有效化验默认指存在关联产品且 sampleDate 落入日期范围的化验记录，不要求一定合格。
- 无标准不等于无化验，需要分开展示。

输出：

```json
{
  "scopeLabel": "当前在库全部产品",
  "summaryText": "最近7天共有 8 个在库分组缺少有效化验。",
  "groups": [
    {
      "groupLabel": "黄冰糖（袋）",
      "stockText": "2板20件",
      "warehouseLabels": ["1号库位"],
      "latestInboundTime": "2026-05-22",
      "riskLabels": ["无有效化验"],
      "nextAction": {"type": "create_assay", "label": "去补充"}
    }
  ]
}
```

Java 资产：

- 库存聚合接口。
- Assay 查询和产品标准关系。

Planner 规则：

- “无化验 / 未化验 / 缺化验”优先使用本工具。
- 如果用户问“无化验库存按产品分布”，也可用 `get_inventory_distribution(assayStatus=MISSING_ASSAY)`；需要明细或补化验入口时使用本工具。

前端展示：

- 分组卡片中标注具体项。
- `去补充` 只是跳转到现有新增化验页面，不是 MCP 写工具。

测试：

- 无化验、无标准、不合格口径严格区分。
- `去补充` 不携带内部 ID 到普通 UI。

### 3.5 `query_assay_standard_coverage`

状态：已实现，M1.4b；第一版支持 `PRODUCT_WITHOUT_STANDARD`。

风险等级：L1/L2，只读统计。

用途：分析产品与质量标准覆盖关系，以及化验记录是否能匹配标准。

典型问题：

- 哪些产品没有质量标准？
- 哪些化验记录无标准？
- 哪些标准最近没有被使用？

输入：

```json
{
  "productScope": {"type": "ALL"},
  "dateRange": {"type": "LAST_DAYS", "days": 90},
  "coverageType": "PRODUCT_WITHOUT_STANDARD | ASSAY_WITHOUT_STANDARD | UNUSED_STANDARD",
  "limit": 50
}
```

输出：

```json
{
  "summaryText": "共有 3 个当前在库产品未绑定有效质量标准。",
  "groups": [
    {
      "groupLabel": "黄冰糖小颗粒（袋）",
      "coverageLabel": "未绑定有效标准",
      "affectedStockText": "1板20件",
      "riskLabels": ["无法自动判定化验合格性"]
    }
  ],
  "notes": ["无标准表示无法判定，不等同于不合格。"]
}
```

测试重点：

- 标准停用、版本、多候选口径。
- 不把无标准回答成不合格。
- `ASSAY_WITHOUT_STANDARD` 和 `UNUSED_STANDARD` 当前返回待确认错误，不由 Agent 猜测。

## 4. M1.4c 二维码 / 托盘生命周期分析

### 4.1 `query_qr_code_lifecycle`

风险等级：L1。

用途：查询一个二维码 / 托盘码从生成、打印、绑定、入库、流转、化验、出库、作废的只读生命周期摘要。

典型问题：

- 这个托盘经历了哪些环节？
- 这个二维码现在是什么状态？
- 这个托盘对应的库存和化验是什么？

输入：

```json
{
  "code": "P202607090001",
  "includeInventory": true,
  "includeAssay": true,
  "includeFlows": true,
  "includePrintInfo": true
}
```

输出：

```json
{
  "codeLabel": "P202607090001",
  "currentStatusLabel": "在库",
  "productLabel": "黄冰糖（袋）",
  "warehouseLabel": "1号库位",
  "quantityText": "1板0件",
  "assaySummary": "无有效化验",
  "timeline": [
    {"time": "2026-07-01 09:00", "eventLabel": "生成二维码", "actorLabel": "系统"},
    {"time": "2026-07-01 10:30", "eventLabel": "入库到 1号库位", "actorLabel": "张三"}
  ],
  "riskLabels": ["存在无化验库存"]
}
```

Java 资产：

- `PalletCodeController.parse`。
- `inventory`、`assay`、`flows`、`flows/cycles`。
- `PalletCodeQueryMapper`。

Planner 规则：

- 用户提供明确托盘码 / 二维码时可直接调用。
- 没有码时不得猜测，问用户扫码或输入码。
- 如果只问当前状态，可继续使用 `get_pallet_status`；如果问经历、生命周期、环节，用本工具。

前端展示：

- 时间线卡片。
- 当前状态摘要和风险标签。

测试：

- 作废码、未入库码、在库码、已出库码。
- 码不存在时不编造。

### 4.2 `query_printed_not_inbound_codes`

风险等级：L1/L2，只读统计。

用途：查询已打印 / 已生成但未完成入库的二维码或托盘码。

典型问题：

- 哪些码打印了但没入库？
- 某生产订单打印的托盘码还有哪些没入库？
- 二维码批量打印后入库完成率是多少？

输入：

```json
{
  "productScope": {"type": "ALL"},
  "orderScope": {"type": "ORDER_NO", "orderNo": "PO-20260709-001"},
  "dateRange": {"type": "LAST_DAYS", "days": 7},
  "groupBy": "batch | order | product",
  "limit": 50
}
```

待确认口径：

- “打印”使用打印任务、标签批次、二维码生成记录还是实际打印日志。
- “未入库”使用 pallet code 状态、库存关联缺失，还是入库流转缺失。

第一版建议口径：

- 若存在实际打印日志，优先使用打印日志。
- 否则使用“已生成标签批次且状态为已打印 / 已生成”的码集合。
- 未入库 = 没有关联当前在库库存，且没有入库成功流转。

输出：

```json
{
  "summaryText": "最近7天共有 120 个码已打印，其中 108 个已入库，12 个未入库，完成率 90.0%。",
  "groups": [
    {
      "groupLabel": "PO-20260709-001",
      "printedCount": 40,
      "inboundCount": 36,
      "notInboundCount": 4,
      "completionRateText": "90.0%",
      "riskLabels": ["存在已打印未入库码"]
    }
  ]
}
```

Planner 规则：

- “打印了但没入库 / 未入库码 / 入库完成率”用本工具。
- 用户提供订单号时先走订单解析或 orderScope。

测试：

- 空打印批次。
- 有打印无入库。
- 部分入库完成率。

### 4.3 `query_pallet_anomalies`

风险等级：L1/L2，只读统计。

用途：查询托盘码、库存和流转之间的不一致或风险。

典型问题：

- 哪些托盘状态异常？
- 有哪些作废码被扫描？
- 哪些托盘状态和库存不一致？
- 有没有重复入库或无入库出库？

输入：

```json
{
  "dateRange": {"type": "LAST_DAYS", "days": 30},
  "anomalyTypes": [
    "VOID_CODE_SCANNED",
    "STATUS_INVENTORY_MISMATCH",
    "DUPLICATE_INBOUND",
    "OUTBOUND_WITHOUT_INBOUND",
    "PRODUCT_BINDING_MISMATCH"
  ],
  "productScope": {"type": "ALL"},
  "warehouseScope": {"type": "ALL"},
  "limit": 50
}
```

第一版异常定义：

- `VOID_CODE_SCANNED`：作废码存在扫描 / 流转 / 操作记录。
- `STATUS_INVENTORY_MISMATCH`：托盘状态非在库但存在当前库存，或状态在库但无当前库存。
- `DUPLICATE_INBOUND`：同一码存在多次有效入库且未形成合理出库闭环。
- `OUTBOUND_WITHOUT_INBOUND`：存在出库流转但找不到有效入库链路。
- `PRODUCT_BINDING_MISMATCH`：二维码绑定产品与库存产品不一致。

输出：

```json
{
  "summaryText": "最近30天发现 3 类托盘异常，共 8 项。",
  "groups": [
    {
      "groupLabel": "状态与库存不一致",
      "count": 5,
      "examples": ["P202607090001", "P202607090002"],
      "riskLabels": ["需人工核对"]
    }
  ]
}
```

测试：

- 各异常类型独立命中。
- 没有扫描日志时应说明“当前无可用扫描日志来源”，不能断言没有发生。

### 4.4 `query_pallet_flow_records`

风险等级：L1。

用途：按托盘码、产品、库位、时间范围、事件类型查询流转记录。

典型问题：

- 某托盘最近有哪些流转？
- 黄冰糖最近 7 天托盘流转有哪些？
- 1号库位最近有哪些托盘进出？

输入：

```json
{
  "code": "P202607090001",
  "productScope": {"type": "ALL"},
  "warehouseScope": {"type": "ALL"},
  "dateRange": {"type": "LAST_DAYS", "days": 7},
  "eventTypes": ["INBOUND", "OUTBOUND", "TRANSFER", "VOID_SCAN"],
  "page": 1,
  "size": 20
}
```

输出：

```json
{
  "summaryText": "最近7天共有 12 条托盘流转记录。",
  "records": [
    {
      "time": "2026-07-09 10:12",
      "eventLabel": "入库",
      "codeLabel": "P202607090001",
      "productLabel": "黄冰糖（袋）",
      "warehouseLabel": "1号库位",
      "operatorLabel": "张三"
    }
  ]
}
```

测试重点：

- 分页限制。
- 事件类型白名单。
- 不展示内部 flow ID。

### 4.5 `query_qr_batch_inbound_completion`

风险等级：L1/L2，只读统计。

用途：针对标签批次、打印批次或生产订单统计二维码入库完成率。

典型问题：

- 某批二维码入库完成率是多少？
- 某订单打印的托盘码还有哪些没入库？

输入：

```json
{
  "batchScope": {"type": "LABEL_BATCH | ORDER_NO | DATE_RANGE", "labelBatchNo": "LB-001"},
  "includeUnfinishedExamples": true,
  "limit": 20
}
```

输出：

```json
{
  "batchLabel": "LB-001",
  "printedCount": 100,
  "inboundCount": 92,
  "notInboundCount": 8,
  "completionRateText": "92.0%",
  "unfinishedExamples": ["P202607090011", "P202607090012"],
  "summaryText": "该批二维码入库完成率为 92.0%。"
}
```

## 5. M1.4a 后续库存明细与库存可信度

### 5.1 `query_inventory_records`

风险等级：L1。

用途：对库存分布结果做受控明细下钻。

典型问题：

- 黄冰糖库存明细有哪些？
- 1号库位具体有哪些托盘？
- 刚才无化验的 2 项分别是什么？

输入：

```json
{
  "productScope": {"type": "SINGLE_PRODUCT", "productId": 84},
  "warehouseScope": {"type": "SINGLE_WAREHOUSE", "warehouseId": 1},
  "filters": {
    "assayStatus": "MISSING_ASSAY",
    "palletStatus": "INSTOCK"
  },
  "page": 1,
  "size": 20
}
```

输出：

```json
{
  "summaryText": "共查询到 2 条当前在库明细。",
  "records": [
    {
      "productLabel": "黄冰糖（袋）",
      "warehouseLabel": "1号库位",
      "stockText": "1板0件",
      "palletCodeLabel": "P2026****001",
      "latestInboundTime": "2026-05-22",
      "assayStatusLabel": "无有效化验",
      "riskLabels": ["无化验"]
    }
  ]
}
```

边界：

- 不返回 `inventoryId`。
- 托盘码是否脱敏需按业务确认；如果托盘码用于现场扫码，可展示完整业务码，但不作为内部 ID。
- 必须分页。

### 5.2 `query_inventory_ageing`

风险等级：L1/L2，只读统计。

用途：按入库时间或生产日期分析库龄。

典型问题：

- 哪些库存超过 30 天？
- 黄冰糖最久未流转的库存在哪些库位？
- 最近 7 天新增库存分布如何？

输入：

```json
{
  "productScope": {"type": "ALL"},
  "warehouseScope": {"type": "ALL"},
  "ageBasis": "INBOUND_DATE | PRODUCTION_DATE | LAST_FLOW_TIME",
  "buckets": [7, 30, 60, 90],
  "limit": 50
}
```

默认口径：

- 第一版默认使用入库时间 `INBOUND_DATE`。
- 如果用户说“生产了多久”，使用 `PRODUCTION_DATE`。
- 如果用户说“多久没动”，使用 `LAST_FLOW_TIME`，但需要确认流转数据完整。

输出：

```json
{
  "summaryText": "当前有 5 个库存分组库龄超过 60 天。",
  "groups": [
    {
      "bucketLabel": "60-90天",
      "stockText": "8板20件",
      "productCount": 3,
      "warehouseCount": 2,
      "riskLabels": ["长期未流转"]
    }
  ],
  "notes": ["库龄按入库日期计算。"]
}
```

### 5.3 `query_inventory_capacity_risks`

风险等级：L1/L2，只读统计。

用途：结合库存分布与库位容量，识别容量紧张、空置和集中存放风险。

典型问题：

- 哪些库位快满了？
- 哪些库位还空着？
- 黄冰糖是不是集中在容量紧张库位？

输入：

```json
{
  "warehouseScope": {"type": "ALL"},
  "productScope": {"type": "ALL"},
  "thresholds": {"warningRate": 0.8, "criticalRate": 0.9},
  "groupBy": "warehouse",
  "limit": 50
}
```

输出：

```json
{
  "summaryText": "共有 2 个库位占用率超过 90%。",
  "groups": [
    {
      "warehouseLabel": "1号库位",
      "occupancyRateText": "93.3%",
      "remainingCapacityText": "2个托盘位",
      "riskLabels": ["容量紧张"]
    }
  ]
}
```

## 6. M1.4d 库位容量与库位健康分析

### 6.1 `query_warehouse_capacity_distribution`

风险等级：L1/L2。

用途：查询全部或指定库区的库位容量、占用、剩余空间和空置情况。

典型问题：

- 哪些库位快满？
- 哪些库位空置？
- 现在还能放多少板？

输入：

```json
{
  "warehouseScope": {"type": "ALL"},
  "statusFilter": ["正常"],
  "occupancyBucket": "EMPTY | LOW | MEDIUM | HIGH | FULL | ANY",
  "limit": 50
}
```

输出：

```json
{
  "summaryText": "当前共有 3 个空置库位，2 个高占用库位。",
  "groups": [
    {
      "warehouseLabel": "8号库位",
      "statusLabel": "正常",
      "capacityText": "最大 10 个托盘位",
      "currentPalletText": "2 个托盘",
      "remainingCapacityText": "8 个托盘位",
      "occupancyRateText": "20.0%"
    }
  ]
}
```

### 6.2 `query_warehouse_recent_operations`

风险等级：L1。

用途：查询某库位最近入库、出库、调拨、盘点等只读操作摘要。

输入：

```json
{
  "warehouseScope": {"type": "SINGLE_WAREHOUSE", "warehouseId": 8},
  "dateRange": {"type": "LAST_DAYS", "days": 7},
  "operationTypes": ["INBOUND", "OUTBOUND", "TRANSFER"],
  "page": 1,
  "size": 20
}
```

Planner 规则：

- 必须先 `resolve_warehouses`。
- “最近发生了什么 / 最近操作”用本工具。
- “当前有什么”仍用 `get_inventory_distribution`。

### 6.3 `query_warehouse_mixed_product_risks`

风险等级：L1/L2。

用途：分析同一库位多产品、多批次、多状态混放风险。

输入：

```json
{
  "warehouseScope": {"type": "ALL"},
  "riskRules": {
    "maxProductTypesPerWarehouse": 3,
    "includeBatchMixing": true,
    "includeStatusMixing": true
  },
  "limit": 50
}
```

待确认：

- 混放是否一定为风险。
- 产品大类、规格、批次、成品/半成品混放的阈值。

## 7. M1.4e 生产订单与标签闭环分析

### 7.1 `query_production_order_progress`

风险等级：L1。

用途：查询生产订单从计划、投料、熬煮、产出、标签、入库的闭环进度。

典型问题：

- 某生产订单现在进行到哪一步？
- 这个订单还有哪些环节没完成？
- 这个订单产出的货入库了吗？

输入：

```json
{
  "orderScope": {"type": "ORDER_NO", "orderNo": "PO-20260709-001"},
  "includeMaterials": true,
  "includeOutputs": true,
  "includeLabels": true,
  "includeInbound": true
}
```

输出：

```json
{
  "orderLabel": "PO-20260709-001",
  "statusLabel": "生产中",
  "progressText": "已投料，已产出 3 托，2 托已入库。",
  "steps": [
    {"stepLabel": "投料", "statusLabel": "已完成"},
    {"stepLabel": "标签打印", "statusLabel": "部分完成"},
    {"stepLabel": "入库", "statusLabel": "部分完成"}
  ],
  "riskLabels": ["存在已产出未入库"]
}
```

前置缺口：

- 需要订单号 resolver 或受控订单搜索。

### 7.2 `query_production_label_completion`

风险等级：L1/L2。

用途：查询生产订单标签打印、绑定、入库完成情况。

典型问题：

- 某订单标签打印完成了吗？
- 打印出的标签是否全部绑定托盘并入库？

输入：

```json
{
  "orderScope": {"type": "ORDER_NO", "orderNo": "PO-20260709-001"},
  "groupBy": "label_batch | product",
  "includeUnfinishedExamples": true
}
```

输出：

```json
{
  "summaryText": "该订单共生成 20 个标签，18 个已绑定托盘，16 个已入库。",
  "printedCount": 20,
  "boundCount": 18,
  "inboundCount": 16,
  "riskLabels": ["存在已打印未绑定", "存在已绑定未入库"]
}
```

### 7.3 `query_material_pick_trace`

风险等级：L1。

用途：查询生产投料和领料追溯。

典型问题：

- 某生产订单用了哪些原料库存？
- 某批原料流向了哪些订单？

输入：

```json
{
  "orderScope": {"type": "ORDER_NO", "orderNo": "PO-20260709-001"},
  "materialScope": {"type": "PALLET_CODE | PRODUCT_SCOPE | ALL"},
  "dateRange": {"type": "LAST_DAYS", "days": 30},
  "page": 1,
  "size": 20
}
```

待确认：

- 原料库存与订单的关联字段是否完整。
- 原料、半成品、成品的产品模型是否统一。

## 8. M1.4f 操作审计与异常排查

### 8.1 `search_operation_logs`

风险等级：L1。

用途：查询业务操作日志，用于排查库存、托盘、库位或用户操作历史。

典型问题：

- 某用户最近做了哪些库存操作？
- 某托盘码相关操作日志有哪些？
- 昨天有哪些失败操作？

输入：

```json
{
  "objectScope": {
    "type": "PALLET_CODE | WAREHOUSE | PRODUCT | MODULE | USER",
    "code": "P202607090001"
  },
  "dateRange": {"type": "LAST_DAYS", "days": 7},
  "operationTypes": [],
  "resultFilter": "SUCCESS | FAILED | ANY",
  "page": 1,
  "size": 20
}
```

输出：

```json
{
  "summaryText": "最近7天查询到 5 条相关操作日志。",
  "records": [
    {
      "time": "2026-07-09 10:00",
      "operatorLabel": "张三",
      "operationLabel": "入库确认",
      "objectLabel": "P202607090001",
      "resultLabel": "成功"
    }
  ],
  "notes": ["操作日志覆盖不完整时，不能据此断言没有发生过操作。"]
}
```

权限：

- 建议管理员或审计角色。
- 普通仓库用户只能查询与自身业务权限相关的安全摘要。

### 8.2 `query_agent_tool_audit`

风险等级：L1，管理员 / 调试权限。

用途：查询 Agent 工具调用审计，用于排查用户说“AI 卡住 / 数据不对 / 没查到”的原因。

典型问题：

- 用户刚才的问题调用了哪些工具？
- 哪些工具调用失败或超时？
- 为什么回答成未查询到？

输入：

```json
{
  "agentSessionId": "optional_for_admin",
  "messageId": "optional",
  "dateRange": {"type": "LAST_DAYS", "days": 1},
  "resultCodes": ["ERROR", "TIMEOUT"],
  "page": 1,
  "size": 20
}
```

输出：

```json
{
  "summaryText": "本轮共调用 2 个工具，1 个成功，1 个失败。",
  "records": [
    {
      "toolLabel": "库存分布查询",
      "durationText": "320ms",
      "resultLabel": "失败",
      "errorLabel": "MCP 工具错误",
      "safeSummary": "参数转换失败，未返回业务数据。"
    }
  ]
}
```

安全边界：

- 管理员调试模式可显示工具名和脱敏摘要。
- 普通用户不能看到此工具结果。

### 8.3 `query_agent_answer_reviews`

风险等级：L1，管理员 / 产品运营权限。

用途：查询 `agent_message_review`，分析未正确回答、低置信度成功和用户纠正。

典型问题：

- 最近用户最常反馈哪些问题？
- 哪些问题因为缺少工具失败？
- 哪些 planner intent miss 需要修？

输入：

```json
{
  "failureDomain": "PLANNER | TOOL | MODEL | CONTEXT | DATA | PERMISSION | UI | USER_INPUT | SAFETY | SYSTEM | UNKNOWN",
  "failureCategory": "INTENT_MISS",
  "confidenceLevel": "LOW",
  "suggestedFixType": "FIX_PLANNER",
  "testCaseStatus": "NEEDED",
  "dateRange": {"type": "LAST_DAYS", "days": 7},
  "page": 1,
  "size": 20
}
```

输出：

```json
{
  "summaryText": "最近7天共有 12 条待审查回答，其中 5 条为意图识别问题。",
  "records": [
    {
      "questionTextSafe": "帮我查全部产品中最近7天的不合格库存，按产品分类",
      "answerStatusLabel": "需要审查",
      "failureLabel": "PLANNER / INTENT_MISS",
      "expectedIntentSummary": "全部产品不合格库存按产品分组",
      "actualIntentSummary": "把整句当产品名解析",
      "suggestedFixLabel": "修 planner"
    }
  ]
}
```

注意：

- 这是运营审查工具，不面向普通用户。
- 不输出 raw tool result 或链路内部敏感字段。

### 8.4 `create_regression_test_from_review`

状态：暂不进入 M1.4 工具实现。

说明：

- 从 review 生成回归测试是开发工作流能力，不是普通仓储 MCP Tool。
- 可以作为后台管理功能或开发脚本设计，不应暴露给普通 Agent 对话。

## 9. 文档和测试同步要求

每实现一个工具，必须同步更新：

- `docs/mcp/mcp-tool-registry.md`
- `docs/mcp-analysis/m14-readonly-analysis-tool-plan.md`
- `docs/mcp-analysis/m14-module-tool-design.md`
- `docs/agent/tool-capability-registry.yaml`
- 必要时更新 `agent-service/README.md`、`warehouse-mcp/README.md`

最低测试要求：

- Java：service / mapper / DTO / internal gateway 白名单 / 权限 / limit / 空结果 / 审计。
- warehouse-mcp：schema / 参数绑定 / isError 映射。
- Python：planner 路由 / 参数校验 / safe adapter / 工具失败与空结果区分 / 不泄露内部字段。
- 前端：卡片展示、移动端不溢出、按钮行为、普通 UI 不显示内部字段。
- E2E 或手工验收：至少覆盖一条自然语言主链路和一个工具失败路径。

## 10. 当前仍需业务确认的问题

1. “最近一次化验”是否统一按采样日期排序，还是按创建时间 / 报告日期。
2. “今天哪些产品没有化验”的默认全集是否为当前在库产品。
3. “打印了但没入库”的打印来源使用实际打印日志、标签批次还是二维码生成记录。
4. “托盘状态异常”的第一版异常枚举是否接受本文定义。
5. 库位容量单位是否固定为托盘位，是否存在库区级容量。
6. 混放是否一定视为风险，产品大类 / 规格 / 批次 / 状态的阈值如何定义。
7. 生产订单标签批次到托盘码、托盘码到库存记录的关联是否完整。
8. 普通用户是否允许查询操作日志摘要，还是仅管理员可见。
