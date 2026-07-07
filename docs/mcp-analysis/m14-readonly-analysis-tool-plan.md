# M1.4 只读分析工具候选规划

本文档最初用于进入 M1.4 前的资产盘点和工具候选拆分。`get_inventory_distribution` 已完成 M1.4a-1 单品首版和 M1.4a-2 多范围、多过滤形态；其余候选仍仅为分析，不新增写操作。

## 设计边界

M1.4 的目标是把现有仓储、化验、二维码、托盘、生产和审计资产整理成受控只读分析能力。

硬性边界：

- 只允许 L1 只读实时数据和必要的 L2 业务预览/校验。
- 不设计任意 SQL、任意 HTTP、任意数据库更新工具。
- 不把 REST 接口一对一复制成 MCP Tool。
- 不让 Agent 猜测产品 ID、库位 ID、库存 ID、托盘码。
- 查询工具必须使用结构化参数，输出业务语义结果。
- 写操作、作废、恢复、删除、导入、确认、入库、出库、生产执行不进入 M1.4a。
- 如需新能力，优先补 Java 后端只读聚合接口，再通过 internal agent gateway 暴露白名单工具。

## 当前资产概览

已存在的 Agent 白名单工具：

- `resolve_products`
- `resolve_warehouses`
- `get_inventory_overview`
- `get_warehouse_status`
- `get_pallet_status`
- `get_assay_status`

已识别的后端资产：

- 库存：`InventoryController`、`InventorySummaryMapper`、`InventoryMapper`。
- 产品：`ProductController`。
- 库位：`WarehouseController`。
- 化验：`AssayController`、`AssayGroupController`、`AssayMapper`。
- 质量标准：`QualityStandardController`、`ProductQualityStandardRelationController`。
- 二维码 / 托盘：`PalletCodeController`、`PalletCodeQueryMapper`、`PalletTaskMapper`。
- 生产订单 / 标签：`ProductionOrderController`、`ProductionBoilingBatchController`。
- 审计：`OperationLogController`，以及 Agent 侧 tool/audit/session/message 相关表和服务。

当前主要缺口：

- 现有接口偏页面查询，缺少面向自然语言问题的只读聚合 DTO。
- 产品解析目前更适合具体产品，产品大类、名称组、规格组等范围型查询需要明确支持。
- 二维码和托盘的异常定义需要业务确认。
- 化验“最近一次”“今天未化验”“无标准”的统计口径需要确认。
- 前端不应展示内部 ID、toolName、raw JSON，因此工具返回需要同时包含 machine-readable 字段和 user-facing 摘要。

## M1.4a：库存分布与库存可信度分析

优先从 `get_inventory_distribution` 开始。

### get_inventory_distribution

风险等级：L1。

状态：已实现完整受控聚合形态。

已实现范围：

- 产品范围支持已确认 `SINGLE_PRODUCT`、`EXACT_PRODUCT_NAME_GROUP`、`PRODUCT_TYPE_GROUP`，以及用户显式要求的 `ALL`。
- 库位范围支持 `ALL` 和 resolver/结构化状态确认的 `SINGLE_WAREHOUSE`。
- 支持产品状态、库位状态、托盘状态、化验判断状态、入库起止日期白名单过滤。
- 支持 `warehouse`、`product`、`warehouse_product` 三种固定分组，默认 20 条、最多 100 条。
- Java 业务端执行只读聚合；权限与现有库存概览一致，要求当前用户已认证，同时保留 `mcp:warehouse:read` delegation scope 和精确只读 POST 路径限制。
- Python 从 resolver/HITL 的 `selectedProduct` 构造单品、产品名称组或产品大类范围；模型伪造产品/库位 ID 会被替换或拒绝。显式“全部产品”不需要内部 ID。
- 跨规格聚合不输出误导性的统一板数，以等价件数和总重量为准。
- 普通 UI 不显示 `productId`、`warehouseId`、`inventoryId`、`toolName` 或 raw JSON。
- 未增加任何写工具、任意 SQL 或任意 HTTP 代理。

仍未实现库区范围、库龄分桶、库存明细下钻和导出；这些能力不继续堆入本工具。

用途：

- 查询某个产品、产品组、产品类型或全部库存的库位分布。
- 支持按库位、产品、库存状态、托盘状态、化验状态聚合。

可回答：

- 黄冰糖都分布在哪些库位？
- 某类产品库存主要集中在哪些库位？
- 哪些库位存放了最多黄冰糖？
- 成品和半成品库存分布有什么差异？

建议输入：

- `productScope`：具体产品、产品名称组、产品类型组或全部。
- `warehouseScope`：具体库位、库区或全部。
- `statusFilter`：库存状态、托盘状态、化验状态。
- `groupBy`：`warehouse`、`product`、`warehouse_product`。
- `limit`。

建议输出：

