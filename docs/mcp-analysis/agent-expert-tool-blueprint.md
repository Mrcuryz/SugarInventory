# Agent 专家工具蓝图（v1 查询 + v2 报表分析与写入占位）

设计日期：2026-07-13
设计状态：`DESIGN_ONLY`
依据：前端菜单与 API、后端 Controller/DTO/Service、当前 MCP 注册表、Agent 产品目标。`docs/openapi.json` 仍缺失，接口与本文不一致时以代码实际行为为准。

## 1. 本文解决什么问题

本文把 Agent 工具按专家、版本、风险和实施状态提前规划清楚，供后续逐批实现和验收。本文不是运行时白名单，也不授权任何写操作。

版本边界：

- v1：模块专家的 L0/L1 查询工具；当前 47 个工具均已实现，原有 17 个工具的业务行为保持不变。
- v2-A：登记口径的数据集、趋势、对比、报表和 L2 dry-run/preview。
- v2-B：严格 `preview -> HITL -> execute` 的 L3/L4 写工具。
- v2 不提供任意 SQL、任意 HTTP、任意字段更新、模型自由 DAG 或多专家自由对话。
- 所有新增项在完成 Java 聚合接口、Gateway、warehouse-mcp、Python 专家白名单、权限、审计、测试和文档前均为 `enabled: false`。

实施状态统一使用：

| 状态 | 含义 |
| --- | --- |
| `CURRENT` | 已存在于当前 47 个只读 MCP 工具中，行为不得由本设计改变 |
| `V1_PLANNED` | 历史规划状态；当前 Registry 已无此状态的工具 |
| `V2_ANALYTICS_PLACEHOLDER` | v2 报表/分析或 preview 占位，不进入当前白名单 |
| `V2_WRITE_PLACEHOLDER` | v2 execute 占位，不进入当前白名单 |
| `PLANNING_PLACEHOLDER` | 数据、规则和模型成熟后的预测/优化/仿真占位，不生成执行凭证 |
| `BLOCKED` | 数据、权限、状态机或业务口径不完整，条件满足前禁止实现 |
| `FORBIDDEN` | 不应成为 MCP Tool |

## 2. 专家拓扑与工具所有权

命名说明：本文以业务语义名 `quality_expert` 表示质量专家；Agent v1 现有运行时 ID 仍为 `assay_expert`。本轮验收将两者视为同一专家域。若后续统一重命名，必须同步 Python profile、能力/配方 Registry、审计口径和启动 hash，不能只改显示名称。

| Agent | 业务边界 | v1 | v2 |
| --- | --- | --- | --- |
| `main_agent` | 意图、路由、HITL、受控配方、最终回答 | 无业务工具 | 仍无业务工具 |
| `inventory_expert` | 当前库存事实、库存明细、备料池 | 查询 | 库存趋势/库龄；库存纠正仅保留 L4 占位 |
| `warehouse_expert` | 库位、容量、占用、位置事实 | 查询 | 容量/空间分析；库位配置写入 |
| `logistics_expert` | 入库、出库、调拨任务、单据、智能报数 | 查询 | 流转报表、dry-run、任务和实物流转执行 |
| `pallet_expert` | 二维码身份、码池、生命周期、异常 | 查询 | 码池/异常分析；码生成、绑定、状态管理 |
| `production_expert` | 订单、煮糖、领料、产出、标签 | 查询 | 生产分析；生产闭环写入 |
| `quality_expert` | 化验、化验组、标准、产品标准关系 | 查询 | 质量趋势；化验和标准维护 |
| `master_data_expert` | 产品、筛网等基础资料 | 查询 | 完整性分析；产品/筛网配置维护 |
| `administration_expert` | 员工、角色、权限 | 管理员只读查询 | 权限分析；员工/RBAC 写入 |
| `audit_expert` | 操作日志、Agent 审计与回答审查 | 审计查询 | 风险/质量/成本分析；Review 状态写入 |
| `analytics_expert` | 仅 v2 登记报表和受控跨域分析 | 不启用 | 只读登记数据集；无业务写工具 |
| `planning_expert` | 数据和规则稳定后的预测、优化与仿真 | 不启用 | 只生成 advisory scenarios；无业务写工具 |

所有权规则：

1. 每个业务工具只有一个 owner；同名工具不能被多个专家各自解释。
2. `resolve_products`、`resolve_warehouses` 是受控共享解析器，但只出现在确有依赖的专家白名单中。
3. 主 Agent 不调用 resolver，也不调用任何业务 MCP；它只发起 handoff 或登记配方。
4. 单据/任务属于 `logistics_expert`，二维码身份属于 `pallet_expert`，当前库存事实属于 `inventory_expert`，不得用相邻模块工具冒充。
5. v2 跨域分析不得把所有模块工具暴露给一个模型，只能调用登记的数据集/报表定义。

## 3. 通用工具契约

### 3.1 查询输入与输出

查询工具按需使用以下公共字段，不能要求模型填写数据库内部 ID：

- 输入：`scope`、`dateRange`、`filters`、`groupBy`、`sort`、`page`、`size`、`includeDetails`。
- 对象引用：必须来自 resolver、上一工具返回的短期 opaque ref 或用户明确提供的业务编号。
- 默认分页：`page=1`、`size=20`；普通查询最大 100，导出必须另走登记报表。
- 输出：`status`、`dataScope`、`asOf`、`filtersApplied`、`summary`、`records`、`total`、`limitations`、`evidenceRefs`。
- 错误：至少区分 `NO_DATA`、`TOOL_TIMEOUT`、`TOOL_ERROR`、`PERMISSION_DENIED`、`ENTITY_AMBIGUOUS`、`LIMIT_EXCEEDED`、`UNSUPPORTED_SCOPE`。

内部主键、原始堆栈、SQL、模型上下文和白名单不得进入浏览器可见结果。

### 3.2 分析/报表契约

所有权威数值由 Java/SQL 确定性聚合生成，模型只负责选择已登记定义和解释结果：

- 输入：`reportDefinitionId`、`reportVersion`、`dateRange`、已登记维度和过滤条件、`comparisonPeriod?`。
- 输出：`datasetId`、`datasetVersion`、`dataAsOf`、`metricDefinitions`、`dimensions`、`series`、`totals`、`dataQuality`、`limitations`、`rowCount`。
- 时间统一按 `Asia/Shanghai`；跨期比较必须使用相同报表版本和口径。
- 导出只支持登记模板、字段脱敏、最大行数和短期下载引用；不允许用户自填 SQL、表名、任意列或文件路径。
- 报表结果必须显示数据范围、截止时间、缺失数据和是否为推断值。

### 3.3 写入契约

每一组 v2 写能力必须成对存在：`preview_*` 为 L2，`execute_*` 为 L3/L4。`execute_*` 只接收：

- `previewId`
- `previewVersion`
- `confirmationRef`
- `idempotencyKey`

execute 不再接收可修改的业务字段。预览内容、用户、权限快照、风险等级、实体版本、过期时间和规范化哈希由服务端保存并绑定。浏览器只持有 `confirmationRef`，内部 execution token 不暴露。

预览统一返回：

- `resolvedEntities`
- `beforeState` / `afterState`
- `impactSummary`
- `blockingIssues`
- `warnings`
- `requiredPermission`
- `targetRiskLevel`
- `expiresAt`
- `confirmationText`

执行统一返回：`operationId`、`status`、`safeChangedSummary`、`executedAt`、`auditRef`。还需区分 `PREVIEW_EXPIRED`、`PREVIEW_VERSION_MISMATCH`、`EXECUTION_TOKEN_INVALID`、`IDEMPOTENCY_CONFLICT`、`STALE_STATE`、`CONCURRENT_MODIFICATION`、`VALIDATION_FAILED`、`BUSINESS_RULE_PENDING`。

## 4. `main_agent`

`main_agent.allowedTools` 永远为空。以下能力是 Runtime 内部职责，不注册为业务 MCP Tool：

