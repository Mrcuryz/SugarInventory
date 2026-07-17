# Agent v1 模块、专家与 MCP 覆盖重新审计

审计日期：2026-07-13
审计范围：前端菜单与路由、`webpage/src/api`、后端 Controller/Service、审计当时的 17 个 MCP 工具、Agent 专家白名单。
审计性质：只读架构审计，不代表本文提出的工具已经实现或已经获得白名单授权。

## 1. 结论

当前 `main + inventory + warehouse + assay + pallet + production + audit` 的专家骨架是从已实现 MCP 工具反推出来的，并没有完整反映系统菜单和后端业务边界。

审计时已有的 17 个只读 MCP 工具（后续实施进度以能力注册表为准）：

- 库存查询：基础覆盖；
- 库位查询：弱覆盖；
- 化验记录与质量异常：较强覆盖；
- 二维码 / 托盘生命周期：较强覆盖；
- 任务、单据、智能报数：基本未覆盖；
- 生产订单、煮糖、领料、产出标签：未覆盖；
- 产品、筛网、标准等基础资料：只有产品 resolver 和标准覆盖检查，不构成模块覆盖；
- 员工、角色、权限：未覆盖；
- 操作日志和 Agent Review：专家仍为空白名单。

因此，当前不能称为“完整 Agent v1”。MCP 数量也不能作为覆盖率指标；应以用户业务目标和页面主流程是否可由只读工具完成来验收。

## 2. 审计来源与不一致

### 2.1 前端菜单

`webpage/src/utils/navigation.js` 和 `webpage/src/router/index.js` 当前展示以下能力域：

1. 首页看板；
2. 操作日志、AI Review；
3. 产品管理；
4. 库存汇总、智能报数、库位管理、仓库平面图；
5. 二维码管理、固定产品二维码池；
6. 半成品/成品入库任务、出库任务、调拨任务；
7. 煮糖批次、生产订单、半成品领用、产出贴码；
8. 筛网管理；
9. 化验、批量化验组、化验标准；
10. 半成品/成品单据；
11. 用户、角色和权限管理。

### 2.2 后端能力

后端 Controller 还确认了以下重要边界：

- 库存：汇总、分布、台账、备料池、库位近期操作；
- 仓储操作：入库、出库、叠放、调拨；
- 托盘任务：任务列表、确认、取消、半成品/成品出库、调拨、库位图任务；
- 自动入库：解析、历史、详情、确认；
- 生产：订单、煮糖批次、领料候选、领料、产出、标签批次、完成生产、追溯图；
- 质量：化验、化验组、质量标准、产品标准关系；
- 主数据：产品、筛网、库位；
- 管理：员工、角色、权限；
- 审计：操作日志、Agent Review、Agent tool audit。

### 2.3 OpenAPI 缺口

项目规范指定优先读取 `docs/openapi.json`，但当前工作区不存在该文件。因此本次以 Controller 实际代码为准，并将“重新导出 OpenAPI、与前端 API 做差异检查”列为 Agent v1 的基础治理任务。

## 3. 页面/API 与当前 MCP 覆盖矩阵