- 总库存数量、总托盘数、涉及库位数。
- 分组明细：库位、产品、数量、托盘数、最近入库时间、化验覆盖状态。
- 风险提示：无化验、库位状态异常、托盘状态异常、数据缺口。

可复用资产：

- `InventoryController` 的库存查询、库存汇总、库位查询能力。
- `InventorySummaryMapper` 的库存汇总能力。
- `InventoryMapper` 中库存、产品、库位、化验、托盘关联查询。
- `ProductController` 的产品查询能力。

已解决缺口：

- Java 提供单一只读聚合接口，Python 不拼接页面接口、不直连数据库。
- “黄冰糖”等模糊输入通过 HITL 区分产品大类、产品名称组和具体规格。
- 默认按等价件数倒序，分组维度为固定枚举，结果数量受 `limit` 约束。

### query_inventory_records

风险等级：L1。

用途：

- 查询符合条件的库存明细记录。
- 作为 `get_inventory_distribution` 的 drill-down 工具。

可回答：

- 黄冰糖库存明细有哪些？
- 某库位当前有哪些托盘？
- 某产品有哪些批次或托盘还在库？

可复用资产：

- `InventoryController` 的分页库存查询。
- `InventoryMapper` 的库存明细关联查询。

缺口：

- 需要统一返回产品、库位、托盘、库存状态、化验状态的业务 DTO。
- 需要分页和最大条数保护。

### query_inventory_without_assay

风险等级：L1/L2。

用途：

- 查询当前在库但缺少化验关联或化验状态未知的库存。

可回答：

- 哪些库存没有化验？
- 黄冰糖库存里哪些托盘没有化验结果？
- 哪些库位存在无化验库存？

可复用资产：

- `InventoryMapper` 中库存与化验关联。
- `AssayController` 的化验查询。

缺口：

- 需要确认“无化验”的判定口径：无报告、无有效报告、报告无标准、报告未通过是否分别统计。

### query_inventory_ageing

风险等级：L1/L2。

用途：

- 按入库时间、生产日期或库存创建时间分析库龄。

可回答：

- 哪些库存存放超过 30 天？
- 黄冰糖最久未流转的库存在哪些库位？
- 最近 7 天新增库存分布如何？

可复用资产：

- 库存表时间字段。
- 出入库历史接口。

缺口：

- 需要确认库龄使用入库时间、生产时间还是库存记录创建时间。

### query_inventory_capacity_risks

风险等级：L1/L2。

用途：

- 分析库位容量、占用、剩余容量和接近满仓风险。

可回答：

- 哪些库位快满了？
- 哪些库位还有较多可用容量？
- 某类产品是否集中在容量紧张库位？

可复用资产：

- `InventoryController` 的 `warehouses` 和库存汇总能力。
- `WarehouseController` 的库位信息。

缺口：

- 需要确认容量字段和单位口径。
- 需要定义风险阈值，例如 80% 或 90%。

## M1.4b：化验查询与质量分析

目标是让 Agent 能可靠回答化验时间、报告详情、异常、不合格、标准覆盖等问题。

### query_assay_records

风险等级：L1。

用途：

- 按产品、日期范围、状态、报告号查询化验记录。

可回答：

- 黄冰糖最近一次化验是什么时候？
- 今天有哪些化验记录？
- 某产品最近 7 天的化验记录有哪些？

可复用资产：

- `AssayController` 的 `query`、`by-product-date`。
- `AssayMapper`。

缺口：

- 需要明确“最近一次”按采样时间、化验日期、创建时间还是报告日期排序。

### get_assay_report_detail

风险等级：L1。

用途：

- 获取某个化验报告详情，包括指标、结论、关联产品和标准。

可回答：

- 某个化验报告详情是什么？
- 这份报告哪些指标合格，哪些不合格？

可复用资产：

- `AssayController` 的 `GET /{id}`。
- 质量标准与产品标准关系接口。

缺口：

- 不应要求用户提供内部 assayId；应支持通过报告号或候选确认进入详情。

### query_assay_abnormalities

风险等级：L1/L2。

用途：

- 查询一段时间内不合格、异常、缺失标准、指标越界的化验记录。

可回答：

- 最近 7 天有哪些不合格或异常？
- 哪些产品最近化验不合格？
- 哪些化验记录无标准？

可复用资产：

- `AssayMapper`。
- `QualityStandardController`。
- `ProductQualityStandardRelationController`。

缺口：

- 需要确认“不合格”和“异常”的字段来源。
- 需要确认无标准是产品未绑定标准，还是报告指标无法匹配标准。

### query_products_without_recent_assay

风险等级：L1/L2。

用途：

- 查询指定产品范围内，最近 N 天没有化验记录的产品。

可回答：

- 今天哪些产品没有化验？
- 最近 7 天哪些在库产品没有化验？
- 哪些成品缺少近期化验？