- 意图路由、专家 handoff、会话状态、HITL 创建/恢复；
- 登记配方选择和确定性 safe formatter；
- 拒绝越权、未登记跨域请求、任意 SQL/HTTP 和直接写入请求；
- 将专家的结构化安全结果转换为自然语言。

## 5. `inventory_expert`

### 5.1 v1 查询工具

| 工具 | 状态 | 风险 | 具体目标 | 关键输入/范围 | 关键输出 | 后端依据/权限 |
| --- | --- | --- | --- | --- | --- | --- |
| `resolve_products` | `CURRENT` | L1 | 解析产品名称/规格候选 | query、type、status、limit | 候选与歧义 | 产品查询；JWT |
| `resolve_warehouses` | `CURRENT` | L1 | 解析库位名称/别名 | query、onlyAvailable、limit | 候选与歧义 | 库位/容量查询；建议 `record:query` |
| `get_inventory_overview` | `CURRENT` | L1 | 产品/状态库存汇总 | productScope、status、date、mesh | 数量、重量、库位数 | `/api/inventory/stock/page`、`summary` |
| `get_inventory_distribution` | `CURRENT` | L1 | 查询产品在哪或库位有什么 | productScope、warehouseScope、groupBy | 分组分布 | `/api/inventory/distribution` |
| `query_inventory_ledger` | `CURRENT` | L1 | 查询当前库存台账明细，不冒充历史流水 | product/warehouse/date/mesh/status、page | 当前库存行、位置、板/件、入库/生产日期 | 已新增聚合；`record:query` |
| `query_prepare_pool_balance` | `CURRENT` | L1 | 查询历史备料池当前余额 | productScope、dateRange、positiveOnly、page | 可用/已用余额与留档时间 | 已新增聚合；`record:query` |

`query_inventory_ledger.dataScope` 固定为 `CURRENT_INVENTORY_LEDGER`，不能解释为完整入出库历史；完整历史归 `query_stock_documents`。

### 5.2 v2 报表/分析占位

| 工具 | 风险 | 目标 | 口径/限制 | 状态 |
| --- | --- | --- | --- | --- |
| `analyze_inventory_movement_trend` | L2 | 按产品/库位/日周月分析入、出、净流量 | 基于单据事实；不反推未记录操作 | `V2_ANALYTICS_PLACEHOLDER` |
| `analyze_inventory_ageing` | L2 | 按入库日/生产日生成库龄分布 | 仅“库龄”，没有保质期规则时不得称临期/过期 | `V2_ANALYTICS_PLACEHOLDER` |
| `build_inventory_snapshot_report` | L2 | 生成指定时点的产品/库位库存快照 | 当前只能生成“查询时快照”；历史时点需快照表 | `V2_ANALYTICS_PLACEHOLDER` |
| `analyze_inventory_level_trend` | L2 | 分析历史库存水平 | 缺少周期快照或完整事件账，当前不可准确重建 | `BLOCKED` |

### 5.3 v2 写入占位

| preview / execute | 风险 | 动作 | 前置安全要求 | 状态 |
| --- | --- | --- | --- | --- |
| `preview_inventory_adjustment` / `execute_inventory_adjustment` | L2 / L4 | 经审批的盘盈、盘亏或纠错 | 先定义调整单、原因码、双人/管理员确认、库存锁、审计和回滚策略；禁止直接改数量 | `BLOCKED` |

当前后端没有正式库存调整业务流程，所以 v2 也不能把“直接更新 inventory”设计成可执行工具。

## 6. `warehouse_expert`

### 6.1 v1 查询工具

| 工具 | 状态 | 风险 | 具体目标 | 关键输入/范围 | 关键输出 | 后端依据/权限 |
| --- | --- | --- | --- | --- | --- | --- |
| `resolve_warehouses` | `CURRENT` | L1 | 解析库位 | query、onlyAvailable | 候选与歧义 | 库位/容量查询 |
| `get_warehouse_status` | `CURRENT` | L1 | 单库位容量、库存、近期操作摘要 | warehouseRef、includeDetails | 状态、容量、明细、近期操作 | inventory/warehouse 聚合；`record:query` |
| `query_warehouse_capacity_distribution` | `CURRENT` | L1 | 全局/分区查看空置、正常、接近满、已满库位 | warehouseScope、occupancyBand、onlyAvailable、page | 容量、占用、剩余、占用率 | inventory warehouses；`record:query` |
| `query_warehouse_recent_operations` | `CURRENT` | L1 | 按库位和时间范围查近期流转 | warehouseScope、dateRange、eventTypes、limit | 业务时间线 | recent-operations + flow；`record:query` |
| `query_warehouse_mixed_storage_facts` | `CURRENT` | L1 | 返回同一库位存在多个产品/规格的事实 | warehouseScope、groupBy、limit | 产品数、规格数、位置证据 | 已新增聚合；不得自行判风险 |

原候选名 `query_warehouse_mixed_product_risks` 暂不采用。风险阈值未确认前只能返回混放事实。

### 6.2 v2 报表/分析占位

| 工具 | 风险 | 目标 | 口径/限制 | 状态 |
| --- | --- | --- | --- | --- |
| `analyze_warehouse_flow_efficiency` | L2 | 按库位分析入出/调拨次数和周转间隔 | 仅使用已记录流转事件 | `V2_ANALYTICS_PLACEHOLDER` |
| `analyze_warehouse_space_utilization` | L2 | 当前容量、空置和利用率分析 | 当前时点可做 | `V2_ANALYTICS_PLACEHOLDER` |
| `analyze_warehouse_capacity_trend` | L2 | 历史容量利用率趋势 | 需定时快照；现有 current capacity 不能还原历史 | `BLOCKED` |
| `analyze_mixed_storage_risk` | L2 | 按业务规则判定混放风险 | 需确认“同库位/同格/同侧”与风险阈值 | `BLOCKED` |

### 6.3 v2 写入占位

| preview / execute | 风险 | 允许动作 | 主要校验 | 目标权限/现状 | 状态 |
| --- | --- | --- | --- | --- | --- |
| `preview_warehouse_change` / `execute_warehouse_change` | L2 / L4 | `CREATE`、`UPDATE`、`SET_MAINTENANCE`、`DELETE` | 名称冲突、容量/库存/待办任务、地图引用、维护影响、删除引用 | 应新增 `warehouse:create/update/maintain/delete`；当前 Controller 仅 JWT | `V2_WRITE_PLACEHOLDER`，权限补齐前阻断 execute |

不设计恢复维护状态动作，因为当前代码未确认对应状态机和接口。

## 7. `logistics_expert`

### 7.1 v1 查询工具

| 工具 | 状态 | 风险 | 具体目标 | 关键输入/范围 | 关键输出 | 后端依据/权限 |
| --- | --- | --- | --- | --- | --- | --- |
| `resolve_products` | `CURRENT` | L1 | 解析单据/任务产品范围 | query、type、status | 候选与歧义 | 产品查询 |
| `resolve_warehouses` | `CURRENT` | L1 | 解析来源/目标库位 | query | 候选与歧义 | 库位查询 |
| `query_pallet_tasks` | `CURRENT` | L1 | 查询入库、出库、调拨任务状态 | code、taskType、bizScene、status、product、targetWarehouse、date、page | 任务、状态、待办原因 | `/api/pallet-codes/tasks/list`；只读任务权限 |
| `query_stock_documents` | `CURRENT` | L1 | 统一查询入库、出库、半成品单据 | documentType、product、warehouse、operator、date、page | 单据类型、数量、操作时间、状态 | in-stock/out-stock/semi records 聚合 |
| `query_auto_inbound_batches` | `CURRENT` | L1 | 查询当前用户智能报数批次历史和状态 | date/status/limit | batchRef、状态、任务数、过期时间 | `/api/auto-inbound/history`；当前仅 Redis 最近 20 条 |
| `get_auto_inbound_batch_detail` | `CURRENT` | L1 | 查询已选择批次解析详情 | batchRef | 解析任务、缺失项、warning、提交状态 | `/api/auto-inbound/{batchId}`；校验所属用户 |

### 7.2 v2 报表、dry-run 与分析占位

