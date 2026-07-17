# M2-0 库存语义查询可行性、真实需求分类与契约冻结

> 日期：2026-07-14
>
> 范围：设计、静态审计、离线评测准备
>
> 运行时变更：无
>
> 最终结论：**CONDITIONAL_GO**

## 1. 执行结论

本轮不支持直接进入生产运行时语义查询实现。允许继续的只是“小型固定编译器是否可行”的离线验证，且必须先解决三项门禁：

1. 当前库存折算件数在 `get_inventory_overview` 与 `get_inventory_distribution` 的权威代码中存在实质口径冲突；
2. 重叠库存入口没有统一权限口径：distribution 显式只要求登录，overview 依赖全局登录保护，summary 页面 API 使用 `record:query`，ledger 使用 `inventory:view`；仓库/组织行级范围也无法从代码证明；
3. 30 条有仓库材料依据的自然语言样本中，没有一条能够证明“必须建设语义查询”，且样本主要由已有工具测试构成，存在严重选择偏差。

因此结论不是 GO，也不是全面 NO_GO：

- 对“冻结草案、补真实需求、统一指标和权限、做离线固定编译器实验”：**CONDITIONAL_GO**；
- 对“新增生产 MCP 工具、接入 Router、连接数据库执行模型计划、替换前端卡片”：当前明确 **NO_GO**。

### 1.1 对既有方向的明确反对

以下判断不成立或被过度简化：

- “47 个工具导致智能感不足，所以语义查询值得做”没有被当前问题样本证明。现有证据中 80% 是已有工具覆盖或 Router/上下文问题。
- `inventory_current_snapshot_v1` 不能把四个候选指标一次性全部启用。`equivalent_pieces`、`total_weight_kg`、`pallet_count` 当前各有不同程度的权威口径阻塞。
- `production_date` 不能进入首版。该字段不在 `inventory` 事实上，启用它需要本轮明确禁止的 `inventory -> pallet_code` 关系；更不能把 `entry_date` 改名冒充生产日期。
- 当前代码没有可复用的通用行级数据权限引擎。文档中写了“Java 注入数据范围”不等于已经实现。
- 不应先建设完整 SQL AST 平台。对于一个数据集、两个固定多对一关系和少量字段，JSON Schema + Registry + Java 固定编译器已足够；如果它仍覆盖不了已确认需求，首先应重新检查需求是否属于登记报表、规则引擎或数据缺口。
- 不应以 `COUNT(DISTINCT pallet_code_id)` 直接命名 `pallet_count`。它只能证明“关联托盘码数”，不能证明物理托盘数或占用位置数。

更简单的优先顺序是：先修正 Router/上下文和已有工具口径，再验证少数真实长尾当前库存问题；只有后者确实存在，才增加一个内部语义查询原型。

## 2. 基线一致性核对

### 2.1 已确认一致的部分

| 核对项 | 结果 | 证据 |
|---|---:|---|
| Java MCP 顶层运行时注册 | 47 | `warehouse-mcp/.../ToolConfiguration.java` |
| Java Internal Tool Gateway 白名单 | 47 | `McpInternalAgentToolGatewayService.ALLOWED_TOOLS` |
| Python Runtime 白名单 | 47 | `agent-service/app/tools/client.py` |
| 9 个专家的工具集合 | 与 47 个白名单一致 | `agent-service/app/agents.py` 与一致性测试 |
| `main_agent` 业务工具 | 0 | 架构与 Runtime 配置一致 |
| 登记复合配方 | 1 | `warehouse_inventory_latest_assay` |
| Semantic Query Runtime | 未启用 | 数据语义路线图仍为 `DESIGN_ONLY` |

### 2.2 文档漂移和未闭环项

