# WMS 边界收窄与智能报数 / 备料池改造方案

## 1. 背景与核心结论

当前系统在推进“智能报数入库”“成品绑定半成品来源”“备料池消耗”等功能时，逐渐暴露出一个核心问题：

> WMS 正在被迫承担 MES 的职责。

真实生产报数通常同时包含：

- 成品生产了多少；
- 使用了哪些半成品 / 原料；
- 各半成品来自哪个生产日期；
- 消耗了多少板、多少件；
- 哪个班组、哪个工序生产；
- 最终有多少成品需要入库。

这些内容本质上属于生产管理 / MES 范围，而不是仓储管理 / WMS 的核心职责。

因此，本次改造不再继续把 WMS 做成半个 MES，而是把 WMS 边界收窄：

> WMS 只负责仓库库存、二维码、出入库、库位、流转记录。  
> 生产消耗只做记录与留档，不再强制参与二维码任务闭环。

---

## 2. 本次改造目标

### 2.1 备料池方向调整

将“转入备料池”调整为：

> 半成品从仓库出库到生产 / 备料环节。

即：

```text
半成品二维码在库
  ↓
转入备料池 / 生产领用
  ↓
视为仓库出库
  ↓
释放二维码
  ↓
按产品 + 生产日期 + 件数形成生产领用统计台账
```

### 2.2 智能报数方向调整

智能报数只执行 WMS 范围内的入库动作：

```text
报数文本
  ↓
识别成品 / 半成品入库项
  ↓
固定产品二维码分配
  ↓
创建 pallet_task
  ↓
确认入库
```

报数中识别出的生产消耗内容只作为备注 / 结构化 JSON 留档：

```text
用原料xxx
4月2号2板
4月10号14板
...
```

这些内容：

- 不自动绑定半成品二维码；
- 不自动扣减半成品库存；
- 不调用 `tasks/semi-bind`；
- 不影响成品入库任务确认。

---

## 3. 业务边界

### 3.1 WMS 负责

WMS 负责：

1. 二维码管理；
2. 固定产品二维码池；
3. 入库任务；
4. 出库任务；
5. 调拨任务；
6. 库位管理；
7. 库存记录；
8. 备料 / 生产领用出库记录；
9. 操作流转记录；
10. 智能报数辅助入库。

### 3.2 WMS 暂不负责

WMS 暂不负责：

1. 生产计划；
2. 生产工单；
3. 产量核算；
4. 原料 / 半成品配方；
5. 产耗比计算；
6. 成品与半成品精确来源绑定；
7. 半成品实际生产消耗扣减；
8. 生产过程质量和班组过程管理。

这些内容未来应由 MES 或轻量生产管理系统负责。

---

## 4. 新业务规则

## 4.1 转入备料池 = 生产领用出库

新的业务定义：

> 半成品转入备料池后，视为该半成品已经离开仓库主库存，进入生产 / 备料环节。

处理结果：

1. 仓库 `inventory` 被扣减或删除；
2. 半成品二维码释放为 `FREE`；
3. 写入一条“转入备料池 / 生产领用”流转记录；
4. 写入一条备料 / 生产领用统计台账；
5. 备料池台账只做统计和留痕，不再参与二维码状态机。

---

## 4.2 转入备料池后不再用二维码追踪

转入备料池前：

```text
二维码 -> 仓库库存 -> 库位 -> 产品 / 日期
```

转入备料池后：

```text
二维码 -> FREE
备料池台账 -> 产品 + 生产日期 + 件数 + 来源快照
```

也就是说：

- 原二维码可以复用；
- 备料池不再通过原二维码进行后续消耗；
- 来源二维码只作为历史快照保存。

---

## 4.3 成品入库不再强制绑定半成品来源

成品入库只处理：

1. 成品产品；
2. 成品数量；
3. 生产日期；
4. 二维码分配；
5. 入库位置；
6. 流转记录。

不再强制：

1. 绑定半成品二维码；
2. 选择 `useAssay` 半成品来源；
3. 调用 `tasks/semi-bind`；
4. 自动消耗半成品备料池；
5. 释放半成品码。

---

## 4.4 智能报数中的生产消耗只做备注留档

例如真实报数：

```text
三.翻高勇15Kg黄冰糖小颗粒30件
（柳冰）
合格证印 2026.4.27
用原料直破黄小颗粒
4月2号2板
4月10号14板
4月11号3板十7件
4月12号3板
4月15号4板
用原料黄小颗粒
4月9号2板
4月16号1板
4月17号3板
4月18号1板
筛黄浮小颗粒
3月.30号2板＋22件
```

