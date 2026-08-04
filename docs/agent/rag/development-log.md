# 智能仓储 RAG 开发日志

状态：`ACTIVE`
建立日期：2026-07-29
时区：`Asia/Shanghai`

## 1. 维护规则

- 本日志只追加，不删除历史记录。
- 每个开发切片使用唯一编号，例如 `RAG-01`。
- 开始开发时登记 `IN_PROGRESS`。
- 完成、暂停或阻塞时追加新记录，不覆盖开始记录。
- 必须区分设计完成、代码完成、工程测试通过、真实 Web 验收通过和正式完成。
- 文档改动也属于实施事实，应记录所更新的设计文件。
- 测试没有执行时必须明确写“未执行”，不得省略。
- 失败测试、未解决风险和外部阻断必须如实记录。

## 2. 当前状态

- 最后更新：2026-08-03
- 当前阶段：`RAG 交付收口`
- 当前工作状态：`SOURCE_INTEGRATED / ENGINEERING_REGRESSION_PASSED / V1_ARTIFACT_VALIDATED / ISOLATED_RUNTIME_28091_VERIFIED / ISOLATED_WEB_5174_VERIFIED / CURRENT_DEPLOYMENT_NOT_SWITCHED`
- 当前 corpus version：`laibin-rag-2026-07-29-v1`（正式 pointer 已选中；隔离 Python/Web 已加载验证，当前部署未切换）
- 当前材料数量：10
- 当前运行时能力：当前提交已完成 28091 Python 及 5174→28080→28091 Web 全链路隔离验收；当前 8080/8091/5173 未切换
- 当前权限决策：仅 `ADMIN` 和 `SUPER_ADMIN`
- 当前更新方式：离线整库重建和版本切换

## 3. 变更日志

### 2026-08-02 — RAG-05 发布、原子切换与回滚开始

状态：`IN_PROGRESS / FIRST_ACTIVATION_PENDING`

目标：

- 实现完整 release 预检、不可变复制、`current.json` 原子切换和显式回滚 CLI；
- 将正式语料发布到 `deploy/simple/artifacts/rag`，并由独立 Agent 实例只读加载；
- 验证失败评测阻断、同版本不覆盖、compare-and-set、中断保护、readiness、corpus version 和审计；
- 记录首次上线没有真实旧版本这一事实，不伪造相同内容的 v2。

设计决策：

- 构建候选与正式运行根目录物理分离；运行时只认正式根目录的 `current.json`；
- release 复制后再次完整验证，最后原子改名；pointer 使用同目录临时文件和 `os.replace`；
- 后续更新已有 active version 时，发布和回滚均强制匹配 expected current version；
- corpus manifest 保持离线构建的 `VALIDATED`，激活状态由 pointer 和发布审计表达，发布程序不重写
  已冻结 manifest；
- 当前仅有真实 v1：现场执行首次激活和中断保护；跨版本回滚由双版本自动化验证，待真实 v2 构建后
  再做现场演练。

文档：

- 新增 `release-switch-and-rollback-design.md`；
- 更新 RAG 文档索引和完整实施计划状态。

验证：

- 尚未执行；代码、正式 artifact、pointer 和运行进程均尚未变更。

下一步：

- 实现 publisher/CLI、部署只读挂载和测试；
- 全量回归通过后执行正式候选预检与首次激活。

### 2026-08-02 — RAG-05 发布器实现与正式候选预检

状态：`ENGINEERING_TESTS_PASSED / GATE_D_NOT_YET_CLOSED`

完成内容：

- 新增 `publisher.py`，实现完整 release 预检、全树摘要、文件数/体积上限、链接和 reparse point
  拒绝、排他发布锁、不可变复制、复制后复检、只读属性和原子 pointer；
- 新增 `validate-release`、`publish-release`、`rollback-release` CLI；
- 已有 active version 时强制 expected current version；同版本内容不同禁止覆盖；
- 指针替换前中断保持旧 pointer，同一完整 inactive release 可核对摘要后续跑；
- 审计事件只记录版本、摘要、操作、时间和发布人，不记录绝对路径或凭据；
- runtime loader 提取共用 release artifact 校验函数，启动校验行为保持不变；
- Docker Compose 新增 corpus 和本地 embedding 模型只读挂载；`.env.example` 新增 RAG 配置；
- 新增双版本发布/回滚、失败评测、同版本冲突、中断、锁、compare-and-set、路径穿越和安全 CLI
  测试。

正式候选预检：

- corpus version：`laibin-rag-2026-07-29-v1`；
- 10 份 document、196 个 chunk、83 条固定评测、23 个发布文件；
- 总大小：1,491,225 bytes；
- release SHA-256：
  `4a7525328af286d8ca7702ddc52c3f857244745bf0db080a84d426c6392456f6`；
- 结果：`SUCCEEDED`；正式 `deploy/simple/artifacts/rag` 仍不存在，未复制 release，未创建 pointer。

验证结果：

- 发布器/runtime 定向：`29 passed`；
- RAG 专项：`115 passed, 5 skipped`；
- Python 全量：`440 passed, 5 skipped, 1 failed`；失败为非 RAG 的
  `test_active_goal_is_exposed_to_expert_as_controlled_contract`，当前生产目标契约的 businessResult
  已由并行工作流改为详细事实标签，而同一并行改动中的断言仍期望短标签“在制物料”；
- 排除上述单项后 Python：`441 passed, 5 skipped, 1 deselected`；
- Java 全量：`mvn -q test` 通过；
- Web：直接执行 Node 测试 `65 passed`；`package.json` 当前没有 `test:unit` script，因此
  `npm run test:unit` 未执行成功，未把它记录为通过；
- Web `npm run build`：通过，只有既有 Sass legacy API 和大 chunk 警告；
- `compileall`：通过；本轮文件均为 UTF-8 无 BOM，未发现乱码特征。

门禁结论：

- RAG 发布器和候选 Gate C 通过；Java/Web 通过；
- 完整 Gate D 要求 Python 全量为绿，当前仍有一个并行工作流的非 RAG 断言不一致；不做豁免，
  不越权修改该并行功能，正式激活保持等待；
- 当前 8080/8091 和 18080/18091 均未停止或重配。

下一步：

- 复查并行工作流收敛后的 Python 全量；
- Gate D 关闭后执行正式不可变复制、原子激活、新端口实例验证和首次上线中断保护记录。

### 2026-07-29 — RAG-00 设计基线

状态：`COMPLETED`

完成内容：

- 盘点甲方提供的 9 份 DOCX 工艺流程图和 1 份 16 页企业宣传册。
- 确认 DOCX 主要由文本框、形状和连接线构成，普通文本抽取存在兼容层重复。
- 确认宣传册绝大多数页面没有可用文本层，需要逐页渲染和中文 OCR。
- 确认全部材料按甲方说明视为当前现行版本。
- 确认缺少生效日期、负责人、审核人和替代关系不阻塞第一版。
- 确认第一版仅管理员访问，并预留后续细粒度权限字段。
- 确认离线一次性处理，运行时不解析文档、不执行 OCR、不生成 embedding。
- 确认材料更新时重新处理完整材料集合，生成新的整库版本。
- 建立 RAG 文档索引、离线加工设计、语料与索引契约、评测方案和本开发日志。

本次文档：

- `docs/agent/rag/README.md`
- `docs/agent/rag/offline-ingestion-design.md`
- `docs/agent/rag/corpus-and-index-contract.md`
- `docs/agent/rag/evaluation-plan.md`
- `docs/agent/rag/development-log.md`

代码改动：

- 无。

测试：

- 未执行业务代码测试，本切片仅建立设计文档。
- 5 份 Markdown 均已检查为 UTF-8 无 BOM。
- 未发现 Unicode 替换字符或常见中文乱码片段。
- 已逐份检查新增文件的 `git diff`，中文内容可读。

验证记录：

- 编码检查：5 份文件 `utf8Bom=false`、`replacementChars=0`。
- 差异检查：`git diff --no-index -- NUL <新增文档>`。
- 业务测试：未执行，原因是本切片只有文档新增，没有业务代码或配置变更。

遗留事项：

- `RAG-01` 开始前需要确定离线处理程序的模块位置和运行方式。
- embedding 模型、OCR 组件和索引物理实现将在 `RAG-01/RAG-02` 技术验证后记录，不在设计阶段猜测。
- `knowledge_expert`、GoalContract、FactEnvelope、CompletionEvaluator、Java 权限和 Web 展示尚未实现。

### 2026-07-29 — RAG-00A 完整实施计划基线

状态：`COMPLETED`

完成内容：

- 复核现有 MCP、Agent、GoalContract、FactEnvelope、CompletionEvaluator、Java Gateway、Web 入口和部署结构。
- 确认第一版采用 Agent Service 内部 L0 只读知识能力，不增加仓储 MCP Tool 数量。
- 冻结小规模语料的 SQLite FTS5 中文预分词、离线文档向量、NumPy 向量检索和 RRF 融合路线。
- 明确 OCR 和 embedding 使用独立 provider 配置，不默认复用当前对话模型。
- 明确文档解析、OCR、切分和文档向量化离线执行；运行时仅执行权限过滤、query embedding 和只读检索。
- 定义 `knowledge_expert`、两个知识 GoalContract、引用卡片、审计和故障状态的实施方向。
- 发现当前 Web AI 助手入口和 Java Agent 会话未完整限制管理员，将 `RAG-SEC-01` 设为首个代码门禁。
- 制定从权限、离线处理、索引、Agent 接入、Web UAT 到发布回滚的完整工作分解和 Gate A～F。

本次文档：

- 新增 `docs/agent/rag/implementation-plan.md`。
- 更新 `docs/agent/rag/README.md`。
- 更新 `docs/agent/rag/offline-ingestion-design.md`。
- 更新 `docs/agent/rag/corpus-and-index-contract.md`。
- 更新 `docs/agent/rag/evaluation-plan.md`。
- 更新 `docs/agent/rag/development-log.md`。

代码改动：

- 无。

测试：

- 未执行业务代码测试，本次仅完成实施计划和设计一致性修订。
- 6 份 Markdown 均已检查为 UTF-8 无 BOM。
- 未发现 Unicode 替换字符或常见中文乱码片段。
- Markdown 相对链接错误数为 0。
- 13 个 JSON fenced code block 均可解析。
- 未发现未闭合的 fenced code block。

验证记录：

- 编码检查：6 份文件 `utf8Bom=false`、`replacementChars=0`、`suspiciousMojibake=0`。
- 结构检查：`markdownLinkErrors=0`、`jsonErrors=0`、`unbalancedFenceErrors=0`。
- 差异检查：`git diff --no-index --stat -- NUL docs/agent/rag/implementation-plan.md` 显示新增 1113 行。
- 业务测试：未执行，原因是本切片只有文档新增和修订，没有业务代码或配置变更。

关键决策：

- 第一版管理员角色为 `ADMIN` 和 `SUPER_ADMIN`。
- `search_approved_knowledge` 是内部只读能力，未来可保持契约迁移为 L0 MCP。
- 生产发布必须同时通过语料、索引、工程、UAT 和回滚门禁。
- provider 未配置时允许完成适配器和 fixture 测试，但不得把真实语料或完整 RAG 标记为完成。

遗留事项：

- 所有代码实施切片仍为 `NOT_STARTED`。
- OCR 和 embedding provider 的实际地址、模型和凭据需在对应实施切片开始时通过环境配置提供。
- 当前工作区存在其他未提交改动，实施时必须按切片隔离并保护。

### 2026-07-29 — RAG-SEC-01 Agent 管理员门禁开始

状态：`IN_PROGRESS`

目标：

- 把“Web Agent 仅管理员使用”从产品约束落实为前后端可验证的系统权限。
- 管理员角色限定为 `ADMIN` 和 `SUPER_ADMIN`。
- 前端隐藏只用于体验，Java 必须执行最终权限校验。
- 为后续 Python `knowledge_expert` 保留第二次受信角色检查，不在本切片提前实现 RAG 检索。

计划改动范围：

- `src/main/java/com/Laibin/SugarInventory/agent/controller/AgentSessionController.java`
- Java Agent 管理员访问守卫及对应测试。
- `webpage/src/components/Menu.vue`
- Web 管理员可见性辅助逻辑及对应测试。
- RAG 设计和开发日志。

明确不修改：

- 不解析或改写甲方原始材料。
- 不构建 corpus、chunk、embedding 或索引。
- 不增加 MCP Tool。
- 不修改库存、库位、托盘、化验或生产业务数据。
- 不清理或覆盖工作区中已有的其他未提交改动。

预期验证：

- ADMIN、SUPER_ADMIN 可以访问 Agent。
- STAFF、QC、自定义普通角色和未登录用户被拒绝。
- 非管理员直接调用 Java Agent API 返回 403。
- Web 仅为管理员展示并挂载 AI 助手。
- 现有管理员 Agent 会话和消息流程回归通过。

预期测试命令：

- `mvn -q -Dtest=<RAG-SEC-01相关Java测试> test`
- `cd webpage && npm run test:unit`
- `cd webpage && npm run build`

风险：

- 当前权限体系同时存在 `roleCode` 与 authority，需要以受信 `LoginUser.user.roleCode` 为准，避免依赖未确认的 `ROLE_*` 映射。
- Agent 会话内部审计请求也会经过同一 Controller，必须允许绑定管理员会话的受信内部请求正常记录。
- 当前工作区已有其他 Agent 改动，测试失败时需要区分既有失败与本切片回归。

### 2026-07-29 — RAG-SEC-01 Agent 管理员门禁工程验证

状态：`ENGINEERING_TESTS_PASSED`

完成内容：

- 新增统一 Java `AgentAccessPolicy`，以受信 `LoginUser.user.roleCode` 限定 `ADMIN` 和 `SUPER_ADMIN`。
- 在 Java Agent 会话 Controller 的创建、查询、消息、流式消息、取消、恢复、反馈、撤销和工具审计入口执行管理员校验。
- 在 Agent Session Service 的会话创建、所有权校验、delegation 校验和内部 Tool Session 校验再次执行管理员校验；管理员角色被移除后，已有会话不能继续调用。
- 在 Python Agent Service 的 `/internal/agent/chat`、`/chat/stream` 和 `/resume` 入口校验 Java 传入的受信用户角色，形成纵深防御。
- Web 顶栏仅为管理员展示 AI 助手入口，非管理员不挂载 `AgentAssistant`，组件自身 `open()` 仍保留二次校验。
- 权限辅助逻辑均兼容角色码前后空白和大小写，但只接受 `ADMIN`、`SUPER_ADMIN`。

