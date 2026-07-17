# Agent v1 只读 MCP 运行与 E2E 验收固化

本文档固化 Agent v1 阶段的本地运行、47 工具确定性契约验收、9 专家真实浏览器 E2E 和常见故障处理。范围覆盖：

- Java Agent Gateway 接入 Python runtime。
- Python runtime 通过 Java internal tool gateway 调用已允许的只读工具。
- 前端 AI 助手 Human-in-the-loop interrupt/resume 真实浏览器验收；
- 9 个业务专家各一条 L1 只读 canary，以及主 Agent 零业务工具的能力快照验收。

## 当前基线

Agent v1 的验收入口为：

- `webpage/e2e/agent-hitl.spec.ts`
- `webpage/playwright.agent-hitl.config.ts`
- `webpage/package.json` 中的 `test:e2e:agent-hitl`
- `scripts/test-agent-fixed-integration.ps1`

验收分为三层，不能只跑浏览器用例：

1. 固定数据契约层：锁定 47 个工具名称、L1 风险、9 专家精确工具集、主 Agent 空工具集、Java/Python/MCP/Registry 一致性，并实际绑定调用全部 47 个 Spring AI ToolCallback。
2. Java/MCP/前端构建层：运行根项目全量单测、warehouse-mcp 全量单测和前端生产构建。
3. 真实部署 E2E：使用本地开发库和具备完整验收权限的测试账号，验证服务握手、HITL、SSE、RBAC、审计归属和 9 专家只读 canary。

当前 E2E 共 16 条：

前 7 条覆盖交互与安全主链路：

1. clarification interrupt 可恢复，并持久化 `RESUMED`。
2. 选择产品后，化验追问与库存追问保持同一产品上下文。
3. 全产品受控过滤与按产品库存分组。
4. 自然语言库位解析后查询并展示库位库存分布。
5. 移动端 AI 助手抽屉不横向溢出。
6. 刷新页面后，未完成 interrupt 不会静默丢失。
7. 安全负向请求不会进入损坏的 HITL waiting 状态。

后 9 条分别覆盖：

1. `inventory_expert -> query_inventory_ledger`
2. `warehouse_expert -> query_warehouse_capacity_distribution`
3. `logistics_expert -> query_pallet_tasks`
4. `pallet_expert -> query_fixed_product_qr_pool`
5. `production_expert -> query_in_process_materials`
6. `assay_expert -> query_quality_standard_catalog`（对应蓝图 quality 域）
7. `master_data_expert -> query_product_catalog`
8. `administration_expert -> query_employee_roster`
9. `audit_expert -> search_operation_logs`

正式 E2E 的每条专家 canary 都必须在 `agent_tool_audit_log` 中找到 `SUCCESS`，并核对 `request_summary.expertAgent`。只看到自然语言回答不能证明专家边界正确。

期望结果为：

```text
16 passed
```

## 必要服务

本地真实验收需要 4 个部分可用：

- MySQL：使用本地开发库。
- Java 后端：默认 `http://127.0.0.1:8080`。
- Python runtime：默认 `http://127.0.0.1:8091`。
- Vite 前端：由 Playwright webServer 自动启动，默认 `http://127.0.0.1:5173`。

## 环境变量

### Java 后端

```powershell
$env:AGENT_RUNTIME_MODE="python"
$env:AGENT_PYTHON_BASE_URL="http://127.0.0.1:8091"
$env:AGENT_PYTHON_SERVICE_KEY="<java-to-python-shared-key>"
$env:AGENT_INTERNAL_TOOL_SERVICE_KEY="<python-to-java-shared-key>"
$env:AGENT_RUNTIME_FALLBACK_ENABLED="false"
$env:DB_PASSWORD="<本地数据库密码>"
```

说明：

- `AGENT_RUNTIME_MODE=python` 是 HITL E2E 的必要条件。否则可能回退到 legacy clarification。
- `AGENT_RUNTIME_FALLBACK_ENABLED=false` 用于验收阶段暴露真实链路问题，避免 fallback 掩盖缺陷。
- 当前配置默认值已切换为 `python` 且默认禁用 fallback；运行脚本仍应显式设置，避免环境差异。
- `AGENT_PYTHON_SERVICE_KEY` 必须与 Python runtime 的同名配置一致。
- `AGENT_INTERNAL_TOOL_SERVICE_KEY` 必须与 Python runtime 调 Java internal tool gateway 的配置一致。
- `DB_PASSWORD` 是 Java 本地开发库连接密码；只允许使用隔离开发/验收库，不得指向生产数据库。

### Python runtime

```powershell
$env:AGENT_PYTHON_SERVICE_KEY="<java-to-python-shared-key>"
$env:AGENT_INTERNAL_TOOL_SERVICE_KEY="<python-to-java-shared-key>"
$env:JAVA_TOOL_GATEWAY_BASE_URL="http://127.0.0.1:8080"
```

说明：

