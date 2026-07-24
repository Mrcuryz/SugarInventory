# 智能仓储 MCP 工具登记表

生成时间：2026-06-13
适用项目：LaibinSugarInventory / 智能仓储
用途：作为 Codex、MCP Server、后续嵌入式 Agent 的工具上下文和开发边界说明。

---

## 1. 基本原则

智能仓储 MCP 的目标不是把所有 REST API 暴露给 Agent，而是把仓储系统中明确、安全、可审计的业务能力封装成 MCP Tools。

Agent 可以：

* 理解用户自然语言意图；
* 调用 MCP Tool 查询系统状态；
* 根据工具返回结果组织回答；
* 在安全规则允许时执行预览；
* 在未来具备确认、幂等、审计后执行写操作。

Agent 不可以：

* 猜测产品 ID；
* 猜测库位 ID；
* 绕过 MCP 直接访问数据库；
* 调用任意 HTTP 接口；
* 执行任意 SQL；
* 在没有预览、确认、幂等和审计的情况下执行入库、出库、调拨等写操作；
* 把工具错误解释为空数据或业务成功。

---

## 2. 风险等级

| 等级 | 含义                         | 是否允许第一版       |
| -- | -------------------------- | ------------- |
| L0 | 静态帮助、字段说明、流程说明             | 允许            |
| L1 | 只读实时查询，不修改业务数据             | 允许            |
| L2 | 业务预览、dry-run、报表生成，不修改库存主数据 | 允许，但需审计或日志    |
| L3 | 普通业务写操作，例如入库、出库、调拨         | 暂不允许，需要后端安全能力 |
| L4 | 高风险管理操作，例如删除、回滚、权限、配置变更    | 暂不允许          |

第一版 MCP 只允许 L0、L1、部分 L2。
L3/L4 工具必须等 executionToken、idempotencyKey、Agent 审计、专用权限和强确认机制完成后才能启用。

---

## 3. 当前已实现工具

### 3.1 `resolve_products`

状态：已实现，M1
风险：L1
用途：把自然语言产品名称解析为候选产品。

典型输入：

```json
{
  "query": "黄冰糖",
  "productType": null,
  "productStatus": null,
  "limit": 10
}
```

典型输出字段：

* `resolutionStatus`
* `needsUserSelection`
* `candidates`
* `error`

当前行为：

* 唯一匹配时返回 `UNIQUE`；
* 多候选时返回 `AMBIGUOUS`；
* 无结果时返回 `NOT_FOUND`；
* 不允许 Agent 在 `AMBIGUOUS` 时自行选择产品 ID。

待增强：M1.0.1

* 增加 `ambiguityType`；
* 增加 `clarificationPrompt`；
* 增加 `options`；
* 支持产品范围选项，例如：

  * 全部黄冰糖大类；
  * 产品名称为“黄冰糖”的全部规格；
  * 某一个具体产品规格。

---

### 3.2 `resolve_warehouses`

状态：已实现，M1
风险：L1
用途：把自然语言库位名称解析为候选库位。

典型输入：

```json
{
  "query": "2号库位",
  "onlyAvailable": true,
  "limit": 10
}
```

典型输出字段：

* `resolutionStatus`
* `needsUserSelection`
* `candidates`
* `error`

当前行为：

* 唯一匹配时返回 `UNIQUE`；
* 多候选时返回 `AMBIGUOUS`；
* 无结果时返回 `NOT_FOUND`。

待增强：M1.0.1

需要支持中文自然库位名称归一化：

| 用户输入    | 应归一化为 |
| ------- | ----- |
| `2号库位`  | `2`   |
| `2号库`   | `2`   |
| `2号位`   | `2`   |
| `库位2`   | `2`   |
| `二号库位`  | `2`   |
| `十二号库位` | `12`  |
| `2#`    | `2`   |

注意：

* 归一化后的数字应优先按 `warehouseName` 匹配；
* 不得直接把归一化数字当作 `warehouseId`；
* 匹配成功时返回 `matchType=NORMALIZED_NAME`；
* 应返回 `matchReason`，例如：将“2号库位”归一化为“2”后匹配库位名称。

---

### 3.3 `get_inventory_overview`

状态：已实现，M1
风险：L1
用途：查询产品库存汇总和明细。

典型调用链：

```text
用户：查一下黄冰糖现在还有多少库存
  ↓
resolve_products
  ↓
若 UNIQUE，则 get_inventory_overview(productId)
  ↓
返回库存汇总
```

当前要求：

* 优先使用 `productId`；
* 如果只有产品名称，必须先调用 `resolve_products`；
* 如果产品解析为 `AMBIGUOUS`，不得继续调用本工具；
* 不得猜测产品 ID。

当前库存数量字段口径：

* `get_inventory_overview` 不再返回 `totalBoards`、`totalPieces`、`stockInfo`；
* MCP 输出使用 `rawFullPallets`、`rawLoosePieces`、`normalizedPallets`、`normalizedLoosePieces`、`totalEquivalentPieces`、`displayStockInfo`；
* `rawFullPallets` 只表示后端原始整板数；
* `rawLoosePieces` 只表示后端原始散件数；
* `normalizedPallets` 和 `normalizedLoosePieces` 是按产品 `piecesPerPallet` 折算后的标准展示数量；
* `totalEquivalentPieces` 是等价总件数；
* `displayStockInfo` 是面向用户展示的板件文本；
* Agent 回答库存时应优先使用 `normalizedPallets + normalizedLoosePieces + totalEquivalentPieces`，不要把 raw 字段解释为最终标准化库存。

待增强：

* 支持产品大类聚合查询；
* 支持产品名称组聚合查询；
* 支持更清晰的板数、散件、总件数、总重量口径说明。

---

### 3.4 `get_warehouse_status`

状态：已实现，M1
风险：L1
用途：查询库位容量、库存明细和最近操作。

典型调用链：

```text
用户：2号库位现在是什么情况
  ↓
resolve_warehouses
  ↓
若 UNIQUE，则 get_warehouse_status(warehouseId)
  ↓
返回库位状态
```

当前要求：

* 优先使用 `warehouseId`；
* 如果只有库位名称，必须先调用 `resolve_warehouses`；
* 如果库位解析为 `AMBIGUOUS`，不得继续调用本工具；
* 如果库位解析为 `NOT_FOUND`，不得编造库位。

待增强：

* 配合 `resolve_warehouses` 支持“2号库位”到数据库库位名“2”的归一化；
* 回答时说明匹配关系，例如：“已按 2号库位 匹配到库位 2”。

---

