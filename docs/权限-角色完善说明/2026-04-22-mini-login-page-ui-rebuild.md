# 小程序登录页 UI 重构

日期：2026-04-22

本次将小程序登录页从普通表单式页面重构为正式产品入口页，结构调整为三段式：

1. 顶部品牌欢迎区  
- 浅蓝渐变背景  
- Logo 小图标置于主标题前  
- 标题、副标题、辅助说明统一居中

2. 中间登录主卡片  
- 标题调整为“欢迎登录”  
- 主按钮突出为“微信一键登录”  
- 手机号绑定、工号绑定作为弱化次按钮并排展示

3. 底部首次使用说明区  
- 改为轻量说明块  
- 保留 3 条简短说明  
- 视觉层级弱于主登录卡片

涉及文件：

- `LaibinSugarInventoryWxAPP/pages/auth/login/index.wxml`
- `LaibinSugarInventoryWxAPP/pages/auth/login/index.wxss`
- `LaibinSugarInventoryWxAPP/pages/auth/login/index.json`