| 工具 | 风险 | 目标 | 口径/限制 | 状态 |
| --- | --- | --- | --- | --- |
| `analyze_stock_movement_throughput` | L2 | 入库/出库/调拨按日周月、产品、库位统计 | 传统与托盘流程需去重并标来源 | `V2_ANALYTICS_PLACEHOLDER` |
| `analyze_task_cycle_time` | L2 | 任务创建到确认/取消耗时与积压 | 需统一 task 状态和时间戳口径 | `V2_ANALYTICS_PLACEHOLDER` |
| `analyze_auto_inbound_reconciliation` | L2 | 比较解析、用户修订、确认和实际入库 | Redis 24h 不足以做长期报表，需持久化解析/确认事实 | `BLOCKED` |
| `preview_auto_inbound_report` | L2 | 将报数文本解析为候选任务，不执行 | 必须后端确定性解析产品/库位；成品确认仍拒绝 | `V2_ANALYTICS_PLACEHOLDER` |
| `preview_inbound_plan` | L2 | dry-run 入库位置、容量、化验/筛网/码状态 | 不分配位置、不加锁、不建任务 | `V2_ANALYTICS_PLACEHOLDER` |
| `preview_outbound_plan` | L2 | dry-run 扣减批次/托盘、库存不足和化验条件 | 从写服务抽出纯选择器 | `V2_ANALYTICS_PLACEHOLDER` |
| `preview_transfer_plan` | L2 | dry-run 来源、目标位置和冲突 | 不创建 pallet_task | `V2_ANALYTICS_PLACEHOLDER` |
| `preview_task_transition` | L2 | 预览任务确认或取消的影响 | action 仅 `CONFIRM`/`CANCEL`；批量上限固定 | `V2_ANALYTICS_PLACEHOLDER` |

### 7.3 v2 写入占位

| execute | 风险 | 业务动作 | 必须绑定的 preview | 目标权限/约束 | 状态 |
| --- | --- | --- | --- | --- | --- |
| `execute_inbound_plan` | L3 | 传统/托盘/平面图受控入库 | `preview_inbound_plan` | 专用 inbound execute 权限、库存/位置重检、事务、幂等 | `V2_WRITE_PLACEHOLDER` |
| `execute_outbound_plan` | L3 | 受控出库 | `preview_outbound_plan` | 专用 outbound execute 权限、库存锁、化验条件、幂等 | `V2_WRITE_PLACEHOLDER` |
| `execute_transfer_plan` | L3 | 托盘调拨 | `preview_transfer_plan` | 专用 transfer execute 权限、源/目标重检、事务 | `V2_WRITE_PLACEHOLDER` |
| `execute_auto_inbound_batch` | L3 | 仅确认受支持的半成品报数批次 | `preview_auto_inbound_report` + 确认预览 | 同一用户、批次未过期、成品任务拒绝、幂等 | `V2_WRITE_PLACEHOLDER` |
| `execute_task_transition` | L3/L4 | 确认或取消入/出/调拨任务 | `preview_task_transition` | action 级权限、批量上限、状态版本、全量审计 | `V2_WRITE_PLACEHOLDER` |

历史 `semi/prepare/*` 和 `semi/consume/*` 已被代码拒绝，标记 `FORBIDDEN`，不得复活为 MCP。

## 8. `pallet_expert`

### 8.1 v1 查询工具

| 工具 | 状态 | 风险 | 具体目标 |
| --- | --- | --- | --- |
| `get_pallet_status` | `CURRENT` | L1 | 单码当前身份、状态、位置和任务摘要 |
| `query_qr_code_lifecycle` | `CURRENT` | L1 | 单码完整生命周期安全时间线 |
| `query_printed_not_inbound_codes` | `CURRENT` | L1 | 已打印未入库二维码分组 |
| `query_pallet_anomalies` | `CURRENT` | L1 | 已确认口径的状态/库存/流转异常 |
| `query_pallet_flow_records` | `CURRENT` | L1 | 按码、产品、库位、日期查询流转记录 |
| `query_qr_batch_inbound_completion` | `CURRENT` | L1 | 批次/订单/产品的入库完成率 |
| `query_fixed_product_qr_pool` | `CURRENT` | L1 | 固定产品码池按产品、码、状态、freeOnly 分页查询 |

现有工具的参数与语义以 MCP 注册表为准。本专家可以使用产品/库位 resolver，但不得据此执行物流任务。

### 8.2 v2 报表/分析占位

| 工具 | 风险 | 目标 | 限制 | 状态 |
| --- | --- | --- | --- | --- |
| `analyze_pallet_state_health` | L2 | 码状态、库存状态、任务状态一致性趋势 | 异常类型必须登记，不允许模型自定义规则 | `V2_ANALYTICS_PLACEHOLDER` |
| `analyze_qr_pool_utilization` | L2 | FREE/PENDING/INSTOCK/INVALID/ORDER_RESERVED 结构和周转 | 状态枚举需版本化 | `V2_ANALYTICS_PLACEHOLDER` |
| `build_qr_batch_reconciliation_report` | L2 | 打印、预留、使用、入库、回收数量对账 | 必须区分生产标签与固定产品码 | `V2_ANALYTICS_PLACEHOLDER` |
| `export_qr_labels` | L2 | 导出已明确范围的二维码标签文件 | 只导出，不改变码状态；固定上限和短期下载引用 | `V2_ANALYTICS_PLACEHOLDER` |

### 8.3 v2 写入占位

| preview / execute | 风险 | 动作 | 主要校验 | 状态 |
| --- | --- | --- | --- | --- |
| `preview_qr_code_generation` / `execute_qr_code_generation` | L2 / L3 | 生成有限数量普通二维码 | count 上限、重复请求、权限、幂等 | `V2_WRITE_PLACEHOLDER` |
| `preview_fixed_qr_binding` / `execute_fixed_qr_binding` | L2 / L4 | 批量把 FREE 码绑定固定产品 | 产品唯一、FREE 数量、批量上限、配置审计 | `V2_WRITE_PLACEHOLDER` |
| `preview_fixed_qr_activation` / `execute_fixed_qr_activation` | L2 / L3 | 启用固定码并创建入库任务 | 码状态、产品、生产日期、任务重复、打印语义 | `V2_WRITE_PLACEHOLDER` |
| `preview_qr_status_change` / `execute_qr_status_change` | L2 / L4 | `INVALIDATE` 或 `RESTORE` | 当前库存/任务/轮次引用、原因必填、强确认 | `V2_WRITE_PLACEHOLDER` |
| `preview_pallet_flow_deletion` / `execute_pallet_flow_deletion` | L2 / L4 | 删除符合保留规则的历史流转 | 仅 180 天外且非当前轮次仍不足；需明确法定/业务保留策略 | `BLOCKED` |

本地打印助手、浏览器 `localhost` 服务和打印机控制标记 `FORBIDDEN`，服务端 Agent 不得调用。

## 9. `production_expert`

### 9.1 v1 查询工具

| 工具 | 状态 | 风险 | 具体目标 | 后端依据/权限 |
| --- | --- | --- | --- | --- |
| `resolve_production_entities` | `CURRENT` | L1 | 解析订单号或煮糖批次号，返回唯一 opaque ref | 订单/批次分页；对应 view 权限 |
| `query_production_order_progress` | `CURRENT` | L1 | 订单计划、状态、领料、产出、标签、入库进度 | order detail/trace；`production:order:view` |
| `query_boiling_batch_trace` | `CURRENT` | L1 | 煮糖批次详情和上/下游追溯 | boiling detail/trace/graph；`production:boiling:view` |
| `query_material_pick_trace` | `CURRENT` | L1 | 订单实际领料和托盘来源追溯 | order trace/material；`production:material:view` |
| `query_production_label_completion` | `CURRENT` | L1 | 标签预留、打印、使用、回收、入库完成情况 | label batches + output codes；订单 view |
| `query_in_process_materials` | `CURRENT` | L1 | 查询当前生产中半成品范围 | materials/in-process；`production:material:view` |
| `query_material_candidates` | `CURRENT` | L1 | 为已确认订单查询可领用材料候选，仅查询不领用 | material-candidates；`production:material:view` |

