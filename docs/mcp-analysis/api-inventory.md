# API Inventory

生成时间：2026-06-13

## 资料来源与差异结论

- 指定文件 `docs/openapi.json` 不存在；仓库内未发现其他 `openapi.json`、Swagger JSON 或 `/v3/api-docs` 静态导出文件。
- 本盘点以 Controller、DTO、Service 实现、权限注解、事务注解和 SQL/migration 为准。
- 由于 OpenAPI 文件缺失，无法进行逐字段 OpenAPI 对比；所有接口均标记为“OpenAPI 缺失”。如后续导出 OpenAPI，应以本文的“代码实际行为”反向核对。
- 全局安全配置：`SecurityConfig` 仅放行 `/api/auth/**`、`/v3/api-docs/**`、`/doc.html`、`/swagger-ui/**`、`/webjars/**`、`/swagger-ui.html`；其余请求默认需要 JWT。方法级权限仅以 `@PreAuthorize` 标注处为准。
- 操作日志：仅加了 `@LogOperation` 的 Controller 方法进入 `OperationLogAspect`，且日志失败会吞掉日志异常并继续执行业务方法。

## Controller 总览

| Controller | Base Path | 主要 Service | 权限特点 | 写操作审计 |
|---|---|---|---|---|
| `AuthController` | `/api/auth` | `AuthService` | 全部公开 | 无 |
| `UserController` | `/api/user` | Mapper 直查 | JWT | 无 |
| `ProductController` | `/api/products` | `ProductService` | 创建/更新/删除有 `product:*` | 产品 CUD 有 |
| `WarehouseController` | `/api/warehouse` | `WarehouseService` | JWT，无方法权限 | 库位 CUD/维护有 |
| `InventoryController` | `/api/inventory` | `InventoryService` | 部分查询有 `record:query` | 无 |
| `InStockController` | `/api/in-stock` | `InStockService` | JWT + `@CheckWarehouseStatus` | 无 |
| `OutStockController` | `/api/out-stock` | `OutStockService` | JWT + `@CheckWarehouseStatus` | 无 |
| `SemiProductRecordController` | `/api/semi-products` | `SemiProductRecordService` | 入库有 `@CheckWarehouseStatus`，无方法权限 | 无 |
| `PalletCodeController` | `/api/pallet-codes` | `PalletCodeService` | JWT，无方法权限 | 多数托盘写操作无 `@LogOperation` |
| `AutoInboundController` | `/api/auto-inbound` | `AutoInboundParseService` / `AutoInboundConfirmService` | JWT，无方法权限 | 无 |
| `AssayController` | `/api/assay` | `AssayService` | 导入/查询/更新/删除需 `quality:test`，详情/存在性无方法权限 | CUD 有 |
| `AssayGroupController` | `/api/assayGroup` | `AssayGroupService` | JWT，无方法权限 | CUD 有 |
| `QualityStandardController` | `/api/quality-standards` | `QualityStandardService` | 强制删除需 `product:delete`，其他无方法权限 | CUD 有 |
| `ProductQualityStandardRelationController` | `/api/product-quality-standards` | `ProductQualityStandardRelationService` | JWT，无方法权限 | CUD 有 |
| `ScreenMeshController` | `/api/screen-mesh` | `ScreenMeshService` | CUD 复用 `product:*`，查询无方法权限 | CUD 有 |
| `EmployeeRosterController` | `/api/employee` | `EmployeeService` | 有 `employee:*` / `user:*` / `rbac:user:view` | 更新/清理有 |
| `RoleController` | `/api/rbac/roles` | `RoleService` | `rbac:role:*` | 无 `@LogOperation` |
| `PermissionController` | `/api/rbac/permissions` | `PermissionService` | `rbac:role:view` | 无 |
| `OperationLogController` | `/api/logs` | `OperationLogService` | `log:view` | 查询日志无 |
| `ProductionOrderController` | `/api/production/orders` | `ProductionOrderService` | `production:*` | 无 `@LogOperation` |

