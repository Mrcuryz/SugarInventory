# 智能报数旧半成品备料池消费路径清理

日期：2026-08-13  
结论：已从 `AutoInboundConfirmServiceImpl` 删除不可达的旧半成品备料池扣减代码；未删除历史表、迁移或查询接口。

## 事实依据

- 当前智能报数确认在执行前明确拒绝 `FINISHED_PRODUCT`，要求先关联生产订单，再由生产订单登记产出和二维码；
- 被删除的 `applyProductionConsumption` 只对 `FINISHED_PRODUCT` 生效，因此在当前控制流中不可达；
- 旧代码会更新 `semi_prepare_pool_balance` 并写入 `production_consumption_record`，与已确认的现行业务口径冲突；
- 现行原料实际消耗事实来自生产订单物料领用：二维码领用并确认后扣减库存，以数据库已有状态和记录为准。

## 改动边界

- 删除智能报数确认服务对 `SemiPreparePoolBalanceMapper`、`ProductionConsumptionRecordMapper` 的依赖；
- 删除旧余额扣减、重量换算和旧消费记录写入私有方法；
- 保留历史模型、表、迁移和只读兼容能力，避免把本次死代码清理扩大成数据结构删除；
- 保留智能报数生产报告中的源文本展示逻辑，但它不再写入旧消费事实。

## 防回归

`AutoInboundLegacyConsumptionBoundaryTest` 锁定以下边界：智能报数确认不得重新依赖旧备料池 Mapper、不得调用旧行锁查询、不得写入“智能报数成品入库登记半成品历史用量”。
