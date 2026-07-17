# M2-0.1 真实需求证据、库存数量口径和权限契约闭环审计

日期：2026-07-14

阶段：M2-0.1

结论：**NO_GO（不进入 M2-0.2；生产 Runtime 继续 NO_GO）**

## 1. 执行结论

本轮不建议继续离线固定编译器实验，更不允许接入生产语义查询 Runtime。原因不是“语义查询一定不适合 WMS”，而是四项前置证据同时未闭环：

1. 现有可核验证据只有产品负责人/开发验收人员的一组浏览器自测，9 条表达中 7 条是 Router、上下文或工具结果契约问题，2 条由现有工具正确处理，0 条证明了稳定的动态指标/维度组合需求。
2. `inventory.quantity` 与 `inventory.pieces` 的候选口径有较强代码和设计文档证据，但没有业务负责人签署，也没有当前部署库的脱敏聚合画像，`inventory_equivalent_pieces` 必须保持 `BLOCKED`。
3. `total_weight_kg` 依赖件数规则，库存行又没有入库时单件重量快照，只能候选为“按当前产品配置估算”；业务是否接受尚未确认，因此保持 `BLOCKED`。
4. 库存读取入口混用 `isAuthenticated()`、`record:query`、`inventory:view`，Service/Mapper 未发现确定性的用户仓库或组织范围注入；部署是否单组织、是否全员可见全部仓库也没有确认，权限契约保持 `BLOCKED`。

继续做固定编译器会把尚未确认的数量公式和权限假设固化进新平台，既不能验证真实产品价值，也会制造第二套库存口径。本阶段更简单且收益更高的路线是：先修现有 Router/上下文契约、完成浏览器复测、取得业务与权限签署、采集代表性验收问题，再重开门禁。

本轮只新增审计文档、脱敏证据集和契约草案；未新增或修改 MCP 工具、Runtime、Router、Java 查询接口、动态数据库查询、前端卡片或部署配置。

## 2. 基线与证据边界

### 2.1 基线一致性

- 当前静态 Capability Registry、Java 内部网关白名单和 Python 测试基线仍以 47 个 L1 只读 MCP 工具、9 个专家、1 个受控复合配方为基线。
- `agent-service/tests/test_agent_service.py` 的能力握手测试断言 `toolCount=47`、`recipeCount=1`；本轮没有启动生产 Runtime 做在线握手，因此结论是“代码/测试基线一致”，不是“生产部署实例已现场核验”。
- 当前工作树包含大量本轮开始前已经存在的未提交改动；本报告审计的是当前工作树行为，不代表这些代码已经提交或部署。本轮没有改动这些既有文件。
- 前一阶段的 30 条问题语料混合了测试、产品示例和少量安全 Review/Page 线索，不能继续作为“真实用户样本”统计。本轮已单独建立 [inventory-real-question-evidence-v1.json](evaluation/inventory-real-question-evidence-v1.json)，测试样例与产品示例不计入真实证据集。
- `query_agent_answer_reviews` 只暴露安全摘要；Review 管理审查台尚未完成。为了避免读取未经脱敏的原始日志，本轮没有从数据库或原始日志补抓用户问题。

### 2.2 真实证据来源

唯一可直接核验的自然语言证据是提交者提供的 8 张浏览器测试截图，共包含 9 个独立用户表达。提交者已说明系统尚未上线，这些问题主要来自本人测试。因此：

- 可以称为“产品负责人/开发验收 UAT 证据”；
- 不可以称为“真实客户代表性样本”；
- 不可以推断普通仓管、质检、生产管理或管理层的真实发生频率；
- 每条只出现一次，`frequencyEvidence` 均为 `OBSERVED_ONCE_NO_CUSTOMER_FREQUENCY`；
- 页面筛选项、自动化测试文案、产品说明中的示例和本次架构师自行构造的问题均未计入。

截图中订单号已在结构化证据集里脱敏；本轮没有读取或输出 token、Authorization、密码、手机号、数据库连接信息、完整用户身份或未经脱敏的原始日志。

## 3. 真实问题证据集与分类统计