实际改动：

- 新增 `src/main/java/com/Laibin/SugarInventory/agent/security/AgentAccessPolicy.java`。
- 更新 `src/main/java/com/Laibin/SugarInventory/agent/controller/AgentSessionController.java`。
- 更新 `src/main/java/com/Laibin/SugarInventory/agent/service/impl/AgentSessionServiceImpl.java`。
- 更新 `agent-service/app/main.py`。
- 新增 `webpage/src/components/agent/agentAccess.mjs` 并更新 `Menu.vue`、`AgentAssistant.vue`。
- 新增或更新 Java、Python、Web 权限测试。

测试结果：

- Java 定向测试：`mvn.cmd -q -Dtest=AgentAccessPolicyTest,AgentSessionControllerAccessTest,AgentSessionControllerWarmupTest,AgentSessionServiceImplTest test`，通过。
- Java 全量测试：`mvn.cmd -q test`，通过。
- Python 权限定向测试：`python -m pytest -q tests/test_agent_service.py -k internal_chat`，`8 passed`。
- Python 全量测试：`python -m pytest -q`，`313 passed`；存在 1 条既有 Starlette `on_event` 弃用警告。
- Web 权限单测：`node --test src/components/agent/agentAccess.test.mjs`，`2 passed`。
- Web 当前全部 Node 测试：`node --test src/components/agent/*.test.mjs src/utils/*.test.mjs`，`37 passed`。
- Web 构建：`npm run build`，通过；存在既有 Sass legacy API 和大 chunk 警告。

验证结论：

- Java API 对非管理员返回业务码 `403`；Python 内部接口返回 HTTP `403`。
- 本切片的代码和工程自动化测试已经通过。
- 尚未在真实登录态浏览器中完成 ADMIN、SUPER_ADMIN、STAFF、QC 的逐角色验收，因此不标记 `UAT_PASSED` 或 `COMPLETED`。
- `RAG-01A` 可以进入开发；RAG 整体运行时检索能力仍未实现。

工作区说明：

- 开始本切片前工作区已有其他 Agent、分析报表和 MCP 相关未提交改动。
- 本切片没有清理、回退或覆盖这些既有改动；部分测试文件的差异同时包含既有功能变更，提交时应按文件或补丁块隔离。

### 2026-07-29 — RAG-01A 离线框架与材料清单开始

状态：`IN_PROGRESS`

目标：

- 建立只读取显式材料目录、不会修改原始材料的离线构建入口。
- 固化 corpus、document、chunk、index 和 QA 的 Pydantic 契约。
- 对甲方 10 份现行材料生成可重复的文件清单、SHA-256 和稳定 `documentId`。
- 固化输出目录、版本命名、安全检查和故障分类，为 DOCX/PDF 解析切片提供稳定输入。

计划改动范围：

- `agent-service/pyproject.toml`
- `agent-service/app/rag/`
- `agent-service/tests/rag/`
- 项目 `.gitignore`
- `docs/agent/rag/`

明确不修改：

- 不修改、重命名或复制 `D:\Users\Mrcury\Desktop\laibin RAG` 中的甲方原始材料。
- 不解析 DOCX 流程正文，不渲染 PDF，不调用 OCR 或 embedding provider。
- 不生成知识块、关键词索引或向量索引。
- 不把原始材料、构建 artifact、绝对源路径或任何 provider 凭据提交到 Git。
- 不修改现有 Agent 运行时、MCP Tool 数量或仓储业务数据。

完成门槛：

- CLI 必须显式提供 `--source` 和 `--output`，且只扫描 source 根目录内的受支持普通文件。
- 拒绝 source/output 相同、output 位于 source 内、符号链接和根目录逃逸。
- 相同相对路径与文件内容生成相同 `documentId` 和 checksum。
- 甲方目录必须稳定识别为 9 个 DOCX、1 个 PDF，共 10 个文件。
- 输出只能写入显式且被 Git 忽略的 artifact 根目录。
- 契约、fixture、安全边界和真实材料 inventory 自动化测试通过。

预期测试：

- `cd agent-service && python -m pytest -q tests/rag`
- `cd agent-service && python -m app.rag.offline.cli inventory --source <材料目录> --output <artifact目录> --corpus-version laibin-rag-2026-07-29-v1`
- `cd agent-service && python -m pytest -q`

风险：

- 材料目录位于工作区外，测试只能只读访问，不能依赖写权限。
- Windows 下 junction/reparse point 与普通符号链接都必须按“不跟随链接”处理。
- 中文文件名、路径空格和 JSON 输出必须保持 UTF-8 无 BOM。
- 当前工作区存在其他未提交改动，必须限制到本切片文件并检查差异。

### 2026-07-29 — RAG-01A 离线框架与材料清单工程验证

状态：`ENGINEERING_TESTS_PASSED`

完成内容：

- 新增 `agent-service/app/rag/contracts.py`，实现 source inventory、corpus、document、process step、chunk、index 和 QA 的 Pydantic v2 基础契约。
- 新增只读 source inventory：规范化相对路径、稳定 `documentId`、流式 SHA-256、确定性排序、文件变化检测和 inventory checksum。
- 新增离线 CLI `inventory` 与 `validate-inventory`。
- 固化本地 release 目录为 `agent-service/build/rag/releases/<corpus-version>/`，并建立 documents、chunks、index、qa、evaluation 骨架。
- CLI 校验 source/output 不重叠、output 位于项目内且被 Git 忽略、source/output 不含链接或 reparse point、同版本 release 不覆盖。
- 新增 `rag-build` optional extra；DOCX/PDF/OCR 依赖与普通 Agent runtime 依赖保持分离。
- 新增契约 fixture、安全边界、稳定性、CLI 和 checksum 测试。
- 新增 `offline-build-runbook.md`，记录命令、依赖、安全边界和故障分类。

真实材料结果：

- source：甲方提供的当前现行材料目录，只读扫描。
- corpus version：`laibin-rag-2026-07-29-v1`。
- 文件数：10。
- 类型：9 个 DOCX、1 个 PDF。
- 总大小：99,671,542 bytes。
- 忽略文件数：0。
- inventory SHA-256：`cefd802a6bbee06ee211c45b1638c57a7c5f03458a5db843ee0ec3649abfd574`。
- 使用两个独立 Git 忽略输出根目录重复执行，inventory SHA-256 相同。
- 生成 artifact 位于 Git 忽略目录，没有进入 `git status`。
- 清单只保存相对路径，不保存本机 source 绝对路径。

测试结果：

- RAG 切片测试：`python -m pytest -q tests/rag`，`13 passed, 1 skipped`。
- 跳过项是当前 Windows 沙箱不允许创建真实符号链接；另有不依赖系统权限的 reparse point 模拟测试覆盖相同 fail-closed 分支。
- Agent Service 全量回归：`python -m pytest -q`，`326 passed, 1 skipped`。
- 全量回归存在 1 条既有 Starlette/TestClient 关于 `httpx`/`httpx2` 的弃用提示，不是本切片失败。
- 编译检查：`python -m compileall -q app\rag`，通过。
- 真实 inventory 生成命令成功，返回 10 文件、9 DOCX、1 PDF。
- `validate-inventory` 对真实清单的契约和 checksum 校验通过。
- `git check-ignore` 确认真实 artifact 命中 `/agent-service/build/`。
- `git diff --check` 无空白错误；新增文件 UTF-8 无 BOM，未发现替换字符。

明确未完成：

- 未解析 9 份 DOCX 正文和流程关系。
- 未渲染或 OCR 16 页 PDF。
- 未生成规范化 document、chunk、embedding 或索引。
- 未把任何 RAG 能力接入 Agent runtime。

结论：

- `RAG-01A` 的工程完成门槛已经通过。
- 本切片不涉及真实 Web 功能，不需要单独 UAT；整体 RAG 仍处于实施中。
- 下一切片为 `RAG-01B`，只能消费 inventory 中登记的 9 个 DOCX。

### 2026-07-29 — RAG-01A 离线框架与材料清单完成

状态：`COMPLETED`

完成依据：

- 代码、契约、CLI、fixture、安全测试、真实 10 文件 inventory、重复构建 checksum 和构建手册均已完成。
- 该切片没有最终用户 Web 界面，不存在待执行的切片级 UAT。
- 原始材料未修改，artifact 被 Git 忽略，运行时能力未提前启用。

### 2026-07-29 — RAG-01B DOCX OOXML 解析开始

状态：`IN_PROGRESS`

目标：

- 只消费 `source-inventory.json` 登记且 checksum 匹配的 9 个 DOCX。
- 提取正文、表格、DrawingML/VML 文本框、兼容结构和可用版面坐标。
- 对 `mc:AlternateContent` 和重复兼容表示执行可解释去重。
- 恢复流程节点候选、步骤编号、控制点、参数、分支和连接关系候选，但不把 XML 出现顺序直接当作业务流程顺序。
- 每份 DOCX 生成规范化前的结构化 extraction JSON 和质量报告。

计划改动范围：

- `agent-service/app/rag/offline/docx_parser.py`
- `agent-service/app/rag/offline/cli.py`
- `agent-service/app/rag/contracts.py`
- `agent-service/tests/rag/`
- `docs/agent/rag/`

输入与输出：

- 输入：`source-inventory.json`、显式 source 根目录、显式 Git 忽略 artifact 根目录。
- 输出：同 corpus release 的 `documents/<document-id>.extraction.json` 和 `qa/docx-extraction-report.json`。
- 视觉 QA：9 份 DOCX 逐份渲染，页图只进入 Git 忽略构建目录。

明确不修改：

- 不修改、另存或覆盖甲方 DOCX。
- 不把原始 DOCX、渲染 PNG/PDF 或绝对 source 路径提交到 Git。
- 不调用 LLM、OCR 或 embedding 生成/补写流程步骤。
- 不在本切片冻结最终 normalized document/chunk。
- 不解析宣传册 PDF，不接入 Agent runtime。

完成门槛：

- 9/9 inventory DOCX checksum 在解析前再次验证。
- 9/9 DOCX 可安全打开并生成 extraction。
- 9/9 文档完成逐页渲染与视觉对照。
- 文本框兼容层重复可解释且没有大段重复输出。
- 关键数值、单位、CCP/CPP 原标签和主要步骤文本与渲染页一致。
- 标题冲突、疑似流程关系和无法确定的连接线进入质量标记，不由代码猜测。
- parser fixture/golden、真实材料 extraction 和 Agent Service 全量回归通过。

预期测试：

- `cd agent-service && python -m pytest -q tests/rag`
- `python -m app.rag.offline.cli parse-docx --source <材料目录> --output <artifact目录> --corpus-version laibin-rag-2026-07-29-v1`
- `cd agent-service && python -m pytest -q`

风险：

- 流程图视觉顺序与 OOXML 节点顺序可能不同，必须结合步骤号、坐标和连接关系。
- Word/WPS `AlternateContent`、VML fallback 和复制对象可能造成重复。
- 连接线端点可能只保存形状 ID，无法可靠映射时必须标记人工复核。
- 当前文档均为单页，但页数只能以实际渲染结果为准。
- 工作区存在其他未提交改动，本切片继续限制在已登记文件。

### 2026-07-29 — RAG-01B DOCX OOXML 解析工程验证

状态：`ENGINEERING_TESTS_PASSED`

完成内容：

- 在 `contracts.py` 增加 DOCX 包、文本块、版面、流程节点、控制参数、连接线、关系候选、
  提取指标、视觉核验和批报告契约。
- 实现 `parse-docx` CLI，只消费 inventory 登记且再次通过路径、类型、大小和 SHA-256
  校验的 9 份 DOCX。
- 对 ZIP 条目数量、解压大小、单条目大小、压缩比、重复路径、目录逃逸和加密条目执行
  fail-closed 检查。
- XML 解析禁用 DTD、外部实体和网络访问；外部关系只登记数量，不访问目标。
- 对 8 份 DrawingML `Choice`/VML `Fallback` 文档去除兼容层重复，并兼容 1 份纯 VML
  文档。
- 提取普通段落、表格、文本框、版面坐标、CCP/CPP、参数原文和单位、连接线几何。
- 连接线没有可靠端点映射时不猜测，只为明确步骤号生成待视觉复核的顺序关系候选。
- 建立真实材料 golden，锁定文档 ID、标题冲突、节点/连接线数量和关键质量标记。

真实材料结果：

- 9/9 DOCX 解析成功，失败数为 0。
- 视觉核验待处理数为 9，批状态为 `REQUIRES_REVIEW`。
- 识别到 8 份 DrawingML/VML 兼容文档和 1 份纯 VML 文档。
- `单晶黄冰糖工艺流程图24.12.docx` 保留 `TITLE_CONFLICT`。
- 白砂糖分装材料保留 `CONTROL_POINT_LABEL_CONFLICT`。
- 所有文档保留 `FLOW_RELATION_REQUIRES_REVIEW` 和
  `VISUAL_REVIEW_PENDING`，未把推测关系固化为业务事实。
- 两个独立输出目录的 extraction manifest SHA-256 均为
  `c51f92b83ca623068eaae0ae2d22f6d0d63f14649bb88df02f51eadc62c98180`。
- extraction 和 QA 报告未包含材料目录绝对路径。

测试结果：

- RAG 切片测试：`python -m pytest -q tests/rag`，`21 passed, 2 skipped`。
- 一个跳过项是 Windows 沙箱不允许创建真实符号链接，已有不依赖系统权限的模拟测试覆盖
  相同 fail-closed 分支。
