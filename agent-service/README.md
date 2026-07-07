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
$env:AGENT_MODEL_MODE="openai_compatible" # or basic
$env:AGENT_MODEL_BASE_URL="https://example-model-gateway/v1"
$env:AGENT_MODEL_NAME="<model-name>"
$env:AGENT_MODEL_API_KEY="<model-service-key>"
$env:AGENT_MODEL_TIMEOUT_MS="30000"
```

## Run

```powershell
cd D:\Laibin\LaibinSugarInventory\agent-service
.\.venv\Scripts\python -m pip install ".[test,run]"
.\.venv\Scripts\python -m uvicorn app.main:app --host 127.0.0.1 --port 8091
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

For follow-up questions such as `这些主要存放在哪些库位？`, the runtime calls
the allowlisted `get_inventory_distribution` tool only when structured state
contains a resolver/HITL-confirmed product scope, or when the user explicitly
requests all products. Supported product scopes are one product, an exact-name
group, a product-type group, and all products. Warehouse scope, status/assay/
pallet/date filters, and grouping use closed enums. Internal IDs must match
structured selected state. Results pass through a distribution-specific safe
adapter before messages/state/SSE/cards; model-supplied IDs are never accepted
as the source of truth.

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

## M1.3R-4 Context Builder and Model Tool Planning

The runtime now asks the model layer for a structured next action instead of
expanding the Python runtime with one branch per Chinese phrase:

```text
messages + structured state + domain context packs + tool schemas
  -> ModelClient.plan_next_action(...)
  -> validated read-only tool call / clarification / answer
  -> safe tool result summary back into messages and state
```

`BasicModelClient` is still a deterministic local substitute so tests and local
acceptance do not depend on an external LLM. A production model client can
replace it by implementing the same `ModelClient` protocol. The runtime remains
the safety boundary: only the six existing L1 read-only tools are allowed,
planned arguments are validated against explicit schemas, resolver ambiguity
still becomes user clarification, and ordinary responses never expose internal
IDs, tool names, tokens, raw JSON, or stack traces.

Domain context packs currently cover:

- MCP read-only safety boundary
- product and inventory lookup rules
- warehouse naming and resolver rules
- pallet status lookup rules
- assay status lookup rules
- selected product / selected warehouse structured state

## M1.3R-5 Streaming (completed)

Streaming is a business event stream, not only answer text chunking. Java remains
the frontend entry point and proxies Python `/internal/agent/chat/stream` through:

```text
POST /api/agent/sessions/{agentSessionId}/messages/stream
```

Every event should use a stable envelope:

```json
{
  "eventId": "evt_001",
  "messageId": "msg_001",
  "agentSessionId": "agt_xxx",
  "type": "progress",
  "sequence": 3,
  "payload": {}
}
```

Event classes:

- User-visible business events: `message_start`, `progress`, `clarification`,
  `text_delta`, `card`, `error`, `message_end`
- Admin-only debug events: `tool_start`, `tool_end`, `debug`
- System control events: `heartbeat`, `cancelled`, `timeout`, `fallback`

`message_end.payload.finishReason` must be one of `completed`,
`clarification_required`, `error`, `cancelled`, `timeout`, or `fallback`.
Clarification cards must end with `clarification_required`, so the frontend knows
the assistant is paused for user input.

State boundaries:

- User messages are written to state immediately.
- Successful safe tool results are written to state/messages.
- Final assistant answers are written before `message_end`.
- `progress` can stay in the event log instead of long-term messages.
- `clarification` must write `pendingClarification`.

Frontend rendering should keep one assistant message container per user question:
progress at the top, cards in the middle, final answer at the bottom. Progress
events should not become separate chat bubbles. The frontend should deduplicate
by `messageId + eventId` and render by `sequence`.

Fallback remains strict: before a session enters Python, Java may fallback for a
simple first read-only query. Once an `agentSessionId` has entered Python
runtime, later failures must return a safe unavailable/error event instead of
switching to the legacy Java AgentGateway.

Implemented scope:

- Python stream emits the envelope above and sends `message_start` + business
  `progress` before invoking the runtime.
- Clarification streams end with `finishReason=clarification_required`; tool
  timeout streams emit `error` and `message_end.finishReason=timeout`.
- Java proxies the Python SSE stream, validates event types, filters debug events
  for non-admin users, and applies a second recursive redaction pass.
- The web assistant consumes POST SSE with `fetch + ReadableStream` and renders
  progress, clarification, cards, final text, and errors in one assistant
  message container.
- M1.3R-5.1 will add the backend cancel-by-message endpoint, Python runtime
  cancellation notification, audit result classification, Java end-to-end SSE
  tests, disconnect handling, and real-browser acceptance. The cancel endpoint,
  Python cancellation registry, event suppression, and audit classification are
  now implemented. Real-browser acceptance covers clarification pause/resume,
  cancellation recovery, late-result suppression, auto-scroll, refresh behavior,
  consecutive messages, and a 390px mobile viewport.
- M1.3R-5.2 adds an OpenAI-compatible provider-native streaming client while
  preserving progress, clarification, and card events. `basic` mode emits the
  approved answer as one compatibility delta; `openai_compatible` mode reads
  provider SSE chunks and forwards only safe visible text as `text_delta`.
  Chain-of-thought, reasoning fields, tool calls, raw JSON, and secrets remain
  prohibited. Active runs are registered by `messageId` so cancellation can
  stop later model chunks, suppress tool-result state writes, and classify
  model/tool timeout or cancellation separately.