### 3.1 全部可核验 UAT 表达

| 分类 | 数量 | 占比 | 说明 |
|---|---:|---:|---|
| `ROUTER_OR_CONTEXT_FIX` | 7 | 77.8% | 产品解析、中文角色参数、指代上下文、库位上下文、订单追问、复合配方产品标签契约 |
| `EXISTING_TOOL` | 2 | 22.2% | 模糊订单的安全消歧、唯一订单的进度查询 |
| `SEMANTIC_QUERY` | 0 | 0% | 没有观察到动态指标/维度/过滤组合需求 |
| `REGISTERED_REPORT` | 0 | 0% | 没有重复报表模式证据 |
| `RULE_ENGINE` | 0 | 0% | 没有规则判定类重复需求证据 |
| `DATA_GAP` | 0 | 0% | 截图中的失败均不能仅凭现象判为数据缺失 |
| `FUTURE_WRITE` | 0 | 0% | 没有写操作表达 |
| `UNSUPPORTED` | 0 | 0% | 没有必须拒绝的能力请求 |

这个统计只能说明“当前 9 条自测证据的失败结构”，不能外推客户总体需求。尤其不能用 `0/9` 宣称客户不需要语义查询，也不能用 7 条失败宣称 Router 问题占客户问题 77.8%。

### 3.2 与 `inventory_current_snapshot_v1` 的关系

- 9 条中只有“1号库位当前库存情况如何？这些产品的化验情况如何？”包含当前库存快照问题。
- 当前库存部分已经由 `get_inventory_distribution` 返回；失败发生在把库存分组结果交给 `resolve_products` 的复合配方阶段。
- 该问题需要已登记的受控复合配方，不需要动态选择指标、维度或 Join；化验结果也不能进入库存语义数据集的首版 Join。
- “这个库位还有多少容量”属于库位容量能力；“最近发生过哪些流转”属于已登记流转事件能力；两者都不属于 `inventory_current_snapshot_v1`。

因此，现有证据没有证明首个语义数据集的产品必要性。

### 3.3 登记报表是否更合适

当前也没有证据支持立即建设 3～5 张登记报表。没有重复问题模式时，先做报表与先做语义查询一样属于提前建设。正确顺序是：修复现有失败、采集角色与频率证据；只有出现稳定的 Top N、分布、库龄或导出需求后，再比较“扩展现有工具参数”“登记报表”“语义查询”的成本。

## 4. Router 与上下文修复清单

以下仅是修复清单，本轮没有修改 Runtime。

| UAT 表达 | 应进入专家 / 已有工具 | 截图中的错误路径 | 根因 | 不引入语义查询能否修复 | 成本 / 收益 | 当前状态 |
|---|---|---|---|---|---|---|
| 查询黄冰糖（袋）的产品详情 | `master_data_expert`；`resolve_products → get_product_detail` | 直接落为无数据 | entity resolution、tool argument construction | 能 | 低 / 高 | 当前 mock 回归通过；浏览器未复测 |
| 查询管理员角色有哪些权限 | `administration_expert`；`get_role_permission_summary` | 把已给出的中文角色名当成缺参 | keyword gap、tool argument construction | 能 | 低 / 中 | 当前 mock 回归通过；浏览器未复测 |
| 它今天有没有化验？ | `assay_expert`；复用产品上下文调用 `get_assay_status` | 丢失产品指代并重新解析失败 | context loss、entity resolution | 能 | 中 / 高 | 当前 mock 回归通过；浏览器未复测 |
| 这个库位还有多少容量？ | `warehouse_expert`；有唯一上下文时 `get_warehouse_status` | 返回所有库位容量长列表 | context loss、intent conflict、formatter | 能 | 低 / 高 | 当前 mock 回归覆盖唯一库位上下文；无上下文仍应追问；浏览器未复测 |
| 最近发生过哪些流转？ | `warehouse_expert`；有唯一库位上下文时 `query_warehouse_recent_operations` | 返回通用能力说明 | context loss、keyword gap | 能 | 低到中 / 中 | 当前 mock 回归覆盖唯一库位上下文；浏览器未复测 |
| 查询生产订单2026的进度 | `production_expert`；`resolve_production_entities` | 返回多个候选并要求完整单号 | 无缺陷，正确消歧 | 不需要修复 | 无 / 正确安全行为 | 保持 |
| 查询唯一生产订单 | `production_expert`；`query_production_order_progress` | 正常返回 | 无缺陷 | 不需要修复 | 无 / 已满足 | 保持 |
| 这个订单领过哪些物料？ | `production_expert`；受控订单引用 → `query_material_pick_trace` | 丢失已确认订单上下文，回到通用说明 | context loss、unsupported follow-up mutation | 能 | 中 / 高 | 当前 mock 回归通过；浏览器未复测 |
| 1号库位库存及这些产品的化验 | 已登记复合配方：`inventory_expert → assay_expert` | 库存成功，产品化验解析失败 | tool-result contract、tool argument construction | 能 | 低到中 / 高 | **仍有开放缺口** |

