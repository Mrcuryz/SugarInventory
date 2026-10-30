# 受控 LLM Agent 生产化决策与验收

日期：2026-08-29  
状态：`DEPLOYED_AND_VERIFIED`

## 1. 问题与结论

服务器首次部署后，“今天整体运营情况如何？”返回了旧的只读查询能力说明。代码审计确认：

- 运行包已经包含 `TODAY_OPERATIONS_OVERVIEW` GoalContract、`analytics_expert` 和
  `today_operations_overview_v1` 登记报表；
- 线上模型模式和模型密钥均已配置；
- 生产部署却固定使用 `AGENT_PLANNING_MODE=deterministic`；
- 该模式仍从旧 IntentRouter 进入 `BasicModelClient` 兜底，没有执行主模型委派和专家循环；
- 当时的部署文档把 deterministic 描述成模型主导的受控执行图，与实际代码不一致。

因此问题不是补一条 Prompt，也不是用户部署操作错误，而是部署基线与已经验收的 Agent 架构发生漂移。

生产环境正式采用现有受控 `llm` 运行路径：

```text
用户输入
  → 主模型理解目标并选择已批准专家
  → Runtime 校验 GoalContract、专家归属和工具边界
  → 专家模型一次提出一个工具动作
  → Runtime / Java Gateway / MCP 校验并调用工具
  → 专家模型仅根据受控 observation 分析结果
  → Runtime 校验事实引用和完成状态
  → 用户可读文本与业务卡片
```

不新增关键词 Router，不为报表问题增加特殊硬编码旁路。

## 2. 生产启动门禁

生产环境必须同时满足以下条件，否则 Agent 拒绝启动：

1. `AGENT_PLANNING_MODE=llm`；
2. `AGENT_PYTHON_MODEL_MODE=openai_compatible`；
3. `AGENT_TOOL_MODE=java_gateway`；
4. `AGENT_STATE_BACKEND=redis`；
5. `AGENT_ALLOW_INSECURE_MOCK_AUTH=false`；
6. 模型地址、模型名称和模型 API Key 均已配置；
7. Python 服务密钥和 Java 工具网关密钥均已配置；
8. 每轮最多 3 次工具调用、1 次受控重试；
9. 受控专家集合完整且不存在未批准专家。

当前批准专家为：

- `inventory_expert`
- `warehouse_expert`
- `assay_expert`
- `logistics_expert`
- `production_expert`
- `analytics_expert`
- `pallet_expert`
- `knowledge_expert`
- `master_data_expert`
- `administration_expert`
- `audit_expert`

`deterministic` 仍可用于开发环境的对照回归，但不能作为生产降级模式。出现模型或配置故障时应明确失败，
不能向用户展示一个功能范围缩水但表面正常的旧 Agent。

## 3. 不变的安全边界

- 主模型不能直接选择业务工具或提交数据库 ID；
- 专家只能看到自身白名单和当前 GoalContract 允许的工具；
- Runtime 继续校验工具预算、参数 Schema、受控 EntityContext、HITL 和完成条件；
- Java Gateway 与 MCP 继续做最终权限和业务范围校验；
- 工具错误、权限拒绝和无数据必须区分；
- 未登记跨域配方、任意 SQL、任意 HTTP 和未批准写操作继续禁止；
- 成品入库试点的预览、确认、幂等、审计和真实执行边界不因本次生产化而放宽。

## 4. 部署要求

生产 `.env` 至少包含：

```dotenv
AGENT_PYTHON_MODEL_MODE=openai_compatible
AGENT_PLANNING_MODE=llm
AGENT_LLM_ALLOWED_EXPERTS=inventory_expert,warehouse_expert,assay_expert,logistics_expert,production_expert,analytics_expert,pallet_expert,knowledge_expert,master_data_expert,administration_expert,audit_expert
```

部署必须重建 Agent 镜像，不能只修改环境变量后继续使用带有“生产禁止 llm”门禁的旧镜像。

## 5. 上线验收

至少验证以下代表性流程：

1. “今天整体运营情况如何？”：委派 `analytics_expert`，运行
   `today_operations_overview_v1`，返回今日运营卡片；
2. “今天产量如何？”：返回生产登记产出日报，不要求生产订单号；
3. “查询黄冰糖（袋）库存”：产品解析后由库存专家查询；
4. “查询黄冰糖（袋）今天的化验”：由化验专家返回详细化验卡片；
5. “查询最近一个月的煮糖批次”：由生产专家完成范围查询；
6. “查询成品产品目录”：由 `master_data_expert` 返回产品主数据，不误判为库存；
7. 工具超时、模型格式错误和权限拒绝：明确失败，不回退旧 Router，也不解释为无数据。

