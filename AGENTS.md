# AGENTS

本文件用于自动化/AI 参与改动时的最低规范约束，所有贡献者请遵守。

## 项目结构
- backend（Java/Spring Boot）：`src/` + `pom.xml`
- web（前端）：`webpage/`
- miniprogram（小程序）：`LaibinSugarInventoryWxAPP/`
- db（数据库脚本）：`project-root/backend/mysql/`（init 数据）

## 命名规范
- Java 包名：沿用 `com.Laibin.SugarInventory` 前缀，子包使用小写单词。
- 类/接口/枚举：UpperCamelCase；方法/字段：lowerCamelCase；常量：UPPER_SNAKE_CASE。
- DTO/VO/PO 后缀必须清晰：`*DTO`/`*QueryDTO`/`*VO`/`*PO`（或实体类）。
- 数据库：表/字段 `snake_case`，主键 `id`，外键 `*_id`。

## DTO/VO 分层
- Controller 只接收/返回 DTO/VO；不要直接暴露 `domain.po`。
- Service/Mapper 使用 PO/Entity；DTO -> PO -> VO 通过装配器/转换方法完成。
- 列表接口统一返回 `Result<PageResult<VO>>`。

## 异常码与全局处理
- 业务错误码统一定义在 `src/main/java/com/Laibin/SugarInventory/domain/enumObject/ErrorCode.java`。
- 业务异常抛 `BusinessException`。
- 非业务异常只记录日志，不在 Controller 内吞掉异常。

## JWT 与 Spring Security
- 无状态：`SessionCreationPolicy.STATELESS`。
- `Authorization: Bearer <token>`；解析在 `JwtAuthenticationFilter`。
- 公开接口仅限 `/api/auth/**` 与文档端点；其余默认鉴权。
- 需要当前用户时用 `@AuthenticationPrincipal LoginUser`。

## AOP
- 操作日志通过 `@LogOperation` + `OperationLogAspect`。
- 仅在需要审计的新增/更新/删除方法上使用，避免记录敏感字段。

## 分页格式
- 请求参数：`page`/`size`（默认 1/10）。
- 响应：`Result<PageResult<T>>`，字段为 `total` 与 `records`。

## 常用命令
- 后端单测：`mvn test`
- 前端 dev：`cd webpage && npm run dev`
- lint：`cd webpage && npm run lint`（如未配置，需先补充脚本）

## 硬性约束
- 任何改动必须带单测和/或文档更新，两者都缺失视为不合规。

# 智能仓储 MCP 分析任务

## 项目目标

本项目准备将现有智能仓储系统的业务能力封装为 MCP Tools，
供 Codex 和后续嵌入式 Agent 调用。

当前阶段只进行：

1. 系统功能梳理
2. API 接口盘点
3. 业务规则识别
4. MCP Tool 候选设计
5. 缺失能力分析

当前阶段禁止修改库存数据，禁止连接生产数据库，
禁止直接生成具有写入生产环境能力的工具。

## 需要分析的材料

按以下优先级分析：

1. docs/openapi.json
2. Controller
3. Request/Response DTO
4. Service接口和实现
5. 权限及事务注解
6. 数据库表结构和迁移脚本
7. 前端页面和API调用
8. 已有业务说明文档

接口文档与代码不一致时，以代码实际行为为准，
并记录不一致项。

## 分析要求

对每个接口整理以下信息：

- 所属模块
- 用户业务目标
- HTTP method和path
- Controller和Service方法
- 请求参数
- 返回结果
- 身份及权限要求
- 是否修改数据
- 是否涉及事务
- 是否支持幂等
- 是否存在并发风险
- 是否需要用户确认
- 主要业务规则
- 主要错误场景
- 是否适合直接成为MCP Tool
- 是否应与其他接口组合为业务级Tool
- 是否需要新建后端聚合接口

## MCP设计原则

1. 不要将全部REST接口一对一转换成MCP Tool。
2. MCP Tool应表达用户业务目标，而不是页面按钮或CRUD动作。
3. 查询和写入工具必须分开。
4. 写操作优先拆分为preview和execute。
5. Agent不得猜测产品ID、库位ID、库存ID。
6. 禁止设计任意HTTP请求、任意SQL或任意数据库更新工具。
7. 工具参数和返回结果必须结构化。
8. 所有写操作必须考虑权限、确认、幂等、事务和审计。
9. 无法从代码确认的业务规则必须标记为“待确认”，不得猜测。

## 输出文件

将结果写入：

- docs/mcp-analysis/system-capability-map.md
- docs/mcp-analysis/api-inventory.md
- docs/mcp-analysis/business-rules.md
- docs/mcp-analysis/mcp-tool-candidates.md
- docs/mcp-analysis/mcp-gap-analysis.md
- docs/mcp-analysis/mcp-tool-specifications.json

## MCP工具风险等级

- L0：静态文档和帮助信息
- L1：只读实时数据
- L2：业务预览和校验，不修改数据
- L3：普通业务写操作，需要明确确认
- L4：批量修改、删除、回滚、权限和配置变更

第一版MCP只允许提出L0、L1和L2工具的实现计划。