最后一项的开放缺口有直接代码证据：Java `InventoryDistributionServiceImpl.productLabel(...)` 会输出类似“产品名 40kg/件 25件/板”；复合编排在 `agent-service/app/runtime.py` 中直接取 `productLabel/groupLabel` 调用 `resolve_products`。虽然同文件已有 `_distribution_group_product_name(...)` 可剥离规格后缀，但复合编排路径没有使用它；现有复合测试只使用了不带规格后缀的简化 mock，未覆盖真实后端契约。这不是缺少语义查询，而是已有工具之间的结构化交接不完整。

当前代码存在 4 个针对截图问题的 mock 回归：产品详情与产品上下文、中文角色名、库位上下文容量/流转、订单消歧及受控引用追问。选择性执行结果为 `4 passed, 142 deselected`。这只能证明当前 mock 契约通过，不能替代真实浏览器、Java Gateway、MCP 和实际数据的端到端复测。

## 5. `inventory_equivalent_pieces` 权威规则审计

完整版本化草案见 [inventory-metric-permission-contract-v1.yaml](inventory-metric-permission-contract-v1.yaml)。当前状态：**BLOCKED**。

### 5.1 字段候选定义

1. `inventory.quantity`：数据库注释仍写“板数”，但当前单二维码单占位规则下，它兼具“整板数”和“占用板位数”两种语义。对于 `pieces=0` 的当前标准行，表示整板数量（通常为 1）；对于 `pieces>0` 的散件行，`quantity=1` 仅表示占用一个库位板位，不能再计入实际库存件数。
2. `inventory.pieces`：当大于 0 时表示该库存行的实际散件数量；当前规则要求小于 `product.pieces_per_pallet`。当为 0 时，该行按整板解释。数据库允许 NULL，现有代码多处把 NULL 当 0，但这尚未获得业务确认。
3. `product.pieces_per_pallet`：产品当前每板件数配置。入库占位规则要求为正数；产品主数据可更新，库存行没有该值快照。

### 5.2 `quantity > 0 && pieces > 0` 是否合法

**按当前设计意图和主要写路径，它是合法状态。** 证据包括：

- `docs/方案/2026-04-27-single-qr-single-inventory-rule.md` 明确规定整板 `quantity=1,pieces=0`，散件 `quantity=1,pieces=实际件数`，后者表示占用 1 板位。
- `SemiProductRecordServiceImpl.buildInventory(...)` 对所有库存行写 `quantity=1`，散件时写实际 `pieces`。
- `PalletInventoryOccupancyRule` 要求单二维码整板只能 1 板，散件必须 `0 < pieces < pieces_per_pallet`。
- 普通部分出库把剩余实际件数写回 `inventory.pieces`，不会把 `quantity` 同步改成 0。
- 托盘出库、生产备料快照等主要路径在 `pieces>0` 时以 `pieces` 为实际数量，否则才计算 `quantity * pieces_per_pallet`。

因此，不能把所有 `quantity>0 && pieces>0` 行当脏数据迁移掉。当前候选公式是：

```text
SUM(CASE WHEN pieces > 0
         THEN pieces
         ELSE quantity * pieces_per_pallet
    END)
```

