# MCP Gap Analysis

生成时间：2026-06-13

## 总结

现有后端已经具备完整的页面型 REST 能力，但还缺少面向 Agent 的安全聚合层。第一版 MCP 若只做 L0/L1/L2，可以复用部分查询接口；一旦进入 L3 执行，必须先补预览、确认、幂等、权限和审计。

## 关键缺口

### 1. 产品名称解析

现状：

- 已有 `/api/products/product`、`/api/products/semi-products`、`/api/products/finished-products`。
- 这些接口偏页面下拉/模糊查询，返回展示名称时会拼接重量和包装方式。
- 自动入库解析当前主要信任 LLM 输出的 `productId`，后端只校验 ID 是否存在。

缺口：

- 缺少确定性 `resolveProducts` 聚合接口。
- 缺少候选置信度、匹配原因、歧义标记、是否需要用户选择。
- 缺少“展示名”和真实 `product.product_name` 的结构化区分。

建议接口：

- `GET /api/agent/resolve/products`
- 输入：`query, productType?, productStatus?, limit?`
- 输出：候选产品、置信度、歧义、需确认字段。

支撑工具：

- `resolve_products`
- `preview_auto_inbound_report`
- `preview_inbound_plan`
- `preview_outbound_plan`

### 2. 库位名称解析

现状：

- 已有 `/api/warehouse/query` 和库存容量查询。
- 入库/调拨执行接口常用 `warehouseName` 或 `warehouseId`，但没有统一解析。

缺口：

- 缺少 `resolveWarehouses` 聚合接口。
- 缺少库位可用容量、状态、侧别候选位置的结构化返回。
- 缺少 Agent 可读的冲突原因，例如维护状态、满仓、指定侧满。

建议接口：

- `GET /api/agent/resolve/warehouses`
- 输入：`query, onlyAvailable?, side?, limit?`
- 输出：候选库位、容量、状态、是否可入库、风险提示。

支撑工具：

- `resolve_warehouses`
- `get_warehouse_status`
- `preview_inbound_plan`
- `preview_transfer_plan`

### 3. 入库预览

现状：

- 成品、半成品、托盘和平面图入库接口都会直接写入数据。
- 自动入库 `/parse` 是文本解析预览，但不是通用入库 dry-run。
- 库位分配逻辑在 `SemiProductRecordService.handlerInStock`、`PalletCodeService` 等写方法内部。

缺口：

- 缺少不写数据的入库计划接口。
- 缺少可返回目标格子、容量占用、化验缺失、筛网缺失、二维码状态、半成品来源校验的统一结构。
- 缺少后端签名的执行 token，无法保证“执行的就是用户刚确认的预览”。

建议接口：

- `POST /api/agent/inbound/preview`
- 输入：结构化入库目标，不接受任意 SQL/HTTP。
- 输出：`planId, targetLocations, capacityCheck, assayCheck, blockingIssues, warnings, executionToken?`
- 预览不能修改 `inventory`、`in_stock`、`semi_product_record`、`pallet_task`、`pallet_code`。

支撑工具：

- `preview_inbound_plan`
- `preview_auto_inbound_report`

### 4. 出库预览

现状：

- `OutStockService.outStock` 在选择库存后立即扣减/删除 `inventory` 并写 `out_stock`。
- 托盘出库 create/confirm 已拆两步，但 create 本身也是写操作。

缺口：

- 缺少出库 dry-run，无法在 Agent 回复中安全展示“将扣哪些批次/托盘”。
- 缺少库存不足、化验缺失、散件换算和出库排序的结构化预览。

建议接口：

- `POST /api/agent/outbound/preview`
- 从现有出库逻辑抽出纯计算选择器，返回 deduction plan，不执行扣减。

支撑工具：

- `preview_outbound_plan`

### 5. 移库/调拨预览

现状：

- 旧调拨直接出库再入库。
- 托盘调拨 create/confirm 两步，但 create 会写 `pallet_task`。
- 目标库位分配逻辑在确认阶段内部。

缺口：

- 缺少不创建任务的调拨预览。
- 缺少同位置冲突、目标库位满、目标侧满、二层堆放限制的统一返回。

建议接口：

- `POST /api/agent/transfer/preview`
- 输出：源位置、目标候选位置、容量检查、阻塞原因。

支撑工具：

- `preview_transfer_plan`

### 6. 入库执行 Token

现状：

- 执行接口直接接收 DTO，无预览 token。
- 自动入库确认使用 Redis `batchId`，但 `batchId` 不是用户确认后的不可篡改执行凭证。

缺口：

- 缺少后端签发的 `executionToken`。
- 缺少 token 与预览内容摘要、用户、权限、过期时间、风险等级绑定。
- 缺少执行时重新校验预览内容是否仍然有效。

建议：

