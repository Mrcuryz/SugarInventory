# 托盘码模块功能文档（API + 流程 + 状态机）

> 适用范围：`/api/pallet-codes` 相关接口。  
> 本文面向前后端联调、测试与实施。

---

## 1. 模块目标

托盘码模块用于建立“**二维码托盘**”的业务闭环：

1. 批量生成托盘码并打印二维码。
2. 扫码绑定产品，形成入库任务。
3. 对成品任务绑定半成品来源明细。
4. 批量确认入库，自动写入库存链路。
5. 支持托盘维度的查询与追溯（化验、库存位置）。

---

## 2. 统一约定

## 2.1 Base URL
- 本地：`http://localhost:8080`
- 路径前缀：`/api/pallet-codes`

## 2.2 鉴权
- 除公开文档未声明的特殊接口外，均需登录态（JWT）。
- 通常从 `@AuthenticationPrincipal LoginUser` 获取操作人。

## 2.3 统一响应格式

```json
{
  "code": 200,
  "msg": "success",
  "data": {}
}
```

- 成功：`code = 200`。
- 失败：`code != 200`，`msg` 为错误原因。

分页数据 `data` 结构：

```json
{
  "total": 123,
  "records": []
}
```

## 2.4 枚举字典

### 托盘状态 `pallet_code.status`
- `FREE`：空闲，可绑定。
- `PENDING`：已绑定，待入库。
- `INSTOCK`：已入库。
- `CONSUMED`：已被消耗（多见于半成品作为原料被扣减后）。
- `INVALID`：已作废。

### 任务类型 `pallet_task.task_type`
- `SEMI_IN`：半成品入库任务。
- `FINISH_IN`：成品入库任务。

### 任务状态 `pallet_task.status`
- `PENDING`：待处理。
- `CONFIRMED`：已确认入库。
- `CANCELED`：已取消。

### 单位
- `0`：板
- `1`：件

---

## 3. 接口清单（按业务阶段）

## 3.1 生成与管理托盘码

### 3.1.1 批量生成托盘码
- **URL**：`POST /api/pallet-codes/generate`
- **功能**：生成指定数量托盘码（每次 1~100）。
- **请求体**：

```json
{
  "count": 20
}
```

- **参数说明**：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| count | Integer | 是 | 生成数量，1~100 |

- **响应 data**：`string[]`（托盘码数组）

```json
{
  "code": 200,
  "msg": "success",
  "data": ["BT0A3ZK", "BT1FD98", "BT7P2QW"]
}
```

- **失败示例**：
  - `生成数量必须在1-100之间`

---

### 3.1.2 托盘码分页查询
- **URL**：`POST /api/pallet-codes`
- **功能**：托盘码管理页查询。
- **请求体**：

```json
{
  "code": "BT0A3ZK",
  "productName": "白冰糖A",
  "productType": "白冰糖",
  "productStatus": "成品",
  "productionDateStart": "2026-04-01",
  "productionDateEnd": "2026-04-08",
  "inventoryOnly": false,
  "pageNum": 1,
  "pageSize": 10
}
```

- **参数说明**：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| code | String | 否 | 托盘码精确匹配 |
| productName | String | 否 | 产品名模糊匹配 |
| productType | String | 否 | 产品类型 |
| productStatus | String | 否 | 半成品/成品 |
| productionDateStart | LocalDate | 否 | 生产日期起 |
| productionDateEnd | LocalDate | 否 | 生产日期止 |
| inventoryOnly | Boolean | 否 | 是否仅返回存在当前库存记录的托盘；产品库存页托盘库存视图使用 `true` |
| pageNum | Long | 否 | 页码，默认 1 |
| pageSize | Long | 否 | 页大小，默认 10 |

- **响应 data**：`PageResult<PalletCodePageVO>`

`records` 单项字段：
`id, code, status, productName, productType, productStatus, productionDate, screenMeshName, assayId, createdAt, createdByName, updatedAt, updatedByName`

---

### 3.1.3 获取托盘二维码图片
- **URL**：`GET /api/pallet-codes/{code}/qrcode`
- **功能**：生成托盘码二维码 PNG（用于预览/打印）。
- **路径参数**：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| code | String | 是 | 托盘码 |

