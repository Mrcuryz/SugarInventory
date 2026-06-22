# Business Rules

生成时间：2026-06-13

## 规则来源

本文件只记录从 Service 实现、DTO 字段、表结构和迁移脚本能确认的规则。`docs/openapi.json` 缺失；`InStockServiceImpl` 中存在既有乱码注释，本文不把乱码注释作为依据。

## 产品

代码确认：

- 产品主字段：`product_name`、`product_type`、`status`、`packaging_method`、`weight_per_piece`、`pieces_per_pallet`、`can_stack`、`screen_mesh_id`。
- 产品类型只允许 `黄冰糖`、`白冰糖`；产品状态只允许 `半成品`、`成品`；包装方式只允许 `箱`、`袋`、`罐` 或空值。
- 创建产品时校验产品名称唯一；更新时校验枚举合法性并支持动态更新。
- 半成品/成品查询接口会把展示名称拼成 `产品名(单件重量kg/包装方式)`；这不等同于真实 `product_name`。
- 半成品入库要求产品存在，且 `screen_mesh_id` 不能为空，否则抛 `SCREEN_MESH_NOT_FOUND`。
- `pieces_per_pallet` 用于板/件换算。散件入库、出库、生产产出预留码都会依赖该字段。

待确认：

- 产品名称唯一是否只按 `product_name` 判断，还是应包含产品类型、包装方式、重量等维度。
- 删除产品前是否应检查库存、历史记录、托盘码、化验标准关系；当前 `ProductService.deleteProduct` 只查产品存在后直接删除，实际依赖数据库外键失败。

## 库位与库存

代码确认：

- 库位字段包含 `warehouse_name`、`max_rows`、`max_capacity`、`cur_capacity`、`status`。
- 新增/修改库位校验库位名称重复。
- `inventory` 对 `(warehouse_id, side, row_number, layer)` 有唯一索引，对 `pallet_code_id` 也有唯一索引。
- 普通库位位置由 `side`（左/右）、`row_number`、`layer` 表示。
- 入库自动分配候选位置；指定 `rowNumber/layer` 时必须同时提供，并且只支持单板。
- `rowNumber` 必须在 `1..warehouse.maxRows` 内；`layer` 只能为 1 或 2。
- 二层入库要求产品 `canStack=true`。可堆积产品会把库位 `maxCapacity` 调整为 `maxRows * 2 * 2`。
- 写入库存时捕获唯一索引冲突并重试，超过重试次数抛“库位分配冲突，请重试”。
- 入库成功后增加 `warehouse.cur_capacity`；出库或生产领料删除库存后减少 `cur_capacity`，部分旧出库流程是否更新容量需逐方法确认。

待确认：

- `warehouse.status` 与 `cur_capacity/max_capacity` 是否有自动同步规则；目前仅看到查询和维护状态更新，未见完整状态机。
- “左/右侧优先”和“从后往前/从前往后”具体排序由 Mapper SQL 决定，本轮未逐条展开 Mapper XML/注解 SQL。

## 成品入库

代码确认：

- 入口：`InStockService.stockIn(InStockRequestDTO, operatorId)`，事务。
- 校验产品存在、库位存在。
- `semiRecords` 中最多只能有一个 `useAssay=true`。
- 非返库入库（`returnInStockFlag != "1"`）会先校验半成品来源库存是否足够。
- 若有一个半成品来源被标记 `useAssay=true`，系统会查该半成品产品和生产日期的化验数据，并复制其检测指标，为成品产品和入库日期生成一条新的化验记录；新化验会执行标准判定并设置版本。
- 如果没有 `useAssay=true`，成品入库会按成品产品和入库日期查已有化验；未查到时 `assay` 可为空进入后续逻辑，但数据库老表对部分 `assay_id` 字段是 NOT NULL，迁移 `2026-04-19-make-assay-links-optional.sql` 已尝试放宽关联。
- 入库记录写入 `in_stock`，半成品来源写入 `in_stock_item`，库存位置写入 `inventory`。
- 单位 `unit="0"` 表示板，`unit="1"` 表示件；散件会按 `pieces_per_pallet` 拆成整板和尾件。

待确认：

- 当前数据库实际是否已执行 `make-assay-links-optional`，决定“无化验入库”是否能落库。
- `returnInStockFlag="1"` 的业务定义仅从代码看是调拨返库跳过半成品消耗校验，命名和适用边界需业务确认。

## 半成品入库

代码确认：