## 4. M1.0.1 解析器增强工具计划

M1.0.1 不新增工具，只增强已实现 resolver。

### 4.1 产品歧义增强

目标：

当用户输入“黄冰糖”时，系统应区分：

1. 黄冰糖产品大类；
2. 产品名称为“黄冰糖”的全部规格；
3. 某一个具体产品规格。

新增返回字段建议：

```json
{
  "ambiguityType": "PRODUCT_SCOPE",
  "clarificationPrompt": "你想查询哪一种黄冰糖库存？",
  "options": [
    {
      "optionType": "PRODUCT_TYPE_GROUP",
      "displayLabel": "全部黄冰糖大类",
      "supported": false
    },
    {
      "optionType": "EXACT_PRODUCT_NAME_GROUP",
      "displayLabel": "产品名称为“黄冰糖”的全部规格",
      "supported": false
    },
    {
      "optionType": "SINGLE_PRODUCT",
      "productId": 84,
      "displayLabel": "黄冰糖（袋）",
      "supported": true
    }
  ]
}
```

Agent 行为：

* `UNIQUE`：可以继续查询；
* `AMBIGUOUS`：必须展示选项并询问用户；
* `NOT_FOUND`：说明未找到，不得编造 ID。

---

### 4.2 库位名称归一化增强

目标：

让“2号库位”“二号库位”“库位2”等自然语言表达匹配数据库中的库位名“2”。

Agent 行为：

* 如果 resolver 返回 `UNIQUE` 且 `matchType=NORMALIZED_NAME`，可以继续查询；
* 回答中应说明匹配关系；
* 如果返回 `AMBIGUOUS`，必须询问用户；
* 如果返回 `NOT_FOUND`，不得继续查询库位状态。

---

## 5. M1.1 计划工具

M1.1 只增加 L1 查询工具，不增加预览和执行。

### 5.1 `get_pallet_status`

状态：已实现，M1.1
风险：L1
用途：扫码或输入托盘码后，查询托盘当前状态、产品、位置、任务、化验和流转。

输入建议：

```json
{
  "code": "P202606130001",
  "includeFlows": true,
  "includeAssay": true,
  "includeInventory": true
}
```

输出建议：

* `palletInfo`
* `inventory`
* `assay`
* `flowCycles`
* `flows`
* `warnings`
* `error`

底层只读接口：

* `GET /api/pallet-codes/parse`
* `GET /api/pallet-codes/{code}/inventory`
* `GET /api/pallet-codes/{code}/assay`
* `GET /api/pallet-codes/{code}/flows/cycles`
* `GET /api/pallet-codes/{code}/flows`

禁止行为：

* 不得作废二维码；
* 不得恢复二维码；
* 不得创建任务；
* 不得确认任务；
* 不得修改托盘状态。

---

### 5.2 `get_assay_status`

状态：已实现，M1.1
风险：L1
用途：查询某产品某日期或某化验 ID 的化验结果、判定结果和异常指标。

输入建议：

```json
{
  "productId": 84,
  "productionDate": "2026-06-13"
}
```

或者：

```json
{
  "assayId": 1001
}
```

输出建议：

* `assay`
* `judgeResult`
* `failedMetrics`
* `appliedStandard`
* `needsAssay`
* `warnings`
* `error`

底层只读接口：

* `GET /api/assay/by-product-date`
* `GET /api/assay/{id}`

禁止行为：

* 不得导入化验；
* 不得更新化验；
* 不得删除化验；
* 不得修改质量标准。

---

### 5.3 `get_production_order_trace`

状态：旧聚合候选，已由 `resolve_production_entities`、`query_production_order_progress`、`query_material_pick_trace` 和 `query_boiling_batch_trace` 分步实现
风险：L1
用途：查询生产订单领料、产出、标签和入库进度。

输入建议：

```json
{
  "orderRef": "受控生产订单引用",
  "includeMaterials": true,
  "includeOutputs": true,
  "includeLabels": true
}
```

输出建议：

* `baseInfo`
* `boilingSources`
* `materials`
* `outputs`
* `labelBatches`
* `progress`
* `warnings`
* `error`

当前实现：

* 先用 `resolve_production_entities` 解析订单号或具体煮糖批次号；
* 用 `query_boiling_batches` 按可选产品、北京时间业务日期范围和可选状态列出已登记煮糖批次；产品不是时间范围查询的必填条件；
* 用 `query_production_order_progress` 查询订单当前进度、关联煮糖批次的投入/预留、实际领料、产出明细与已确认的实际入库去向；
* 用 `query_material_pick_trace` 查询实际登记的领料与来源托盘；
* 用 `query_boiling_batch_trace` 查询煮糖批次使用记录和流转摘要。

生产阶段语义约束：

* `boilingSources` 表示订单与煮糖批次之间已登记的投入、引用或预留关系，必须携带用户可读数量和状态；
* `RESERVED` 等预留状态不得累计到实际领料、实际消耗或实际产出中；只有相应业务记录真正完成后，才可作为实际事实统计；
* 一个批次关联多个订单时，用户明确要求“这两个/这些/全部”可在受控上限内分别查询并返回多张订单卡片；否则返回结构化候选项供选择；
* 返回给普通用户的文本和卡片必须映射为中文业务状态，不得直接展示 `PREPRINTED`、`USED_UP`、`RESERVED` 等内部枚举值。

实际入库去向判定：

* 只有产出二维码已经关联库存、已有入库时间，或状态明确为 `INSTOCK` 时，才计入实际入库去向；
* 任务目标库位仅代表待执行目标，不得作为实际入库事实返回；
* 输出只包含库位名称、入库码数量和托盘码，不返回订单、产出、库存等内部数据库 ID。

---

### 5.4 `search_operation_logs`

状态：已实现，Agent v1 audit-01
风险：L1
用途：查询操作日志，用于审计和问题排查。

输入建议：

```json
{
  "tableName": "inventory",
  "operator": "张三",
  "startTime": "2026-06-01 00:00:00",
  "endTime": "2026-06-13 23:59:59",
  "page": 1,
  "size": 20
}
```

输出建议：

* `total`
* `records`
* `error`

权限：

* `log:view`

注意：

* 当前系统操作日志覆盖不完整；
* Agent 不得把日志缺失解释为“没有发生过操作”。

---

## 6. 化验分析与报表工具计划

### 6.1 `query_assay_records`

状态：已实现，M1.4b
风险：L1
用途：按产品、日期范围、合格状态查询化验记录。

用户示例：

```text
帮我分析最近30天黄冰糖的化验数据。
```

