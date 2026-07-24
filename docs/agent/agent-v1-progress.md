# Agent v1 实施进度台账

> 本文件是 Agent v1 开发的跨 Codex 线程同步入口。任何线程开始 Agent/MCP 相关工作前，应先阅读本文件；完成或暂停一个实施切片时，应在同一批改动中更新本文件。

## 维护规则

- 只记录已经由代码、测试或文档确认的事实；规划项不得标记为已完成。
- “当前工作项”用于减少多个线程修改同一链路的冲突。接手前同时检查 `git status` 和相关文件差异。
- 不删除历史记录。状态变化追加到“变更日志”，并同步更新“当前状态”。
- 每个实施切片必须贯通：Java 聚合接口、internal Agent Gateway 白名单、warehouse-mcp、Python 专家路由与参数、Tool Capability Registry、测试和文档。
- 所有 v1 新工具保持 L1 只读；不得引入写操作、任意 SQL、任意 HTTP、模型自由 DAG 或专家自由互聊。
- 原有 17 个只读 MCP 工具的业务行为不得改变；新增 V1 工具同样保持 L1 只读。
- 日期时间使用 Asia/Shanghai；验证结果必须写明命令或测试范围。

## 当前状态

- 最后更新：2026-07-24
- 总体阶段：Agent v1 只读查询目标闭环
- 当前实施切片：`V1-READONLY-GOAL-CONTRACT`
- 当前工作状态：ENGINEERING_UAT_PASS / ROLE_UAT_PENDING（52 个只读工具已归入 43 个目标合同；九专家代表性真实链路已通过，仍待按真实角色执行完整矩阵）
- 当前负责线程：本 Codex 只读查询收口线程（2026-07-23）
- 当前文件范围：52 工具目标归属、43 个 GoalContract、模型目标绑定、事实完成门禁和真实角色 UAT

## 已确认基线

- 已完成主 Agent、Router、专家 Agent 和首个受控多专家配方的基础架构。
- 主 Agent 不持有业务工具；专家只能访问本专家白名单工具。
- 当前仅登记一个复合配方：`warehouse_inventory_latest_assay`。
- 当前共有 52 个已实现的只读 MCP 工具；原有工具的业务行为未改变。
- Agent v1 专家规划基线：inventory、warehouse、logistics、pallet、production、quality、master_data、administration、audit。
- v2-A 分析/preview 与 v2-B execute 仅保留设计和占位，不在本阶段实现。

## 专家覆盖进度

| 专家 | v1 状态 | 当前说明 |
|---|---|---|
| `inventory_expert` | COMPLETED | 6 个 L1 工具 |
| `warehouse_expert` | COMPLETED | 5 个 L1 工具 |
| `logistics_expert` | COMPLETED | 4 个 L1 工具 |
| `pallet_expert` | COMPLETED | 9 个 L1 工具 |
| `production_expert` | COMPLETED | 7 个 L1 工具 |
| `quality_expert` | COMPLETED | 运行时 ID 为 `assay_expert`，12 个 L1 工具 |
| `master_data_expert` | COMPLETED | 4 个 L1 工具（含共享 `resolve_products`） |
| `administration_expert` | COMPLETED | 3 个 L1 工具 |
| `audit_expert` | COMPLETED | 3 个 L1 工具 |

## 当前工作项

### V1-PROD-00：通用受控业务实体 ref

- 状态：COMPLETED
- 发现：生产订单分页的 `orderNo` 是模糊匹配，返回 VO 又包含内部数据库 ID；不能直接作为 Agent 的唯一实体选择协议。
- 已实现：通用 `AgentEntityRefCodec`，提供 HMAC 防篡改、短 TTL、当前用户绑定、权限范围绑定和实体类型校验；密钥缺失时 fail-closed。
- 限制：现有 `AssayReportRefCodec` 是化验报告专用 ref，未绑定用户/权限和 TTL，不直接扩展为生产实体 ref。
- 验收：篡改、过期、跨用户复用、实体类型不匹配、权限变化和密钥缺失均有确定性单测并 fail-closed。
- 部署：新增 `AGENT_ENTITY_REF_SECRET`；首次初始化自动生成，已有简易部署目录执行 `update.sh` 时若缺失也会自动生成，无需手填。

### V1-PROD-01：生产实体解析与生产订单进度

- 状态：COMPLETED
- 目标：确定并实现 `production_expert` 的首个完整只读切片。
- 候选工具：`resolve_production_entities`、`query_production_order_progress`。
- 安全边界：不猜测生产订单或煮糖批次 ID；不开放新增、更新、删除、领用、完成或标签生成操作。
- 契约约束：模糊订单号仅返回候选 ref；只有唯一匹配或用户选择的 ref 才能查询订单进度。不得接受 Agent 手填的数据库 ID。
- 已完成：Java 只读聚合接口、受控 ref、Gateway 白名单、warehouse-mcp、Python `production_expert` 路由/参数/确定性回答、能力注册表、测试和文档。
- 工具行为：唯一候选自动查询订单进度；多候选停止并要求完整订单号；无候选与工具失败保持区分。
- 工具总数：17 增至 19；未新增配方、写工具或自由执行图。

## 验证记录

### V1-LOG-02：库存单据查询

- 状态：COMPLETED
- 目标工具：`query_stock_documents`。
- 数据来源：传统入库记录、出库记录、半成品记录；每次必须明确一种 `documentType`。
- 语义边界：不跨三个异构来源伪造全局排序或完整事件账；不返回单据、化验或人员内部 ID。
- 权限边界：Agent 专用接口重新执行 `record:query`，并沿用原服务对当前用户的数据范围约束。
- 写边界：不开放入库、出库、半成品录入、修改、删除或导出。

### V1-LOG-01：托盘任务查询

- 状态：COMPLETED
- 目标工具：`query_pallet_tasks`。
- 已确认来源：前端任务中心、`POST /api/pallet-codes/tasks/list`、`PalletTaskQueryDTO`、`PalletTaskPageVO` 和 `PalletTaskQueryMapper`。
- 安全缺口：现有页面查询接口未声明 `@PreAuthorize('task:view')`，且响应直接包含 taskId、palletCodeId、productId、warehouseId、assayId 等内部 ID；不得直接作为 MCP 响应。
- 实施方向：新增 Agent 专用只读聚合接口，重新执行 `task:view` RBAC，仅返回任务类型、场景、状态、业务编号、产品/库位/时间等安全字段，并限制枚举、日期范围和分页。
- 写边界：不开放任务创建、确认、取消、入库、出库或调拨。
- 已完成：新增 Agent 专用聚合、`task:view` RBAC、Gateway、warehouse-mcp、logistics_expert 路由/参数/确定性回答和测试；正式登记为第 25 个工具。

### V1-PROD-05：在制物料与领料候选

- 状态：COMPLETED
- 目标工具：`query_in_process_materials`、`query_material_candidates`。
- 输入边界：在制物料采用受控筛选与分页；领料候选仅接受受控 `orderRef`，不接受数据库订单 ID。
- 语义边界：候选只表示后端当前查询口径下可展示的物料，不代表已领用、系统推荐、FIFO/FEFO 判定或质量放行。
- 写边界：不开放领料、预留、退料、库存调整或订单修改。
- 已完成：两个工具均已全链路贯通；production_expert 当前 v1 规划集完成，工具总数为 24。

