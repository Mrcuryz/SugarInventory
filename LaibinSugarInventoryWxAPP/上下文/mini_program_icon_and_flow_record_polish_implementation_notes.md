# 小程序图标系统与流转记录精修实现说明

## 本次范围

本轮按 `mini_program_icon_and_flow_record_polish_task.md` 收口以下内容：

- 托盘详情页流转记录不再使用首字缩写图标，统一改为线性 SVG 动作图标。
- 流转记录按事件类型输出不同的位置/业务阶段表达，不再硬套统一箭头。
- 化验参考标准不再直接展示接口原始数组字符串。
- 查询中心、任务中心、扫码空态、工作台提醒、我的页入口补齐统一线性图标。

## 图标系统

新增统一线性图标资源目录：

- `assets/icons-line/flow-link.svg`
- `assets/icons-line/flow-in.svg`
- `assets/icons-line/flow-out.svg`
- `assets/icons-line/flow-transfer.svg`
- `assets/icons-line/flow-prepare.svg`
- `assets/icons-line/flow-cancel.svg`
- `assets/icons-line/flow-invalid.svg`
- `assets/icons-line/flow-assay.svg`
- `assets/icons-line/flow-consume.svg`
- `assets/icons-line/icon-search.svg`
- `assets/icons-line/icon-task.svg`
- `assets/icons-line/icon-notice.svg`
- `assets/icons-line/icon-location.svg`
- `assets/icons-line/icon-help.svg`
- `assets/icons-line/icon-contact.svg`
- `assets/icons-line/icon-version.svg`

`empty-state` 组件新增 `icon` 属性，支持各页面按业务类型展示统一线性图标。

## 流转记录事件表达

托盘详情页按 `operationType` 映射图标，不再使用 `绑 / 入 / 备` 等首字徽标。

流转记录 UI 已进一步做轻量化：

- 操作人前使用用户线性小图标，不再额外堆“操作人”文字。
- 每条记录改为卡片式三层结构：头部为操作类型和辅助时间，副信息为操作人，内容区按业务模板展示。
- 位置信息不再拆成大量同权重胶囊，而是展示为 `库位1 · 左侧 · 第1排 · 1层` 这类结构化文本。
- 调拨记录用“原位置 / 箭头图标 / 目的位置”突出库存位置变化，并弱化展示托盘码、任务 ID 等附加信息。
- 备注前使用备注小图标，颜色和字号进一步弱化。
- 日期标题放大并加重，右侧时间改为更小、更淡的辅助文本，不再像状态标签。

## 位置信息来源排查

后端确认入库、出库、调拨、转入备料池时，会将当时的 `inventory` 位置快照写入 `pallet_flow_record`：

- 入库写 `to_warehouse_id / to_side / to_row_number / to_layer`
- 出库写 `from_warehouse_id / from_side / from_row_number / from_layer`
- 调拨写 `from_*` 和 `to_*`
- 转入备料池写 `from_*`

若小程序拿到的 flow 位置为空，通常不是 `inventory` 当前表没有字段，而是对应 `pallet_flow_record` 记录缺少当时的位置快照。后端查询已增加当前库存兜底：当 flow 快照缺失且当前 `inventory.pallet_code_id` 仍存在时，会用当前库存补可推导的位置。

注意：普通出库、转入备料池执行后库存行可能已删除；如果历史 flow 当时没有写入 `from_*` 快照，就不能再从当前 `inventory` 反推出原位置。

位置表达规则已调整为“按事件模板展示，字段缺失时用未记录占位，不整块隐藏”：

- `SEMI_INSTOCK` / `FINISH_INSTOCK`：只展示目标位置，格式为 `存入 库位 X · 左侧 · 1排 · 1层`。
- `OUT`：只展示来源位置，格式为 `从 库位 X · 左侧 · 1排 · 1层 出库`。
- `TRANSFER`：展示来源与目标，格式为 `库位 X · 左侧 · 1排 · 1层 调拨至 库位 Y · 右侧 · 2排 · 1层`。
- `PREPARE_CONSUMED`：展示来源位置与业务阶段变化，格式为 `从 库位 X · 左侧 · 1排 · 1层 转入备料池`。
- `CONSUMED`：展示业务结果 `备料池托盘已完成消耗`。
- `SEMI_BIND` / `FINISH_BIND`：展示绑定关系说明 `与成品托盘完成绑定`，不展示仓内位置迁移。
- `CANCELED` / `ASSAY` / `INVALID`：不展示位置变化。

库内位置优先展示库位、侧、排、层；如果历史记录缺少某个字段，使用 `库位未记录 / 侧未记录 / 排未记录 / 层未记录`，避免位置信息整块消失。

## 化验标准展示

`qualifiedStandards` 会先尝试解析 JSON；当接口返回 `["无"]`、`[]`、`无` 等值时，统一转成：

- `无匹配标准`
- 或 `本次未关联标准`

避免在小程序中直接暴露接口原始数组字符串。

## 页面精修

- 查询中心：托盘查询标题和其他查询模块增加线性图标，弱化规划标签，不再出现“第二阶段”开发标记。
- 任务中心：任务摘要增加清单图标，空态文案改成任务处理语义。
- 工作台：系统提醒和空态使用统一图标。
- 我的页：常用入口改为统一线性 SVG 图标，登录标签收敛为“在线”。