输入建议：

```json
{
  "productId": 84,
  "startDate": "2026-05-14",
  "endDate": "2026-06-13",
  "isQualified": null,
  "page": 1,
  "size": 100
}
```

输出建议：

* `records`
* `summary`
* `pageInfo`
* `warnings`
* `error`

当前实现：

* 如果产品名称存在歧义，必须先 `resolve_products`；
* 支持 `SINGLE_PRODUCT`、`EXACT_PRODUCT_NAME_GROUP`、`PRODUCT_TYPE_GROUP`、`ALL` 受控产品范围；
* 支持 `EXACT`、`LAST_DAYS`、`RANGE` 采样日期范围；
* 支持 `ANY`、`PASS`、`FAILED`、`NO_STANDARD`、`MULTIPLE_CANDIDATES` 判定过滤，其中 `FAILED` 在后端映射为底层 `FAIL`；
* 默认按 `sampleDate DESC, createdAt DESC` 查询，`size` 最大 100；
* 输出只包含业务标签、摘要、分页和安全 `recordRef`，不向普通 UI 暴露 `assayId` 或 `productId`；
* 大数据量必须分页，不得一次返回过多原始记录。

---

### 6.2 `analyze_assay_records`

状态：未实现，计划 M2 或 M5
风险：L2
用途：对化验记录做统计分析，不修改业务数据。

输入建议：

```json
{
  "productId": 84,
  "startDate": "2026-05-14",
  "endDate": "2026-06-13",
  "groupBy": ["date", "metric"],
  "includeOutliers": true
}
```

输出建议：

* `recordCount`
* `qualificationRate`
* `metricStats`
* `failedMetrics`
* `outliers`
* `trendSummary`
* `warnings`
* `analysisId`
* `error`

建议：

* 统计计算优先在后端或分析服务中完成；
* 模型只负责解释和总结；
* 不建议把大量原始化验明细全部塞进模型上下文。

---

### 6.3 `export_assay_report`

状态：未实现，计划 M5
风险：L2
用途：导出化验分析报表，例如 Excel 或 PDF。

输入建议：

```json
{
  "analysisId": "assay_analysis_20260613_001",
  "format": "XLSX",
  "includeRawRecords": true,
  "includeCharts": true
}
```

输出建议：

* `fileId`
* `fileName`
* `downloadUrl`
* `expiresAt`
* `error`

权限建议：

* `quality:test`
* 或新增 `agent:assay:export`

审计要求：

* 必须记录导出人；
* 必须记录产品、日期范围、文件类型；
* 不得导出超出当前用户权限范围的数据。

---

## 7. M2 预览类工具计划

M2 工具只做 dry-run，不修改库存主数据。

这些工具当前尚未实现，因为后端缺少真正的 Agent 聚合预览接口。

### 7.1 `preview_auto_inbound_report`

状态：未实现
风险：L2
用途：解析自然语言报数文本，返回待确认任务、缺失项和风险，不执行入库。

输入建议：

```json
{
  "rawText": "今天黄冰糖半成品入2板到2号库位",
  "entryDate": "2026-06-13",
  "clientRequestId": "uuid"
}
```

输出建议：

* `batchId`
* `tasks`
* `missingFields`
* `warnings`
* `candidateResolutions`
* `canExecute`

注意：

* 当前自动入库 parse 依赖 LLM 输出 ID；
* 后续应加入确定性产品和库位解析；
* 预览不得分配二维码；
* 预览不得写库存。

---

### 7.2 `preview_inbound_plan`

状态：未实现，阻塞于后端 dry-run
风险：L2
用途：预览入库会占用哪些库位、是否容量不足、是否缺化验或筛网。

建议后端接口：

```text
POST /api/agent/inbound/preview
```

输入建议：

```json
{
  "items": [
    {
      "productId": 84,
      "productStatus": "成品",
      "productionDate": "2026-06-13",
      "quantity": 1,
      "unit": "PALLET",
      "warehouseId": 2,
      "side": "LEFT"
    }
  ],
  "mode": "traditional"
}
```

输出建议：

* `planId`
* `resolvedItems`
* `targetLocations`
* `capacityCheck`
* `requiredAssays`
* `blockingIssues`
* `warnings`
* `riskLevel`

禁止行为：

* 不得写 `inventory`；
* 不得写 `in_stock`；
* 不得写 `semi_product_record`；
* 不得写 `pallet_task`；
* 不得改变托盘码状态。

---

### 7.3 `preview_outbound_plan`

状态：未实现，阻塞于后端 dry-run
风险：L2
用途：预览出库会扣哪些库存批次或托盘，是否库存不足，是否缺化验。

建议后端接口：

```text
POST /api/agent/outbound/preview
```

输出建议：

* `planId`
* `selectedInventory`
* `deductions`
* `shortage`
* `assayCheck`
* `blockingIssues`
* `warnings`

禁止行为：

* 不得扣减库存；
* 不得删除库存行；
* 不得写 `out_stock`；
* 不得创建托盘出库任务。

---

### 7.4 `preview_transfer_plan`

状态：未实现，阻塞于后端 dry-run
风险：L2
用途：预览托盘或库存调拨目标位置、容量冲突和同位置冲突。

建议后端接口：

```text
POST /api/agent/transfer/preview
```

输出建议：

* `planId`
* `resolvedPallets`
* `sourceLocations`
* `targetCandidates`
* `capacityCheck`
* `sameLocationConflicts`
* `blockingIssues`

禁止行为：

* 不得创建调拨任务；
* 不得移动库存；
* 不得改变托盘状态；
* 不得写流转记录。

---

## 8. M3/M4 执行类工具计划

执行类工具暂不实现。

### 8.1 执行类前置条件

启用任何 L3 执行工具前，必须完成：

* executionToken；
* idempotencyKey；
* Agent 操作审计；
* 专用权限点；
* 用户明确确认；
* 预览和执行内容绑定；
* 执行前重新校验库存、库位、托盘状态；
* 并发冲突处理；
* 结构化错误码。

---

### 8.2 `execute_inbound_plan`

状态：未实现，未来 M4
风险：L3
用途：执行已预览且经用户确认的入库。

输入建议：

```json
{
  "executionToken": "token",
  "idempotencyKey": "uuid",
  "confirmationText": "确认将黄冰糖1板入库到2号库位"
}
```

禁止：

* 不得直接接收完整入库 DTO 并执行；
* 不得绕过 preview；
* 不得接受 Agent 临时改写后的 productId、warehouseId、quantity。

---

### 8.3 `execute_outbound_plan`

