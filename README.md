# 来宾糖厂仓储管理系统

基于 `Spring Boot + MySQL + Vue 3 + 微信小程序` 的仓储管理系统，覆盖原料/半成品/成品的入库、出库、库存、化验、库位、员工与操作审计等核心流程，并提供 Web 管理端与微信小程序双端协同。

## 项目概览

- 后端：`Java 21`、`Spring Boot 3`、`MyBatis-Plus`、`Spring Security + JWT`、`Redis`
- Web 管理端：`Vue 3` + `Vite` + `Element Plus`
- 微信小程序：`原生小程序`
- 数据库：`MySQL 8`
- 扩展能力：`OpenAPI/Knife4j` 接口文档、AOP 操作日志、AI 自动入库解析（OpenAI）

## 核心功能

- 认证与鉴权
- Web 登录、微信登录、手机号绑定、工号手动绑定
- JWT 无状态鉴权，非公开接口默认需要 `Authorization: Bearer <token>`

- 仓储业务
- 入库管理（半成品/成品）
- 出库与调拨
- 库存总览、库位容量与库存查询
- 库位维护与状态管理
- 托盘码生成、绑定、作废、二维码查询

- 质量与基础资料
- 产品管理（半成品/成品）
- 化验数据与验收标准分组
- 质量标准管理
- 筛网规格管理
- 员工名册管理（导入/增删改查）

- 审计与追踪
- `@LogOperation + OperationLogAspect` 自动记录关键增删改
- 操作日志查询

- AI 自动入库
- 自动解析报数文本为结构化入库任务
- 解析任务批次缓存到 Redis（24 小时）
- 支持批次查询与确认入库

## 项目结构

```text
.
├─ src/                                # Spring Boot 后端源码
├─ laibin.sql                          # MySQL 初始化脚本（含示例数据）
├─ migrations/                         # 按文件名顺序执行的增量迁移
├─ webpage/                            # Vue 3 Web 管理端
├─ LaibinSugarInventoryWxAPP/          # 微信小程序
├─ docker/                             # Docker 与 Nginx 示例配置
├─ AGENTS.md                           # AI/自动化协作规范
└─ CONTRIBUTING.md                     # 贡献指南
```

## 快速开始

### 1) 环境准备

- JDK `21`
- Maven `3.9+`
- Node.js `18+`（推荐）
- MySQL `8.x`
- Redis `6+`
- 微信开发者工具（运行小程序）

### 2) 初始化数据库

1. 创建数据库：`laibin`
2. 导入脚本：`laibin.sql`
3. 执行增量迁移并建立 checksum 台账

示例命令：

```bash
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS laibin DEFAULT CHARSET utf8mb4;"
mysql -u root -p laibin < laibin.sql
```

Windows/PowerShell 环境使用受控迁移执行器：

```powershell
.\scripts\apply-database-migrations.ps1 -EnvFile .\.env
```

已有数据库首次接入时，可在核实历史版本后显式使用
`-BaselineThrough <迁移文件名>`；该参数只登记已经存在的历史变更，不应凭日期猜测。
部署前可进行只读复核：

```powershell
.\scripts\apply-database-migrations.ps1 -EnvFile .\.env -VerifyOnly
```

### 3) 配置后端

后端配置位于：`src/main/resources/application.yml`

重点配置项：

- `spring.datasource.*`（MySQL）
- `spring.data.redis.*`（Redis）
- `jwt.secret`、`jwt.expiration`
- `wechat.appid`、`wechat.secret`
- `openai.api-url`、`openai.api-key`

建议：

- 使用环境变量或私有配置文件覆盖敏感信息，不要把真实密钥提交到仓库。
- Web 登录必须通过 `WEB_LOGIN_PASSWORD` 提供至少 12 位的共享口令；应用不再包含默认口令，缺失、过短或仍为占位值时启动失败。
- `JWT_SECRET` 必须是 Base64，且解码后至少 64 字节，以满足当前 HS512 签名要求；不合格配置会在启动期失败。

### 4) 启动后端

```bash
mvn spring-boot:run
```

默认端口：`8080`

### 5) 启动 Web 管理端