### 9.2 v2 报表/分析占位

| 工具 | 风险 | 目标 | 口径/限制 | 状态 |
| --- | --- | --- | --- | --- |
| `analyze_production_order_completion` | L2 | 订单数量、状态、周期和完成率趋势 | 先集中订单状态枚举和终态定义 | `BLOCKED` |
| `analyze_material_consumption_variance` | L2 | 计划与实际领料差异 | plannedMaterialJson 需结构化版本和单位换算 | `V2_ANALYTICS_PLACEHOLDER` |
| `analyze_production_yield` | L2 | 计划/实际产出及材料产出比 | 需确认重量、板、件的标准换算和损耗口径 | `BLOCKED` |
| `analyze_label_reconciliation` | L2 | 预留、打印、使用、回收、待入库标签对账 | 按标签批次和产品确定性聚合 | `V2_ANALYTICS_PLACEHOLDER` |
| `build_boiling_trace_report` | L2 | 生成单批次的可审计追溯报告 | 仅现有 trace graph 证据，不补推断边 | `V2_ANALYTICS_PLACEHOLDER` |

### 9.3 v2 写入占位

| preview / execute | 风险 | 动作 | 当前权限/关键校验 | 状态 |
| --- | --- | --- | --- | --- |
| `preview_production_order_change` / `execute_production_order_change` | L2 / L3 | `CREATE`、`CANCEL` | `production:order:create/cancel`；状态、计划 JSON、重复订单、并发编号 | `V2_WRITE_PLACEHOLDER` |
| `preview_boiling_batch_change` / `execute_boiling_batch_change` | L2 / L3 | `CREATE`、`UPDATE`、`CANCEL` | boiling create/update/cancel；批次状态和引用重检 | `V2_WRITE_PLACEHOLDER` |
| `preview_material_consumption` / `execute_material_consumption` | L2 / L3 | `PICK`、`FINISH_PICKING` | `production:material:pick`；仅成品订单、托盘在库、库存锁、全量领料摘要 | `V2_WRITE_PLACEHOLDER` |
| `preview_production_output_change` / `execute_production_output_change` | L2 / L3 | `ADD`、`UPDATE` | `production:output:create`；订单状态、产品、数量、日期 | `V2_WRITE_PLACEHOLDER` |
| `preview_production_output_deletion` / `execute_production_output_deletion` | L2 / L4 | 删除尚可删除的产出记录 | 引用、标签、入库任务和状态影响；强确认 | `V2_WRITE_PLACEHOLDER` |
| `preview_production_label_operation` / `execute_production_label_operation` | L2 / L3 | `RESERVE`、`MARK_PRINTED` | label reserve/print 权限；FREE 码锁定、数量换算、幂等 | `V2_WRITE_PLACEHOLDER` |
| `preview_production_finish` / `execute_production_finish` | L2 / L3 | 确认生产结束并生成待入库任务 | `production:label:finish`；实际产出、预留码、重复完成、事务 | `V2_WRITE_PLACEHOLDER` |

旧 `bindFixedQrs` 当前代码明确拒绝，标记 `FORBIDDEN`，不得因 REST 路径存在而暴露。

## 10. `quality_expert`（Agent v1 运行时 ID：`assay_expert`）

### 10.1 v1 查询工具

| 工具 | 状态 | 风险 | 具体目标 |
| --- | --- | --- | --- |
| `resolve_products`、`resolve_warehouses` | `CURRENT` | L1 | 解析质量查询范围 |
| `get_assay_status` | `CURRENT` | L1 | 产品/日期的化验状态 |
| `query_assay_records` | `CURRENT` | L1 | 化验记录分页与最近记录 |
| `get_assay_report_detail` | `CURRENT` | L1 | 单份报告指标和标准快照 |
| `query_assay_abnormalities` | `CURRENT` | L1 | 不合格、无标准、多候选等已登记异常 |
| `query_products_without_recent_assay` | `CURRENT` | L1 | 当前在库产品的缺化验事实 |
| `query_assay_standard_coverage` | `CURRENT` | L1 | 第一版仅 `PRODUCT_WITHOUT_STANDARD` |
| `query_assay_groups` | `CURRENT` | L1 | 批量化验组目录、包含产品和状态 |
| `query_quality_standard_catalog` | `CURRENT` | L1 | 标准目录按品类、状态、版本查询 |
| `get_quality_standard_detail` | `CURRENT` | L1 | 标准 7 项指标、范围、版本和备注 |
| `query_product_standard_relations` | `CURRENT` | L1 | 已确认产品绑定哪些标准、默认项和生效状态 |

v1 质量查询目标权限建议从 `quality:test` 拆成 `quality:view`；标准/组/关系当前多处仅 JWT，细粒度权限补齐前不得扩大可见范围。

### 10.1.1 已确认的库存批次质量增量

业务已确认 `product_id + production_date` 是产品批次键，`assay.sample_date` 的业务语义就是生产日期。因此“库存批次没有稳定关联键”的旧结论不再成立，但现有运行时查询尚未统一到该口径。

| 工具 | 风险 | 目标 | 状态 |
| --- | --- | --- | --- |
| `query_inventory_by_quality_standard` | L1 | 按当前库存批次最新化验筛选命中指定标准的库存 | `PLANNED_READ_MODEL_REQUIRED` |
| `query_inventory_by_assay_metrics` | L1 | 按当前库存批次最新化验的受控原始指标条件筛选库存 | `PLANNED_READ_MODEL_REQUIRED` |

两者必须共享 `inventory_current_assay_fact_v1`，不得以 Agent 多次调用“库存 + 产品最近化验”代替。运行时白名单只能在统一读模型、权限、回归测试和工具登记完成后扩展。

### 10.2 v2 报表/分析占位

| 工具 | 风险 | 目标 | 口径/限制 | 状态 |
| --- | --- | --- | --- | --- |
| `analyze_assay_trends` | L2 | 指标、判定随时间趋势 | 标准版本变化必须在时间线上标注 | `V2_ANALYTICS_PLACEHOLDER` |
| `analyze_assay_pass_rate` | L2 | 按产品/日期/标准版本统计通过率 | `NO_STANDARD` 和 `MULTIPLE_CANDIDATES` 不并入 FAIL | `V2_ANALYTICS_PLACEHOLDER` |
| `analyze_quality_metric_distribution` | L2 | 7 项指标分布、越界率和缺失率 | 仅描述性统计，不自动归因 | `V2_ANALYTICS_PLACEHOLDER` |
| `analyze_quality_standard_coverage` | L2 | 标准覆盖、版本分布和关系现状 | `ASSAY_WITHOUT_STANDARD`、`UNUSED_STANDARD` 继续待确认 | `BLOCKED`（未确认类型） |
| `analyze_inventory_batch_qualification` | L2 | 判断当前库存批次是否合格 | 批次键已确认为产品 + 生产日期；仍须先完成统一最新化验读模型 | `READY_AFTER_IQ_02_IQ_03` |

### 10.3 v2 写入占位

| preview / execute | 风险 | 动作 | 权限/校验 | 状态 |
| --- | --- | --- | --- | --- |
| `preview_assay_change` / `execute_assay_change` | L2 / L3 | `CREATE_SINGLE`、`CREATE_NEW_VERSION` | `quality:assay:write`（建议新增）；产品/日期/版本、7 项字段、标准判定预览 | `V2_WRITE_PLACEHOLDER` |
| `preview_assay_deletion` / `execute_assay_deletion` | L2 / L4 | 删除化验记录 | 关联引用、历史审计、替代版本、强确认 | `V2_WRITE_PLACEHOLDER` |
| `preview_assay_group_change` / `execute_assay_group_change` | L2 / L4 | `CREATE`、`UPDATE`、`DELETE` | 建议 `quality:group:*`；产品范围和使用影响 | `V2_WRITE_PLACEHOLDER`，权限补齐前阻断 |
| `preview_quality_standard_change` / `execute_quality_standard_change` | L2 / L4 | `CREATE`、`UPDATE`、`DELETE` | 建议 `quality:standard:*`；固定 7 指标、编号/版本唯一、关系影响；强制删除默认禁止 | `V2_WRITE_PLACEHOLDER`，权限补齐前阻断 |
| `preview_product_standard_relation_change` / `execute_product_standard_relation_change` | L2 / L4 | `BIND`、`UPDATE`、`SET_DEFAULT`、`DELETE` | 产品/标准品类一致、最后一个启用关系不可删、权限和审计 | `V2_WRITE_PLACEHOLDER`，权限补齐前阻断 |

