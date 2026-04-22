# 小程序绑定页 UI 重构

日期：2026-04-22

本次将小程序“手机号绑定”和“工号绑定”页面按登录页同一套视觉风格重做。

处理内容：

1. 统一页面结构  
- 顶部说明区  
- 中部主卡片  
- 底部说明卡

2. 统一视觉元素  
- 左上圆形背景  
- 右上点阵装饰  
- 浅蓝品牌标签和大标题  
- 白色主卡片与蓝色主按钮

3. 文案清理  
- 修复原有乱码标题和提示文案  
- 保持绑定逻辑不变，只调整展示与交互文案

涉及文件：

- `LaibinSugarInventoryWxAPP/pages/auth/bind-phone/index.wxml`
- `LaibinSugarInventoryWxAPP/pages/auth/bind-phone/index.wxss`
- `LaibinSugarInventoryWxAPP/pages/auth/bind-phone/index.js`
- `LaibinSugarInventoryWxAPP/pages/auth/bind-phone/index.json`
- `LaibinSugarInventoryWxAPP/pages/auth/bind-manual/index.wxml`
- `LaibinSugarInventoryWxAPP/pages/auth/bind-manual/index.wxss`
- `LaibinSugarInventoryWxAPP/pages/auth/bind-manual/index.js`
- `LaibinSugarInventoryWxAPP/pages/auth/bind-manual/index.json`
