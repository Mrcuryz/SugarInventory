# Warehouse MCP Server

`warehouse-mcp` is a minimal read-only MCP Server for the Laibin warehouse system. It uses Spring AI MCP Server STDIO transport and calls the existing warehouse backend through fixed HTTP read endpoints. It does not connect to the database and does not expose write, execute, arbitrary HTTP, or arbitrary SQL tools.

## Tools

| Tool | Purpose | Backend mapping |
| --- | --- | --- |
| `resolve_products` | Resolve a product name/id query into unique, ambiguous, or not-found candidates. | `GET /api/products/{id}`, `GET /api/products/product` |
| `resolve_warehouses` | Resolve a warehouse name/id query into unique, ambiguous, or not-found candidates. | `GET /api/warehouse/{id}`, `GET /api/warehouse/query` |
| `get_inventory_overview` | Read paged product inventory summary and calculated totals. | `GET /api/products/{id}`, `GET /api/inventory/stock/page` |
| `get_inventory_distribution` | Read controlled product scopes with warehouse, status, assay, pallet, date, and grouping filters. | `POST /api/inventory/distribution` (read-only aggregate) |
| `query_assay_records` | Read controlled assay record lists and summaries by product scope, sample-date range, and judge status. | `POST /api/assay/records/query` (read-only aggregate) |
| `get_assay_report_detail` | Read one controlled assay report detail by opaque `reportRef`. | `POST /api/assay/report-detail/query` (read-only detail) |
| `query_assay_abnormalities` | Read grouped assay quality abnormalities by product scope, sample-date range, abnormal type, and grouping. | `POST /api/assay/abnormalities/query` (read-only aggregate) |
| `query_products_without_recent_assay` | Read current inventory groups without a valid assay in a controlled date range. | `POST /api/assay/products-without-recent-assay/query` (read-only aggregate) |
| `query_assay_standard_coverage` | Read current inventory product groups without an effective quality standard. | `POST /api/assay/standard-coverage/query` (read-only aggregate) |
| `query_qr_code_lifecycle` | Read one QR / pallet code lifecycle summary with inventory, assay, print, flow, and risk labels. | `POST /api/pallet-codes/lifecycle/query` (read-only aggregate) |
| `query_printed_not_inbound_codes` | Read printed label batches and QR codes not completed inbound, grouped by batch, order, or product. | `POST /api/pallet-codes/printed-not-inbound/query` (read-only aggregate) |
| `query_pallet_anomalies` | Read controlled pallet, inventory, and flow inconsistency groups. | `POST /api/pallet-codes/anomalies/query` (read-only aggregate) |
| `query_pallet_flow_records` | Read paged pallet flow records by code, product, warehouse, date, or event type. | `POST /api/pallet-codes/flow-records/query` (read-only aggregate) |
| `query_qr_batch_inbound_completion` | Read printed/inbound counts and completion rate for a label batch, order, product, or date scope. | `POST /api/pallet-codes/batch-inbound-completion/query` (read-only aggregate) |
| `resolve_production_entities` | Resolve a production order or boiling batch to short-lived user-bound entity references. | `POST /api/production/agent-read/entities/resolve` |
| `query_production_order_progress` | Read current order material, output, label, QR binding, and inbound progress from a controlled orderRef. | `POST /api/production/agent-read/orders/progress/query` |
| `query_boiling_batch_trace` | Read only registered boiling batch detail, usage, graph edges, and timeline from a controlled batchRef. | `POST /api/production/agent-read/boiling-batches/trace/query` |
| `query_material_pick_trace` | Read registered actual material picks and pallet sources for a controlled production order. | `POST /api/production/agent-read/orders/material-pick-trace/query` |
| `query_production_label_completion` | Read label reservation/use/recycle and QR binding/inbound completion for a controlled production order. | `POST /api/production/agent-read/orders/label-completion/query` |
| `query_in_process_materials` | Read current registered in-process semi-finished material records with controlled filters and pagination. | `POST /api/production/agent-read/materials/in-process/query` |
| `query_material_candidates` | Read current semi-finished inventory candidates for a controlled production order without making a pick recommendation. | `POST /api/production/agent-read/orders/material-candidates/query` |
| `query_pallet_tasks` | Read current pallet tasks with controlled filters, explicit task permission, and no task mutation. | `POST /api/logistics/agent-read/pallet-tasks/query` |
| `query_stock_documents` | Read one explicit inbound, outbound, or semi-product document source without merging it into a fabricated global ledger. | `POST /api/logistics/agent-read/stock-documents/query` |
| `query_auto_inbound_batches` | Read the current user's recent non-expired intelligent reporting batches without confirming inbound. | `POST /api/logistics/agent-read/auto-inbound/batches/query` |
| `get_auto_inbound_batch_detail` | Read a selected current-user batch through an opaque user-bound reference, omitting raw text and internal IDs. | `POST /api/logistics/agent-read/auto-inbound/batches/detail/query` |
| `query_warehouse_capacity_distribution` | Read current capacity, occupancy and remaining-capacity facts; display bands are explicitly not business risk decisions. | `POST /api/warehouse/agent-read/capacity-distribution/query` |
| `query_warehouse_recent_operations` | Read recorded inbound, outbound and transfer pallet-flow events for one warehouse or all warehouses; not a complete audit ledger. | `POST /api/warehouse/agent-read/recent-operations/query` |
| `query_warehouse_mixed_storage_facts` | Read current same-warehouse multiple-product or multiple-specification facts without deciding mixed-storage risk. | `POST /api/warehouse/agent-read/mixed-storage-facts/query` |
| `query_product_catalog` | Read current product master data without inventory, quality, or production-availability claims. | `POST /api/master-data/agent-read/products/query` |
| `get_product_detail` | Read one exactly named product's current configuration and conversion summary without internal IDs. | `POST /api/master-data/agent-read/products/detail/query` |
| `query_screen_mesh_catalog` | Read the current screen-mesh catalog without exposing maintenance identities or modifying configuration. | `POST /api/master-data/agent-read/screen-meshes/query` |
| `query_assay_groups` | Read current assay product-group configuration without treating groups as standards or qualification. | `POST /api/quality/agent-read/assay-groups/query` |
| `query_quality_standard_catalog` | Read current quality-standard codes, versions, states, and summary counts. | `POST /api/quality/agent-read/standards/query` |
| `get_quality_standard_detail` | Read one exact standard code/version and its metric configuration without making the final quality decision. | `POST /api/quality/agent-read/standards/detail/query` |
| `query_product_standard_relations` | Read current product-standard bindings, defaults, priorities, and effective periods without changing them. | `POST /api/quality/agent-read/product-standard-relations/query` |
| `query_employee_roster` | Read the current employee roster with masked mobile numbers and no login/binding credentials. | `POST /api/administration/agent-read/employees/query` |
| `query_roles` | Read the current role catalog and aggregate counts without internal IDs. | `POST /api/administration/agent-read/roles/query` |
| `get_role_permission_summary` | Read one exact role permission summary; actual access remains subject to RBAC. | `POST /api/administration/agent-read/roles/permission-summary/query` |
| `search_operation_logs` | Search recorded business-operation summaries without old/new field values. | `POST /api/audit/agent-read/operation-logs/query` |
| `query_agent_tool_audit` | Query safe Agent tool-call result/error categories and duration without arguments or internal IDs. | `POST /api/audit/agent-read/agent-tool-audit/query` |
| `query_agent_answer_reviews` | Query safe Agent answer-review status summaries without original questions, answers, tools, or decision snapshots. | `POST /api/audit/agent-read/agent-answer-reviews/query` |
| `query_inventory_ledger` | Query current inventory rows and locations; explicitly not a historical movement ledger or qualification result. | `POST /api/inventory/agent-read/ledger/query` |
| `query_prepare_pool_balance` | Query current positive historical semi-finished prepare-pool balances without reserving or consuming them. | `POST /api/inventory/agent-read/prepare-pool-balance/query` |
| `query_fixed_product_qr_pool` | Query current fixed-product QR pool status without binding, printing, activating, invalidating, restoring, or changing inventory. | `POST /api/pallet-codes/agent-read/fixed-product-pool/query` |
| `get_warehouse_status` | Read warehouse capacity, inventory details, and recent operations. | `GET /api/warehouse/{id}`, `GET /api/inventory/warehouses`, `POST /api/inventory/qualified-inventory/{warehouseId}/page`, `GET /api/inventory/warehouses/{warehouseId}/recent-operations` |
| `get_pallet_status` | Read pallet code status, current inventory position, assay information, flow cycles, and flow details. | `GET /api/pallet-codes/parse`, `GET /api/pallet-codes/{code}/inventory`, `GET /api/pallet-codes/{code}/assay`, `GET /api/pallet-codes/{code}/flows/cycles`, `GET /api/pallet-codes/{code}/flows` |
| `get_assay_status` | Read an assay by id, or by product and production date, including judge result, failed metrics, and applied standard details. | `GET /api/assay/{id}`, `GET /api/assay/by-product-date` |

