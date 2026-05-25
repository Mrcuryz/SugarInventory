# 微信开发者工具项目根目录修正

日期：2026-04-22

问题现象：

- 微信开发者工具编译时报：
  - `app.json: ["pages"][0] could not find the corresponding file: "pages/auth/login/index.wxml"`

原因：

- 仓库根目录本身也存在 `project.config.json`
- 如果直接用仓库根目录打开项目，而不是打开 `LaibinSugarInventoryWxAPP`
- 开发者工具会按根目录项目配置解析，导致小程序页面路径定位错误

处理：

- 在仓库根目录的 `project.config.json` 和 `project.private.config.json` 中补充：
  - `miniprogramRoot: "LaibinSugarInventoryWxAPP/"`

结果：

- 即使从仓库根目录导入项目，开发者工具也会把小程序根目录正确指向 `LaibinSugarInventoryWxAPP`
