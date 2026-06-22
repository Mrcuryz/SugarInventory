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
├─ data/laibin.sql                     # MySQL 初始化脚本（含示例数据）
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
2. 导入脚本：`data/laibin.sql`

示例命令：

```bash
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS laibin DEFAULT CHARSET utf8mb4;"
mysql -u root -p laibin < data/laibin.sql
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
- 当前代码中 Web 固定口令常量为：`lbsp`（`AuthServiceImpl`）

> 建议上线前改为安全的账号体系与密码策略，不要使用固定口令。

## Docker 说明

仓库提供了 `docker/` 目录下的 Dockerfile 与 compose 示例，但当前路径与构建上下文依赖本地目录结构，开箱即用性有限。建议根据实际部署环境调整：

- `docker/Dockerfile`
- `docker/docker-compose.yml`
- `docker/nginx.conf`

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

