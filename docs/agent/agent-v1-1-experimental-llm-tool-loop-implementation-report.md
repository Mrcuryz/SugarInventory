# Agent V1.1 Experimental LLM Tool Loop 实施报告

日期：2026-07-16

结论：`READY_FOR_LOCAL_MODEL_UAT`，生产 `NO_GO`

## 修改范围

- Python Runtime 新增 `AGENT_PLANNING_MODE=deterministic|llm`；默认 `deterministic`。
- `llm` 模式仅允许 inventory、warehouse、assay 三个实验专家。
- 新增严格的主模型动作和专家循环动作模型。
- 专家循环最多 3 次工具调用、1 次 retryable 重试、1 次模型参数纠正。
- 模型可见工具 schema 将 `productId` / `warehouseId` 替换为 Runtime 状态引用。
- resolver 多候选继续复用现有 HITL；恢复时校验原专家和权限上下文。
- 工具结果形成仅用于本轮循环的安全 observation；没有建设 FactEnvelope 平台。
- 现有登记跨域配方保持确定性执行，模型只能提出已登记 recipeId。
- production 环境启用实验模式会启动失败。

没有修改：

- 47 个 MCP 工具及其 schema；
- Java Gateway 权限；
- MCP 专家白名单；
- Router deterministic 主路径；
- 前端业务卡片体系；
- Semantic Query、SQL、HTTP、写工具、preview、execute；
- inventory 数量业务公式。

## 已覆盖的真实问题

- 产品查询后继续库存读取，而不是结束在 resolver。
- 明确库位问题可以清除旧产品过滤，避免上下文过度继承。
- 产品展示标签不能被模型重新作为 resolver 权威参数。
- 多候选产品选择后恢复原专家工具循环。
- 模型生成 raw ID 或跨专家工具会被 Runtime 拒绝。
- 工具错误不能作为 `NO_DATA` 完成证据。

## 最小协议版本

- `MainAgentDecisionV1`：1.0
- `ExpertLoopDecisionV1`：1.0
- Runtime state refs：`CURRENT_PRODUCT`、`CURRENT_WAREHOUSE`

这些是实验执行协议，不代表恢复 GoalContract、FactEnvelope 或 FollowupAction 平台建设。

## 目标完成规则

- `FINAL_ANSWER` / `PARTIAL_ANSWER` 必须引用实际 observation。
- 只引用 `TOOL_ERROR`、`PERMISSION_DENIED`、`PLAN_REJECTED` 或 `AMBIGUOUS` 不能形成业务结论。
- 未解决 `TOOL_ERROR` 时不能 `FINAL_ANSWER`。
- 有未解决错误的 `PARTIAL_ANSWER` 必须明确说明失败，不能写成无数据。
- 权限拒绝由 Runtime 立即输出确定性拒绝，不交给模型改写。
- 达到工具预算后停止，不自动扩大范围或切换专家。

## 测试

新增实验测试覆盖：

- 默认关闭和预算硬上限；
- 模型可见 schema 不含数据库 ID；
- `resolve_products → get_inventory_overview → FINAL_ANSWER`；
- 明确库位查询清除旧产品范围；
- displayLabel 执行参数拒绝；
- raw ID 拒绝后一次纠正；
- 工具错误不能解释为无数据；
- resolver HITL 恢复；
- 跨专家工具拒绝；
- OpenAI-compatible / DeepSeek-compatible JSON 动作解析。

当前结果：

```text
Python Agent: 189 passed
```

Java 根项目 `203` tests、warehouse-mcp `50` tests、前端生产构建和前端请求错误单测 `4` tests 均通过。

## 首轮真实 DeepSeek UAT 缺陷修复

5174 首轮真实浏览器测试暴露了两类问题：resolver 已成功后专家结构化动作失败，以及完整 Agent 轮次超过 15 秒。代码审计确认：