- 另一个跳过项是普通测试环境未设置工作区外真实材料路径。
- 显式设置 `LAIBIN_RAG_SOURCE_DIR` 后执行真实 golden：`1 passed`。
- Agent Service 全量回归：`python -m pytest -q`，`335 passed, 2 skipped`。
- 全量回归存在 1 条既有 Starlette/TestClient 关于 `httpx`/`httpx2` 的警告，不是失败。
- `python -m compileall -q app\rag` 通过。

工程结论：

- 结构化 extraction、批报告、输入安全校验和确定性门槛已通过。
- extraction 仍是规范化前的中间事实，不能绕过视觉核验直接进入 corpus、chunk 或索引。

### 2026-07-29 — RAG-01B DOCX 视觉核验阻塞

状态：`BLOCKED`

阻塞事实：

- 文档处理规范要求对 9 份 DOCX 逐页渲染并检查全部页面。
- 当前环境没有 LibreOffice/`soffice`，标准 DOCX 渲染脚本无法运行。
- 尝试使用本机 Word COM 导出时返回 `0x80070520`，说明当前自动化会话不可用。
- 尝试通过 Windows UI 自动化启动 Word 后没有得到可检查的应用窗口。
- 因此无法证明版面顺序、连接线、分支、回用关系和渲染页文本与结构提取完全一致。

处理决定：

- 不继续反复启动不可用的渲染组件，不修改原始 DOCX。
- 保留全部 `VISUAL_REVIEW_PENDING` 和 `FLOW_RELATION_REQUIRES_REVIEW` 标记。
- `RAG-01B` 只记录为工程测试通过，不标记 `COMPLETED`。
- 待离线构建机提供可用 Office/LibreOffice 渲染环境后恢复逐页视觉核验。
- 与 DOCX 视觉核验无依赖的 `RAG-01C` PDF 切片可以继续推进。

### 2026-07-29 — RAG-01C PDF 渲染与 OCR 适配开始

状态：`IN_PROGRESS`

目标：

- 只消费 inventory 登记且 checksum 匹配的 1 份 PDF。
- 安全读取 PDF 元数据、页数和现有文本层。
- 在 Git 忽略 artifact 中逐页渲染 200～300 DPI PNG，并检查全部 16 页。
- 建立可配置、默认关闭、不得回传本机路径的 OCR provider 契约和适配层。
- 无 OCR provider 时仍生成可审计的预检/渲染结果，但真实 PDF 语料保持阻塞。
- 为后续版面分区和规范化保留页码、区域、置信度、原文和质量标记。

计划改动范围：

- `agent-service/app/rag/contracts.py`
- `agent-service/app/rag/config.py`
- `agent-service/app/rag/offline/pdf_parser.py`
- `agent-service/app/rag/offline/cli.py`
- `agent-service/tests/rag/`
- `docs/agent/rag/`

安全与完成门槛：

- 不修改、覆盖或另存甲方原始 PDF。
- 解析前再次核对 inventory，拒绝链接、路径逃逸和文件变化。
- PDF 解析器禁用不必要的网络行为；OCR 只允许显式 HTTPS endpoint 和环境变量凭据。
- provider 请求只传页面图像字节和页号，不传本机绝对路径。
- 16/16 页面成功渲染并完成视觉检查；页面 PNG 不提交 Git。
- 真实 PDF 无 OCR provider 时不得标记语料完成。
- fixture、安全测试、真实渲染检查和 Agent Service 全量回归通过。

预期验证：

- `cd agent-service && python -m pytest -q tests/rag`
- `python -m app.rag.offline.cli parse-pdf --source <材料目录> --output <artifact目录> --corpus-version laibin-rag-2026-07-29-v1`
- `cd agent-service && python -m pytest -q`

### 2026-07-29 — RAG-01C PDF 渲染与 OCR 适配工程验证

状态：`ENGINEERING_TESTS_PASSED`

完成内容：

- 增加 PDF package、页级 extraction、文本层状态、OCR 状态、归一化区域、渲染信息、
  视觉核验和批报告契约。
- 新增 `parse-pdf`，只消费 inventory 登记且再次通过路径、类型、大小和 SHA-256 校验的
  PDF。
- 使用 pypdf 严格读取元数据、16 页、页面尺寸和文本层；拒绝加密、异常文件大小、
  异常页数和无效几何。
- 使用显式 `pdftoppm.exe` 逐页渲染 250 DPI PNG；限制单页超时、文件大小、像素数量，
  并记录页级相对路径、尺寸与 SHA-256。
- 实现默认关闭的 HTTP JSON OCR provider：只允许无凭据 HTTPS endpoint 和显式 host
  allowlist，禁用 redirect 和环境代理，限制图像/响应大小并严格验证响应契约。
- OCR 请求不发送本机路径；脱敏日志不保存 endpoint、API key、页面图像、OCR 文本或响应正文。
- 新增 `record-pdf-visual-review`，以预期 extraction SHA-256 执行 compare-and-set，
  再次校验每页路径、hash 和尺寸后记录人工视觉复核。
- 建立 PDF fixture 与真实结构 golden，覆盖加密文件、无 OCR、OCR 成功/失败、HTTPS
  配置、host allowlist、非法坐标、页面被替换、复核并发和不可覆盖。

真实宣传册结果：

- 文档 ID：`doc-94170623c8ba7c517d863337`。
- 源 SHA-256：`49ad40ab08e07f305570948ace821df151673dd2aecab679afbd2fc9fe694cc3`。
- 16/16 页成功渲染，页面均为 3347×1890。
- 已逐页检查全部 16 个 PNG；方向正确、页面完整，无黑页、裁切、缺字形或渲染破损。
- 页面主题连续覆盖封面、目录、企业简介、企业荣誉、产品介绍、销售网络和认证体系。
- 文本层为 12 页 `EMPTY`、4 页 `SPARSE`、0 页 `PRESENT`。
- 第 2、3 页虽分别抽出 66/67 字符，但仅为重复目录数字和少量产品词，远少于可见正文；
  因此收紧可用文本层门槛，两页仍进入 OCR 待处理。
- 两个独立输出目录的复核前 extraction manifest SHA-256 均为
  `48ae59ba2cdc8686c63a5c9f869448937ed7a268292473d59daf2629f02e8a19`。
- 两次构建的 16 个 PNG 目录执行逐字节比较，无差异。
- 视觉复核后的 extraction SHA-256 为
  `b135ffc7f8a16de4baaf6003ed697fcd27435fc70c1ff714359890d435c9b1a0`。
- 视觉复核后的 batch manifest SHA-256 为
  `5e01a9aced4c4cc5239c937f7178bb394d9d1faac9b2788f469aef4559523a2e`。
- 真实 artifact 位于 Git 忽略目录，不包含材料根目录绝对路径或 provider 凭据。

测试结果：

- PDF 定向测试：`16 passed`。
- RAG 切片测试：`37 passed, 3 skipped`。
- 三个普通测试跳过项分别是 Windows 沙箱真实链接创建、未配置真实 DOCX 目录和未配置
  真实 PDF 目录/renderer；对应安全模拟与显式真实运行已覆盖。
- 真实 PDF golden：重新读取 99 MB 源文件并渲染 16 页，`1 passed`。
- Agent Service 全量回归：`352 passed, 3 skipped`。
- 全量回归存在 1 条既有 Starlette/TestClient 关于 `httpx`/`httpx2` 的警告，不是失败。
- `python -m compileall -q app\rag tests\rag` 通过。

工程结论：

- PDF 安全预检、页面渲染、视觉核验、OCR 适配边界和工程自动化测试已通过。
- 本切片没有把宣传册内容写入规范化 document、chunk、索引或 Agent runtime。

### 2026-07-29 — RAG-01C 真实 OCR 阻塞

状态：`BLOCKED`

阻塞事实：

- 当前没有 OCR provider endpoint、host allowlist、API key 和模型配置。
- 宣传册 12 页没有文本层，另外 4 页文本层稀疏且明显不足，16 页均必须 OCR。
- provider fixture 通过不能替代真实中文 OCR、版面分区和重要企业事实人工复核。

处理决定：

- 16 页均保留 `OCR_PROVIDER_NOT_CONFIGURED`，批状态保持 `REQUIRES_REVIEW`。
- 不把少量 pypdf 文本当作完整页面正文，不由 LLM 看图补写宣传材料。
- 不把 PDF 渲染成功或视觉核验通过标记为宣传册语料完成。
- OCR provider 可用后，从完整 inventory 重新执行 `parse-pdf`，完成 OCR、页面区域和事实复核。

### 2026-07-29 — RAG-01D 规范化前置条件阻塞

状态：`BLOCKED`

阻塞条件：

- `RAG-01B` 的 9 份 DOCX 尚未在可用 Office/LibreOffice 环境完成逐页视觉核验。
- `RAG-01C` 的 16 页宣传册尚未完成真实 OCR、版面分区和重要事实复核。

影响：

- 不能冻结 10 份规范化 document。
- 不能将结构 extraction 直接降格为最终知识事实。
- 不能提前生成 chunk、关键词索引、向量索引或接入 Agent runtime。

恢复顺序：

1. 在离线构建机恢复 DOCX renderer，完成 9 份视觉对照。
2. 配置受控 OCR provider，重新处理宣传册并完成 16 页 OCR/版面/事实复核。
3. 两项质量门禁通过后启动 `RAG-01D`，从完整材料集合重新生成规范化语料。

### 2026-07-31 — RAG-ENV-01 Office 与本地 OCR 环境实施开始

状态：`IN_PROGRESS`

目标：

- 恢复 9 份 DOCX 的无人值守逐页渲染和视觉核验能力。
- 在材料不离开本机的前提下完成 16 页宣传册的中文 OCR。

环境事实：

- Windows 11 64 位，15.7 GB 内存，RTX 3060 Laptop 6 GB。
- Microsoft Office Home and Student 2019 已安装，但 Word COM 在当前非交互会话不可用。
- LibreOffice 和 OCR 引擎尚未安装；Poppler `pdftoppm.exe` 已验证可用。
- 现有 `rag-venv` 继承系统 site-packages，不作为最终可复现 OCR 环境。

关键决策：

- DOCX 使用 LibreOffice 稳定版和技能包 `render_docx.py`，不再以 Word COM 作为构建依赖。
- 本地 OCR 使用 `rapidocr==3.9.2`、`onnxruntime==1.23.2`、PP-OCRv6 中文小模型和 CPU
  execution provider。
- 新建不继承系统 site-packages 的 `agent-service/build/rag-build-venv`。
- 保留现有 `HttpJsonOcrProvider`；新增显式 `local-rapidocr` provider，不静默切换后端。
- CPU 优先是为了固定结果和减少 CUDA/cuDNN 组合风险；GPU 加速保留为后续扩展。

设计文档：

- `docs/agent/rag/office-and-local-ocr-environment.md`

计划验证：

- 安装后记录 LibreOffice 与 OCR 组件实际版本。
- 使用一个真实 DOCX 完成 renderer smoke test，再执行 9 份 DOCX 全量渲染。
- 使用 fixture 验证本地 OCR 契约，再从完整 inventory 重新执行真实 PDF OCR。
- 执行 provider、PDF parser、RAG 与 Agent Service 回归测试。

当前风险：

- LibreOffice 与 Office 的分页、字体替换可能存在差异，必须逐页人工目检。
- OCR 结果不是业务事实来源，低置信度文本和重要企业事实仍必须人工对照。

### 2026-07-31 — RAG-ENV-01 Office 与本地 OCR 环境实施完成

状态：`COMPLETED`

完成内容：

- 通过官方 Windows x86-64 MSI 安装 LibreOffice 26.2.5.2；`winget` 的 MSI 事务挂起后
  改用已下载的官方安装包，安装返回 `3010`（成功、建议重启）。
- 验证 `C:\Program Files\LibreOffice\program\soffice.com --version` 成功；Word 2019
  继续仅作为人工工具，不进入无人值守构建依赖。
- 确认 Windows 渲染必须固定控制台入口 `soffice.com`，并把 Poppler bin 目录加入当前
  进程 `PATH`；缺少 Poppler 时只能生成中间 PDF，不能生成逐页 PNG。
- 新建不继承系统 site-packages 的 `agent-service/build/rag-build-venv`，安装
  `rapidocr==3.9.2`、`onnxruntime==1.23.2` 和项目 `rag-build` extra。
- 新增 `LocalRapidOcrProvider` 与环境 resolver；provider 默认关闭，显式支持
  `local-rapidocr` 和原有 `http-json`，普通 Agent runtime 不加载本地 OCR 依赖。
- 新增 DOCX 视觉复核契约与 `record-docx-visual-review`：逐页验证 PNG、尺寸、空白页和
  SHA-256，并用预期 extraction SHA-256 执行 compare-and-set。

真实 DOCX 结果：

- 9/9 DOCX 结构解析成功，9/9 文档、14/14 页面完成 LibreOffice 渲染和人工目检。
- 14 个新构建页面与已目检页面逐页 SHA-256 一致，差异数为 0。
- 5 个尾部空白第 2 页被识别并保留；无裁切、重叠、缺字形或表格布局破损。
- `visualValidationPendingCount` 从 9 降为 0。
- `单晶黄冰糖工艺流程图24.12.docx` 的页内“咖啡调糖”标题冲突继续保留为
  `TITLE_CONFLICT`。
- 最终 DOCX extraction manifest SHA-256：
  `db9643abff1803b76e520cd9a3df81b96a8013f2a6ccdb92b017b556a5837857`。

真实 PDF OCR 结果：

- 使用 `local-rapidocr`、模型
  `rapidocr-3.9.2-ppocrv6-small-ch-onnx-cpu` 和 CPU execution provider。
- 16/16 页 OCR 成功，OCR 待处理数为 0；16/16 页视觉复核成功，视觉待处理数为 0。
- 所有非空识别区域保存文本、置信度和 `[0,1]` 归一化坐标。
- 第 15 页平均置信度约 `0.818`，保留 `OCR_LOW_CONFIDENCE`；人工检查发现低置信区域
  集中在证书缩略图，未擅自修正或提升可信度。
- 同一真实页面跨独立进程重复识别文本一致。
- 最终 PDF extraction SHA-256：
  `af605269166418bcc4fef13a0caa582a9edcbf4598e27b48a77d0a312e11ec2e`。