状态：未实现，未来 M4
风险：L3
用途：执行已预览且经用户确认的出库。

要求：

* 必须基于 `preview_outbound_plan` 的执行 token；
* 必须有幂等键；
* 必须记录扣减明细；
* 必须记录用户确认文本。

---

### 8.4 `execute_transfer_plan`

状态：未实现，未来 M4
风险：L3
用途：执行已预览且经用户确认的调拨。

要求：

* 必须基于 `preview_transfer_plan` 的执行 token；
* 必须有幂等键；
* 必须记录源库位、目标库位、托盘码和确认文本。

---

## 9. L4 管理类能力

状态：暂不实现
风险：L4

管理类能力包括：

* 产品新增、修改、删除；
* 库位新增、修改、删除、维护；
* 筛网维护；
* 质量标准维护；
* 产品-标准关系维护；
* 角色权限维护；
* 员工清理；
* 二维码作废、恢复；
* 历史流转删除；
* 库存回滚；
* 批量调整库存。

第一版和第二版均不得开放通用管理工具。

未来如果需要，应拆成单独工具，每个工具必须具备：

* 强权限；
* 强确认；
* 前后差异预览；
* 审批或二次确认；
* 操作审计；
* 不允许任意字段更新。

明确禁止：

```text
manage_reference_data
execute_sql
call_any_api
update_any_table
delete_any_record
```

---

## 10. 未覆盖需求处理规则

当用户提出当前工具无法完成的任务时，Agent 应进入“能力缺口处理流程”。

例如用户说：

```text
帮我分析最近30天黄冰糖的化验数据并导出报表。
```

如果当前没有完整的 `analyze_assay_records`、`export_assay_report`，Agent 不得编造趋势分析结果，不得假装已经导出。

应回复：

```text
当前 MCP 已支持受控化验记录查询，但还没有完整的化验趋势分析和报表导出工具，无法安全完成“分析并导出”的全部任务。

我目前可以完成的部分是：
1. 解析产品；
2. 查询受控产品范围内的化验记录列表和摘要；
3. 查询单个产品日期的化验状态；
4. 生成需要新增的 MCP 工具规格。

要完整支持这个需求，需要新增：
- analyze_assay_records
- export_assay_report
```

并记录未覆盖意图。

---

## 11. 未覆盖意图记录建议

后续建议新增 unmet_intent 日志，字段包括：

* `userId`
* `rawUserInput`
* `recognizedIntent`
* `missingTools`
* `relatedDomain`
* `currentPage`
* `userRole`
* `createdAt`
* `priority`
* `suggestedToolSpec`

用途：

* 统计真实用户最常问但未覆盖的问题；
* 指导 MCP 工具迭代；
* 避免一次性交付时遗漏业务场景。

---

## 12. Agent 调用规则

### 12.1 Resolver 规则

当 resolver 返回 `UNIQUE`：

* 可以继续调用后续查询工具。

当 resolver 返回 `AMBIGUOUS`：

* 必须展示候选项；
* 必须询问用户；
* 不得自行选择 ID；
* 不得继续调用库存或库位详情工具。

当 resolver 返回 `NOT_FOUND`：

* 不得编造 ID；
* 应说明未找到；
* 可展示建议查询方式。

当 resolver 返回 `NORMALIZED_NAME`：

* 可以继续查询；
* 回答时应说明匹配关系。

---

### 12.2 查询工具规则

查询工具可以自动调用，但必须满足：

* 关键实体已唯一确定；
* 用户具备权限；
* 工具失败不得解释为空数据；
* 返回数据过多时必须分页或摘要。

---

### 12.3 预览工具规则

预览工具可以在未来自动调用，但必须满足：

* 不写库存；
* 不创建任务；
* 不改变托盘状态；
* 不生成真实业务单；
* 返回 blockingIssues 和 warnings；
* 结果不能直接当作执行依据，除非后端签发 executionToken。

---

### 12.4 执行工具规则

执行工具未来必须满足：

* 用户明确确认；
* 后端 executionToken；
* idempotencyKey；
* 专用权限；
* 执行前重新校验；
* Agent 操作审计；
* 执行结果返回业务单号或任务号。

当前阶段禁止实现执行工具。

---

## 13. 工具状态总表

