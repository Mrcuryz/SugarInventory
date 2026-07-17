# MCP Tool Candidates

生成时间：2026-06-13

## 设计边界

- 第一版只允许提出 L0、L1、L2 工具实现计划。
- L3/L4 写操作只列为未来候选，不在第一版实现。
- 工具表达用户业务目标，不按 REST API 一对一转换。
- Agent 不得猜测产品 ID、库位 ID、库存 ID、托盘码状态；必须先解析、预览、确认。
- 写操作必须具备权限、确认、幂等、事务和审计；当前后端缺口见 `mcp-gap-analysis.md`。

## 候选工具总览

| 工具名 | 分类 | 风险 | 第一版 | 用户目标 |
|---|---|---:|---|---|
| `warehouse_help` | 帮助 | L0 | 是 | 解释系统能做什么、字段含义、风险边界 |
| `resolve_products` | 查询/解析 | L1 | 是 | 把自然语言产品名称解析为候选产品 |
| `resolve_warehouses` | 查询/解析 | L1 | 是 | 把库位名称/别名解析为候选库位 |
| `get_inventory_overview` | 查询 | L1 | 是 | 查询某产品/状态的库存汇总 |
| `get_warehouse_status` | 查询 | L1 | 是 | 查询库位容量、库存明细和最近操作 |
| `get_pallet_status` | 查询 | L1 | 是 | 扫码后了解托盘当前产品、位置、任务和流转 |
| `get_assay_status` | 查询 | L1 | 是 | 查询产品日期化验和判定结果 |
| `query_inventory_by_quality_standard` | 查询 | L1 | 是 | 查询当前库存中符合指定化验标准的批次 |
| `query_inventory_by_assay_metrics` | 查询 | L1 | 是 | 按最新化验原始指标数值条件筛选当前库存批次 |
| `preview_auto_inbound_report` | 预览 | L2 | 是 | 把报数文本解析成待确认入库任务和风险 |
| `preview_inbound_plan` | 预览 | L2 | 是 | 预览入库会放到哪里、缺什么、是否冲突 |
| `preview_outbound_plan` | 预览 | L2 | 是 | 预览出库会扣哪些库存、是否足够 |
| `preview_transfer_plan` | 预览 | L2 | 是 | 预览移库/调拨目标位置和风险 |
| `get_production_order_trace` | 查询 | L1 | 是 | 查询生产订单领料、产出、标签和入库进度 |
| `search_operation_logs` | 查询 | L1 | 是 | 审计最近业务操作 |
| `execute_inbound_plan` | 执行 | L3 | 否 | 确认执行入库 |
| `execute_outbound_plan` | 执行 | L3 | 否 | 确认执行出库 |
| `execute_transfer_plan` | 执行 | L3 | 否 | 确认执行调拨 |
| `manage_reference_data` | 管理 | L4 | 否 | 产品、库位、筛网、标准、权限等维护 |

## 工具详设

### `warehouse_help`

- 描述：返回智能仓储 MCP 能力、风险等级、字段字典、写操作限制。
- 输入：`topic?: "inventory"|"pallet"|"assay"|"production"|"risk"|"all"`。
- 输出：帮助文本、相关工具列表、风险提示。
- 风险：L0。
- 权限：无需业务权限，但仍建议绑定登录上下文。
- 确认：不需要。
- 幂等：天然幂等。
- 审计：不需要业务审计。
- 底层映射：静态文档；可引用 `docs/mcp-analysis/*`。

### `resolve_products`

- 描述：按自然语言名称、产品类型、成品/半成品状态解析产品候选，返回置信度和歧义。
- 输入：`query`, `productType?`, `productStatus?`, `limit?`。
- 输出：候选产品 `{productId, productName, productType, productStatus, packagingMethod, weightPerPiece, piecesPerPallet, canStack, screenMeshId, confidence, matchReason}`；`ambiguity`；`needsUserSelection`。
- 风险：L1。
- 权限：JWT；建议不要求 `product:*` 写权限。
- 确认：不需要；若后续写操作必须由用户选择候选。
- 幂等：同一查询只读幂等。
- 审计：可记录 MCP 查询日志，不写业务审计。
- 底层映射：现有 `/api/products/product`、`/api/products/semi-products`、`/api/products/finished-products`；建议新增确定性解析接口，避免依赖 LLM 输出 ID。