| 事项 | 发现 | 本轮处理 |
|---|---|---|
| MCP 工具状态表 | `docs/mcp/mcp-tool-registry.md` 声明 47，但“当前实现状态”表只计到 46，缺 `get_inventory_distribution`；文档后文及运行时存在该工具 | 只记录，不修改旧注册表 |
| Capability Registry 顶部复核摘要 | 仍写 `docs/openapi.json` 缺失，但仓库中该文件实际存在 | 只记录，不顺手重写历史结论 |
| V1 最终验收 | 确定性测试和构建已通过；真实部署 E2E 仍因本地凭据/环境条件未完全执行 | 不把 V1 描述成“生产实测已全部闭环” |
| 前端卡片契约 | Java/Python 当前仍是 `BusinessCard`/`fields`，无通用 `schemaVersion`；前端特殊处理 `inventory_distribution` | 保留现状，仅新增离线契约草案 |

本轮没有修改上述运行时、旧文档或测试基线。

## 3. 真实问题语料与分类统计

完整逐条记录见 `evaluation/inventory-question-corpus-v1.json`。每条记录包含角色证据、目标、期望输出、数据来源、历史事实、跨域关系、当前可答性和不能回答原因。

### 3.1 证据强度

- 30 条纳入统计的样本全部在仓库中已有明确自然语言措辞；
- 其中 5 条来自 Review Lite/页面复核安全摘要，20 条来自 Agent 自动化测试，5 条来自产品目标示例；
- 仓库没有可安全读取的生产对话随机样本，也没有角色分布、频率或客户组织信息；
- 另有 4 条从页面筛选操作归一化出的假设，已标记 `DERIVED_NOT_OBSERVED`，不进入需求占比；
- 没有读取 token、Authorization、手机号、完整身份、数据库连接信息或原始敏感日志。

### 3.2 分类结果

| 分类 | 数量 | 比例 | 判断 |
|---|---:|---:|---|
| `EXISTING_TOOL` | 16 | 53.3% | 已有确定性工具能够覆盖 |
| `ROUTER_OR_CONTEXT_FIX` | 8 | 26.7% | 应修路由、消歧、实体解析或会话上下文 |
| `SEMANTIC_QUERY` | 0 | 0.0% | 当前有证据样本没有证明必需性 |
| `REGISTERED_REPORT` | 2 | 6.7% | 是导出/登记报表问题，不应交给任意查询 |
| `RULE_ENGINE` | 0 | 0.0% | 页面派生的“状态”问题因口径不明未计入 |
| `DATA_GAP` | 2 | 6.7% | 历史库存、批次化验覆盖事实不足 |
| `FUTURE_WRITE` | 1 | 3.3% | 当前只读边界明确拒绝 |
| `UNSUPPORTED` | 1 | 3.3% | 凭据泄漏提示注入，必须拒绝 |

`EXISTING_TOOL + ROUTER_OR_CONTEXT_FIX = 24/30 = 80%`。就现有材料而言，“智能感不足”的主因更像是工具使用和上下文，而不是缺少语义查询。

逐条判断中，23/30 当前可以由已有能力可靠回答或正确追问；2/30 需要历史事实，6/30 涉及跨域关联。跨域问题已有受控工具/登记配方或明确数据缺口，不能被首个纯库存语义数据集吸收。30 条样本均没有可信角色标注，这也阻止了按普通仓管、主管和管理层估算需求。

但不能反向得出“语义查询需求为 0”。自动化测试本来就是围绕已实现工具编写，样本选择机制会系统性压低长尾分析问题。因此本轮不能回答真实客户中语义查询的百分比，只能回答：**仓库当前证据没有证明它值得进入运行时。**

### 3.3 客户确认缺口

进入原型前至少需要一批脱敏、去重、按来源随机抽取的真实库存问题，并补齐：

- 普通仓管、库管主管、生产协调、管理层各自的样本量与频率；
- 问题发生时用户所在页面、上一轮上下文和最终是否解决；
- 用户需要的是列表筛选、聚合、解释、导出还是写操作；
- 用户说“件数”“板数”“重量”“库存”时实际采用的业务口径；
- 用户是否真的需要任意维度组合，还是只需要 3～5 张稳定登记报表；
- 仓库侧别、入库日期分布和 Top N 是否有真实决策用途。

## 4. `inventory_current_snapshot_v1` 数据集草案

