# Agent V1.1a 实施报告

日期：2026-07-15  
结论：`IMPLEMENTED_ROUTER_FIX_PENDING_BROWSER_RERUN`  
下一切片：`V1.1b_NO_GO_UNTIL_BROWSER_RETEST`

## 1. 修改范围

- Java 库存分布组新增只读输出 `canonicalProductName`，值直接来自查询行的产品名称字段。
- warehouse-mcp 仅透传新增响应字段；没有新增工具，也没有修改任何工具输入 schema。
- Python safe adapter 将规范名与 `productLabel`/`groupLabel` 分开。
- 库存到化验复合配方只把 `canonicalProductName` 交给 `resolve_products`。
- 规范名缺失时不解析展示标签，记录 `ENTITY_CONTEXT_MISSING`，目标结果降为 `PARTIAL_SUCCESS`。
- 库存分布卡片的既有“补充化验”展示动作只使用规范名；缺失时不生成该动作。
- 根据 2026-07-15 业务确认，冻结一板一码口径：不足满板时 `pieces` 是该板实际件数，统计不得再叠加整板。
- 修正三个当前库存聚合入口，并移除已经失效的“口径未确认”提示。
- 产品存在当前库存时，禁止修改 `pieces_per_pallet` 和 `weight_per_piece`；不同规格必须新建产品。
- 真实浏览器原句“1号库库存及这些产品最新化验”暴露复合意图短语缺口；在既有登记配方边界内补充 `最新化验`、`最近化验`、`最新质检` 三个受控短语，并增加失败优先测试。
- RBAC、工具数量、专家数量和复合配方数量均未改变；Router 仅修复上述已证实的窄范围缺口。

## 2. 修复的真实用户问题

浏览器验收曾出现以下链路：

```text
1号库位库存分布
→ 产品展示为“黄冰糖（袋） 40kg/件 25件/板”
→ 继续查询最新化验
→ resolver 收到整个展示标签
→ 产品无法唯一匹配或查不到
```

修复后，展示仍保留完整规格，但执行参数只能是 Java 给出的规范产品名。测试明确验证 resolver 收到 `黄冰糖（袋）`，不包含 `40kg/件` 或 `25件/板`。

第一次手工浏览器复测又发现：

```text
1号库库存及这些产品最新化验
→ 只调用库存分布
→ 没有进入已登记的库存到化验复合配方
```

这不是实体交接失败，而是 Router 对“最新化验”紧凑表达覆盖不足。现已用真实原句建立回归测试并做窄范围修复，仍需在运行服务重启后进行第二次浏览器验收。

## 3. 新增契约及版本

V1.1a 没有提前冻结完整 `EntityContextV1`、`GoalContract` 或 `FactEnvelope`。新增的是一个最小内部交接规则：

```text
inventory distribution group handoff v1
canonicalProductName: execution candidate
productLabel/groupLabel: display only
missing canonicalProductName: stop downstream entity execution
```

这是对既有业务 DTO 的向后兼容响应扩展，不是新 MCP 工具输入契约。机器可读通用契约仍留在 V1.1b，避免把一个缺陷修复扩大为平台建设。

## 4. 目标完成规则

V1.1a 尚未实现通用 `GoalCompletionEvaluator`。当前登记复合配方的确定性状态调整为：

- 所有有限产品都有唯一解析结果，且化验事实为成功或明确 `NO_DATA`：沿用 `SUCCESS`。
- 缺少规范名、解析不唯一、工具错误或扇出截断：`PARTIAL_SUCCESS`。
- 缺少规范名不得发起 `resolve_products`。
- `NO_DATA` 仍不等同于工具错误。
- 批次级质量限制继续强制进入最终回答。

## 5. 是否改变现有工具行为

- 工具数量：仍为 47。
- 专家：仍为 9 个业务专家，`main_agent` 无业务工具。
- 登记复合配方：仍为 1 个。
- 输入 schema：未改变。
- Java 最终鉴权与专家白名单：未改变。
- `get_inventory_distribution` 响应组增加 `canonicalProductName`。
- `get_inventory_distribution`、`query_products_without_recent_assay`、`query_assay_standard_coverage` 的当前库存聚合改用已确认的一板一码公式；工具 schema 未改变，但部分板的件数和重量结果会被纠正。
- 产品更新 REST 接口在存在当前库存时拒绝实际改变每板件数或单件重量，返回错误码 `1029`；名称等其他字段仍可更新。

