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


## AI 助手入口

Web 管理端主框架右上角提供“AI 助手”按钮。用户必须先登录仓储系统；前端不接收、不保存、不展示 `delegationToken`、`WAREHOUSE_DELEGATED_TOKEN` 或 Authorization Header。

最小交互流程：

- 点击“AI 助手”后，前端调用 `POST /api/agent/sessions` 创建当前用户的 Agent 会话。
- 前端只展示会话状态、过期时间、自然语言对话内容和业务候选卡片，不展示 MCP 工具名或调用摘要。
- 用户输入自然语言后，前端调用 `POST /api/agent/sessions/{agentSessionId}/messages`。
- 后端 `AgentGatewayService` 负责模型规划、会话上下文、MCP 工具调用、消歧和审计。
- 同一 Agent 会话内支持“这些”“刚才那个”“它”“这个库位”等跟进指代；后端会结合最近唯一产品/库位上下文继续规划。
- 当产品或库位解析返回 `AMBIGUOUS` 时，前端展示业务候选卡片，Agent 不得自行猜测 ID。点击候选卡片时，前端只把选择项作为同一对话窗口的上下文发送，不把展示文本追加成新的用户消息。
- 点击关闭时，前端调用 `DELETE /api/agent/sessions/{agentSessionId}` 撤销会话，后端关闭对应 STDIO MCP 进程。

当前支持的只读场景：

- 查黄冰糖（袋）库存。
- 查 2 号库位状态。
- 查托盘状态。
- 查某产品某日期化验状态。

当前 STDIO 一用户一 MCP 进程只是过渡方案；生产多用户 Agent 推荐迁移到 HTTP/Streamable HTTP MCP，通过请求级上下文注入用户委托身份。

### AI 助手最终产品目标

AI 助手前端体验以 `docs/agent/ai-assistant-product-goal.md` 为准。普通用户看到的是自然语言回答、候选按钮、结果卡片、追问建议、加载中的业务状态和人话错误解释，而不是 MCP 工具调用过程。

P1 视觉产品化要求：

- 助手抽屉采用独立头部、会话状态条、对话工作区和固定输入区四段结构，避免看起来像普通表单抽屉。
- 用户消息、助手消息、等待选择、错误、取消和业务结果卡片应有清晰视觉语义。
- 候选项以业务卡片呈现，只展示 `displayLabel`、业务类型、描述和支持状态，不展示内部实体 ID。
- 库存分布等结构化结果应以结果卡片承载，重点字段分组展示，风险或异常提示使用独立提示样式。
- 管理员调试摘要只能在调试模式下显示，普通用户界面仍不得展示工具调用细节。

P2 展示层组件化要求：

- `AgentAssistant.vue` 只负责会话创建、消息发送、流式事件归并、取消和候选项提交，不直接承载候选卡、结果卡和调试摘要的细节模板。
- 会话状态、消息气泡、业务结果卡和候选项卡片拆分到 `src/components/agent/`，复用同一套展示 helper，避免同一业务结果在不同位置出现不同视觉规则。
- 展示组件只接收安全白名单字段，例如 `displayLabel`、`description`、`card.fields` 和脱敏调试摘要；普通展示路径不得新增 `productId`、`warehouseId`、raw JSON、token 或 Authorization Header。
- 后续新增预览卡、确认卡、报表卡时优先扩展展示组件，不在主抽屉模板中继续堆叠业务卡片 markup。

P3 对话身份呈现要求：

- 每条消息都应展示轻量身份信息。助手侧名称固定为“智能仓储助手”，名称后以标签显示后端会话返回的 `modelDisplayName`，该字段来自 `agent.model.name` / `openai.model` 等运行配置，便于后续扩展多模型路由。
- 如果后端未返回模型名称，前端显示“模型未配置”，并应视为后端配置或会话接口缺失问题处理。
- 用户侧名称直接取当前登录态的用户姓名或员工标识，不允许让前端用户手动输入或伪造身份。
- 会话状态栏需要同时展示助手身份、运行模型和当前登录用户，消息列表中保留轻量作者标识，避免用户只在单条消息内才能确认当前对话身份。
- 头像、名称和模型标签应延续当前 Web 管理端的浅色、低噪声、业务工具风格，不使用营销式大头像或高饱和装饰。

普通用户界面不得展示：

- `productId`、`warehouseId`；
- `toolName`、`SUCCESS`；
- raw JSON；
- token、Authorization Header、密码；
- 后端异常堆栈、数据库连接信息、内部服务器路径。

管理员调试模式可以查看脱敏后的工具调用摘要、耗时和错误码。Agent Gateway 负责会话记忆、工具编排、权限上下文、流式事件和审计；MCP Server 负责安全工具能力，不直接和用户对话。
### Agent 模型规划

AI 助手消息接口默认使用 `agent.model.mode=llm`，通过 OpenAI 兼容接口把用户自然语言规划为受控 JSON：库存、库位、托盘、化验或不支持意图。模型只负责意图和实体抽取，不直接返回产品 ID、库位 ID，也不调用任意接口。

可配置项：

```yaml
agent:
  model:
    mode: llm # llm 或 rule
    name: ${openai.model}
    max-tokens: 800
```

当模型接口不可用时，后端会回退到规则解析，保证只读查询入口仍可用，但自然语言效果会下降。M1.2 的会话上下文为后端内存态，关闭或撤销 Agent 会话会清理上下文，后端进程重启也会丢失上下文。

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