- 最终 PDF extraction manifest SHA-256：
  `d0665370d34977f03eb9fa7c3bad3dc2bc79f507d810426214be3d1acc8f2717`。

验证命令：

- `python -m compileall -q agent-service/app/rag`
- `python -m pytest -q agent-service/tests/rag/test_docx_parser.py agent-service/tests/rag/test_local_ocr.py`
- `cd agent-service && build/rag-build-venv/Scripts/python.exe -m pytest -q tests/rag`
- `cd agent-service && build/rag-build-venv/Scripts/python.exe -m pytest -q`

验证结果：

- DOCX 复核与本地 OCR 定向测试：`18 passed`。
- RAG 全量测试：`47 passed, 3 skipped`。
- Agent Service 全量回归：`363 passed, 3 skipped`。
- `compileall` 通过。
- 原材料始终只读；真实构建产物和本地模型环境均位于 Git 忽略目录。

遗留风险：

- DOCX 的流程连接关系、控制点标签冲突和内部标题冲突仍需在规范化阶段处理。
- 宣传册仍需产品卡片版面分区、重要企业事实对照和第 15 页低置信区域取舍。
- LibreOffice MSI 返回 `3010`，建议在合适维护窗口重启 Windows；当前命令行渲染已正常，
  不构成继续离线构建的阻塞。

下一步：

- 启动 `RAG-01D` 规范化与内容复核；在内容级质量项关闭前不生成 chunk 或索引。

### 2026-07-31 — RAG-01D 规范化与内容复核开始

状态：`IN_PROGRESS`

目标：

- 从同一完整 release 中消费 9 份已通过视觉核验的 DOCX extraction 和
  1 份已完成本地 OCR/视觉核验的 PDF extraction。
- 生成 10 份可追溯的规范化 document、内容复核 profile、未解决质量报告、
  normalization report 和 corpus manifest 初稿。
- 关闭或明确保留流程关系、标题/控制点冲突、PDF 板块归属与低置信区域。

设计决定：

- 新增 `normalization-and-content-review-design.md`，将 profile 作为人工复核决定的
  版本化、机器可读输入。
- normalizer 不使用 LLM 改写原文、补流程、修正 OCR 或猜测产品范围。
- 视觉/OCR、checksum、页数、profile 绑定和数值保真任一不通过时 fail-closed。
- corpus 本切片最高只到 `VALIDATED`，不切换为 `ACTIVE`，不生成 chunk 或索引。

计划改动范围：

- `agent-service/app/rag/contracts.py`
- `agent-service/app/rag/offline/normalizer.py`
- `agent-service/app/rag/offline/cli.py`
- `agent-service/tests/rag/`
- `docs/agent/rag/`

完成门槛：

- 10/10 document 产物、稳定 checksum 和有效来源定位。
- 关键数值/单位前后一致率 100%，无来源事实数为 0。
- 自动化测试、真实产物审计、UTF-8 无 BOM 和文档/日志回填通过。

### 2026-07-31 — RAG-01D 规范化与内容复核完成

状态：`COMPLETED`

完成内容：

- 扩展 `DocumentContract`、`PageContract`、`ProcessStepContract`、`CorpusManifest`
  和 extraction 契约，增加页面板块、逐页知识域、复核说明、复核/profile 哈希、
  多 CCP/CPP、document SHA-256 和冻结清单字段。
- 新增版本化 `NormalizationReviewProfile`、`NormalizationReport` 及对应文档级/
  页面级复核决定契约。
- 新增 `rag-offline normalize`，在写入前 fail-closed 验证 inventory、source、
  extraction、profile 和全部 DOCX/PDF 页面 PNG SHA-256；已存在产物拒绝覆盖。
- 将 DOCX 多段“工艺描述”按明确步骤号归并，将控制点说明表按人工复核的工艺名称映射到
  对应步骤，避免把表内序号误当作流程步骤号。
- 结构化参数范围、单位、CCP/CPP 和表格设备，同时保留原始 `sourceText`；源文
  “压力温度…℃”只标记疑似错字，不代为修正。
- 对宣传册 16 页逐页写入受控板块、知识域、产品范围、规范化正文、排除文本和复核说明；
  第 15 页低置信证书编号、范围和日期未进入知识事实。
- 新增正式内容复核配置、内容复核报告和真实规范化发布 golden。

正式发布：

```text
agent-service/build/rag-r01d-release-2026-07-31/
  releases/laibin-rag-2026-07-29-v1/
```

正式摘要：

- inventory SHA-256：
  `cefd802a6bbee06ee211c45b1638c57a7c5f03458a5db843ee0ec3649abfd574`
- review profile SHA-256：
  `44da044e3669d1f8585be70f61708029421fb5a3e765c30c88aaafd8179e05ee`
- document manifest SHA-256：
  `4fcf6769da4d725e778fc0cc2193dd25b2822f1c11a4931415847b4d45fd7b2f`

验收结果：

- 10/10 规范化文档、30 页、108 个编号步骤；
- 32 个结构化参数、14 个 CCP/CPP、8 个表格设备字段；
- 5 个源文尾随空白页显式保留；
- `blockingIssueCount == 0`，8 个已复核 warning；
- corpus 状态 `VALIDATED`，未生成 chunk/index，未切换 `ACTIVE`；
- 所有文档角色均为 `ADMIN`、`SUPER_ADMIN`。

验证命令和结果：

- `python -m pytest tests/rag/test_normalizer.py tests/rag/test_contracts.py -q`
  → `9 passed`
- `python -m pytest tests/rag -q`
  → `52 passed, 4 skipped`
- 配置 `LAIBIN_RAG_NORMALIZED_RELEASE` 后执行
  `python -m pytest tests/rag/test_real_normalization_golden.py -q`
  → `1 passed`
- `python -m pytest -q`
  → `369 passed, 4 skipped`
- `git diff --check`
  → 通过；工作树中另有大量既存未提交改动，本切片未回退或覆盖。

保留 warning：

- 单晶黄冰糖文件名与内部标题冲突及源文疑似错字；
- 白砂糖步骤 6 同时出现 `CCP3` 和 `CCP1`；
- 两份流程源文出现“压力温度…℃”；
- 宣传册第 15 页低置信证书缩略图及两处源文重复/疑似错字；
- “糖分装”没有可确认的具体产品范围。

下一步：

- 启动 `RAG-02` 前先登记设计与开发日志；以本次 `VALIDATED` document 为唯一输入，
  实施知识块、关键词索引、向量索引和固定检索评测。
- 在 RAG-02 验收通过前不接入 Agent runtime，不将 corpus 设为 `ACTIVE`。

### 2026-08-01 — RAG-02 知识块、索引与检索评测开始

状态：`IN_PROGRESS`

目标：

- 以 RAG-01D 正式 `VALIDATED` document 为唯一输入生成稳定知识块；
- 建设 SQLite FTS5 中文词法索引和本地中文向量索引；
- 实现 lexical/vector/RRF hybrid 离线检索器；
- 建立 60～100 条固定检索评测并冻结指标结果。

设计决定：

- 新增 `chunking-index-and-evaluation-design.md`；不跨 document、产品、版本、页码或
  步骤切块，参数上下界和单位必须同块。
- RAG-01D 输入 release 保持只读，RAG-02 生成新的隔离 release，不复制 OCR 页面和
  extraction 中间件。
- 词法索引使用 SQLite FTS5；中文离线生成单字/双字和完整业务词，精确保留型号、
  CCP/CPP、数字、范围与单位。
- 向量 provider 使用本地 `fastembed==0.8.0` 和 `BAAI/bge-small-zh-v1.5` ONNX；
  512 维、L2 归一化、CPU 推理，模型文件留在 Git 忽略缓存。
- 本地模型包 SHA-256 为
  `bf023219b6029148fddf764d248808816c0ca1f107f058231bb1ae0fa526f83f`；
  已完成两条中文文本的加载和向量范数冒烟验证。
- 正式构建不接受测试 fake provider；模型或维度不一致时 fail-closed。
- 本阶段最高仍为 `VALIDATED`，不接入 Agent runtime，不设置 `ACTIVE`。

计划改动范围：

- `agent-service/app/rag/contracts.py`
- `agent-service/app/rag/offline/chunker.py`
- `agent-service/app/rag/offline/index_builder.py`
- `agent-service/app/rag/offline/embedding_provider.py`
- `agent-service/app/rag/offline/retrieval_evaluator.py`
- `agent-service/app/rag/offline/cli.py`
- `agent-service/tests/rag/`
- `docs/agent/rag/`

预期验证：

- chunk/index 契约、稳定摘要、边界、FTS5、向量维度和权限单测；
- 正式 release golden；
- 固定评测门槛；
- `python -m pytest tests/rag -q`；
- `python -m pytest -q`；
- UTF-8 无 BOM、乱码扫描和 `git diff --check`。

外部依赖状态：

- SQLite FTS5：本机构建 Python 已验证可用；
- NumPy：`2.5.1`；
- FastEmbed：`0.8.0`；
- ONNX Runtime：`1.23.2`；
- 中文 embedding 模型：已下载并本地 CPU 验证，不再阻塞 RAG-02B。

### 2026-08-01 — RAG-02 知识块、索引与检索评测完成

状态：`COMPLETED`

实施内容：

- 新增确定性 chunker，构建前重算 inventory、review profile、document manifest 和每份
  document 摘要；任何不一致均 fail-closed。
- 生成 196 个 chunk：9 个流程总览、108 个步骤、26 个控制点、23 个物料分支、9 个
  企业、16 个产品、3 个认证和 2 个销售知识块；重复块和空来源均为 0。
- 礼品糖第 12、13 页按产品卡片独立切分；控制点 chunk 自包含产品、步骤说明、设备、
  CCP/CPP、精确数值和单位。
- 新增 SQLite FTS5 中文单字/双字及受控词索引，索引中不接受任意用户 FTS 表达式。
- 新增 `fastembed==0.8.0` 本地 provider，使用 `BAAI/bge-small-zh-v1.5`、512 维、
  L2 `float32` NumPy 矩阵；构建命令要求显式本地模型目录并禁用联网下载。
- 新增 lexical/vector/hybrid 三种检索模式、RRF `k=60`、精确短语和证据类型加权；
  融合版本为 `rrf-control-aware/1.0.2`。
- 角色门禁在检索前执行，允许 corpus manifest 中的 `ADMIN`、`SUPER_ADMIN`；其他角色
  直接 `FORBIDDEN` 且证据为空。向量缺失区分 `DEGRADED`/`UNAVAILABLE`，不伪装为
  `NO_DATA`。
- 新增 83 条固定 JSONL 和机器可读评测报告；加入真实 RAG-02 发布 golden。
- CLI 新增 `build-chunks`、`build-indexes` 和 `evaluate-index`，所有阶段存在目标时拒绝
  覆盖。

首轮评测发现与修正：

- 首轮 82 条中，“白砂糖原料验收 CCP1”正确 document 已排第一，但同文档的普通步骤
  chunk 先于控制点 chunk；未降低门槛。
- 将控制点正文补成自包含证据，并把 CCP/CPP、金属控制、检测阈值和设备型号意图的
  `CONTROL_POINT` 加权固化进版本化融合 manifest。
- 增加 `SUPER_ADMIN` 正向权限用例后为 83 条；重新从冻结 RAG-01D 输入构建正式 release。
- 本轮创建的 4 个草稿/失败/旧规则 Git 忽略目录已在核验绝对路径后删除，不可恢复；
  RAG-01D 输入未触碰，最终只保留唯一正式 RAG-02 目录。

正式输出：

```text
agent-service/build/rag-r02-release-2026-08-01/
  releases/laibin-rag-2026-07-29-v1/
```

冻结摘要：

- chunk manifest：`fff89ef6e272dff99ff1bf94fe5688186ae1847b517f83d7ab90537758337893`；
- lexical index：`22a74909d0e5eb7afbc75f3dc3815e4352bc02efd55f1665a47cab389a47c13d`；
- vector index：`80f5562500bf0a12cac4d88e5886e7c3601a8ae544f8c74df45f657db673ec32`；
- index manifest：`2a36b5a8d15f129453d5041562b1b56bc19dfab49a795b82e4da7a105b94fd83`；
- model artifact：`6b919dcf92ce43b2640c4d27a806d14a632f0659c1cc283abd51058ffed842bc`；
- evaluation set：`658dca252ecc1dff0ef7a24a61c08e62d0b6a5d960b286ff69c6fb0c0c1bc16d`。

正式评测：

- 83/83 通过，失败 0；
- Recall@5、精确产品/控制点、数值 Top3、数值证据和权限边界均为 100%；
- 相似文档禁止项命中数 0，无来源 chunk 0；
- hybrid P95 8.71 ms，门槛为 300 ms。

验证：

- RAG 专项：`59 passed, 5 skipped`；跳过项均为需要显式真实材料/release 环境变量的
  golden，不代表失败。
- RAG-01D + RAG-02 两级真实发布 golden：`2 passed`。
- Agent Service 全量：`380 passed, 5 skipped`。
- 正式构建未使用 fake provider；正式报告状态为 `SUCCEEDED`。

边界：

- corpus 状态仍为 `VALIDATED`，没有创建 `current.json`，没有切换 `ACTIVE`；
- 未接入 Agent runtime、Java 或 Web；未修改业务数据库；
- RAG-03A 必须基于本次 index manifest 实现只读 loader，不能绕过摘要、角色或状态检查。

### 2026-08-01 — RAG-03A Runtime loader 与内部检索开始

状态：`IN_PROGRESS`

目标：

- 只读加载 `current.json` 指向的 RAG-02 验证 release；
- 提供受信角色调用的进程内 `search_approved_knowledge`；
- 固化输入限制、安全 evidence、readiness、超时、降级与审计语义；
- 保证 RAG 默认关闭时现有 Agent 无行为和依赖回归。

设计与文件范围：

