# RAG 源码版本库集成交付清单

状态：`COMPLETED / SOURCE_INTEGRATED / ENGINEERING_REGRESSION_PASSED`
日期：2026-08-03
基线 Git HEAD：`c9e2274`
配套计划：`rag-source-integration-plan-2026-08-03.md`

## 1. 结论

当前 RAG 工作区实现不能作为一个完整目录直接提交。审计将交付内容分为三部分：

1. 76 个 RAG 专属源码、测试和文档文件，可以整文件纳入；
2. 11 个位于公共目录、但内容边界清晰的新增依赖文件，可以整文件纳入；
3. 44 个已跟踪共享文件包含 RAG、管理员门禁及其他并行工作流改动，必须逐 hunk 拆分。

因此，本轮只把前两类共 87 个文件作为安全暂存边界。该暂存集合是可审查的阶段性集合，
不是可独立运行或可提交的最终集合；44 个共享文件中的必要 hunk 尚未纳入。

## 2. 安全扫描结果

创建本清单前，A 类共有 75 个文件；本清单自身创建后为 76 个。原 75 个文件扫描结果：

| 检查项 | 结果 |
| --- | --- |
| 文件数 / 总大小 | 75 / 875,781 bytes |
| 扩展名 | 48 个 `.py`、20 个 `.md`、6 个 `.json`、1 个 `.jsonl` |
| UTF-8 严格解码 | 通过 |
| UTF-8 BOM | 0 |
| JSON / JSONL 解析错误 | 0 |
| 大于 2 MiB 的文件 | 0 |
| reparse point / 符号链接 | 0 |
| 可疑密钥格式 | 0 |
| 与受保护 env 中实际值完全相同 | 0 |

另发现 85 个 `__pycache__/*.pyc`，均命中 `agent-service/.gitignore`，不属于候选源码，也不纳入
暂存。测试 `test_runtime_contracts.py` 中的字符 `�` 是用于验证 UTF-8 质量门禁的负向断言，
不是乱码。

## 3. A 类：RAG 专属文件

| 路径 | 文件数 | 处理 |
| --- | ---: | --- |
| `agent-service/app/rag/**` | 23 | `INCLUDE_WHOLE` |
| `agent-service/tests/rag/**` | 31 | `INCLUDE_WHOLE` |
| `docs/agent/rag/**` | 22 | `INCLUDE_WHOLE`；包含本清单 |

这些目录中的 `__pycache__`、`.pyc` 和其他被忽略生成物不在文件数内。

## 4. B 类：公共目录中的独立新增文件

以下 11 个文件内容已逐文件审查，可整文件纳入，但其中部分依赖 C 类共享 hunk：

| 文件 | 归属 | 未暂存依赖 |
| --- | --- | --- |
| `agent-service/tests/__init__.py` | RAG 测试包导入基础 | Python 共享集成 |
| `src/main/java/com/Laibin/SugarInventory/agent/security/AgentAccessPolicy.java` | RAG-SEC 管理员门禁 | Controller / Service 共享 hunk |
| `src/test/java/com/Laibin/SugarInventory/agent/security/AgentAccessPolicyTest.java` | 管理员策略单测 | 无 |
| `src/test/java/com/Laibin/SugarInventory/agent/AgentSessionControllerAccessTest.java` | Controller 非管理员拒绝测试 | Controller 共享 hunk |
| `src/test/java/com/Laibin/SugarInventory/common/GlobalExceptionHandlerTest.java` | UAT 发现的错误信息泄漏回归 | GlobalExceptionHandler 共享 hunk |
| `webpage/src/components/agent/agentAccess.mjs` | Web 管理员入口判断 | Menu / AgentAssistant 共享 hunk |
| `webpage/src/components/agent/agentAccess.test.mjs` | Web 角色单测 | 无 |
| `webpage/src/components/agent/knowledgeCardPresentation.mjs` | 知识引用、安全过滤和状态映射 | 两个 Vue 展示组件共享 hunk |
| `webpage/src/components/agent/knowledgeCardPresentation.test.mjs` | 知识引用展示单测 | 无 |
| `webpage/src/components/agent/reviewPayload.mjs` | UAT 发现的复核字段长度和 UTF-16 边界处理 | AgentAssistant 共享 hunk |
| `webpage/src/components/agent/reviewPayload.test.mjs` | 复核字段边界单测 | 无 |

## 5. C 类：必须拆分的共享文件

### 5.1 Python Agent 和容器

以下 20 个文件均保持未暂存：

```text
agent-service/Dockerfile
agent-service/README.md
agent-service/pyproject.toml
agent-service/app/agents.py
agent-service/app/config.py
agent-service/app/goal_contracts.py
agent-service/app/knowledge.py
agent-service/app/main.py
agent-service/app/model.py
agent-service/app/observability.py
agent-service/app/orchestration.py
agent-service/app/progress.py
agent-service/app/runtime.py
agent-service/app/schemas.py
agent-service/app/state_models.py
agent-service/app/tool_arguments.py
agent-service/tests/test_agent_service.py
agent-service/tests/test_goal_contracts.py
agent-service/tests/test_llm_tool_loop.py
agent-service/tests/test_state_infrastructure.py
```

主要风险：

- `runtime.py`、`tool_arguments.py` 和 `goal_contracts.py` 同时包含登记报表及其他 Agent 演进；
- `agents.py` 的知识专家改动与备料池行为调整相邻；
- `config.py` 的 RAG 配置与模型模式、报表专家配置混合；
- `observability.py` 的知识指标与流式诊断指标位于同一 hunk；
- `state_models.py` 的 `last_knowledge_result` 与报表上下文位于同一 hunk；
- 多个公共测试文件同时覆盖 RAG-SEC、RAG 和报表功能，不能整文件归入 RAG。

