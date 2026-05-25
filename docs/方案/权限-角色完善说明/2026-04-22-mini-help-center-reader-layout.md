# 小程序帮助中心改版

日期：2026-04-22

本次将小程序帮助中心从“整页折叠卡片”改为“目录 + 正文”的双栏阅读布局。

主要改动：

1. 左侧目录栏  
- 支持当前章节高亮  
- 支持收起 / 展开  
- 支持滚动  
- 记住上次的目录收起状态和当前章节

2. 右侧正文区  
- 每章统一为：本章说明、操作步骤、现场注意事项、常见问题、立即操作  
- 底部提供“上一章 / 下一章”

3. 章节动作按钮  
- 支持直接跳转到扫码页、任务中心、查询中心、库存查询、化验查询、作业记录等页面  
- 对扫码页和任务中心补了“带默认状态”的跳转能力：
  - 扫码页可带默认作业模式
  - 任务中心可带默认状态筛选

4. 权限判断  
- 动作按钮按 `permissionCodes` 做可访问判断  
- 无权限时按钮置灰，不再误跳转

涉及文件：

- `LaibinSugarInventoryWxAPP/pages/help/index/index.js`
- `LaibinSugarInventoryWxAPP/pages/help/index/index.wxml`
- `LaibinSugarInventoryWxAPP/pages/help/index/index.wxss`
- `LaibinSugarInventoryWxAPP/pages/help/index/index.json`
- `LaibinSugarInventoryWxAPP/pages/tasks/index/index.js`
- `LaibinSugarInventoryWxAPP/utils/storage.js`
