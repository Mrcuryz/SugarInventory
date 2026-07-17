# Agent v1 只读 MCP 最终验收报告

验收日期：2026-07-14  
验收范围：9 个业务专家、无业务工具的主 Agent、47 个 L1 只读 MCP 工具、Java/Python/MCP 边界、固定数据契约、前端构建与真实浏览器 E2E。

## 结论

当前版本已达到 **Agent v1 隔离验收环境 GO**，可以冻结功能并制作同版本候选发布包。

甲方正式生产部署仍为 **NO-GO**。这不是功能验收失败，而是项目当前明确不进入甲方服务器，且尚未完成同版本发布包、Redis、密钥、回滚和监控演练。不得把本报告解释为已批准生产上线。

已确认：

- 9 个业务专家均配置真实 L1 工具：inventory 6、warehouse 5、assay 12、pallet 9、production 7、logistics 4、master_data 3、administration 3、audit 3；共享解析工具去重后的并集为 47。
- `main_agent.allowedTools` 为空；Java Gateway 对主 Agent、未知专家和跨专家工具调用均 fail-closed。
- Python、Java Gateway、warehouse-mcp 和 Tool Capability Registry 的工具集合一致。
- 启动握手同时校验协议、工具清单、配方清单及完整专家权限映射；专家权限漂移会阻止启用 Agent。
- 47 个工具均为 L1；未发现 `preview_*`、`execute_*`、任意 SQL/HTTP 或业务写工具。
- 本轮只连接隔离本地验收库，未连接生产数据库，未修改库存数据。

## 验收证据

| 层级 | 验收项 | 结果 |
| --- | --- | --- |
| Python Runtime | 专家边界、路由、HITL、受控编排、safe formatter、capability hash | `139 passed` |
| Java 根项目 | Gateway 最终授权、握手、委托路径、权限矩阵、聚合服务等全量单测 | `195 passed` |
| warehouse-mcp | 47 工具注册、schema、47 个 callback 参数绑定与调用 | `50 passed` |
| 固定数据契约 | Python + warehouse-mcp 固定数据集成脚本 | 通过 |
| 前端 | `npm run build` | 通过；保留大 chunk 与 Sass legacy API 警告 |
| 构建卫生 | 旧 build 代码、加载顺序、Registry/OpenAPI 检查 | 通过 |
| OpenAPI | Gateway 上游路径与 `docs/openapi.json` 对照 | 55 条路径全部存在 |
| 真实浏览器 E2E | HITL/SSE/刷新/安全 7 条 + 9 专家 canary | `16 passed` |
| 工具审计 | 9 专家 canary 的工具状态和 `expertAgent` | 均为 `SUCCESS` 且专家归属正确 |

## 已关闭的原 P0 阻断项

1. **Java 专家到工具授权**：Java 维护确定性专家白名单；缺少专家、未知专家、主 Agent 或跨专家调用返回安全错误，且不进入 MCP。
2. **专家映射握手**：新增 `agentProfileRegistryHash`，规范化覆盖专家名、domain 和排序后的 `allowedTools`；Java/Python 算法一致并 fail-closed。
3. **真实 16 条 E2E**：已在隔离本地验收库执行，数据库审计断言未关闭，全部通过。
4. **细粒度只读权限**：Agent 聚合接口已收口到 `inventory:view`、`warehouse:view`、`document:view`、`task:view`、`qrcode:view`、`qrcode:pool_view`、`assay:view` 等专用权限；新增确定性权限矩阵测试。
5. **OpenAPI 漂移**：已导出 OpenAPI，并增加 Gateway 55 条上游路径自动对照脚本；同时修正 `get_warehouse_status` 路径文档漂移。
6. **只读事务**：新增的 Agent read service 实现已统一增加 `@Transactional(readOnly = true)` 防御层。

## 剩余风险

以下不阻断 Agent v1 隔离验收，但应进入发布或后续治理清单：

1. `quality_expert` 是蓝图语义名，运行时兼容 ID 仍为 `assay_expert`；正式重命名必须走版本化 Registry 变更。
2. 操作日志并非覆盖全部底层读路径；“未查到操作日志”不能解释为“没有发生操作”。Agent 工具审计链路已单独验证。
3. 库存批次与化验缺少稳定关联，不能回答“当前库存批次是否合格”；历史趋势、混放风险、FIFO/FEFO 和生产完成判定仍需事实/快照/规则层。
4. 前端主 chunk 约 1.27 MB，且 Sass legacy API 有弃用警告；当前不阻断 L1 功能，但需要独立性能与依赖升级任务。
5. 正式生产部署前仍须使用同一版本组合交付 Java jar、Python wheel、warehouse-mcp exec jar 和前端 dist，并完成 Redis、Secret、回滚、监控与故障注入演练。

## 放行边界

- **允许**：继续在隔离验收环境 smoke、冻结 Agent v1、制作候选发布包、完善部署自动化。
- **不允许**：连接生产库验证、启用写工具、绕过 Java Gateway/RBAC/HITL/审计、模型自由生成 DAG、专家自由互聊，或据此直接宣布甲方生产上线。
- V2 必须先建设事实、快照、指标和版本化规则层，再进入登记式 v2-A 报表/preview；完成 executionToken、幂等、版本重检、审批和审计后才可进入 v2-B execute。
