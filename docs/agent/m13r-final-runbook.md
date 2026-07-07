# M1.3R-final 运行与 E2E 验收固化

本文档固化 M1.3R-final 阶段的本地运行、真实浏览器 E2E 验收和常见故障处理。范围覆盖：

- Java Agent Gateway 接入 Python runtime。
- Python runtime 通过 Java internal tool gateway 调用已允许的只读工具。
- 前端 AI 助手 Human-in-the-loop interrupt/resume 真实浏览器验收。

## 当前基线

M1.3R-final 的前端验收入口为：

- `webpage/e2e/agent-hitl.spec.ts`
- `webpage/playwright.agent-hitl.config.ts`
- `webpage/package.json` 中的 `test:e2e:agent-hitl`

当前 E2E 覆盖 5 条主链路：

1. clarification interrupt 可恢复，并持久化 `RESUMED`。
2. 选择产品后，化验追问与库存追问保持同一产品上下文。
3. 移动端 AI 助手抽屉不横向溢出。
4. 刷新页面后，未完成 interrupt 不会静默丢失。
5. 安全负向请求不会进入损坏的 HITL waiting 状态。

最近一次验收结果为：

```text
6 passed
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
```

说明：

- `AGENT_RUNTIME_MODE=python` 是 HITL E2E 的必要条件。否则可能回退到 legacy clarification。
- `AGENT_RUNTIME_FALLBACK_ENABLED=false` 用于验收阶段暴露真实链路问题，避免 fallback 掩盖缺陷。
- `AGENT_PYTHON_SERVICE_KEY` 必须与 Python runtime 的同名配置一致。
- `AGENT_INTERNAL_TOOL_SERVICE_KEY` 必须与 Python runtime 调 Java internal tool gateway 的配置一致。

### Python runtime

```powershell
$env:AGENT_PYTHON_SERVICE_KEY="<java-to-python-shared-key>"
$env:AGENT_INTERNAL_TOOL_SERVICE_KEY="<python-to-java-shared-key>"
$env:AGENT_JAVA_TOOL_GATEWAY_BASE_URL="http://127.0.0.1:8080"
```

说明：

- `AGENT_PYTHON_SERVICE_KEY` 用于校验 Java 调 Python runtime 的请求。
- `AGENT_INTERNAL_TOOL_SERVICE_KEY` 用于 Python 调 Java internal tool gateway。
- `AGENT_JAVA_TOOL_GATEWAY_BASE_URL` 指向 Java 后端地址。

### Playwright E2E

```powershell
$env:AGENT_E2E_USERNAME="<测试账号>"
$env:AGENT_E2E_PASSWORD="<测试密码>"
$env:AGENT_E2E_DB_PASSWORD="<本地数据库密码>"
```

可选项：

```powershell
$env:AGENT_E2E_BASE_URL="http://127.0.0.1:5173"
$env:AGENT_E2E_DB_ASSERTIONS="0"
```

说明：

- 默认不需要手动启动 Vite，`playwright.agent-hitl.config.ts` 会自动启动。
- 如果设置了 `AGENT_E2E_BASE_URL`，调用方需要保证该地址对应的前端服务已启动。
- 如果本机暂时无法提供数据库断言条件，可临时设置 `AGENT_E2E_DB_ASSERTIONS=0`，但正式验收建议开启数据库断言。

## 启动顺序

### 1. 启动 Python runtime

```powershell
cd D:\Laibin\LaibinSugarInventory\agent-service
$env:AGENT_PYTHON_SERVICE_KEY="<java-to-python-shared-key>"
$env:AGENT_INTERNAL_TOOL_SERVICE_KEY="<python-to-java-shared-key>"
$env:AGENT_JAVA_TOOL_GATEWAY_BASE_URL="http://127.0.0.1:8080"
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

如果 `toolGateway` 不是 `UP`，优先检查 `AGENT_INTERNAL_TOOL_SERVICE_KEY` 和 `AGENT_JAVA_TOOL_GATEWAY_BASE_URL`。

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
Running 6 tests using 1 worker
6 passed
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
- 临时绕过数据库断言可设置 `AGENT_E2E_DB_ASSERTIONS=0`，但正式验收不建议绕过。

## 验收边界

M1.3R-final E2E 只验证 Agent HITL 交互链路、前端状态、上下文延续、刷新恢复与安全负向状态，不验证业务写操作。

当前 Agent internal tool gateway 只允许白名单只读工具：

- `resolve_products`
- `resolve_warehouses`
- `get_inventory_overview`
- `get_inventory_distribution`
- `get_warehouse_status`
- `get_pallet_status`
- `get_assay_status`

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