### V1-PROD-04：生产标签完成度

- 状态：COMPLETED
- 目标工具：`query_production_label_completion`。
- 输入边界：仅接受生产实体解析返回的受控 `orderRef`。
- 语义边界：分别报告标签预留/使用/回收，以及产出二维码需求/绑定/入库；打印时间不等同于入库完成。
- 写边界：不开放标签预留、打印、核销、回收、二维码绑定或生产完成。
- 已完成：Java 聚合、Gateway、warehouse-mcp、Python 专家路由/参数/确定性回答、注册表、测试和文档；打印、绑定、入库三个阶段保持显式区分。

### V1-PROD-03：生产领料追溯

- 状态：COMPLETED
- 目标工具：`query_material_pick_trace`。
- 输入边界：仅接受生产实体解析返回的受控 `orderRef`。
- 语义边界：只展示订单实际领料记录及其中已登记的托盘、产品、库位、数量、状态和时间；不推断计划差异、损耗或实际消耗率。
- 写边界：不开放领料、完成领料、退料或库存变更。
- 已完成：Java 聚合、Gateway、warehouse-mcp、Python 专家、注册表、测试和文档；响应不返回内部 material/pallet/product/warehouse ID。

### V1-PROD-02：煮糖批次追溯

- 状态：COMPLETED
- 目标工具：`query_boiling_batch_trace`。
- 输入边界：仅接受 `resolve_production_entities(entityType=BOILING_BATCH)` 返回的受控 `batchRef`。
- 语义边界：仅呈现现有批次详情、使用记录和代码已登记的 trace 节点；不得推断缺失的上游来源、下游订单或物料关系。
- 写边界：不开放创建、修改、取消、预留、消耗或释放煮糖批次。
- 已完成：Java 聚合、Gateway、warehouse-mcp、Python 专家路由/参数/确定性回答、能力注册表、测试和文档。
- 脱敏：原始 trace 节点 ID 被映射为响应内 `trace_node_n`，原始 `meta` 不返回；悬空边被丢弃，不补推断边。

| 日期 | 范围 | 结果 | 说明 |
|---|---|---|---|
| 2026-07-13 | 实施启动 | 待验证 | 尚未对本切片作完成性声明 |
| 2026-07-13 | 生产接口静态审计 | 通过 | 已复核订单 Controller、Service、DTO、VO、Mapper 和前端 API；确认查询权限及模糊匹配行为 |
| 2026-07-13 | `AgentEntityRefCodecTest` | 4 passed | 防篡改、TTL、用户/权限/类型绑定及密钥缺失 fail-closed |
| 2026-07-14 | Python Agent 全量测试 | 110 passed | 含生产订单唯一匹配链路、多候选停止、专家归属和受控 ref 参数测试 |
| 2026-07-14 | Java 根项目全量测试 | 通过 | `mvn -q test`；包含聚合服务、Gateway 精确白名单和 MCP capability 校验 |
| 2026-07-14 | warehouse-mcp 全量测试 | 49 passed | 工具注册清单为 19，两个生产工具 schema 边界通过 |
| 2026-07-14 | scoped `git diff --check` | 通过 | 无空白错误；仅有 Git 的 LF/CRLF 提示，文件内容仍为 UTF-8 无 BOM |
| 2026-07-14 | `V1-PROD-02` Python Agent 全量测试 | 111 passed | 含煮糖批次受控 ref、专家归属、两工具固定链路及限制说明 |
| 2026-07-14 | `V1-PROD-02` Java 根项目全量测试 | 通过 | `mvn -q test`，包含安全节点映射、悬空边过滤、Gateway 和 MCP 握手 |
| 2026-07-14 | `V1-PROD-02` warehouse-mcp 全量测试 | 通过 | 20 个工具清单和 `batchRef` 严格 schema 通过；结束时存在 Surefire fork JVM 清理告警但退出码为 0 |
| 2026-07-14 | `V1-PROD-03` Python Agent 全量测试 | 112 passed | 领料意图固定路由到 `production_expert`，只用受控 `orderRef` |
| 2026-07-14 | `V1-PROD-03` Java 根项目全量测试 | 通过 | `mvn -q test`；内部 ID 不进入领料安全 VO |
| 2026-07-14 | `V1-PROD-03` warehouse-mcp 全量测试 | 通过 | 21 个工具清单和 `orderRef` schema 通过；仍有已记录的 Surefire fork 清理告警，退出码为 0 |
| 2026-07-14 | `V1-PROD-03` scoped diff/乱码检查 | 通过 | `git diff --check` 通过，新增文件未发现乱码字符 |

## 风险与阻断项

- 工作区存在大量尚未提交的 M1.4 修改和未跟踪文件；所有线程必须在现有工作区增量修改，禁止重置、覆盖或清理无关文件。
- 生产模块页面接口可能偏 CRUD，MCP 工具必须按业务目标聚合，不能简单一对一暴露 REST API。
- 实体解析、权限和分页上限必须采用确定性规则；无法从代码确认的业务口径标记为“待确认”并安全拒绝。
- 通用 opaque ref 已实现，但每个后续详情工具仍必须在 Controller 层重新执行 RBAC，并在 Service 层验证实体类型、当前用户、权限范围和 TTL。

## 下一步

## 2026-07-14：V1-LOG-03 智能报数批次只读查询完成

- 新增 `query_auto_inbound_batches` 与 `get_auto_inbound_batch_detail`，当前正式注册只读 MCP 工具共 28 个。
- 列表仅查询当前用户 Redis 中未过期的近期批次；详情必须先以当前用户列表反查用户绑定的 HMAC 受控引用，原始 `batchId` 不进入 Agent。
- 安全结果删除原始报数文本、内部批次/任务/产品/库位 ID；普通回答仅显示编号，不展示 `batchRef`。
- 支持同一会话在列表后按“第一个/第二个/第三个”查询详情；解析结果明确不等同于入库确认、质量放行或库存事实。
- `logistics_expert` 当前 v1 规划工具集已完成，下一步按蓝图进入 `warehouse_expert` 剩余覆盖复核与实现。

下一步：

1. 启动 `warehouse_expert` 的 v1 覆盖复核，确认现有 `get_warehouse_status` 是否满足蓝图，补齐缺失的只读工具。

## 2026-07-14：V1-WH-01 库位容量分布完成

- 新增 `query_warehouse_capacity_distribution`，正式注册只读 MCP 工具增至 29 个。
- 支持全局或单库位范围、EMPTY/LOW/MEDIUM/HIGH/FULL 展示分段、仅有剩余容量过滤和安全分页。
- LOW/MEDIUM/HIGH 明确定义为固定展示分段，不作为业务风险阈值、库位分配或调拨建议；容量单位继续标记为沿用现有系统配置、业务口径待确认。
- 单库位“还有多少容量”继续走 `resolve_warehouses -> get_warehouse_status`，全局“哪些库位快满/空置”才使用容量分布工具。
- 验证：Python Agent 119 passed；Java service/Gateway/stdio 定向测试通过；warehouse-mcp 全量测试通过。

下一步：

1. 实现 `V1-WH-02 query_warehouse_recent_operations`，保持“最近发生了什么”和“当前有什么”工具边界。

## 2026-07-14：V1-WH-02 库位近期流转完成