系统应识别：

### 可执行入库项

```text
15Kg黄冰糖小颗粒 30件
生产日期：2026-04-27
```

### 生产消耗备注项

```text
直破黄小颗粒：
- 4月2号 2板
- 4月10号 14板
- 4月11号 3板+7件
...
```

提交时：

- 入库项执行二维码分配和入库；
- 消耗项写入备注和结构化 JSON；
- 消耗项不自动扣库存。

---

## 5. 备料池 / 生产领用出库改造

## 5.1 原有逻辑问题

旧逻辑中，“转入备料池”容易变成一个中间状态：

```text
仓库库存
  ↓
备料池 ACTIVE
  ↓
成品绑定半成品
  ↓
成品确认入库时消耗
```

这会带来几个问题：

1. 半成品进入生产现场后仍然依赖二维码状态机；
2. 一板被部分消耗后，二维码到底是 `INSTOCK` 还是 `FREE` 很难处理；
3. 散件按件消耗会污染仓库库存模型；
4. 成品不绑定半成品时，备料池数据难以闭环；
5. WMS 变相承担 MES 的消耗核算职责。

因此新版本应将“转入备料池”改为生产领用出库台账。

---

## 5.2 新流程

### 操作入口

用户仍然可以在任务中心、平面图、小程序或扫码页面选择：

```text
转入备料池 / 生产领用
```

### 后端处理

确认转入备料池时：

```text
1. 校验二维码存在；
2. 校验二维码当前在库；
3. 查询对应 inventory；
4. 查询产品 pieces_per_pallet；
5. 折算实际件数 totalPieces；
6. 从 inventory 中扣除 / 删除该库存；
7. 写入生产领用台账；
8. 写入托盘流转记录；
9. 释放二维码为 FREE；
10. 返回处理结果。
```

---

## 5.3 件数折算规则

### 整板库存

如果库存为整板：

```text
unit = 板
quantity = N
pieces = 0 或 null
```

折算：

```text
totalPieces = quantity * product.pieces_per_pallet
boardCountSnapshot = quantity
pieceCountSnapshot = 0
```

### 散件库存

如果库存为散件：

```text
pieces > 0
```

折算：

```text
totalPieces = pieces
boardCountSnapshot = 0
pieceCountSnapshot = pieces
```

### 注意

仓库主库存仍然保留当前“板位 / 二维码”模型。  
本次只是在转入生产 / 备料时，将其转换成按件统计的生产领用台账。

---

## 5.4 建议新增表：production_prepare_ledger

建议新增一张生产领用台账表，而不是继续强依赖原 `semi_prepare_pool`。

表名建议：

```text
production_prepare_ledger
```

或：

```text
semi_prepare_out_record
```

推荐字段：

```sql
CREATE TABLE production_prepare_ledger (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',

    product_id INT NOT NULL COMMENT '半成品产品ID',
    product_name_snapshot VARCHAR(100) NULL COMMENT '产品名称快照',
    product_status VARCHAR(20) NULL COMMENT '产品状态，通常为半成品',
    production_date DATE NULL COMMENT '半成品生产日期',
    screen_mesh_id INT NULL COMMENT '筛网ID',
    assay_id INT NULL COMMENT '化验ID',

    source_pallet_code_id INT NULL COMMENT '来源二维码ID，仅快照追溯',
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
) COMMENT='生产领用/备料池出库统计台账';
```

字段说明：

- `source_pallet_code` 只作为历史快照，不参与后续二维码状态判断；
- `total_pieces` 用于后续统计；
- 该表不作为仓库库存表，不参与入库 / 出库 / 调拨的库存可用量计算。

---

## 5.5 是否保留原 semi_prepare_pool

建议：

1. 原 `semi_prepare_pool` 暂时保留；
2. 旧接口和旧逻辑暂时不删除；
3. 新智能报数和新备料池流程不再依赖旧 `semi_prepare_pool.ACTIVE -> CONSUMED` 链路；
4. 如果前端仍有旧页面，可隐藏或标注“旧版功能”；
5. 后续稳定后再考虑清理。

---

## 6. 成品半成品绑定逻辑处理

## 6.1 现有逻辑保留

现有成品绑定半成品接口、任务明细表、`tasks/semi-bind` 等逻辑：

```text
暂时保留，不删除。
```

原因：

1. 避免破坏已实现功能；
2. 避免影响历史数据；
3. 后续如果甲方需要，仍可人工使用；
4. 降低本次改造风险。

## 6.2 新流程默认不使用