### 5.2 部署示例

以下 4 个文件保持未暂存：

```text
deploy/env/agent.env.example
deploy/simple/.env.example
deploy/simple/agent.Dockerfile
deploy/simple/docker-compose.yml
```

RAG extras、环境变量和只读 volume 可以拆出，但同一文件还包含模型模式等并行变更。真实 `.env`、
正式 artifact 和本地模型目录始终禁止纳入 Git。

### 5.3 共享契约和设计文档

以下 7 个文件保持未暂存：

```text
docs/agent/ai-assistant-product-goal.md
docs/agent/readonly-goal-contract-registry.yaml
docs/agent/tool-capability-registry.yaml
docs/agent/schemas/goal-draft-v1.schema.json
docs/agent/schemas/result-reasoning-draft-v1.schema.json
docs/agent/evaluation/readonly-goal-stability-corpus-v1.json
docs/mcp/mcp-tool-registry.md
```

这些文件包含知识目标、内部 L0 工具和固定评测用例，但也同时承载其他 Agent/MCP 迭代，必须逐
hunk 处理。

### 5.4 Java Gateway 和安全边界

以下 9 个文件保持未暂存：

```text
src/main/java/com/Laibin/SugarInventory/agent/controller/AgentSessionController.java
src/main/java/com/Laibin/SugarInventory/agent/gateway/RuntimeRoutingAgentGatewayService.java
src/main/java/com/Laibin/SugarInventory/agent/python/dto/PythonAgentChatResponseDTO.java
src/main/java/com/Laibin/SugarInventory/agent/service/impl/AgentSessionServiceImpl.java
src/main/java/com/Laibin/SugarInventory/common/GlobalExceptionHandler.java
src/test/java/com/Laibin/SugarInventory/agent/AgentSessionControllerWarmupTest.java
src/test/java/com/Laibin/SugarInventory/agent/AgentSessionServiceImplTest.java
src/test/java/com/Laibin/SugarInventory/agent/HttpPythonAgentClientTest.java
src/test/java/com/Laibin/SugarInventory/agent/RuntimeRoutingAgentGatewayServiceTest.java
```

需要拆出的内容包括管理员强制门禁、`reviewTrace` 安全映射、知识调用审计和 UAT 发现的通用错误
信息脱敏；文件中的其他工具、调试和网关演进不能随 RAG 一并纳入。

### 5.5 Web 集成

以下 4 个文件保持未暂存：

```text
webpage/src/components/AgentAssistant.vue
webpage/src/components/Menu.vue
webpage/src/components/agent/AgentBusinessCard.vue
webpage/src/components/agent/AgentMessageBubble.vue
```

`AgentBusinessCard.vue` 和 `AgentAssistant.vue` 与登记报表展示改动高度交织，是 Web 侧最高风险
拆分点。`AgentMessageBubble.vue` 的当前 diff 基本属于知识状态展示，但仍应在最终 cached patch 中
与 helper 导入、测试和构建一起核验。

## 6. D 类：明确排除

```text
agent-service/.venv/**
agent-service/build/**
agent-service/**/__pycache__/**
agent-service/**/*.pyc
deploy/simple/artifacts/**
deploy/simple/.env
webpage/node_modules/**
webpage/dist/**
本地模型、密钥、进程日志、临时发布锁和 pointer 临时文件
```

仓库中其他库存历史、生产、登记报表、MCP 扩展和 UAT 文档，即使文本中出现 `storage`、
`average` 或测试用的版本词，也不因此归入 RAG。

## 7. 集成前提交门禁（历史）

在 44 个 C 类文件的必要 hunk 尚未拆出前：

- staged A/B 集合不得直接提交；
- 不得声称 clean checkout 可以构建或运行 RAG；
- 不运行在线部署或切换当前服务；
- 下一步必须生成最小 cached patch，并从暂存内容构造可验证树；
- 如某个 RAG hunk无法与并行功能安全分离，应转为协调依赖，而不是猜测归属。

## 8. A/B 暂存验证

2026-08-03 已按显式路径暂存 A/B 类文件，未创建提交：

| 检查项 | 结果 |
| --- | --- |
| staged 文件数 | 87 |
| staged 变更 | 22,541 行新增 |
| manifest 边界外文件 | 0 |
| artifact、env、缓存等禁止路径 | 0 |
| UTF-8 BOM / 非法 UTF-8 | 0 / 0 |
| 可疑密钥格式 | 0 |
| `git diff --cached --check` | 通过 |
| RAG 专项 | 119 passed，5 skipped |
| Web A/B helper | 12 passed |
| Java A/B 安全与异常测试 | 6 passed |

第一次 cached check 发现 10 个 Python 文件末尾多余空行和 Markdown 行尾空格；已做纯机械清理，
保持 UTF-8 无 BOM，复查通过。第一次并行测试命令因 PowerShell 未给 Maven 的逗号参数加引号而
解析失败；修正命令后 Python、Web、Java 三组验证均通过。

## 9. 最终集成结果

- 本清单中的 RAG 专属目录、独立依赖和共享集成点已进入 `4150e6d`；
- 提交由共享工作区中的其他任务创建并推送，本清单执行线程没有创建或推送提交；
- `4150e6d` 同时收录 analytics/reporting 基线，无法再以独立 RAG staged diff 表达最终边界；
- 当前提交回归：Python `454 passed, 5 skipped`，RAG `119 passed, 5 skipped`，Java
  `316 passed`，Web `67 passed`，Vite build 通过；
- `agent-service/build/**`、`deploy/simple/artifacts/**`、真实 env、模型、密钥和运行日志仍未进入 Git；
- 源码集成门禁已关闭，后续门禁转为真实 env、只读模型目录和当前在线实例验收。