`POST /api/inventory/qualified-inventory/{warehouseId}/page`, `POST /api/inventory/distribution`, the five assay query paths, and the five `/api/pallet-codes/*/query` paths are used only as existing or dedicated read query endpoints. No business write endpoints are called. The server still does not expose preview, execute, SQL, arbitrary HTTP proxy, direct database, pallet mutation, assay mutation, label mutation, or quality-standard mutation tools.

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

M1.2 did not add a login tool or write-capable business MCP tools. M1.4 now exposes the six original read tools, `get_inventory_distribution`, five assay analysis tools, and five QR / pallet lifecycle tools, so the server exposes seventeen read-only tools. It still exposes no login, preview, execute, SQL, arbitrary HTTP proxy, direct database, or write tools.
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

`query_assay_records` accepts the same controlled product scopes plus `EXACT`, `LAST_DAYS`, or `RANGE` sample-date filters and closed judge statuses: `ANY`, `PASS`, `FAILED`, `NO_STANDARD`, and `MULTIPLE_CANDIDATES`. `FAILED` is mapped to the backend judge result `FAIL`. Results are paged, default to `sampleDate DESC`, and return safe business labels rather than internal assay or product IDs.

`query_assay_abnormalities` uses the same controlled product scopes and sample-date filters, but only aggregates existing assay records with `FAILED`, `NO_STANDARD`, or `MULTIPLE_CANDIDATES` statuses. It does not count `NO_ASSAY`.

