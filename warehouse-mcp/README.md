# Warehouse MCP Server

`warehouse-mcp` is a minimal read-only MCP Server for the Laibin warehouse system. It uses Spring AI MCP Server STDIO transport and calls the existing warehouse backend through fixed HTTP read endpoints. It does not connect to the database and does not expose write, execute, arbitrary HTTP, or arbitrary SQL tools.

## Tools

| Tool | Purpose | Backend mapping |
| --- | --- | --- |
| `resolve_products` | Resolve a product name/id query into unique, ambiguous, or not-found candidates. | `GET /api/products/{id}`, `GET /api/products/product` |
| `resolve_warehouses` | Resolve a warehouse name/id query into unique, ambiguous, or not-found candidates. | `GET /api/warehouse/{id}`, `GET /api/warehouse/query` |
| `get_inventory_overview` | Read paged product inventory summary and calculated totals. | `GET /api/products/{id}`, `GET /api/inventory/stock/page` |
| `get_inventory_distribution` | Read controlled product scopes with warehouse, status, assay, pallet, date, and grouping filters. | `POST /api/inventory/distribution` (read-only aggregate) |
| `get_warehouse_status` | Read warehouse capacity, inventory details, and recent operations. | `GET /api/warehouse/{id}`, `GET /api/inventory/warehouses`, `POST /api/inventory/qualified-inventory/{warehouseId}/page`, `GET /api/inventory/warehouses/{warehouseId}/recent-operations` |
| `get_pallet_status` | Read pallet code status, current inventory position, assay information, flow cycles, and flow details. | `GET /api/pallet-codes/parse`, `GET /api/pallet-codes/{code}/inventory`, `GET /api/pallet-codes/{code}/assay`, `GET /api/pallet-codes/{code}/flows/cycles`, `GET /api/pallet-codes/{code}/flows` |
| `get_assay_status` | Read an assay by id, or by product and production date, including judge result, failed metrics, and applied standard details. | `GET /api/assay/{id}`, `GET /api/assay/by-product-date` |

`POST /api/inventory/qualified-inventory/{warehouseId}/page` is used only as an existing read query endpoint. No business write endpoints are called. M1.1 still does not expose preview, execute, SQL, arbitrary HTTP proxy, direct database, pallet mutation, assay mutation, or quality-standard mutation tools.

## Environment

For local Codex development, set both values before starting the server. `WAREHOUSE_API_TOKEN` is a STATIC_TOKEN development fallback only and is not the production authorization model:

```powershell
$env:WAREHOUSE_API_BASE_URL = "http://localhost:8080"
$env:WAREHOUSE_API_TOKEN = "<test-or-dev-token>"
```

Optional:

```powershell
$env:WAREHOUSE_API_TIMEOUT = "5s"
$env:WAREHOUSE_DELEGATED_TOKEN = "<backend-injected-agent-token>"
$env:WAREHOUSE_AGENT_SESSION_ID = "<backend-agent-session-id>"
$env:WAREHOUSE_MCP_LOG_FILE = "D:\Laibin\LaibinSugarInventory\warehouse-mcp\warehouse-mcp.log"
```

## Build and Test

```powershell
cd D:\Laibin\LaibinSugarInventory\warehouse-mcp
mvn clean test
mvn clean package -DskipTests
```


## Agent Session Authorization

Production Agent authorization is backend delegated. The MCP server does not expose a login tool and does not return a delegation token to the Agent. A logged-in Web or mini-program user creates an Agent session through the backend, and the backend injects a short-lived delegated identity into the MCP context.

For the current STDIO transition path, the backend may inject `WAREHOUSE_DELEGATED_TOKEN` and `WAREHOUSE_AGENT_SESSION_ID` into the MCP process environment. Production should prefer HTTP/Streamable HTTP MCP with request-level identity injection. `X-Agent-Tool-Name` and `X-Agent-Session-Id` are sent to the backend only for audit correlation; permissions are enforced by the backend from the `AGENT_DELEGATION` token and `agent_session` table state.

See `docs/mcp/agent-session-authorization.md` for the backend session API, validation rules, and audit model.

## M1.2 Agent Gateway Usage

`warehouse-mcp` can run in two authorization modes:

- `STATIC_TOKEN`: local Codex development only. Set `WAREHOUSE_API_TOKEN` manually and start the jar directly.
- `USER_DELEGATED`: productized Web/mini-program Agent flow. The backend creates an `agent_session`, internally issues a short-lived delegated token, and injects it into a dedicated MCP process through `WAREHOUSE_DELEGATED_TOKEN`.

In `USER_DELEGATED` mode, the Agent and frontend never receive the delegated token. The backend `McpSessionManager` binds one STDIO MCP process to one `agentSessionId` and injects:

```powershell
WAREHOUSE_DELEGATED_TOKEN=<backend-internal-token>
WAREHOUSE_AGENT_SESSION_ID=<agent-session-id>
WAREHOUSE_API_BASE_URL=http://localhost:8080
WAREHOUSE_MCP_LOG_FILE=logs/mcp/warehouse-mcp-<agent-session-id>.log
```

The STDIO one-user-one-process model is a transition path. Production multi-user deployments should move to HTTP/Streamable HTTP MCP with request-level delegated identity injection.

M1.2 did not add a login tool or new business MCP tools. M1.4a-1 adds only `get_inventory_distribution`, so the server now exposes seven read-only tools. It still exposes no login, preview, execute, SQL, arbitrary HTTP proxy, direct database, or write tools.
## Resolver Semantics