### `resolve_warehouses`

- 描述：解析库位名称、模糊名称或 ID，返回候选库位容量和状态。
- 输入：`query`, `onlyAvailable?`, `side?`, `limit?`。
- 输出：候选库位 `{warehouseId, warehouseName, status, maxRows, curCapacity, maxCapacity, hasSpace, confidence}`。
- 风险：L1。
- 权限：JWT；如返回容量明细可要求 `record:query`。
- 确认：不需要。
- 幂等：只读幂等。
- 审计：不需要业务审计。
- 底层映射：`/api/warehouse/query`、`/api/inventory/query`、`/api/inventory/warehouses`；建议新增解析聚合接口。

### `get_inventory_overview`

- 描述：按产品、产品状态、日期、筛网等查询库存汇总，回答“还有多少库存、在哪些库位”。
- 输入：`productId?`, `productName?`, `productStatus?`, `productionDate?`, `screenMeshId?`, `page`, `size`。
- 输出：汇总 `{totalBoards,totalPieces,totalWeight,warehouseCount}`，明细 records `{warehouseId, warehouseName, productId, productName, productStatus, entryDate, totalQuantity, totalPieces, stockInfo}`，解析警告。
- 风险：L1。
- 权限：`record:query` 优先；若沿用现有部分接口仅 JWT，建议后端统一为 `record:query`。
- 确认：不需要。
- 幂等：只读。
- 审计：可选查询日志。
- 底层映射：`/api/inventory/stock/page`、`/api/inventory/summary`、`/api/products/product`。

### `get_warehouse_status`

- 描述：查看库位容量、当前库存明细和最近流转。
- 输入：`warehouseId?`, `warehouseName?`, `includeInventoryDetails?`, `includeRecentOperations?`, `limit?`。
- 输出：库位容量、库存明细、最近操作、风险提示。
- 风险：L1。
- 权限：`record:query`。
- 确认：不需要。
- 幂等：只读。
- 审计：可选查询日志。
- 底层映射：`/api/inventory/query`、`/api/inventory/qualified-inventory/{warehouseId}/page`、`/api/inventory/warehouses/{warehouseId}/recent-operations`。

### `get_pallet_status`

- 描述：扫码或输入托盘码后，返回托盘基础状态、当前库存位置、化验、待办任务和流转时间线。
- 输入：`code`, `includeFlows?`, `cycleNo?`, `includeAssay?`, `includeInventory?`。
- 输出：`palletInfo`、`inventory`、`assay`、`flowCycles`、`flows`、`warnings`。
- 风险：L1。
- 权限：JWT；库存/流转明细建议要求 `record:query`。
- 确认：不需要。
- 幂等：只读。
- 审计：可选查询日志。
- 底层映射：`/api/pallet-codes/parse`、`/{code}/inventory`、`/{code}/assay`、`/{code}/flows/cycles`、`/{code}/flows`、`/tasks/list`。

### `get_assay_status`

- 描述：查询指定产品和生产日期的化验结果、命中标准、失败指标和标准快照。
- 输入：`productId?`, `productName?`, `productionDate?`, `assayId?`。
- 输出：`assay`、`judgeResult`、`qualifiedStandards`、`failedMetrics`、`appliedStandard`、`needsAssay`。
- 风险：L1。
- 权限：详情可 JWT；列表查询沿用 `quality:test` 或新增只读质量权限。
- 确认：不需要。
- 幂等：只读。
- 审计：不需要业务审计。
- 底层映射：`/api/assay/by-product-date`、`/api/assay/{id}`、`/api/assay/query`。

