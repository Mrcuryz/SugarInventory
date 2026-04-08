# 二维码托盘体系更新梳理 & 前端页面设计（Vue + 微信小程序）

## 1. 本次后端更新梳理（仅后端）

### 1.1 核心目标
本次升级将“托盘”从普通业务字段提升为核心追溯实体，围绕**二维码、任务、入库、流转**建立完整链路：

1. 先生成托盘码（可批量）。
2. 扫码绑定产品信息，生成待入库任务。
3. 对成品任务可绑定半成品来源明细。
4. 确认入库后，回写托盘状态与化验关联。
5. 全流程写入托盘流转记录（可用于追溯）。

### 1.2 新增/增强的数据结构

#### 托盘主表 `pallet_code`
用于维护托盘生命周期与关联信息：
- `code`：托盘码（二维码载荷）
- `status`：`FREE / PENDING / INSTOCK / CONSUMED / INVALID`
- `product_id / product_status / production_date / screen_mesh_id / assay_id`

#### 托盘任务表 `pallet_task`
用于承载入库前置动作：
- `task_type`：`SEMI_IN / FINISH_IN`
- `status`：`PENDING / CONFIRMED / CANCELED`
- 记录绑定产品、筛网、生产日期、确认人、确认时间等

#### 成品来源明细表 `pallet_task_semi_item`
用于成品入库前记录其半成品来源：
- 关联 `pallet_task_id`
- 存储半成品托盘码、数量、单位、是否套用化验

#### 流转表 `pallet_flow_record`
用于追溯关键动作：
- 典型操作：`SEMI_BIND / FINISH_BIND / SEMI_INSTOCK / FINISH_INSTOCK / CANCELED`
- 记录操作人、时间、来源/去向库位、备注等

### 1.3 关键业务规则

1. **托盘绑定前必须为 `FREE`**，避免重复占用。
2. **同一托盘不能有未完成入库任务**。
3. 成品任务绑定半成品时：
   - 仅允许“半成品托盘”；
   - 仅允许“在库/已消耗可追溯状态”；
   - `useAssay=true` 最多一条。
4. 入库确认按任务类型自动分支：
   - `SEMI_IN` 走半成品入库；
   - `FINISH_IN` 走成品入库 + 扣减来源半成品。
5. 确认入库后统一回写：
   - 托盘 `status -> INSTOCK`；
   - 任务 `status -> CONFIRMED`；
   - 流转记录新增入库事件。

### 1.4 主要接口（与前端直接相关）

#### 托盘码管理
- `POST /api/pallet-codes/generate` 批量生成托盘码
- `POST /api/pallet-codes` 托盘码分页查询
- `GET /api/pallet-codes/{code}/qrcode` 获取二维码 PNG
- `POST /api/pallet-codes/invalid` 批量作废托盘码

#### 托盘任务与入库
- `POST /api/pallet-codes/tasks/list` 托盘任务分页查询
- `POST /api/pallet-codes/bind` 扫码绑定并创建任务
- `POST /api/pallet-codes/tasks/semi-bind` 成品任务绑定半成品来源
- `POST /api/pallet-codes/tasks/confirm` 批量确认托盘入库
- `POST /api/pallet-codes/tasks/cancel` 批量取消任务

#### 扫码解析与追溯查询
- `GET /api/pallet-codes/parse?code=...` 扫码解析托盘信息
- `GET /api/pallet-codes/{code}/assay` 查询托盘化验
- `GET /api/pallet-codes/{code}/inventory` 查询托盘库存位置

---

## 2. Vue 管理端页面设计

> 目标定位：运营/仓管/质检在 PC 端做“生成、管理、任务处理、追溯”。

### 2.1 信息架构（菜单）

1. **托盘码中心**
   - 托盘码管理
   - 托盘任务中心
2. **扫码与追溯**
   - 扫码工作台
   - 托盘追溯详情
3. **入库协同**
   - 成品任务半成品绑定
   - 批量入库确认

### 2.2 页面 1：托盘码管理（列表页）

**功能**
- 条件筛选：code、产品名、产品类型、产品状态、生产日期区间、状态。
- 分页查看。
- 批量勾选：作废托盘码、导出。
- 行操作：查看二维码、复制托盘码、查看关联任务。

**关键组件**
- `FilterForm + DataTable + BatchActionBar + QrPreviewDialog`。

**接口映射**
- 查询：`POST /api/pallet-codes`
- 作废：`POST /api/pallet-codes/invalid`
- 二维码预览：`GET /api/pallet-codes/{code}/qrcode`

### 2.3 页面 2：托盘任务中心（列表 + 抽屉）

**功能**
- 筛选：任务类型、任务状态、产品维度、生产日期区间。
- 支持 `PENDING` 任务的一键处理。
- 行内操作：
  - 绑定半成品明细（仅 `FINISH_IN + PENDING`）
  - 入库确认
  - 取消任务

**关键组件**
- `TaskStatusTag`（PENDING/CONFIRMED/CANCELED）
- `TaskTypeTag`（SEMI_IN/FINISH_IN）
- `SemiItemsEditorDrawer`
- `ConfirmInDialog`

**接口映射**
- 查询：`POST /api/pallet-codes/tasks/list`
- 绑定半成品：`POST /api/pallet-codes/tasks/semi-bind`
- 批量确认：`POST /api/pallet-codes/tasks/confirm`
- 取消：`POST /api/pallet-codes/tasks/cancel`

### 2.4 页面 3：扫码工作台（PC 扫码枪/手动）

**功能**
- 输入/扫码托盘码后自动解析。
- 显示当前托盘状态与已绑定信息。
- 若 `FREE`：展示绑定表单（产品、状态、日期、数量、单位、备注）并创建任务。
- 若 `PENDING`：跳转任务处理。
- 若 `INSTOCK/CONSUMED/INVALID`：提示只读并可进入追溯。