| 工具名                           | 状态            |        阶段 | 风险 | 是否已实现 | 说明            |
| ----------------------------- | ------------- | --------: | -: | ----- | ------------- |
| `resolve_products`            | 已实现，需增强       | M1/M1.0.1 | L1 | 是     | 产品解析，需增强人类消歧  |
| `resolve_warehouses`          | 已实现，需增强       | M1/M1.0.1 | L1 | 是     | 库位解析，需支持中文归一化 |
| `get_inventory_overview`      | 已实现           |        M1 | L1 | 是     | 产品库存查询        |
| `get_warehouse_status`        | 已实现           |        M1 | L1 | 是     | 库位状态查询        |
| `get_pallet_status`           | 已实现           |      M1.1 | L1 | 是     | 托盘码状态和流转查询    |
| `get_assay_status`            | 已实现           |      M1.1 | L1 | 是     | 单产品/单日期化验状态查询 |
| `get_production_order_trace`  | 已由分步工具替代 | M1.1/M1.2 | L1 | 否 | 不再建设单一大而全工具 |
| `query_assay_records`         | 已实现          |     M1.4b | L1 | 是     | 受控化验记录范围查询    |
| `get_assay_report_detail`     | 已实现          |     M1.4b | L1 | 是     | 受控化验报告详情查询    |
| `query_assay_abnormalities`   | 已实现          |     M1.4b | L1 | 是     | 受控化验质量异常聚合查询 |
| `query_products_without_recent_assay` | 已实现 | M1.4b | L1 | 是 | 受控缺化验在库分组查询 |
| `query_assay_standard_coverage` | 已实现 | M1.4b | L1 | 是 | 当前在库产品质量标准覆盖查询 |
| `query_unqualified_inventory` | 已实现 | Agent v1 quality-02 | L1 | 是 | 当前库存批次最新化验明确判定为不合格的库存筛选 |
| `query_inventory_by_quality_standard` | 已实现 | Agent v1 quality-02 | L1 | 是 | 以当前批次最新化验原始值逐项匹配指定完整标准 |
| `query_inventory_by_assay_metrics` | 已实现 | Agent v1 quality-02 | L1 | 是 | 按单一白名单原始化验指标和数值条件筛选当前库存 |
| `query_qr_code_lifecycle` | 已实现 | M1.4c | L1 | 是 | 单个二维码 / 托盘码生命周期查询 |
| `query_printed_not_inbound_codes` | 已实现 | M1.4c | L1/L2 | 是 | 已打印但未完成入库的二维码分组查询 |
| `query_pallet_anomalies` | 已实现 | M1.4c | L1/L2 | 是 | 托盘状态、库存和流转异常聚合查询 |
| `query_pallet_flow_records` | 已实现 | M1.4c | L1 | 是 | 受控托盘流转记录分页查询 |
| `query_qr_batch_inbound_completion` | 已实现 | M1.4c | L1/L2 | 是 | 二维码标签批次入库完成率查询 |
| `resolve_production_entities` | 已实现 | Agent v1 production-01 | L1 | 是 | 生产订单/煮糖批次受控实体解析 |
| `query_boiling_batches` | 已实现 | Agent v1 production-02 | L1 | 是 | 按可选产品、北京时间业务日期范围和状态列出煮糖批次，返回后续详情查询所需的受控候选引用 |
| `query_production_order_progress` | 已实现 | Agent v1 production-01 | L1 | 是 | 生产订单关联煮糖批次投入/预留、实际领料、产出、标签、入库进度及有事实依据的产出入库去向 |
| `query_boiling_batch_trace` | 已实现 | Agent v1 production-02 | L1 | 是 | 煮糖批次已登记详情、使用记录和追溯关系 |
| `query_material_pick_trace` | 已实现 | Agent v1 production-03 | L1 | 是 | 生产订单已登记实际领料和托盘来源 |
| `query_production_label_completion` | 已实现 | Agent v1 production-04 | L1 | 是 | 生产订单标签预留/使用/回收与二维码绑定/入库完成度 |
| `query_in_process_materials` | 已实现 | Agent v1 production-05 | L1 | 是 | 当前已登记在制半成品领料记录分页查询 |
| `query_material_candidates` | 已实现 | Agent v1 production-05 | L1 | 是 | 受控生产订单的当前半成品库存候选查询 |
| `query_pallet_tasks` | 已实现 | Agent v1 logistics-01 | L1 | 是 | 当前轮次托盘任务安全分页查询；`task:view` |
| `query_stock_documents` | 已实现 | Agent v1 logistics-02 | L1 | 是 | 明确来源的入库、出库或半成品单据查询；`record:query` |
| `query_auto_inbound_batches` | 已实现 | Agent v1 logistics-03 | L1 | 是 | 当前用户 Redis 中未过期的近期智能报数批次；不确认入库 |
| `get_auto_inbound_batch_detail` | 已实现 | Agent v1 logistics-03 | L1 | 是 | 通过用户绑定受控引用读取安全详情；不返回原始文本和内部 ID |
| `query_warehouse_capacity_distribution` | 已实现 | Agent v1 warehouse-01 | L1 | 是 | 当前库位容量、占用和剩余容量事实；展示分段不作为业务风险判断 |
| `query_warehouse_recent_operations` | 已实现 | Agent v1 warehouse-02 | L1 | 是 | 已登记托盘流转的近期入库、出库和调拨事件；不是完整审计账 |
| `query_warehouse_mixed_storage_facts` | 已实现 | Agent v1 warehouse-03 | L1 | 是 | 同库位多产品/多规格客观事实；不作混放风险判断 |
| `query_product_catalog` | 已实现 | Agent v1 master-data-01 | L1 | 是 | 当前产品目录配置；`product:view`；不代表库存或质量状态 |
| `get_product_detail` | 已实现 | Agent v1 master-data-01 | L1 | 是 | 按准确唯一名称查询产品详情与换算配置；不返回内部 ID |
| `query_screen_mesh_catalog` | 已实现 | Agent v1 master-data-01 | L1 | 是 | 当前筛网目录；`screen_mesh:view`；不代表实际使用情况 |
| `query_assay_groups` | 已实现 | Agent v1 quality-01 | L1 | 是 | 当前化验产品分组；`assay:view`；不是标准或合格范围 |
| `query_quality_standard_catalog` | 已实现 | Agent v1 quality-01 | L1 | 是 | 当前质量标准目录及版本状态；`quality_standard:view` |
| `get_quality_standard_detail` | 已实现 | Agent v1 quality-01 | L1 | 是 | 按准确代码和版本查询指标配置；LLM 不作最终判定 |
| `query_product_standard_relations` | 已实现 | Agent v1 quality-01 | L1 | 是 | 产品当前标准绑定、默认项和生效区间；不证明报告采用或产品合格 |
| `query_product_quality_configuration` | 已实现 | Agent v1 quality-02 | L1 | 是 | 按已确认产品聚合当前适用质量标准和所属批量化验组；不按名称推断关系 |
| `query_employee_roster` | 已实现 | Agent v1 administration-01 | L1 | 是 | 当前员工名册；`rbac:user:view`；手机号脱敏且不返回凭据/绑定信息 |
| `query_roles` | 已实现 | Agent v1 administration-01 | L1 | 是 | 当前角色目录和汇总数量；不返回内部 ID 或员工姓名清单 |
| `get_role_permission_summary` | 已实现 | Agent v1 administration-01 | L1 | 是 | 按准确角色编码/名称查询权限摘要；实际访问仍重新鉴权 |
| `search_operation_logs` | 已实现 | Agent v1 audit-01 | L1 | 是 | 业务操作日志安全摘要；只返回变更字段名，不返回 old/new 值 |
| `query_agent_tool_audit` | 已实现 | Agent v1 audit-01 | L1 | 是 | Agent 调用结果/错误类别与耗时；不返回参数、Prompt、内部 ID 或堆栈 |
| `query_agent_answer_reviews` | 已实现 | Agent v1 audit-01 | L1 | 是 | 回答 Review 状态安全摘要；不返回原问题、原回答或决策快照 |
| `query_inventory_ledger` | 已实现 | Agent v1 inventory-01 | L1 | 是 | 当前库存行台账；不是历史流水，不证明批次合格 |
| `query_prepare_pool_balance` | 已实现 | Agent v1 inventory-01 | L1 | 是 | 历史半成品备料池当前正余额；不代表订单预留或可领用 |
| `query_fixed_product_qr_pool` | 已实现 | Agent v1 pallet-02 | L1 | 是 | 固定产品码池当前状态；可打印不表示已打印/启用，不执行任何码状态写入 |
| `analyze_assay_records`       | 计划            |     M2/M5 | L2 | 否     | 化验统计分析        |
| `export_assay_report`         | 计划            |        M5 | L2 | 否     | 化验报表导出        |
| `preview_auto_inbound_report` | 计划            |        M2 | L2 | 否     | 自然语言报数预览      |
| `preview_inbound_plan`        | 阻塞于后端 dry-run |        M2 | L2 | 否     | 入库预览          |
| `preview_outbound_plan`       | 阻塞于后端 dry-run |        M2 | L2 | 否     | 出库预览          |
| `preview_transfer_plan`       | 阻塞于后端 dry-run |        M2 | L2 | 否     | 调拨预览          |
| `execute_inbound_plan`        | 未来            |        M4 | L3 | 否     | 入库执行          |
| `execute_outbound_plan`       | 未来            |        M4 | L3 | 否     | 出库执行          |
| `execute_transfer_plan`       | 未来            |        M4 | L3 | 否     | 调拨执行          |
| `manage_reference_data`       | 不建议作为单一工具     |        未来 | L4 | 否     | 应拆分为多个强权限工具   |
| `execute_sql`                 | 禁止            |        永不 | L4 | 否     | 不允许           |
| `call_any_api`                | 禁止            |        永不 | L4 | 否     | 不允许           |
| `update_any_table`            | 禁止            |        永不 | L4 | 否     | 不允许           |