### `query_inventory_by_quality_standard`

- 描述：按当前库存批次查询最新化验，并筛选在化验生成时已保存为命中指定标准的库存。
- 输入：`productScope?`, `warehouseScope?`, `productionDateRange?`, `standardRef`, `groupBy?`, `page?`, `size?`。
- 输出：`dataScope=CURRENT_INVENTORY_BATCH_LATEST_ASSAY`、库存数量、批次生产日期、日期来源、最新化验版本、命中标准快照、数据质量警告。
- 风险：L1。
- 权限：建议 `inventory:view` + `quality:view`。
- 限制：标准名称先解析为受控引用；多版本歧义时要求用户选择；不按当前标准重算历史化验。
- 底层缺口：需新增统一当前库存批次化验读模型和聚合接口，不能继续直接连接 `inventory.assay_id`。

### `query_inventory_by_assay_metrics`

- 描述：按当前库存批次最新化验的原始数值筛选库存。
- 输入：`productScope?`, `warehouseScope?`, `productionDateRange?`, `conditions[1..3]`, `groupBy?`, `page?`, `size?`；条件字段和运算符使用固定枚举。
- 输出：`dataScope=CURRENT_INVENTORY_BATCH_LATEST_ASSAY`、库存数量、批次生产日期、最新化验版本、命中指标值、缺失指标数和数据质量警告。
- 风险：L1。
- 权限：建议 `inventory:view` + `quality:view`。
- 限制：第一版仅支持最多 3 条 `AND` 条件；不接受 SQL、列名、公式或模型自定义单位换算；不根据指标筛选结果自动宣称产品合格。
- 底层缺口：与标准命中查询共享统一当前库存批次化验读模型。

### `preview_auto_inbound_report`

- 描述：解析自然语言报数文本，返回半成品/成品任务、缺失项、风险和需用户选择的产品/库位候选，不修改库存。
- 输入：`rawText`, `entryDate?`, `parseType?`。
- 输出：`batchId?`, `tasks`, `globalRemarks`, `missingFields`, `warnings`, `candidateResolutions`, `canExecute=false|true`。
- 风险：L2。
- 权限：JWT；建议要求入库预览权限，例如 `inventory:inbound:preview`。
- 确认：预览不需要；执行前必须确认。
- 幂等：预览可重复；建议支持 `clientRequestId` 以便同一文本返回同一预览批次。
- 审计：记录 MCP 预览日志；不写业务 `operation_log`。
- 底层映射：现有 `/api/auto-inbound/parse`，但需补确定性产品/库位解析和不分配二维码的 dry-run 模式。

### `preview_inbound_plan`

- 描述：预览一个或多个产品/托盘入库会占用哪些库位格子、是否缺化验/筛网/二维码、是否容量不足。
- 输入：`items[{productId|productName, productStatus, productionDate, quantity, unit, warehouseId|warehouseName, side?, rowNumber?, layer?, palletCode?}]`, `mode: "traditional"|"pallet"|"warehouse_map"`。
- 输出：`planId`, `resolvedItems`, `targetLocations`, `requiredAssays`, `capacityCheck`, `riskLevel`, `blockingIssues`, `warnings`。
- 风险：L2。
- 权限：建议新增 `inventory:inbound:preview`。
- 确认：不需要；输出不可直接当执行凭证，除非未来生成执行 token。
- 幂等：同输入只读幂等；未来执行需 `idempotencyKey`。
- 审计：MCP 预览日志。
- 底层映射：现无完整聚合接口；需新增后端 preview。可复用 `ProductService`、`WarehouseService`、`InventoryMapper.getUsedRowListForUpdate` 的非锁定版本、`AssayService`。

### `preview_outbound_plan`