- 新增 `query_warehouse_recent_operations`，正式注册只读 MCP 工具增至 30 个。
- 支持单库位解析后查询或全局查询，并把底层 `SEMI_INSTOCK`、`FINISH_INSTOCK`、`OUT`、`PREPARE_CONSUMED`、`ORDER_MATERIAL_PICK`、`TRANSFER` 映射为稳定的 INBOUND/OUTBOUND/TRANSFER 业务类别。
- 时间范围、类别和 limit 在 Python、MCP 和 Java 三层校验；单库位多候选仍经过现有 HITL，恢复后保持 warehouse_expert 权限。
- 明确数据范围仅为 `pallet_flow_record` 已登记事件，不宣称是完整操作日志、审计日志或无缺口历史事件账。
- 验证：Python Agent 120 passed；Java service/Gateway/stdio 定向测试通过；warehouse-mcp 全量测试通过。

下一步：

1. 实现 `V1-WH-03 query_warehouse_mixed_storage_facts`，只返回同库位多产品/规格客观事实，不判定混放风险。

## 2026-07-14：V1-WH-03 库位多产品/多规格事实完成

- 新增 `query_warehouse_mixed_storage_facts`，正式注册只读 MCP 工具增至 31 个；`warehouse_expert` 当前 v1 规划集完成。
- 聚合同一库位当前产品数、产品类型数、产品/筛网/状态组合数和受限证据标签；不向 Agent 返回库位、产品、筛网或库存内部 ID。
- `factType` 仅支持 ANY、MULTIPLE_PRODUCTS、MULTIPLE_SPECIFICATIONS；全链路限制 limit，SQL 聚合最多扫描 200 个库位分组，标签长度和回答展示数量均受限。
- 明确多产品/多规格仅是客观事实；在混放规则和阈值确认前，不得判定违规、风险或生成调拨建议。
- 验证：Python Agent 121 passed；Java service/Gateway/stdio 定向测试通过；warehouse-mcp 全量测试通过。

下一步：

1. 按蓝图进入 `quality_expert` 与 `master_data_expert` 的 v1 工具覆盖复核和实现。

## 2026-07-14：V1-MD-01 主数据专家完成

- 新增独立 `master_data_expert`，其白名单仅包含 `query_product_catalog`、`get_product_detail`、`query_screen_mesh_catalog`；正式注册只读 MCP 工具增至 34 个。
- 产品目录支持名称、品类、半/成品状态、包装、筛网和安全分页；产品详情只接受准确唯一产品名称，模糊或重复匹配安全拒绝。
- 产品与筛网结果删除内部 ID、创建人和更新人；配置换算只作复述，不推导库存数量、质量合格、装载建议或生产可用性。
- Agent 专用接口分别校验 `product:view` 和 `screen_mesh:view`，所有工具保持 L1 只读。
- 验证：Python Agent 123 passed；Java service/Gateway/stdio 定向测试通过；warehouse-mcp 全量测试通过。

下一步：

1. 实现 quality_expert 的 `query_assay_groups`、`query_quality_standard_catalog`、`get_quality_standard_detail`、`query_product_standard_relations`。

## 2026-07-14：V1-QUAL-01 质量配置查询完成

- 新增 `query_assay_groups`、`query_quality_standard_catalog`、`get_quality_standard_detail`、`query_product_standard_relations`，正式注册只读 MCP 工具增至 38 个；quality_expert（运行时兼容名 assay_expert）当前 v1 规划集完成。
- 化验组结果删除组、产品和维护人内部 ID，并明确产品分组不是质量标准、合格结论或库存批次范围。
- 质量标准详情必须同时提供准确标准代码与版本；目录和详情均不证明某次报告采用该标准，最终判定仍由确定性匹配与判定服务负责。
- 产品标准关系按准确唯一产品名称查询，返回默认项、优先级、启用和生效区间，但不证明报告采用或产品合格。
- Agent 专用接口分别校验 `assay:view` 和 `quality_standard:view`，不开放任何新增、更新、删除或绑定写操作。
- 验证：Python Agent 125 passed；Java quality service/Gateway/stdio 定向测试通过；warehouse-mcp 全量测试通过。

下一步：

1. 进入 `audit_expert`，实现受控操作日志查询。
2. 继续核对 inventory_expert 与 pallet_expert 蓝图剩余项。
3. 保持所有写能力关闭。

## 2026-07-14：V1-ADMIN-01 员工与权限查询完成

- 新增 `query_employee_roster`、`query_roles`、`get_role_permission_summary`，正式注册只读 MCP 工具增至 41 个；administration_expert 当前 v1 规划集完成。
- 员工查询仅返回工号、姓名、部门、职位、状态、角色编码和脱敏手机号；不返回内部记录 ID、完整手机号、登录凭据、微信绑定信息或鉴权令牌。
- 角色目录不返回角色/权限内部 ID 和员工姓名清单；权限摘要必须按角色编码或名称精确唯一匹配，并明确实际访问仍需登录身份和 Java Gateway RBAC 重新鉴权。
- 新增独立 `administration_expert`，白名单仅含上述 3 个 L1 工具；Java 接口分别校验 `rbac:user:view` 与 `rbac:role:view`，不开放员工、角色或权限写操作。
- 验证：Python Agent 122 passed；Java administration service/Gateway/stdio 定向测试通过；warehouse-mcp 全量测试通过。

下一步：

1. 继续核对 inventory_expert 与 pallet_expert 蓝图剩余项，完成后进入 v1 总体验收。

## 2026-07-14：V1-AUDIT-01 审计安全摘要查询完成

- 新增 `search_operation_logs`、`query_agent_tool_audit`、`query_agent_answer_reviews`，正式注册只读 MCP 工具增至 44 个；audit_expert 当前 v1 规划集完成。
- 业务操作日志仅输出模块、操作类型、操作人、时间和变更字段名称，不输出 oldData、字段值、请求正文或原始异常。
- Agent 工具审计不输出会话、用户、消息、调用内部 ID、上游路径、参数、请求响应摘要、Prompt、模型上下文、密钥或堆栈。
- 回答 Review 只输出质量状态与失败分类，不输出用户原问题、助手原回答、工具名、决策快照、证据正文或审核人身份。
- 新增 `agent:audit:view` 权限迁移并仅默认授予 ADMIN；三项接口分别执行 `log:view`、`agent:audit:view`、`agent:review:view`。
- 验证：Python Agent 125 passed；Java audit/administration/Gateway/stdio 定向测试通过；warehouse-mcp 全量测试通过。

下一步：

1. 实现 pallet_expert 最后一项 `query_fixed_product_qr_pool`。
2. 补齐后执行 Java、Python、warehouse-mcp、前端构建与本地集成/E2E 总体验收。

## 2026-07-14：V1-INV-01 库存台账与备料余额完成

- 新增 `query_inventory_ledger`、`query_prepare_pool_balance`，正式注册只读 MCP 工具增至 46 个；inventory_expert 当前 v1 规划集完成。
- 库存台账直接查询当前 inventory 行及业务名称、位置、板件数、入库日期和托盘已登记生产日期，明确不是完整历史流水且不证明批次合格。
- 备料池工具仅支持 `positiveOnly=true`，只返回 remainingPieces > 0 的现存余额；零余额完整历史仍不支持，且余额不代表订单预留、质量合格或可直接领用。
- 两项 Agent 聚合接口重新执行 `record:query`，结果不暴露产品、库位、筛网、库存、化验或备料池内部 ID，不执行占用、领用或库存调整。
- 验证：Python Agent 127 passed；Java inventory/Gateway/stdio 定向测试通过；warehouse-mcp 注册测试待完成最终重跑。