- **响应**：`image/png` 二进制图片。
- **失败示例**：
  - `托盘码格式非法`
  - `托盘码校验失败`
  - `托盘码不存在`

---

### 3.1.4 批量作废托盘码
- **URL**：`POST /api/pallet-codes/invalid`
- **功能**：将托盘置为 `INVALID`，并取消关联任务。
- **请求体**：

```json
{
  "codes": ["BT0A3ZK", "BT1FD98"],
  "remark": "标签污染，重新制码"
}
```

- **参数说明**：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| codes | String[] | 是 | 托盘码列表，不能为空 |
| remark | String | 否 | 备注 |

- **响应 data**：`null`

---

## 3.2 扫码绑定与任务处理

### 3.2.1 扫码解析托盘码
- **URL**：`GET /api/pallet-codes/parse?code=BT0A3ZK`
- **功能**：校验并解析托盘码，返回基础信息。
- **查询参数**：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| code | String | 是 | 托盘码 |

- **响应 data**：`PalletCodeInfoVO`

字段：
`id, code, status, productName, productStatus, productionDate, screenMeshName, assayId, createdAt, createdBy, updatedAt, updatedBy`

---

### 3.2.2 绑定托盘并创建入库任务
- **URL**：`POST /api/pallet-codes/bind`
- **功能**：扫码后绑定产品信息，创建待入库任务（不处理化验数据）。
- **请求体**：

```json
{
  "code": "BT0A3ZK",
  "productId": 101,
  "productStatus": "成品",
  "productionDate": "2026-04-08",
  "quantity": 1,
  "unit": "0",
  "remark": "早班扫码绑定"
}
```

- **参数说明**：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| code | String | 是 | 扫码得到的托盘码 |
| productId | Integer | 是 | 产品ID |
| productStatus | String | 是 | `半成品` / `成品` |
| productionDate | LocalDate | 是 | 生产日期 |
| quantity | Integer | 否 | 数量（默认 1） |
| unit | String | 是 | `0` 板 / `1` 件 |
| remark | String | 否 | 备注 |

- **响应 data**：`PalletBindResultVO`

字段：
`palletCodeId, code, palletStatus, taskId, taskType, taskStatus, productId, productName, productType, productStatus, screenMeshId, screenMeshName, productionDate, createdAt, remark`

- **关键规则**：
  1. 托盘必须是 `FREE` 才能绑定。
  2. 同托盘不能已存在未完成任务。
  3. `productStatus=半成品` 时创建 `SEMI_IN`；`成品` 时创建 `FINISH_IN`。
  4. 绑定成功后托盘变为 `PENDING`。

---

### 3.2.3 托盘任务分页查询
- **URL**：`POST /api/pallet-codes/tasks/list`
- **功能**：任务中心列表查询。
- **请求体**：

```json
{
  "code": "BT0A3ZK",
  "taskType": "FINISH_IN",
  "status": "PENDING",
  "productName": "白冰糖A",
  "productType": "白冰糖",
  "productStatus": "成品",
  "productionDateStart": "2026-04-01",
  "productionDateEnd": "2026-04-08",
  "pageNum": 1,
  "pageSize": 10
}
```

- **响应 data**：`PageResult<PalletTaskPageVO>`

`records` 单项字段：
`taskId, taskType, taskStatus, palletCodeId, code, productId, productName, productType, productStatus, productionDate, screenMeshId, screenMeshName, hasSemiItems, semiItemCount, semiItems, assayId, createdBy, createdAt, confirmedBy, confirmedAt`

---

### 3.2.4 成品任务绑定半成品明细
- **URL**：`POST /api/pallet-codes/tasks/semi-bind`
- **功能**：给 `FINISH_IN + PENDING` 任务绑定半成品来源（全量覆盖）。
- **请求体**：

```json
{
  "code": "BT0A3ZK",
  "items": [
    {
      "semiPalletCode": "BT9H2TK",
      "quantity": 1,
      "unit": "0",
      "useAssay": true
    },
    {
      "semiPalletCode": "BT7P2QW",
      "quantity": 10,
      "unit": "1",
      "useAssay": false
    }
  ]
}
```

- **参数说明（items）**：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| semiPalletCode | String | 是 | 半成品托盘码 |
| quantity | Integer | 是 | 数量 |
| unit | String | 是 | `0` 板 / `1` 件 |
| useAssay | Boolean | 否 | 是否套用该条半成品的化验数据 |