- `executionToken = sign(planId + normalizedPlanHash + userId + permissions + expiresAt + riskLevel)`
- 执行接口只接受 token + 用户确认 + 幂等键，不接受任意改写后的计划。
- 执行前重新锁定关键资源并校验库存/库位/托盘状态未变化。

支撑未来工具：

- `execute_inbound_plan`
- `execute_outbound_plan`
- `execute_transfer_plan`

### 7. 幂等请求

现状：

- 多数写接口没有 `idempotencyKey`。
- 自动入库 parse 每次生成新的 `batchId`。
- 托盘任务 create 类接口可通过状态限制减少重复，但无法区分用户重试和重复业务请求。

缺口：

- 缺少统一幂等表或请求日志。
- 缺少 `clientRequestId/idempotencyKey` 与响应缓存。
- 缺少重复提交时返回原执行结果的约定。

建议数据表：

- `agent_idempotency_request(id, idempotency_key, user_id, operation_type, request_hash, status, response_json, error_code, created_at, updated_at, expires_at)`

建议规则：

- 同一用户、同一工具、同一幂等键、同一请求哈希：返回原结果。
- 同一幂等键但请求哈希不同：返回冲突错误。
- 幂等记录与业务事务同提交。

### 8. 结构化错误码

现状：

- `ErrorCode` 只覆盖部分库存/产品/化验错误。
- 很多 Service 直接 `new BusinessException("中文消息")`，Controller 有时包装成 `500`。
- 部分权限/认证错误由 Spring Security 返回纯文本。

缺口：

- Agent 难以根据错误类型判断是否可让用户补充信息、重试、换库位或停止。
- 缺少字段级错误路径，例如 `items[0].productId` 未匹配。
- 缺少并发冲突、幂等冲突、预览过期、执行 token 无效等 Agent 专用错误码。

建议错误结构：

```json
{
  "code": "WAREHOUSE_LOCATION_OCCUPIED",
  "message": "目标位置已有库存，不能入库",
  "severity": "BLOCKING",
  "field": "items[0].rowNumber",
  "retryable": false,
  "suggestedActions": ["choose_other_location", "refresh_preview"]
}
```

建议新增错误码族：

- `RESOLUTION_AMBIGUOUS`
- `PRODUCT_NOT_RESOLVED`
- `WAREHOUSE_NOT_RESOLVED`
- `PREVIEW_EXPIRED`
- `EXECUTION_TOKEN_INVALID`
- `IDEMPOTENCY_CONFLICT`
- `INVENTORY_CHANGED`
- `WAREHOUSE_LOCATION_OCCUPIED`
- `PALLET_STATE_CHANGED`

### 9. 操作审计

现状：

- `@LogOperation` 覆盖产品、库位、筛网、化验、标准、部分员工等。
- 托盘任务、自动入库确认、生产订单等关键写操作多数没有 `@LogOperation`。
- `OperationLogAspect` 会在日志失败时继续执行业务。
- 审计主要记录 Controller 参数和新旧对象，不记录 Agent 工具名、自然语言输入、预览摘要、用户确认文本、幂等键、执行 token。

缺口：

- 缺少 Agent 操作审计。
- 缺少执行前预览与执行后结果关联。
- 缺少关键库存写操作的统一审计覆盖。

建议：

- 新增 `agent_operation_audit` 表，字段至少包括：`tool_name, risk_level, user_id, input_json, preview_id, execution_token_id, idempotency_key, confirmation_text, result_json, status, error_code, created_at`。
- L2 预览写轻量审计或请求日志；L3/L4 执行必须写审计。
- 托盘、生产订单、自动入库执行补业务审计。

### 10. 权限模型

现状：

- 全局 JWT 已启用。
- 方法级权限覆盖不一致：库存查询部分有 `record:query`，传统入库/出库/调拨、托盘任务多数无 `@PreAuthorize`。

缺口：

- 缺少 Agent 专用权限点，例如 `agent:inventory:preview`、`agent:inventory:execute`。
- 不能仅依赖前端菜单控制。

建议权限点：

- `agent:product:resolve`
- `agent:warehouse:resolve`
- `agent:inventory:view`
- `agent:inbound:preview`
- `agent:outbound:preview`
- `agent:transfer:preview`
- `agent:operation:audit:view`
- 未来执行类：`agent:inbound:execute`、`agent:outbound:execute`、`agent:transfer:execute`

### 11. OpenAPI 产物

现状：

- `docs/openapi.json` 缺失。
- 有 `SwaggerConfig.java` 和文档端点放行，但未提交静态 OpenAPI 文件。

缺口：

- 无法自动比对接口契约。
- MCP 规格无法从稳定 OpenAPI 产物生成或校验。

建议：

- 在 CI 或本地脚本中生成 `docs/openapi.json`。
- 将 `api-inventory.md` 中记录的不一致项反向修复 Controller 注解或 DTO schema。

### 12. 当前库存批次化验统一读模型