新的智能报数入库、固定二维码入库、成品报数入库：

```text
不再自动调用 tasks/semi-bind。
```

即：

1. 成品可以不绑定半成品来源；
2. 成品可以直接确认入库；
3. 半成品消耗信息只写备注 / 报数留档；
4. 不因为没有半成品来源阻断成品入库。

---

## 7. 智能报数模块改造

## 7.1 新解析目标

智能报数解析结果需要分为三类：

```text
1. inboundItems
   真正要执行入库的项目

2. productionConsumptionItems
   生产消耗信息，只留档

3. unmatchedNames
   产品表未匹配到的名称
```

---

## 7.2 示例解析

原始文本：

```text
三.翻高勇15Kg黄冰糖小颗粒30件
（柳冰）
合格证印 2026.4.27
用原料直破黄小颗粒
4月2号2板
4月10号14板
4月11号3板十7件
4月12号3板
4月15号4板
用原料黄小颗粒
4月9号2板
4月16号1板
4月17号3板
4月18号1板
筛黄浮小颗粒
3月.30号2板＋22件
```

解析结果建议：

```json
{
  "reportType": "PRODUCTION_REPORT",
  "inboundItems": [
    {
      "lineNo": 1,
      "productNameRaw": "15Kg黄冰糖小颗粒",
      "productId": 301,
      "productName": "15Kg黄冰糖小颗粒",
      "quantity": 30,
      "unit": "件",
      "productionDate": "2026-04-27",
      "dateSource": "CERTIFICATE_DATE",
      "remark": "翻高勇；柳冰；合格证印2026.4.27"
    }
  ],
  "productionConsumptionItems": [
    {
      "materialNameRaw": "直破黄小颗粒",
      "productId": null,
      "items": [
        {
          "productionDate": "2026-04-02",
          "boardCount": 2,
          "pieceCount": 0,
          "quantityText": "2板"
        },
        {
          "productionDate": "2026-04-10",
          "boardCount": 14,
          "pieceCount": 0,
          "quantityText": "14板"
        },
        {
          "productionDate": "2026-04-11",
          "boardCount": 3,
          "pieceCount": 7,
          "quantityText": "3板7件"
        }
      ]
    },
    {
      "materialNameRaw": "黄小颗粒",
      "productId": 101,
      "items": [
        {
          "productionDate": "2026-04-09",
          "boardCount": 2,
          "pieceCount": 0,
          "quantityText": "2板"
        }
      ]
    }
  ],
  "unmatchedNames": [
    "直破黄小颗粒",
    "筛黄浮小颗粒"
  ],
  "warnings": [
    "生产消耗信息仅作为备注保存，不会自动扣减库存",
    "直破黄小颗粒未匹配到产品表",
    "筛黄浮小颗粒未匹配到产品表"
  ]
}
```

---

## 7.3 报数提交规则

提交报数草稿时：

### 执行

只对 `inboundItems` 执行：

```text
固定产品二维码分配
  ↓
创建 pallet_task
  ↓
确认入库
  ↓
写库存和流转记录
```

### 不执行

对 `productionConsumptionItems`：

```text
不分配二维码
不绑定半成品码
不扣 inventory
不扣 production_prepare_ledger
不释放二维码
不创建出库任务
```

只做：

```text
写备注
写 production_report_record
写 consumption_json
```

---

## 8. 报数留档表设计

建议新增：

```text
production_report_record
```

定位：

> 保存原始生产报数文本、识别出的入库项、识别出的生产消耗项、未匹配名称。  
> 该表不直接参与库存计算。

建表示例：

```sql
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

    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/COMMITTED/CANCELED/FAILED',
    error_message VARCHAR(500) NULL COMMENT '失败原因',

    created_by INT NULL COMMENT '创建人',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    updated_at DATETIME NULL COMMENT '更新时间',
    remark VARCHAR(500) NULL COMMENT '备注'
) COMMENT='生产报数留档记录';
```

---

## 9. 入库备注生成规则

当智能报数中存在生产消耗项时，自动拼接到入库任务或流转记录备注中。

备注示例：

```text
来源：智能报数入库
报数日期：2026-04-27
生产备注：翻高勇；柳冰；合格证印2026.4.27

生产消耗备注（仅留档，不自动扣库存）：
直破黄小颗粒：
- 2026-04-02：2板
- 2026-04-10：14板
- 2026-04-11：3板7件

黄小颗粒：
- 2026-04-09：2板
- 2026-04-16：1板

未匹配产品：
- 直破黄小颗粒
- 筛黄浮小颗粒
```

