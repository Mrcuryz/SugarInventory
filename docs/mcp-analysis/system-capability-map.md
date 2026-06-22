# System Capability Map

生成时间：2026-06-13

## 能力分层

| 领域 | 已有后端能力 | 主要入口 | 数据对象 | Agent 可用性 |
|---|---|---|---|---|
| 身份认证 | Web/微信登录、手机号/工号绑定、当前用户信息 | `/api/auth/*`, `/api/user/info` | `user`, `employee_roster`, `role`, `permission` | 仅用于会话上下文，不设计业务 Tool |
| 产品资料 | 产品查询、创建、更新、删除、半成品/成品下拉 | `/api/products/*` | `product`, `screen_mesh` | 查询可用；管理写操作第一版不执行 |
| 筛网 | 筛网查询、维护 | `/api/screen-mesh/*` | `screen_mesh` | 查询可用；管理写操作第一版不执行 |
| 库位资料 | 库位查询、创建、更新、删除、维护状态 | `/api/warehouse/*`, `/api/inventory/warehouses*` | `warehouse` | 查询可用；管理写操作第一版不执行 |
| 库存查询 | 产品库存、库位容量、库位明细、托盘位置、最近操作 | `/api/inventory/*`, `/api/pallet-codes/{code}/inventory` | `inventory`, `pallet_flow_record` | 适合作为 L1 查询工具 |
| 成品入库 | 传统成品入库、托盘任务确认入库、平面图单板入库 | `/api/in-stock/add`, `/api/pallet-codes/tasks/confirm`, `/api/pallet-codes/warehouse-map/slot/inbound` | `in_stock`, `inventory`, `pallet_task`, `pallet_flow_record` | 需要预览/确认/幂等后才可执行 |
| 半成品入库 | 普通/栈式半成品入库、自动入库确认半成品 | `/api/semi-products/add`, `/api/semi-products/stack-in`, `/api/auto-inbound/{batchId}/confirm` | `semi_product_record`, `inventory`, `production_report_record` | 解析/预览可用；执行需新增接口治理 |
| 出库 | 传统出库、栈式出库、托盘普通/成品出库任务 | `/api/out-stock/*`, `/api/pallet-codes/*/out/*` | `out_stock`, `inventory`, `pallet_task`, `pallet_flow_record` | 查询/预览优先；执行需确认和幂等 |
| 移库/调拨 | 旧调拨出库再入库、托盘级调拨任务、平面图调拨任务 | `/api/out-stock/transferOut`, `/api/pallet-codes/transfer/*`, `/api/pallet-codes/warehouse-map/tasks/create` | `inventory`, `warehouse`, `pallet_task`, `pallet_flow_record` | 需要新增调拨预览 |
| 托盘码 | 生成、解析、码池、固定码绑定、作废、恢复、标签 PDF | `/api/pallet-codes/*` | `pallet_code`, `production_order_label_code` | 查询可用；管理写操作不进第一版 |
| 化验 | 化验导入、查询、详情、按产品日期查询、更新、删除 | `/api/assay/*` | `assay` | 查询可用；写操作不进第一版 |
| 质量标准 | 标准和产品关系维护，化验判定 | `/api/quality-standards/*`, `/api/product-quality-standards/*` | `quality_standards`, `quality_standard_item`, `product_quality_standard_relation` | 标准查询/解释可用；管理写操作不进第一版 |
| 智能报数 | 文本解析、批次历史、批次查询、确认半成品入库 | `/api/auto-inbound/*` | Redis batch, `production_report_record`, `semi_prepare_pool_balance` | parse/get 可作为 L2；confirm 暂不执行 |
| 生产订单 | 订单创建、领料、产出、预分配标签、确认生产结束、标签打印 | `/api/production/orders/*` | `production_order*`, `pallet_code`, `pallet_task` | 查询/预览可用；写操作需专门设计 |
| 审计 | 操作日志查询、部分 CUD 自动审计 | `/api/logs/query`, `@LogOperation` | `operation_log` | L1 查询可用；审计覆盖需补齐 |

## 当前业务主链路

### 传统库存链路

1. 产品、筛网、库位基础资料维护。
2. 半成品入库或成品入库写入 `inventory`。
3. 出库按库位和出库策略扣减 `inventory` 并写 `out_stock`。
4. 旧调拨通过先出库再入库完成。

### 托盘码链路

1. 生成托盘码或准备固定产品二维码。
2. 扫码绑定产品并创建入库任务，或固定码打印启用后创建任务。
3. 确认入库写 `inventory`，托盘状态进入 `INSTOCK`。
4. 创建出库/调拨任务。
5. 确认出库释放托盘或确认调拨移动库存。
6. 流转写入 `pallet_flow_record`，按 `cycle_no` 保留历史。

### 生产订单链路

1. 创建生产订单。
2. 成品订单领用在库半成品托盘，生成实际材料记录并释放半成品托盘。
3. 预分配固定产品二维码标签。
4. 确认生产结束，根据实际产出核销预打印标签，创建待入库托盘任务。
5. 扫码确认产出入库，回写产出码入库进度。

### 智能报数链路

1. 用户提交自然语言报数文本。
2. LLM 解析为半成品/成品任务，后端校验产品、库位、数量、化验等缺失项。
3. 批次缓存 Redis 24 小时。
4. 当前确认接口只允许半成品任务执行入库；成品任务要求走生产订单流程。
5. 生产消耗信息以留档为主，历史备料池余额足够时可扣减。

## 能力成熟度

| 能力 | 成熟度 | 说明 |
|---|---|---|
| 库存只读查询 | 高 | 接口较多，能查产品库存、库位容量、明细、托盘位置 |
| 托盘流转查询 | 高 | 有轮次、明细、最近库位操作 |
| 化验判定查询 | 中高 | 判定逻辑集中，标准快照可追溯 |
| 生产订单查询 | 中高 | 详情聚合材料、产出、标签批次 |
| 自然语言报数解析 | 中 | 有 LLM 解析和风险项，但缺确定性名称解析和 Agent 专用预览 |
| 入库/出库/调拨执行 | 中 | 事务和部分锁已存在，但缺幂等、确认 token、统一权限和审计 |
| 管理类配置 | 中 | CRUD 能力完整，但权限覆盖不一致 |
| Agent 聚合能力 | 低 | REST 接口偏页面/流程按钮，缺面向业务目标的聚合 API |

## 适合第一版 MCP 的能力

第一版只提出 L0、L1、L2 计划：

- L0：业务规则说明、接口帮助、风险说明。
- L1：产品/库位/库存/托盘/化验/生产订单/操作日志查询。
- L2：自然语言报数解析预览、入库/出库/调拨可行性预览、产品/库位名称解析预览、化验标准判定预览。

不进入第一版执行：

- 真实入库、出库、调拨、二维码作废/恢复、角色权限、员工清理、产品/库位/标准维护。
- 任何任意 SQL、任意 HTTP、任意数据库更新能力。

## 阶段二一致性检查

- 本能力图与 `business-rules.md` 一致：执行类能力均标注为需要补充预览、确认、幂等和审计。
- 对自动入库只把 parse/get 归为 L2/L1，confirm 保持 L3，不纳入第一版执行计划。