- 入口：`SemiProductRecordService.addSemiProductRecord` 和 `stackModeInStock`，均事务。
- 校验产品、库位；产品必须配置筛网。
- 按产品和生产日期查询化验；未见强制要求化验存在。
- 普通半成品入库复用 `handlerInStock/handlerInStockPieces` 写入 `inventory`。
- 成功后写 `semi_product_record`，包括产品、库位、数量、筛网、操作人、操作日期、化验 ID、总重量和单位。
- 栈式入库使用 `stackModeInStockQuantity`，查找候选位置时不允许二层堆放。

待确认：

- 半成品入库无化验时是否业务允许，还是仅因数据库迁移兼容历史数据。
- 栈式入库与普通库位的边界条件和库位配置方式需业务确认。

## 出库

代码确认：

- 普通出库入口：`OutStockService.processOutStock`，事务。
- 栈式出库入口：`processStackOutStock`，事务。
- 出库先按库位、侧别、层和出库优先级查询库存，再按产品过滤。
- `unit="0"` 表示出库板数，会换算成 `quantity * piecesPerPallet` 件；`unit="1"` 表示出库散件数。
- 若某库存行件数大于需出库件数，则只更新该行 `pieces`；否则删除该 `inventory` 行。
- 出库不足时抛 `INSUFFICIENT_STOCK`，错误信息会给出差额板/件。
- 每个批次会按产品和生产日期查化验；无化验则抛 `ASSAY_NOT_FOUND`。
- 出库记录写入 `out_stock`，含库位、产品、出库数量、散件、入库日期、出库日期、操作员、化验、重量、出库单位和优先级。

待确认：

- 普通出库是否更新 `warehouse.cur_capacity`：本轮关键片段未看到完整容量更新逻辑，需进一步逐行确认或补充测试。
- 出库排序的精确定义需要检查 Mapper SQL。

## 调拨/移库

代码确认：

- 旧批量调拨入口：`OutStockService.transferOut`，事务；先出库，再构造 `InStockRequestDTO` 写入目标库位，`returnInStockFlag="1"`。
- 托盘级调拨入口：`PalletCodeService.createTransferTasks` + `confirmTransferTasks`，事务。
- 创建调拨任务时同一请求禁止重复扫码；托盘必须可调拨；不能存在未完成出库或调拨任务。
- 确认调拨时锁定托盘、库存和源/目标库位；目标库位不存在则报错。
- 目标库位与当前位置相同不能确认调拨。
- 调拨分配目标位置时遇到唯一索引冲突会重试，失败抛“库位分配冲突，请重试”。
- 调拨任务记录 `target_warehouse_id`、`target_side`，流转记录写入来源/目标库位、侧、排、层。

待确认：

- 旧 `transferOut` 和新托盘调拨是否同时在线，哪个应作为未来唯一业务入口。

## 托盘码与托盘任务

代码确认：

- 托盘码状态至少包括 `FREE`、`PENDING`、`INSTOCK`、`INVALID`、`ORDER_RESERVED`。
- 固定产品二维码通过 `fixed_product_id` + `fixed_mode_enabled` 绑定固定产品。
- 历史扫码绑定 `bindPalletAndCreateTask`：只允许 `FREE` 码；固定产品 FREE 码要求后台打印并启用；会创建 `SEMI_IN` 或 `FINISH_IN` 待确认任务，并把托盘置为 `PENDING`。
- 托盘轮次 `current_cycle_no/cycle_no` 用于区分历史循环。
- 入库确认 `confirmSingleFinishedTaskIn`：拒绝 `ORDER_RESERVED` 码直接入库；要求托盘 `PENDING` 且存在待处理入库任务；按任务类型进入半成品或成品入库处理。
- 批量确认入库逐条处理，同一事务中任一失败会回滚。
- 半成品普通出库、成品出库、调拨均有 create/confirm 两步。
- 历史生产占用相关接口调用 `rejectLegacyProductionFlowWrite`，代码行为是拒绝旧流程写操作。
- 平面图批量任务只支持 `OUT` 和 `TRANSFER`；`PREPARE` 被拒绝。

待确认：

- 哪些托盘任务入口仍面向用户，哪些只保留兼容历史页面。
- 作废/恢复二维码、删除流转记录目前缺少方法级权限；是否由前端菜单权限控制需确认。

## 化验与质量标准

代码确认：