- **响应 data**：`TaskSemiItemVO[]`

字段：
`id, semiPalletCode, semiProductId, semiProductName, productionDate, quantity, unit, useAssay`

- **关键规则**：
  1. 仅对成品待处理任务有效。
  2. 半成品托盘必须为“半成品”且状态为 `INSTOCK` 或 `CONSUMED`。
  3. `useAssay=true` 仅允许 1 条。
  4. 提交是“全量覆盖”语义（不是增量追加）。

---

### 3.2.5 批量确认托盘入库
- **URL**：`POST /api/pallet-codes/tasks/confirm`
- **功能**：批量确认入库，按任务类型自动走半成品/成品链路。
- **请求体**：

```json
{
  "items": [
    {
      "code": "BT0A3ZK",
      "warehouseName": "A1",
      "entryDate": "2026-04-08",
      "side": "左",
      "quantity": 1,
      "unit": "0",
      "remark": "入库确认"
    }
  ]
}
```

- **参数说明（items）**：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| code | String | 是 | 托盘码 |
| warehouseName | String | 是 | 入库仓库名称 |
| entryDate | LocalDate | 否 | 入库日期，默认任务生产日期 |
| side | String | 否 | 左/右，默认左 |
| quantity | Integer | 否 | 默认 1 |
| unit | String | 否 | 默认 `0`（板） |
| remark | String | 否 | 备注 |

- **响应 data**：`InVO[]`

- **关键规则**：
  1. 托盘必须是 `PENDING`。
  2. 必须存在待处理任务。
  3. 成品任务会校验半成品明细并执行库存扣减链路。
  4. 入库成功后：托盘 `INSTOCK`、任务 `CONFIRMED`、写入流转记录。

---

### 3.2.6 批量取消入库任务
- **URL**：`POST /api/pallet-codes/tasks/cancel`
- **功能**：按托盘码取消任务，并最终作废托盘码。
- **请求体**：同 `invalid`

```json
{
  "codes": ["BT0A3ZK"],
  "remark": "工单取消"
}
```

- **响应 data**：`null`

- **结果说明**：
  - 相关任务改为 `CANCELED`
  - 托盘改为 `INVALID`

---

### 3.2.7 仓库平面图创建任务
- **URL**：`POST /api/pallet-codes/warehouse-map/tasks/create`
- **功能**：按仓库平面图的库位、侧别和前 N 板创建任务；传入 `codes` 时按指定托盘码精确创建任务。

请求体关键字段：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| operationType | String | 是 | `OUT` / `TRANSFER` / `PREPARE`；`PREPARE` 表示半成品转入备料池任务 |
| warehouseId | Integer | 是 | 来源库位 ID |
| side | String | 是 | 来源侧：左 / 右 |
| quantity | Integer | 是 | 前 N 板；传入 `codes` 时按 `codes.size()` 为准 |
| codes | String[] | 否 | 指定托盘码列表；用于格子级单板操作 |
| rowNumber | Integer | 否 | 指定格子排号；传入 `codes` 时用于校验托盘仍在点击格子 |
| layer | Integer | 否 | 指定格子层数；传入 `codes` 时用于校验托盘仍在点击格子 |
| targetWarehouseName | String | 调拨时必填 | 调拨目标库位名称 |
| targetSide | String | 调拨时必填 | 调拨目标侧：左 / 右 |
| remark | String | 否 | 备注 |

说明：
- `PREPARE` 只支持半成品托盘，后端会创建 `taskType=OUT, bizScene=PREPARE_CONSUMED` 的待处理任务。
- 传入 `codes + rowNumber + layer` 时，后端会校验托盘仍在当前仓库、侧、排、层，避免格子点击后位置变化导致误建任务。
- 返回 `WarehouseMapTaskCreateResultVO`，前端可按 `routePath` 提示“去处理”。

---

## 3.3 追溯查询

### 3.3.1 查询托盘化验数据
- **URL**：`GET /api/pallet-codes/{code}/assay`
- **功能**：按托盘码查询化验信息；支持从任务/产品+日期回填化验关联。
- **响应 data**：`PalletAssayVO`

