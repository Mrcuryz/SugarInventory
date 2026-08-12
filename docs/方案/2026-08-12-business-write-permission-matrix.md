# 业务写权限矩阵收口

## 背景与目标

2026-08-12 审查发现，部分写接口仅校验登录态、沿用过宽的旧权限，或者前端仍对无权用户展示写按钮。本轮将库位、传统库存、二维码、筛网、化验、化验组、质量标准、自动入库和 Agent 审核的写权限收口到明确的业务权限码。

本轮只在本地/影子验收环境执行。未连接生产库，未修改生产数据。

## 权限矩阵

| 业务域 | 读权限 | 写权限 |
| --- | --- | --- |
| 库位 | `warehouse:view` | `warehouse:create` / `warehouse:update` / `warehouse:delete` / `warehouse:status` |
| 传统库存作业 | `inventory:view` / `document:view` / `record:query` | `inventory:inbound` / `inventory:outbound` / `inventory:transfer` |
| 二维码 | `qrcode:view` / `qrcode:pool_view` | `qrcode:generate` / `qrcode:print` / `qrcode:bind_fixed_product` / `qrcode:activate` / `qrcode:invalidate` / `qrcode:flow_delete` |
| 任务 | `task:view` | `task:create` / `task:confirm` / `task:cancel` |
| 筛网 | `screen_mesh:view` | `screen_mesh:create` / `screen_mesh:update` / `screen_mesh:delete` |
| 化验 | `assay:view` | `assay:create` / `assay:update` / `assay:delete`；保留 `quality:test` 作为历史化验写权限的兼容入口 |
| 批量化验组 | `assay_group:view` | `assay_group:create` / `assay_group:update` / `assay_group:delete` |
| 质量标准 | `quality_standard:view` | `quality_standard:create` / `quality_standard:update` / `quality_standard:delete` / `quality_standard:bind_product` |
| Agent 审核 | `agent:review:view` | `agent:review:update` |

Controller 方法上使用精确的 `@PreAuthorize`；前端使用同一权限码控制按钮、弹窗写操作和打印/下载入口。前端隐藏仅用于体验，最终安全边界仍由后端强制。

## 默认角色赋权

- `ADMIN`：新增权限全部赋予，保持管理员现有业务能力。
- `STAFF`：为了兼容现有传统出入库工作流，仅补齐 `inventory:inbound` / `inventory:outbound` / `inventory:transfer`。
- `QC`：不默认赋予上述传统库存写权限。
- 其他角色：不自动扩权，由管理员在角色权限页面显式配置。

数据库变更见 `migrations/2026-08-12-complete-business-write-permission-matrix.sql`，脚本使用权限码唯一键和 `INSERT ... SELECT ... WHERE NOT EXISTS` 保持可重复执行。

## 自动化验证

- `BusinessWritePermissionMatrixTest`：校验 Controller 权限表达式、新增权限码及默认角色赋权。
- `InventoryDistributionAuthorizationTest`：校验库存分布查询与 `inventory:view` 读边界一致。
- 完整后端测试：`mvn test`，396 个测试全部通过。
- 前端生产构建：`npm run build` 通过。

## 真实浏览器验收

验收使用 `scripts/prepare-permission-matrix-browser-uat.ps1` 在影子库创建临时只读角色、只读用户和管理员用户，并通过真实登录页完成以下验收：

1. 只读用户可正常打开库位、二维码、筛网、化验组和质量标准页面。
2. 只读用户不可见编辑、新增、删除、作废、打印和下载等写操作；二维码详情弹窗的打印/下载入口也被隐藏。
3. 在同一浏览器登录态下，库位查询返回业务码 200；传统出库、库位更新和有效二维码生成请求均返回业务码 403、`权限不足`。
4. 管理员用户可见上述写按钮和二维码打印/下载入口。
5. 管理员向不存在的库位 ID 发送结构合法的更新请求，请求已穿过权限层，在业务层因对象不存在失败，未修改数据。
6. 正常页面导航期间无 JavaScript console error。

验收后删除临时用户和角色，保留权限迁移结果。

## 后续项

管理员更新不存在库位时，当前 Service 会因直接访问空对象而进入通用 500 分支。该问题不影响本轮权限结论，已归入后续“异常、DTO/VO 与生产化债务”项统一修复。
