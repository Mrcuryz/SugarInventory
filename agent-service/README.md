# Warehouse Agent Service

`agent-service/` is the Python Agent Runtime for the smart warehouse assistant.
It keeps LangGraph-compatible conversation state, handles candidate selections,
calls the Java Internal Agent Tool Gateway, and returns user-facing answers
without exposing tool names, internal IDs, tokens, raw JSON, or stack traces.

## Scope

Implemented endpoints:

- `GET /internal/agent/health`
- `POST /internal/agent/chat`
- `POST /internal/agent/chat/stream`
- `POST /internal/agent/resume`

Runtime rules:

- Main path: `AGENT_TOOL_MODE=java_gateway`
- Test-only path: `AGENT_TOOL_MODE=mock`
- Python never connects to the database.
- Python never calls arbitrary HTTP endpoints.
- Python only calls the Java Gateway path `/internal/agent/tools/{toolName}`.
- Python never receives or returns `delegationToken`, user login token, or Authorization header.
- Python does not add MCP tools and does not call write interfaces.

## Configuration

```powershell
$env:AGENT_TOOL_MODE="java_gateway"
$env:JAVA_TOOL_GATEWAY_BASE_URL="http://localhost:8080"
$env:AGENT_INTERNAL_TOOL_SERVICE_KEY="<python-to-java-tool-key>"
$env:AGENT_PYTHON_SERVICE_KEY="<java-to-python-service-key>"
$env:REQUEST_TIMEOUT_MS="15000"
```

## Run

```powershell
cd D:\Laibin\LaibinSugarInventory\agent-service
.\.venv\Scripts\python -m pip install ".[test,run]"
.\.venv\Scripts\python -m uvicorn app.main:app --host 127.0.0.1 --port 8090
```

The current M1.3R-1c runtime uses an in-memory LangGraph-compatible
checkpointer. Installing the optional `langgraph` extra is reserved for the next
runtime iteration that wires a concrete `StateGraph`.


## M1.3R-1d Domain Context

The runtime now builds a small domain context pack before model/tool argument
construction. When a user message contains warehouse semantics such as `库位`,
`仓库`, `容量`, `存放`, or `位置`, the Context Builder adds warehouse naming rules
for the model layer:

- `warehouseName` may be stored as a pure number, for example `2`.
- Users may say `2号库位`, `2号库`, `库位2`, or `二号库位`.
- The agent must call `resolve_warehouses` before querying warehouse status.
- The resolver `query` should be the mentioned warehouse phrase, not the whole sentence.
- The agent must not guess `warehouseId`.

`ToolArgumentBuilder` calls the configured `ModelClient` with messages, state,
domain context, and tool schema. The current `BasicModelClient` is a local
replaceable model substitute; production can replace it with a real LLM client.
## Test

```powershell
cd D:\Laibin\LaibinSugarInventory\agent-service
.\.venv\Scripts\python -m pip install ".[test]"
.\.venv\Scripts\python -m pytest
```

Mock mode is used only in pytest fixtures. Real local acceptance should use
`java_gateway` mode against the Java Internal Agent Tool Gateway.
## M1.3R-1d-fix Safe Result Adapters

Real Java Gateway results are converted through an explicit allowlist before
answer generation:

```text
raw_gateway_result -> safe_business_result -> answer / SSE event
```

The inventory adapter reads quantities from the nested `summary` object and,
when necessary, the first `records` item. User-facing inventory answers prefer
`displayStockInfo`, then normalized pallets and loose pieces, followed by total
equivalent pieces and total weight. Missing quantity fields produce an explicit
capability message instead of a placeholder summary.

The warehouse adapter reads the nested `capacity` object and only retains the
warehouse display name, status, maximum capacity, current occupancy, current
pallet count, occupancy rate, and remaining capacity. Internal IDs and raw
objects never reach ordinary answers.

For follow-up questions such as `这些主要存放在哪些库位？`, the runtime uses
safe warehouse distribution records when the inventory overview contains them.
If the current read-only result has no location distribution, the assistant
states the capability gap and does not present a total inventory summary as a
location answer.

SSE events apply a final recursive redaction pass. Ordinary responses and SSE
events do not expose tool names, success markers, internal product or warehouse
IDs, tokens, Authorization headers, raw JSON, or stack traces. Debug remains
`null` by default.

## M1.3R-3 Java Forwarding

The Web frontend continues to call Java:

```text
POST /api/agent/sessions/{agentSessionId}/messages
```

With `agent.runtime.mode=python`, Java checks the owned active `agent_session`,
calls this service at `GET /internal/agent/health` and
`POST /internal/agent/chat`, then maps the safe response back to the existing
frontend DTO. Both `user_message` and `candidate_selected` enter the same chat
endpoint and Python state.

Service credentials have separate directions:

- `AGENT_PYTHON_SERVICE_KEY`: Java -> Python internal authentication.
- `AGENT_INTERNAL_TOOL_SERVICE_KEY`: Python -> Java Internal Tool Gateway authentication.

Neither key is a user token. They must not be sent by the frontend, included in
model context, returned in ordinary responses, or written to audit summaries.

Java defaults to `legacy` mode. Local forwarding configuration:

```powershell
$env:AGENT_RUNTIME_MODE="python"
$env:AGENT_PYTHON_BASE_URL="http://localhost:8091"
$env:AGENT_PYTHON_TIMEOUT_MS="30000"
$env:AGENT_PYTHON_SERVICE_KEY="<java-to-python-service-key>"
$env:AGENT_RUNTIME_FALLBACK_ENABLED="true"
```

Fallback is intentionally narrow. Only a first, explicit, simple read query may
fall back before Python has established conversation state. Candidate
selection and contextual follow-ups never switch to legacy. A session that
falls back is pinned to legacy for its remaining lifetime.