下一步：

1. 执行 Agent v1 全量构建、集成/E2E、文档一致性和安全边界验收。

## 2026-07-14：V1-PALLET-02 固定产品二维码池完成

- 新增 `query_fixed_product_qr_pool`，正式注册只读 MCP 工具增至 47 个；所有专家的 `v1_planned` 已清零。
- 工具支持按产品名称、最多 20 个二维码、状态、freeOnly 和分页查询，只返回业务码、产品名、状态、固定模式、可打印事实和更新时间，不返回二维码或产品内部 ID。
- `allowPrint` 仅表示当前 FREE 状态满足码池可打印条件，不表示标签已打印、码已启用或已创建入库任务。
- Agent 聚合接口重新执行 `qrcode:pool_view`，不开放绑定、PDF 打印、启用、作废、恢复、托盘确认或库存写入。
- 验证：Python Agent 128 passed；Java fixed-pool/Gateway/stdio 定向测试通过；warehouse-mcp 全量测试通过。

下一步：

1. 检查 roadmap 中所有专家 `v1_planned` 均为空，核对 47 个工具在 Python、Gateway、MCP 和 capability registry 的集合一致性。
2. 全量运行 Python、Java、warehouse-mcp、前端构建与 m13r E2E，本地真实环境只作 smoke test。

## 2026-07-14：Agent V1 全量验收完成

- 9 个业务专家均具有真实 L1 只读能力；`main_agent` 仍无业务工具。Roadmap 中所有专家的 `v1_planned` 均为空，当前只保留 1 个登记式复合配方，不支持模型自由 DAG 或专家自由互聊。
- 47 个工具已贯通 Java 聚合接口、internal Agent Gateway、warehouse-mcp、Python 专家白名单与参数路由、Tool Capability Registry、测试和文档；运行时能力接口返回 `toolCount=47`、`recipeCount=1`。
- 修复 Java 实际 JAR 启动时 `AgentEntityRefCodec` 多构造器未明确注入导致的启动失败，并增加构造器注入回归测试。
- 修复 Java/Python 启动握手仍固定为旧 17 工具哈希与数量的问题；当前严格校验 47 工具注册哈希，不一致继续 fail-closed，不回退旧 Agent。
- 修复页面刷新后旧 HITL 可能暂时保持 `PENDING` 且 UI 已展示为可用的问题：助手抽屉只在替换会话完成、旧会话中断已撤销后显示。
- 全量验证：Python Agent `133 passed`；Java 根项目 `188` tests、0 failure/error；warehouse-mcp `49` tests；前端生产构建通过；本地 m13r HITL E2E `7 passed`。
- 本地真实数据库只用于 smoke/E2E，不作为 CI 固定业务数据断言；V1 仍无写工具、preview/execute 或任意 SQL/HTTP 能力。

下一阶段：

1. Agent V1 功能实现冻结，仅处理验收发现的缺陷和部署前兼容性问题。
2. 按既定路线先建设事实、快照、指标和版本化规则层，再进入 V2-A 登记报表与 preview；满足 executionToken、幂等、版本重检、审批和审计后才进入 V2-B。

## 2026-07-16：V1.1 Experimental LLM Tool Loop 进入本地 UAT 准备

- M2 库存语义查询与动态 SQL 路线继续 `NO_GO`，暂停扩大 GoalContract、FactEnvelope 和 FollowupAction 平台。
- 新增 `AGENT_PLANNING_MODE=deterministic|llm`；默认仍为 deterministic，production 环境禁止启动 llm 模式。
- llm 实验仅开放 inventory、warehouse、assay 三个专家；每轮绑定一个专家，最多 3 次现有 L1 工具调用和 1 次 retryable 重试，跨专家仍只允许已有登记配方。
- 主模型只决定直接回答、澄清、单专家、登记配方或不支持；专家模型按一次一个动作执行“提议—校验—观察—再提议”。Runtime 继续掌握工具白名单、参数 schema、受控状态引用、HITL、权限、预算和完成门禁。
- 模型可见 schema 不含 `productId` / `warehouseId`，只使用 `CURRENT_PRODUCT` / `CURRENT_WAREHOUSE`；Runtime 注入现有工具参数。展示标签重新进入 resolver、模型 raw ID、跨专家工具和未登记配方均 fail-closed。
- 工具观察严格区分 AVAILABLE、NO_DATA、TOOL_ERROR、PERMISSION_DENIED 和 PLAN_REJECTED；模型只引用错误 observation 时不得完成，未解决错误时不得返回完整完成。
- 新增本地实验测试及 OpenAI-compatible/DeepSeek-compatible 结构化动作测试；首轮真实 DeepSeek 浏览器 UAT 已执行并发现下述超时与结构化动作诊断问题。
- 关键开放风险是模型往返延迟：按 Shadow 单次约 5～10 秒估算，含 resolver 的完整任务可能达到 20～40 秒。只有真实 UAT 同时证明完成率提升、安全用例 100% 和延迟可接受，才考虑扩大实验；生产仍为 `NO_GO`。

## 2026-07-16：5174 首轮 LLM Tool Loop UAT 缺陷修复

- 首轮真实浏览器测试确认 resolver 和 MCP 调用可成功，但暴露“专家动作无法校验”和“整轮处理超时”；根因不是 47 个工具或业务权限，而是结构化动作错误被吞没、单工具超时被误用为整轮超时，以及完成后重复调用模型流式改写。
- 新增独立 `AGENT_RUN_TIMEOUT_MS`：LLM 默认 90 秒、确定性默认 20 秒；`REQUEST_TIMEOUT_MS` 保持为单次 Java Gateway 工具超时，未放宽模型、工具或数据权限。
- LLM 最终答案改为直接输出已校验的专家答案，不再增加一次模型调用；结构化 JSON/Schema 失败只允许一次格式修复，仍失败则 fail-closed。
- 新增不含模型原文和业务参数的安全诊断；401/服务密钥问题改为链路认证失败，不再误报为业务权限拒绝。
- 回归验证：Python Agent `186 passed`；Java 根项目 `203` tests；warehouse-mcp `50` tests；前端请求错误单测 `4` tests；前端生产构建通过。5174 修复后真实浏览器复测仍待完成，生产继续 `NO_GO`。

## 2026-07-16：5174 第二轮 LLM Tool Loop UAT 缺陷修复

- 第二轮真实浏览器复测确认首轮的结构化动作和超时问题已消失；新增发现为库存分布 `canonicalProductName` 在实际 MCP 可执行包中缺失，以及两个单域短追问被主模型误判为跨域登记配方。
- `canonicalProductName` 问题定位为源码与 `warehouse-mcp-0.1.0-exec.jar` 构建时间不一致。已重建并通过字节码接口核验；仍禁止从带规格的 `displayLabel` 反解析产品名。
- 跨域配方仍由确定性匹配器和 Runtime 守卫。误提配方时仅允许一次主模型安全重路由，第二次不暴露配方；重复误提继续 fail-closed。未新增自由跨域、工具或权限。
- 新增“只看黄冰糖”“它最新化验怎么样”和真实库存到化验配方回归。最终验证：Python Agent `189 passed`；Java根项目 `203` tests；warehouse-mcp `50` tests；前端请求错误单测 `4` tests；前端生产构建通过。新运行包上的浏览器复测仍待完成，生产继续 `NO_GO`。