但“代码和设计文档一致”仍不等于“业务负责人已确认”。`owner` 与 `approvedBy` 尚为空，规则必须保持 `BLOCKED`。

### 5.3 合法、历史与歧义状态

| 状态 | 候选解释 | 当前处理意见 |
|---|---|---|
| `quantity=1,pieces=0` | 当前规则下的一整板 | 候选合法 |
| `quantity=1,0<pieces<pieces_per_pallet` | 合法散件，占 1 板位，实际数量以 pieces 为准 | 候选合法 |
| `quantity>1,pieces=0` | 可能是旧版多板记录 | 不自动判错，先做部署库画像 |
| `quantity>1,pieces>0` | 旧版多板行被部分出库后可能产生，语义歧义 | 隔离审查，不参与权威指标 |
| `pieces>=pieces_per_pallet` | 违反当前散件规则 | 候选无效 |
| `quantity<=0`、`pieces<0`、每板件数无效 | 违反数量不变量 | 候选无效 |
| `pieces IS NULL` | 数据库允许，代码常按 0 兼容 | 历史兼容待确认 |

普通调拨只更新库存位置，不修改数量字段；堆叠/迁移逻辑没有发现统一改写 `quantity/pieces` 的路径。当前入库主路径不会生成 `quantity>1,pieces>0`，但若旧版存在多板库存行，部分出库仅更新 `pieces`，有产生歧义混合状态的风险。

### 5.4 当前数据库是否存在混合状态

**无法证明。** 本轮没有连接生产或部署数据库，也没有读取连接信息。版本库 `laibin.sql` 只有结构与视图定义，没有可作为当前部署状态的库存数据快照。代码路径证明合法混合状态“应当可能存在”，但不能据此声称当前库“确实存在多少条”。

重新开门禁前应由受控 DBA/运维流程只返回以下脱敏聚合计数，不返回库存明细、用户信息或业务敏感字段：

- 总库存行数；
- `quantity=1,pieces=0` 行数；
- `quantity=1,pieces>0` 行数；
- `quantity>1,pieces=0` 行数；
- `quantity>1,pieces>0` 行数；
- NULL、负数、`pieces>=pieces_per_pallet`、缺产品/库位、每板件数无效的行数。

## 6. `total_weight_kg` 权威规则审计

当前状态：**BLOCKED**，且必须等待 `inventory_equivalent_pieces=CONFIRMED`。

### 6.1 候选口径

- 当前配置中的单件重量字段是 `product.weight_per_piece`，数据库类型为 `decimal(10,2)`，业务注释为 kg/件。
- `inventory` 表没有单件重量、每板件数或产品版本快照。
- 当前库存汇总和分布都连接当前 `product` 配置计算重量，因此产品配置变化会改变同一批现存库存的计算重量。
- 产品更新接口允许修改 `weight_per_piece` 和 `pieces_per_pallet`；更新 DTO 没有与创建 DTO 同等的正数校验。

在业务明确接受前，唯一可描述的候选公式是：

```text
total_weight_kg = confirmed_equivalent_pieces * current product.weight_per_piece
estimatedByCurrentProductConfiguration = true
```

它只能称为“按当前产品配置估算的当前库存重量”，不能称为入库时真实重量或历史重量。

### 6.2 NULL、0、非法值与精度

- `weight_per_piece` 为 NULL、0 或负数时，应返回 `DATA_QUALITY_ERROR` 或明确 limitations，不能 `COALESCE` 为 0，也不能让 SQL `SUM` 静默忽略该库存行。
- 不同包装和规格必须通过不同受控产品记录处理；模型不能自由换算单位或合并异构规格。
- 建议计算使用 `BigDecimal`，行级不舍入，聚合后仅显示层保留 2 位、`HALF_UP`。这是待审批候选，不是已冻结规则。
- 当前入口展示有的去尾零、有的前端 `toFixed(2)`，尚未形成单一舍入契约。

如果业务要求入库时真实重量，必须先增加库存或入库批次重量快照/版本关系；不能用当前产品重量回溯历史事实。

