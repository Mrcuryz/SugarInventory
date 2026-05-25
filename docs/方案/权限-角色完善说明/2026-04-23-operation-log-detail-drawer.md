# 操作日志详情抽屉改版

日期：2026-04-23

本次调整分两部分：

1. 后端操作日志字段过滤
- 在 `OperationLogAspect` 中忽略化验自动判定相关内部字段：
  - `appliedStandardId`
  - `appliedStandardName`
  - `appliedStandardVersion`
  - `judgeResult`
  - `failedMetricCount`
  - `failedMetricsJson`
  - `standardSnapshotJson`
  - `judgeMessage`
- 原因：这些字段来自系统自动判定过程，不属于用户直接操作内容，继续记录会让日志噪音过大。

2. Web 操作日志页改版
- 主表简化为：
  - 时间
  - 操作业务
  - 操作类型
  - 操作人
  - 操作
- “操作内容”“原数据”移入右侧详情抽屉
- 抽屉展示规则：
  - 新增：只显示操作内容
  - 删除：只显示原数据
  - 修改：显示字段、修改前、修改后

涉及文件：
- `src/main/java/com/Laibin/SugarInventory/aspect/OperationLogAspect.java`
- `webpage/src/components/OperationLogs.vue`