| 能力域 | 主要页面/API | 当前 MCP 覆盖 | 结论 |
| --- | --- | --- | --- |
| 首页看板 | `Home.vue`，库存、库位、产品、任务综合展示 | 可复用部分库存工具 | 不应单独设专家；后续只能使用登记的汇总配方 |
| 产品主数据 | `/products/**` | `resolve_products` 只能消歧 | 严重不足，不能查询完整产品目录、详情和状态 |
| 库存 | `/inventory/**` | overview、distribution 较完整 | 基础可用；台账、备料池余额仍缺工具 |
| 库位与地图 | `/warehouse/**`、容量、近期操作 | resolver、status | 弱覆盖；容量分布、近期操作、混放风险缺失 |
| 智能报数 | `/auto-inbound/**` | 无 | 未覆盖；v1 只应开放解析结果、历史和校验状态查询 |
| 二维码/托盘 | `/pallet-codes/**` | 生命周期、异常、流转、打印未入库、批次完成率 | 分析覆盖较好；固定产品池与任务列表仍不属于现有工具 |
| 任务中心 | `/pallet-codes/tasks/**`、出库/调拨任务 | 无业务级任务查询工具 | 未覆盖；不能用 pallet lifecycle 代替任务状态查询 |
| 生产订单 | `/production/orders/**` | 无 | 未覆盖 |
| 煮糖批次 | `/production/boiling-batches/**` | 无 | 未覆盖 |
| 生产领用 | material candidates、pick、in-process | 无 | 未覆盖；v1 只读，禁止领用和完工写入 |
| 产出/标签 | outputs、label batches、QR bind/print | 只有二维码侧局部信息 | 未形成生产闭环视角 |
| 化验 | `/assay/**` | 记录、详情、异常、缺化验、标准覆盖 | 较强覆盖 |
| 批量化验组 | `/assayGroup/**` | 无 | 未覆盖 |
| 化验标准 | `/quality-standards/**`、产品标准关系 | 仅 coverage 的一个已确认口径 | 不足；标准目录、详情和绑定现状不可直接查询 |
| 筛网 | `/screen-mesh/**` | 无 | 未覆盖 |
| 单据 | in-stock、out-stock、semi-products records | 无 | 未覆盖；不能把库存当前值当作历史单据 |
| 操作日志 | `/logs/query` | 无 | `audit_expert` 为空 |
| Agent Review | `/agent/reviews/**` | 无 | `audit_expert` 为空 |
| 员工 | `/employee/**` | 无 | 未覆盖，且需要管理员 RBAC 和字段脱敏 |
| 角色/权限 | `/rbac/**` | 无 | 未覆盖；普通用户不得查看完整权限结构 |
| 本地打印助手 | 浏览器本机 `localhost` 服务 | 无，也不应由服务端 Agent 直接调用 | 明确排除出 Agent v1 服务端 MCP |
| 登录/绑定 | `/auth/**` | 无，也不应成为普通 MCP | 明确排除，避免认证绕过 |

## 4. 建议的 Agent v1 专家划分

专家不应与每个菜单一一对应。应按业务规则、权限边界和上下文内聚性划分。

### 4.1 `main_agent`

- 负责路由、上下文、HITL、安全拒绝和最终回答；
- 业务工具永远为空；
- 不把首页看板做成“万能专家”；
- 只有登记配方才能执行多专家任务。

### 4.2 `inventory_expert`

负责当前库存事实、库存分布、库存台账和备料池余额。产品 resolver 可作为其受控依赖，但产品主数据不归该专家维护。

v1 最小工具集：

- 已有：`resolve_products`、`resolve_warehouses`、`get_inventory_overview`、`get_inventory_distribution`；
- 缺少：`query_inventory_ledger`、`query_prepare_pool_balance`。

### 4.3 `warehouse_expert`

负责库位状态、容量、占用、空置、近期操作和混放事实，不负责入库/出库任务执行。

v1 最小工具集：

- 已有：`resolve_warehouses`、`get_warehouse_status`；
- 缺少：`query_warehouse_capacity_distribution`、`query_warehouse_recent_operations`；
- 待业务确认：`query_warehouse_mixed_product_risks` 的风险阈值。在口径确认前只能返回混放事实，不能自行判定风险。

### 4.4 `logistics_expert`

新增。负责入库、出库、调拨任务，历史单据和智能报数批次。任务和单据共享同一仓储流转语义，合并为一个专家比按页面拆分更稳定。

v1 最小工具集：

- `query_pallet_tasks`；
- `query_stock_documents`；
- `query_auto_inbound_batches`；
- 可选：`get_auto_inbound_batch_detail`。

全部只读。确认任务、取消任务、入库、出库、调拨和智能报数确认均留到 v2 的 preview/execute 体系。

### 4.5 `pallet_expert`

负责二维码身份、托盘生命周期、固定产品码池、流转和异常事实，不负责仓储任务确认。

v1 最小工具集：

- 已有 5 个 M1.4c 工具和 `get_pallet_status`；
- 缺少：`query_fixed_product_qr_pool`；
- 二维码生成、作废、恢复、绑定、打印均不是 v1 工具。

### 4.6 `production_expert`

从占位升级为可用专家，内部 context pack 需要同时理解订单、煮糖、领料、产出和标签闭环，但仍只暴露业务级查询。

v1 最小工具集：

- `query_production_order_progress`；
- `query_boiling_batch_trace`；
- `query_material_pick_trace`；
- `query_production_label_completion`。

创建/取消订单、领料、完成领料、登记产出、绑定/打印标签和生产完工均禁止。

### 4.7 `quality_expert`

由现有 `assay_expert` 演进而来，负责化验记录、批量化验组、质量标准和产品标准关系。名称改为 quality 更符合实际边界；可以保留 `assay_expert` 作为兼容标识直至状态迁移完成。