## 7. 库存读取权限矩阵

| 入口/能力 | Controller 权限 | Service 数据范围 | Mapper 范围条件 | Agent Gateway / 专家白名单 | 实际部署假设 |
|---|---|---|---|---|---|
| `get_inventory_overview` → `GET /api/inventory/stock/page` | 无方法级注解；全局 `anyRequest().authenticated()` | 未接收当前用户或数据范围 | 产品状态/名称/分页，无组织或用户仓库范围 | Agent 会话需 `mcp:warehouse:read`；仅 `inventory_expert` 可调用 | 未确认单组织、未确认全仓可见 |
| `get_inventory_distribution` → `POST /api/inventory/distribution` | `isAuthenticated()` | 接收产品/库位查询范围，但不是权限范围 | 可选 ALL 或单库位；无用户范围注入 | 会话 scope、只读路径白名单、`inventory_expert` 白名单 | 未确认；ALL 可能扩大到全部库位 |
| `query_inventory_ledger` → `POST /api/inventory/agent-read/ledger/query` | `inventory:view` | 未接收当前用户或数据范围 | 产品/库位/筛网/状态/日期/分页，无组织范围 | 会话 scope、只读路径白名单、`inventory_expert` 白名单 | 未确认 |
| inventory summary 页面 → `POST /api/inventory/summary` | `record:query` | 未接收当前用户或数据范围 | 查询条件与分页，无组织范围 | 非该 MCP 工具直接入口 | 未确认 |
| 库位库存明细页 → `/qualified-inventory/{warehouseId}` | 无方法级注解；仅全局登录 | 由请求 warehouseId 选择 | 指定 warehouseId，但未证明用户可访问该库位 | 部分现有工具可经受控路径调用 | 未确认 |
| 未来 `inventory_current_snapshot_v1` | 不存在 | 不存在 | 不存在 | 不存在 | 不得实现 |

已有安全边界值得保留：内部 Agent 网关验证服务密钥、活动会话、同一用户、`mcp:warehouse:read`、只读路径和专家工具白名单，MCP 下游请求带受控会话令牌。但这些是“可否调用工具”的边界，不能替代业务权限和行级数据范围。尤其 `mcp:warehouse:read` 不能自动证明用户有 `inventory:view`，查询参数中的 `warehouseScope=ALL` 也不能当作权限注入。

### 7.1 未确认项

当前代码和文档不能证明：

1. 当前部署是单组织；
2. 所有登录用户都能查看全部库存；
3. 是否存在按仓库、库区、角色、班组、人员或厂区的数据范围；
4. 未来是否会多厂区或多组织；
5. Agent 应与页面同权还是更严格。

数据库 RBAC 迁移中出现权限分配，不等于实际部署与业务政策确认。Service/Mapper 没有用户范围注入，不能把“尚未实现行级权限”误写成“业务上无需行级权限”。

此外，Capability Registry 对 `query_inventory_ledger` 明确登记了 `inventory:view`，但 `get_inventory_overview` 与 `get_inventory_distribution` 的工具说明段没有同等明确的业务权限字段；`laibin.sql` 中库存汇总视图使用 `SQL SECURITY DEFINER`，也不包含用户或组织范围。这些都不是直接漏洞结论，但说明权限契约尚未成为单一、可测试的权威来源。

### 7.2 统一权限契约草案

契约草案状态为 `BLOCKED`：

```yaml
capability: inventory_read
requiredPermissions: null
requiredPermissionsProposal:
  allOf: [inventory:view]
deploymentScopeMode: UNCONFIRMED
scopeInjectionOwner: JAVA_BACKEND
scopeSource: PENDING_AUTHENTICATED_USER_DATA_SCOPE_POLICY
agentPlanMaySpecifyScope: false
agentPlanMayNarrowScope: true
permissionDeniedCode: INVENTORY_READ_PERMISSION_DENIED
scopeUnresolvableCode: INVENTORY_DATA_SCOPE_UNRESOLVED
failClosed: true
```

