# WMS边界收窄与智能报数生产领用改造记录

## 改造边界

- WMS只负责库存、二维码、出入库、库位和流转记录。
- 智能报数识别出的生产消耗只留档，不自动扣库存，也不绑定半成品二维码。
- “生产领用”确认后视为仓库主库存出库：删除库存占位，释放二维码，写生产领用台账，并累加备料池余额。
- 备料池库存不是生产领用流水，而是 `semi_prepare_pool_balance.remaining_pieces > 0` 的半成品余额。
- 为兼容现有 `pallet_flow_record.operation_type` 字段，生产领用流转记录继续存储 `PREPARE_CONSUMED`，前端和小程序统一展示为“生产领用”。
- 旧 `semi_prepare_pool`、半成品绑定和消耗接口暂时保留，不删除，只不再作为新智能报数默认链路。

## 新增表

```sql
CREATE TABLE production_prepare_ledger (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    product_id INT NOT NULL COMMENT '半成品产品ID',
    product_name_snapshot VARCHAR(100) NULL COMMENT '产品名称快照',
    product_status VARCHAR(20) NULL COMMENT '产品状态',
    production_date DATE NULL COMMENT '半成品生产日期',
    screen_mesh_id INT NULL COMMENT '筛网ID',
    assay_id INT NULL COMMENT '化验ID',
    source_pallet_code_id INT NULL COMMENT '来源二维码ID',
    source_pallet_code VARCHAR(50) NULL COMMENT '来源二维码编码快照',
    source_inventory_id INT NULL COMMENT '来源库存ID快照',
    source_warehouse_id INT NULL COMMENT '来源库位ID快照',
    source_warehouse_name VARCHAR(100) NULL COMMENT '来源库位名称快照',
    source_side VARCHAR(10) NULL COMMENT '来源侧别快照',
    source_row_number INT NULL COMMENT '来源排号快照',
    source_layer INT NULL COMMENT '来源层号快照',
    board_count_snapshot INT DEFAULT 0 COMMENT '领用板数快照',
    piece_count_snapshot INT DEFAULT 0 COMMENT '领用散件数快照',
    total_pieces INT NOT NULL COMMENT '折算总件数',
    pieces_per_pallet INT NULL COMMENT '每板件数快照',
    source_task_id INT NULL COMMENT '来源任务ID',
    source_flow_id BIGINT NULL COMMENT '来源流转记录ID',
    created_by INT NULL COMMENT '操作人',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    remark VARCHAR(500) NULL COMMENT '备注'
) COMMENT='生产领用出库统计台账';

CREATE TABLE production_report_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    source_text TEXT NOT NULL COMMENT '原始报数全文',
    report_type VARCHAR(50) NULL COMMENT '报数类型',
    report_date DATE NULL COMMENT '报数日期',
    inbound_json JSON NULL COMMENT '识别到的入库项JSON',
    consumption_text TEXT NULL COMMENT '生产消耗文本摘要',
    consumption_json JSON NULL COMMENT '生产消耗结构化JSON',
    unmatched_names JSON NULL COMMENT '未匹配产品名称',
    inbound_task_ids JSON NULL COMMENT '本次报数生成的入库任务ID列表',
    pallet_codes JSON NULL COMMENT '本次报数绑定的二维码列表',
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT' COMMENT '状态',
    error_message VARCHAR(500) NULL COMMENT '失败原因',
    created_by INT NULL COMMENT '创建人',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    updated_at DATETIME NULL COMMENT '更新时间',
    remark VARCHAR(500) NULL COMMENT '备注'
) COMMENT='生产报数留档记录';
```

本次修正新增：

```sql
CREATE TABLE semi_prepare_pool_balance (...) COMMENT='备料池半成品余额';
CREATE TABLE production_consumption_record (...) COMMENT='生产消耗扣减记录';
```

## 验收点

- 生产领用确认后 `inventory` 被扣除，二维码变为 `FREE`。
- 新增 `production_prepare_ledger` 记录来源二维码、库位、日期、板数/件数和折算件数。
- `semi_prepare_pool_balance` 按产品、生产日期、筛网、化验批次维护 `remaining_pieces`。
- 库存汇总中的“备料池库存”展示待消耗半成品余额，而不是生产领用流水。
- 智能报数确认先执行成品入库，再按产品和生产日期扣减备料池余额；未匹配或余额不足的消耗项仅留档提示。
- 页面不再提示“生产消耗仅作为备注保存，不扣库存”。