Product resolver results always follow one of three paths:

- `UNIQUE`: the Agent may pass the returned `productId` to `get_inventory_overview`.
- `AMBIGUOUS`: the Agent must ask the user to choose an option. Terms such as `黄冰糖` may mean a product type group, a product-name group, or one concrete product specification.
- `NOT_FOUND`: the Agent should tell the user no matching product was found.

For ambiguous products, `options` may include:

- `PRODUCT_TYPE_GROUP`: for example all products whose `productType` is `黄冰糖`. It is supported by `get_inventory_distribution`; `get_inventory_overview` still requires a concrete product.
- `EXACT_PRODUCT_NAME_GROUP`: all specifications under the same human product name. It is supported by `get_inventory_distribution`; `get_inventory_overview` still requires a concrete product.
- `SINGLE_PRODUCT`: a concrete `productId`; this is currently supported and can be used for follow-up inventory reads.

Warehouse resolver normalizes common Chinese slot expressions before querying by warehouse name. Examples such as `2号库位`, `2号库`, `2号位`, `库位2`, `二号库位`, `十二号库位`, `2#`, and `2 号` are normalized to warehouse names like `2` or `12`. The normalized number is not treated as a `warehouseId`; it is sent to the existing read-only warehouse-name query endpoint and returned with `matchType=NORMALIZED_NAME` when matched.

## Pallet and Assay Query Semantics

`get_pallet_status` always reads the pallet parse endpoint first. Optional inventory, assay, and flow subqueries can be disabled. Non-fatal optional subquery failures return `partial=true` with `warnings`; authentication, permission, timeout, and server errors return the unified `error` structure.

`get_assay_status` accepts either `assayId`, or `productId + productionDate`, or `productQuery + productionDate`. When `productQuery` is ambiguous, it returns `AMBIGUOUS` and `needsUserSelection=true` through the same resolver semantics; it does not guess a product id. When no assay exists for a product/date query, it returns `resolutionStatus=NOT_FOUND` and `needsAssay=true`.

## Inventory Quantity Semantics

`get_inventory_overview` does not return `totalBoards`, `totalPieces`, or `stockInfo`.

The MCP output uses explicit quantity fields:

- `rawFullPallets`: backend raw full-pallet count.
- `rawLoosePieces`: backend raw loose-piece count.
- `normalizedPallets`: pallet count after converting raw full pallets and loose pieces by `piecesPerPallet`.
- `normalizedLoosePieces`: loose-piece remainder after conversion.
- `totalEquivalentPieces`: total equivalent pieces.
- `displayStockInfo`: user-facing pallet/piece text.

Agents should answer inventory questions using `normalizedPallets`, `normalizedLoosePieces`, and `totalEquivalentPieces` first. Raw fields are only the backend source quantities and must not be treated as the normalized inventory display.

`get_inventory_distribution` accepts `SINGLE_PRODUCT`, `EXACT_PRODUCT_NAME_GROUP`, `PRODUCT_TYPE_GROUP`, or explicit `ALL`; warehouse scope is either all warehouses or one resolver-confirmed warehouse. Filters and grouping dimensions are closed enums. Product/warehouse IDs must come from resolver or user-selection state. Cross-specification totals use equivalent pieces and weight instead of inventing a common pallet scale. The backend response contains business labels and safe aggregate fields but no product, warehouse, or inventory IDs.

The delegated warehouse-read scope explicitly allows this exact read-only POST path. Other business POST paths remain denied. The endpoint still requires an authenticated current user and does not bypass Spring Security.

## Run with STDIO

Run the packaged jar directly so STDOUT remains reserved for MCP protocol messages:

```powershell
java -jar target/warehouse-mcp-0.1.0.jar
```

STDOUT is reserved for MCP protocol messages. Application logs are written to `WAREHOUSE_MCP_LOG_FILE` or `warehouse-mcp.log`.

## Codex Configuration

Use `.codex/config.toml.example` as a template after packaging:

```toml
[mcp_servers.smart_warehouse]
command = "java"
args = ["-jar", "D:\\Laibin\\LaibinSugarInventory\\warehouse-mcp\\target\\warehouse-mcp-0.1.0.jar"]

[mcp_servers.smart_warehouse.env]
WAREHOUSE_API_BASE_URL = "http://localhost:8080"
WAREHOUSE_API_TOKEN = "REPLACE_WITH_TEST_TOKEN"
```

Do not commit real tokens.

## MCP Tool Registry

工具规划、风险等级、已实现/未实现工具清单见：
`docs/mcp/mcp-tool-registry.md`

## OpenAPI Export

If the backend is running locally, export the current OpenAPI document from the project root:

```powershell
.\scripts\export-openapi.ps1 -BaseUrl http://localhost:8080
```

The script writes UTF-8 without BOM to `docs/openapi.json`.


## AI Assistant Product Goal

`warehouse-mcp` is the internal safe-tool layer for the smart warehouse AI assistant. It should not shape the normal user-facing conversation by exposing tool names, raw ids, raw JSON, or protocol details. The final product goal is documented in:

`docs/agent/ai-assistant-product-goal.md`

Key boundary:

- The Agent Gateway owns user conversation, context memory, tool orchestration, streaming business events, and audit.
- The MCP Server owns safe tool capability and backend API access.
- Normal users should see natural-language answers, business cards, candidate buttons, progress states, and human-readable errors.
- Admin debug mode may show sanitized tool call summaries.
- Write operations remain prohibited unless they follow `preview -> confirmation -> executionToken -> idempotencyKey -> execute` in a future phase.