- 新增 `runtime-loader-and-internal-search-design.md`；
- 新增 `agent-service/app/rag/runtime/` 的契约、loader、retriever 和 knowledge tool；
- 调整 `Settings`、Agent health/capabilities、observability、runtime optional extra 和部署配置示例；
- 增加 RAG-03A 单元、故障、正式 release golden 和性能测试；
- 不修改 `knowledge_expert`、GoalContract、路由、Java、Web 或业务数据库；
- 不在正式 artifact 创建生产 `current.json`，不把 corpus 从 `VALIDATED` 切换为 `ACTIVE`。

冻结决策：

- 启用模式只认 `<AGENT_RAG_ROOT>/current.json`，不提供任意 release/path 参数；
- pointer 必须绑定 corpus manifest、index manifest、evaluation set 和 evaluation report 摘要；
- loader 接受 `VALIDATED`/`ACTIVE`，前者仅用于工程验收，正式 ACTIVE 切换属于 RAG-05；
- `AGENT_RAG_REQUIRED=true` 且加载失败时应用创建 fail-closed；optional 模式只把知识能力标记
  `UNAVAILABLE`，不影响其他实时业务目标；
- 角色在任何索引或 embedding 调用前校验，仅 `ADMIN`、`SUPER_ADMIN` 可检索；
- 运行时安全输出不含路径、内部 ID、分数、embedding、原始 JSON 或堆栈；
- query embedding 故障只有存在 lexical 证据时才能 `DEGRADED`，否则为 `UNAVAILABLE`。

预期验证：

- RAG-03A 专项 pytest；
- 正式 RAG-02 release 候选 pointer 加载和黄金问题；
- 本地混合检索 P95 不超过 300 ms，端到端不超过 3000 ms；
- Agent Service 全量 pytest；
- `git diff` 与 UTF-8 无 BOM/乱码检查。

### 2026-08-01 — RAG-03A Runtime loader 与内部检索工程验证

状态：`ENGINEERING_TESTS_PASSED`

实施内容：

- 新增冻结的 `current.json`、readiness、检索输入、受信上下文、安全 citation/evidence 和
  结果契约；输入未知字段一律拒绝，模型不能提交角色、路径、索引、documentId 或 chunkId。
- 新增只读 runtime loader：拒绝链接/reparse point 和目录逃逸，实际校验 normalized
  document 数量、schema、ACTIVE 状态、管理员角色和 chunk 引用；重算 corpus manifest、
  chunk/index、FTS5、向量/row map 和 retrieval evaluation report 摘要，校验固定评测门禁。
- pointer 同时绑定 corpus manifest、index manifest、evaluation set 和 evaluation report；
  评测报告被改动但未同步发布摘要时 fail-closed。
- 新增运行时 FTS5/vector/hybrid 检索内核，支持 knowledge domain 多选和已知 product family
  精确过滤；离线 CLI/evaluator 改用同一运行时检索内核，避免后续算法漂移。
- 新增 query-only `FastEmbedQueryProvider`，使用显式本地模型目录且禁止链接和联网下载；
  运行时不提供 document embedding 方法。
- 新增进程内 `search_approved_knowledge`：受信角色在 Pydantic 输入校验、FTS5 和 embedding
  之前执行；仅 `ADMIN`、`SUPER_ADMIN` 可检索，其他角色返回 `FORBIDDEN` 且不触碰索引。
- 输出只含 corpusVersion、queryLabel、knowledgeDomains、opaque evidenceId、标题、最多 800
  字符正文、业务引用和质量标记；不含路径、内部 ID、分数、embedding 或原始 JSON。
- query embedding 故障有可靠 lexical evidence 时为 `DEGRADED`，无可靠证据或纯 vector
  失败时为 `UNAVAILABLE`；超时/并发占用丢弃 evidence，不伪装为 `NO_DATA`。
- 新增 `knowledge_search_total` 与 `knowledge_search_duration`，审计仅记录状态、角色、
  corpus version、输入长度/过滤数量、evidence 数和耗时，不记录问题或证据正文。
- `Settings`、health、capabilities、环境示例和 Docker runtime extra 已接入；默认关闭时不
  导入 NumPy/FastEmbed/ONNX Runtime，也不读取 artifact。
- 在项目隔离的 `agent-service/.venv` 安装 `fastembed==0.8.0`、
  `onnxruntime==1.23.2` 及其运行依赖；`pip check` 为 `No broken requirements found`。

正式语料验证：

- 输入 release：
  `agent-service/build/rag-r02-release-2026-08-01/releases/laibin-rag-2026-07-29-v1`；
- query model：`agent-service/build/rag-models/fast-bge-small-zh-v1.5`；
- golden 只在 pytest 临时目录复制 release 并生成候选 pointer，正式 release 未写入
  `current.json`、未修改、未切换为 `ACTIVE`；
- ADMIN/SUPER_ADMIN 的 5 类真实问题循环 20 次全部 `SUCCEEDED`，STAFF 为
  `FORBIDDEN` 且 evidence 为空；安全字段递归检查通过；
- query embedding + hybrid + 安全映射端到端 P95 为 `11.296 ms`，小于 3000 ms 门槛；
- 机器可读性能记录：
  `agent-service/build/rag-r03a-validation-2026-08-01/real-runtime.xml`。

验证结果：

- `python -m compileall -q app tests/rag`：通过；
- RAG 专项：`95 passed, 5 skipped`；5 个 skip 均为需要其他显式真实材料环境变量的
  RAG-01 golden；正式 RAG-03A golden 本机自动发现冻结产物并已执行，不在 skip 中；
- 正式 RAG-03A runtime golden：`1 passed`；
- Agent Service 全量：`415 passed, 5 skipped, 2 failed`；失败仅为本切片开始前已经存在的
  `GoalDraftV1`、`ResultReasoningDraftV1` 运行时模型与对应 JSON Schema 文件漂移，本切片
  未修改这些模型或 schema；
- 显式 deselect 上述既有参数化 Schema 测试后：`415 passed, 5 skipped, 2 deselected`；
- `git diff --check`：退出码 0，无空白错误；工作区已有 Windows LF/CRLF 提示保留；
- 新增 runtime 源码和设计文档 UTF-8 strict decode、无 BOM、无替换字符测试通过。

边界与下一步：

- 本阶段没有新增 HTTP 知识接口、MCP Tool、`knowledge_expert`、GoalContract、Java/Web
  映射或业务数据库访问；
- corpus 保持 `VALIDATED`，正式发布/原子切换/回滚仍属于 RAG-05；
- RAG-03B 可以基于 `app.state.knowledge_service` 和冻结契约接入唯一权限的
  `knowledge_expert`，不得绕过 loader 或把内部检索结果直接暴露给模型。

### 2026-08-01 — RAG-03B 知识专家、目标、事实和路由接入启动

状态：`IN_PROGRESS`

目标：

- 新增只持有 `search_approved_knowledge` 的 `knowledge_expert`；
- 接入两个知识 GoalContract、同名 FactEnvelope 和 CompletionEvaluator；
- 同时覆盖 deterministic/LLM 路由，并保持实时业务目标归属不变；
- 以确定性安全格式器输出证据和引用，阻断 corpus 提示注入；
- 正确区分 `NO_DATA`、`UNAVAILABLE`、权限拒绝、非法查询和工具失败。

改动范围：

- Python Agent profile、schema、GoalContract、router、argument builder、runtime 和配置；
- Python 自动化测试、机器可读目标登记、RAG README/实施计划和本日志；
- 不修改 Java、Web、仓储 MCP、业务数据库或正式 RAG release；
- 不创建正式 `current.json`，corpus 继续保持 `VALIDATED`。

关键决策：

- 内部知识工具不加入仓储 `ALLOWED_TOOLS`，不改变 53 个仓储工具白名单和 registry hash；
- 受信 `userId/roleCode` 只来自 `ChatRequest.user`，模型不能提供或覆盖；
- PROCESS 与 ENTERPRISE 目标各自固定 knowledge domain，Runtime 不允许模型扩域；
- 知识 evidence 只作为不可信事实，安全格式器负责最终证据摘录与来源，不由 corpus 文本控制行为；
- 未登记静态+实时组合配方时要求拆分，不允许自由 DAG。

设计文档：

- `docs/agent/rag/knowledge-expert-and-agent-integration-design.md`

计划验证：

- `python -m pytest tests/rag/test_agent_knowledge_integration.py`；
- RAG 专项和 Agent Service 全量 pytest；
- schema/registry 一致性、`git diff --check`、UTF-8 无 BOM和乱码检查。

### 2026-08-01 — RAG-03B 知识专家、目标、事实和路由接入完成

状态：`ENGINEERING_TESTS_PASSED`

完成内容：

- 新增 `knowledge_expert`，唯一允许能力为 Python 进程内
  `search_approved_knowledge`，主 Agent 继续保持空工具集；
- 新增 `PROCESS_KNOWLEDGE_QUERY`、`ENTERPRISE_KNOWLEDGE_QUERY` 及对应事实契约，
  GoalContract 总数从 49 增至 51；
- deterministic 与 LLM 两种模式均可路由知识问题，Runtime 固定原始查询、知识域和空产品过滤，
  不接受模型改写身份、范围或产品；
- 生产订单实际消耗、批次质量、库存等实时事实继续进入现有业务路径；没有登记配方的静态+实时
  混合问题要求用户拆分，不生成自由 DAG；
- 知识结果经确定性安全格式器生成引用回答和 `knowledge_evidence` 卡片，不把语料证据交给模型
  进行第二轮行为决策；
- 语料中的 Prompt、工具、SQL、HTTP、认证令牌等指令型文本会被过滤，全部证据不安全时按
  `RAG_UNAVAILABLE` 失败，不调用任何业务工具；
- `SUCCEEDED/DEGRADED` 有证据和权威 `NO_DATA` 可以完成；`UNAVAILABLE`、权限拒绝、非法查询
  和工具失败不能伪装成无数据或完成；
- Python 内部工具登记与 Java/MCP capability snapshot 分离，仓储白名单仍为 53 个工具、
  10 个 Gateway 业务专家；
- 对齐目标/事实 JSON Schema、目标稳定性语料、能力登记、部署配置示例和运行说明。

验证结果：

- `python -m compileall -q app`：通过；
- 知识接入、GoalContract 和 Agent 主测试组合：`222 passed`；
- Agent Service 全量：`430 passed, 5 skipped`；5 项跳过均为依赖外部 provider/release 的既有
  条件测试，不代表失败；
- `git diff --check`：退出码 0，无空白错误；仅保留工作区既有 LF/CRLF 提示；
- 本切片涉及的 29 个源码、配置、测试和文档文件均通过 UTF-8 strict decode，无 BOM、Unicode
  替换字符或常见中文乱码片段；
- corpus 保持 `VALIDATED`，未创建正式 `current.json`，未激活或修改正式 release；
- 未修改 Java、Web、仓储 MCP 或业务数据库。

遗留风险与下一步：

- RAG-03C 继续实现 Java 安全映射、知识调用审计和引用响应透传；
- RAG-04 再实施 Web 引用展示和 ADMIN/SUPER_ADMIN 真实浏览器验收；
- RAG-05 之前不得把建议版本切为正式 `ACTIVE`。

### 2026-08-01 — RAG-03C Java 安全映射、流式事件与审计启动

状态：`IN_PROGRESS`

目标：

- 让 Python 知识回答通过 Java 非流式和 SSE 链路时使用同一显式安全字段；
- 让 Java 审计可追溯知识专家、目标、状态和 corpus version；
- 保持用户身份只来自当前 `LoginUser` 与已校验 Agent session；
- 正确映射成功、降级、无数据、不可用、权限拒绝和非法查询；
- 不记录问题原文、evidence 正文、路径、内部 ID、分数、Prompt 或凭据。

改动范围：

- Python `reviewTrace` 安全知识审计摘要及对应测试；
- Java Python DTO、Runtime Gateway 显式知识卡片映射、SSE 映射和审计；
- Java 单元/集成测试、RAG 设计文档、实施计划、MCP/产品目标和本日志；
- 不修改 Web 展示组件、仓储 MCP、业务数据库、正式 release 或 `current.json`。

关键决策：

- 知识审计复用现有 `agent_tool_audit_log`，`toolName=knowledge_search` 只是审计能力标签，
  不加入 53 个仓储工具白名单；
- 知识卡片只允许 `cardType/title/fields`，field 只允许“内容”“来源”的 `label/value`；
- 流式内部 `audit` 事件只供 Java 消费，不向 Web 转发；
- Java 审计只保存安全元数据，不保存用户查询或证据正文；
- RAG-03C 完成后 corpus 仍保持 `VALIDATED_NOT_ACTIVE`。

设计文档：

- `docs/agent/rag/java-gateway-knowledge-mapping-and-audit-design.md`

计划验证：

- Python RAG/Agent 全量 pytest；
- Java RAG-03C 定向测试和 Maven 全量测试；
- stream/non-stream 契约一致性、`git diff --check`、UTF-8 无 BOM 与乱码检查。

### 2026-08-01 — RAG-03C Java 安全映射、流式事件与审计工程验证

状态：`ENGINEERING_TESTS_PASSED`

实施内容：

- Python Runtime 为确定性与 LLM 两条知识路径生成固定 `reviewTrace.knowledgeAudit`，只含专家、
  Goal、状态、知识域、版本、证据数量和降级标记；每次新知识目标会清理上次检索摘要；
- Java Python 响应 DTO 增加内部 `reviewTrace` 承接，不进入 Web 响应模型；
- Java 对 `knowledge_evidence` 非流式卡片和 SSE card 统一按 `cardType/title/fields` 重建，字段
  仅允许“内容”“来源”的 `label/value`；未知键不透传，允许字段的文本值仍拒绝绝对路径及
  内部 document/chunk/evidence 标识；
- Java 从受信 `LoginUser`、Spring Security authority 和已校验 session 构建 Python 身份；伪造
  `pageContext.roleCode/userId/permissionCodes` 被丢弃；
- 内部 `audit` SSE 只供 Java 解析，不向 Web 转发；完整内部 trace 即使含工具诊断，也只提取
  固定 handoff 和 knowledge audit 元数据；
- 复用 `agent_tool_audit_log` 写入 `knowledge_search` 审计标签，记录 Goal、知识域、状态、corpus
  version、证据数量和耗时，不保存问题原文、evidence、路径、分数、Prompt、token 或原始 JSON；
