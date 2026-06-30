# Agent Session Authorization

This document records the M1 Agent session authorization boundary for the warehouse MCP integration.

## Principles

- The MCP server does not expose a login tool.
- The Agent never receives a user password, the original Web/mini-program token, or a `delegationToken` field.
- A logged-in backend user creates an Agent session through `/api/agent/sessions`.
- The backend may issue a short-lived `AGENT_DELEGATION` token only for internal MCP context injection.
- Codex local development may still use a static `WAREHOUSE_API_TOKEN`; this is development-only and not a production authorization model.

## Session API

- `POST /api/agent/sessions`: creates a session for the authenticated user and returns metadata only.
- `GET /api/agent/sessions/current`: lists the current user's sessions.
- `DELETE /api/agent/sessions/{agentSessionId}`: revokes a session.

The session response includes `agentSessionId`, user metadata, scopes, expiry, MCP server name, and MCP transport. It intentionally does not include any delegated token.

## Delegation Token Validation

Every `AGENT_DELEGATION` token request must validate the `agent_session` row:

- session exists;
- session is active;
- session is not expired;
- token user matches session user;
- user still exists and has a role;
- scope allows the request.

Initial scope is `mcp:warehouse:read`. Future scopes should split product, warehouse, inventory, pallet, assay, and log access.

## Audit Layers

- Agent Tool audit records tool name, session id, user id, sanitized argument summary, result, error code, and duration.
- Backend API audit records session id, user id, tool name header, HTTP method/path, response status, result, error code, and duration.

`X-Agent-Tool-Name` is audit metadata only. It is not a permission source.

## Transport

STDIO plus environment-injected delegated token is only a transition path. Production should prefer HTTP/Streamable HTTP MCP with request-level delegated identity injection.