```bash
cd webpage
npm install
npm run dev
```

- Vite 默认端口一般为 `5173`
- 已配置 `/api` 代理到 `http://localhost:8080`

### 6) 启动微信小程序

1. 使用微信开发者工具打开目录：`LaibinSugarInventoryWxAPP/`
2. 在项目内执行依赖安装（若需要）：`npm install`
3. 开发者工具中执行“构建 npm”
4. 按实际环境调整 `LaibinSugarInventoryWxAPP/utils/request.js` 的 `BASE_URL`

## 接口文档

后端启动后可访问：

- Knife4j：`http://localhost:8080/doc.html`
- Swagger UI：`http://localhost:8080/swagger-ui/index.html`
- OpenAPI JSON：`http://localhost:8080/v3/api-docs`

如需将本地运行后端的 OpenAPI 文档导出到仓库文件：

```powershell
.\scripts\export-openapi.ps1 -BaseUrl http://localhost:8080
```

脚本会生成 UTF-8 无 BOM 的 `docs/openapi.json`。如果接口需要鉴权，可先设置 `WAREHOUSE_API_TOKEN` 环境变量。

提交或发布前应将快照与同一构建的运行实例对比：

```powershell
.\scripts\check-openapi-snapshot.ps1 -BaseUrl http://localhost:8080
.\scripts\check-agent-openapi-registry.ps1
```

## 统一返回与分页约定

- 统一响应：`Result<T>`
- 分页响应：`Result<PageResult<T>>`
- 分页字段：`total`、`records`
- 默认分页参数：`page=1`、`size=10`

## 主要接口分组（按 Controller）

- `/api/auth` 认证与绑定
- `/api/user` 用户信息
- `/api/products` 产品
- `/api/quality-standards` 质量标准
- `/api/assay` 化验数据
- `/api/assayGroup` 化验验收分组
- `/api/screen-mesh` 筛网
- `/api/warehouse` 库位
- `/api/in-stock` 入库
- `/api/out-stock` 出库/调拨
- `/api/inventory` 库存
- `/api/semi-products` 半成品记录
- `/api/pallet-codes` 托盘码
- `/api/employee` 员工名册
- `/api/logs` 操作日志
- `/api/auto-inbound` AI 自动入库

## 账号与登录说明（开发环境）

- Web 登录接口：`POST /api/auth/web-login`
- 登录参数：`name` + `password`
- Web 登录口令来自环境变量 `WEB_LOGIN_PASSWORD`，仓库中没有默认值。
- “记住用户名”只持久化用户名，不保存密码；历史版本留下的持久化密码会在登录页初始化时清除。

> 当前仍是统一口令模型，不等同于个人密码体系；正式安全评审应继续评估 SSO 或逐用户凭证。

## Docker 说明

仓库提供了 `docker/` 目录下的 Dockerfile 与 compose 示例，但当前路径与构建上下文依赖本地目录结构，开箱即用性有限。建议根据实际部署环境调整：

- `docker/Dockerfile`
- `docker/docker-compose.yml`
- `docker/nginx.conf`

`deploy/simple/update.sh` 会在更新应用容器前先运行一次性 MySQL 迁移容器；迁移失败、checksum 不一致或迁移锁被占用时，不会继续启动新版本后端。生产后端同时启用 schema 启动门禁，数据库台账或必需表不完整时会失败关闭。

## 开发规范

请在提交前阅读并遵守：

- [AGENTS.md](./AGENTS.md)
- [CONTRIBUTING.md](./CONTRIBUTING.md)

重点约束（摘要）：

- Controller 仅收发 DTO/VO，不直接暴露 PO
- 业务异常使用 `BusinessException`，错误码统一维护在 `ErrorCode`
- 鉴权默认全局开启，仅 `"/api/auth/**"` 与文档端点公开
- 任何改动必须附带单测和/或文档更新

## 常用命令

```bash
# 后端单测
mvn test

# Web 前端开发
cd webpage && npm run dev
```

## 许可

当前仓库未显式声明开源许可证。若计划公开到 GitHub，建议补充 `LICENSE` 文件（例如 MIT/Apache-2.0）。