## DTO 盘点

主要入参 DTO 与关键字段：

- 库存入库：`BaseInStockDTO(productId, warehouseName, entryDate, quantity, unit, side, rowNumber, layer, screenMeshId, palletCodeId, returnInStockFlag)`；`InStockRequestDTO` 继承并携带 `semiRecords`；`AddSemiProductRecordDTO` 继承基础入库字段。
- 出库/调拨：`OutStockRequestDTO(productId, warehouseId, quantity, unit, outType, side)`；`TransferOutStockRequestDTO` 追加 `inWarehouseName`。
- 托盘：`BindPalletTaskDTO(code, productId, productStatus, productionDate, quantity, unit, remark)`；`ConfirmPalletInBatchDTO(items)`；`ConfirmPalletInItemDTO(code, warehouseName, entryDate, side, rowNumber, layer, quantity, unit, remark)`；`Create*TaskDTO/Confirm*BatchDTO(codes, remark)`；`CreateTransferTaskDTO(items)`，明细包含 `code,targetWarehouseName,targetSide,remark`；`WarehouseMapBatchOperationDTO(operationType, warehouseId, side, quantity, codes, rowNumber, layer, targetWarehouseName, targetSide, remark)`；`WarehouseMapSlotInboundDTO(code, productId, productStatus, productionDate, warehouseName, side, rowNumber, layer, remark)`。
- 产品/库位：`ProductCreateDTO(productName, productType, status, packagingMethod, weightPerPiece, piecesPerPallet, canStack, screenMeshId)`；`ProductUpdateDTO(productId, productName, productType, status, packagingMethod, weightPerPiece, piecesPerPallet, ScreenMeshId, canStack)`；`WarehouseDTO(warehouseName,maxRows)`；`WarehouseUpdateDTO(id,warehouseName,maxRows)`。
- 化验：`AssaySubmitDTO(productId, selectType, relatedId, sampleDate, colorValue, reducingSugar, dryWeight, conductivityAsh, sucrose, insolubleImpurity, phValue)`；`AssayQueryDTO(productName, productId, sampleDate, isQualified, startDate, endDate, testerName, version, page, size)`；`QualityStandardDTO(productType, standardCode, standardLevel, version, status, standardName, remark, items)`；`QualityStandardItemDTO(metricCode, metricName, minValue, maxValue, unit, compareType, sortOrder, remark)`。
- 自动入库：`AutoInboundParseRequest(rawText, entryDate, operator, parseType)`；`AutoInboundConfirmRequest(confirmedTaskIds, operator, updatedTasks)`。
- 生产订单：`ProductionOrderCreateDTO(orderType, productionDate, plannedMaterialJson, plannedOutputJson, teamName, remark)`；`ProductionMaterialPickDTO(palletCodeIds, palletCodes, remark)`；`ProductionLabelReserveDTO(items[productId, boardCount, pieceCount, qrCount], remark)`；`ProductionFinishDTO(items[productId, productionDate, boardCount, pieceCount], remark)`。
- 查询类 DTO：`InventoryQueryDTO`、`OutProductQueryDTO`、`OutStockBatchQueryDTO`、`PalletCodeQueryDTO`、`PalletTaskQueryDTO`、`SemiProductRecordDTO`、`OperationLogQueryDTO`、`RoleQueryDTO`、`EmployeeQueryDTO` 等均用于分页或条件过滤。

## 接口清单

### 认证与当前用户

