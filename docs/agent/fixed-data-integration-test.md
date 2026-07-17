# 固定数据 Agent 集成测试

入口：`scripts/test-agent-fixed-integration.ps1`。

该测试套件把三段生产链路的边界放在同一次确定性验收中：Python Runtime 使用 MockToolClient 固定业务数据，锁定 9 个非空业务专家、主 Agent 空工具集、47 个 L1 工具并集、Tool Capability Registry 和 Java/MCP 清单一致性；Java Gateway 使用固定 McpSession/HTTP fixture 验证内部鉴权、47 工具白名单、能力握手和 fail-closed；warehouse-mcp 验证 47 个工具名称、关闭的输入 schema，并让每个 Spring AI ToolCallback 至少完成一次真实参数绑定和调用。测试不连接开发或生产业务库，不依赖现有库存数据。

这是一套进程内/边界 fixture 集成契约测试，不替代真实部署 smoke test。真实环境 E2E 负责验证 9 个专家各自至少一条只读 canary、HITL、SSE、RBAC 和审计归属；不能用少量浏览器 canary 替代 47 工具的确定性契约验收。
