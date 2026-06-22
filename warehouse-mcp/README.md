# Warehouse MCP Server

`warehouse-mcp` is a minimal read-only MCP Server for the Laibin warehouse system. It uses Spring AI MCP Server STDIO transport and calls the existing warehouse backend through fixed HTTP read endpoints. It does not connect to the database and does not expose write, execute, arbitrary HTTP, or arbitrary SQL tools.

## Tools

| Tool | Purpose | Backend mapping |
| --- | --- | --- |
| `resolve_products` | Resolve a product name/id query into unique, ambiguous, or not-found candidates. | `GET /api/products/{id}`, `GET /api/products/product` |
| `resolve_warehouses` | Resolve a warehouse name/id query into unique, ambiguous, or not-found candidates. | `GET /api/warehouse/{id}`, `GET /api/warehouse/query` |
| `get_inventory_overview` | Read paged product inventory summary and calculated totals. | `GET /api/products/{id}`, `GET /api/inventory/stock/page` |
| `get_warehouse_status` | Read warehouse capacity, inventory details, and recent operations. | `GET /api/warehouse/{id}`, `GET /api/inventory/warehouses`, `POST /api/inventory/qualified-inventory/{warehouseId}/page`, `GET /api/inventory/warehouses/{warehouseId}/recent-operations` |

`POST /api/inventory/qualified-inventory/{warehouseId}/page` is used only as an existing read query endpoint. No business write endpoints are called.

## Environment

Set both values before starting the server:

```powershell
$env:WAREHOUSE_API_BASE_URL = "http://localhost:8080"
$env:WAREHOUSE_API_TOKEN = "<test-or-dev-token>"
```

Optional:

```powershell
$env:WAREHOUSE_API_TIMEOUT = "5s"
$env:WAREHOUSE_MCP_LOG_FILE = "D:\Laibin\LaibinSugarInventory\warehouse-mcp\warehouse-mcp.log"
```

## Build and Test

```powershell
cd warehouse-mcp
mvn test
mvn package
```

## Run with STDIO

```powershell
cd warehouse-mcp
mvn spring-boot:run
```

Or run the packaged jar:

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

## OpenAPI Export

If the backend is running locally, export the current OpenAPI document from the project root:

```powershell
.\scripts\export-openapi.ps1 -BaseUrl http://localhost:8080
```

The script writes UTF-8 without BOM to `docs/openapi.json`.