- 描述：预览出库会扣减哪些库存批次/托盘，是否库存不足，是否缺化验。
- 输入：`productId|productName`, `warehouseId|warehouseName?`, `quantity`, `unit`, `outType?`, `side?`, `productionDate?`, `preferPalletCodes?`。
- 输出：`planId`, `selectedInventory`, `deductions`, `shortage`, `assayCheck`, `riskLevel`, `blockingIssues`。
- 风险：L2。
- 权限：建议新增 `inventory:outbound:preview`。
- 确认：不需要；执行需用户确认。
- 幂等：只读预览幂等。
- 审计：MCP 预览日志。
- 底层映射：现无 dry-run；需从 `OutStockService.outStock` 提取选择和扣减计划计算逻辑。

### `preview_transfer_plan`

- 描述：预览托盘/库存从源库位移动到目标库位的目标位置和冲突风险。
- 输入：`items[{code? inventoryId?}], sourceWarehouseId?`, `targetWarehouseId|targetWarehouseName`, `targetSide?`。
- 输出：`planId`, `resolvedPallets`, `sourceLocations`, `targetCandidates`, `sameLocationConflicts`, `capacityCheck`, `blockingIssues`。
- 风险：L2。
- 权限：建议新增 `inventory:transfer:preview`。
- 确认：不需要。
- 幂等：只读预览幂等。
- 审计：MCP 预览日志。
- 底层映射：现无 dry-run；需复用 `PalletCodeService` 调拨校验和库位分配逻辑，抽离无写版本。

### `get_production_order_trace`

- 描述：按订单号/ID 查询生产订单计划、领料、产出、标签批次、二维码入库进度。
- 输入：`orderId?`, `orderNo?`, `includeMaterials?`, `includeOutputs?`, `includeLabels?`。
- 输出：`baseInfo`, `materials`, `outputs`, `outputCodes`, `labelBatches`, `progress`。
- 风险：L1。
- 权限：`production:order:view`。
- 确认：不需要。
- 幂等：只读。
- 审计：可选查询日志。
- 底层映射：`GET /api/production/orders/{id}`、`GET /api/production/orders/{id}/trace`，若按订单号需新增解析接口或列表查询。

### `search_operation_logs`

- 描述：按模块、操作人、时间范围查询审计日志。
- 输入：`tableName?`, `operationType?`, `operator?`, `startTime?`, `endTime?`, `page`, `size`。
- 输出：分页日志 `{tableName, operationType, operator, operationTime, changedFields, oldData}`。
- 风险：L1。
- 权限：`log:view`。
- 确认：不需要。
- 幂等：只读。
- 审计：查询日志可选。
- 底层映射：`POST /api/logs/query`。

## 未来执行类候选

### `execute_inbound_plan`

- 分类：执行，L3。
- 前置：必须来自 `preview_inbound_plan` 的 `planId` 和后端签发 `executionToken`；要求 `idempotencyKey`。
- 权限：入库执行权限；当前后端无统一权限点。
- 审计：必须写 `operation_log` 或专门 Agent 审计表。
- 底层：未来聚合执行接口，不直接调用现有 REST 多接口串联。

### `execute_outbound_plan`

- 分类：执行，L3。
- 前置：预览计划、确认文本、幂等键。
- 权限：出库执行权限。
- 审计：必须记录扣减明细、操作者、Agent 请求、用户确认。
- 底层：未来聚合执行接口。

### `execute_transfer_plan`

- 分类：执行，L3。
- 前置：预览计划、确认目标库位、幂等键。
- 权限：调拨执行权限。
- 审计：必须记录源/目标位置和托盘码。
- 底层：未来聚合执行接口。

### `manage_reference_data`

- 分类：管理，L4。
- 覆盖产品、库位、筛网、化验标准、角色权限、二维码作废/恢复等。
- 第一版不实现；后续也应拆分为单独、强权限、强审计工具，禁止任意更新。

## 阶段三一致性检查

- 本文件没有把 REST 接口一对一转换为 Tool。
- 查询、预览、执行和管理已分开。
- 第一版实现计划仅包含 L0/L1/L2。
- 所有写操作均标为未来 L3/L4，并要求确认、幂等、审计和后端聚合接口。
