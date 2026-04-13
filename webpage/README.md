# web

## 托盘码模块 Web 管理端

第一阶段已挂载托盘码管理和拆分后的任务中心入口：

- `/pallet-code/list`：托盘码管理，支持分页筛选、批量生成、二维码预览、绑定、作废、化验/库存查看和流转抽屉。
- `/pallet-task/overview`：任务总览入口。
- `/pallet-task/semi/in`：半成品入库任务。
- `/pallet-task/semi/out`：半成品出库任务。
- `/pallet-task/finish/in`：成品入库任务。
- `/pallet-task/finish/out`：成品出库任务。
- `/pallet-task/transfer`：调拨任务。
- `/warehouse-map`：仓库平面图，独立展示仓区空间关系、库位状态、查询命中和右侧库位详情。
- `/warehouse`：库位台账页，展示库位概况统计、精确筛选、后端排序、库位编辑、平面图定位和最近 10 条库位流转操作。
- `/productStock`：库存总览中心，按产品汇总、托盘库存、备料池库存三个维度查看库存并联动托盘、库位、化验和流转。

托盘码相关请求统一收口在 `src/api/palletCode.js`，状态展示字典统一维护在 `src/utils/palletCodeDict.js`。

当前任务页已调整为批量操作口径：

- 入库、出库、调拨、取消任务通过表格勾选后在顶部批量按钮执行。
- 成品入库的“绑定半成品明细”仍保留为单条操作。
- 入库确认和半成品明细绑定统一执行“一板一码”数量规则：单位为板时数量固定为 1，单位为件时按对应产品的 `piecesPerPallet` 校验。
- 创建入库任务阶段不再向用户展示数量/单位字段；前端暂按 `quantity=1`、`unit=0` 兼容现有后端接口。
- 半成品出库任务页按 tab 收敛按钮：普通出库只提供创建出库、确认、取消；转入备料池只提供创建、确认、取消，不再提供手工确认消耗入口。
- 托盘绑定入库任务使用产品三级级联选择（成品/半成品 -> 产品类型 -> 具体产品），并自动联动 `productStatus`。
- 本模块时间戳展示统一格式化为 `YYYY-MM-DD HH:mm:ss`。

## 轻量页签管理

Web 管理端主布局已增加轻量页签栏：

- 页签状态维护在 `src/stores/tabs.js`，按页面路由 `path` 去重。
- 首页页签固定保留，不可关闭。
- 最多同时打开 10 个页签，超过后提示并阻止新增。
- 支持关闭当前、关闭其他、关闭全部和刷新当前页。
- 通过 `keep-alive` 按路由 `name` 缓存页面状态，缓存白名单集中维护在 `KEEP_ALIVE_TAB_NAMES`。
- 页签图标与左侧菜单共用 `src/utils/navigation.js` 的菜单配置和图标映射；页签样式采用“当前页签上浮、非当前页签弱化”的轻量层级，并通过 transform + opacity 实现关闭过渡。

## UI 风格规范

Web 管理端轻量 UI token 统一维护在 `src/assets/main.scss`：

- 以蓝色为主色，成功、警告、危险色只作为辅助语义色使用。
- 通用层级样式包括 `page-header`、`search-card`、`table-card`、`action-card`，用于统一页面标题区、查询区、操作区和数据区。
- Element Plus 的按钮、表格、弹窗、抽屉、页签、输入控件在全局样式中统一了间距、圆角、hover、表头和弹窗底部按钮区。
- 任务页、托盘码管理页、页签栏和主菜单复用同一套 token，避免在页面内散写高饱和色和默认表格 hover 色。
- 列表页查询区统一使用 `search-card` 查询面板，表格区统一使用 `table-card` 数据面板；列表级业务动作统一收敛到 `table-toolbar`，分页区统一通过 `pagination-wrapper` / `table-footer` 靠右展示。
- 状态标签统一采用浅底色体系，常规行内操作按钮在表格中降为轻按钮样式，减少列表页的高饱和按钮堆积。
- 表格单元格默认单行省略展示，表格卡片提供最小宽度与横向容错，避免日期、产品、仓库、状态等短字段被突兀拆行。
- 自动入库页将基础参数和报数文本拆成上下两个区域；化验管理的历史版本展开区改为信息块卡片；首页改为工作台结构，包含欢迎概览、核心指标、快捷入口、库位占用概览和库位明细。
- 首页继续保留工作台结构，并通过快捷入口跳转到独立的仓库平面图页面；平面图页面复用仓库容量、筛选和明细接口，以二维示意图表达仓区空间关系。
- 仓库平面图库位明细中，点击半成品单板格子会额外显示“转入备料池”，调用平面图任务接口创建 `PREPARE_CONSUMED` 待处理任务，并在结果弹窗提供“去处理”入口。
- 产品库存页升级为库存总览中心：产品汇总使用库存接口聚合，托盘库存使用托盘码分页并补充当前位置，且启用 `inventoryOnly` 只展示存在当前库存记录的托盘；备料池库存使用转入备料池任务分页；托盘、仓库平面图、化验、流转入口在表格操作列内联动。
- `/stock` 现在重定向到 `/stock/semi`，不再提供全部单据混合页面；单据中心只保留 `/stock/semi` 半成品单据和 `/stock/finish` 成品单据两个二级入口。
- 半成品单据按入库单、出库单、转入备料单、调拨单查看；成品单据按入库单、出库单、调拨单查看。两类页面复用同一个台账组件，通过 `productStatus` 和 `bizScene` 过滤新版托盘任务单据；退货入库入口已从 Web 操作区和台账页签移除，仅保留历史代码与后端能力归档。

## Project setup

```
yarn install
```

### Compiles and hot-reloads for development

```
yarn serve
```

### Compiles and minifies for production

```
yarn build
```

### Lints and fixes files

```
yarn lint
```

### Customize configuration

See [Configuration Reference](https://cli.vuejs.org/config/).
