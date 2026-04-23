# 2026-04-23 二维码管理、化验回填与入库收口

本轮调整覆盖以下三类问题：

1. 二维码管理页操作栏按状态收口
2. 化验回填导致 `pallet_flow_record.operator_id` 为空时报错
3. 入库链路仍然强制依赖当天化验记录

## 后端

- `AssayResolveService` 新增只预览不回写的 `previewForPallet(...)`
- `AssayResolveServiceImpl` 在 `operatorId` 为空时不再写入 `ASSAY` 流转记录
- `PalletCodeServiceImpl`
  - 查询二维码化验时改为预览解析，不再在 GET 场景回写
  - 半成品入库、成品入库不再因为缺少化验记录直接拒绝业务
  - 新增 `restoreInvalidPalletCodes(...)`，支持取消作废
- `PalletCodeController`
  - 新增 `/api/pallet-codes/invalid/restore`
  - 对入库确认、化验查询、库存位置查询、作废动作增加通用异常兜底，避免原始 SQL 报错直接返回前端

## Web

- 二维码管理页操作栏按状态展示：
  - `FREE`：绑定、二维码、下载、流转、作废
  - `PENDING`：二维码、下载、任务、流转、化验
  - `INSTOCK`：二维码、下载、化验、位置、流转
  - `INVALID`：二维码、下载、取消作废
- 任务跳转支持带 `code/status` 预设查询
- 仓库平面图支持从路由读取 `palletCode/warehouseName`
- 二维码管理页支持在无化验记录时直接弹出新增化验表单

## 相关文件

- `src/main/java/com/Laibin/SugarInventory/service/impl/AssayResolveServiceImpl.java`
- `src/main/java/com/Laibin/SugarInventory/service/impl/PalletCodeServiceImpl.java`
- `src/main/java/com/Laibin/SugarInventory/controller/PalletCodeController.java`
- `src/main/java/com/Laibin/SugarInventory/domain/vo/PalletCodePageVO.java`
- `src/main/java/com/Laibin/SugarInventory/mapper/PalletCodeQueryMapper.java`
- `webpage/src/api/palletCode.js`
- `webpage/src/components/PalletCodeList.vue`
- `webpage/src/components/PalletTaskCenter.vue`
- `webpage/src/components/WarehouseMap.vue`

## 中断恢复补充

- 恢复 `PalletCodeController` 中被错误编码污染的中文注释、Swagger 文案和异常提示。
- Web 端使用 `webpage/src/assets/logo.png` 作为页面图标，并替换登录页品牌图标。
- 小程序帮助中心新增首页海报展示，使用 `LaibinSugarInventoryWxAPP/assets/poster.png`。
- 小程序扫码页补齐 `getTaskList` 导入，并在校验待处理任务失败时返回明确提示，避免出现 `Can't find variable: getTaskList`。