注意：

> 备注中必须明确“仅留档，不自动扣库存”。

---

## 10. 前端页面调整

## 10.1 智能报数确认页分区

页面应分为三块：

### A. 将执行入库的项目

展示：

1. 产品名称；
2. 生产日期；
3. 数量；
4. 拆分结果；
5. 需要二维码数量；
6. 可用二维码数量；
7. 入库库位；
8. 侧别；
9. 风险提示。

### B. 仅作为备注保存的生产消耗

展示：

1. 原料 / 半成品名称；
2. 生产日期；
3. 板数；
4. 件数；
5. 是否匹配到产品表；
6. 折算总件数，如果能计算。

顶部提示：

```text
以下内容仅作为生产消耗备注保存，不会自动扣减库存，也不会绑定半成品二维码。
```

### C. 未匹配名称

展示：

```text
以下名称未在产品表中匹配到，请确认是否需要维护产品别名：
- 直破黄小颗粒
- 筛黄浮小颗粒
```

---

## 10.2 提交前确认提示

点击提交前，如果存在 `productionConsumptionItems`，需要弹窗确认：

```text
本次报数中包含生产消耗信息。
系统将只执行成品/半成品入库，生产消耗信息仅作为备注保存，不会自动扣减半成品库存。
是否继续？
```

用户确认后才提交。

---

## 11. 后端接口调整建议

## 11.1 解析报数

```http
POST /api/report-inbound/parse
```

返回中增加：

```json
{
  "inboundItems": [],
  "productionConsumptionItems": [],
  "unmatchedNames": [],
  "warnings": []
}
```

---

## 11.2 查询报数草稿

```http
GET /api/report-inbound/drafts/{draftId}
```

返回草稿时包含：

1. 入库项；
2. 消耗备注项；
3. 未匹配项；
4. 提示信息。

---

## 11.3 提交草稿

```http
POST /api/report-inbound/drafts/{draftId}/commit
```

提交逻辑：

1. 校验入库项字段完整；
2. 校验二维码数量充足；
3. 校验库位字段完整；
4. 执行入库项；
5. 生成报数留档记录；
6. 将生产消耗项写入备注和 `production_report_record.consumption_json`；
7. 不执行半成品消耗。

---

## 11.4 转入备料池确认

如果已有接口，例如：

```text
POST /api/pallet-codes/tasks/semi-prepare/confirm
```

或类似接口，需要调整确认逻辑：

1. 仍然扫码确认；
2. 仍然校验库存；
3. 不再创建或激活需要后续消耗的二维码型备料池；
4. 改为写入 `production_prepare_ledger`；
5. 释放二维码。

---

## 12. 后端代码改造点

## 12.1 ReportInbound 相关

需要修改：

1. 报数 Prompt；
2. 报数解析 DTO；
3. Redis 草稿结构；
4. 草稿校验逻辑；
5. 提交逻辑；
6. 备注生成逻辑；
7. 报数留档写入逻辑。

新增或调整：

```text
ReportInboundParseResult
ReportInboundDraft
ReportInboundItem
ProductionConsumptionItem
ProductionReportRecord
ProductionReportRecordMapper
ProductionReportRecordService
```

---

## 12.2 PalletCodeService 相关

需要调整“转入备料池确认”逻辑：

1. 从当前库存取产品、日期、数量、位置；
2. 折算 `totalPieces`；
3. 写生产领用台账；
4. 删除 inventory 或按现有逻辑出库；
5. 写流转记录；
6. 释放 `pallet_code` 为 `FREE`。

注意：

> 保留旧 `confirmSemiConsume` / `bindSemiItemsToTask` 等逻辑，但新智能报数流程不调用。

---

## 12.3 Mapper 相关

新增：

```text
ProductionPrepareLedgerMapper
ProductionReportRecordMapper
```

可能需要新增查询：

```text
selectInventoryByPalletCodeIdForUpdate
insertProductionPrepareLedger
insertProductionReportRecord
```

---

## 13. 数据迁移建议

## 13.1 新增表

建议新增两个表：

1. `production_prepare_ledger`；
2. `production_report_record`。

## 13.2 不删除旧表

暂时不删除：

1. `semi_prepare_pool`；
2. `pallet_task_semi_item`；
3. 旧半成品绑定相关表。

## 13.3 历史数据

历史 `semi_prepare_pool.ACTIVE` 数据可以暂不迁移。  
如后续需要，可以单独写迁移脚本，将其转成 `production_prepare_ledger`。

第一阶段不强制处理历史备料池数据。

---

