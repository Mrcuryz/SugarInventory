# 项目全量复审与发布就绪性结论

日期：2026-08-12（Asia/Shanghai）

范围：历史设计/实施/验收文档、当前 Java/Python/MCP/Web 代码、迁移与 shadow 数据库

总判定：`LOCAL_ENGINEERING_BASELINE_PASS / SHADOW_BROWSER_UAT_PASS / PRODUCTION_RELEASE_NO_GO`

## 1. 审查方法

本轮按文档日期和证据等级重新审查，不把历史结论直接当成当前事实。发生冲突时依次以当前 Controller/Service/Mapper、迁移与真实数据库、Python Runtime/Java Gateway/MCP 合同、机器可读登记、OpenAPI 和历史文档为准。

审查覆盖传统 WMS 的登录/RBAC、产品、仓库/库位、库存、托盘二维码、任务、出入库、调拨、生产订单/煮糖批次/领料/产出、化验/标准、操作审计与打印助手；同时覆盖 AI 助手会话、专家边界、54 个 Java/MCP 工具、58 个 GoalContract、七类登记报表、RAG 和首个 L3 候选。

## 2. 本轮按顺序完成的整改

- Controller/DTO/VO、统一异常、JWT/RBAC、操作日志、OpenAPI 与数据库迁移门禁已收口；传统业务写路径使用明确的最小操作权限。
- 打印助手、旧库存写路径、托盘任务字段、自动入库幂等/并发/失败恢复、数据库 schema 启动校验均补齐代码、测试和文档。
- 成品入库引导、S4 shadow 故障注入、断线重试、状态失效、权限撤销和默认关闭回滚完成；MCP execute 仍未注册。
- 七类登记报表补齐权限技术矩阵、运行/导出/清理观测和异常分类；报表运行/归档/审计编排已下沉到业务服务，Controller 不再本地捕获异常。
- 库存历史 Windows 调度脚本支持 PowerShell 5.1、无写入预检和真实退出码；shadow 仍是 0/7，未伪造历史。
- Agent 性能链路增加 Goal 工具裁剪、完成态空工具 schema、短结果提示和只含字节数的诊断；80/80 功能成功，但性能门禁明确失败。
- RAG 打包/启动门禁和可选降级完成；原始材料按项目约定由仓库外部交付，当前尚未取得正式 artifact/model，required 模式 fail-closed。
- 最终回归发现并修复知识分类把“原料消耗及产出”误判为流程知识的问题；实时生产语义不会被知识纠偏抢占。
- 最终浏览器 fixture 修复 Windows PowerShell/MySQL 中文命令行编码和 `user.name` 长度问题，改用 UTF-8 SQL 文件及短 ASCII 一次性账号并增加合同测试。

## 3. 最终自动化基线

| 组件 | 结果 |
| --- | --- |
| Python Agent | 485 passed，8 skipped |
| Java 主项目 | 438 passed，0 failed/skipped |
| warehouse-mcp | 72 passed，0 failed/skipped |
| Web Node tests | 91 passed，0 failed/skipped |
| Web production build | 2161 modules transformed，build success |
| Java/MCP package | 两个可执行 JAR 构建成功 |
| 打包 Python Runtime | `knowledge.py`、`runtime.py`、RAG CLI 与源码 SHA-256 分别一致 |
| 代码格式 | `git diff --check` 通过 |

8 个 Python skip 全部来自尚未取得仓库外部交付的真实 RAG 原文/release/model，已单独作为外部交付与发布阻断记录，不算成功覆盖。

前端首次与两个 Maven 打包并行构建时，esbuild 在渲染阶段因本机内存竞争退出；无并发单独重跑后 9.66 秒成功，故以单进程结果作为正式构建证据。

## 4. 最终真实 Chromium 验收

使用指定 shadow env、新构建的 Java/Python/Vite 和可精确清理的临时 ADMIN：

1. 登录、用户信息和首页 WMS 实时数据均为 HTTP 200；首页显示 87 个库位。
2. “今天整体运营情况如何？”经过 6 个受控阶段，调用 `run_registered_report=SUCCESS`，返回五区块今日运营概览；事实为 33 个产品、55 个库位、55,522 件、2,220,880 kg、1 条待处理任务。
3. “生产订单 PO20260801 的实际原料消耗是多少？”经过生产专家并调用 `resolve_production_entities=SUCCESS`，因订单不存在明确返回无匹配；没有进入 RAG、没有伪造消耗。
4. 两次 SSE、消息审核和所有业务 API 均为 HTTP 200；正常验收阶段浏览器 console error 为 0。

页面关闭和服务停止后出现的连接终止错误不计入业务链路；服务关闭前已单独读取全部 console error，结果为 0。

## 5. 副作用与清理

最终 canary 只产生一次临时报表快照及 Agent 会话/审计，没有库存、库位、托盘、任务、生产或化验写入。验收后按用户 ID、会话 ID、reportRunId 和 manifest 精确删除：

- 临时用户、花名册和角色；
- Agent session、tool/api audit、interrupt、message review/evidence；
- report run、execution run、export audit；
- 所有 L2/L3 预览/确认/请求/审计（本次实际为零）。

最终 9 组聚合计数为 `0|0|0|0|0|0|0|0|0`，Java、Python、Vite 和 Chromium 均已停止。指定 env 未修改、未回显，临时口令和服务密钥未写入仓库。

## 6. 当前发布阻断

### P0：必须先处理

1. RAG 原始材料不属于本地仓库内容，当前尚未取得外部交付的正式 artifact/model；收到可信 release 与匹配模型并完成校验/切换/回滚前，不得发布知识能力。
2. 四领域性能门禁失败：整体 P50/P95 19.953/46.562 秒，四领域 P95 均超过 15 秒目标。

### P1：生产数据与组织条件

1. 库存趋势只有启用基线，连续可信 `DAILY_CLOSE` 与守恒对账为 0/7；目标机计划任务尚待管理员安装。
2. 报表仓管/质检/生产主管最小权限角色与真人验收未完成；现有技术账号不能代替正式职责设计。
3. 报表与库存历史运行状态尚需接入目标告警平台，并完成正式部署/回滚演练。

### L3 独立审批

S4 隔离技术验收已通过，但 `agent:finish-inbound:execute` 正式角色仍为零，S2/S3 默认关闭，MCP execute 未注册。只有业务、安全和运维责任人完成独立生产评审后，才可讨论打开首个成品入库候选；不得自动推广到出库、调拨或半成品。

## 7. 推荐下一步

1. 本轮源码、迁移和文档已与本地浏览器快照分离，并以本文件所在提交建立可复现 Git 基线。
2. 接收并核验仓库外部交付的 RAG 原始材料/正式资产，同时处理候选模型/供应端长尾 A/B、库存历史计划任务安装和自然积累。
3. 设计正式报表角色并组织三类真人按真实问题验收，同时接入目标告警。
4. 最后单独召开 L3 生产发布评审；未批准前保持默认关闭和 MCP execute 未注册。

因此本轮可以宣布本地工程基线和 shadow 浏览器验收通过，不能宣布整项目生产发布通过。