可复用资产：

- 产品列表。
- 库存分布。
- 化验记录。

缺口：

- 需要确认统计全集：全部启用产品、当前有库存产品、今日生产产品，还是指定产品组。

### query_assay_standard_coverage

风险等级：L1/L2。

用途：

- 分析产品和化验报告的质量标准覆盖情况。

可回答：

- 哪些产品没有质量标准？
- 哪些化验记录没有匹配标准？
- 哪些标准长期没有被使用？

可复用资产：

- `QualityStandardController`。
- `ProductQualityStandardRelationController`。
- `AssayController`。

缺口：

- 需要确认产品到标准的有效期、版本、启停用规则。

## M1.4c：二维码 / 托盘生命周期分析

目标是让 Agent 能追踪托盘码从打印、入库、流转、化验、作废到异常的完整只读链路。

### query_qr_code_lifecycle

风险等级：L1。

用途：

- 查询某个二维码或托盘码的生命周期摘要。

可回答：

- 这个托盘经历了哪些环节？
- 这个二维码当前是什么状态？
- 这个托盘对应哪些库存和化验信息？

可复用资产：

- `PalletCodeController` 的 `parse`、`inventory`、`assay`、`flows`、`flows/cycles`。
- `PalletCodeQueryMapper`。

缺口：

- 需要统一生命周期事件模型，避免直接暴露页面字段。

### query_pallet_flow_records

风险等级：L1。

用途：

- 查询托盘流转记录，支持按托盘码、产品、时间范围、事件类型过滤。

可回答：

- 某托盘最近有哪些流转？
- 某产品最近 7 天托盘流转是否正常？

可复用资产：

- `PalletCodeController` 的 flow 相关接口。
- 出入库历史接口。

缺口：

- 需要明确事件类型枚举和排序规则。

### query_printed_not_inbound_codes

风险等级：L1/L2。

用途：

- 查询已打印但尚未入库的二维码或托盘码。

可回答：

- 哪些码打印了但没入库？
- 某批次二维码入库完成率是多少？
- 二维码批量打印后还有多少未入库？

可复用资产：

- `PalletCodeController` 的任务列表和二维码查询。
- 生产订单标签批次接口。

缺口：

- 需要确认“打印”的数据源：打印任务、标签批次、二维码生成记录，还是实际打印日志。
- 需要确认“未入库”的判定字段。

### query_pallet_anomalies

风险等级：L1/L2。

用途：

- 查询托盘生命周期异常。

可回答：

- 哪些托盘状态异常？
- 有哪些作废码被扫描？
- 哪些托盘存在重复入库、无入库出库、状态与库存不一致？

可复用资产：

- `PalletCodeController`。
- `PalletCodeQueryMapper`。
- 出入库历史。
- 操作日志。

缺口：

- 需要业务确认异常定义。
- 需要确认是否存在作废码扫描日志；如果没有，需要先补审计来源。

### query_qr_batch_inbound_completion

风险等级：L1/L2。

用途：

- 分析某个打印批次、生产订单或产品范围内二维码入库完成率。

可回答：

- 二维码批量打印后入库完成率是多少？
- 某生产订单打印的托盘码还有哪些没入库？

可复用资产：

- 生产订单标签批次接口。
- 托盘码状态查询。
- 库存关联查询。

缺口：

- 需要建立打印批次到托盘码再到库存记录的稳定关联。

## M1.4d：库位容量与库位健康分析

### query_warehouse_capacity_distribution

风险等级：L1/L2。

用途：

- 查询库位容量、占用率、剩余容量。

可回答：

- 哪些库位快满？
- 哪些库位空置？
- 某库区容量是否紧张？

可复用资产：

- `InventoryController` 的库位和库存汇总接口。
- `WarehouseController`。

缺口：

- 需要确认容量单位和占用率计算公式。

### query_warehouse_recent_operations

风险等级：L1。

用途：

- 查询某库位最近操作。

可回答：

- 某库位最近发生了什么？
- 某库位最近有哪些入库或出库？

可复用资产：

- `InventoryController` 的 `warehouses/{warehouseId}/recent-operations`。
- 操作日志。

缺口：

- 用户不能直接提供内部 warehouseId，需要先通过 `resolve_warehouses` 或候选确认。

### query_warehouse_mixed_product_risks

风险等级：L1/L2。

用途：

- 分析同一库位多产品、多批次、多状态混放风险。

可回答：

- 哪些库位混放产品较多？
- 黄冰糖是否和其他产品混放？

可复用资产：

- 库存明细查询。
- 产品信息。

缺口：

- 需要业务确认混放是否一定为风险，以及风险阈值。

## M1.4e：生产订单与标签闭环分析

### query_production_order_progress

风险等级：L1。

用途：