- 增加 `RAG_UNAVAILABLE` 与 `UPSTREAM_BAD_REQUEST` 的知识专用安全错误提示；没有新增 HTTP
  知识接口、Java/MCP Tool、数据库迁移、Web 组件或业务写操作。

测试与验证：

- Python 定向：
  `python -m pytest tests/rag/test_agent_knowledge_integration.py tests/test_agent_service.py -q`
  → `197 passed`；
- Java 定向：
  `mvn -q '-Dtest=RuntimeRoutingAgentGatewayServiceTest,HttpPythonAgentClientTest' test`
  → `34 passed`；
- Python 全量首次使用系统 Python 时因缺少 `pypdf` 在收集阶段失败；随后混用 bundled
  `PYTHONPATH` 时得到 `429 passed, 5 skipped, 1 failed`，唯一失败原因是该环境缺少
  `fastembed`。切换到此前为 RAG 建立的 `agent-service/build/rag-build-venv` 后执行
  `python -m pytest -q` → `430 passed, 5 skipped`；这些环境失败未通过修改代码或放宽测试规避；
- `mvn -q test` → `301 passed`，0 failure、0 error、0 skipped；输出中的库存历史异常栈为既有
  失败分支测试的预期日志，Maven 退出码为 0；
- RAG-03C 定向测试覆盖身份防伪、非流式/流式白名单、内部 audit 消费、corpus version 审计、
  HTTP `reviewTrace` 反序列化、允许字段内路径/内部标识过滤和 `RAG_UNAVAILABLE` 安全降级；
- `git diff --check` → 退出码 0，无空白错误；仅有工作区既存 LF/CRLF 提示；
- 本切片涉及的 12 个源码、测试和文档文件均通过 UTF-8 strict decode，无 BOM、Unicode 替换
  字符或常见中文乱码片段；
- `agent-service` 全目录未发现 `current.json`。

边界与下一步：

- corpus 继续保持 `VALIDATED_NOT_ACTIVE`，未创建正式 `current.json`，未修改正式 release；
- 仓储 Java Gateway/MCP 仍为 53 个登记工具，`knowledge_search` 只是审计能力标签；
- RAG-04 可开始实施 Web 来源展示、空结果/降级状态和 ADMIN/SUPER_ADMIN 真实浏览器 UAT；
- RAG-05 之前不得激活 corpus 或执行正式原子切换。

### 2026-08-01 — RAG-04 Web 知识引用展示与真实 UAT 启动

状态：`IN_PROGRESS`

目标：

- 在现有 Agent 消息容器中增加安全、可读的知识来源卡片；
- 展示文档标题、页码、步骤/章节和静态资料边界；
- 明确区分正常结果、关键词降级、知识库内无证据和知识库不可用；
- 完成 ADMIN/SUPER_ADMIN 与非管理员的 10 项真实浏览器 UAT；
- 保持 corpus 为 `VALIDATED_NOT_ACTIVE`，不创建正式 `current.json`。

改动范围：

- Web 知识卡片/消息状态纯函数、Vue 展示和前端自动化测试；
- RAG Web/UAT 设计、实施计划、README、本日志和真实验收记录；
- 必要时只在工作区正式运行目录之外建立临时隔离 UAT runtime；
- 不修改仓储 MCP、业务数据库、业务写操作或正式 RAG release。

关键决策：

- 复用 RAG-03C 的 `knowledge_evidence`、标题和“内容/来源”白名单，不扩大上游响应字段；
- 状态只按冻结卡片类型和固定安全文案识别，不对任意模型回答做模糊推断；
- Web 再次拒绝路径、原始 JSON、内部 ID、凭据和异常对象，专用知识卡不得回退到通用字段渲染；
- 收起再打开复用当前页面会话；刷新后验证可重建受信新会话，不虚构跨刷新聊天历史持久化；
- 正式 corpus 发布、原子切换和回滚仍只属于 RAG-05。

设计文档：

- `docs/agent/rag/web-knowledge-citation-and-uat-design.md`

计划验证：

- `node --test src/components/agent/*.test.mjs src/utils/*.test.mjs`；
- `npm run build`；
- 10 项 ADMIN/非管理员真实浏览器 UAT；
- `git diff --check`、UTF-8 无 BOM、乱码和正式 `current.json` 检查。

### 2026-08-01 — RAG-04 Web 工程验证与真实浏览器部分验收

状态：`ENGINEERING_TESTS_PASSED / UAT_PARTIAL_ENV_BLOCKED`

实施内容：

- 新增 `knowledgeCardPresentation.mjs`，只识别 `knowledge_evidence`，只提取“内容/来源”，
  将来源排版为文档标题和页码/步骤/章节定位；
- 增加路径、原始 JSON、document/chunk/evidence/observation ID、凭据、对象和异常长度过滤；
- 不完整或不安全的知识卡从消息可见卡片中整体移除，且专用知识分支不回退到通用字段渲染；
- `AgentBusinessCard` 增加知识引用样式和静态资料边界；
- `AgentMessageBubble` 增加成功、关键词降级、无证据和不可用状态条；
- 现场发现简短 SSE 文案“知识库当前不可用。”未被首版识别，已以精确白名单修复并复验；
- 新增 Web/UAT 设计和十项场景记录，没有修改 Python、Java、MCP、业务数据库或正式 release。

自动化验证：

- 定向知识展示测试：`7 passed`；
- 首轮 Web 全部现有 Node 测试：`61 passed`；
- 首轮 `npm run build`：通过，仅有项目既有 Sass legacy API 和大 chunk 警告；
- 最终 Web 全部现有 Node 测试：`62 passed`；
- 最终 `npm run build`：通过，仅有项目既有 Sass legacy API 和大 chunk 警告；
- 本切片源码和文档通过 UTF-8 strict decode、无 BOM、乱码和 Unicode 替换字符检查；
- `git diff --check` 退出码 0，仅有工作区既有 LF/CRLF 提示；
- `agent-service` 全目录没有 `current.json`。

真实浏览器结果：

- UAT-01 ADMIN 入口和会话连接：通过；
- UAT-07 不可用状态及实时业务边界：通过；
- UAT-08 收起再打开、刷新后安全新建会话：通过；
- UAT-09 STAFF 页面入口隐藏：通过；
- UAT-10 STAFF 伪造 `roleCode/userId` 直接请求：统一 HTTP 响应内业务码 `403`、`data=null`，
  活动 Agent 会话为 0，通过；
- UAT-02～06 因当前 8091 Python 进程没有加载候选 corpus 而阻断，不能用 `UNAVAILABLE`
  冒充知识命中、无答案或关键词降级；
- 完整记录：`docs/agent/rag/web-knowledge-uat-record-2026-08-01.md`。

环境与安全边界：

- 当前没有从进程内存、浏览器存储或日志提取服务密钥，也没有关闭认证；
- 完成剩余 UAT 需要用户提供/配置与 Java 一致的两项本地服务密钥后，重启隔离候选 Python UAT；
- `agent-service` 正式目录仍未创建 `current.json`，corpus 保持 `VALIDATED_NOT_ACTIVE`；
- RAG-04 未标记 `UAT_PASSED/COMPLETED`，RAG-05 不开始正式激活。

### 2026-08-01 — RAG-04 候选 RAG 启用、真实命中与现场缺陷修复

状态：`ENGINEERING_TESTS_PASSED / UAT_8_OF_10_RESTART_BLOCKED`

环境实施：

- 安全读取 `D:\Users\Mrcury\Desktop\laibin-shadow-model.env`，只输出变量名、存在性和长度；
  文件仅提供模型 API Key、数据库 URL、用户名和密码，没有内部服务密钥；
- 重新生成 Java/Python 内部服务密钥、JWT 和实体引用密钥，不输出值，写入用户临时目录并收紧
  ACL；
- 在 `%LOCALAPPDATA%\Temp\laibin-rag04-uat-20260801-a1` 完整复制冻结 release，生成仅用于
  UAT 的临时 `current.json`，确认 0 个 reparse point；
- 重新启动 Java/Python 后健康检查为 `health=UP`、`toolGateway=UP`、`stateStore=UP`、
  `planningMode=DETERMINISTIC`、`rag=READY`，corpus version 为
  `laibin-rag-2026-07-29-v1`；
- 临时 pointer、密钥和进程日志均未写入仓库，正式 `agent-service` 没有 `current.json`。

真实浏览器结果：

- 工艺问题“单晶黄冰糖的完整工艺流程是什么”显示“现行资料”、5 条安全来源和第 1 页/章节，
  `alertCount=0`，无路径、JSON、内部 ID 或校验细节；
- 企业问题“公司有哪些认证和销售网络”显示宣传册第 3、15、14、2 页等认证/销售来源；
- 同会话追问“那企业简介里还记录了什么？”继续显示企业宣传册第 2/3 页来源；
- 与此前已通过的入口、不可用、会话刷新和 STAFF 门禁合计为 `8 PASS / 2 BLOCKED`。

现场缺陷与修复：

- `RAG04-RUNTIME-01`：自然结晶时间和公司成立问法未被有界知识路由覆盖；已增加分类，并只从
  明确句式提取一个产品显示名交给冻结产品族校验；
- 通过真实 release golden 验证“白砂糖 + 企业知识域”候选为空时返回 `NO_DATA`，没有修改冻结
  索引、83 项 Gate C 报告或 release；
- `RAG04-WEB-02`：长知识回答把完整内容写入 1000 字审阅摘要，且 Java 返回完整校验异常；Web
  现按 10000/1000 UTF-16 契约截断，Java 参数校验和非业务异常改为固定安全消息；
- Web 修复已通过 HMR/刷新真实复验；Python 路由、`NO_DATA` 和 Java 全局异常修复已通过自动化，
  但当前服务进程尚未重启加载这些后续修复。

验证结果：

- Python 定向真实 release/集成回归：`15 passed`；
- Python 全量：`433 passed, 5 skipped`；
- Java 全量：`304 passed`，0 failure、0 error、0 skipped；
- Web Node 全量：`65 passed`；
- `npm run build`：通过，仅有既有 Sass legacy API 和大 chunk 警告。

阻断与下一步：

- 本地权限代理因审批额度限制拒绝停止当前 8080/8091 进程；没有使用其他命令或关闭认证绕过；
- 需在可重启窗口停止当前 Java/Python，重新打包 JAR，并以现有受保护临时 env 启动修复后代码；
- 随后完成 UAT-05 浏览器 `NO_DATA` 和 UAT-06 受控 query embedding 降级，再恢复普通 Python；
- RAG-04 不能标记 `UAT_PASSED/COMPLETED`，RAG-05 继续保持 `NOT_STARTED`，corpus 仍为
  `VALIDATED_NOT_ACTIVE`。

### 2026-08-02 — RAG-04 修复后重启、剩余 UAT 与运行时恢复

状态：`COMPLETED / UAT_10_OF_10_PASSED`

重启与配置：

- 用户停止原 8080/8091 进程后，执行 `mvn -q package -DskipTests`，修复后的 Java JAR 打包成功；
- Java 首次启动发现本地环境缺少必填 `WECHAT_APP_SECRET`；仅为本地 UAT 进程设置
  `uat-disabled` 占位值，不修改仓库配置、不调用微信接口；
- 修复后的 Java 与普通 Python 启动成功，统一使用受保护临时 env 和工作区外隔离候选 pointer；
- 健康检查为 `status=UP`、`toolGateway=UP`、`stateStore=UP`、
  `planningMode=DETERMINISTIC`、`rag=READY`，corpus version 为
  `laibin-rag-2026-07-29-v1`。

UAT-05 `NO_DATA`：

- 真实页面输入“白砂糖的企业认证资料是什么”；
- 页面显示唯一“现行资料中未找到可核验内容”状态，说明只代表当前知识库没有证据；
- `statusCount=1`、来源数 0、`alertCount=0`，无 Windows 路径、原始 JSON 或内部 ID；
- 结论：`PASS`。

UAT-06 `DEGRADED`：

- 在工作区外创建临时 `sitecustomize.py`，只替换查询向量方法并抛出受控
  `RAG_QUERY_EMBEDDING_FAILED`，不改业务源码、索引或 release；
- 重启隔离 Python 后健康状态仍为 `UP/READY`，输入“白砂糖生产工艺中金属检测 CCP2 的控制参数
  是什么”；
- 页面显示唯一“关键词检索结果”和“语义检索暂时不可用，以下来源仍可核验”，返回 5 条现行
  资料来源；`alertCount=0`，无路径、JSON 或内部 ID；
- 停止故障 Python，删除临时替身并恢复普通 Python；健康检查重新为 `UP/READY`；
- 恢复后输入“单晶黄冰糖的完整工艺流程是什么”，页面重新显示“现行资料”和 5 条来源；
- 结论：`PASS / FAULT_INJECTION_REMOVED / NORMAL_RUNTIME_RESTORED`。

最终结论：

- RAG-04 固定 10 项真实浏览器场景全部通过；
- Python 433、Java 304、Web 65 项全量测试和 Web 生产构建结果继续有效，修复后 JAR 已重新打包；
- 当前 Java/Python 本地服务运行正常，故障注入文件已删除；
- 正式 `agent-service` 目录仍无 `current.json`，corpus 保持 `VALIDATED_NOT_ACTIVE`；
- RAG-04 标记为 `COMPLETED`，RAG-05 尚未开始正式激活。

验收后运行时交接补记：

- 本轮普通 Java/Python 进程退出后，00:16 检测到另一组新监听：Java PID 38292、Python PID
  29500；两者启动时间晚于本轮验收进程；
- 使用本轮受保护密钥访问该 Python 健康端点返回认证失败，说明它不是本轮配置；没有读取进程
  内存、浏览器存储或日志以获取其密钥；
- 停止两个精确 PID 的请求因可能中断其他线程工作流而被审批门禁拒绝；门禁要求在告知风险后取得
  用户明确授权，未采用替代命令绕过；
- 故障注入文件已删除，正式目录仍无 `current.json`；该事件不推翻已完成的 10 项 UAT，只阻断
  最终本地运行时交接。