机器可读草案见 `inventory-current-snapshot-v1-dataset.yaml`。其状态为 `M2_0_CONTRACT_DRAFT`，`runtime_enabled=false`。

### 4.1 权威事实粒度

一行事实代表：**一个当前占用物理库位坐标的 `inventory` 记录**。

依据：

- `laibin.sql:154` 和迁移脚本对 `(warehouse_id, side, row_number, layer)` 建立唯一约束；
- `inventory` 行包含产品、库位、位置坐标、当前数量/零散件、入库日期等可变现状；
- `pallet_code_id` 可能为空，因此一行不能无条件称为一个托盘；
- 它不是“产品 × 仓库”的聚合行，也不是入库事件、库存日快照或历史事件账。

`asOf` 应表示 Java 在同一只读事务中取得结果的查询时点。它只能说明“该时点查询到的当前状态”，不能把当前表恢复成过去事实。

### 4.2 指标权威来源矩阵

| 指标 | 建议状态 | 业务定义 | 权威来源/冲突 | Null、去重、精度 | 对账工具 |
|---|---|---|---|---|---|
| `inventory_record_count` | 条件可启用 | 允许的多对一关系后 `COUNT(*)` | `inventory` 事实行和 ledger 明细 | 无行=0；不去重；整数 | `query_inventory_ledger` |
| `equivalent_pieces` | **阻塞** | 建议为 `pieces>0 ? pieces : quantity*pieces_per_pallet` 后求和 | 出库代码和汇总视图支持互斥语义；`InventoryDistributionSqlProvider` 当前无条件相加，存在重复折算 | `pieces_per_pallet` 非法即数据质量错误；每事实一次；整数 | 三个库存工具 |
| `total_weight_kg` | **阻塞** | 每行权威折算件数 × 单件重量后求和 | 依赖 `equivalent_pieces`；当前 distribution 同样受重复折算影响 | `BigDecimal`；聚合前不舍入；重量非法即错误 | 三个库存工具 |
| `pallet_count` | **禁用** | 当前实现实际是非空 `pallet_code_id` 去重数 | `InventoryDistributionSqlProvider` 为 `COUNT(DISTINCT i.pallet_code_id)` | 空托盘码不计；整数 | distribution、ledger |

关键冲突示例（每板 25 件）：

| 事实行 | 建议/汇总视图 | 当前 distribution | 差异 |
|---|---:|---:|---:|
| `quantity=2, pieces=0` | 50 件 | 50 件 | 0 |
| `quantity=1, pieces=10` | 10 件 | 35 件 | +25 件 |
| 上述两行合计 | 60 件 | 85 件 | +25 件 |

`laibin.sql:552` 的视图用 `pieces > 0` 时不累计 `quantity`；`PalletCodeServiceImpl.buildPalletOutWeight` 也采用互斥语义。相反，`InventoryDistributionSqlProvider.METRICS` 使用 `quantity * pieces_per_pallet + pieces`。现有单测主要验证生成结构或 mock 聚合值，没有证明真实数据下两套口径一致。

另一个容易遗漏的对账陷阱是：`get_inventory_overview` 的 `summary` 由 MCP 对当前页 `records` 调用 `summarize(records)` 得到，不是独立全量聚合。只有遍历完所有分页，或证明相同过滤结果完整落在一页内，才能把它与语义查询总计对账。否则差异可能只是分页范围，不是指标公式。

不能为了新数据集选其中一个“默认正确”。需要产品/仓储人员确认零散件编码规则，并用真实脱敏夹具对三个入口逐行对账。

### 4.3 维度权威来源矩阵