## 2026-07-17：5173 第三轮 LLM Tool Loop UAT 缺陷修复

- 第三轮真实浏览器复测确认三个问题：单域短追问“只看黄冰糖”可能被主模型的实时事实守卫直接拒绝；典型任务仍包含 2～3 次串行模型请求；HITL 选择成功后候选卡片仍永久占据会话空间。
- 实时事实守卫不再把主模型生成的业务答案直接展示给用户。主模型越权直接回答时，Runtime 会丢弃该答案并恢复为受控专家委派；无法确定安全专家时仍保持 fail-closed，不允许主模型编造实时库存事实。
- 对已有受控实体上下文、未跨专家边界的短追问，Runtime 可复用当前专家，省去一次主模型路由；专家模型仍负责语义理解、工具选择和结果分析。这是有上下文边界的延迟优化，不是恢复关键词 Router。
- 新增主模型/专家模型分段耗时和整轮耗时指标。当前观测中工具调用均为毫秒级，10～20 秒主要来自串行模型请求；首次问题或跨域问题仍可能需要 2～3 次模型调用，因此在真实浏览器复测前不能宣称延迟问题已完全解决。
- 用户选择产品或库位后立即收起全部候选卡片，改为单行展示“用户已选择 xx（产品/库位）”；恢复请求失败时重新显示候选，避免错误地锁死交互。
- 回归验证：Python Agent `198 passed`；前端生产构建通过。真实浏览器延迟、短追问连续性和卡片收起行为仍待新进程复测，生产继续 `NO_GO`。

## 2026-07-17：5173 第四轮 LLM Tool Loop UAT 延迟与超时修复

- 真实 UAT 聚合指标确认：主模型 5 次有效路由平均约 3.4 秒；专家 21 次有效动作平均约 5.7 秒；发生 1 次 30.2 秒专家超时和 1 次 18.1 秒结构校验失败。库存概览、分布工具本身通常低于 0.25 秒；产品解析首调用受 MCP 会话冷启动影响，3 次平均约 2.8 秒。
- 对库存概览、库存分布、库位状态和单次化验状态启用“模型选工具、Runtime 校验并调用、安全事实格式化器完成”的快速完成路径；简单事实查询不再为了重述已校验结果额外调用一次专家模型，组合查询仍保留专家后续分析。
- 已有同域受控上下文且确定性边界检查确认专家不变时，“查询/查/帮我查/看看”等明确追问复用当前专家，仍由专家模型选择工具；与库存总览快速完成叠加时可减少主路由和结果重述两次模型往返。
- 专家模型发生超时或连续结构化动作失败时，仅允许对 resolver、库存概览/分布、库位状态和单次化验状态使用既有确定性计划作异常恢复；恢复仍重新执行专家白名单、参数、实体引用、权限和审计边界，不能扩大查询范围，也不成为正常主路径。
- 库存总览有库存时不再输出“无具体位置摘要”，统一追加“需要我帮你查库存分布吗？”；专家提示同时明确“库存总览未返回位置”不等于“库存没有位置”。主模型与专家上下文窗口由最近 12 条收口到 8 条，结构化实体状态继续保留。
- 不向普通用户展示模型 chain-of-thought、内部推理或原始 MCP 帧；后续如继续增强等待体验，只展示可审计的业务阶段进度和脱敏工具状态。回归验证：Python Agent `200 passed`；新运行参数将模型单次超时从 30 秒收口到 20 秒，真实浏览器耗时仍待复测，生产继续 `NO_GO`。

## 2026-07-17：Agent 会话级 MCP 预热

- 创建 Agent 会话成功后异步预热该用户、该会话独占的 warehouse-mcp STDIO 进程；会话创建接口不等待预热完成，预热失败或队列满只回退到首次工具调用时按需启动，不把会话创建误报为失败。
- 预热与用户立即发起的首个工具调用通过同一 `agentSessionId` 原子绑定，共享一次 MCP 初始化和 47 工具注册核验，不重复启动进程，也不跨用户或跨会话复用委托身份。
- MCP 启动完成后再次校验会话仍属于当前用户且保持 ACTIVE；若预热期间会话被撤销或过期，立即关闭刚启动的进程。会话主动撤销、自然过期和 Java 进程关闭继续清理对应 MCP 子进程。
- 新增有界预热线程池和 `AGENT_MCP_WARMUP_*` 配置。日志仅记录 SUCCESS、FAILED、QUEUE_FULL、异常类型和耗时，不记录委托 token、工具参数或业务结果。
- 本优化只减少 MCP 首次解析器冷启动，不能消除主模型和专家模型的推理耗时；后续仍按“可信业务进度事件 -> 最终答案增量流式输出”的顺序推进。

## 2026-07-17：可信业务进度与已校验答案增量输出

- Python SSE 不再等整轮 Runtime 完成后才一次性返回中间状态。流式请求在独立运行线程中执行，Runtime 通过受控回调实时发送真实阶段；客户端断开时取消令牌继续阻止后续回答写入。
- 业务进度覆盖主路由、专家规划、产品/库位消歧，以及库存、库位、化验、托盘、生产、物流、主数据、员工权限和审计查询。47 个工具必须全部登记业务阶段；事件只含固定 `stage + text`，不含模型思维链、工具名、业务参数、内部 ID 或原始 MCP 帧。
- LLM 结构化动作和最终答案仍先完整通过 schema、事实与完成门禁，禁止直接展示未校验 token。校验完成后，最终公开答案按标点和固定上限拆成连续 `text_delta`，保持拼接后文本逐字一致，也不增加一次模型调用。
- 这属于“已校验答案的传输级增量输出”，不是模型原生 token 首字延迟优化；模型路由和专家决策耗时仍需通过模型选择、提示压缩和真实 UAT 指标继续治理。
- 将库存试点中的快速完成、模型失败安全恢复和后续建议从 Runtime 条件分支抽取为 `FastCompletionPolicy`、`SafeFallbackPolicy`、`NextActionPolicy`。新增领域规则必须通过独立策略和单测进入，不能直接扩大所有工具的快捷路径或异常回退范围。

## 2026-07-17：化验与托盘代表性多轮试点

- 化验试点覆盖“产品 + 生产日期化验 → 最近 30 天历史记录 → 展开第一条具体指标”。真实化验报告 `recordRef/reportRef` 只保存在 Runtime 受控状态，模型上下文和工具观察仅出现 `CURRENT_ASSAY_REPORT`，最终执行前再由 Runtime 绑定真实引用。
- 托盘试点覆盖“用户明确托盘码查询现状 → 再看它的历史流转”。首轮工具成功后写入 `CURRENT_PALLET`，后续模型只能沿用受控引用；模型生成的、既不在本轮用户消息中也未绑定当前状态的托盘码会被拒绝。
- `selected_pallet` 已进入可持久化会话状态。MCP 进程重启或 Agent 状态恢复后仍可保持同一托盘身份，但会话、用户和权限边界不因此扩大。
- 托盘专家仍不在默认 LLM 试点范围，仅允许测试或显式配置启用；化验和托盘也暂不新增 GoalContract。当前结论是“代表性多轮机制可复用”，不是“其余 47 个工具已自动完成产品验收”。
- 最终回归：Python Agent `213 passed`；Java 根项目 `209` tests；warehouse-mcp `50` tests；前端现有 Node 测试 `14 passed`；前端生产构建通过。前端尚未配置统一 `test:unit` npm 脚本，当前用真实测试文件入口执行，不将缺少脚本误报为用例失败。