- `AGENT_PYTHON_SERVICE_KEY` 用于校验 Java 调 Python runtime 的请求。
- `AGENT_INTERNAL_TOOL_SERVICE_KEY` 用于 Python 调 Java internal tool gateway。
- `JAVA_TOOL_GATEWAY_BASE_URL` 指向 Java 后端地址。旧名称 `AGENT_JAVA_TOOL_GATEWAY_BASE_URL` 不会被当前 Python Runtime 读取。

### Playwright E2E

```powershell
$env:AGENT_E2E_USERNAME="<测试账号>"
$env:AGENT_E2E_PASSWORD="<测试密码>"
$env:AGENT_E2E_DB_PASSWORD="<本地数据库密码>"
$env:AGENT_PYTHON_SERVICE_KEY="<java-to-python-shared-key>"
```

可选项：

```powershell
$env:AGENT_E2E_BASE_URL="http://127.0.0.1:5173"
$env:AGENT_E2E_PYTHON_BASE_URL="http://127.0.0.1:8091"
$env:AGENT_E2E_DB_ASSERTIONS="0"
```

说明：

- 默认不需要手动启动 Vite，`playwright.agent-hitl.config.ts` 会自动启动。
- 如果设置了 `AGENT_E2E_BASE_URL`，调用方需要保证该地址对应的前端服务已启动。
- `AGENT_PYTHON_SERVICE_KEY` 还用于 E2E 调用 Python capabilities endpoint，核对 `toolCount=47`、1 个受控配方、9 个非空业务专家和 `main_agent.allowedToolCount=0`。
- 如果本机暂时无法提供数据库断言条件，可临时设置 `AGENT_E2E_DB_ASSERTIONS=0` 做 UI smoke，但该结果不能用于正式验收。
- 正式验收账号至少需要 canary 涉及的 `inventory:view`、`warehouse:view`、`document:view`、`task:view`、`qrcode:view`、`qrcode:pool_view`、`assay:view`、`production:material:view`、`quality_standard:view`、`product:view`、`rbac:user:view`、`log:view` 权限。权限不足应视为验收失败，不得改成忽略 403。

## 启动顺序

### 验收前置：执行固定数据契约验收

```powershell
cd D:\Laibin\LaibinSugarInventory
.\scripts\test-agent-fixed-integration.ps1
mvn test
cd .\webpage
npm run build
```

任一命令失败都不得继续声明 47 工具可交付。固定数据套件不连接业务数据库，也不会修改库存。

### 0. 构建可执行 MCP 包

```powershell
cd D:\Laibin\LaibinSugarInventory\warehouse-mcp
mvn package -DskipTests
```

期望生成 `target/warehouse-mcp-0.1.0-exec.jar`。Java Agent Gateway 默认启动该
Spring Boot 可执行包；普通的 `warehouse-mcp-0.1.0.jar` 是 thin jar，不能作为
STDIO MCP 进程直接运行。

同时构建 Java 后端可执行包：

```powershell
cd D:\Laibin\LaibinSugarInventory
mvn package -DskipTests
```

### 1. 启动 Python runtime

```powershell
cd D:\Laibin\LaibinSugarInventory\agent-service
$env:AGENT_PYTHON_SERVICE_KEY="<java-to-python-shared-key>"
$env:AGENT_INTERNAL_TOOL_SERVICE_KEY="<python-to-java-shared-key>"
$env:JAVA_TOOL_GATEWAY_BASE_URL="http://127.0.0.1:8080"
python -m uvicorn app.main:app --host 127.0.0.1 --port 8091
```

### 2. 启动 Java 后端

```powershell
cd D:\Laibin\LaibinSugarInventory
$env:AGENT_RUNTIME_MODE="python"
$env:AGENT_PYTHON_BASE_URL="http://127.0.0.1:8091"
$env:AGENT_PYTHON_SERVICE_KEY="<java-to-python-shared-key>"
$env:AGENT_INTERNAL_TOOL_SERVICE_KEY="<python-to-java-shared-key>"
$env:AGENT_RUNTIME_FALLBACK_ENABLED="false"
$env:DB_PASSWORD="<本地数据库密码>"
java -jar .\target\SugarInventory-1.0-SNAPSHOT.jar
```

如果使用 IDE 启动 Java 后端，也需要在对应 Run Configuration 中设置同样的环境变量。

### 3. 检查 Python runtime 健康状态

```powershell
Invoke-RestMethod `
  -Uri "http://127.0.0.1:8091/internal/agent/health" `
  -Headers @{"X-Agent-Service-Key"="<java-to-python-shared-key>"}
```

期望结果：

```text
status = UP
toolGateway = UP
```

如果 `toolGateway` 不是 `UP`，优先检查 `AGENT_INTERNAL_TOOL_SERVICE_KEY` 和 `JAVA_TOOL_GATEWAY_BASE_URL`。

继续检查能力快照：

```powershell
$capabilities = Invoke-RestMethod `
  -Uri "http://127.0.0.1:8091/internal/agent/capabilities" `
  -Headers @{"X-Agent-Service-Key"="<java-to-python-shared-key>"}
$capabilities.toolCount
$capabilities.recipeCount
$capabilities.agentProfileRegistryHash
$capabilities.agentProfiles
```