| 接口 | 业务目标 | 入参/出参 | 权限 | 写数据 | 事务/幂等/审计 |
|---|---|---|---|---|---|
| `POST /api/auth/web-login` | Web 登录 | `WebLoginDTO` -> `AuthVO` | 公开 | 否/可能更新登录态待确认 | 无显式事务；幂等不适用；无审计 |
| `POST /api/auth/wechat-login` | 微信登录 | `WechatLoginDTO` -> `AuthVO` | 公开 | 可能创建/绑定用户，需看 `AuthServiceImpl` | 无显式事务；无审计 |
| `POST /api/auth/phone-bind` | 手机号绑定 | `WechatPhoneDTO` -> Object | 公开 | 是 | 无显式事务；无审计 |
| `POST /api/auth/manual-bind` | 工号绑定 | `EmployeeVerifyDTO` -> Object | 公开 | 是 | 无显式事务；无审计 |
| `GET /api/user/info` | 当前用户与权限 | JWT -> `UserInfoVO` | JWT | 否 | 只读 |

### 产品、筛网、库位

| 接口 | 业务目标 | Controller -> Service | 权限 | 写数据 | 事务/审计 | OpenAPI |
|---|---|---|---|---|---|---|
| `GET /api/products/{id}` | 按 ID 查产品 | `getProduct` -> `getById` | JWT | 否 | 无 | 缺失 |
| `GET /api/products/product` | 按名称/类型/状态查产品 | `getProductsByCondition` -> `getProductsByCondition` | JWT | 否 | 无 | 缺失 |
| `GET /api/products/product/page` | 分页查产品 | Controller 内分页 | JWT | 否 | 无 | 缺失 |
| `GET /api/products/semi-product-names` | 半成品下拉 | `getSemiProductNames` | JWT | 否 | 无 | 缺失 |
| `GET /api/products/semi-products` | 半成品模糊查 | `getSemiProductsByCondition` | JWT | 否 | 无 | 缺失 |
| `GET /api/products/finished-product-names` | 成品下拉 | `getFinishedProductNames` | JWT | 否 | 无 | 缺失 |
| `GET /api/products/finished-products` | 成品模糊查 | `getFinishedProductsByCondition` | JWT | 否 | 无 | 缺失 |
| `POST /api/products` | 创建产品 | `ProductCreateDTO` -> `createProduct` | `product:create` | 是 | 无显式事务；`@LogOperation` | 缺失 |
| `PUT /api/products` | 更新产品 | `ProductUpdateDTO` -> `updateProduct` | `product:update` | 是 | `@Transactional`; `@LogOperation` | 缺失 |
| `DELETE /api/products/{id}` | 删除产品 | `deleteProduct` | `product:delete` | 是 | `@Transactional`; `@LogOperation` | 缺失 |
| `GET /api/products/getProductWarehouse/{id}` | 查产品存放库位 | `getProductWarehouse` | JWT | 否 | 误标 `@LogOperation(DELETE)`，但实际只读 | 缺失 |
| `GET /api/screen-mesh/list` / `page` / `all` | 查询筛网 | `ScreenMeshService` | JWT | 否 | 无 | 缺失 |
| `POST /api/screen-mesh/add` | 新增筛网 | `ScreenMeshCreateDTO` | `product:create` | 是 | `@LogOperation` | 缺失 |
| `PUT /api/screen-mesh/update` | 更新筛网 | `ScreenMeshUpdateDTO` | `product:update` | 是 | `@LogOperation` | 缺失 |
| `DELETE /api/screen-mesh/delete/{id}` | 删除筛网 | id | `product:delete` | 是 | `@LogOperation` | 缺失 |
| `GET /api/warehouse/query` / `{id}` | 查询库位 | `WarehouseService` | JWT | 否 | 无 | 缺失 |
| `POST /api/warehouse/create` | 新增库位 | `WarehouseDTO` | JWT | 是 | 无显式事务；`@LogOperation` | 缺失 |
| `PUT /api/warehouse/update` | 修改库位 | `WarehouseUpdateDTO` | JWT | 是 | 无显式事务；`@LogOperation` | 缺失 |
| `PUT /api/warehouse/maintain/{id}` | 置维护状态 | id | JWT | 是 | `WarehouseService.setWarehouseToMaintain` 有事务；`@LogOperation` | 缺失 |
| `DELETE /api/warehouse/delete/{id}` | 删除库位 | id | JWT | 是 | 无显式事务；`@LogOperation` | 缺失 |

