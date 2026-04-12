# CONTRIBUTING

欢迎贡献。本项目对结构与规范要求较严格，请遵循以下约定。

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
- 业务异常抛 `BusinessException`，由 `GlobalExceptionHandler` 统一转成 `Result`。
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

示例：

```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "total": 123,
    "records": []
  }
}
```

## 常用命令
- 后端单测：`mvn test`
- 前端 dev：`cd webpage && npm run dev`
- lint：`cd webpage && npm run lint`（如未配置，需先补充脚本）

## 硬性约束
- 任何改动必须带单测和/或文档更新，两者都缺失视为不合规。