字段：
`productName, sampleDate, colorValue, reducingSugar, dryWeight, conductivityAsh, sucrose, insolubleImpurity, phValue, testerName, isQualified, qualifiedStandards, createdAt`

---

### 3.3.2 查询托盘库存位置
- **URL**：`GET /api/pallet-codes/{code}/inventory`
- **功能**：查询托盘当前所在库位。
- **响应 data**：`PalletInventoryVO`

字段：
`warehouseName, side, rowNumber, layer, quantity, unit, inStockTime`

说明：`unit=false` 表示板，`unit=true` 表示件。

---

### 3.3.3 查询托盘流转轮次分页
- **URL**：`GET /api/pallet-codes/{code}/flows/cycles?pageNum=1&pageSize=5`
- **功能**：按托盘码分页查询该托盘所有历史循环轮次摘要；默认每页 5 个 cycle。
- **响应 data**：`PageResult<PalletFlowCyclePageVO>`

字段：
`cycleNo, productId, productName, productStatus, startTime, endTime, flowCount, isCurrentCycle, isEnded`

说明：
- `isCurrentCycle = cycleNo == pallet_code.current_cycle_no`。
- `isEnded` 表示该轮已结束；历史轮次必然为 `true`，当前轮次在托盘已释放回 `FREE` 或作废为 `INVALID` 时也为 `true`。
- 兼容历史数据：`cycle_no = 0` 的流转记录会作为第 0 轮返回。

---

### 3.3.4 查询托盘指定轮次流转明细
- **URL**：`GET /api/pallet-codes/{code}/flows?cycleNo=3`
- **功能**：按托盘码 + 循环号查询该轮全部 flow 明细，按 `operation_time ASC, id ASC` 排序。
- **响应 data**：`PalletFlowDetailVO[]`

字段：
`id, cycleNo, taskId, operationType, operationName, operationTime, operatorId, operatorName, productId, productName, productStatus, assayId, fromWarehouseId, fromWarehouseName, fromSide, fromRowNumber, fromLayer, toWarehouseId, toWarehouseName, toSide, toRowNumber, toLayer, remark, extData`

说明：任务触发的流转记录会写入 `taskId`，追溯时优先使用该结构化字段关联 `pallet_task.id`，`remark` 仅保留为人工说明。

---

### 3.3.5 批量删除托盘流转记录
- **URL**：`POST /api/pallet-codes/flows/delete`
- **功能**：按 flow 记录 ID 批量删除历史记录。
- **删除限制**：
  - 记录必须全部存在。
  - 不允许删除当前轮次：`pallet_flow_record.cycle_no == pallet_code.current_cycle_no` 时拒绝。
  - 不允许删除 180 天内记录：仅 `operation_time < now - 180 days` 可删除。

请求体：

```json
{
  "ids": [101, 102, 103]
}
```

---

### 3.3.6 流转记录定时清理
- **执行时间**：每天凌晨 3 点。
- **清理范围**：`operation_time < now - 180 days` 且 `pallet_flow_record.cycle_no <> pallet_code.current_cycle_no` 的流转记录。
- **保护规则**：当前轮次不删，180 天内不删。
- **实现方式**：批量 SQL 删除，执行前打印可清理数量。

---

### 3.3.7 库位台账与最近操作
- **URL**：`GET /api/inventory/query`
- **功能**：库位台账分页查询；前端传 `sortField/sortOrder`，后端执行排序。
- **常用参数**：`warehouseName, status, page, size, sortField, sortOrder, createdStart, createdEnd, updatedStart, updatedEnd`
- **排序字段**：`namePinyin` 按 `warehouse_name` 拼音排序；`createdAt` 按创建时间；`updatedAt` 按最近修改时间。
- **响应字段补充**：`maxRows, currentPalletCount, currentProductCount, createdAt, updatedAt`

- **URL**：`GET /api/inventory/warehouses/{warehouseId}/recent-operations?limit=10`
- **功能**：查询指定库位最近流转操作记录，默认最近 10 条。
- **响应字段**：`operationTime, operationType, operationName, operatorName, palletCode, productName, fromWarehouseName, toWarehouseName, remark`

---

## 4. 模块工作流程

## 4.1 主流程（扫码绑定到入库）