`query_products_without_recent_assay` uses controlled product and warehouse scopes, defaults to `CURRENT_INVENTORY`, and finds current inventory groups whose product has no assay sample date in the requested range. It keeps no-assay separate from no-standard and failed-assay records.

`query_assay_standard_coverage` uses controlled product scopes and finds current inventory products that do not have an effective quality standard relation. First implementation supports `PRODUCT_WITHOUT_STANDARD`; `ASSAY_WITHOUT_STANDARD` and `UNUSED_STANDARD` remain reserved until business rules are confirmed.

The M1.4c lifecycle tools use these first-version semantics: printed means `production_order_label_batch.printed_at`; inbound means the output code has an inventory association, an inbound timestamp, or `INSTOCK` status. The application has no independent QR scan-log source, so `VOID_CODE_SCANNED` returns an evidence-gap note instead of an invented anomaly result.

The delegated warehouse-read scope explicitly allows this exact read-only POST path. Other business POST paths remain denied. The endpoint still requires an authenticated current user and does not bypass Spring Security.

## Run with STDIO

Run the packaged jar directly so STDOUT remains reserved for MCP protocol messages:

```powershell
java -jar target/warehouse-mcp-0.1.0-exec.jar
```

STDOUT is reserved for MCP protocol messages. Application logs are written to `WAREHOUSE_MCP_LOG_FILE` or `warehouse-mcp.log`.

## Codex Configuration

Use `.codex/config.toml.example` as a template after packaging:

```toml
[mcp_servers.smart_warehouse]
command = "java"
args = ["-jar", "D:\\Laibin\\LaibinSugarInventory\\warehouse-mcp\\target\\warehouse-mcp-0.1.0-exec.jar"]

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