建议将 `inventory:view` 作为统一库存读取权限候选，并显式决定 `record:query` 的迁移/兼容方式；不建议把 `isAuthenticated()` 保留为语义查询权限。若业务确认当前为全局单组织，也应由 Java 根据部署策略注入 `GLOBAL_SINGLE_ORG`，而不是让模型选择 ALL。若存在仓库级数据范围但 Java 无法确定性注入，语义查询必须继续 NO_GO。

## 8. 现有工具与页面口径差异矩阵

| 入口 | 数量来源与公式 | 重量来源与公式 | 与候选规则关系 | 主要限制/风险 |
|---|---|---|---|---|
| `get_inventory_overview` | `v_warehouse_inventory_summary` 先以 `pieces>0` 排除 `quantity`，后续使用 `total_quantity*每板件数+total_pieces` | 当前 `product.weight_per_piece` | 数量与候选公式等价 | MCP 汇总只汇总本次分页 records；重量是当前配置估算 |
| `get_inventory_distribution` | 直接对库存行计算 `quantity*每板件数+pieces` | 同一错误件数表达式 × 当前单件重量 | **冲突：合法散件行会多算一板** | `pallet_count` 是非空 `pallet_code_id` 去重数，不是全部占位/库存行数；权限仅登录 |
| `query_inventory_ledger` | 返回原始 `quantity/pieces`，不折算 | 不返回权威汇总重量 | 可作为逐行对账输入 | 普通用户可能误把 quantity 与 pieces 相加；需受控对账器解释 |
| inventory summary 页面 | 视图先排除散件行 quantity；Service/Mapper 再做板+件 | 当前产品单件重量 | 数量与候选公式等价 | 权限为 `record:query`；无重量快照；展示舍入未统一 |
| 库位库存明细汇总 | `SUM(CASE WHEN pieces>0 THEN 0 ELSE quantity END)` + `SUM(pieces)` | 页面不形成统一权威重量 | 与候选公式等价 | 仅按指定库位；无用户库位范围证明 |
| 普通出库/托盘出库 | `pieces>0 ? pieces : quantity*每板件数` | 出库时用当时查询到的产品单件重量写事件总重量 | 与候选公式等价 | 旧版 `quantity>1,pieces>0` 会产生歧义；事件重量不能还原当前库存重量 |

这里需要纠正一个容易过度简化的判断：`InventorySummaryMapper` 表面上也是“板+件”，但它读取的视图已经把散件行的 `quantity` 归零，因此不能仅凭公式外观判定它与 distribution 同样重复累计。真正冲突的是直接读取 `inventory` 的 distribution SQL。

另一个命名风险是 `get_inventory_distribution.palletCount`：SQL 使用 `COUNT(DISTINCT i.pallet_code_id)`。没有托盘码的合法库存行不会计入，故当前 UI 中“共 N 个托盘”不能直接视为物理托盘/占位总数。该字段不应在未确认前作为首版 `pallet_count` 权威指标。

## 9. 是否需要数据修复或迁移

当前结论是：**不批准立即迁移，也不能判定无需迁移。**

顺序应为：

1. 业务负责人确认字段语义、合法状态和历史兼容规则；
2. 受控运维对当前部署库只做脱敏聚合画像；
3. 对账 `overview`、`distribution`、`ledger`、summary 和库位明细；
4. 修复直接聚合公式和错误命名后，再判断是否存在需要迁移的无效数据。

若画像只发现 `quantity=1,pieces>0`，这是候选合法散件状态，不得迁移为 `quantity=0`。只有 NULL/负数、`pieces>=pieces_per_pallet`、`quantity>1,pieces>0` 等经业务确认的无效或歧义状态才进入修复清单。建议先告警和隔离，再做可审计、可回滚的数据修复；本轮没有执行任何数据查询或修改。

## 10. 语义查询需求重新评估与门禁

### 10.1 需求门禁