现状：

- 业务已确认 `assay.sample_date` 是生产日期，`product_id + sample_date` 是批次键。
- 化验更新创建新版本，但不会同步更新所有库存、托盘和任务中的旧 `assay_id`。
- 部分页面按产品和日期查询最新版本，库存分布、标准筛选和部分 Agent 查询仍直接关联旧 `assay_id`。
- 当前“缺化验在库产品”查询只判断产品在日期范围内是否存在任意化验，不能证明每个在库批次是否有化验。

缺口：

- 缺少统一的 `inventory_current_assay_fact_v1` 只读事实模型。
- 缺少稳定的库存批次日期解析及 `batchDateSource`、日期冲突警告。
- 缺少基于最新批次化验的标准命中查询和原始指标条件查询。
- `qualified_standards` 以名称为主，版本级命中证据不足。
- 当前复合流程以“库存产品 + 产品最近化验”拼接，不能冒充库存批次质量结论。

建议接口：

- `POST /api/agent/inventory-quality/by-standard`
- `POST /api/agent/inventory-quality/by-metrics`
- 两者共享固定 Mapper/视图，不接受任意 SQL、任意字段或任意表达式。
- 当前质量查询一律按产品和生产日期取最新化验；显式 `assay_id` 只用于历史报告下钻。

支撑工具：

- `query_inventory_by_quality_standard`
- `query_inventory_by_assay_metrics`
- 现有不合格库存、缺化验库存和托盘当前质量查询也应迁移到同一事实模型。

详细口径、实施目标和验收用例见 `docs/agent/inventory-assay-batch-semantics.md`。

## 与候选工具的映射

| 缺口 | 影响工具 | 阻塞级别 |
|---|---|---|
| 产品解析 | `resolve_products`, 所有 preview | 高 |
| 库位解析 | `resolve_warehouses`, 入库/调拨 preview | 高 |
| 入库预览 | `preview_inbound_plan`, `preview_auto_inbound_report` | 高 |
| 出库预览 | `preview_outbound_plan` | 高 |
| 调拨预览 | `preview_transfer_plan` | 高 |
| 执行 token | 所有未来 L3 执行 | 必须 |
| 幂等 | 所有未来 L3/L4 执行 | 必须 |
| 结构化错误码 | 所有工具 | 中高 |
| Agent 审计 | L2/L3/L4 | 中高；执行必须 |
| 权限点 | 所有 Agent 工具 | 中高；执行必须 |
| OpenAPI 文件 | 文档和契约校验 | 中 |

## 第一版实施建议

第一步：

- 实现 `resolve_products`、`resolve_warehouses`、`get_inventory_overview`、`get_warehouse_status`、`get_pallet_status`、`get_assay_status`、`get_production_order_trace`、`search_operation_logs`。
- 这些可以先通过现有 REST 聚合或后端只读接口完成。

第二步：

- 新增三个 dry-run：`/api/agent/inbound/preview`、`/api/agent/outbound/preview`、`/api/agent/transfer/preview`。
- 从现有写 Service 抽出纯计算规则，不写业务表。

第三步：

- 新增结构化错误模型、Agent 查询/预览审计、OpenAPI 导出。

第四步：

- 设计但不启用 L3 执行：execution token、幂等表、强确认、强权限、强审计。

## 阶段四一致性检查

- 与 `mcp-tool-candidates.md` 一致：第一版只实现 L0/L1/L2；L3/L4 仅列未来要求。
- 与 `business-rules.md` 一致：不确定业务边界仍列为待确认。
- 与 `api-inventory.md` 一致：OpenAPI 缺失、方法权限和审计覆盖不一致是已确认缺口。

## 2026-06-25 Agent 会话授权更新

已补充 M1 Agent 会话授权基础能力：

- 新增 `/api/agent/sessions`、`/api/agent/sessions/current`、`DELETE /api/agent/sessions/{agentSessionId}`，响应不返回 delegationToken。
- 后端内部可签发短期 `AGENT_DELEGATION` token，用于注入 MCP 上下文。
- 每次处理 `AGENT_DELEGATION` token 时检查 `agent_session` 状态、过期时间、用户有效性和 `mcp:warehouse:read` scope。
- 新增 Agent Tool 审计和后端 API 审计表结构。
- `X-Agent-Tool-Name` 仅作为审计辅助，不作为权限依据。
- `warehouse-mcp` 支持开发 STATIC_TOKEN、过渡 STDIO 委托 token 和 Agent session header 注入；未新增业务工具。

仍待后续增强：

- 生产推荐切换到 HTTP/Streamable HTTP MCP，实现请求级身份注入。
- scope 需要从 `mcp:warehouse:read` 拆分为 product、warehouse、inventory、pallet、assay、log 等细粒度授权。
- 前端/小程序尚需接入 Agent 会话创建和撤销入口。
