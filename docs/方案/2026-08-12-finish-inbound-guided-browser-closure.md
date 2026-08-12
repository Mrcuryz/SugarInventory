# 成品入库固定二维码引导浏览器闭环

日期：2026-08-12

结论：`LOCAL_BROWSER_UAT_PASS / L2_PREVIEW_ONLY / S4_NO_GO / PRODUCTION_NOT_RELEASED`

## 1. 范围

本轮验证用户只说“3号库位入库2板黄冰糖”时，系统能否通过产品消歧、二维码来源选择、实物二维码选择、显式任务创建、任务资格预览和精确表单预览完成一条可理解的业务旅程。验收不启用 S3，不授予 `agent:finish-inbound:execute`，不点击人工入库提交，也不注册任何 MCP 写工具。

## 2. 实施内容

- 新增 `scripts/prepare-finish-inbound-guided-browser-uat.ps1`，只允许本地数据库，创建并清理一次性账号、库位和两枚固定二维码；不修改角色权限；
- 新增 `FinishInboundGuidedBrowserUatContractTest`，锁定两枚二维码、两层预览、合法绑定追溯和零库存/零执行边界；
- 新增 `2026-08-12-backfill-pallet-task-semi-item-prepare-fields.sql`，补齐历史只存在于 docs 的运行时字段并进入 checksum 迁移账本；
- 更新数据库启动门禁测试，使正式迁移基线从 40 条增至 41 条；
- 修正 MCP 登记中的固定二维码激活路径，并明确 `FINISH_BIND` 是生命周期追溯，不是库存移动。

## 3. 真实浏览器证据

隔离拓扑为 `Chromium 5177 → Java 38082 → Python 38091 → STDIO MCP`，模型配置从指定 env 文件仅加载到进程内；服务密钥、JWT 和临时登录密码均使用本轮进程覆盖，未写回配置文件。

实际页面步骤：

1. 登录一次性 ADMIN 账号并打开 AI 助手；
2. 输入“3号库位入库2板黄冰糖”；
3. 选择“黄冰糖（袋） 25.0kg/件 40件/板”；
4. 选择“使用空闲固定二维码新建任务”；
5. 选择两枚一次性固定二维码，确认 1/2 时按钮禁用、2/2 时启用；
6. 在“创建固定二维码待入库任务”弹窗核对生产日期、二维码和目标库位，再在二次确认弹窗点击“创建任务”；
7. PDF 下载成功，原卡片改为“已创建待入库任务”并失去选择能力；
8. 自动任务资格预览返回 2/2 可继续；
9. 打开既有批量入库弹窗，确认两行均为 `3号库位 / 当天 / 左 / 1板`；
10. 点击“生成安全预览”，精确 L2 卡片逐项显示相同数据，并提示当前账号没有 AI 执行权限；
11. 不点击“直接提交（人工流程）”，结束并撤销 Agent 会话。

浏览器业务请求均返回 200。控制台没有应用运行错误；仅有 Vue i18n 既有弃用警告，以及登录页在尚无 token 时探测 `/api/user/info` 产生的预期 401。

## 4. 验收中发现并修复的问题

### 4.1 迁移账本全绿但运行字段缺失

第一次精确预览返回安全失败。Java 日志显示 MyBatis 查询 `pallet_task_semi_item.prepare_balance_id` 时数据库报 unknown column。根因是 2026-04-28 的结构 SQL只放在 `docs/`，未纳入正式 `migrations/`；此前基线因此无法发现漏项。

补偿迁移按 `information_schema` 逐列判断，支持已手工执行历史 SQL 的环境重复接入，并补齐：

- `prepare_balance_id`；
- `board_count`；
- `piece_count`；
- `total_pieces`；
- `semi_pallet_code_id` 可空边界。

shadow 库应用前为 40/40，应用后为 41/41。保持同一任务、同一会话和同一表单重试后，精确预览成功。

### 4.2 把绑定追溯误判为库存流转

首版反向验收要求 `pallet_flow_record=0`，但固定码激活按现有服务契约会为每枚二维码写一条 `FINISH_BIND`，用于记录“成品入库登记/固定产品二维码打印并启用”。代码、Controller 描述和小程序展示规则都将其定义为绑定关系，不是仓内位置迁移。

最终验收改为更严格且语义正确的断言：总共恰好 2 条 flow，并且两条都必须是与对应 `FINISH_IN` 任务关联的 `FINISH_BIND`；任何其他 flow 为 0，同时 `inventory` 和 `stock_movement_event` 必须为 0。

## 5. 数据库反向结果

| 事实 | 结果 |
| --- | ---: |
| `FINISH_IN + PENDING` 任务 | 2 |
| 合法 `FINISH_BIND` 追溯 | 2 |
| 非预期 flow | 0 |
| inventory 写入 | 0 |
| stock movement 写入 | 0 |
| 任务资格预览归档 | 1 |
| 精确执行预览归档 | 1 |
| execute 工具调用 | 0 |
| execution request | 0 |

验收结束后，一次性账号、库位、二维码、任务、追溯、Agent 会话/预览/审计和浏览器下载均已清理；三个隔离端口已关闭。正式补偿迁移及其账本记录保留。

## 6. 发布边界

这次通过只证明固定二维码引导到 L2 精确预览的本地浏览器闭环。S3 仍默认关闭，S4 的正式角色授权、审计故障注入、断线/重复点击/过期/撤销/跨用户矩阵和回滚演练仍未完成；不得据此注册 `execute_finish_inbound_task` MCP Tool 或声明生产发布。

## 7. 最终回归与发布包烟雾

- Java 根项目：114 个测试套件、427 项测试全部通过；
- `warehouse-mcp`：2 个测试套件、72 项测试全部通过；
- Python Agent：`478 passed, 8 skipped`；
- Web Node：91 项测试全部通过；
- Java 根项目、`warehouse-mcp` 和 Web 生产构建全部通过；
- 新构建 JAR 已确认携带 `2026-08-12-backfill-pallet-task-semi-item-prepare-fields.sql`；
- 使用新 JAR 和 shadow 库真实启动，schema 门禁报告 41 个迁移、47 张必需表就绪，MCP 运行产物校验通过；
- 真实 Chromium 打开 Swagger UI 成功，运行实例 `/v3/api-docs` 为相对 server `/`、207 个当前启用路径，存在 L2 preview 路径且不存在 execute 路径，控制台 0 error/warning。

发布包烟雾结束后，JAR 进程、浏览器、验收端口和临时日志均已清理；shadow 库只保留正式迁移及账本记录。