**接口映射**
- 解析：`GET /api/pallet-codes/parse`
- 绑定建任务：`POST /api/pallet-codes/bind`

### 2.5 页面 4：托盘追溯详情

**功能**
- 顶部卡片：托盘基础信息（状态、产品、日期、筛网、创建更新信息）。
- Tab1：化验信息（assay）。
- Tab2：库存位置信息（inventory）。
- Tab3：流转时间线（当前后端未直接提供查询接口时可先显示“关键节点摘要”，后续补流转列表接口）。

**接口映射**
- 基础信息：`GET /api/pallet-codes/parse`
- 化验：`GET /api/pallet-codes/{code}/assay`
- 库存：`GET /api/pallet-codes/{code}/inventory`

### 2.6 页面 5：批量入库确认

**功能**
- 从任务中心勾选多个 `PENDING` 任务进入本页。
- 每行编辑：仓库名、侧别、数量、单位、备注、入库日期。
- 一次提交后展示逐条结果（成功/失败原因）。

**接口映射**
- 提交：`POST /api/pallet-codes/tasks/confirm`

---

## 3. 微信小程序页面设计

> 目标定位：现场扫码、快速绑定、快速确认、快速追溯。

### 3.1 导航建议（Tab + 子页面）

- Tab1：**扫码**（默认首页）
- Tab2：**任务**
- Tab3：**追溯**
- Tab4：**我的**

### 3.2 页面 A：扫码首页

**流程**
1. 点击“扫一扫”读取二维码。
2. 调 `parse` 获取托盘状态。
3. 按状态分流：
   - `FREE`：进入“绑定创建任务页”；
   - `PENDING`：进入“待处理任务详情页”；
   - `INSTOCK/CONSUMED/INVALID`：进入“追溯详情页”。

**交互重点**
- 扫码失败要支持手动输入托盘码。
- 对格式非法、校验失败给出明确提示（区分“码错”与“系统无此码”）。

### 3.3 页面 B：绑定创建任务

**表单字段**
- 托盘码（只读）
- 产品（下拉/搜索）
- 产品状态（半成品/成品）
- 生产日期
- 数量、单位（板/件）
- 备注

**提交后**
- 成功：toast + 跳转任务详情。
- 失败：显示后端规则提示（如“非 FREE 不可绑定”）。

### 3.4 页面 C：任务处理详情

**展示**
- 任务类型、状态、产品信息、生产日期、创建时间。

**操作区**
- 若 `FINISH_IN + PENDING`：进入“绑定半成品明细”。
- 入库确认：填写仓库、侧别、数量、单位、备注后提交。
- 取消任务按钮（管理员/仓管权限可见）。

### 3.5 页面 D：绑定半成品明细（成品任务）

**明细行字段**
- 半成品托盘码（扫码录入）
- 数量
- 单位
- `useAssay` 开关

**规则提示（前端提前校验）**
- 至少 1 条。
- `useAssay=true` 最多 1 条。
- 提交前去重相同半成品托盘码。

### 3.6 页面 E：追溯详情

**模块**
1. 托盘基础信息卡片。
2. 化验数据卡片（如果存在）。
3. 在库位置卡片（如果在库）。
4. “来源半成品”摘要（来自任务明细返回字段，可在任务接口数据中承接）。

---

## 4. 前端通用设计建议（Vue 与小程序共用）

### 4.1 状态字典统一
- 托盘状态：`FREE / PENDING / INSTOCK / CONSUMED / INVALID`
- 任务类型：`SEMI_IN / FINISH_IN`
- 任务状态：`PENDING / CONFIRMED / CANCELED`
- 单位：`0=板 / 1=件`

建议前端维护统一 `enumMap`，避免散落在各页面。

### 4.2 接口封装分层
- `api/palletCode.ts`：托盘码、解析、二维码、作废
- `api/palletTask.ts`：任务查询、绑定半成品、确认入库、取消
- `api/palletTrace.ts`：化验、库存、（后续流转列表）

### 4.3 表单校验策略
- 前置校验：必填、数值范围、日期合理性。
- 规则镜像：将“useAssay最多一条”等后端规则前置提示，减少提交失败。
- 保留后端兜底：所有业务错误统一 toast + 错误详情弹窗。

### 4.4 角色与权限
建议至少划分：
- 仓管：扫码绑定、确认入库、任务查询
- 质检：查看化验、追溯
- 管理员：批量作废、取消任务、全量查询

### 4.5 异常与离线策略（小程序）
- 扫码后先本地暂存最近 20 条历史（便于断网后回看）。
- 网络失败时保留草稿（绑定表单、入库确认表单）。

---

## 5. 交付优先级建议

### P0（先上线）
1. 扫码解析 + 绑定创建任务
2. 任务列表 + 入库确认
3. 托盘码管理列表 + 二维码预览
4. 追溯详情（基础信息 + 化验 + 库位）

### P1（增强）
1. 成品任务半成品绑定的批量扫码体验优化
2. 批量确认入库页
3. 流转时间线完整展示（需后端补“流转记录查询接口”）

### P2（优化）
1. 二维码打印模板（A4/热敏）
2. 异常任务看板（超时 PENDING、化验缺失）
3. 追溯报表导出

---

## 6. 建议补充的后端接口（便于前端完整闭环）

当前已可支撑主流程；若要把“追溯”做完整，建议新增：

1. `GET /api/pallet-codes/{code}/flows`：按托盘码查询流转记录时间线
2. `GET /api/pallet-codes/{code}/tasks`：查询托盘历史任务（含已取消/已确认）
3. `POST /api/pallet-codes/qrcode/batch-export`：批量二维码导出（zip/pdf）

