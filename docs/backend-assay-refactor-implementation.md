# 后端化验解析收口实现说明

本次按 `LaibinSugarInventoryWxAPP/上下文/backend_assay_refactor_plan.md` 的后端目标做收口：

- 入库链路不再因为没有 `assayId` 直接拒绝业务，`in_stock`、`inventory`、`semi_product_record` 的化验关联改为可空。
- 新增 `AssayResolveService` 作为统一化验解析入口，托盘化验查询、托盘入库确认、成品套用半成品化验等链路复用该服务。
- 解析优先级为：托盘显式 `assayId` -> 当前库存 `assayId` -> 当前任务 `assayId` -> `productId + productionDate/businessDate` fallback。
- fallback 命中唯一候选或唯一最新版本时自动回填托盘、当前库存、当前任务，并写入 `ASSAY` 托盘流转记录。
- 若回填时发现已有非空 `assayId` 被改绑到另一条确定化验，流转名称记录为“更新化验关联”。
- 成品任务套用半成品化验时，也会为成品托盘补一条独立的 `ASSAY` 流转记录。
- fallback 命中多条且无法按唯一最新版本收敛时，不自动绑定，返回 `multiple_candidates` 状态。
- 托盘化验接口不再把“暂无化验”作为业务失败抛出，而是返回带 `resolveStatus/resolveMessage` 的空化验结果，方便前端稳定展示。
