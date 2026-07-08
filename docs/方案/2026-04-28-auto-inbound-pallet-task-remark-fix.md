# 智能报数入库任务备注超长修复

## 问题

智能报数确认入库时，每个二维码都会创建一条 `pallet_task`。此前代码把任务备注、入库数量以及生产消耗处理结果一起写入 `pallet_task.remark`。当生产消耗结果较多时，备注内容会超过当前数据库字段 `varchar(255)`，导致确认入库失败。

## 修复

1. `pallet_task.remark` 只保留短摘要：`智能报数入库`、当前二维码对应数量、人工备注摘要。
2. 不再把生产消耗明细写入每条托盘任务备注。
3. 生产消耗明细继续保存在 `production_report_record` 和 `production_consumption_record` 中。
4. 对 `pallet_task.remark` 和 `production_report_record.remark` 增加长度保护，避免后续类似截断错误。

## 口径

托盘任务备注用于快速识别任务来源，不承载完整报数和生产消耗明细；完整追溯应查看生产报数留档和生产消耗记录。