并行端口交接补记：

- 用户决定不停止另一工作流的 8080/8091，要求改用其他端口重启；
- 预检查确认 18080/18091 空闲后，以隐藏后台方式启动 Java 18080 和 Python 18091；
- Java 第一次以 PID 40412 直接读取工作区 `target` JAR；其他工作流在其运行期间于 00:51 重新
  写入该 JAR，导致 Logback 延迟类加载出现 `NoClassDefFoundError`，进程退出；
- 检查确认新 JAR 同时包含 `GlobalExceptionHandler.class` 和 Logback 依赖，随后复制到工作区外
  用户临时目录，校验 SHA-256 为
  `B105A707603B6A41C7A12A74819398B55C6DB02B6EDAD2DF925D7B1E680D1C16`，标记只读后启动最终
  Java PID 32512；后续 `target` 重建不再影响该实例；
- Python 启动器 PID 36752、监听 PID 39364；现有 8080/8091 未停止、未重配；
- Java 指向 Python 18091，Python 指向 Java 18080，继续使用受保护临时 env、隔离候选 pointer 和
  本地 embedding；
- 最终健康检查：`status=UP`、`toolGateway=UP`、`stateStore=UP`、
  `planningMode=DETERMINISTIC`、`rag=READY`、`ragEnabled=true`、`ragRequired=true`；
- Java 内部网关探针使用正确服务密钥返回 HTTP 200 和预期 `AGENT_SESSION_NOT_FOUND`，证明认证
  通过且在执行业务查询前被会话门禁拒绝；
- Python `/internal/agent/chat` 同样返回 HTTP 200 和预期 `AGENT_SESSION_NOT_FOUND`，证明
  18091→18080 调用链可用；测试内存会话随后删除；
- Java/Python stdout/stderr 仅写入 `%LOCALAPPDATA%\Temp`，未写入工作区；
- 当前 Web/Vite 仍指向原 8080。本并行实例用于安全交接和后续独立切换，不抢占现有 Web 工作流；
- 最终运行时交接状态：`PARALLEL_RUNTIME_READY_18080_18091`。

### 2026-08-02 — RAG-05 正式 v1 激活、恢复与最终验证

状态：`COMPLETED / FIRST_ACTIVATION_ACTIVE / REAL_V2_ROLLBACK_DEFERRED`

Gate D 收敛：

- 复核确认此前唯一 Python 失败是并行工作流已经更新生产 GoalContract、但对应测试仍保留旧事实
  标签；将该项断言同步到当前生产契约，没有修改运行时行为；
- Python 全量最终为 `446 passed, 5 skipped`；RAG 专项为 `119 passed, 5 skipped`；
- Java 全量为 `308 passed, 0 failed, 0 errors, 0 skipped`；
- Web 直接 Node 全量为 `65 passed`，`npm run build` 通过，仅有既有 Sass legacy API 和大 chunk
  警告；当前 `package.json` 没有 `test:unit` script，未虚构该命令为通过；
- `compileall` 通过；Compose YAML 和 corpus/model 两个只读挂载断言通过。真实
  `deploy/simple/.env` 不存在，因此没有把完整 Compose 启动记录为通过。

正式发布：

- 正式候选再次通过 10 份 document、196 个 chunk、83 条固定评测、23 个文件和全树摘要预检；
- 完整 release SHA-256 为
  `4a7525328af286d8ca7702ddc52c3f857244745bf0db080a84d426c6392456f6`；
- `publish-release` 成功复制不可变 v1 并原子创建 `current.json`，返回
  `pointerChanged=true`、`releaseCopied=true`；
- 发布时间为 `2026-08-02T01:26:59.915161+08:00`，发布审计状态为 `COMMITTED`；
- corpus manifest 保持 `VALIDATED` 且文件为只读；激活状态由 pointer 和发布审计表达；
- 正式运行根目录及 artifact 继续由 Git ignore 排除，没有把索引或 pointer 提交进源码区。

运行态和安全验证：

- 以受保护临时 env、正式 RAG 根目录和本地 embedding 启动 28091 独立 Python Agent，复用
  18080 Java Gateway；原 8080/8091、18080/18091 未停止或重配；
- health 为 `UP`，依赖状态为 `model=BASIC`、`toolGateway=UP`、`stateStore=UP`、
  `planningMode=DETERMINISTIC`、`rag=READY`；capabilities 报告 v1；
- 正式 release golden 验证 ADMIN CCP2 查询返回证据和 v1，STAFF 被拒绝，结果无路径、内部 ID
  或凭据；
- 日志位于工作区外 `C:\tmp`，未发现启动错误或密钥输出。

中断与回滚验证：

- 使用真实 v1 候选和隔离运行根目录在 pointer 替换前注入中断，确认不产生不完整 pointer；同一
  候选续跑成功并复用已核验 release；
- 正式根目录同版本回滚按 `ROLLBACK_TARGET_IS_CURRENT` 失败闭锁，退出码 1；操作前后
  `current.json` SHA-256 均为
  `f6e8f0247160be9542f5a7f942ecddd6222ce5150bce2ee30a9d8d0ac24c5e2b`，审计数量均为 1，
  临时 artifact 为 0；
- 双有效 fixture 的 v2→v1 原子回滚和替换前中断恢复定向测试为 `2 passed`；
- 当前没有真实 v2，没有复制 v1 伪造版本。甲方下次提供完整更新材料时必须补做现场真实
  v2→v1 回滚、重启、readiness、知识冒烟和审计核对。

文档：

- 更新 `README.md`、`implementation-plan.md`、`release-switch-and-rollback-design.md` 和
  `offline-build-runbook.md`；
- 新增 `rag05-release-record-2026-08-02.md`，记录完整摘要、运行态、测试和限制项；
- RAG-05 首次激活完成，后续进入按材料版本触发的离线整库重建和发布维护阶段。

### 2026-08-03 — RAG 当前状态重审与 v2 概念纠偏

状态：`CORRECTED / SOURCE_INTEGRATION_PENDING / ONLINE_DEPLOYMENT_UNVERIFIED`

触发原因：

- 用户指出设计和输入材料中从未存在业务 v2；
- 复核确认此前把自动化测试的 `test-corpus-v2` 延伸成“真实 v2 待办”，属于执行过程中的概念
  漂移；
- 必须重新区分材料处理、工作区实现、artifact 发布、版本库集成和当前在线部署。

v2 纠偏：

- 当前只有甲方提供的 10 份现行材料和一个真实 corpus version；
- `test-corpus-v1`、`test-corpus-v2` 均由相同的白砂糖/红糖简化 fixture 内容生成，仅 version
  标识不同；
- 该测试只验证 pointer、compare-and-set、不可变 release、中断恢复和回滚协议；
- 不存在第二套甲方材料，不存在业务 v2，也不存在当前必须完成的“真实 v2 回滚”里程碑；
- 保留本日志此前的错误历史表述，并以本条追加记录明确纠正，不改写历史。

重新核查结果：

- 原始材料仍为 9 个 DOCX、1 个 PDF；正式 v1 为 10 份 document、196 个 chunk；
- 固定评测状态 `SUCCEEDED`，83/83 通过；候选与正式副本 release SHA-256 均为
  `4a7525328af286d8ca7702ddc52c3f857244745bf0db080a84d426c6392456f6`；
- 正式 pointer、只读 release 和 `COMMITTED` 审计仍完整；
- 当前 Git HEAD `c9e2274` 没有 RAG 集成引用；RAG 源码、测试和文档被 Git 跟踪的文件数为 0，
  RAG 范围共有 74 个未跟踪文件；
- 28091、18080、18091 已停止；当前 8080/8091 在监听，但 8091 使用已知验收密钥返回 401，
  未绕过认证读取其配置；
- 真实 `deploy/simple/.env` 和 `deploy/simple/artifacts/rag-model` 不存在；
- 因此“v1 artifact 已验证”成立，“源码已交付”和“当前在线 RAG 已上线”不成立。

2026-08-03 回归：

- Python 全量：`449 passed, 5 skipped`；
- RAG 专项：`119 passed, 5 skipped`；
- Java 全量：`312 passed, 0 failed, 0 errors, 0 skipped`；
- Web Node：`65 passed`；Web 构建通过；
- `git diff --check` 通过；74 个 RAG 相关文本文件通过 UTF-8 严格解码且无 BOM。

文档：

- 新增 `rag-current-state-audit-2026-08-03.md` 作为当前权威状态基线；
- 更新 README、实施计划、发布/回滚设计、离线 runbook 和 2026-08-02 发布记录；
- 将当前状态统一改为
  `WORKTREE_ENGINEERING_VALIDATED / V1_ARTIFACT_VALIDATED / SOURCE_INTEGRATION_PENDING /
  ONLINE_DEPLOYMENT_UNVERIFIED`。

下一步：

- 从共享 dirty worktree 中准确识别并纳入 RAG 所需源码、测试、Java/Web 集成和部署配置；
- 对确定提交重新回归，再准备真实部署 env、模型目录和当前在线实例验收；
- 完成上述门禁前不得标记整体 `COMPLETED`。

### 2026-08-03 — RAG 源码版本库集成开始

状态：`IN_PROGRESS / MANIFEST_AND_STAGING_BOUNDARY_REVIEW`

目标：

- 把当前已通过工程验证的 RAG 实现转化为可审查、可重建的版本库交付；
- 明确 RAG 专属新增文件、共享文件中的 RAG hunk、依赖文件和禁止纳入项；
- 只暂存边界清晰且属于 RAG 的改动，不夹带其他并行工作流内容。

安全边界：

- 禁止执行 `git add .`、`git add -A` 或按整个仓库暂存；
- 不还原、不覆盖、不重排其他工作流的未提交修改；
- `agent-service/build/**`、`deploy/simple/artifacts/**`、模型、密钥、日志和真实 env 不进入 Git；
- 共享文件必须逐 hunk 核对；无法安全拆分时保持未暂存并记录依赖，不猜测归属；
- 本切片不修改库存数据、不连接生产数据库、不启动或停止当前服务；
- 不创建业务 v2，也不把测试版本当成业务材料版本。

计划文档：

- `docs/agent/rag/rag-source-integration-plan-2026-08-03.md`。

当前事实：

- Git HEAD `c9e2274` 没有 RAG 集成引用；
- RAG 范围当前有 74 个未跟踪文件；
- 当前工作区 Python、RAG、Java、Web 回归均为绿色；
- 工作区同时包含其他 Agent、报表、库存历史和生产功能改动，不能按文件夹外的共享文件整体归入
  RAG。

下一步：

- 生成逐文件交付清单和 shared hunk 风险清单；
- 先纳入 RAG 专属且依赖边界清晰的新增文件，再处理共享集成点；
- 暂存后核对 staged diff，并在日志中记录纳入、保留和阻塞项。

### 2026-08-03 — RAG 源码 A/B 类安全暂存完成

状态：`IN_PROGRESS / A_B_STAGED / SHARED_HUNK_EXTRACTION_PENDING`

完成内容：

- 新增 `rag-source-integration-manifest-2026-08-03.md`，记录 A/B/C/D 四类交付边界；
- RAG 专属目录共 76 个文件，公共目录中的独立新增依赖共 11 个文件；
- 使用显式路径暂存上述 87 个文件，未使用宽泛暂存，未创建提交；
- 44 个已跟踪共享文件保持未暂存，等待按 hunk 拆出 RAG、管理员门禁和安全修复；
- `__pycache__`、`.pyc`、build、artifact、模型、真实 env、日志和其他并行工作均未纳入。

安全与格式检查：

- staged 边界外文件 0，禁止路径 0；
- UTF-8 BOM 0、非法 UTF-8 0、可疑密钥格式 0；
- 第一次 `git diff --cached --check` 发现 Python 文件末尾多余空行和 Markdown 行尾空格；
- 完成纯机械格式清理后，`git diff --cached --check` 通过；
- staged diff 当前为 87 个新增文件、22,541 行新增。

验证：

- RAG 专项：`119 passed, 5 skipped`；
- Web helper：`12 passed, 0 failed`；
- Java 定向：`6 passed, 0 failed, 0 errors, 0 skipped`；
- 首次并行验证的 Maven `-Dtest` 逗号参数未加引号，PowerShell 解析失败；修正参数后复跑通过，
  该失败不属于代码或测试失败。

当前限制：

- A/B 暂存集合依赖尚未暂存的 Python、Java、Web、Deploy 和共享契约 hunk；
- 当前 staged tree 不能独立构建或运行 RAG，不得直接提交；
- 当前在线 8080/8091 的 RAG 配置仍未验证，本切片没有启动、停止或切换服务。

下一步：

- 按 manifest 的 44 个 C 类文件生成最小 cached patch；
- 优先处理依赖最清晰的容器、配置、契约和 Java 映射，再处理高度混合的 `runtime.py`、
  `tool_arguments.py`、`goal_contracts.py` 和 `AgentBusinessCard.vue`；
- 从最终暂存内容构造可验证树并完成全量回归后，再决定是否创建提交。

### 2026-08-03 — RAG 共享 hunk 提取开始

状态：`IN_PROGRESS / SHARED_HUNK_EXTRACTION`

目标：

- 在不覆盖工作区并行改动的前提下，把 44 个 C 类共享文件中的 RAG 必需变更加入暂存区；
- 先处理所有 hunk 均可证明属于 RAG 的低风险文件，再处理同一 hunk 内的混合变更；
- 每批暂存后检查 cached diff、依赖闭包和定向测试，最终构造可独立验证的 staged tree。

第一批候选：

- Python/容器：`agent-service/Dockerfile`、`knowledge.py`、`model.py`、`orchestration.py`、
  `progress.py`、`deploy/simple/agent.Dockerfile`；
- Java：`PythonAgentChatResponseDTO.java`；
- Web：`AgentMessageBubble.vue`。

边界：

- 只有完整 diff 均属于 RAG 的文件才允许整文件暂存；
- 含报表、模型模式、流式诊断或其他业务改动的文件继续按 hunk 处理；
- 不修改、不还原工作区中的非 RAG 内容，不创建提交，不启动或切换服务。