- 查询生产订单状态、投料、熬煮、产出、标签、入库闭环。

可回答：

- 某生产订单现在进行到哪一步？
- 某订单还有哪些环节未完成？

可复用资产：

- `ProductionOrderController` 的列表、详情、追溯接口。
- `ProductionBoilingBatchController` 的追溯和图谱接口。

缺口：

- 需要定义订单进度摘要 DTO。

### query_production_label_completion

风险等级：L1/L2。

用途：

- 查询生产订单标签打印、绑定、入库完成情况。

可回答：

- 某订单标签打印完成了吗？
- 打印出的标签是否全部绑定托盘并入库？

可复用资产：

- 生产订单标签批次接口。
- 托盘码查询。
- 库存查询。

缺口：

- 需要确认标签批次到托盘码、托盘码到库存的关联完整性。

### query_material_pick_trace

风险等级：L1。

用途：

- 查询生产投料和领料追溯信息。

可回答：

- 某生产订单用了哪些原料库存？
- 某批原料流向了哪些订单？

可复用资产：

- 生产订单追溯。
- 出库历史。

缺口：

- 需要确认原料库存与生产订单之间的关联字段是否完整。

## M1.4f：操作审计与异常排查

### search_operation_logs

风险等级：L1。

用途：

- 查询业务操作日志。

可回答：

- 某用户最近做了哪些库存操作？
- 某托盘码相关操作日志有哪些？
- 某时间段有哪些失败操作？

可复用资产：

- `OperationLogController`。
- `OperationLogAspect`。

缺口：

- 需要确认日志字段是否包含足够的业务对象标识。

### query_agent_tool_audit

风险等级：L1。

用途：

- 查询 Agent 工具调用审计。

可回答：

- 用户刚才的问题调用了哪些工具？
- 哪些工具调用失败或超时？
- 哪些请求被安全过滤？

可复用资产：

- Agent tool audit 相关表和服务。
- M1.3R-5.1 流式审计分类。

缺口：

- 需要统一工具审计结果分类和查询接口。

### query_unmet_agent_intents

风险等级：L1/L2。

用途：

- 分析 Agent 未满足的问题，用于后续工具规划。

可回答：

- 用户最近常问但系统回答不了的问题有哪些？
- 哪些问题因为缺少工具而失败？

可复用资产：

- Agent conversation/message 表。
- tool audit 表。

缺口：

- 需要确认未满足意图是否已有结构化记录；没有则需要先补日志模型。

## 推荐优先级

第一批建议只做库存与化验的最小闭环：

1. `get_inventory_distribution`
2. `query_inventory_records`
3. `query_assay_records`
4. `query_assay_abnormalities`
5. `query_qr_code_lifecycle`
6. `query_printed_not_inbound_codes`

理由：

- `get_inventory_distribution` 直接补齐当前用户最常问的库存分布问题。
- `query_inventory_records` 是分布结果的明细下钻。
- 化验查询是库存可信度的直接补充。
- 二维码 / 托盘生命周期是仓库可信度的第二阶段核心，但异常口径需要先确认。

## 建议实现形态

M1.4 工具不建议直接拼接页面 REST 接口。建议采用以下路径：

1. Java 后端新增只读聚合接口或 internal read model service。
2. Java internal agent gateway 只暴露白名单工具。
3. Python runtime 只调用 Java 白名单工具，不直接访问数据库。
4. 前端只展示业务摘要、候选项、表格和必要风险提示，不展示内部 toolName、raw JSON、内部 ID。

每个工具至少需要：

- 工具规格文档。
- Java 聚合接口单测。
- Python runtime 工具路由单测。
- 前端真实浏览器验收用例或人工验收脚本。
- Agent tool audit 记录。

## 暂不进入 M1.4a 的能力

以下能力暂不实现：

- 化验导入、修改、删除。
- 质量标准新增、修改、删除。
- 托盘码作废、恢复、删除、重新打印。
- 入库、出库、移库、盘点执行。
- 生产订单创建、取消、投料、完成、绑定、打印执行。
- 任意 SQL 查询。
- 任意 HTTP 代理。

## 待确认问题

1. “黄冰糖”这类输入默认代表具体产品、产品名称组，还是产品大类？
2. “最近一次化验”按化验日期、采样日期、报告日期还是创建时间排序？
3. “今天哪些产品没有化验”的产品全集是启用产品、当前有库存产品，还是今日有生产/入库的产品？
4. “无标准”指产品未绑定标准，还是报告指标无法匹配标准？
5. “打印了但没入库”的打印来源是打印任务、标签批次、二维码生成记录，还是实际打印日志？
6. 托盘异常的第一版定义是否包含：作废码被扫描、重复入库、状态与库存不一致、无入库出库、绑定产品不一致？
7. 库位容量风险阈值是否使用固定阈值，还是按库区/产品类型配置？