必须满足：`toolCount=47`、`recipeCount=1`、`agentProfileRegistryHash` 为 64 位 SHA-256、恰好 9 个业务专家的 `allowedToolCount > 0`、`main_agent.allowedToolCount=0`。Java 启动时会同时校验协议版本、工具 hash、配方 hash 和专家权限映射 hash；任一不一致必须 fail-closed。

### 4. 运行 E2E

```powershell
cd D:\Laibin\LaibinSugarInventory\webpage
$env:AGENT_E2E_USERNAME="<测试账号>"
$env:AGENT_E2E_PASSWORD="<测试密码>"
$env:AGENT_E2E_DB_PASSWORD="<本地数据库密码>"
npm run test:e2e:agent-hitl
```

期望结果：

```text
Running 16 tests using 1 worker
16 passed
```

## 常见故障

### Login response did not include data.token

说明登录接口返回格式与测试预期不一致，或账号密码未通过认证。

处理：

- 确认 `AGENT_E2E_USERNAME` 和 `AGENT_E2E_PASSWORD`。
- 确认 Java 后端连接的是正确本地数据库。
- 查看 Playwright trace 中 `/api/auth/web-login` 的真实响应体。

### connect ECONNREFUSED 127.0.0.1:5173

说明测试访问前端时 Vite 不可用。

处理：

- 使用 `npm run test:e2e:agent-hitl`，不要直接绕过 `playwright.agent-hitl.config.ts`。
- 如果设置了 `AGENT_E2E_BASE_URL`，需要手动启动对应前端服务。

### Agent service authentication is not configured

说明 Python runtime 未配置 `AGENT_PYTHON_SERVICE_KEY`。

处理：

- 在 Python runtime 启动环境中设置 `AGENT_PYTHON_SERVICE_KEY`。
- 确认 Java 的 `AGENT_PYTHON_SERVICE_KEY` 与 Python 一致。

### AI 助手暂时不可用

说明 Java 到 Python runtime 的主链路失败，或 Python runtime 到 Java internal tool gateway 失败。

处理：

- 检查 Python runtime 是否运行在 `127.0.0.1:8091`。
- 检查 Java 的 `AGENT_PYTHON_BASE_URL`。
- 调用 Python health endpoint，确认 `status=UP` 且 `toolGateway=UP`。
- 检查 Java 与 Python 两侧 service key 是否一致。

### LEGACY_CLARIFICATION without interruptId

说明 Java 后端没有走 Python runtime HITL 链路，常见原因是未设置 `AGENT_RUNTIME_MODE=python`。

处理：

- 设置 `AGENT_RUNTIME_MODE=python`。
- 验收阶段保持 `AGENT_RUNTIME_FALLBACK_ENABLED=false`。

### Set AGENT_E2E_DB_PASSWORD

说明 E2E 启用了数据库断言，但缺少本地数据库密码。

处理：

- 设置 `AGENT_E2E_DB_PASSWORD`。
- 临时 UI smoke 可设置 `AGENT_E2E_DB_ASSERTIONS=0`；正式验收禁止绕过，因为 9 专家归属和工具成功审计需要数据库证据。

### Python runtime capabilities endpoint returned 401/503

说明 Playwright 没有拿到正确的 `AGENT_PYTHON_SERVICE_KEY`，或 Python runtime 并非当前待验收实例。

处理：

- 在启动 Python、Java 和 Playwright 的环境中使用同一组 Java -> Python service key；
- 确认 `AGENT_E2E_PYTHON_BASE_URL` 指向待验收 runtime；
- 不得跳过 capabilities 检查后继续声称 47 工具和 9 专家已验收。

### 专家 canary 返回权限错误

说明测试账号缺少对应 L1 查询权限，或迁移脚本/角色权限尚未生效。

处理：

- 使用专用本地验收账号并补齐本节列出的只读权限；
- 不得给账号增加写权限来绕过验收；
- 不得把 403 解释为空数据或改成用 mock 通过正式 E2E。

## 验收边界

Agent v1 E2E 验证 Agent HITL 交互链路、前端状态、上下文延续、刷新恢复、安全负向状态，以及 9 个专家各一条真实 L1 只读主路径。它不验证业务写操作，也不逐条替代 47 工具的固定数据契约测试。

当前 Agent internal tool gateway 只允许能力注册表登记的 47 个白名单只读工具。完整清单以 `docs/mcp/mcp-tool-registry.md` 和 `docs/agent/tool-capability-registry.yaml` 为准；启动握手会校验工具数量、工具注册表哈希、配方哈希和专家权限映射哈希，任一不一致时 fail-closed。Java Gateway 还会在每次工具调用前重新执行专家到工具的最终授权。

不得为了 E2E 验收引入任意 SQL、任意 HTTP、任意数据库更新或写业务工具。

## 归档要求

每次修改 HITL 或 streaming 相关实现后，至少执行：

```powershell
cd D:\Laibin\LaibinSugarInventory\webpage
npm run build
npm run test:e2e:agent-hitl
```

验收通过后保留源码和文档变更，不提交以下临时产物：

- `webpage/test-results/`
- `webpage/playwright-report/`
- 临时 Python/Java 服务日志