### 2026-08-03 — RAG 源码集成收口与当前提交回归

状态：`SOURCE_INTEGRATED / ENGINEERING_REGRESSION_PASSED / ONLINE_DEPLOYMENT_UNVERIFIED`

完成内容：

- 重新核对共享工作区后确认 `HEAD=4150e6d` 且与 `origin/dev` 一致；
- RAG 离线管线、Runtime、专项测试、设计文档及 Agent/Java/Web/Deploy 集成点已进入该提交；
- `4150e6d` 由共享工作区中的其他任务创建并推送，本线程没有创建、改写或回退提交；
- 提交同时包含 analytics/reporting 基线，提交标题没有单独体现 RAG，本日志补充其交付可追溯性；
- 正式 v1 artifact、模型、真实 env、密钥和运行日志继续按设计保留在 Git 外。

环境处理：

- 仓库原先误跟踪的 `.venv` 已由当前提交移除，系统 Python 初始缺少 `pip` 和 RAG 测试依赖；
- `python -m ensurepip --upgrade` 因 `C:\Python312\Scripts` 无写权限失败；
- 改用 `python -m ensurepip --user --upgrade` 恢复 pip，并以 `--user` 安装 `.[test]` 和
  `.[rag-runtime]`；未在仓库内创建虚拟环境，也未将第三方依赖加入 Git；
- RAG 首次收集因缺少 `pypdf` 失败；补齐 test 依赖后两个真实检索测试因缺少 `fastembed`
  失败；补齐 rag-runtime 依赖后全部通过。这些失败均属于环境依赖缺失，不是代码断言失败。

验证命令和结果：

- `python -m pytest tests/rag -q`：`119 passed, 5 skipped`；
- `python -m pytest -q`：`454 passed, 5 skipped`；
- `mvn -q test`：`316 passed, 0 failed, 0 errors, 0 skipped`；
- `node --test <23 个 src/**/*.test.mjs>`：`67 passed`；
- `npm run build`：通过；仅保留既有 Sass legacy API 和大 chunk 警告；
- 回归开始前 `git status --short` 为空，测试基于 `4150e6d` 执行。

当前限制：

- 2026-08-02 隔离实例的 10/10 UAT 仍是有效历史证据，但发生在 `4150e6d` 形成之前；
- 本阶段没有启动、停止或切换 8080/8091，也没有读取未知认证密钥；
- 当前在线实例是否配置真实 env、只读模型和正式 v1 corpus 仍未验证；
- 只有完成同一提交、同一 artifact 的受控部署和管理员/非管理员验收后，才能标记整体上线完成。

下一步：

- 准备受控部署 env 和只读模型目录；
- 启动独立端口实例，验证认证 health、`RAG=READY`、v1 corpus version、知识冒烟和权限；
- 对确定提交和 artifact 复跑 Web 管理员/非管理员验收并记录部署事实。

### 2026-08-03 — RAG 当前提交隔离部署验证开始

状态：`IN_PROGRESS / CONTROLLED_AGENT_28091`

目标：

- 从当前源码提交和正式 v1 artifact 启动新的隔离 Agent 实例；
- 验证服务密钥认证、health、`RAG=READY`、corpus version、管理员知识检索和非管理员拒绝；
- 不复用未知的 8091 认证密钥，不停止或重配当前 8080/8091 服务。

隔离边界：

- 监听地址固定为 `127.0.0.1:28091`，启动前确认端口空闲；
- 使用 `AGENT_ENV=test`、内存状态、确定性规划和 mock tool gateway，只验证静态知识链路；
- mock tool gateway 不启用免认证，所有内部端点仍要求随机 `X-Agent-Service-Key`；
- 不连接数据库、Redis 或生产系统，不执行仓储写操作，不调用外部模型；
- RAG 使用正式 `deploy/simple/artifacts/rag` 根目录和本地核验模型；
- 临时服务密钥、PID 和 stdout/stderr 仅放在工作区外的 `C:\tmp`，不写入日志或 Git；
- 正式 artifact、pointer、release 和模型只读使用，不执行发布、回滚或内容重建。

前置事实：

- `HEAD=4150e6d` 且与 `origin/dev` 一致；
- 当前 8080、8091 正在监听，28091 空闲；
- 正式 `current.json` 和本地模型目录存在，Compose 模型挂载目录尚未准备；
- 外部 `laibin-shadow-model.env` 存在，但仅登记了模型和数据库变量，没有可复用的
  `AGENT_PYTHON_SERVICE_KEY`；本切片不会读取或打印变量值。

下一步：

- 对正式 release 重跑不可变发布校验；
- 核对模型必需文件并启动 28091；
- 完成认证、能力、检索、权限和日志安全验收。

### 2026-08-03 — RAG 当前提交隔离部署验证完成

状态：`COMPLETED / ISOLATED_RUNTIME_28091_VERIFIED / CURRENT_WEB_DEPLOYMENT_UNVERIFIED`

完成内容：

- 正式 v1 release 在启动前和验收后均通过 10 文档、196 chunks、83 条评测、23 个文件及
  release SHA 校验；release 可写文件 0，无 pointer 临时文件、发布锁或 prepared 审计残留；
- 将构建模型复制到 Git 忽略的 `deploy/simple/artifacts/rag-model`，7 个文件、95,332,206
  字节，逐文件哈希差异 0，可写文件 0，reparse point 0；
- 在 `127.0.0.1:28091` 启动 PID 28764，使用 test/memory/deterministic/mock 隔离配置；
- 无密钥和错误密钥 health 均返回 401；正确密钥返回 `UP`、RAG `READY`、目标 v1；
- ADMIN 工艺知识和数值证据成功，SUPER_ADMIN 冻结无证据用例返回 `NO_DATA`，STAFF 返回
  403，静态知识与实时库存混合问题返回 `needsUserSelection=true`；
- 数值回答包含 `Φ1.5mm`、`Φ2.0mm`、`Φ2.5mm`，成功和无证据响应均未匹配路径、凭据或
  不允许的内部字段；
- 运行时指标记录 ADMIN 成功 2 次、SUPER_ADMIN 无证据 1 次；成功 p95 约 32.956 ms，
  无证据约 2.957 ms；
- 进程仅有 loopback 已建立连接，非回环连接 0；日志中服务密钥和常见凭据模式匹配 0；
- 新增 `rag-controlled-runtime-validation-2026-08-03.md` 保存完整验收事实。

命令问题与纠正：

- 第一次模型复制使用 `Copy-Item -LiteralPath <path>\*`，通配符没有展开，目标保持空并由校验
  报告 7 个缺失文件；随后仅对已验证为空的目标显式枚举复制，最终哈希和只读检查通过；
- 任意“月球仓库”问法没有命中知识目标，因此不作为无证据验收；改用既有冻结 UAT 问法后
  `NO_DATA` 通过；
- 省略“生产工艺”语境的数值问法没有命中知识目标；补全明确静态工艺语境后命中全部冻结数值。

安全与运行边界：

- 临时服务密钥随机生成，文件关闭 ACL 继承且仅授权当前 Windows 用户；密钥值未输出、未写入
  文档或 Git；
- 当前 8080/8091 未停止或重配，28091 验收结束后继续运行；
- 本切片没有连接数据库、Redis、生产系统或外部模型，没有修改仓储业务数据；
- 真实 `deploy/simple/.env` 和当前 Web/Java 到 28091 的完整链路仍未验证。

下一步：

- 准备持久化、受控分发的真实部署 env；
- 在新端口启动 Java 隔离实例并指向 28091；
- 从当前 Web 对同一提交执行 ADMIN/SUPER_ADMIN/STAFF 验收，再决定是否切换 8091。

### 2026-08-03 — 当前提交 Java/Web 隔离全链路验收启动

状态：`IN_PROGRESS / CONTROLLED_JAVA_28080_WEB_5174`

目标：

- 从精确 `HEAD=4150e6d` 导出干净副本，避免当前共享工作区的未暂存改动进入验收构建；
- 在 `127.0.0.1:28080` 启动临时 Java，并通过受控服务密钥接入已验证的 Python 28091；
- 在 `127.0.0.1:5174` 启动同一提交的临时 Web，完成真实浏览器登录、知识问答和权限门禁验收；
- 不停止、不重配现有 8080、8091、5173 服务，不切换正式部署流量。

安全与数据边界：

- 数据库变量仅从甲方提供的本地环境文件注入，不打印值；已确认 URL 指向本机，不连接生产数据库；
- 仅允许 Web 登录、Agent 会话和审计产生本地测试记录，不执行库存、托盘、库位、盘点等仓储业务写操作；
- 禁用 MCP warmup/runtime verification 和可配置定时任务；验收窗口避开 00:00、03:00 固定任务触发点，完成后停止临时 Java/Web；
- Java JWT、内部工具密钥和 Python 服务密钥只在工作区外临时运行目录保存或进程内传递，不写入文档、日志或 Git；
- Python 保持 deterministic/mock tool gateway，仅验证静态 RAG 链路，不调用外部模型和实时仓储工具。

计划验证：

- Java 启动、登录和到 Python 28091 的受认证调用；
- 管理员 Web 静态知识成功、数值证据、无证据和静态/实时混合路由；
- 普通员工 Web Agent 入口不可见且后端接口拒绝；
- 浏览器控制台、服务日志、端口和临时进程边界；
- 验收完成后补齐证据、结果和遗留风险，再决定是否具备正式切换条件。

### 2026-08-03 — 当前提交 Java/Web 隔离全链路验收完成

状态：`COMPLETED / ISOLATED_WEB_5174_VERIFIED / CURRENT_DEPLOYMENT_NOT_SWITCHED`

完成内容：

- 从 `4150e6d` 只导出 `pom.xml`、`src/`、`webpage/` 共 945 个文件，干净构建 Java JAR 成功；
- 在 28080 启动 Java 并指向已验证的 28091，在 5174 启动同提交 Web 并代理到 28080；
- ADMIN 页面入口、会话连接、静态知识 5 张来源卡片、`Φ1.5mm`/`Φ2.0mm`/`Φ2.5mm` 数值、
  `NO_DATA` 安全限定和静态/实时混合拆分均通过；
- STAFF 页面无 AI 助手入口；直接创建会话返回 HTTP 200、统一业务码 403、`data=null`；
- 本地花名册没有 SUPER_ADMIN 账号，未伪造 Web 身份；该角色复用同日 28091 可信上下文验收；
- 浏览器控制台错误 0；验收窗口 Java WARN/ERROR 0；24 个日志/文本证据文件中密钥和凭据模式命中 0；
- Java PID 43200、Web PID 18056 在核对命令行身份后停止；28091 继续运行，现有 8080/8091/5173
  未由本轮停止或重配；
- 新增 `rag-controlled-web-runtime-validation-2026-08-03.md` 保存环境、场景、纠偏和证据事实。

命令问题与纠正：

- 全仓 `git archive` 由 Windows `tar` 解压时遇到中文路径错误；改为在新目录只导出运行所需路径；
- Java 首次使用 7 段 cron 被 Spring 拒绝并在监听前退出；改用有效 6 段表达式和显式 JDK 21；
- 首次会话冒烟使用未登记 scope，未创建会话；改用与 Web 一致的空请求体后成功；
- 首个“来宾白砂糖”问法因产品标签不在正式语料而按精确范围拒绝，未擅自映射；
- “筛网孔径”不是冻结金属检测数值的准确语义；改用“金属检测限值”后命中全部三项数值；
- 数值对话框截图受滚动位置影响没有单独覆盖三项数值，因此以 Playwright DOM 三项各 3 处可见
  命中为主证据，并在验证记录中明确说明。

遗留门禁：

- 持久化真实部署 env 尚未准备；
- 现有 Web/Java/Python 服务尚未切换到本轮验证配置；
- 切换后 health、corpus version、ADMIN/STAFF 和回滚准备冒烟尚未执行；
- 若甲方要求 SUPER_ADMIN Web 现场验收，需要管理员先创建真实测试账号。

## 4. 后续切片

| 切片 | 内容 | 当前状态 |
| --- | --- | --- |
| `RAG-SEC-01` | Agent Web/Java/Python 管理员门禁 | `ENGINEERING_TESTS_PASSED` |
| `RAG-01A` | 离线框架、契约与材料清单 | `COMPLETED` |
| `RAG-01B` | DOCX OOXML 解析与视觉核验 | `VISUAL_QA_PASSED / CONTENT_FLAGS_RETAINED` |
| `RAG-01C` | PDF 渲染、文本层检测与 OCR 适配 | `COMPLETED` |
| `RAG-01D` | 统一规范化和语料冻结 | `COMPLETED / CORPUS_VALIDATED` |
| `RAG-02` | 知识块、关键词索引、向量索引和固定检索评测 | `COMPLETED / CORPUS_VALIDATED` |
| `RAG-03A` | Runtime loader、内部检索契约、权限和审计 | `ENGINEERING_TESTS_PASSED` |
| `RAG-03B` | `knowledge_expert`、目标、事实、路由和安全引用 | `ENGINEERING_TESTS_PASSED` |
| `RAG-03C` | Java 安全映射、知识调用审计和引用响应透传 | `ENGINEERING_TESTS_PASSED` |
| `RAG-04` | Web 管理员展示、审计和真实浏览器验收 | `COMPLETED / UAT_10_OF_10_PASSED` |
| `RAG-05` | 发布、原子切换和恢复机制 | `SOURCE_INTEGRATED / ENGINEERING_REGRESSION_PASSED / V1_ARTIFACT_VALIDATED / ISOLATED_RUNTIME_28091_VERIFIED / ISOLATED_WEB_5174_VERIFIED / CURRENT_DEPLOYMENT_NOT_SWITCHED` |

## 5. 日志模板

```markdown
### YYYY-MM-DD — RAG-XX 标题

状态：`IN_PROGRESS | ENGINEERING_TESTS_PASSED | UAT_PASSED | COMPLETED | BLOCKED`

目标：

- ...

改动范围：

- ...

关键决策：

- ...

验证命令：

- `...`

验证结果：

- ...

遗留风险：

- ...

下一步：

- ...
```