v1 最小工具集：

- 已有 M1.4b 五个分析工具和 `get_assay_status`；
- 缺少：`query_assay_groups`、`query_quality_standard_catalog`、`get_quality_standard_detail`；
- `ASSAY_WITHOUT_STANDARD`、`UNUSED_STANDARD` 仍是待确认口径，不得补规则。

### 4.8 `master_data_expert`

新增。负责产品和筛网等低频但跨模块复用的基础资料查询，不负责员工/RBAC，也不负责修改配置。

v1 最小工具集：

- `query_product_catalog`；
- `get_product_detail`；
- `query_screen_mesh_catalog`。

产品 resolver 不能替代产品目录工具。产品、筛网的新增、修改和删除属于 v2/L3-L4，不进入 v1。

### 4.9 `administration_expert`

新增、管理员专用。负责员工名册、角色和权限的受控只读查询。该专家的存在不意味着普通用户可访问管理数据。

v1 最小工具集：

- `query_employee_roster`；
- `query_roles`；
- `get_role_permission_summary`。

必须复用后端 RBAC，默认脱敏手机号、登录标识等字段。创建员工、调整角色/权限、停用账号和删除角色全部禁止。

### 4.10 `audit_expert`

负责业务操作日志、Agent 工具审计和 Agent Review。与 administration 分开，避免“能管理权限”等同于“能查看所有审计数据”。

v1 最小工具集：

- `search_operation_logs`；
- `query_agent_tool_audit`；
- `query_agent_answer_reviews`。

必须按用户权限和数据范围过滤，普通浏览器不展示工具白名单、模型上下文、内部密钥或原始堆栈。

## 5. 不建议设为独立专家的页面

- 首页：是展示聚合，不是独立业务域；
- 仓库平面图：是 warehouse/inventory/pallet 的展示入口；
- 本地打印助手：运行在用户电脑，不应由服务端 Agent 越权控制；
- 登录、微信绑定：属于认证基础设施，不做 MCP；
- AI 助手会话自身：属于 main agent runtime，不是业务专家。

## 6. 工具覆盖验收标准

“某模块有专家”至少应满足：

1. 有一个以上真实工具，而不是空白名单占位；
2. 能回答该模块页面上最常见的三个只读业务问题；
3. resolver、详情、列表/聚合之间没有用猜 ID 补缺口；
4. NO_DATA、权限拒绝、实体歧义、超时和服务错误可区分；
5. 工具经过 Java 聚合接口、internal Gateway、warehouse-mcp、Python 路由/formatter、能力注册表和测试全链路；
6. 页面存在写操作不代表 v1 必须开放写 MCP；
7. 普通 REST CRUD 不应一对一转换成 MCP。

## 7. Agent v1 建议实施顺序

### P0：让空白和关键业务域变为可用

1. production：订单进度、煮糖追溯、领料追溯、标签完成率；
2. logistics：任务查询、单据查询、智能报数历史；
3. warehouse：容量和近期操作；
4. audit：操作日志、工具审计、回答审查。

### P1：补齐配置与质量上下文

1. product catalog/detail；
2. screen mesh catalog；
3. quality standard catalog/detail；
4. assay groups；
5. fixed-product QR pool。

### P2：管理员只读能力和覆盖收口

1. employee roster；
2. role/permission summary；
3. inventory ledger、prepare pool balance；
4. 重新导出 OpenAPI，生成菜单/API/工具覆盖的 CI 差异报告。

## 8. v1 / v2 边界

Agent v1 仍只允许 L0、L1 和已确认的 L2 查询/分析，不增加任意报表生成、任意 SQL、自由 DAG 或写操作。

Agent v2 再处理：

- 跨模块趋势报表、图表、导出；
- preview/execute 写工具；
- executionToken、idempotencyKey、强确认、事务和写审计；
- 入库、出库、调拨、任务确认、生产领料、产出登记、标签操作和配置修改。

各专家的具体 v1 查询工具、v2 报表/分析工具以及 `preview -> HITL -> execute` 写工具占位，统一见：

- `docs/mcp-analysis/agent-expert-tool-blueprint.md`
- `docs/agent/expert-tool-roadmap-registry.yaml`
- `docs/agent/cross-expert-recipe-roadmap-registry.yaml`
- `docs/agent/data-semantic-roadmap-registry.yaml`
- `docs/agent/business-rule-roadmap-registry.yaml`

上述文件均为 `DESIGN_ONLY`，不会把占位工具加入当前运行时白名单。