| 维度 | 来源 | 候选基数 | 过滤 | 分组 | 排序 | Null/未知 | 状态与限制 |
|---|---|---|---:|---:|---:|---|---|
| `product` | `inventory.product_id -> product.id` | 中，未实测 | 是 | 是 | 是 | 关系缺失即数据质量错误 | 权限门禁后可用；模型只能用 opaque `valueRef` |
| `product_type` | `product.product_type` | 低，未实测 | 是 | 是 | 是 | 显示“未设置”并 warning | 权限门禁后可用 |
| `warehouse` | `inventory.warehouse_id -> warehouse.id` | 中，未实测 | 是 | 是 | 是 | 关系缺失即数据质量错误 | 权限门禁后可用；模型只能用 opaque `valueRef` |
| `warehouse_side` | `inventory.side` | 极低 | 是 | 是 | 是 | 显示“未设置侧别”并 warning | 权限门禁后可用 |
| `inbound_date` | `inventory.entry_date` | 高，未实测 | 是 | 是 | 是 | 归入“未知日期” | **待业务确认**；仅用于当前库存日期分布/库龄 |
| `production_date` | 只能来自 `pallet_code.production_date` | 高，未实测 | 否 | 否 | 否 | 不适用 | **首版禁用**；需要被禁止的额外关系 |

`laibin.sql:139` 对 `entry_date` 的注释是“入库日期（生产日期）”，但代码和托盘表又单独维护 `production_date`。这不是可以由架构师猜测的同义词，需要业务确认和数据抽样。未确认前，`inbound_date` 也只能处于条件状态。

### 4.4 关系与重复累计防护

首版只允许：

- `inventory.product_id = product.id`，多对一；
- `inventory.warehouse_id = warehouse.id`，多对一。

固定编译器必须保证 Join 后的事实行计数不增加，并在测试中加入“孤儿关系”和“主数据异常重复”夹具。禁止 assay、production、stock document、pallet flow、pallet code 和自定义 Join。

## 5. 数据范围与权限审计

### 5.1 代码中实际存在的权限

| 入口/能力 | Controller 权限 | Service/Mapper 行级过滤 | 结论 |
|---|---|---|---|
| `/api/inventory/distribution` | `isAuthenticated()` | 未发现当前用户/组织/仓库范围参数 | 登录用户全局查询语义 |
| overview 使用的 `/api/inventory/stock/page`、`/api/products/{id}` | 无方法级 `@PreAuthorize`，依赖全局默认鉴权 | 未发现行级范围参数 | 与 distribution 类似，但权限契约是隐式的 |
| 页面 `/api/inventory/summary` | `record:query` | 未发现行级范围参数 | 与 Agent overview 不是同一入口，仍说明库存查询权限不统一 |
| `/api/inventory/agent-read/ledger/query` | `inventory:view` | 未发现行级范围参数 | 显式使用另一 RBAC 权限点 |

仓库中可以确认 RBAC，但不能确认通用组织、工厂、仓库、库区或用户分配范围。`LoginUser` 及库存查询 Service 没有展示可复用的仓库 scope。代码结构看起来是全局/单租户式，但“实际部署为单组织”无法从仓库证明。

### 5.2 必须补齐的权限契约

在任何运行时原型前必须：

1. 由产品、安全和后端共同选择库存语义查询的唯一权限点；
2. 书面确认实际部署是单组织全仓可见，还是存在仓库/库区范围；
3. 若存在范围，Java 必须从认证上下文注入，Plan schema 中不得出现 scope、组织 ID、仓库原始 ID；
4. opaque `valueRef` 必须绑定用户、会话、实体类型、过期时间和当前可见范围；
5. 相同用户对现有工具与语义数据集必须得到相同允许/拒绝和可见数据集合；
6. 权限拒绝、无数据、数据质量错误、工具故障必须使用不同错误码和 Artifact。

未完成以上事项时，语义查询运行时为 **NO_GO**。

## 6. 最小 `SemanticQueryPlan`

JSON Schema 草案见 `schemas/inventory-semantic-query-plan-v1.schema.json`。

### 6.1 结论

对于当前被允许的范围，以下链路足够：

```text
模型候选 Plan
  -> JSON Schema 结构校验
  -> Dataset Registry 启用状态与操作符校验
  -> Java 权限/范围/成本校验
  -> Java 固定字段映射编译器或受控 MyBatis Provider
  -> 只读事务执行
```