### 库存、入库、出库、移库

| 接口 | 业务目标 | 关键规则 | 权限 | 写数据 | 事务/确认/幂等 |
|---|---|---|---|---|---|
| `POST /api/in-stock/add` | 成品入库 | 校验产品、库位；半成品用量；最多一个 `useAssay`；按库位侧/排/层生成 `inventory`；可散件拆分 | JWT + `@CheckWarehouseStatus` | 是 | `InStockService.stockIn` 有事务；无预览、无幂等、无显式审计 |
| `POST /api/in-stock/query` | 入库记录分页 | 按 `InStockQueryDTO` | JWT | 否 | 只读 |
| `POST /api/semi-products/add` | 半成品入库 | 校验产品/库位/筛网；查当日化验但允许为空；生成库存和半成品记录 | JWT + `@CheckWarehouseStatus` | 是 | `SemiProductRecordService.addSemiProductRecord` 有事务；无预览/幂等/审计 |
| `POST /api/semi-products/stack-in` | 半成品栈式入库 | 特殊库位按一层候选位置入库 | JWT + `@CheckWarehouseStatus` | 是 | 有事务；无预览/幂等/审计 |
| `POST /api/semi-products/batch-get` | 批量查半成品记录 | id 列表 | JWT | 否 | 只读 |
| `GET /api/semi-products/my-records` | 查本人半成品记录 | 按 openid + 日期 | JWT | 否 | 只读 |
| `POST /api/semi-products/records` | 查询半成品记录 | 条件分页 | JWT | 否 | 只读 |
| `POST /api/out-stock/out` | 产品出库 | 按库位、侧、层和出库优先级取库存；扣减/删除库存；按批次写 `out_stock`；要求出库批次有化验 | JWT + `@CheckWarehouseStatus` | 是 | `@Transactional`; 无预览/幂等/审计 |
| `POST /api/out-stock/stack-out` | 栈式出库 | 按栈式顺序扣减库存 | JWT + `@CheckWarehouseStatus` | 是 | `@Transactional`; 无预览/幂等/审计 |
| `POST /api/out-stock/transferOut` | 调拨出库并再入库 | 先出库，再构造成品入库请求写入目标库位 | JWT + `@CheckWarehouseStatus` | 是 | `@Transactional`; 无预览/幂等/审计 |
| `POST /api/out-stock/records` | 出库记录查询 | 条件分页 | JWT | 否 | 只读 |
| `POST /api/inventory/summary` | 库存摘要 | `InventoryQueryDTO` | `record:query` | 否 | 只读 |
| `GET /api/inventory/stock` / `stock/page` | 产品库存汇总 | 支持产品状态、名称 | JWT | 否 | 只读 |
| `GET /api/inventory/prepare-pool-balance` | 历史备料池余额 | 查询历史生产占用余额 | JWT | 否 | 只读 |
| `GET /api/inventory/warehouses` | 库位容量 | 容量百分比 | `record:query` | 否 | 只读 |
| `GET /api/inventory/query` | 库位容量分页 | query params + 可选 body ids | `record:query` | 否 | 只读；与 POST 同 path |
| `POST /api/inventory/query` | 批量查库位容量 | `OutStockBatchQueryDTO` | `record:query` | 否 | 只读；与 GET 同 path |
| `POST /api/inventory/qualified-warehouses` | 查可出库库位 | 产品/标准/筛网/日期条件 | `record:query` | 否 | 只读 |
| `POST /api/inventory/qualified-inventory/{warehouseId}` / `page` | 查库位库存明细 | 库位 + 出库条件 | JWT | 否 | 只读 |
| `GET /api/inventory/warehouses/{warehouseId}/recent-operations` | 库位最近流转 | 限制条数 | `record:query` | 否 | 只读 |