## 2026-07-17：化验查询业务化展示与快速完成

- 真实浏览器 UAT 发现 `get_assay_status` 虽已返回实测值和标准快照，Runtime 却只复述 `judgeResult`，导致 `NO_STANDARD` 等内部枚举直接暴露，且用户还需第三轮追问具体指标。该问题定性为工具结果适配和完成策略缺陷，不只是文案问题。
- 单产品、单生产日期化验查询现在直接适配为安全化验报告：展示产品、生产日期、业务判定、采用标准，以及色值、还原糖分、干燥失重、电导灰分、蔗糖分、不溶于水杂质和 pH 的实测值、对应标准与逐项结果。前端报告卡可展开和收起。
- `NO_STANDARD` 对用户统一表达为“暂无法判定”；卡片明确“未配置适用标准”，各指标标记“未判定”，并说明这不等同于不合格。`MULTIPLE_CANDIDATES` 表达为“待人工确认”。Runtime 从模型观察中移除原始判定枚举，并在最终安全输出增加兜底翻译。
- 历史化验查询使用独立可折叠历史卡，展示日期、产品、业务判定、采用标准、异常指标和化验员；不会默认把 180 天所有报告的七项指标全部展开，也不会产生逐记录 N+1 查询。用户仍可通过 `CURRENT_ASSAY_REPORT` 受控引用展开某条完整报告。
- `get_assay_status`、普通 `query_assay_records` 和 `get_assay_report_detail` 在不涉及趋势、分析或对比时使用已校验事实快速完成，省去结果复述模型调用；趋势和分析问题仍保留专家模型路径。
- 回归验证：Python Agent `215 passed`；前端 Node 测试 `16 passed`；前端生产构建通过。Java 与 warehouse-mcp 本批未改动，沿用上一批 `209` / `50` 项通过结果。真实浏览器视觉和真实数据复测仍待执行，生产继续 `NO_GO`。

## 2026-07-17：北京时间与相对日期可信解析

- 浏览器 UAT 发现模型把“今天”错误转换为历史日期 `2025-04-09`。根因不是化验 MCP 返回错误，而是主/专家模型上下文缺少受信当前日期，且 Runtime 只校验模型日期格式、没有校验相对日期语义。
- Agent 每轮向主模型和专家模型注入服务端生成的 `BUSINESS_TIME`，明确时区为 `Asia/Shanghai`、UTC 偏移为 `+08:00`，并提供今天、昨天、本周、上周、本月和上月的确定日期边界。模型记忆中的日期不得覆盖该上下文。
- Runtime 在任何 LLM 工具调用前重新解析用户原话；“今天、昨天、前天、本周、上周、本月、上月、本季度、上季度、今年、去年、最近 N 天”等表达由确定性 `BusinessClock` 转换。模型生成的冲突日期会被覆盖；范围请求误选单日工具会被拒绝并要求重新规划。
- 相对时间不是可选业务能力，因此不依赖模型主动调用新的时间 MCP 工具。`最近 N 天` 保持现有 `LAST_DAYS` 协议，由 MCP 后端按调用时日期落地；需要日历边界的表达转换为明确 `EXACT` 或 `RANGE` 参数。
- 新增北京时间跨 UTC 日期边界、中文相对日期、错误日期覆盖、单日工具范围拒绝、单日化验报告工具选择和日志时间窗口测试。回归验证：Python Agent `233 passed`。

## 2026-07-23：生产与托盘核心旅程真实浏览器验收完成

- 生产专家已统一到“模型理解 → 专家决策 → 工具调用 → 结果分析”执行范式；煮糖批次、多个关联生产订单、实际领料、产出及真实入库去向使用受控实体上下文和业务卡片，后端枚举不进入普通回答。
- 托盘旅程完成“现状 → 最近流转 → 完整历史”验收。直接历史查询会登记 `CURRENT_PALLET`，`PALLET_CURRENT_STATUS` 与 `PALLET_FLOW_HISTORY` 分别通过 GoalContract、FactEnvelope 和 CompletionEvaluator 校验。
- 完整历史工具选择增加意图—工具一致性门禁：专家误选生命周期摘要时由 Runtime 拒绝并要求专家重新决策，不恢复关键词 Router 直接调用。完整历史参数规范化为受控全量日期范围，普通卡片只显示“完整历史”。
- 托盘历史安全观察和最终展示把 `cycle` / `cycleNo` 转换为“第 N 次流转”；非法格式/校验位与格式合法但不存在分别映射为不可重试的输入错误和权威无数据，同一签名不循环调用。
- 2026-07-23 真实浏览器最终用例返回托盘 `BT000YGI` 共 14 条、4 次完整流转，展示 6 个受控业务处理阶段和完整历史卡片；未暴露模型思维链、工具名、内部日期下界或后端枚举。
- 本轮新增/更新的定向验证：Python `test_llm_tool_loop.py` 45 passed；Java `PalletCodeServiceImplTest` 通过。完整全量测试将在下一阶段改动完成后统一重跑。

## 2026-07-23：前十只读任务 GoalContract 统一与扩展

- 当前 Runtime 目标合同从 9 个扩展为 17 个，覆盖库存总览/分布、库位库存、待处理任务、煮糖批次列表与追溯、订单进度、实际领料、实际产出去向、托盘现状与历史、单份化验报告、历史化验、不合格库存、指定标准筛选、原始指标筛选和库存缺化验。
- `CoreGoalTypeV1` / `CoreFactTypeV1` 成为 Runtime、GoalDraft 和 ResultReasoning 的共同类型来源；模型提示和 JSON Schema 不再单独维护早期库存目标列表。
- 主 Agent 的结构化委派新增可选 `goalType`。命中登记目标时，模型先声明用户目标，Runtime 再校验目标唯一负责专家；专家越界时拒绝，不依靠工具调用后反推纠正。尚未登记的只读目标在过渡期仍可由 Runtime 根据实际主查询工具受控归类。
- 质量查询增加动态实体范围绑定：全部产品/全部库位不要求伪造实体；单产品/单库位范围必须匹配 `EntityContextV1`。FactEnvelope 同时记录日期、标准和指标筛选范围。
- `PRODUCTION_ORDER_PROGRESS` 与 `PRODUCTION_OUTPUT_DESTINATIONS` 即使复用同一只读聚合工具，也作为两个用户目标分别判定；后者只接受已确认实际入库去向语义。
- 定向回归已覆盖共同类型来源、动态范围、防静默扩大、批次列表权威空结果、质量空结果和生产产出去向目标绑定。Python Agent 全量回归 `277 passed`。

当时下一步：登记其余只读目标；该项已由下一节“51 个只读工具目标契约覆盖收口”完成。

## 2026-07-23：51 个只读工具目标契约覆盖收口