## 14. 流转记录要求

转入备料池现在本质是生产领用出库。

建议流转记录：

```text
operation_type = PREPARE_OUT
operation_name = 转入备料池 / 生产领用
```

备注中写：

```text
已视为生产领用出库，二维码已释放。
折算数量：xx件。
来源库位：xxx。
```

如果不新增枚举，也可以沿用旧的 `PREPARE_CONSUMED`，但建议文案改清楚：

```text
转入备料池（生产领用出库）
```

---

## 15. 验收标准

## 15.1 转入备料池

1. 半成品二维码在库时可转入备料池；
2. 转入后 inventory 被扣除；
3. 转入后二维码状态变为 `FREE`；
4. 生成一条生产领用台账；
5. 台账中包含来源二维码、来源库位、产品、日期、件数；
6. 托盘流转记录中能看到“转入备料池 / 生产领用出库”；
7. 该二维码后续可重新用于固定产品二维码打印 / 绑定。

---

## 15.2 智能报数

1. 可以识别成品入库项；
2. 可以识别生产消耗项；
3. 生产消耗项不会自动扣库存；
4. 生产消耗项会写入备注和报数留档；
5. 未匹配产品名称会展示给用户；
6. 页面明确提示“生产消耗仅留档”；
7. 提交时只对入库项分配二维码并确认入库；
8. 成品入库不再因为未绑定半成品来源而阻断。

---

## 15.3 成品入库

1. 成品 30 件、每板 25 件时，拆成：
   - 1板；
   - 5件，占1板位。
2. 分配 2 个固定产品二维码；
3. 两个二维码都入库成功；
4. 入库备注包含生产消耗留档；
5. 不调用半成品绑定接口。

---

## 16. 测试用例

## 16.1 备料池测试

### 用例 1：整板半成品转入备料池

输入：

```text
黄小颗粒 2板
每板 25 件
```

期望：

```text
production_prepare_ledger.total_pieces = 50
二维码释放 FREE
inventory 删除或扣除
```

### 用例 2：散件半成品转入备料池

输入：

```text
黄小颗粒 10件
```

期望：

```text
production_prepare_ledger.total_pieces = 10
二维码释放 FREE
inventory 删除或扣除
```

### 用例 3：转入后二维码可复用

期望：

```text
同一二维码可再次作为固定产品二维码被选中。
```

---

## 16.2 智能报数测试

### 用例 1：生产报数含成品和消耗

输入：

```text
15Kg黄冰糖小颗粒30件
用原料黄小颗粒
4月9号2板
```

期望：

```text
生成入库项：15Kg黄冰糖小颗粒30件
生成消耗备注：黄小颗粒 4月9号2板
只执行成品入库
不扣半成品库存
```

### 用例 2：未匹配产品

输入：

```text
用原料直破黄小颗粒
4月2号2板
```

期望：

```text
直破黄小颗粒进入 unmatchedNames
不强行匹配产品
```

### 用例 3：“十”识别为“+”

输入：

```text
4月11号3板十7件
```

期望：

```text
boardCount = 3
pieceCount = 7
quantityText = 3板7件
```

---

## 17. 给 Codex 的执行要求

请按以下优先级实施。

### P0：业务边界调整

1. 智能报数不再处理半成品自动绑定；
2. 成品入库不再强制关联半成品；
3. 生产消耗只作为备注和结构化 JSON 留档。

### P1：新增数据表

1. 新增 `production_prepare_ledger`；
2. 新增 `production_report_record`；
3. 不删除旧表。

### P2：改造转入备料池

1. 转入备料池视为生产领用出库；
2. 释放二维码；
3. 写生产领用台账；
4. 保留来源快照。

### P3：改造智能报数解析和提交

1. 解析结果拆分为入库项和生产消耗项；
2. 提交时只执行入库项；
3. 消耗项写备注和留档；
4. 前端明确提示不自动扣库存。

### P4：测试与文档

1. 补充单元测试；
2. 补充接口测试；
3. 更新帮助文档；
4. 更新智能报数页面提示文案。

---

## 18. 最终说明

本次改造的核心目标是：

> 让 WMS 回到仓储系统本身，不再承担 MES 的生产消耗职责。

当前系统先做到：

```text
半成品进入生产 = 仓库出库
成品生产消耗 = 报数备注留档
成品入库 = 固定二维码 + 入库任务
成品与半成品 = 暂不强绑定
```

这样可以显著降低落地难度，让 WMS 至少先在小范围内跑起来，同时保留未来接入 MES 或轻量生产管理系统的扩展空间。