验收证据必须保留输入、用户输出、卡片、模型主路由、专家、工具调用和最终状态；不得保存模型原始推理、
API Key、内部服务密钥或未脱敏业务参数。

## 6. 回滚原则

若新版本发生 P0 问题，应回滚完整 Agent 版本和对应部署配置。禁止仅把
`AGENT_PLANNING_MODE` 改回 `deterministic` 继续对用户提供服务，因为这会再次造成演示能力与生产能力不一致。

## 7. 隔离环境验收记录

正式切换前使用独立端口、独立 Redis DB 和单专家白名单进行真实 Java Gateway + 模型联调，线上 Agent 未参与测试。

已通过的代表性路径：

- “今天整体运营情况如何？”：`analytics_expert` → `today_operations_overview_report`；
- “今天产量如何？”：`analytics_expert` → `daily_production_report`；
- “最近7天产品质量如何？”：`analytics_expert` → `quality_assay_trend_report`；
- “查询黄冰糖（袋）当前库存总览”：`inventory_expert` → 产品解析 → 库存总览；
- “查询各库位容量使用情况”：`warehouse_expert` → 库位容量分布。

隔离验收同时发现模型曾混淆“单次最新化验”“指定日期化验”和“历史化验”：

- 具体产品 + 指定日期，必须使用 `PRODUCT_ASSAY_REPORT`，并调用 `get_assay_status`；
- “最近/最新一次化验”仍属于 `PRODUCT_ASSAY_REPORT`，先以 `query_assay_records` 取最新一条，再调用
  `get_assay_report_detail`；
- 只有明确的历史、记录列表、最近 N 天或日期范围查询，才使用 `PRODUCT_ASSAY_HISTORY`；
- 质量趋势、合格率和变化比较继续属于分析报表，不得误路由为历史明细。

Runtime 对上述边界进行 GoalContract 级纠偏，`query_assay_records` 在单次报告中仅作为辅助工具，不能单独形成
“化验详情已完成”的证据。相关修复已通过 Agent 全量回归：504 项通过，5 项按环境条件跳过。修复后的真实模型
隔离复测通过后，才能进入其余专家验收和生产切换。

最终隔离验收覆盖了全部 11 个批准专家。除上述路径外，生产批次、待处理任务、已打印未入库二维码、产品目录、
流程知识、角色目录和 Agent 工具审计均完成“主模型路由 → 对应专家 → 白名单工具 → 结果分析/安全格式化”的真实
模型验证。工具能力清单仅开放 L0-L2 查询与预览工具，未向本次受控 LLM 白名单开放执行型写工具。

## 8. 生产切换与公开入口验证

2026-08-29 已将腾讯云线上 `sugar-agent` 从旧 `deterministic` 镜像切换到受控 LLM 镜像。切换后：

- `planningMode=llm`；
- 11 个批准专家白名单完整；
- 模型、Java Gateway、Redis 状态均为 `UP`，RAG 为 `READY`；
- 容器启动后重启次数为 0；
- 隔离 canary 容器已在验收结束后删除；
- deterministic 回滚镜像和切换前配置、wheel 备份仍保留在服务器。

通过公开域名、真实登录、Java Agent 会话接口完成以下端到端验证：

| 输入 | 结果 | 模型决策 | 工具调用 | 总耗时 |
| --- | --- | ---: | ---: | ---: |
| 今天整体运营情况如何？ | 返回 `today_operations_overview_report` 卡片 | 3 | 1 | 约 10.7 秒 |
| 查询黄冰糖（袋）当前库存总览 | 正确进入库存链路并返回无数据事实 | 3 | 2 | 约 11.1 秒 |
| 查询黄冰糖（袋）2026-07-17的化验情况 | 正确进入单日化验报告链路 | 3 | 2 | 约 9.0 秒 |
| 查询2026-06-30的全部产品煮糖批次 | 正确进入生产批次查询链路 | 3 | 1 | 约 9.3 秒 |

另通过前端实际使用的 `/messages/stream` SSE 通道复验“今天整体运营情况如何？”，依次收到
`message_start`、处理进度、调试摘要、报表卡片、增量文本和 `message_end(completed)`；报表卡片类型仍为
`today_operations_overview_report`。因此非流式兼容接口与用户页面的流式接口均已验证。

线上不再出现旧 Agent 的“当前只支持库存、库位、托盘、化验和生产订单进度”能力说明。生产数据为空时，回答必须
明确表示“系统当前已登记数据为空”，不能把无数据解释为真实业务没有发生。