### 托盘码与任务

| 接口 | 业务目标 | 写数据 | 事务/风险 | 适合 MCP |
|---|---|---|---|---|
| `POST /api/pallet-codes/generate` | 批量生成托盘码 | 是 | `@Transactional`; 缺少方法权限；普通管理写操作 | 管理类 L4，不纳入第一版执行 |
| `POST /api/pallet-codes` | 托盘码分页 | 否 | 只读 | 可组合进查询工具 |
| `POST /api/pallet-codes/fixed-product/bind` | 固定产品码批量绑定 | 是 | `for update` 锁 FREE 码；无方法权限 | L4 管理 |
| `GET /api/pallet-codes/fixed-product/pool` | 固定产品码池 | 否 | 只读 | L1 |
| `POST /api/pallet-codes/fixed-product/qrcode-labels/pdf` | 导出固定产品码 PDF | 否/文件输出 | 只允许 FREE 固定码 | L1/L2 辅助 |
| `POST /api/pallet-codes/fixed-product/activate/pdf` | 打印并启用固定码，创建入库任务 | 是 | 事务；会改变码状态并建任务 | L3/L4，不做第一版执行 |
| `POST /api/pallet-codes/tasks/list` | 托盘任务分页 | 否 | 只读 | L1 |
| `GET /api/pallet-codes/{code}/flows/cycles` / `flows` | 托盘流转时间线 | 否 | 只读 | L1 |
| `POST /api/pallet-codes/flows/delete` | 删除历史流转 | 是 | 仅 180 天外且非当前轮次；无方法权限 | L4 |
| `POST /api/pallet-codes/tasks/confirm` | 批量确认入库任务 | 是 | 事务；失败回滚；无预览/幂等 | L3，需新增预览和 token |
| `POST /api/pallet-codes/invalid` / `invalid/restore` | 作废/恢复二维码 | 是 | 事务；状态限制 | L4 |
| `POST /api/pallet-codes/tasks/cancel` | 取消当前轮次待入库任务 | 是 | 事务 | L3/L4 |
| `GET /api/pallet-codes/parse` | 解析托盘码 | 否 | 校验码并返回状态 | L1 |
| `GET/POST qrcode*` | 生成二维码/标签 | 否 | 文件输出 | L1 辅助 |
| `GET /api/pallet-codes/{code}/assay` / `inventory` | 查托盘化验/位置 | 否 | 只读 | L1 |
| `POST /api/pallet-codes/bind` | 创建入库任务 | 是 | FREE -> PENDING，递增轮次 | L3，需预览/确认 |
| `POST /api/pallet-codes/tasks/semi-bind` | 历史成品任务登记半成品用量 | 是 | 注释称历史停用但实现仍有逻辑 | 不建议暴露 |
| `POST /api/pallet-codes/semi/out/create` / `confirm` | 半成品普通出库任务 | 是 | create/confirm 两步，但无幂等 | L3 |
| `POST /api/pallet-codes/semi/prepare/create` / `confirm` / `consume/confirm` | 历史生产占用 | 是 | 实现调用 `rejectLegacyProductionFlowWrite`，旧流程停用 | 不暴露 |
| `POST /api/pallet-codes/finish/out/create` / `confirm` | 成品出库任务 | 是 | create/confirm 两步 | L3 |
| `POST /api/pallet-codes/transfer/create` / `confirm` | 托盘调拨任务 | 是 | 目标库位锁定并分配；有冲突重试 | L3 |
| `POST /api/pallet-codes/warehouse-map/tasks/create` | 平面图批量建出库/调拨任务 | 是 | 批量任务；旧 PREPARE 拒绝 | L4 |
| `POST /api/pallet-codes/warehouse-map/slot/inbound` | 平面图单板入库 | 是 | 创建并直接确认到指定格子 | L3，需预览 |