不需要完整 SQL AST 平台。固定编译器内部可以使用枚举映射和预定义 SQL 片段，但模型永远不提供 SQL、表名、列名、Join、权限条件、公式、函数或原始 ID。

### 6.2 Plan 字段与边界

| 字段 | 规则 |
|---|---|
| `datasetId` | 常量 `inventory_current_snapshot_v1` |
| `datasetVersion` | 常量 `1.0` |
| `resultShape` | `AGGREGATE` 或登记明细层 `INVENTORY_RECORDS` |
| `measures` | 最多 2 个；Registry 未启用的指标即使 schema 可识别也不能执行 |
| `dimensions` | 最多 2 个；不包含 `production_date` |
| `filters` | 最多 8 个；实体使用 opaque `valueRef`，日期最大 180 天 |
| `sort` | 最多 2 个登记字段 |
| `limit` | 1～100，默认 20 |

Schema 是结构边界，不是启用清单的唯一权威。`equivalent_pieces` 和 `total_weight_kg` 为了离线规划评测保留在结构枚举中，但 Dataset Registry 当前会拒绝执行；`pallet_count` 和 `production_date` 未进入 Plan 枚举。

还需由 Java 做 JSON Schema 无法安全表达的校验：

- 字段在当前 Registry 版本是否 `ENABLED`；
- `sort.field` 是否出现在维度或指标中；
- BETWEEN 起止顺序和 180 天限制；
- entity `valueRef` 的签发人、会话、类型、权限范围与有效期；
- 明细下钻只返回登记列，不因 `dimensions=[]` 扩大列范围；
- 预计扫描量、超时、并发和分页；
- canonical JSON 后计算 `queryPlanHash`，审计保存 Plan、Registry 版本和权限范围摘要。

## 7. 有界迭代状态设计

不替换现有 `last_*` 状态。建议在现有 Redis 会话状态中新增可选命名空间 `analysisStateV1`；旧 Runtime 和 formatter 不识别该字段时继续按 V1 工作。

```json
{
  "datasetId": "inventory_current_snapshot_v1",
  "datasetVersion": "1.0",
  "queryPlan": {},
  "queryPlanHash": "sha256:...",
  "filters": [],
  "resultRef": "res_...",
  "asOf": "2026-07-14T10:00:00+08:00",
  "provenance": {
    "registryVersion": "1.0",
    "auditRef": "aud_...",
    "scopeHash": "sha256:..."
  },
  "availableDrilldowns": ["INVENTORY_RECORDS"],
  "iterationCount": 1,
  "limitations": ["当前库存快照，不是历史趋势"],
  "previousPlanRefs": ["plan_..."]
}
```

`previousPlanRefs` 是实现“撤销上一次”的服务器侧引用，不保存数据库结果，也不由模型填写。最多保留 3 次变更。

允许的状态变更只有：

- `ADD_FILTER`、`REMOVE_FILTER`；
- `REPLACE_MEASURES`；
- `REPLACE_DIMENSIONS`；
- `REPLACE_SORT`；
- `DRILLDOWN_TO_INVENTORY_RECORDS`；
- `UNDO_LAST_PLAN`。

推荐模型输出受限 `PlanMutation`，由 Java/Python 的确定性状态归并器基于上一计划生成完整新 Plan；不要让模型复制并自由改写整个分析状态。第 4 次修改、数据集切换、Join 变化、写操作和 scope 变化都必须拒绝。

`resultRef` 绑定用户与会话并设置短 TTL；下钻依据登记 group key/opaque ref，而不是让模型从结果文字中拼条件。数据库返回的任何文本只能作为数据展示，不能进入系统指令或工具描述。

## 8. Artifact 契约审计

Schema 草案见 `schemas/inventory-artifact-v1.schema.json`。

### 8.1 现状判断

当前前后端可以在不替换 47 个工具卡片的情况下并行支持一个新 Envelope，但不能直接把现有 `BusinessCard.fields` 当作版本化 Artifact：

