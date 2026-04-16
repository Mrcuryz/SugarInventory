# 小程序升级第一阶段交付说明

## 本轮定位

本轮已从“第一阶段骨架搭建”推进到“第一阶段收口优化”。目标不是继续扩页面，而是把已经能跑的主流程收成可试用版本。

## 已完成范围

### 1. 一级结构重建完成

1. 新 tabBar 调整为：工作台 / 任务 / 扫码 / 查询 / 我的
2. 登录、绑定、工作台、扫码、任务、托盘详情、查询、我的、帮助中心均已切到新结构
3. 旧页面仍保留在 `app.json` 中，便于后续分阶段下线

### 2. 扫码主流程已收口

1. 扫码页只保留现场作业模式，不再混入查询模式
2. 已支持四种作业模式：入库 / 出库 / 转入备料池 / 调拨
3. 连续扫码会进入“本次任务池”，支持批量确认、批量取消、单条移除
4. 新增本次作业上下文统计：已扫描 / 成功 / 重复 / 失败
5. 扫码成功、重复、失败均有现场化反馈
6. 任务池支持自动聚焦到最新一条任务

### 3. 规则已统一

1. 入库绑定时，产品状态与产品不再默认选中第一项
2. 只有明确存在唯一可选值时，才允许自动带出
3. 数量规则统一为：
   - 单位为“板”时，数量固定为 `1`
   - 单位为“件”时，只允许正整数
   - 件数不能超过产品的每板件数
4. 上述规则已接入扫码页与任务中心的相关确认表单

### 4. 查询 / 我的 / 帮助中心已从占位页升级

1. 查询中心已支持：
   - 手动输入托盘码
   - 扫码查询
   - 查询前校验托盘码是否有效
   - 区分未查询 / 查询中 / 无结果 / 查询失败状态
2. 我的页已改为正式三段式结构：
   - 账号信息
   - 常用入口
   - 系统操作
3. 旧“使用手册”已改为帮助中心样式，增加：
   - 现场提示
   - 目录
   - 折叠章节
   - 常见问题

### 5. 设计与组件层已统一

1. 新增并复用统一页面壳、标题、状态标签、空状态、任务卡片、底部操作栏等公共组件
2. 新 tabBar 图标与首页快捷入口图标已切到新图标体系
3. 页面圆角、阴影、按钮语义、状态色基本统一

### 6. 托盘详情页已进入重构版

1. 托盘详情页已改为“顶部摘要卡 + 中部 Tab + 底部固定操作栏”
2. 首屏摘要卡已合并托盘码、状态、产品、位置、当前任务与建议操作
3. 中部 Tab 已拆为：
   - 基础信息
   - 化验数据
   - 流转记录
4. 基础信息页不再按接口字段纵向堆叠，而是改为：
   - 基础档案卡
   - 当前位置卡
   - 当前任务卡
5. 化验页已升级为正式指标展示区，不再只是几行摘要
6. 流转页已升级为时间线样式
7. 存在待处理任务时，可直接在托盘详情页完成：
   - 处理任务
   - 取消任务
   - 待入库任务的直接确认入库

### 7. 工作台 / 任务中心 / 扫码页已完成一轮 UI 收口

1. 工作台弱化了模板化蓝色大卡，强化了“现场值班台”感
2. 快捷作业入口已拉开主次，扫码相关入口更突出
3. 任务中心筛选区改为更接近 segmented control 的结构
4. 任务中心补了列表说明区，单条处理与批量处理的关系更清楚
5. 扫码页补强了模式区与扫码主面板，主按钮更像作业主入口
6. 任务卡片已统一优化，成功 / 失败任务在视觉上更容易区分

## 关键目录

```text
api/
  auth.js
  pallet.js
  product.js
  task.js

components/
  action-sheet/
  bottom-action-bar/
  empty-state/
  loading-view/
  page-header/
  page-shell/
  pallet-summary-card/
  status-tag/
  task-card/

pages/
  auth/login/
  auth/bind-phone/
  auth/bind-manual/
  workbench/index/
  tasks/index/
  tasks/detail/
  scan/index/
  pallet/detail/
  query/index/
  profile/index/
  help/index/
```

## 已接入接口

### 认证

- `POST /api/auth/wechat-login`
- `POST /api/auth/phone-bind`
- `POST /api/auth/manual-bind`
- `GET /api/user/info`

### 托盘

- `GET /api/pallet-codes/parse`
- `GET /api/pallet-codes/{code}/inventory`
- `GET /api/pallet-codes/{code}/assay`
- `GET /api/pallet-codes/{code}/flows/cycles`
- `GET /api/pallet-codes/{code}/flows`

### 任务

- `POST /api/pallet-codes/bind`
- `POST /api/pallet-codes/tasks/list`
- `POST /api/pallet-codes/tasks/confirm`
- `POST /api/pallet-codes/tasks/cancel`
- `POST /api/pallet-codes/semi/out/create`
- `POST /api/pallet-codes/semi/out/confirm`
- `POST /api/pallet-codes/semi/prepare/create`
- `POST /api/pallet-codes/semi/prepare/confirm`
- `POST /api/pallet-codes/finish/out/create`
- `POST /api/pallet-codes/finish/out/confirm`
- `POST /api/pallet-codes/transfer/create`
- `POST /api/pallet-codes/transfer/confirm`

### 产品

- `GET /api/products/product?status=半成品|成品`
- `GET /api/products/semi-products`
- `GET /api/products/finished-products`

## 复用的旧逻辑

1. 微信登录与绑定流程
2. `utils/request.js` 中的 Bearer Token 请求模式
3. 用户信息获取逻辑
4. 托盘解析、任务创建与确认接口口径
5. 枚举、状态映射与 Web 端一致的业务语义

## 本轮建议手工验证

1. 打开小程序默认进入新登录页，已有 token 时自动进入工作台
2. 微信登录、手机号绑定、工号绑定均可走通
3. 工作台可进入扫码 / 查询 / 任务页，且最近扫码与最近任务可查看
4. 扫码页仅包含入库 / 出库 / 转入备料池 / 调拨四种模式
5. 入库扫码后，产品状态与产品默认不预选第一项
6. 单位为“板”时数量固定为 1，单位为“件”时数量只能输入正整数
7. 出库、转入备料池任务创建失败后，可在本次任务池保留并重试
8. 调拨创建失败后，可在本次任务池重试，不需要重新扫码
9. 本次任务池可批量确认、批量取消、清空本地池，且都有二次确认
10. 查询中心支持手输和扫码两种入口，异常码和不存在托盘有区分反馈
11. 托盘详情页第一屏能直接看到状态、产品、位置和当前可执行操作
12. 我的页、帮助中心已不再是空白占位页
13. 托盘详情页首屏能直接判断“这板是谁、在哪、现在能做什么”
14. 托盘详情页中的待处理任务可直接处理，不必先跳转任务中心
15. 工作台、任务中心、扫码页的视觉母体比前一轮更统一

## 仍明确留到第二阶段

1. 查询中心的库存查询、化验查询、作业记录完整落地
2. 托盘详情页的更多扩展信息与更完整动作闭环
3. 弱网下更完整的离线队列与冲突处理
4. 产品 / 库位 / 筛网等低频管理页重构
5. 更完整的视觉规范与组件规则沉淀
6. 查询中心、工作台、任务中心、扫码页的整页 UI 精修仍可继续提升
7. 图标体系仍需继续扩展到更多低频旧页面