| 门禁 | 当前证据 | 结果 |
|---|---|---|
| 至少 8 个有真实证据的语义查询模式 | 0 个 | FAIL |
| 或真实有效问题至少 15% 需要受控动态组合 | 0/9；且 9 条不是代表性客户样本 | FAIL |
| 或有低频但高价值、登记报表无法覆盖的关键场景 | 未观察到 | FAIL |
| 件数规则 `CONFIRMED` | `BLOCKED` | FAIL |
| 至少一个有业务价值的数值指标可启用 | `inventory_record_count` 技术上可数但业务价值未证；件数/重量被阻断 | FAIL |
| 权限契约 `CONFIRMED` | `BLOCKED` | FAIL |
| 能与现有工具完整对账 | 可设计，但 distribution 已知不一致且部署数据未取样 | FAIL |

### 10.2 重新判断

- 高频失败是否来自 Router：当前自测证据中是，7/9 都可归入 Router、上下文或工具契约。
- 是否已有工具可完成但未正确调用：是，产品详情、角色权限、产品化验指代、库位容量/流转、订单领料追问和受控复合配方均已有能力。
- 是否真正存在反复出现的动态指标/维度需求：没有证据。
- 是否只需改进过滤条件上下文：当前证据甚至还没到这一层；主要是实体和受控引用上下文。
- 是否应先扩现有工具参数：没有足够重复模式，不建议现在扩 schema。
- 是否应先做登记报表：也没有足够证据，不建议预建 3～5 张。

## 11. 最终 GO / CONDITIONAL_GO / NO_GO

**M2-0.1 最终结论：NO_GO。**

- 不进入 M2-0.2 离线固定编译器实验；因此本报告不提出 M2-0.2 的实现范围。
- 生产 Runtime 语义查询继续 NO_GO。
- 这不是永久否决语义查询，而是拒绝在需求、数量口径和权限均未闭环时以技术实验替代业务确认。

重新评审前只建议完成以下非 M2-0.2 工作：

1. 由 WMS 业务负责人签署 `inventory_equivalent_pieces` 的合法状态、公式与历史兼容规则；产品、安全和部署负责人签署统一读取权限契约。
2. 由受控 DBA/运维返回当前部署库的脱敏聚合画像；不返回业务明细。
3. 在单独的 Runtime 修复任务中处理 Router/上下文清单，尤其补上真实 `productLabel` 规格后缀的复合配方契约测试；然后进行 Java Gateway + MCP + 浏览器端到端复测。
4. 在上线前 UAT 或小范围试用中按角色采集问题、期望结果、失败原因和重复频率；真实问题与测试问题继续分库存放。
5. 只有重新满足需求门禁、件数规则 `CONFIRMED`、至少一个高价值指标可用、权限契约 `CONFIRMED`、对账可闭环后，再决定是否重开 M2-0.2。

## 12. 主要证据索引

- 产品目标与边界：`docs/agent/ai-assistant-product-goal.md`
- Tool Registry：`docs/mcp/mcp-tool-registry.md`、`docs/agent/tool-capability-registry.yaml`
- 前阶段审计：`docs/agent/m2-0-inventory-semantic-query-feasibility.md`
- Review 安全边界：`docs/agent/agent-answer-review-plan.md`
- 单二维码规则：`docs/方案/2026-04-27-single-qr-single-inventory-rule.md`
- 散件路径审计：`docs/方案/2026-04-28-finished-inbound-semi-consumption-loose-piece-audit.md`
- 库存汇总视图：`laibin.sql` 中 `v_warehouse_inventory_summary`
- 数量写路径：`SemiProductRecordServiceImpl`、`PalletInventoryOccupancyRule`
- 数量读/出库路径：`InventorySummaryMapper`、`InventoryMapper`、`InventoryDistributionSqlProvider`、`OutStockServiceImpl`、`PalletCodeServiceImpl`
- 权限入口：`InventoryController`、`InventoryAgentReadController`、`SecurityConfig`、`AgentSessionServiceImpl`、`McpInternalAgentToolGatewayService`
- 浏览器 UAT 脱敏证据：[inventory-real-question-evidence-v1.json](evaluation/inventory-real-question-evidence-v1.json)
- 规则与权限契约：[inventory-metric-permission-contract-v1.yaml](inventory-metric-permission-contract-v1.yaml)
