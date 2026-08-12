# Agent 登记、构建产物与 L3 门禁归一化

日期：2026-08-12

范围：当前 Agent/MCP 机器登记、Python/Java/MCP 白名单、运行时握手、Python 打包产物、成品入库 L3 gate

结论：当前合同已对齐；没有新增工具或写权限；L3 总体继续 `NO_GO / NOT_RELEASED`

## 1. 当前事实基线

- Runtime 已登记 58 个 GoalContract，`planned_goals=0`；
- Java Gateway 与 warehouse-mcp 共同登记 54 个无业务写入工具，其中 52 个 L1、2 个 L2；
- 两个 L2 工具是 `preview_task_transition` 与 `preview_finish_inbound_execution`；
- Python 另有 1 个进程内 L0 `search_approved_knowledge`，不进入 Java/MCP 工具清单或 tool registry hash；
- 当前仅有 1 个受控复合配方 `warehouse_inventory_latest_assay`；
- 没有登记任何 `execute_*` MCP 工具。

运行时 capability 基线为：

```text
protocolVersion=1.0
toolCount=54
toolRegistryHash=908b62b9a490fd94e925d15257ca7682e584e67accb709e8cb40d9ae73653a28
recipeCount=1
recipeRegistryHash=c5ee0907e4134024138ff5489bd9b58d92c4103663a00f0d0d14fa5a40d12385
agentProfileRegistryHash=eac298cbdbcf296cc6f93a8d835e9ccb33e96ad5fc61cdb02a7d11fb3b3c044c
```

这些值不是靠本文生效。Python `/internal/agent/capabilities`、Java `HttpPythonAgentClient` 和 MCP 启动清单必须一致，任一不一致都 fail-closed。

## 2. 修正的漂移

1. `readonly-goal-contract-registry.yaml` 和 `tool-capability-registry.yaml` 的说明仍写成 1 个 L2，现已按运行时修正为 52 L1 + 2 L2，并新增机器可读分项计数；
2. `agent-v1-progress.md` 的“当前状态”和专家数量仍停留在较早报表阶段，现已更新当前基线；历史变更日志和带日期验收文档保留当时数字；
3. `finish-inbound-l3-gate.yaml` 的 `execution_implementation_status` 和原子审计仍停在 S3 前状态，现已确认 S3 默认关闭实现及成功审计同事务本地证据完成；
4. S4 所需的真实审计故障回滚、状态变化失败路径、正式角色职责、完整浏览器恢复矩阵及发布/回滚决策改为独立机器可读待办，避免把“已有自动化脚本”误写成“已完成 UAT”。

## 3. 防再次漂移

- Python 合同逐项对比 Runtime 专家白名单、Java Gateway `ALLOWED_TOOLS`、warehouse-mcp ToolCallback 清单和 capability registry；
- Goal registry 合同锁定 58、54、52、2、1 的分项关系；
- Java 合同锁定 S3 已实现但默认关闭、S4 待验收、生产未发布且不存在 `execute_*` MCP 工具；
- 新增打包合同，对 `agent-service/app` 与 `agent-service/build/lib/app` 中受控的 Python/JSON/YAML 文件逐字节比较，并同时拒绝缺文件和多余旧文件。

## 4. 边界

本次没有修改业务接口、库存数据、角色授权、特性开关或模型可调用能力。S3 本地证据不能替代 S4，S4 完成前不得开启生产开关，也不得注册 `execute_finish_inbound_task`。

## 5. 自动化与真实浏览器验收

自动化结果：

- Agent 全量：`478 passed, 8 skipped`；跳过项是缺少可选真实语料/外部运行条件的既有测试；最后新增的 Registry 状态合同另以相关文件 `223 passed` 复验；
- Java 根项目：113 个测试套件、424 项测试全部通过；
- warehouse-mcp：2 个测试套件、72 项测试全部通过；
- 根项目与 warehouse-mcp 当前 JAR 均重新打包成功；
- `agent-service/app` 与 `agent-service/build/lib/app` 的 51 个受控 Python/JSON/YAML 文件逐字节一致。

真实浏览器使用用户指定的本地 shadow env，安全材料和 Web 验收密码只存在于子进程；未开启 S2/S3 执行开关。验收结果：

1. 临时管理员成功登录，AI 助手显示“会话已连接”；
2. 自然语言库存查询实际走完“理解用户需求 → 专家确认查询条件并决定工具 → 确认产品范围 → 分析工具返回结果 → 整理最终回答”五步；无匹配产品时明确返回无数据，没有编造库存；
3. Python 健康接口返回 `UP`，模型、Java tool gateway 和本次内存状态存储均为 `UP`；本次没有把内存状态存储写成生产证据；
4. 受保护 capability 接口在无服务密钥时返回 401；携带本次进程密钥后返回 200、`toolCount=54`、`recipeCount=1`，三个 registry hash 与上文完全一致；
5. 11 个 Java-backed profile 的工具并集为 54，仅有两个 `preview_*`，不存在 `execute_*`；Python 进程内知识工具没有混入该并集；
6. 主应用 OpenAPI 为 207 条路径，没有 execute 路径；保留的是精确预览及 S2 确认/撤销控制面，默认关闭的 S3 执行 Controller 没有暴露；
7. 主应用页面本身无 console error；诊断标签页中可见的 401 和 favicon 404 分别来自主动验证未授权访问与浏览器默认图标请求。

验收结束后已撤销 Agent 会话，关闭浏览器，停止 Java、Python、MCP 子进程和 Vite；删除临时管理员、角色、权限关系、日志、临时 uvicorn 依赖及 Playwright 产物。38082、38091、5177 均无监听，未执行任何库存、任务、流转或 L3 业务写入。