强制删除质量标准不复用 `product:delete`；必须先修正为质量域专用权限和影响分析。

## 11. `master_data_expert`

### 11.1 v1 查询工具

| 工具 | 状态 | 风险 | 具体目标 | 后端依据 |
| --- | --- | --- | --- | --- |
| `query_product_catalog` | `CURRENT` | L1 | 按名称、品类、半/成品、包装、筛网、状态查询产品目录 | products page |
| `get_product_detail` | `CURRENT` | L1 | 产品字段、换算、堆放能力、筛网和安全引用摘要 | product detail + relation aggregate |
| `query_screen_mesh_catalog` | `CURRENT` | L1 | 查询筛网目录、规格和状态 | screen-mesh list/page |

`resolve_products` 仍是其他专家的对象解析工具，不能替代产品目录和详情。

### 11.2 v2 报表/分析占位

| 工具 | 风险 | 目标 | 限制 | 状态 |
| --- | --- | --- | --- | --- |
| `analyze_master_data_completeness` | L2 | 发现缺筛网、缺换算、缺包装等配置缺口 | 规则必须按产品状态/类型登记 | `V2_ANALYTICS_PLACEHOLDER` |
| `analyze_master_data_usage_impact` | L2 | 修改/删除前查询库存、单据、托盘、化验、生产引用影响 | 只返回引用摘要，不泄露无权限记录 | `V2_ANALYTICS_PLACEHOLDER` |

### 11.3 v2 写入占位

| preview / execute | 风险 | 动作 | 权限/校验 | 状态 |
| --- | --- | --- | --- | --- |
| `preview_product_change` / `execute_product_change` | L2 / L4 | `CREATE`、`UPDATE`、`DELETE` | `product:create/update/delete`；唯一性、枚举、库存/历史/托盘/标准引用、换算影响 | `V2_WRITE_PLACEHOLDER` |
| `preview_screen_mesh_change` / `execute_screen_mesh_change` | L2 / L4 | `CREATE`、`UPDATE`、`DELETE` | 当前复用 product 权限；建议拆 `screen_mesh:*`；产品引用影响 | `V2_WRITE_PLACEHOLDER` |

不设计 `manage_reference_data` 万能工具；产品和筛网必须是独立、强类型 schema。

## 12. `administration_expert`

### 12.1 v1 查询工具

| 工具 | 状态 | 风险 | 具体目标 | 权限/脱敏 |
| --- | --- | --- | --- | --- |
| `query_employee_roster` | `CURRENT` | L1 | 按工号、姓名、部门、状态、角色查询员工 | `rbac:user:view`；手机号默认脱敏，不返回密码/绑定凭据 |
| `query_roles` | `CURRENT` | L1 | 查询角色、状态和用户数量摘要 | `rbac:role:view` |
| `get_role_permission_summary` | `CURRENT` | L1 | 查询已选择角色的权限分组和敏感能力摘要 | `rbac:role:view`；不返回内部实现上下文 |

### 12.2 v2 报表/分析占位

| 工具 | 风险 | 目标 | 限制 | 状态 |
| --- | --- | --- | --- | --- |
| `analyze_role_permission_exposure` | L2 | 发现高风险权限、重复角色和越权组合 | 风险规则必须由权限策略登记，模型不能自定 | `V2_ANALYTICS_PLACEHOLDER` |
| `analyze_employee_access_coverage` | L2 | 员工状态与角色分配完整性 | 只能分析当前状态；缺少历史授权事件时不做历史结论 | `V2_ANALYTICS_PLACEHOLDER` |

### 12.3 v2 写入占位

| preview / execute | 风险 | 动作 | 权限/校验 | 状态 |
| --- | --- | --- | --- | --- |
| `preview_employee_change` / `execute_employee_change` | L2 / L4 | `ADD`、`UPDATE`、`IMPORT`、`CLEAR_RESIGNED` | 对应 employee/user 权限；字段脱敏、批量影响、账号/绑定引用、强确认 | `V2_WRITE_PLACEHOLDER` |
| `preview_role_change` / `execute_role_change` | L2 / L4 | `CREATE`、`UPDATE`、`SET_STATUS`、`DELETE` | `rbac:role:*`；系统角色、在用用户、最后管理员保护 | `V2_WRITE_PLACEHOLDER` |
| `preview_role_permission_assignment` / `execute_role_permission_assignment` | L2 / L4 | 替换角色权限集合 | `rbac:role:assign_permission`；增删差异、权限升级、高风险权限二次确认 | `V2_WRITE_PLACEHOLDER` |

任何修改当前执行用户自身权限、删除最后一个管理员或通过 Agent 提权的请求必须 fail-closed。

## 13. `audit_expert`

### 13.1 v1 查询工具

| 工具 | 状态 | 风险 | 具体目标 | 权限/限制 |
| --- | --- | --- | --- | --- |
| `search_operation_logs` | `CURRENT` | L1 | 按模块、操作类型、操作人、时间查业务日志 | `log:view`；old/new data 字段级脱敏 |
| `query_agent_tool_audit` | `CURRENT` | L1 | 查 Agent 工具调用、结果类别、延迟和拒绝原因 | `agent:audit:view`；无 prompt/密钥/原始堆栈 |
| `query_agent_answer_reviews` | `CURRENT` | L1 | 查回答 Review 状态、评分和安全摘要 | `agent:review:view`；安全摘要 |

### 13.2 v2 报表/分析占位

| 工具 | 风险 | 目标 | 限制 | 状态 |
| --- | --- | --- | --- | --- |
| `analyze_operation_risk_trend` | L2 | 按模块/动作/失败类型分析操作风险 | 当前 `@LogOperation` 覆盖不完整，需先补审计 | `BLOCKED` |
| `analyze_agent_quality` | L2 | 路由、工具成功率、Review、部分成功和拒绝趋势 | 只能使用安全摘要和登记指标 | `V2_ANALYTICS_PLACEHOLDER` |
| `analyze_agent_tool_usage_cost` | L2 | 工具耗时、调用数、token 和模型成本分析 | 成本价格版本和模型 usage 必须可追溯 | `V2_ANALYTICS_PLACEHOLDER` |

### 13.3 v2 写入占位

| preview / execute | 风险 | 动作 | 权限/校验 | 状态 |
| --- | --- | --- | --- | --- |
| `preview_agent_review_status_change` / `execute_agent_review_status_change` | L2 / L3 | 更新 Review 状态和受控反馈 | `agent:review:update`；合法状态机、审计、不得修改原回答/工具证据 | `V2_WRITE_PLACEHOLDER` |

操作日志和工具审计记录不提供删除、改写工具。

## 14. `analytics_expert`（仅 v2）

设置该专家的目的是隔离跨域报表，而不是重新制造“能看到所有工具”的万能 Agent。它的业务工具白名单只允许：

| 工具 | 风险 | 具体目标 | 安全边界 | 状态 |
| --- | --- | --- | --- | --- |
| `run_registered_report` | L2 | 运行一个登记且版本固定的报表定义 | 仅允许 registry 中的 definitionId、维度、过滤和公式 | `V2_ANALYTICS_PLACEHOLDER` |
| `compare_registered_report_periods` | L2 | 同口径跨期对比 | 两期必须同 definition/version/datasetVersion | `V2_ANALYTICS_PLACEHOLDER` |
| `export_registered_report` | L2 | 导出登记报表为 CSV/XLSX/PDF | 权限、脱敏、行数、TTL、下载审计；不接受路径 | `V2_ANALYTICS_PLACEHOLDER` |

