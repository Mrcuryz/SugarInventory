# MCP Auth Design

## M1.2 Agent Gateway Closed Loop

M1.2 connects the productized Web entry with the existing read-only warehouse MCP tools without exposing credentials to the Agent.

Flow:

1. The user logs in to the warehouse Web or mini-program client.
2. The frontend calls `POST /api/agent/sessions` with the current user token held by the normal application session.
3. The backend creates or validates an `agent_session` and returns only session metadata.
4. The frontend sends natural-language input to `POST /api/agent/sessions/{agentSessionId}/messages`.
5. `AgentGatewayService` validates that the current user owns the active session.
6. `McpSessionManager` starts or reuses one dedicated `warehouse-mcp` process for that `agentSessionId`.
7. The backend internally calls `issueDelegationTokenForInternalUse(...)` and injects the short-lived delegated token into the MCP process environment as `WAREHOUSE_DELEGATED_TOKEN`.
8. `warehouse-mcp` calls the warehouse backend with the delegated identity.
9. The backend validates every `AGENT_DELEGATION` request against the `agent_session` table before authorizing the read-only backend API.
10. The Gateway records tool/API audit summaries and returns a natural-language answer to the frontend.

## Token Boundary

The following values must never be returned to the frontend, Agent, LLM, MCP tool output, audit summaries, or logs:

- user login token
- `delegationToken`
- `WAREHOUSE_DELEGATED_TOKEN`
- `Authorization` header
- refresh token
- password
- database connection strings
- full stack traces

`POST /api/agent/sessions` returns only:

- `agentSessionId`
- `userId`
- `name`
- `roleCode`
- `permissionCodes`
- `scopes`
- `expiresAt`
- `status`
- MCP metadata such as `mcpServerName` and `mcpTransport`

## Authorization Model

The MCP server has no login tool. The Agent does not know a password and does not receive the user's original token.

`AGENT_DELEGATION` tokens are short-lived backend-internal credentials. Every delegated backend request must verify:

- token audience is `warehouse-mcp`;
- token type is `AGENT_DELEGATION`;
- `agentSessionId` exists;
- `agent_session.status` is active;
- `agent_session.expires_at` has not passed;
- the user is still valid;
- requested scope covers the backend API;
- the backend API is read-only for the current M1.x scope.

`X-Agent-Session-Id` and `X-Agent-Tool-Name` are audit correlation fields only. They are not permission inputs.

## M1.2 STDIO Transition

The current local implementation uses one STDIO MCP process per `agentSessionId`.

Injected environment variables:

- `WAREHOUSE_DELEGATED_TOKEN`
- `WAREHOUSE_AGENT_SESSION_ID`
- `WAREHOUSE_API_BASE_URL`
- `WAREHOUSE_MCP_LOG_FILE`

Session lifecycle:

- session creation does not start MCP until the first message needs a tool;
- one active user session maps to one MCP process;
- different users must not share the same global-token MCP process;
- revoking the session closes the process;
- expired or dead processes are cleaned before binding a new MCP session.

This STDIO environment-token approach is a transition path only.

## Production Direction

Production multi-user Agent should move to HTTP/Streamable HTTP MCP with request-level user delegation:

- no long-lived global token in an MCP process;
- identity injected per request;
- better concurrency for Web and mini-program users;
- per-request cancellation, timeout, and audit correlation;
- easier horizontal scaling and process isolation.

## Tool Scope

M1.2 does not add business MCP tools. The current allowed read-only tools remain:

- `resolve_products`
- `resolve_warehouses`
- `get_inventory_overview`
- `get_warehouse_status`
- `get_pallet_status`
- `get_assay_status`

The Gateway must not expose login, preview, execute, SQL, arbitrary HTTP proxy, direct database access, or write-interface tools.

## Audit

Agent Tool audit records:

- `userId`
- `agentSessionId`
- `toolName`
- `upstreamPath`
- `resultCode`
- `errorCode`
- `durationMs`
- `createdAt`
- `requestSummary`
- `responseSummary`

Backend API audit records:

- `userId`
- `agentSessionId`
- `toolName`
- HTTP method/path
- permission result
- business result
- `errorCode`
- `durationMs`
- `createdAt`

Audit summaries must be sanitized and must not contain tokens, authorization headers, passwords, database connection strings, or full stack traces.

## Agent Model Planning

M1.2 uses `AgentModelClient` as the replaceable model boundary. The default mode is `agent.model.mode=llm`, which calls an OpenAI-compatible chat completion endpoint and asks the model to return a strict JSON plan. The plan can only contain these intents:

- `INVENTORY_OVERVIEW`
- `WAREHOUSE_STATUS`
- `PALLET_STATUS`
- `ASSAY_STATUS`
- `UNSUPPORTED`

The model must not return product IDs or warehouse IDs. The Gateway still calls `resolve_products` and `resolve_warehouses`, and it must stop on `AMBIGUOUS` instead of guessing an ID.

Fallback mode is `agent.model.mode=rule`. The LLM client also falls back to the rule parser if the model call fails, preserving the read-only safety boundary with lower language quality.

## M1.2 Assistant Experience Layer

The Web assistant now treats MCP tool calls as backend execution details. The frontend displays only natural-language answers and business candidate cards; it does not render tool names, upstream paths, request summaries, or response summaries in the chat transcript.

The Gateway keeps a lightweight in-memory conversation context per `agentSessionId`:

- recent user and assistant messages;
- last uniquely resolved product label;
- last uniquely resolved warehouse label;
- current ambiguity options.

This context lets follow-up prompts such as `这些`, `它`, `刚才那个`, and `这些糖主要存放在哪个库位` continue from the prior turn. The model receives only sanitized conversational context and labels; product and warehouse IDs are still resolved through the existing resolver tools, and ambiguous resolver results still stop for user selection.

Candidate options are returned as business choices. When a user clicks a candidate card, the frontend sends it as `pageContext.selectedOption` in the same conversation turn instead of appending the display label as a new user message. The Gateway accepts the selection only if it matches a supported option from the current session memory, then calls the downstream read-only tool directly with the verified product or warehouse id. Unsupported group options, such as product-type or product-name aggregation that has no backend aggregate query yet, remain visible to the user but disabled in the Web UI.

This memory is an M1.2 process-local implementation. It is cleared when the Agent session is revoked and is lost if the backend process restarts. Production should persist conversation state or move it into the HTTP/Streamable HTTP Agent session layer.