### 自动入库

| 接口 | 业务目标 | 关键行为 | 风险 |
|---|---|---|---|
| `POST /api/auto-inbound/parse` | 将报数文本解析为任务 | 调 LLM；生成 `batchId`；任务缓存 Redis 24h；返回风险/缺失项 | L2 预览性质，但依赖 LLM ID 解析，需增加后端确定性解析 |
| `GET /api/auto-inbound/history` | 当前用户解析历史 | Redis zset 最近 20 条 | L1 |
| `GET /api/auto-inbound/{batchId}` | 查询解析批次 | Redis 读取，过期报错 | L1/L2 |
| `POST /api/auto-inbound/{batchId}/confirm` | 确认报数入库 | 当前实现拒绝成品任务；半成品执行入库，分配固定码；保存生产报数留档和 Redis 结果 | L3，第一版 MCP 不执行 |

### 化验、标准、关联

| 接口 | 业务目标 | 权限 | 写数据 | 关键规则 |
|---|---|---|---|---|
| `POST /api/assay/import` | 导入化验 | `quality:test` | 是 | 单产品或批量化验组；按产品 + 日期递增版本；执行标准判定 |
| `POST /api/assay/exists` | 查化验是否存在 | JWT | 否 | 产品 + 日期 |
| `POST /api/assay/query` | 分页查化验 | `quality:test` | 否 | 返回判定详情 |
| `GET /api/assay/{id}` | 化验详情 | JWT | 否 | 查不到抛业务异常 |
| `GET /api/assay/by-product-date` | 产品日期查最新化验 | JWT | 否 | 不存在返回空 |
| `POST /api/assay/{id}` | 更新化验 | `quality:test` | 是 | 实际插入新版本，再更新版本号；审计 |
| `DELETE /api/assay/{id}` | 删除化验 | `quality:test` | 是 | 被库存/记录引用时删除失败 |
| `GET/POST/PUT/DELETE /api/quality-standards*` | 标准管理 | 多数 JWT，强制删需 `product:delete` | CUD 是 | 必须完整配置 7 项固定指标；产品类型匹配 |
| `/api/product-quality-standards/*` | 产品-标准关系 | JWT | CUD 是 | 不允许产品最后一个启用标准关系被删除 |
| `/api/assayGroup/*` | 批量化验组 | JWT | CUD 是 | 供批量导入选择相关产品 |

### RBAC、员工、日志、生产订单

| 模块 | 接口范围 | 业务能力 | 权限 |
|---|---|---|---|
| 员工 | `/api/employee/import/add/query/update/clearResigned` | 名册导入、增查改、清理离职 | `employee:*`、`user:*`、`rbac:user:view` |
| 角色 | `/api/rbac/roles` | 角色分页、详情、创建、更新、权限分配、状态、删除 | `rbac:role:*` |
| 权限 | `/api/rbac/permissions` | 权限点列表 | `rbac:role:view` |
| 操作日志 | `/api/logs/query` | 操作日志分页 | `log:view` |
| 生产订单 | `/api/production/orders` | 订单、领料、产出、预打印标签、确认生产结束 | `production:*` |

## 阶段一一致性检查

- `docs/openapi.json` 缺失，无法对 OpenAPI 做逐项差异；本文以代码为准。
- API 与 AGENTS 规范存在差异：多个写接口没有方法级 `@PreAuthorize`，多个业务写接口无 `@LogOperation`，部分 Controller 直接返回 PO（如 `Product`、`Warehouse`、`EmployeeRoster`），不完全符合“Controller 只接收/返回 DTO/VO”。
- `ProductController#getProductWarehouse` 是只读接口但标了 `@LogOperation(type=DELETE)`，会造成误审计风险。
- `InventoryController` 同时定义 GET 和 POST `/api/inventory/query`，HTTP method 可区分，但 OpenAPI 生成和前端调用需要特别注意。