- Java `AgentBusinessCardVO` 与 Python `BusinessCard` 没有 `schemaVersion`；
- 前端对 `inventory_distribution` 存在工具/卡片类型专用渲染；
- `fields: Map<String,String>` 难以稳定表达表格类型、单位、分页、provenance 和限制；
- 现有通用 fallback 可以保留，首版只为实验性 Artifact 增加独立 renderer。

### 8.2 单一权威责任建议

推荐：**Java 语义应用层直接生成 `DatasetResult`，并用同一份 Registry 确定性包装成 Artifact Envelope；Python 只透传 Artifact，并用安全摘要帮助模型生成文字解释。**

原因：

- Java 已负责权限、指标、数据范围、执行和审计，最接近权威数据；
- 单位、列类型、限制、分页和 provenance 都能从 Registry/执行结果确定；
- 若 Java 输出 DatasetResult、Python 再维护完整转换规则，会在两处重复指标/列/版本逻辑；
- 模型不得成为可信 UI schema 生成器。

更简单的实现形式不是再建一个大型 Presenter 平台，而是让 Artifact 的 `data` 直接使用版本化 DatasetResult 结构。只有确有第二种消费者时再拆独立 adapter。

### 8.3 首版类型与约束

| 类型 | 用途 | 移动端策略 |
|---|---|---|
| `metric_grid` | 无分组或少量核心指标 | 1～2 列，自适应换行 |
| `data_table` | 聚合列表或登记明细 | 最多 8 列；移动端优先 3～4 列、横向滚动或行展开 |
| `chart` | 分类柱状图；有序入库日期分布可用折线 | 默认 bar；类别 Top N；其余合并/表格 fallback |
| `warning` | 权限拒绝、数据质量、范围超限、不支持 | 必须有安全 fallbackText |
| `text` | 限制说明和无结构结论 | 不允许 HTML、链接或按钮 |

折线图只有 `categoryField=inbound_date` 且日期升序时可用，并必须带限制：“当前仍在库记录的入库日期分布，不是历史库存趋势”。按仓库/产品的分类值不能叫趋势，也不应用 line。

前端正常渲染不需要知道 `toolName`；管理员调试信息应走独立脱敏诊断通道，不能混入 Artifact。前端只识别 `schemaVersion + artifactType`，未知版本展示 `fallbackText`。

## 9. 离线黄金测试集与评测

黄金集见 `evaluation/inventory-semantic-query-golden-set-v1.json`，共 26 个用例，覆盖：

- 仓库/产品分组、产品/仓库过滤、指标切换、排序和 Top N；
- 当前库存按入库日期分布；
- 登记明细下钻、撤销和 3 次迭代上限；
- 歧义表达和实体候选消歧；
- 历史趋势、化验 Join、数据集切换、自定义公式；
- Prompt Injection、SQL/物理表字段、敏感字段、原始 ID；
- 日期/行数上限、权限范围扩大；
- 折线图误用防护。

### 9.1 指标必须分开统计

| 指标 | 建议门禁 | 说明 |
|---|---:|---|
| 安全负向请求拒绝率 | **100%** | 任一失败即阻止运行时原型 |
| 禁止数据集、字段、Join 拒绝率 | **100%** | 任一失败即阻止 |
| 当前快照不被解释为历史趋势 | **100%** | 任一失败即阻止 |
| 不生成 SQL、表名、列名 | **100%** | 任一失败即阻止 |
| 不扩大用户数据范围 | **100%** | 任一失败即阻止 |
| 核心计划完全正确率 | ≥90% | canonical Plan 完全匹配，连续 3 次运行 |
| 普通计划字段级准确率 | ≥97% | dataset、指标、维度、过滤、排序、limit 分开计 |
| 应追问问题的追问率 | ≥90% | 不得用错误查询掩盖歧义 |
| 不支持问题正确拒绝率 | ≥95% | 错误码和安全替代说明均正确 |
| 同题多次 canonical 一致性 | ≥95% | 每例 3 次；等价字段顺序先 canonicalize |

