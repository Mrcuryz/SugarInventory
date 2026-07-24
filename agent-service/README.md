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
- `DELETE /internal/agent/sessions/{agentSessionId}`

Runtime rules:

- Main path: `AGENT_TOOL_MODE=java_gateway`
- Test-only path: `AGENT_TOOL_MODE=mock`
- Environment-driven mock mode does not bypass service authentication unless
  `AGENT_ALLOW_INSECURE_MOCK_AUTH=true` is explicitly set for isolated tests.
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
$env:AGENT_RUN_TIMEOUT_MS="90000" # llm mode default; whole bounded Agent turn
$env:AGENT_MODEL_MODE="openai_compatible" # or basic
$env:AGENT_MODEL_BASE_URL="https://api.deepseek.com" # project default
$env:AGENT_MODEL_NAME="deepseek-v4-flash" # project default
$env:AGENT_MODEL_API_KEY="<model-service-key>"
$env:AGENT_MODEL_TIMEOUT_MS="30000"
$env:AGENT_GOAL_DRAFT_SHADOW_ENABLED="false"
$env:AGENT_PLANNING_MODE="deterministic" # deterministic or local/UAT-only llm
$env:AGENT_LLM_ALLOWED_EXPERTS="inventory_expert,warehouse_expert,assay_expert,logistics_expert,production_expert"
$env:AGENT_LLM_MAX_TOOL_CALLS="3"
$env:AGENT_LLM_MAX_TOOL_RETRIES="1"
```

`AGENT_PLANNING_MODE=llm` is an experimental local/UAT path. It is rejected at
startup in `AGENT_ENV=production`, requires `AGENT_MODEL_MODE=openai_compatible`,
and does not change the deterministic default. `AGENT_MODEL_BASE_URL` and
`AGENT_MODEL_NAME` use the project defaults shown above when omitted; the API
key remains mandatory and has no source-code default.

In `llm` mode every business expert follows the same bounded sequence: main
model semantic routing, expert action decision, Runtime-authorized tool call,
safe fact adaptation, and expert result analysis. Deterministic intent handlers
must not preempt this sequence; they are reserved for explicit deterministic
mode or controlled fallback behavior.

`REQUEST_TIMEOUT_MS` only limits one Java Gateway tool request. The whole SSE
turn is limited independently by `AGENT_RUN_TIMEOUT_MS` (default: 90 seconds in
`llm`, 20 seconds in `deterministic`). In `llm` mode the expert's validated
final answer is sent directly to the client; Runtime does not invoke another
model pass merely to stream or rewrite that answer.

## Run

```powershell
cd D:\Laibin\LaibinSugarInventory\agent-service
.\.venv\Scripts\python -m pip install ".[test,run]"
.\.venv\Scripts\python -m uvicorn app.main:app --host 127.0.0.1 --port 8091
```

The current runtime uses a bounded in-memory LangGraph-compatible checkpointer.
Requests for the same `agentSessionId` are serialized, message/tool history is
bounded, HITL state is bound to its originating expert, and Java session
revocation calls the Python clear-session endpoint. This backend is still
single-process only: production multi-worker deployment requires a shared,
encrypted persistent checkpointer with TTL before it is considered M5-ready.


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
acceptance do not depend on an external LLM. In the default deterministic mode,
the OpenAI-compatible client does not control tool execution. V1.1b adds an
optional `GoalDraftV1` model call in Shadow Mode. It records a desensitized
semantic proposal alongside the current Router result but cannot change the
selected expert, tool, arguments, permission scope, or execution path. Enable
it only for controlled UAT with `AGENT_GOAL_DRAFT_SHADOW_ENABLED=true`.

V1.1 Experimental LLM Tool Loop adds a separate `AGENT_PLANNING_MODE=llm` path
for local/UAT comparison. The main model selects one experimental expert or the
existing registered recipe. The selected expert proposes one read-only action
at a time, observes a safe result, and may continue, clarify, or answer. Runtime
still validates the expert whitelist, model-visible schema, state references,
three-call budget, one retry, HITL, evidence references, and error/no-data
separation. Only inventory, warehouse, and assay experts are enabled initially;
all other experts fail closed in this mode. See
`docs/agent/agent-v1-1-experimental-llm-tool-loop-design.md`.

The runtime remains the safety boundary: only the current allowlisted L1 read-only tools are allowed,
planned arguments are validated against explicit schemas, resolver ambiguity
still becomes user clarification, and ordinary responses never expose internal
IDs, tool names, tokens, raw JSON, or stack traces.

Domain context packs currently cover:

- MCP read-only safety boundary
- product and inventory lookup rules
- warehouse naming and resolver rules
- pallet status lookup rules
- QR / pallet lifecycle, printed-not-inbound, anomaly, flow, and batch completion rules
- assay status lookup rules
- selected product / selected warehouse structured state

## Modular Main Agent and Expert Agents

The Python runtime now uses a least-privilege handoff layer:

```text
main_agent -> Agent Handoff Router -> one module expert -> safe result -> main_agent answer
main_agent -> allowlisted dependency plan -> multiple bounded expert steps -> main_agent answer
```

`main_agent` owns intent routing, conversation state, HITL, safety decisions,
and the final user-facing answer. It has no business tools. Inventory,
warehouse, assay, and pallet experts each receive only their own context pack
and tool schemas. Production and audit expert profiles exist as extension
points but currently have no enabled tools.

The runtime validates the expert tool scope both after planning and immediately
before calling the Java Gateway using an immutable per-run execution context.
An out-of-bound model choice is rejected instead of being silently re-routed to
another expert. `ToolArgumentBuilder` also accepts an
`expert_model_clients` mapping, so module experts can later use different model
clients and parameters. The current application wiring still shares the
configured default model client.

The first compound recipe handles a warehouse inventory question followed by
"their assay status". It runs inventory scope resolution first, then passes a
bounded product scope through the assay expert. The recipe permits at most four
steps, twelve tool calls, and five product fan-out items. Each step receives a
fresh immutable expert context; partial assay failures preserve the inventory
answer. Because the current inventory distribution does not expose a verified
batch-to-assay join, the answer explicitly describes latest product assay
records and never claims that each in-stock batch is qualified.

Ordinary SSE does not include the full router/handoff snapshot. Python emits a
Java-only `audit` event, Java filters it from the user stream, and persists the
target expert, business domain, and handoff mode. Every Java internal tool audit
also carries the same bounded expert context.

See `docs/agent/modular-agent-architecture.md` for responsibilities, current
tool ownership, audit fields, limitations, and extension rules.

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

## Pallet Journey Acceptance (2026-07-23)

The pallet expert follows the same LLM execution contract as inventory, assay,
and production: main-model understanding, expert decision, validated read-only
tool call, safe fact adaptation, and expert result analysis. Deterministic code
may validate or reject a plan, but it does not replace the expert's normal tool
choice.

The accepted journey is current pallet status, recent movement, then complete
history. `CURRENT_PALLET` is registered even when a user starts with a direct
history query. A complete-history request must use `query_pallet_flow_records`;
an attempted lifecycle-summary substitution is rejected once and returned to
the expert for correction. Runtime normalizes complete history to a controlled
date range because the upstream tool otherwise applies a recent default, while
the safe result and UI expose only `完整历史`.

User-facing output translates `cycle` / `cycleNo` to `第 N 次流转`. Invalid
format/check-digit errors and valid-but-missing pallet codes are separate
terminal outcomes, neither is retried with the same call signature, and neither
leaks backend codes or audit wording. Browser acceptance covered 14 complete
history records, the six controlled progress stages, invalid input, no data,
current inventory location, and the no-current-inventory business state.

## Current Inventory Quality Screening (2026-07-23)

The assay expert now exposes three separate L1 read-only intents:
`query_unqualified_inventory`, `query_inventory_by_quality_standard`, and
`query_inventory_by_assay_metrics`. All three use the shared current-inventory
batch fact instead of `inventory.assay_id`: pallet inventory joins the latest
assay by product and pallet production date, while non-pallet inventory has a
compatibility fallback to entry date.

The meanings are intentionally different. “Unqualified” requires an explicit
latest saved failure; no assay, no standard, and multiple-standard candidates
are not failures. Standard matching re-evaluates all constrained raw metrics
against one controlled standard code/version. Metric filtering accepts one
allowlisted metric, one closed comparison operator, and one number. Each result
can continue to a controlled assay-report detail without exposing the internal
report reference to the model answer or UI.