- Runtime GoalContract 从 17 个扩展为 42 个，机器注册表 `current_goals` 覆盖全部 51 个 L1 工具，`planned_goals` 归零。合同按用户业务结果划分，不按工具一对一复制。
- `GoalContractV1` 新增声明式 `factValidation` 和 `evidenceTools`：所需字段、列表字段、权威空结果和少数合法多形态结果由合同描述；Resolver、目录发现等支撑工具只推进实体/筛选上下文，不能生成完成证据。
- P1 覆盖库存台账/备料池、库位状态/容量/操作/混放、化验异常/标准覆盖、托盘异常/未入库码/批次完成度、生产标签/在制品、库存单据和自动报数批次。P2 覆盖化验基础资料、固定产品二维码池、生产领料候选、产品/筛网主数据、员工/角色权限和三类审计治理目标。
- 纠正原计划中 `MATERIAL_CANDIDATES` 的支撑实体：该目标依赖受控生产订单解析 `resolve_production_entities`，不是产品解析。
- 同一工具可在不同目标承担不同角色。例如 `query_quality_standard_catalog` 在指定标准库存筛选中只是支撑工具，在化验基础资料目标中才是完成证据；Runtime 优先遵守当前 GoalContract，不允许支撑调用切换活动目标。
- 主模型目标类型、专家归属、模型 Schema、Runtime 合同和机器注册表保持共同来源/一致性测试；LLM 集成测试验证“主模型绑定库位容量目标 → 库位专家选工具 → Runtime 生成事实 → CompletionEvaluator COMPLETE”。
- Python Agent 全量回归 `281 passed`；当前代码层只读目标契约覆盖完成，下一门禁是按仓管、质检、生产主管及后台治理角色执行真实浏览器验收矩阵。未增加写操作、任意 SQL、任意 HTTP、模型自由执行图或新复合配方。

下一步：冻结本批合同结构，按 `readonly-role-uat-matrix.md` 执行真实角色 UAT 并验证目标完成率、事实一致性、轮次和延迟；修复验收缺陷后再宣布“只读查询”产品目标完成。报表分析和 L3 写入仍不进入本阶段。

## 2026-07-24：25 项只读目标真实页面补充验收

- 使用账号陈思聪在 `http://127.0.0.1:5173/` 完成 25 项输入、候选选择、多轮追问、权威空结果和权限拒绝验收，逐项证据见 `readonly-query-uat-25-case-record-2026-07-24.md`。
- 当时结果为 23 项功能通过、Q09 部分通过、G03 权限边界通过；该阶段不得表述为 25/25 功能全部完成。
- 修复活动 GoalContract 下全局产品/仓库范围缺省、多轮相对日期补充、模型空结果漏引证据、目标归属专家纠正、仓管员/角色权限和指定库位操作/全局审计的语义混淆。
- 仓储 MCP 将北京时间 RFC3339 日期时间转换为 Java 后端需要的北京时间本地日期时间，解决 `WAREHOUSE_RECENT_OPERATIONS` 的 `LocalDateTime` 反序列化失败。
- Python Agent 全量测试 291 项通过；仓储 MCP `WarehouseToolsTest` 通过并重新构建可执行 MCP 包。本轮未执行入库、出库、调拨或其他 Agent 写操作。
- 当时识别出的阻塞项是 Q09 产品—化验组事实缺口，以及 G03 授权审计角色的正向验收；Q09 已由后续“产品质量配置聚合闭环”关闭，稳定性重放仍需按当前 43 个目标执行。

## 2026-07-24：42 个只读目标工程验收与真实链路复验

- 自动化合同门禁保持覆盖全部 42 个 GoalContract 和 51 个 L1 工具。Python Agent 全量回归 `282 passed`；warehouse-mcp 全量测试通过并重新构建运行包。
- 真实链路按“登录 → 创建 Agent 会话并预热 MCP → 主模型理解 → 专家决策 → MCP → Java 只读聚合接口 → FactEnvelope / CompletionEvaluator → SSE 增量回答”执行，代表性覆盖库存、库位、化验、物流任务、生产、托盘、主数据、后台管理和审计九个专家域。
- 库位可用容量在用户未指定仓库时按当前用户可见的全部仓库查询，不再产生无必要的范围追问；本次真实数据为 87 个库位，专家为完成汇总受控调用两页库容数据，两次工具调用均成功。
- 专家回答的 `observationId` 只保留在结构化 `citedObservationIds` 中供 Runtime 校验；面向用户的答案禁止出现 `obs_*`、`observationId` 或“数据来源”等内部证据标识，Runtime 增加最终展示边界兜底。
- 审计工具的 JSON Schema 继续接受 RFC3339 `date-time`；MCP 在调用只接受 `LocalDateTime` 的 Java 接口前统一换算为北京时间本地时间。真实复验“今天的 Agent 工具调用审计”只产生一次 `UPSTREAM_PERMISSION_DENIED`，不再先出现带时区时间导致的服务器错误，且没有放宽当前用户权限。
- 首轮库容真实复验为穷举 87 个库位自动翻了两页，整轮耗时约 29.4 秒。专家约束收口为：分页结果已有权威 `total`/`summary` 时，除非用户明确要求全部明细，不为逐条穷举继续翻页；使用汇总和当前页代表性明细完成回答。复验降为 1 次工具调用，工具耗时 154 ms，整轮约 13.2 秒，并明确当前页与完整列表边界。
- Chrome 插件不可用时改用 Codex 内置浏览器完成同一真实页面交互验收。已验证库位容量查询、产品候选选择后折叠、库存总览续问、化验报告指标/标准/判定及展开收起、普通账号审计权限拒绝、待处理任务按类型折叠/全选和跳转既有批量处理弹窗。批量处理只打开弹窗后取消，未提交任何写操作。
- 当前结论仅为工程验收、九专家代表性真实链路及上述代表性交互通过，不等于 42 个目标在仓管、质检、生产主管、后台治理等全部真实角色下均已完成产品验收；因此“只读查询”产品目标仍不能标记为最终完成。

## 2026-07-24：Q09 产品质量配置聚合闭环

- 新增 `PRODUCT_QUALITY_CONFIGURATION` GoalContract 和 `query_product_quality_configuration` L1 工具，当前基线更新为 43 个 GoalContract、52 个只读工具。
- 主 Agent 将“某产品适用的化验标准和化验组”委派给 assay_expert；具体产品必须先经 `resolve_products` 消歧，Runtime 再把受控产品引用注入聚合工具。结果同时提供适用质量标准和所属批量化验组，不允许模型按名称推断关系。
- 后端聚合读取产品—质量标准关系及现有 `assay_group.related_products` 产品 ID 绑定；响应不返回产品、标准或化验组内部 ID。接口保留 `quality_standard:view` 与 `assay:view` 双权限。
- 首次真实复验发现新接口漏登记在 Agent 委托令牌的只读 POST 路径白名单，因此在 Controller 前被 403 拒绝。已补齐白名单和权限矩阵回归测试，没有放宽角色权限或委托 scope。
- 浏览器复验输入“查询黄冰糖适用的化验标准和化验组”，约 8.5 秒返回产品候选；选择“黄冰糖（袋）25.0kg/件 40件/板”后约 10.3 秒完成。结果为适用 `GB` 版本 1（标准名称“黄冰糖”、默认且启用），当前未配置批量化验组；工具审计 `SUCCESS`、上游 API HTTP 200。
- 补充复验“系统当前有哪些化验组？”仍由 `ASSAY_REFERENCE_DATA` 处理并返回 5 个目录项，证明新合同没有覆盖或破坏通用质量参考数据查询。
- 最终回归：Python Agent `294 passed`；Java 根项目与 warehouse-mcp 全量测试通过。Q09 已关闭；只读查询产品目标仍待 G03 授权审计角色正向验收和按角色的稳定性重放。

