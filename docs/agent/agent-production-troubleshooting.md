# Agent 生产故障处理手册

普通用户统一看到：“Agent 服务暂不可用，本次未执行任何业务查询或变更。”该提示意味着 fail-closed，不应切换旧 Agent 或绕过 Gateway。

| 现象 | 管理员检查 | 处理 |
| --- | --- | --- |
| capability 握手失败 | protocolVersion、toolRegistryHash、recipeRegistryHash、agentProfileRegistryHash、工具/配方数量 | 停止流量，确认部署版本及专家权限映射一致；禁止临时放宽哈希。 |
| Redis 状态不可用 | 认证、网络、TTL、持久化、时钟 | 恢复 Redis；不要切换内存状态继续生产会话。 |
| planVersion 不匹配 | planId、recipeId、planVersion、fingerprint | 拒绝恢复，让用户重新发起查询；不要迁移或改写旧计划。 |
| 会话锁超时 | session_lock_wait_duration、锁 TTL、实例状态 | 检查慢调用和续租；等待租约过期，禁止强行并发执行。 |
| PARTIAL_SUCCESS | 步骤安全摘要中的 TOOL_TIMEOUT/TOOL_ERROR/PERMISSION_DENIED | 保留库存结果并明确限制；不要把失败写成 NO_DATA。 |
| BUDGET_EXCEEDED | toolCallCount、fan-out、步骤数 | 返回已完成部分；不得临时提高当前计划预算。 |

排障日志使用 traceId、sessionId、requestId、runId、handoffId、planId、stepId、toolCallId、interruptId 关联。普通浏览器响应中不得出现专家名、工具白名单、模型上下文、密钥或堆栈。HITL 过期或版本漂移时重新发起请求，不手工修改 Redis 状态。
