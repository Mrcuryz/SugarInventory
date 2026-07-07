# Agent Answer Review Plan

## 目标

本功能用于记录和审查 AI 助手对话中未正确回答、低置信度成功和用户纠正的场景，沉淀为后续工具补齐、planner 修复、UI 修复和回归测试输入。

记录对象必须只包含用户可见的安全文本和结构化归因信息，不保存 raw tool result、token、Authorization、堆栈、chain-of-thought 或内部未经脱敏的数据。

## 分阶段实施

### M1.4-review-1：基础记录与用户反馈

状态：已完成基础版。

范围：

- 新增 `agent_message_review` 和 `agent_message_review_evidence`。
- 前端在每轮助手回答完成后提交用户可见安全回答。
- 后端做会话归属校验、文本清洗、低置信度初步归因。
- 前端提供最小反馈入口，用户可标记“没解决 / 答非所问 / 数据不对 / 展示问题”。
- 后端将用户反馈升级为 `NEEDS_REVIEW`，记录 `failure_domain`、`failure_category`、`suggested_fix_type`。

本次落地：

- SQL 迁移：`migrations/2026-07-07-add-agent-message-review.sql`。
- Java API：
  - `POST /api/agent/sessions/{agentSessionId}/message-reviews`
  - `POST /api/agent/sessions/{agentSessionId}/message-reviews/{messageId}/feedback`
- 前端行为：
  - 每轮流式回答完成后，提交用户可见安全文本、页面路径、卡片是否生成、实际工具名摘要。
  - Review 写入为非阻塞旁路，不影响候选选择、resume 和继续对话。
  - 助手消息下方提供“没解决 / 答非所问 / 数据不对 / 展示问题”反馈入口。
- 测试：
  - `AgentMessageReviewServiceImplTest`
  - `mvn -q "-Dtest=AgentMessageReviewServiceImplTest,AgentSessionServiceImplTest,RuntimeRoutingAgentGatewayServiceTest" test`
  - `cd webpage && npm run build`

不做：

- 不做审查台列表页。
- 不自动创建回归测试文件。
- 不记录 raw JSON 或内部 ID 明细。
- 不新增写业务能力。

### M1.4-review-2：自动纠正检测与证据增强

状态：部分完成。

范围：

- 检测下一轮用户是否包含“不是 / 不对 / 你理解错了 / 我问的是 / 不是这个 / 怎么没有”等纠正表达。
- 自动将上一轮标记为 `NEEDS_REVIEW`。
- 补充 evidence summary，例如“上一轮只调用 resolve_products，未调用 get_inventory_distribution”。
- 关联 tool audit、runtime audit 和 interrupt state，但只保存摘要。

本次落地：

- 前端检测下一轮纠正表达，并调用 feedback API 标记上一轮回答。
- 后端新增 `USER_CORRECTION` 分类，落为 `USER_INPUT / USER_CORRECTION_DETECTED`，并将 `test_case_status` 标记为 `NEEDED`。

未完成：

- 尚未自动关联 tool audit / runtime audit / interrupt state。
- 尚未自动生成“实际只调用了哪个工具”的证据摘要。

### M1.4-review-3：审查台与修复流转

状态：未开始。

范围：

- 新增管理员审查列表与详情。
- 支持筛选 `failure_domain`、`failure_category`、`confidence_level`、`suggested_fix_type`、`test_case_status`。
- 支持人工确认预期意图、实际意图、建议修复方向。
- 支持标记 `WONT_FIX`、`FIXED`、`TEST_NEEDED`。

### M1.4-review-4：从 review 生成回归测试

状态：未开始。

范围：

- 将确认后的 review 转成 planner / safe adapter / E2E 回归用例。
- 字段 `test_case_status` 从 `NEEDED` 流转到 `CREATED`、`PASSING`。
- 将典型失败问题纳入 M1.4 工具回归集。

## 数据模型

### agent_message_review

核心字段：

- `answer_status`：`COMPLETED`、`LOW_CONFIDENCE`、`NEEDS_REVIEW`、`FAILED`、`CANCELLED`。
- `confidence_level`：`HIGH`、`MEDIUM`、`LOW`、`UNKNOWN`。
- `failure_domain`：`PLANNER`、`TOOL`、`MODEL`、`CONTEXT`、`DATA`、`PERMISSION`、`UI`、`USER_INPUT`、`SAFETY`、`SYSTEM`、`UNKNOWN`。
- `failure_category`：二级原因，例如 `INTENT_MISS`、`TOOL_MISSING`、`TOOL_NOT_CALLED`、`CONTEXT_LOST`、`CARD_RENDER_BAD`。
- `expected_intent_summary`：审查员或用户反馈中描述的预期意图。
- `actual_intent_summary`：系统实际执行路径的安全摘要。
- `expected_tool_names`：预期工具名列表，逗号分隔。
- `actual_tool_names`：实际工具名列表，逗号分隔。
- `expected_capability`：预期业务能力，例如“全部产品不合格库存按产品分组”。
- `assistant_answer_text_safe`：普通 UI 展示给用户的安全文本。
- `assistant_answer_summary`：后端生成或前端提供的短摘要。
- `suggested_fix_type`：`ADD_TOOL`、`FIX_PLANNER`、`FIX_TOOL_SCHEMA`、`FIX_SAFE_ADAPTER`、`FIX_UI_RENDER`、`FIX_PERMISSION`、`FIX_DATA_MODEL`、`FIX_PROMPT`、`FIX_TEST_CASE`、`USER_TRAINING`、`WONT_FIX`。
- `test_case_status`：`NONE`、`NEEDED`、`CREATED`、`PASSING`。
- `review_source`：`AUTO`、`USER_FEEDBACK`、`ADMIN`。
- `review_status`：`OPEN`、`TRIAGED`、`FIXED`、`WONT_FIX`。

### agent_message_review_evidence

每条 evidence 保存：

- `evidence_type`：`TOOL_AUDIT`、`RUNTIME_AUDIT`、`USER_FEEDBACK`、`AUTO_HEURISTIC`、`UI_SNAPSHOT`。
- `ref_id`：关联审计或消息 ID。
- `evidence_summary`：审查列表可直接阅读的安全摘要。

## 自动低置信度规则

第一阶段只做保守规则：

- 用户问库存、库位、托盘、化验等业务问题，但本轮未记录实际工具调用：`LOW_CONFIDENCE / PLANNER / TOOL_NOT_CALLED`。
- 回答包含“未找到匹配”“暂不支持”“无法准确”“请换一个更准确”等兜底话术：`LOW_CONFIDENCE / DATA / NO_MATCH_OR_FALLBACK`。
- 用户反馈“答非所问”：`NEEDS_REVIEW / PLANNER / INTENT_MISS`。
- 用户反馈“展示问题”：`NEEDS_REVIEW / UI / CARD_RENDER_BAD`。

后续阶段再接入 tool audit 和下一轮用户纠正规则。

## 安全边界

- 只记录普通 UI 已展示或用户输入的安全文本。
- 不记录 token、Authorization、raw JSON、堆栈、chain-of-thought。
- 不把 review API 暴露为业务写入工具。
- 不允许 Python 直连数据库写 review。
- 所有 review 写入通过 Java 鉴权和会话归属校验完成。