- `REQUEST_TIMEOUT_MS` 被同时用于单个 Java 工具请求和整轮 SSE，无法覆盖主模型、专家模型、resolver、业务工具和最终判断的串行耗时；
- 结构化动作客户端吞掉超时、HTTP、JSON 和 Schema 错误并统一返回 `None`，只能得到“未返回可校验动作”；
- 专家模型已生成最终答案后，SSE 又调用一次模型重写并流式输出，增加了无业务价值的延迟和失败点；
- Java/MCP 链路 401 被 LLM 循环误归类为用户业务权限不足。

修复后：

- `REQUEST_TIMEOUT_MS` 只约束单次 Java 工具请求；新增 `AGENT_RUN_TIMEOUT_MS`，LLM 模式默认 90 秒、确定性模式默认 20 秒；
- LLM 模式直接发送已经校验的专家最终答案，不再做第二次模型改写；
- JSON 或 Schema 不合格时只允许一次格式修复，第二次仍失败则 fail-closed；
- 诊断只保留阶段、尝试次数、耗时、HTTP 状态和校验字段路径，不保存模型原文、推理内容、参数或凭据；
- 认证失败、权限拒绝、模型超时、模型格式错误和工具超时分别返回不同错误类别。

修复后的真实浏览器复测仍是本地 UAT 的必要门禁，不能仅凭单元测试声明完成。

## 第二轮 5174 UAT：配方误判与运行包契约漂移

第二轮浏览器测试确认首轮的结构化动作与整轮超时问题已经消失，同时暴露两个新的、彼此独立的问题：

- “1号库位库存及这些产品的最新化验”库存步骤成功，但化验扇出报告缺少 `canonicalProductName`。源码和单测已包含该字段，实际被 Java 会话拉起的 `warehouse-mcp-0.1.0-exec.jar` 却早于字段变更，属于运行包契约漂移，不是数据库或产品规格数据缺失；
- “只看黄冰糖”和“它最新化验怎么样”被主模型误判为登记跨域配方。Runtime 的配方拒绝是正确安全行为，但一次误判直接终止会话造成了不必要的用户失败。

本轮最小修复：

- 重建并核验实际 MCP 可执行 JAR，其中 `InventoryDistributionGroup` 已包含 `canonicalProductName()`；继续禁止从 `displayLabel` 反解析产品；
- 主模型只有在当前消息本身同时请求库位库存和这些产品的化验时才可提出登记配方；
- Runtime 仍用现有确定性配方匹配器验证。若模型误提配方，只允许一次受控重路由，第二次请求不再暴露配方，并携带脱敏拒绝原因；若仍重复提出配方则 fail-closed；
- 不新增工具、不扩大专家白名单、不开放跨域自由调用，也不使用 Router 替模型静默决定专家。

新增回归覆盖短产品筛选、化验指代追问和真实登记复合请求。全量结果为 Python Agent `189 passed`、Java 根项目 `203` tests、warehouse-mcp `50` tests、前端请求错误单测 `4` tests，前端生产构建通过。

## 未闭环风险

1. Shadow 实测单次模型延迟约 5～10 秒；移除重复最终模型调用后，含 resolver 的典型任务仍有三次模型往返，延迟可能不可接受。
2. 真实 DeepSeek 严格 JSON 稳定性尚未形成统计；一次格式修复只提高容错，不代表可以放松 Schema 或 Runtime 校验。
3. 普通工具输出尚无统一 output schema；目前只批准三个实验专家，不能据此开放其余六个专家。
4. `CURRENT_PRODUCT` / `CURRENT_WAREHOUSE` 是会话内状态绑定，不是新的跨系统 signed entityRef。
5. 库存到化验的源码与最新 MCP 运行包均已包含 `canonicalProductName`，但新会话上的真实浏览器复测仍是部署闭环门禁。
6. 最终回答事实一致性与本轮修复后的浏览器稳定性仍需真实 UAT 评测，尤其是“最新化验”、历史趋势和库存批次合格等夸大风险。

## 是否建议下一步

建议进入本地 DeepSeek UAT，但不建议扩大专家范围，也不建议替换 Router 默认路径。

只有真实 UAT 同时证明目标完成率提升、错误边界 100% 通过且延迟可接受，才讨论：

- 是否继续保留主模型独立路由调用；
- 是否开放另外一个专家域；
- 是否让 `llm` 成为 UAT 默认，而不是生产默认。