首批数据集占位：

| datasetId | owner 数据域 | 用途 | 当前准备度 |
| --- | --- | --- | --- |
| `inventory_snapshot_v1` | inventory/warehouse | 当前库存与容量快照 | 当前时点可做；历史需快照表 |
| `stock_movement_v1` | logistics | 入/出/调拨流量 | 需统一传统与托盘事件、去重 |
| `production_order_fact_v1` | production | 订单、材料、产出、标签 | 可从聚合详情起步；状态枚举待集中 |
| `quality_assay_fact_v1` | quality | 化验指标、判定、标准版本 | 可做；产品 + 生产日期批次语义已确认 |
| `pallet_lifecycle_fact_v1` | pallet | 码状态、轮次、任务和流转 | 可做；扫描日志缺口仍存在 |
| `agent_audit_fact_v1` | audit | 路由、工具、Review、成本 | 需统一指标持久化和数据保留策略 |

跨域报表定义只做占位，不加入当前配方注册表：

- `inventory_quality_overview_v1`：目标改为当前库存批次 + 批次最新化验，数据范围为 `CURRENT_INVENTORY_BATCH_LATEST_ASSAY`；在 IQ-02、IQ-03 完成前，现有 `PRODUCT_LATEST_ASSAY` 拼接仍不得宣称批次资格。
- `production_material_output_reconciliation_v1`：订单实际材料、实际产出和标签对账；产出率公式待业务确认。
- `warehouse_flow_efficiency_v1`：库位当前容量与登记流转量；历史容量趋势在快照表完成前不支持。

## 15. 共享解析器和 opaque ref

v1 建议仅保留下列共享解析能力：

- `resolve_products`
- `resolve_warehouses`
- `resolve_production_entities`（仅 production）

其他列表/详情工具在自身聚合接口内返回 `ENTITY_AMBIGUOUS` 和候选 ref，避免为每张表增加 resolver。opaque ref 必须：

- 包含实体类型、版本、用户/权限范围和短 TTL；
- 防篡改，不能等同于可手填数据库 ID；
- 恢复 HITL 后重新校验实体仍存在、版本未漂移、用户仍有权限。

## 16. 明确不开放的能力

| 能力 | 结论 | 原因 |
| --- | --- | --- |
| 任意 SQL、任意 HTTP、任意表/列报表 | `FORBIDDEN` | 绕过业务规则、RBAC 和审计 |
| main Agent 直接持有业务工具 | `FORBIDDEN` | 破坏最小权限和模块边界 |
| 模型生成任意执行 DAG | `FORBIDDEN` | 当前仅允许登记配方 |
| 多专家自由互聊 | `FORBIDDEN` | 状态、权限和审计不可控 |
| 直接修改库存数量 | `FORBIDDEN` | 必须先建立正式调整单流程 |
| 历史生产占用/消耗旧接口 | `FORBIDDEN` | 代码已拒绝旧流程 |
| 旧产出二维码手工绑定 | `FORBIDDEN` | 当前代码已拒绝，预打印流程取代 |
| 本地打印助手/打印机控制 | `FORBIDDEN` | 服务端 Agent 不应控制用户本机 |
| 登录、微信绑定、手机号/工号绑定 | `FORBIDDEN` | 认证基础设施不做普通 MCP |
| 修改/删除业务审计日志 | `FORBIDDEN` | 破坏审计证据 |

## 17. 实现前阻断项

### P0：所有新增工具共同前置

1. 导出 `docs/openapi.json`，建立 Controller/前端 API/工具 registry 差异检查。
2. 新增只读与写入专用权限，修复仅 JWT 的 Warehouse、Pallet、AutoInbound、AssayGroup、QualityStandard、ProductStandardRelation 接口。
3. 补齐关键写操作的 `@LogOperation` 或 Agent 专用操作审计，尤其托盘、自动入库、生产和 RBAC。
4. 统一业务错误码、时间范围、分页、opaque ref 和 safe result schema。
5. v2 写操作必须先实现 preview store、execution token、幂等表、实体版本、事务重检和强确认。

### 业务/数据口径待确认

1. `ASSAY_WITHOUT_STANDARD`、`UNUSED_STANDARD`。
2. 当前库存批次与化验的稳定关联；解决前不得回答“当前库存批次是否合格”。
3. 生产订单完整状态机、完工状态和周期口径。
4. 材料消耗差异、产出率、损耗率及板/件/重量换算口径。
5. 混放风险规则和库位状态/容量同步规则。
6. 旧传统调拨与托盘调拨哪个是唯一未来入口。
7. 库存/库位历史趋势需要的快照粒度和保留期限。
8. 自动入库解析/修订/确认历史的持久化范围。
9. 流转日志删除的业务/合规保留策略。

## 18. 建议实施顺序

### Agent v1

1. `production_expert`：生产实体解析、订单进度、煮糖追溯、领料、标签、在制材料。
2. `logistics_expert`：任务、单据、自动入库历史/详情。
3. `warehouse_expert`：容量分布、近期操作、混放事实。
4. `quality_expert` 与 `master_data_expert`：标准、关系、化验组、产品、筛网。
5. `audit_expert`：先修权限/脱敏，再开放日志和 Agent 审计。
6. `administration_expert`：最后开放，管理员专用。
7. inventory/pallet 的剩余明细工具收口。

### Agent v2-A

1. 建立数据集/指标/报表 registry 和确定性计算层。
2. 优先做单域趋势与对账；跨域定义继续受控登记。
3. 再实现 inbound/outbound/transfer 和生产流程 preview；preview 通过测试不等于允许 execute。

### Agent v2-B

1. 先普通 L3：物流计划执行、生产流程执行、Review 状态。
2. 再 L4：二维码状态、基础资料、质量配置、员工/RBAC。
3. 库存调整、流转删除等高风险能力保持阻断，直到业务审批和保留策略建立。

## 19. 每个工具的验收门槛

任一候选只有同时满足以下条件才能从占位变为运行时能力：

1. Java Agent 聚合接口只表达单一业务目标，不拼接任意 REST。
2. Java Gateway 校验 JWT/RBAC、专家、工具、风险、参数、数据范围和审计。
3. warehouse-mcp schema 有严格枚举、长度、日期、分页和批量上限。
4. Python Router 只路由到 owner 专家；专家白名单不包含相邻模块工具。
5. safe formatter 确定性处理 NO_DATA、部分失败、限制和敏感字段。
6. 查询有固定数据测试；分析有固定数据集和口径测试；写入有 preview/execute/并发/幂等/过期/权限/HITL 测试。
7. capability/recipe/report registry hash 纳入启动握手，任何漂移 fail-closed。
8. 文档、注册表、测试和工具数一致，`git diff --check` 与中文可读性检查通过。

## 20. 跨专家业务配方 Registry

跨专家业务目标不得由模型临时拆解为任意 DAG。新增配方只能先进入独立的设计态 Registry，完成业务语义、步骤、权限、预算、失败策略、测试和 hash 握手后，才能人工批准进入运行时 Registry。

配方不是跨模块问题的默认实现。如果语义层或 Java 聚合接口已经能用一个登记报表/业务工具确定性完成，应优先使用单工具；只有确实需要分步权限、依赖、HITL、预算或部分失败语义时才使用多专家配方。化验风险日报和二维码对账在事实层成熟后，可能收敛为 `analytics_expert` 的登记报表，而不必永久保留多专家编排。

当前运行时仍只有 `warehouse_inventory_latest_assay` 一个配方。下列配方全部为 `DESIGN_ONLY`：