---

## 14. 推荐迭代路线

### M1：只读基础查询

已完成：

* `resolve_products`
* `resolve_warehouses`
* `get_inventory_overview`
* `get_warehouse_status`

### M1.0.1：解析器增强

待做：

* 产品范围消歧；
* 库位自然语言归一化；
* Agent 消歧提示结构。

### M1.1：托盘和化验查询

已完成：

* `get_pallet_status`
* `get_assay_status`

### M1.2：生产订单和日志查询

历史计划中：

* `get_production_order_trace`

`search_operation_logs` 已由 Agent v1 audit-01 完成，不再属于未实现项。

### M2：预览能力

计划：

* `preview_auto_inbound_report`
* `preview_inbound_plan`
* `preview_outbound_plan`
* `preview_transfer_plan`

前置：

* 后端新增 dry-run 聚合接口；
* 不得写业务表。

### M3：执行安全基础

计划：

* executionToken；
* idempotencyKey；
* agent_operation_audit；
* agent:* 权限点；
* 结构化错误码。

### M4：执行类工具

计划：

* `execute_inbound_plan`
* `execute_outbound_plan`
* `execute_transfer_plan`

### M5：报表分析

计划：

* `query_assay_records`
* `analyze_assay_records`
* `export_assay_report`
* 库存日报、异常趋势、质量分析等报表工具。

---

## 15. 项目上下文使用要求

Codex 在处理以下任务前必须读取本文件：

* 修改 MCP Tool；
* 新增 MCP Tool；
* 评估自然语言需求是否已覆盖；
* 设计 Agent 流程；
* 实现预览或执行工具；
* 处理用户提出的未覆盖需求。

如果用户需求没有被本文件覆盖，Codex 应先输出“能力缺口分析”，不得直接实现高风险工具。

---

## 11. M1.2 Agent Gateway

M1.2 不新增仓储业务 MCP Tool，目标是把“当前登录用户 -> Agent 会话 -> MCP 工具调用 -> 后端鉴权 -> 前端回答”串起来。

当前生产化入口：

* Web 用户登录后点击“AI 助手”；
* 前端调用 `POST /api/agent/sessions` 创建 Agent 会话；
* 前端只接收 `agentSessionId`、用户/角色/权限、scope、过期时间和状态；
* 前端调用 `POST /api/agent/sessions/{agentSessionId}/messages` 发送自然语言消息；
* 后端 `AgentGatewayService` 绑定当前登录用户，调用模型抽象和 `smart_warehouse` MCP；
* MCP 使用后端内部注入的用户委托身份调用仓储后端；
* Gateway 返回自然语言回答、工具调用摘要和需要用户选择的候选项。

授权边界：

* 不实现 MCP 登录工具；
* 不向前端、Agent、LLM 或 MCP Tool 输出返回 `delegationToken`；
* `X-Agent-Tool-Name` 只用于审计辅助，不作为权限依据；
* Codex 本地仍可用 `STATIC_TOKEN` 做开发验收；
* 产品化 Web/小程序 Agent 使用 `USER_DELEGATED`；
* 当前 STDIO 一用户一 MCP 进程只是过渡方案；
* 生产多用户推荐 HTTP/Streamable HTTP MCP，以支持请求级用户身份注入。

M1.2 完成时只有以下 6 个已实现业务工具：

* `resolve_products`
* `resolve_warehouses`
* `get_inventory_overview`
* `get_warehouse_status`
* `get_pallet_status`
* `get_assay_status`

M1.3/M1.4 及 Agent v1 全模块查询完成后，当前白名单已扩展为 52 个只读业务工具。在原 6 个基础工具之外，新增：

* `get_inventory_distribution`
* `query_assay_records`
* `get_assay_report_detail`
* `query_assay_abnormalities`
* `query_products_without_recent_assay`
* `query_assay_standard_coverage`
* `query_unqualified_inventory`
* `query_inventory_by_quality_standard`
* `query_inventory_by_assay_metrics`
* `query_qr_code_lifecycle`
* `query_printed_not_inbound_codes`
* `query_pallet_anomalies`
* `query_pallet_flow_records`
* `query_qr_batch_inbound_completion`
* `resolve_production_entities`
* `query_boiling_batches`
* `query_production_order_progress`
* `query_boiling_batch_trace`
* `query_material_pick_trace`
* `query_production_label_completion`
* `query_in_process_materials`
* `query_material_candidates`
* `query_pallet_tasks`
* `query_stock_documents`
* `query_auto_inbound_batches`
* `get_auto_inbound_batch_detail`
* `query_warehouse_capacity_distribution`
* `query_warehouse_recent_operations`
* `query_warehouse_mixed_storage_facts`
* `query_product_catalog`
* `get_product_detail`
* `query_screen_mesh_catalog`
* `query_assay_groups`
* `query_quality_standard_catalog`
* `get_quality_standard_detail`
* `query_product_standard_relations`
* `query_product_quality_configuration`
* `query_employee_roster`
* `query_roles`
* `get_role_permission_summary`
* `search_operation_logs`
* `query_agent_tool_audit`
* `query_agent_answer_reviews`
* `query_inventory_ledger`
* `query_prepare_pool_balance`
* `query_fixed_product_qr_pool`

仍禁止：