```mermaid
flowchart TD
    A[批量生成托盘码] --> B[打印/贴码]
    B --> C[现场扫码 parse]
    C --> D{托盘状态}

    D -->|FREE| E[bind 绑定产品并创建任务]
    E --> F{任务类型}

    F -->|SEMI_IN| G[直接确认入库 confirm]
    F -->|FINISH_IN| H[semi-bind 绑定半成品来源]
    H --> I[确认入库 confirm]

    G --> J[托盘=INSTOCK 任务=CONFIRMED]
    I --> J

    D -->|PENDING| K[进入任务中心继续处理]
    D -->|INSTOCK/CONSUMED/INVALID| L[追溯查询 assay/inventory]
```

## 4.2 异常与中断流程

```mermaid
flowchart TD
    A[任务待处理] --> B{是否取消/作废}
    B -->|是| C[tasks/cancel]
    C --> D[任务=CANCELED]
    D --> E[托盘=INVALID]

    B -->|否| F[继续入库确认]
```

---

## 5. 状态变更说明

## 5.1 托盘状态机

```mermaid
stateDiagram-v2
    [*] --> FREE: generate
    FREE --> PENDING: bind
    PENDING --> INSTOCK: tasks/confirm
    PENDING --> INVALID: invalid / tasks/cancel
    INSTOCK --> CONSUMED: 被后续生产领用/扣减
    CONSUMED --> [*]
    INVALID --> [*]
```

## 5.2 任务状态机

```mermaid
stateDiagram-v2
    [*] --> PENDING: bind 创建任务
    PENDING --> CONFIRMED: tasks/confirm
    PENDING --> CANCELED: tasks/cancel 或 invalid 触发取消
    CONFIRMED --> [*]
    CANCELED --> [*]
```

## 5.3 关键动作与状态结果对照

| 动作 | 接口 | 托盘状态 | 任务状态 | 说明 |
|---|---|---|---|---|
| 生成托盘码 | `generate` | `FREE` | - | 初始可绑定 |
| 绑定并建任务 | `bind` | `PENDING` | `PENDING` | 任务类型由产品状态决定 |
| 成品绑定半成品 | `tasks/semi-bind` | 不变 | 不变 | 更新任务来源明细 |
| 确认入库 | `tasks/confirm` | `INSTOCK` | `CONFIRMED` | 自动写流转记录 |
| 作废托盘 | `invalid` | `INVALID` | `CANCELED`(若有) | 任务被取消 |
| 取消任务 | `tasks/cancel` | `INVALID` | `CANCELED` | 同时作废托盘 |

---

## 6. 联调建议

### 6.1 并发与历史收尾约束

- `inventory` 增加唯一约束 `uk_inventory_location(warehouse_id, side, row_number, layer)`，同一具体库位同一时刻只允许一块托盘/一板库存占用。
- 入库与托盘调拨在写入目标库位时，如果遇到唯一约束冲突，会重新读取最新库位占用并最多重试 3 次；超过后返回“库位分配冲突，请重试”。
- 托盘开始新一轮绑定前，会自动将该托盘 `cycle_no < current_cycle_no` 且 `status = PENDING` 的旧任务置为 `CANCELED`，并写入 `operation_type = CANCELED` 的流转记录。
- `pallet_flow_record.task_id` 用于结构化关联触发该 flow 的任务；无任务触发的作废、人工消耗确认等记录允许为空。

1. **前端先做字典统一**：状态/单位统一 map，避免硬编码散落。
2. **提交前预校验**：
   - `useAssay=true` 只能 1 条；
   - `quantity > 0`；
   - 生产日期/入库日期格式正确。
3. **错误提示统一**：直接透传后端 `msg`，尤其是状态类错误（如“仅允许 FREE 绑定”）。
4. **流程页建议按状态分流**：`FREE` 走绑定，`PENDING` 走任务处理，其他走追溯只读。

---

## 7. 常见错误码/报错（示例）

> 当前模块业务异常多通过 `Result.error(code, msg)` 返回，以下为常见 `msg`：

- `托盘码格式非法`
- `托盘码校验失败`
- `托盘码不存在`
- `托盘当前状态不可绑定（仅允许 FREE 状态绑定）`
- `该托盘已存在未完成的入库任务`
- `未找到待处理的成品入库任务`
- `仅允许一条明细使用化验数据`
- `找不到化验数据`
- `当前托盘状态不支持入库确认`