| recipeId | 用户目标 | 固定专家步骤 | 关键语义/阻断项 |
| --- | --- | --- | --- |
| `outbound_preflight_check_v1` | 出库前检查库存、批次、化验、库位和任务冲突 | logistics preview -> inventory facts -> quality/rule decision -> main summary | 必须先得到精确待扣库存；质量放行和批次关联未完成前保持 `BLOCKED` |
| `inventory_health_check_v1` | 形成库存、容量、库龄、缺化验、缺标准和混放事实健康检查 | inventory -> warehouse -> quality -> main summary | 混放只能先返回事实；无快照时不输出历史趋势 |
| `production_order_closure_check_v1` | 查询订单从领料、产出、标签到待入库任务是否闭环 | production -> pallet -> logistics -> main summary | 只做闭环检查，不执行生产完成或任务确认 |
| `assay_risk_daily_v1` | 生成化验异常、缺化验和标准覆盖日报 | inventory population -> quality aggregates -> main/report formatter | `NO_DATA`、缺化验、无标准和查询失败必须分开 |
| `qr_inbound_reconciliation_v1` | 对账订单标签、二维码状态、打印和入库任务 | production -> pallet -> logistics -> main summary | 生产标签和固定产品码分开统计，不自动补入库 |

每个配方定义至少包含：

- `recipeId`、`recipeVersion`、`status`、`executionMode`；
- trigger 和明确的 anti-trigger；
- 固定步骤、owner expert、`allowedTools`、依赖和不可变顺序；
- `maxSteps`、`maxToolCalls`、`maxFanOut`、超时和数据量上限；
- 输入/输出 `dataScope`、规则版本、指标版本和时间点；
- `NO_DATA`、部分失败、超时、权限拒绝、预算超限的确定性策略；
- 固定测试数据、预期步骤指纹和 registry hash。

专家之间只传结构化安全结果和 opaque ref，不传自然语言指令。模型不能增加、删除、重排步骤，不能替换专家或工具，也不能让专家自由互聊。设计态定义见 `docs/agent/cross-expert-recipe-roadmap-registry.yaml`；它不修改当前 `orchestration-recipe-registry.yaml`。

## 21. 数据事实、快照与指标语义层

### 21.1 事实与快照

报表、预测和优化不能直接依赖页面 DTO 或临时拼表。规划以下版本化数据资产：

| datasetId | 类型/粒度 | 时间字段 | 主要用途 | 当前状态 |
| --- | --- | --- | --- | --- |
| `inventory_snapshot_v1` | 每次快照 × 组织/工厂/库区/库位/格位/产品/生产日/托盘 | `snapshotAt` | 历史库存水平、库龄和空间占用 | 当前只有实时库存，需新增快照任务和保留策略 |
| `stock_movement_fact_v1` | 每个入库/出库/调拨业务事件 | `occurredAt`、`recordedAt` | 吞吐、净流量、周转、事件账 | 需统一传统和托盘流程并去重 |
| `production_order_fact_v1` | 每个订单版本/状态事件 | `eventAt` | 订单进度、周期、完成率 | 状态枚举和状态机待集中 |
| `production_material_fact_v1` | 订单 × 实际领用明细/托盘 | `pickedAt` | 材料消耗和追溯 | 可用，单位口径需版本化 |
| `production_output_fact_v1` | 订单 × 产品 × 产出行/标签批次 | `outputAt` | 产出、标签和入库对账 | 计划/实际单位换算待确认 |
| `assay_fact_v1` | 化验记录版本 × 指标 | `sampleAt`、`createdAt` | 合格率、指标趋势、标准版本影响 | 可用；不等于库存批次放行事实 |
| `pallet_lifecycle_fact_v1` | 托盘码 × cycle × 生命周期事件 | `eventAt` | 状态健康、周转和异常 | 缺独立扫描日志的场景必须标记 |
| `warehouse_capacity_snapshot_v1` | 快照 × 库位/侧/区域 | `snapshotAt` | 容量利用率历史趋势 | 当前只有实时容量，需新增快照 |

事实数据必须保留来源系统、来源记录 ref、写入时间、事件时间、修订版本、组织作用域和数据质量标志。业务数据的修正不能静默覆盖历史事实；应记录更正事件或版本。

### 21.2 指标 Registry

任何可展示、比较、告警或用于模型特征的指标都必须登记：

- `metricId` 和不可复用的 `version`；
- 中文名称、业务定义、负责人 `owner`；
- 确定性 `formula`、分子/分母、空值和除零规则；
- `unit`、允许维度和聚合方式；
- event time / snapshot time / processing time 的时间口径和时区；
- 来源 fact 及其最低版本；
- 新鲜度、完整性、唯一性、迟到数据和已知缺口；
- 状态：`DRAFT`、`APPROVED`、`ACTIVE`、`DEPRECATED`、`BLOCKED`；
- 基准测试数据和期望结果 hash。

首批指标占位包括库存等价件数、入/出库量、净流量、库龄、库位利用率、订单完成率、材料消耗差异率、产出率、化验通过率、二维码入库完成率。完整结构见 `docs/agent/data-semantic-roadmap-registry.yaml`。

没有历史快照或完整事件账时，只能回答当前快照或已记录事件范围，不得声称支持历史库存水平、历史容量利用率或完整趋势。

## 22. 版本化业务规则引擎

Wiki 继续负责向人和 Agent 解释规则来源、术语和示例；确定性 Rule Engine 负责计算业务决策。LLM 不能依据 Wiki 文本直接给出最终放行、分配、完工或审批结论。

首批规则族：

| ruleId | 目标 | 现状与限制 |
| --- | --- | --- |
| `outbound_eligibility` | 判断已选择库存是否允许出库 | 当前规则散落在 Service；必须抽取并覆盖库存、化验、状态、任务和权限 |
| `quality_release` | 对明确业务批次输出放行/阻断及原因 | 批次与化验稳定关联完成前 `BLOCKED` |
| `fifo_allocation` | 按确认顺序选择库存 | 当前 Mapper 排序需审计和测试后才能登记 |
| `fefo_allocation` | 按有效期优先选择库存 | 缺有效期字段和业务规则，保持 `BLOCKED` |
| `mixed_storage` | 判定混放事实是否构成风险 | 同库位/同格/同侧和阈值待业务确认 |
| `capacity_allocation` | 选择可用侧/排/层并解释冲突 | 现有逻辑分散在写 Service，需提取纯规则并版本化 |
| `production_completion` | 判断订单是否满足完工条件 | 状态机、材料/产出/标签条件需集中 |
| `approval_required` | 按动作、影响和风险决定审批级别 | 当前无统一审批策略，先设计后实现 |

每个规则版本必须登记输入 schema、输出 decision enum、reason codes、生效时间、优先级、负责人、审批状态、实现 hash、测试集 hash 和兼容范围。preview 保存规则版本；execute 必须使用相同规则版本重新计算，版本漂移或结果变化时拒绝执行并要求重新 preview。

## 23. 文件导入与暂存批次

自动录入化验属于 `quality_expert`，不得新增万能文件导入工具。规划以下强类型工具：

| 工具 | 风险 | 作用 | 状态 |
| --- | --- | --- | --- |
| `inspect_assay_import_file` | L2 | 对已上传附件做文件安全检查并创建隔离暂存批次 | `V2_ANALYTICS_PLACEHOLDER` |
| `preview_assay_file_import` | L2 | 解析、映射、校验并生成逐行 preview，不写化验业务表 | `V2_ANALYTICS_PLACEHOLDER` |
| `execute_assay_file_import` | L3 | 只执行已确认、未过期且校验通过的暂存批次 | `V2_WRITE_PLACEHOLDER` |

处理流水线固定为：

```text
上传附件
  -> 大小/扩展名/MIME/文件魔数/恶意内容/宏/压缩炸弹检查
  -> 隔离区与 contentHash
  -> 模板识别和解析
  -> 字段映射与用户确认
  -> 产品/化验组/标准确定性解析
  -> 重复检测、单位和数值范围校验
  -> 标准匹配和判定 preview
  -> 行级 errorCode / warning
  -> stagingBatch + TTL + version
  -> HITL 确认
  -> execute + 幂等 + 事务 + 审计
```

暂存批次状态至少包括 `RECEIVED`、`QUARANTINED`、`PARSING`、`MAPPING_REQUIRED`、`VALIDATING`、`PREVIEW_READY`、`AWAITING_CONFIRMATION`、`EXECUTING`、`SUCCEEDED`、`FAILED`、`EXPIRED`、`CANCELLED`。

