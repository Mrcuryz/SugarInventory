# MCP Acceptance Log

## 2026-06-23 - `get_inventory_overview` Quantity Field Semantics

Scope: M1 read-only `get_inventory_overview` output contract.

Accepted behavior:

- `get_inventory_overview` no longer returns `totalBoards`, `totalPieces`, or `stockInfo`.
- MCP output uses `rawFullPallets`, `rawLoosePieces`, `normalizedPallets`, `normalizedLoosePieces`, `totalEquivalentPieces`, and `displayStockInfo`.
- `rawFullPallets` only represents the backend raw full-pallet count.
- `rawLoosePieces` only represents the backend raw loose-piece count.
- `normalizedPallets` and `normalizedLoosePieces` represent the inventory display quantity after converting raw full pallets and loose pieces by the product `piecesPerPallet`.
- `totalEquivalentPieces` represents the equivalent total piece count.
- `displayStockInfo` is the user-facing pallet/piece text.
- Agent answers about inventory should prioritize `normalizedPallets + normalizedLoosePieces + totalEquivalentPieces`.
- Raw fields are diagnostic/source fields and must not be used as the primary user-facing stock quantity.

Example:

- Backend raw quantity: `rawFullPallets=10`, `rawLoosePieces=70`.
- Product conversion: `piecesPerPallet=40`.
- MCP normalized output: `normalizedPallets=11`, `normalizedLoosePieces=30`, `totalEquivalentPieces=470`, `displayStockInfo=11板30件`.

Restrictions confirmed:

- No new MCP tools.
- No preview or execute tools.
- No SQL tool, HTTP proxy tool, direct database access, or write endpoint call.
## 2026-06-26 - M1.3 Agent Assistant Frontend Experience

Scope: Web Agent Gateway and frontend assistant behavior for the existing six L1 read-only MCP tools.

Accepted behavior:

- The normal assistant UI renders product and warehouse choices as business candidate cards.
- The normal assistant UI does not render `toolName`, `SUCCESS`, `productId`, or `warehouseId`.
- Selecting a candidate card is sent as `pageContext.selectedOption` in the same conversation context, not as a new visible user message.
- Selected product labels are sanitized before user-facing answers, so labels such as `(#84)` are not displayed in normal answers.
- Follow-up questions such as `这些主要存放在哪些库位？` reuse the last uniquely selected product context and do not require the user to re-enter the product name.
- Follow-up questions such as `它今天有没有化验？` reuse the last uniquely selected product context and query today's assay status when the user says `今天`.
- Warehouse expressions such as `2号库位` continue to go through warehouse resolver normalization before querying warehouse status.
- Admin users may enable a debug mode to view tool call summaries; normal users do not receive or see tool call details in the Agent message response.
- Conversation context is cleared when the Agent session is revoked.

Restrictions confirmed:

- No new MCP business tools.
- No login MCP tool.
- No preview or execute tools.
- No SQL tool, HTTP proxy tool, direct database access, or write endpoint call.
- Token, Authorization headers, and backend stack traces must not be rendered in the frontend.