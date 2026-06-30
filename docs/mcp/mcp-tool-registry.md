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

状态：未实现，计划 M1.1 或 M1.2
风险：L1
用途：查询生产订单领料、产出、标签和入库进度。

输入建议：

```json
{
  "orderId": 123,
  "includeMaterials": true,
  "includeOutputs": true,
  "includeLabels": true
}
```

输出建议：

* `baseInfo`
* `materials`
* `outputs`
* `labelBatches`
* `progress`
* `warnings`
* `error`

底层接口候选：

* `GET /api/production/orders/{id}`
* `GET /api/production/orders/{id}/trace`

待补：

* 如果用户只提供订单号，需要订单号解析接口。

---

### 5.4 `search_operation_logs`

状态：未实现，计划 M1.2
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

状态：未实现，计划 M2 或 M5
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

注意：

* 如果产品名称存在歧义，必须先 `resolve_products`；
* 不得一次返回过多原始记录；
* 大数据量应分页或后端聚合。

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

如果当前没有 `query_assay_records`、`analyze_assay_records`、`export_assay_report`，Agent 不得编造结果，不得假装已经导出。

应回复：

```text
当前 MCP 还没有批量化验分析和报表导出工具，无法安全完成这个任务。

我目前可以完成的部分是：
1. 解析产品；
2. 查询单个产品日期的化验状态；
3. 生成需要新增的 MCP 工具规格。

要完整支持这个需求，需要新增：
- query_assay_records
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
| `get_production_order_trace`  | 计划            | M1.1/M1.2 | L1 | 否     | 生产订单追踪        |
| `search_operation_logs`       | 计划            |      M1.2 | L1 | 否     | 操作日志查询        |
| `query_assay_records`         | 计划            |     M2/M5 | L1 | 否     | 批量化验记录查询      |
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

计划：

* `get_production_order_trace`
* `search_operation_logs`

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

M1.2 后仍然只有以下 6 个已实现业务工具：

* `resolve_products`
* `resolve_warehouses`
* `get_inventory_overview`
* `get_warehouse_status`
* `get_pallet_status`
* `get_assay_status`

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