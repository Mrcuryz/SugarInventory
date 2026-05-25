# 小程序“我的”页角色与手机号修复

日期：2026-04-22

本次修复针对小程序“我的”页的两处展示问题：

1. 角色显示统一为角色名称  
`/api/user/info` 新增返回 `roleName`，小程序优先展示角色名称，只有缺失时才回退到 `roleCode`。

2. 手机号与绑定方式展示纠正  
`/api/user/info` 新增返回 `mobile`、`phone`、`bindMethod`。  
小程序“我的”页不再只依赖旧的 `phone` 字段，改为优先读取 `mobile`，并根据 `bindMethod` 显示“手机号绑定”或“工号绑定”。

涉及文件：

- `src/main/java/com/Laibin/SugarInventory/controller/UserController.java`
- `src/main/java/com/Laibin/SugarInventory/domain/vo/UserInfoVO.java`
- `LaibinSugarInventoryWxAPP/pages/profile/index/index.js`
- `LaibinSugarInventoryWxAPP/pages/profile/index/index.wxml`