这些是建议验收阈值，不是已取得的成绩。本轮未选择模型端点，也未运行生产 Runtime 或数据库查询，因此不能报告准确率。

### 9.2 结果回答评测

除 Plan 外，必须用固定 DatasetResult 夹具检查最终回答：

- 不把排序第一说成“异常”或“原因”；
- 不从相关性推断因果；
- 明确 `asOf` 和权限范围；
- 数值、单位和舍入与 DatasetResult 一致；
- 空数据、权限拒绝、数据质量错误、执行失败使用不同表述；
- 当前日期分布不表述为库存增长/下降；
- `pallet_count` 未启用时不换词偷偷输出“托盘数”。

## 10. 与现有确定性工具对账设计

对账必须在相同用户、相同过滤、同一只读事务/尽可能相同 `asOf`、相同可见范围下执行。不能以“选择一个结果为准”处理差异。

| 用例 | 语义数据集候选 | 对账工具 | 期望 | 差异定位重点 |
|---|---|---|---|---|
| R-01 仅整板行 | 件数、重量 | overview、distribution、ledger | 完全一致 | `quantity × pieces_per_pallet` |
| R-02 `quantity=1,pieces>0` 零散行 | 件数、重量 | 三者 | 完全一致 | 已知 distribution 重复折算风险 |
| R-03 整板行 + 零散行混合 | 件数、重量 | 三者 | 完全一致 | 行级互斥后再聚合 |
| R-04 多产品、不同每板件数 | 总件数、总重量 | 三者 | 件/重量一致 | 不输出无意义“总板数” |
| R-05 `pieces_per_pallet` 或重量非法 | 错误 | 三者 | 显式数据质量错误 | 不得静默排除或按 0 |
| R-06 `pallet_code_id` 为空 | 记录数、关联托盘码数 | distribution、ledger | 记录数=1，关联托盘码数=0 | 证明两指标不是同义词 |
| R-07 空范围 | 0/空表 | 三者 | `NO_DATA`，不是权限拒绝 | 空值和零值规则 |
| R-08 无权限用户 | 无结果 | 三者 | 同一 `PERMISSION_DENIED` | 当前入口权限契约不一致 |
| R-09 仓库/产品过滤 | 各指标 | 三者 | 可见集合和数值一致 | 实体解析、范围注入 |
| R-10 并发库存变化 | 各指标 | 三者 | 同一事务快照或明确不同 asOf | 防止拿两个时间点误判口径 |
| R-11 分组总和 | 分组与总计 | distribution、ledger | 分组求和=总计 | Join 重复和 unknown 组 |
| R-12 舍入 | 重量 | 三者 | BigDecimal 值一致 | 聚合前舍入、展示舍入 |
| R-13 overview 多分页 | 件数、重量 | overview、ledger | 遍历所有页后才与全量一致 | MCP summary 只汇总当前页 records |

差异必须归因到以下之一并保留审计证据：现有工具口径、新数据集定义、数据源、`asOf`、权限范围、关系基数、去重、Null 或舍入。权威口径只能由业务规则与代码/数据共同确认，不能由测试期望反向决定。

## 11. 实施复杂度与风险

### 11.1 复杂度评估

| 工作项 | 复杂度 | 说明 |
|---|---|---|
| 获取并脱敏真实问题样本 | 中 | 技术简单，治理和代表性困难 |
| 确认库存件数/重量口径 | 中 | 需要业务、写入路径、存量数据共同验证 |
| 统一权限和 scope | 中到高 | 若实际是单组织则较小；若存在仓库级范围则需后端能力 |
| JSON Schema + Registry Validator | 小 | 一个数据集和有限枚举 |
| Java 固定编译器 | 中 | 不需要完整 AST，但要覆盖权限、成本、分页和审计 |
| 有界状态与撤销 | 中 | 可兼容 Redis；需并发锁和 TTL 测试 |
| 单一 Artifact Envelope | 中 | 契约不复杂，移动端和版本回退需测试 |
| 历史库存趋势 | 高/不在范围 | 缺历史事实，不能用当前表补做 |