## 变更日志

- 2026-07-14：修复真实浏览器验收 M01/M02/M06 与 S01/S02/S03。补齐“查询所有成品产品”、中文角色名、产品详情复合展示名和自然流转表达；产品详情先经共享 `resolve_products` 建立受控产品上下文，`master_data_expert` 因此增加该共享解析工具但唯一工具并集仍为 47；“有没有化验”不再误判为缺化验。库位容量和近期流转优先沿用 `selected_warehouse`，不再退化为全局查询。新增 `selected_production_order` 持久化状态，生产订单多候选改用统一 HITL 候选卡片与受控 `orderRef` 恢复，后续“这个订单领过哪些物料”可继续原订单；生产状态转换为中文。Python 全量测试 `151 passed`，Java Gateway/握手定向测试通过，新的 `agentProfileRegistryHash` 为 `0f7da43814511eff9eeb2ba6bea4c250d47bd071a5a28859f2fe77cdc7637c87`。
- 2026-07-14：修复真实浏览器验收 I06、Q02、Q04、Q06、P02、P03。产品名称提取会去除“的/相关”等结构尾词，Q02“黄冰糖最近30天的化验记录”可进入产品多候选 HITL；全局“哪些化验记录没有标准”按 `NO_STANDARD` 化验异常查询处理，不再错误要求产品；“哪些二维码打印了但还没有入库”识别为只读查询而非入库写操作；通用托盘流转查询不再误提取产品。Java 修复缺化验产品 SQL 的 `warehouseNamesFROM` 拼接错误，以及化验异常 MyBatis XML 动态 SQL 中未转义的 `<=`，后者已用本地真实数据库验证返回 200。Python 全量测试 `146 passed`，相关 Java 定向测试通过。

- 2026-07-14：根据真实浏览器业务验收修正产品目录默认范围。“查询成品产品目录”等通用目录表达不再自动下发 `productStatus=成品`，默认返回包含成品和半成品的完整产品主数据目录；仅在用户明确说“只看/仅查询/筛选/状态为成品或半成品”时才应用状态过滤。新增默认全量与显式状态筛选回归测试。
- 2026-07-14：完成基础 Canary 人工浏览器首轮缺陷修复。`query_product_catalog` 不再把“成品产品目录”等范围短语误传为具体产品名；库位“快满”无匹配时明确说明固定 80% 展示口径，不再用筛选后零值伪装全局容量；托盘任务、固定二维码池、在制物料、质量标准和操作日志的受控枚举统一转换为中文业务语义；空在制记录与空产品目录明确区分 NO_DATA 和查询失败。保持确定性 safe formatter 为最终安全边界，未把原始工具结果交给模型。Python 全量测试 `140 passed`。
- 2026-07-14：完成 Agent v1 生产化加固收口。Java Gateway 新增专家到工具最终授权并拒绝主 Agent、未知专家和跨专家调用；Java/Python 新增覆盖完整专家权限映射的 `agentProfileRegistryHash` 启动握手；Agent 只读聚合接口收口到细粒度 RBAC，并统一增加只读事务防御层；导出 OpenAPI 并验证 55 条 Gateway 上游路径。最终回归：Python 139 passed、Java 全量通过、warehouse-mcp 50 passed、固定数据契约通过、前端生产构建通过、真实浏览器 E2E 16 passed（含 9 专家工具审计 canary）。结论更新为“隔离验收环境 GO，甲方正式生产部署 NO-GO（待同版本发布包及部署演练）”。

- 2026-07-13：建立 Agent v1 跨 Codex 线程进度台账；启动 `V1-PROD-01` 审计，尚未声明工具实现完成。
- 2026-07-13：完成生产查询接口静态审计；识别出裸 ID/模糊订单号与蓝图安全边界的冲突，新增 `V1-PROD-00` 作为生产工具前置，不以 Base64 或内部 ID 绕过。
- 2026-07-13：完成 `V1-PROD-00` 通用受控业务实体 ref 及 4 项单测；简易部署初始化和更新脚本可自动生成独立密钥；`V1-PROD-01` 解除前置阻断并进入接入阶段。
- 2026-07-14：完成 `V1-PROD-01` 全链路，新增 `resolve_production_entities`、`query_production_order_progress`；工具总数更新为 19，Python 110 项、Java 根项目全量、warehouse-mcp 49 项测试通过。
- 2026-07-14：启动 `V1-PROD-02`，文件范围和追溯语义边界已登记，尚未声明工具可用。
- 2026-07-14：完成 `V1-PROD-02` 全链路，新增 `query_boiling_batch_trace`，工具总数更新为 20；Python 111 项、Java 根项目全量及 warehouse-mcp 全量测试通过。
- 2026-07-14：启动 `V1-PROD-03` 生产领料追溯，尚未声明工具可用。
- 2026-07-14：完成 `V1-PROD-03` 全链路，新增 `query_material_pick_trace`，工具总数更新为 21；Python 112 项和相关 Java/MCP 测试通过。
- 2026-07-14：启动 `V1-PROD-04` 生产标签完成度，尚未声明工具可用。
- 2026-07-14：完成 `V1-PROD-04` 全链路，新增 `query_production_label_completion`，工具总数更新为 22；Python 113 项、Java 根项目全量及 warehouse-mcp 全量测试通过。
- 2026-07-14：启动 `V1-PROD-05` 在制物料与领料候选查询设计审计，尚未声明工具可用。
- 2026-07-14：完成 `query_in_process_materials` 全链路并登记为第 23 个工具；继续实施同组 `query_material_candidates`。
- 2026-07-14：完成 `query_material_candidates` 并登记为第 24 个工具；Python 115 项、Java 根项目和 warehouse-mcp 全量测试通过，production_expert 当前 v1 规划集完成。
- 2026-07-14：启动 `V1-LOG-01 query_pallet_tasks`；识别出现有页面接口缺少显式 `task:view` 注解且返回内部 ID，决定新增安全聚合层而非直接暴露。
- 2026-07-14：完成 `query_pallet_tasks` 全链路并登记为第 25 个工具；新增 logistics_expert，任务查询重新执行 `task:view` 且不返回内部 ID。
- 2026-07-14：启动 `V1-LOG-02 query_stock_documents` 单据聚合设计审计。
- 2026-07-14：完成 `query_stock_documents` 全链路并登记为第 26 个工具；每次只查询一种明确单据来源，不伪造统一历史流水。
- 2026-07-14：启动 `V1-LOG-03` 智能报数批次列表与详情的安全审计。
- 2026-07-14：进入 `V1-FINAL-ACCEPTANCE`；确定性验收锁定 47 个 L1 工具、9 个非空业务专家和主 Agent 空工具集，并补充 47 ToolCallback 调用测试与 9 专家浏览器 canary。Python 139 项、根项目 Java 188 项、warehouse-mcp 50 项和前端构建已通过；真实部署 16 条 Playwright E2E 因当前终端未配置本地验收账号、数据库密码和服务密钥而尚未执行，不能声明最终上线通过。
