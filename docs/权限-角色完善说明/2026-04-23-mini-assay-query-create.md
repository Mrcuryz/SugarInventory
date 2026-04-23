# 小程序化验查询页新增录入

日期：2026-04-23

本次改动在小程序查询中心下的“化验查询”页面补齐“新增”能力，复用现有后端化验录入接口，不新增后端逻辑。

实现范围：
- 在 `pages/query/assay` 页面增加“新增”入口
- 新增表单字段与 Web 端化验管理页保持一致：
  - 选择类型：选择产品（单次录入）/ 批量化验组（批量录入）
  - 化验产品名称
  - 采样日期
  - 色值
  - 还原糖分
  - 干燥失重
  - 电导灰分
  - 蔗糖分
  - 不溶于水杂质
  - pH值
- 选择产品时复用产品三级选择器
- 选择批量化验组时复用批量化验组查询接口
- 提交时调用 `/api/assay/import`

涉及文件：
- `LaibinSugarInventoryWxAPP/pages/query/assay/index.js`
- `LaibinSugarInventoryWxAPP/pages/query/assay/index.wxml`
- `LaibinSugarInventoryWxAPP/pages/query/assay/index.wxss`
- `LaibinSugarInventoryWxAPP/api/query.js`
