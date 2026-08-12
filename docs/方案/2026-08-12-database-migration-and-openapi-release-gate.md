# 数据库迁移与 OpenAPI 发布门禁整改

日期：2026-08-12

## 1. 问题复核

代码仓库原有 40 个正式增量迁移，但此前没有统一迁移执行器、checksum 台账、并发锁或应用启动门禁。各验收脚本按需执行单个 SQL，导致 shadow 库出现“部分新表存在、部分运行表缺失”的漂移状态。实际缺失包括报表运行、库存日对账、库存历史任务证据、任务预览和成品入库控制面等表；应用虽然启动，但 `ApplicationReadyEvent` 任务随后因缺表报错。

`docs/openapi.json` 只能人工导出，未与同一构建的真实 `/v3/api-docs` 比对；历史快照还使用运行端口生成的 server URL，容易产生无意义漂移。简化部署产物也没有携带迁移，`update.sh` 会直接更新应用。

## 2. 整改结果

### 2.1 迁移执行与台账

- `scripts/apply-database-migrations.ps1` 按文件名顺序执行 `migrations/*.sql`。
- `schema_migration` 保存迁移文件名、SHA-256、执行类型、时间和耗时；已登记文件内容变化时失败关闭。
- `schema_migration_lock` 提供带 owner token 和超时回收的数据库迁移锁，避免并发发布交错执行。
- 本地数据库默认允许；远程数据库必须同时显式提供 `-AllowRemoteDatabase` 和精确的 `-ExpectedDatabase`。
- `-VerifyOnly` 不修改数据库，检查迁移遗漏、仓库文件缺失和 checksum 漂移。
- 已有数据库只能通过明确的 `-BaselineThrough` 接入，基线操作在台账中标记为 `BASELINED`，与实际执行的 `APPLIED` 区分。

### 2.2 应用启动门禁

主应用包现在内置全部 SQL 迁移。生产设置 `DATABASE_SCHEMA_VERIFICATION_ENABLED=true` 后，`DatabaseSchemaReadinessVerifier` 在应用 ready 前核对：

- 运行包内全部迁移均有台账；
- checksum 与运行包完全一致；
- 基础业务表及迁移声明创建的表真实存在。

任一条件不满足即终止启动，并提示先执行受控迁移。开发默认关闭，避免未接入台账的历史个人库被无意改写；`deploy/simple` 强制开启。

### 2.3 简化部署闭环

- 构建脚本把完整迁移目录复制到 `artifacts/database/migrations`。
- Compose 增加 `operations` profile 的一次性 MySQL 迁移容器。
- `update.sh` 在 `docker compose up` 前先执行迁移；失败时停止发布。
- 后端容器补齐 Redis 服务地址、密码，并启用 schema 门禁。
- `.env.example` 增加显式基线和迁移锁超时配置，默认不自动基线。

### 2.4 OpenAPI 契约

- OpenAPI server URL 固定为部署相对地址 `/`，消除验收端口差异。
- `export-openapi.ps1` 输出递归排序、UTF-8 无 BOM 的稳定 JSON。
- `check-openapi-snapshot.ps1` 将仓库快照与同一运行实例的完整规范做规范化比较。
- 原 Agent Gateway 登记检查继续验证 60 个上游路径均存在。

## 3. shadow 库迁移记录

只读审计确认 shadow 库的真实结构已覆盖到 2026-07-24。随后显式执行：

- 29 个历史迁移登记为 `BASELINED`；
- 11 个后续迁移真实执行为 `APPLIED`；
- 首次整改后为 40/40，pending=0，checksum 全匹配，迁移锁已释放。

2026-08-12 随后的成品入库完整浏览器验收发现一个基线漏项：`docs/2026-04-28-pallet-task-semi-item-prepare-balance-migration.sql` 从未进入正式 `migrations/`，但运行代码已读取 `prepare_balance_id`、`board_count`、`piece_count` 和 `total_pieces`。因此账本虽然全绿，精确预览仍可能报缺列。现已新增幂等补偿迁移 `2026-08-12-backfill-pallet-task-semi-item-prepare-fields.sql`，同时把旧 `semi_pallet_code_id` 改为可空；shadow 库真实应用 1 条后为 41/41、pending=0。该迁移不修改已经登记的历史文件，可兼容曾手工执行历史 SQL 的环境。

这次变更是本机 shadow 数据库的工程验收，不代表生产数据库已经迁移。生产仍必须单独备份、核实基线、执行迁移并保存发布证据。

## 4. 验证

- 迁移执行器 `-VerifyOnly`：PASS，41/41。
- schema 门禁真实启动：PASS，41 个迁移、47 张必需表就绪。
- 原缺表堆栈不再出现；启动只报告真实的数据质量状态：2026-08-11 缺少可信日终快照。
- OpenAPI 快照与 live 应用：214 个路径完整一致。
- Agent OpenAPI/Registry：60 个上游路径全部存在。
- Swagger UI 真实浏览器加载成功，自动入库 4 个端点可见。
- 后续使用新构建 JAR 再次真实启动，schema 门禁仍为 41 个迁移、47 张必需表；Chromium 读取当前默认开关下的 live `/v3/api-docs` 为 207 个启用路径、相对 server `/`，L2 preview 存在且 execute 路径不存在，控制台 0 error/warning。该运行路径数与开启验收条件端点时的 214 路径不是同一开关组合，不据此改写前述完整快照比较结论。
- Web 登录页正常渲染，浏览器控制台 0 error。
- shell 脚本通过 Git for Windows `sh -n`；Compose 配置解析通过。
- 本次启动产生的库存历史失败证据和报表清理审计各 1 条已按精确 ID 删除；验收端口与临时 Redis 均已关闭。

## 5. 发布边界

- 不自动推断既有数据库的基线版本。
- 不在应用启动过程中执行 DDL；部署步骤先迁移，应用只做失败关闭校验。
- checksum 变化必须新建迁移，不允许修改已经登记的历史 SQL。
- 库存趋势发布门禁仍取决于目标环境连续 7 天可信快照与守恒对账；补表不等于数据已经可信。