## 6. 新增与回归测试

新增或加强：

- 真实规格后缀不进入 resolver 参数；
- 缺少规范名时不调用 resolver，并返回局部成功；
- 多产品扇出仍最多 5；
- HITL 跨 Runtime 恢复仍保留规范名；
- Java 规范名来自产品名称字段，而非格式化标签；
- MCP 完整透传规范名；
- `quantity=1`、`pieces=10`、`piecesPerPallet=40` 时，三个聚合入口均按 10 件计算，不再得到 50；
- 有库存时修改 `x/y` 被拒绝；相同数值不同小数位不误判；无库存时使用带 `NOT EXISTS inventory` 的原子更新条件；
- Agent 不再展示已经失效的“口径未确认”限制。

回归结果：

| 门禁 | 结果 |
|---|---|
| Python Agent 全量 | 153 passed |
| Java 全量 | 203 passed，0 failure/error |
| warehouse-mcp 全量 | 50 passed，0 failure/error |
| 前端生产构建 | 通过；仅既有 Sass deprecated 与 chunk size 警告 |
| 工具/专家/握手 | 全量测试通过；`toolCount=47`、`recipeCount=1`、专家白名单集合仍受断言保护 |
| OpenAPI/Registry | 55 个 Gateway 上游路径一致 |
| diff whitespace | 通过；只有工作区既有 LF/CRLF 提示 |
| UTF-8/BOM/乱码 | 本切片相关文件未发现 BOM 或乱码特征 |
| 真实浏览器 | 第一次手工复测证明新版口径限制生效，但也发现原句未进入复合配方；Router 缺口已修复，等待服务重启后的第二次复测。自动浏览器连接再次失败，未用替代自动化冒充验收 |

## 7. 未闭环风险

1. 件数与当前库存重量规则已 `CONFIRMED` 并修复现有聚合入口；但当前部署尚未做脱敏数据画像，历史 `quantity > 1`、非法 `pieces` 或非正产品配置仍是数据质量风险。
2. 库存读取权限仍未形成统一契约；V1.1 后续动作不得自动扩大查询范围。
3. 当前规范名不是唯一键。resolver 返回歧义时会安全停止；要消除二次解析，需要未来在合适的 Agent 专用边界签发会话绑定实体引用，而不是把数据库 ID 暴露给 Python。
4. Java `AgentEntityRefCodec` 当前绑定用户、类型、权限摘要和 TTL，但尚未绑定 Agent 会话；不能直接宣称完整满足 `EntityContextV1`。
5. 第二次真实浏览器复测未完成，因此尚不能证明 Router 修复已部署并让完整库存到化验目标闭环。

## 8. 是否建议进入下一切片

当前不建议进入 V1.1b。原因不是代码单测失败，而是 V1.1a 门禁明确要求真实后端和浏览器复测；第一次手工复测发现 Router 缺口，修复后尚未完成第二次验收。

服务重启后只需完成以下最小手工复测：

1. 提问“1号库位当前库存情况如何？这些产品的最新化验怎么样？”；
2. 确认带规格展示标签仍正常显示；
3. 通过脱敏管理员执行轨迹确认 resolver 参数为规范产品名，不含规格后缀；
4. 确认不存在规范名的兼容数据返回局部结果，而不是“无数据”；
5. 确认回答不再出现“口径尚未确认”，且部分板件数没有重复累计；
6. 确认 5 个产品扇出上限和局部失败表现。

浏览器复测通过后，V1.1b 仍只应冻结三个目标、五个事实类型，并把 `SelectedEntity` 做版本化演进；不应新建通用 Entity Graph 或第二套执行计划平台。

## 9. 对原设计的批判性修正

- `EntityContextV1` 不应与现有 `SelectedEntity` 并存；应做兼容演进。
- `ReadTaskPlan` 不应成为自由 DAG 的改名版本；应扩展现有登记复合计划。
- Java、MCP、Python 不应各自生成完整 `FactEnvelope`；事实转换的单一责任应留在 Python safe adapter 后。
- 为了一个显示标签 bug 立即给所有产品签发新实体引用，会把修复扩大到 Controller、会话和 RBAC；V1.1a 使用权威规范名并在歧义时停止，是更小且可测试的方案。
- 口径未签署时不能被新架构包装成“事实”；签署后应由确定性后端统一公式并删除过期 limitation，不能继续让模型在多个候选公式之间选择。