* login MCP Tool；
* `preview_*`；
* `execute_*`；
* 任意 SQL 工具；
* 任意 HTTP 代理工具；
* 直接数据库访问工具；
* 入库、出库、调拨、托盘确认、二维码作废/恢复、化验导入/更新/删除、质量标准修改等写接口调用。

详见 `docs/mcp/mcp-auth-design.md`。

---

## 16. AI 助手产品目标

MCP 工具是智能仓储 AI 助手的内部能力层，不是普通用户界面。最终产品体验和路线图以 `docs/agent/ai-assistant-product-goal.md` 为准。

处理 AI 助手、Agent Gateway、自然语言交互、前端助手体验、工具展示方式、上下文记忆或查询/写入边界相关任务时，必须同时阅读：

* `docs/agent/ai-assistant-product-goal.md`
* `docs/mcp/mcp-tool-registry.md`

核心边界：

* 普通用户和助手对话，不和工具、接口、ID 对话；
* 普通 UI 不展示 `toolName`、`SUCCESS`、`productId`、`warehouseId`、raw JSON 或内部错误；
* 管理员调试模式才展示脱敏工具摘要；
* 查询/分析可走受控只读数据访问层；
* 写操作必须 `preview -> 用户确认 -> executionToken -> idempotencyKey -> execute`；
* MCP Server 负责安全工具能力，不直接负责用户对话体验。

---

## 17. M1.4a 库存分布工具

### `get_inventory_distribution`

状态：已实现（M1.4a-1 单品首版；M1.4a-2 多范围、多过滤完整形态）

风险等级：L1，只读

用途：查询已确认产品范围的当前库存分布，并按库位、产品或库位和产品组合聚合。

受控输入：

```json
{
  "productScope": {"type": "PRODUCT_TYPE_GROUP", "productType": "黄冰糖"},
  "warehouseScope": {"type": "SINGLE_WAREHOUSE", "warehouseId": 2},
  "statusFilter": {
    "productStatuses": ["成品"],
    "warehouseStatuses": ["正常"],
    "palletStatuses": ["INSTOCK"],
    "assayStatus": "PASS",
    "entryDateFrom": "2026-07-01",
    "entryDateTo": "2026-07-07"
  },
  "groupBy": "warehouse_product",
  "limit": 50
}
```

约束：

* `productId` 只能来自 `resolve_products` 唯一结果、用户候选选择或 Python structured state 中的 `selectedProduct`；
* `productScope` 支持 `SINGLE_PRODUCT`、`EXACT_PRODUCT_NAME_GROUP`、`PRODUCT_TYPE_GROUP` 和显式 `ALL`；产品名称组和产品大类必须来自 resolver/HITL 结构化候选，不能由模型自行构造；
* `warehouseScope` 支持 `ALL` 和 `SINGLE_WAREHOUSE`；`warehouseId` 必须与已确认 `selectedWarehouse` 一致；
* 前端 resume 仍只提交 `optionId`，不得提交 `productId`；
* `statusFilter` 仅允许白名单产品状态、库位状态、托盘状态、化验判断状态和入库日期范围；
* 当 `assayStatus` 为 `PASS`、`FAIL`、`NO_STANDARD`、`MULTIPLE_CANDIDATES` 或 `HAS_ASSAY` 时，日期范围按化验 `sample_date` 理解；当 `assayStatus` 为 `MISSING_ASSAY` 时，因为不存在化验记录，日期范围按库存 `entry_date` 理解；
* `groupBy` 仅允许 `warehouse`、`product`、`warehouse_product`；
* `limit` 默认 20，范围 1 到 100；
* 权限沿用当前用户 Agent session 委托身份、`mcp:warehouse:read` scope 和后端已认证只读查询边界；
* Java 通过 `POST /api/inventory/distribution` 完成只读聚合，Python 不连接数据库；
* 单一板件规格输出 `normalizedPallets` / `normalizedLoosePieces`；跨规格汇总不虚构统一板数，改用 `totalEquivalentPieces`、总重量和“跨规格”展示文本；
* 普通回答、SSE 和卡片只接收 safe adapter 白名单字段，不显示内部 ID、工具名或原始 JSON。

当前 internal agent gateway 白名单已有 52 个只读工具。除原有能力外，已完成库存、库位、物流、二维码/托盘、生产、质量、主数据、员工/RBAC、审计和当前库存质量筛选工具；仍未增加 login、`preview_*`、`execute_*`、任意 SQL、任意 HTTP 代理或业务写能力。

仍不支持：库区范围、任意状态字段、库龄分桶、明细下钻和报表导出。这些能力需要独立工具或后续规格评审，不扩展为任意 SQL/HTTP 能力。

当前库存质量筛选统一复用 `inventory_current_assay_fact_v1`：托盘库存以 `product_id + pallet_code.production_date` 作为批次，非托盘库存兼容回退 `product_id + inventory.entry_date`，并读取同批次最新版本化验，不使用 `inventory.assay_id`。三个工具语义严格分开：

* `query_unqualified_inventory` 只返回最新化验保存判定为明确不合格的当前库存；无化验、无标准和多候选不计入不合格；
* `query_inventory_by_quality_standard` 由后端用最新原始指标逐项匹配用户明确指定的标准代码和版本，所有受约束指标都满足才命中；它不改写历史判定，也不证明产品已绑定该标准；
* `query_inventory_by_assay_metrics` 当前只接受一个白名单指标、一个受控比较运算符和一个数值，不由 LLM 计算或拼接 SQL。

三类结果均可携带仅供 Runtime 受控追问使用的化验报告引用；普通回答和卡片不得展示该引用、数据库 ID、`FAIL` 等后端枚举或原始 JSON。

---

## 18. M1.4 后续模块工具设计索引

状态：M1.4b 五个化验查询工具、M1.4c 五个二维码 / 托盘生命周期工具，以及后续 Agent v1 库位、物流、生产、质量目录、当前库存质量筛选、主数据、员工/RBAC、审计和库存补充工具均已实现并加入当前 52 工具白名单。下列索引保留历史模块设计来源；是否可用以本文件“工具状态总表”和 `docs/agent/tool-capability-registry.yaml` 为准。

详细设计见 `docs/mcp-analysis/m14-module-tool-design.md`。该文档基于已完成的审计、HITL、LLM Wiki Lite、Answer Review 和 `get_inventory_distribution` 链路，拆分 M1.4b-f 后续只读分析工具。

设计中的模块：

* M1.4b 化验查询与质量分析：
  * `query_assay_records`（已实现，M1.4b）
  * `get_assay_report_detail`（已实现，M1.4b）
  * `query_assay_abnormalities`（已实现，M1.4b）
  * `query_products_without_recent_assay`（已实现，M1.4b）
  * `query_assay_standard_coverage`（已实现，M1.4b；第一版支持 `PRODUCT_WITHOUT_STANDARD`）