首版默认 fail-closed：所选行存在 blocking error 时不执行；是否允许“仅提交有效行”属于待确认事务策略，不由 Agent 自行决定。execute 不接受文件、字段映射或行数据，只接受 preview/confirmation/idempotency 引用。禁止从任意 URL 拉取文件、接受任意文件路径、执行宏、公式或嵌入对象。

原 `preview_assay_change` 不再承担文件导入，只处理结构化单条新增版本；文件批量导入使用上述独立工具链。

## 24. 外部工作群与消息渠道接入

规划独立 `Channel Integration Gateway`，通过白名单适配器连接企业微信、钉钉等渠道。它是身份、附件和消息安全边界，不是绕过 Agent Gateway 的新入口。

职责包括：

- 校验渠道签名、回调来源和应用身份；
- 以 tenant/channel/conversation/messageId 做消息去重和顺序控制；
- 将渠道用户绑定到系统用户和 RBAC，不信任昵称或群名；
- 映射群线程、引用消息、附件、Agent session 和 requestId；
- 附件先进入安全检查与隔离区；
- 对通知、失败、重试、撤回和审计建立统一事件；
- 只允许配置好的渠道 API 和目标，禁止任意 HTTP/任意 webhook。

群聊首期只支持查询、登记报表、提醒和跳转确认。即使发消息的人有权限，群内其他成员也可能没有相同权限，因此：

- 只有标记为 `GROUP_SAFE` 的字段和摘要可以直接回群；
- 敏感、个人、权限、审计或明细数据必须私聊，或返回已登录 Web 页的短期深链接；
- 群聊不得直接恢复 L3/L4 HITL，也不得执行 execute；
- L3/L4 请求只能生成 Web 跳转，用户在系统内重新鉴权、查看 preview 并确认；
- 渠道身份解绑、成员变化或权限变化后，已有确认链接立即重新校验或失效。

## 25. 规划、预测、优化与仿真层

未来新增 `planning_expert`，但仅在历史数据、指标和业务规则稳定后启用。它没有业务查询工具并集，也没有任何 `execute_*` 工具，只能访问登记的数据集、预测服务、规则引擎和求解器。

设计态工具：

| 工具 | 目标 | 关键输出 | 状态 |
| --- | --- | --- | --- |
| `forecast_registered_demand` | 对登记产品范围和时间粒度做需求预测 | point forecast、区间、模型版本、特征版本、误差基线 | `PLANNING_PLACEHOLDER` |
| `assess_inventory_risk` | 评估安全库存、缺货和过量风险 | 风险区间、假设、数据新鲜度、规则版本 | `PLANNING_PLACEHOLDER` |
| `generate_replenishment_scenarios` | 生成有限补货方案 | 方案、约束、成本/服务水平、不可行原因 | `PLANNING_PLACEHOLDER` |
| `generate_production_schedule_scenarios` | 生成有限生产排程候选 | 订单/产能/材料约束、目标函数、冲突 | `PLANNING_PLACEHOLDER` |
| `optimize_warehouse_layout_scenarios` | 比较库位规划候选 | 移动量、容量、混放规则、成本和限制 | `PLANNING_PLACEHOLDER` |
| `simulate_registered_scenario` | 执行登记的 what-if 场景 | 输入假设、输出指标、敏感性、模型/规则版本 | `PLANNING_PLACEHOLDER` |
| `compare_planning_scenarios` | 确定性比较已生成方案 | 同口径指标、Pareto/排序依据、不可比项 | `PLANNING_PLACEHOLDER` |

计算链固定为“预测模型 + 版本化规则引擎 + 优化器/求解器 + LLM 解释 + 人工确认”。LLM 不负责权威数值计算、约束求解或最终业务判定。

规划结果是 advisory artifact，不是 preview，也不是 executionToken。用户选择某个建议后，必须重新发起对应业务请求，从 resolver 和新的业务 preview 开始；不得把方案 ref 直接转换为生产订单、补货、库存移动或其他写操作。

## 26. 模型治理

预测、排序、异常检测和优化辅助模型统一进入 Model Registry。每个模型发布版本至少登记：

- `trainingDatasetId/version/hash`、数据时间范围和数据授权；
- `featureSetId/version`、特征计算代码 hash 和泄漏检查；
- `modelId/version`、算法、超参数、运行环境和依赖；
- 离线评测集、指标、置信区间、基线模型和相对提升；
- 分产品/时间/组织的偏差和失败切片；
- 数据漂移、特征漂移、预测误差和业务结果监控阈值；
- 可解释性方法、主要特征、限制和不适用范围；
- owner、reviewer、审批状态、审批时间、回滚版本和变更记录。

审批状态建议为 `DRAFT`、`EVALUATED`、`APPROVED_FOR_ADVISORY`、`SUSPENDED`、`RETIRED`。模型只允许批准为建议用途，不设置“自动执行业务写入”的批准状态。漂移超阈值、输入超出训练范围、特征缺失或评测基线失败时必须降级为规则/人工处理，并显示限制。

模型建议不得直接创建生产订单、修改库存、确认出入库、调整库位或改变质量放行结论。

## 27. 主动通知、长流程与多组织扩展

### 27.1 主动通知与订阅

通知由登记事件、规则结果或登记报表触发，不由模型自由决定收件人和内容。订阅至少绑定：

- user/role、channel、notificationDefinitionId 和版本；
- organization/plant/warehouseArea scope；
- 阈值、静默期、频率上限、时区和有效期；
- 数据分类、群安全级别、退订和审计。

通知只携带安全摘要和系统跳转，不携带 executionToken、内部 ID、原始附件或敏感明细。重复事件应按 event fingerprint 合并，防止告警风暴。

### 27.2 长流程

报表导出、文件解析、跨域分析、预测和优化使用异步 Job 模型：

- 状态：`QUEUED`、`RUNNING`、`WAITING_HITL`、`SUCCEEDED`、`PARTIAL_FAILED`、`FAILED`、`CANCELLED`、`EXPIRED`；
- 关联 traceId、requestId、runId、planId、jobId、stepId、interruptId；
- 重试只针对登记为幂等且可重试的步骤，并使用相同业务指纹；
- 补偿只能调用登记的补偿动作，禁止通用回滚或反向 SQL；
- 达到重试上限、规则冲突或数据漂移时进入人工接管；
- 人工接管必须记录接管人、原因、当时版本和后续动作。

### 27.3 多组织作用域

当前仍按单组织运行，但以下对象从设计上预留 `organizationScope`、`plantScope`、`warehouseAreaScope`：

- 不可变执行上下文、session/run/plan/HITL；
- resolver 和 opaque ref；
- 工具参数、数据集、指标、规则和模型；
- SessionLock、幂等键、缓存和审计；
- 配方、通知、附件暂存和报表下载。

scope 必须来自受信身份映射和 RBAC，不能由用户文本或模型自由填写。跨 scope 查询、配方、报表、模型训练和写入默认拒绝；未来开放时需要显式授权和聚合规则。

## 28. 调整后的总体路线

1. 完成 Agent v1 日常查询覆盖，确保九个业务专家均有真实只读工具和确定性测试。
2. 建设事实表、历史快照、指标 Registry、数据质量和版本化规则层。
3. 建设 v2-A 单域/登记跨域报表、文件暂存预览及入出库/调拨 dry-run。
4. 完成 executionToken、幂等、实体/规则版本重检、审批、事务、HITL 和完整审计。
5. 仅在第 4 步验收后逐项开放 v2-B execute；L4 配置和权限能力最后开放。
6. 工作群在身份映射和数据分类完成后先开放查询、报表和通知，只提供 Web 跳转确认。
7. 历史数据、指标、规则和模型评测稳定后，再建设 planning_expert、预测、优化和仿真。
8. 多组织真正启用前，完成 scope 隔离、跨 scope 测试和审计验收。

所有阶段继续禁止任意 SQL、任意 HTTP、万能管理工具、模型自由 DAG、专家自由互聊、直接修改库存以及绕过 preview/HITL 的写入。