- 化验指标：色值、还原糖、干燥失重、电导灰分、蔗糖分、不溶于水杂质、pH。
- 化验判定从 `product_quality_standard_relation` 查询当前产品启用且生效的标准关系。
- 无启用标准时，判定结果为 `NO_STANDARD`，兼容结论为 `无标准`。
- 单个标准逐项判断：缺少检测值视为该指标失败；`compareType` 支持 `lte`、`gte`、`range`，为空时根据上下限推断。
- 命中一个标准返回 `PASS/合格`；命中多个标准返回 `MULTIPLE_CANDIDATES/合格` 并采用首个候选；无命中返回 `FAIL/不合格`。
- 化验导入可按单产品或批量化验组导入；批量组通过 `relatedProducts` 扩展成多个产品。
- 化验更新不是覆盖旧记录，而是创建新记录并递增版本。
- 删除化验时若被外键引用，捕获为“该记录已被其他数据关联，无法删除”。
- 化验标准必须完整配置 7 项固定指标；同产品类型下相同标准名称和版本不能重复；标准编号不能重复。
- 产品标准关系要求产品存在、标准存在、产品类型与标准适用品类一致；不能删除产品最后一个启用中的标准关系。

待确认：

- 多个候选标准“首个”的排序依据需要检查 Mapper 的 `selectActiveByProductId` SQL。
- `is_qualified` 数据库旧枚举只有 `合格/不合格`，但代码可能写入 `无标准`；需确认迁移是否已放宽枚举。

## 自动入库/智能报数

代码确认：

- 解析阶段调用 LLM，将原始文本解析为 `ParsedInboundItem`，再生成 `AutoInboundTask`。
- 解析结果缓存 Redis，key 前缀 `auto_inbound:batch:`，TTL 24 小时；用户历史保留最近 20 条。
- 解析任务会校验 LLM 输出的 `productId` 和 `warehouseId` 是否存在；缺失或未匹配会记录 `missingFields/reasons`。
- 半成品任务会按产品和日期检查当日化验，缺失作为 warning。
- 确认阶段拒绝成品报数任务：提示必须先关联生产订单，再通过生产订单登记产出和分配二维码。
- 半成品确认会标准化板/件数量，分配固定产品二维码并执行入库；确认后保存生产报数留档，并把 Redis 批次结果更新为提交后状态。
- 生产消耗识别会优先留档；部分历史备料池余额足够时可扣减 `semi_prepare_pool_balance` 并写 `production_consumption_record`。

待确认：

- LLM 输出产品 ID/库位 ID 的来源和可靠性；当前代码有缺失校验，但没有面向 Agent 的确定性名称解析接口。
- 自动入库确认没有明确幂等 request key；重复提交同一批次的行为需专项验证。

## 生产订单

代码确认：

- 生产订单类型只支持 `SEMI` / `FINISH`。
- 创建订单生成 `POyyyyMMddNNNN` 编号；使用 `selectLatestOrderNoForUpdate` 防并发重复，重复时重试一次。
- 成品订单才能领用半成品；领料要求托盘在库且产品状态为半成品。
- 领料会写 `production_order_material` 快照，写半成品出库记录，删除库存，减少库位容量，写托盘流转，并释放托盘为 FREE。
- 预打印标签按产品和数量锁定固定产品 FREE 二维码；不足时报错。
- 确认生产结束会按实际产出计算需要二维码数，锁定预留标签，创建产出行、产出二维码关联和待入库托盘任务，未用标签回收。
- 已有 USED/RECYCLED 标签时，再次确认生产结束直接返回详情，具有一定幂等保护。

待确认：

- 生产订单状态枚举没有集中 enum，状态字符串分散在 Service；需确认完整状态机。
- 旧手动绑定产出接口 `bindFixedQrs` 当前直接抛错，说明预打印流程已替代旧流程。

## 并发与一致性

代码确认：

- 库位唯一索引防止同一格子重复入库。
- 托盘库存唯一索引防止同一码重复在库。
- 多处使用 `for update`：托盘码解析锁定、固定码分配、生产订单编号、标签批次号、库存/库位调拨、备料池余额扣减。
- 批量入库/出库/调拨多为事务包裹，单项失败会回滚。

风险：

- 传统非托盘出库/入库与托盘流程共用库存表，是否所有路径都一致维护 `cur_capacity` 需补专项测试。
- 多个写接口无幂等键，Agent 重试可能造成重复任务或重复出入库。
- 部分写接口无方法级权限和操作审计，不适合直接暴露为 MCP 执行工具。

## 阶段二一致性检查

- 本文件与 `api-inventory.md` 一致：OpenAPI 缺失、以代码为准。
- 对无法从 Service 确认的业务边界均列为“待确认”。
- 未把 REST 接口映射为 MCP Tool；MCP 设计留到阶段三。