* M1.4c 二维码 / 托盘生命周期分析（已实现）：
  * `query_qr_code_lifecycle`
  * `query_printed_not_inbound_codes`
  * `query_pallet_anomalies`
  * `query_pallet_flow_records`
  * `query_qr_batch_inbound_completion`
* M1.4a 后续库存明细与库存可信度：
  * `query_inventory_records`
  * `query_inventory_ageing`
  * `query_inventory_capacity_risks`
* M1.4d 库位容量与库位健康分析：
  * `query_warehouse_capacity_distribution`
  * `query_warehouse_recent_operations`
  * `query_warehouse_mixed_product_risks`
* M1.4e 生产订单与标签闭环分析：
  * `query_production_order_progress`
  * `query_production_label_completion`
  * `query_material_pick_trace`
* M1.4f 操作审计与异常排查：
  * `search_operation_logs`
  * `query_agent_tool_audit`
  * `query_agent_answer_reviews`

M1.4c 第一版口径：打印以 `production_order_label_batch.printed_at` 为来源；二维码入库以输出码已关联库存、存在入库时间或状态为 `INSTOCK` 判定。当前系统没有独立二维码扫描日志来源，因此 `VOID_CODE_SCANNED` 只返回能力缺口说明，不虚构异常结果。

2026-07-23 托盘旅程验收补充：`query_pallet_flow_records` 同时承担“最近流转”和“完整历史”业务目标。用户明确说完整/全部/所有历史时，Python Runtime 必须把模型参数规范化为受控全量日期范围；MCP 仍接收明确范围以避免后端默认最近窗口，但安全适配结果只输出“完整历史”，不向模型最终答案或普通卡片暴露内部日期下界。专家若误选 `get_pallet_status` 或 `query_qr_code_lifecycle` 代替完整历史，Runtime 拒绝该计划并允许专家重新选择 `query_pallet_flow_records`，不由确定性 Router 直接调用工具。

托盘码校验与不存在必须保持不同错误语义：业务码 `1030` 映射为不可重试的 `UPSTREAM_BAD_REQUEST`，提示用户核对格式/校验位；业务码 `1031` 映射为不可重试的 `UPSTREAM_NOT_FOUND`，表示格式合法但无记录。两类终态都禁止同签名循环重试。`cycle` / `cycleNo` 只允许在内部事实中存在，模型观察和普通展示统一转换为“第 N 次流转”。

未标注“已实现”的工具仍必须逐个实现、测试和登记。设计文档不是白名单授权；未实现前 Agent 不得声称已支持这些能力。

2026-07-20 生产范围查询补充：当时共 48 个工具；新增 `query_boiling_batches`，用于按可选产品和北京时间业务日期范围查询煮糖批次。9 个业务专家的 Agent v1 查询规划集均已完成全链路登记。详细覆盖矩阵和专家划分见 `docs/mcp-analysis/agent-v1-module-expert-reaudit.md` 与 `docs/mcp-analysis/agent-expert-tool-blueprint.md`。

---

## 19. 模块化 Agent 编排

Python Agent Runtime 已加入第一版主 Agent / 专家 Agent handoff 骨架。主 Agent 负责意图识别、会话上下文、HITL、安全边界和最终自然语言回答，本身不持有业务 MCP 工具。受支持的只读任务按模块交给：

* `inventory_expert`：库存与产品范围；
* `warehouse_expert`：库位容量与状态；
* `logistics_expert`：托盘任务、单据和智能报数批次；
* `pallet_expert`：二维码与托盘生命周期；
* `production_expert`：生产订单、煮糖、领料和标签闭环查询；
* `assay_expert`：运行时质量域专家，对应蓝图中的 `quality_expert`；
* `master_data_expert`：产品和筛网主数据；
* `administration_expert`：员工、角色与权限摘要；
* `audit_expert`：业务日志、Agent 工具审计和回答 Review 摘要。

每个专家只接收自己的 context pack 和工具 schema。规划完成后及调用 Java Internal Agent Gateway 前都会校验专家工具白名单；Python 专家白名单不替代 Java 最终权限和只读白名单。

当前专家运行在同一 Python 进程内，默认共享现有模型客户端；已预留按专家注入不同 `ModelClient` 和参数策略的扩展点。详细设计见 `docs/agent/modular-agent-architecture.md`。

生产启用时，Java Gateway 必须校验 Python Runtime 的协议版本、52 个工具的 registry hash、唯一受控配方的 registry hash；首次绑定 warehouse-mcp 时必须再次核对完整工具清单。任一不一致均 fail-closed，不回退旧 Agent。9 个专家的精确白名单和主 Agent 空工具集必须由确定性测试锁定；专家映射 hash 尚未纳入启动握手时，应作为上线阻断项处理。配方定义见 `docs/agent/orchestration-recipe-registry.yaml`。

---

## 20. 业务时间与相对日期约定

北京时间属于 Agent 每轮推理的受信运行时上下文，不属于需要模型自行决定是否调用的业务工具。当前不新增 `get_system_time` MCP Tool，避免模型漏调时间工具、增加延迟或在得到时间前先生成错误业务参数。

调用 MCP 前必须遵守：

* Agent Runtime 使用服务端时钟和固定业务时区 `Asia/Shanghai` 解析“今天、昨天、前天、本周、上周、本月、上月、本季度、上季度、今年、去年”等表达；
* 主模型和专家模型只把 `selectedContext.BUSINESS_TIME` 作为当前日期来源，不得使用训练记忆或自行猜测当前年份；
* Runtime 必须在工具调用前再次根据用户原话归一化日期，不能只信任模型生成的 `productionDate`、`dateRange`、`startDate/endDate` 或日志时间窗口；
* “最近 N 天”继续使用 `LAST_DAYS`，由 MCP 后端在调用时按业务系统日期解析；日历周、月、季度和年度转换为确定的 `RANGE`；
* 如果用户请求日期范围而模型选择只接受单日的工具，Runtime 必须拒绝该计划并重新选择范围工具，不得静默缩成一天。

该约定适用于化验、库存日期过滤、托盘/二维码生命周期、生产单据、备料池、操作日志和审计等所有带日期参数的只读工具。未来如果开放独立 MCP 给不具备可靠系统时钟的第三方 Host，可增加 L0 `get_business_time_context` 互操作工具；它不能替代 Runtime 的强制日期归一化。