### 11.2 主要风险

| 风险 | 概率/影响 | 控制 |
|---|---|---|
| 新层放大已有错误指标 | 高/高 | 指标未对账不启用；R-01～R-13 |
| 权限比现有入口更宽 | 中/极高 | 统一权限契约、scopeHash、同用户对账 |
| 为假设需求建设平台 | 高/高 | 先收真实问题和频率；未证明则停止 |
| Schema 很小但 Registry 维护线性增长 | 中/中 | 首版只一个数据集；字段增加需需求证据和 owner |
| 模型产生合法但业务错误 Plan | 中/高 | 完全匹配评测、歧义追问、后端 Registry 校验 |
| 当前日期分布被误称趋势 | 中/高 | 100% 负向门禁、固定 limitation |
| Java/Python 双重 Presenter 漂移 | 中/中 | Java 单一权威 Artifact，Python 透传 |
| 结果文字提示注入 | 中/高 | 结果永不作为指令，固定 formatter/presenter |
| 通用表格移动端不可用 | 中/中 | 列数上限、优先列、横向滚动/行展开、fallbackText |

## 12. GO / NO-GO 门禁

### 12.1 本轮结论：CONDITIONAL_GO

只有全部满足以下条件，才可以开始“离线原型”，而不是生产接入：

1. 获得至少 30 条脱敏、去重、可说明抽样方式的真实库存问题，覆盖至少两类实际角色；
2. 至少出现一组重复、稳定的当前库存长尾问题，现有窄工具、Router/上下文修复和登记报表都不能以更低成本覆盖；建议门槛为 8 个不同问题模式或有效样本的 15%，但客户明确的高价值低频场景可以用业务价值替代频率门槛；
3. 业务确认零散件编码和 `equivalent_pieces` 公式，三条现有路径用夹具对账；
4. `total_weight_kg` 的精度、Null、舍入和异常规则确认；
5. `pallet_count` 改为准确名称或继续禁用；
6. `entry_date` 的业务含义确认；`production_date` 继续禁用；
7. 统一权限点并证明动态查询不扩大用户可见范围；若声称单组织全仓可见，需部署负责人书面确认；
8. 数据扫描基线、索引、超时和并发上限有可验证数值；
9. 离线黄金集的五个 100% 硬门禁全部通过，其他指标达到约定阈值；
10. 现有 47 工具、9 专家、HITL、Redis、Gateway、MCP 回归测试保持通过。

出现以下任一情况，应转为 NO_GO 并停止语义查询方向：

- 真实问题仍主要由 Router/上下文、已有工具或 3～5 张登记报表覆盖；
- 件数/重量无法形成统一权威口径；
- 数据权限范围不能在 Java 中确定性复用；
- 客户真正要的是历史库存、批次质量证明或写操作，而现有数据事实不支持；
- 固定编译器已无法满足首个数据集，且原因是需求不断引入跨域 Join/自定义公式；这意味着首个切片选择错误，而不是应立即升级为任意 SQL/完整 AST。

## 13. 下一步最小实施切片（仅在门禁满足后）

推荐顺序：

1. **M2-0.1 证据与口径闭环**：安全导出真实问题样本；业务签字确认件数/重量/入库日期；安全负责人确认权限和部署范围。本步仍不写运行时代码。
2. **M2-0.2 离线 Java 契约实验**：只在测试/独立包中实现 Schema/Registry 校验和固定编译器，输入黄金 Plan、输出编译描述或固定 SQL 模板；使用内存夹具，不注册 Controller/MCP，不接 Router，不连生产数据库。
3. **M2-0.3 离线 Artifact 夹具**：Java 从固定 DatasetResult 生成 5 种 Artifact，前端增加一个隔离的 story/test renderer；不替换现有卡片。
4. 只有三步全部通过，才单独审议 M2-1 Feature Flag 下的只读原型；届时仍不得删除、隐藏或修改现有 47 个工具。

本轮没有提出 `analytics_expert`、跨域 Join、SQL 生成、历史趋势、preview、execute 或部署变更。
