# 智能仓储 RAG 文档索引

状态：`SOURCE_PRESENT / FORMAL_ARTIFACT_AND_MODEL_MISSING / REQUIRED_FAIL_CLOSED / OPTIONAL_BROWSER_DEGRADED_PASS / PRODUCTION_NO_GO`
基线日期：2026-08-12
适用范围：来宾智能仓储 Web Agent 的静态知识检索

## 1. 目标

本目录记录 RAG 从材料治理、离线加工、知识索引、运行时检索到验收上线的完整设计和开发事实。

当前材料位于：

```text
D:\Users\Mrcury\Desktop\laibin RAG
```

该目录中的原始材料由甲方提供，并已明确为当前现行版本。原始材料不在用户问答时解析；系统只使用离线处理后生成的规范化知识和检索索引。

## 2. 文档清单

| 文档 | 用途 | 当前状态 |
| --- | --- | --- |
| [implementation-plan.md](implementation-plan.md) | 完整技术路线、工作分解、依赖、测试和发布门禁 | 计划基线已完成 |
| [offline-ingestion-design.md](offline-ingestion-design.md) | 离线加工、发布、更新和运行时边界 | DOCX/PDF 抽取及规范化已完成 |
| [corpus-and-index-contract.md](corpus-and-index-contract.md) | 语料、知识块、索引和引用契约 | extraction/document/chunk/index 契约已实现 |
| [offline-build-runbook.md](offline-build-runbook.md) | 离线依赖、命令、安全边界和故障处理 | RAG-01/RAG-02 完整离线命令已记录 |
| [office-and-local-ocr-environment.md](office-and-local-ocr-environment.md) | Office/LibreOffice 与 OCR 选型、配置和验收 | 已实施并通过真实材料验证 |
| [normalization-and-content-review-design.md](normalization-and-content-review-design.md) | RAG-01D 规范化、内容复核、质量分类和语料冻结 | 已实施并通过 |
| [normalization-content-review-report.md](normalization-content-review-report.md) | 10 份材料逐文档复核结论、冻结哈希和遗留 warning | 已完成 |
| [chunking-index-and-evaluation-design.md](chunking-index-and-evaluation-design.md) | RAG-02 切块、FTS5、本地向量、RRF 和固定评测设计 | 已实施并通过 |
| [runtime-loader-and-internal-search-design.md](runtime-loader-and-internal-search-design.md) | RAG-03A 只读加载、检索契约、权限、故障语义和审计设计 | 已实施并通过工程验证 |
| [knowledge-expert-and-agent-integration-design.md](knowledge-expert-and-agent-integration-design.md) | RAG-03B 知识专家、GoalContract、路由、事实和引用设计 | 已实施并通过工程验证 |
| [java-gateway-knowledge-mapping-and-audit-design.md](java-gateway-knowledge-mapping-and-audit-design.md) | RAG-03C Java 安全映射、流式事件、知识审计和错误语义 | 已实施并通过工程验证 |
| [web-knowledge-citation-and-uat-design.md](web-knowledge-citation-and-uat-design.md) | RAG-04 Web 引用、状态、安全过滤和真实浏览器 UAT | 已完成，10 项 UAT 全部通过 |
| [web-knowledge-uat-record-2026-08-01.md](web-knowledge-uat-record-2026-08-01.md) | RAG-04 十项真实浏览器场景、缺陷和环境门禁记录 | 10 项通过，故障注入已撤销并恢复普通 RAG |
| [release-switch-and-rollback-design.md](release-switch-and-rollback-design.md) | RAG-05 预检、不可变发布、原子指针、回滚和首次上线验收口径 | 源码和 v1 artifact 已验证，当前在线部署待验证 |
| [rag05-release-record-2026-08-02.md](rag05-release-record-2026-08-02.md) | 2026-08-02 v1 发布与隔离运行实例验证的历史事实记录 | 历史验证通过；不代表当前在线状态 |
| [rag-current-state-audit-2026-08-03.md](rag-current-state-audit-2026-08-03.md) | 重新核对 Git、artifact、进程、测试、部署和文档后的真实状态 | 当前权威状态基线 |
| [rag-controlled-runtime-validation-2026-08-03.md](rag-controlled-runtime-validation-2026-08-03.md) | 当前提交在 28091 的认证、RAG、权限和安全验收记录 | 隔离 Python 运行态通过；后续 Web 结果见下一记录 |
| [rag-controlled-web-runtime-validation-2026-08-03.md](rag-controlled-web-runtime-validation-2026-08-03.md) | 当前提交在 5174→28080→28091 的真实浏览器、数值、无证据、混合路由和角色门禁记录 | 隔离 Web 全链路通过；当前部署未切换 |
| [rag-source-integration-plan-2026-08-03.md](rag-source-integration-plan-2026-08-03.md) | 将 dirty worktree 中的 RAG 实现安全纳入版本控制 | 已集成到 `4150e6d`，工程回归通过 |
| [rag-source-integration-manifest-2026-08-03.md](rag-source-integration-manifest-2026-08-03.md) | RAG 专属文件、外围依赖、共享 hunk 和禁止纳入项清单 | 集成清单已收口；部署 artifact 继续排除在 Git 外 |
| [rag-current-artifact-and-runtime-reaudit-2026-08-12.md](rag-current-artifact-and-runtime-reaudit-2026-08-12.md) | 当前 artifact、模型、部署保护、降级链路与恢复门禁复审 | 当前权威状态；正式 artifact/model 缺失，生产 `NO_GO` |
| [evaluation-plan.md](evaluation-plan.md) | 检索、回答、权限和回归评测方案 | extraction/document/index golden 与固定检索评测已建立 |
| [development-log.md](development-log.md) | RAG 设计与开发的追加式事实日志 | 已建立 |

后续新增设计时，应先在本文件登记，再创建独立文档。不得只在聊天记录、代码注释或开发者个人笔记中保留关键设计。

## 3. 已确认业务决策

1. 甲方提供的全部材料视为当前现行版本。
2. 材料未提供生效日期时不补录、不推断，也不阻塞处理。
3. 当前不建设负责人、审核人、审批流和文档替代关系，但数据结构预留扩展字段。
4. Agent 当前产品范围是 Web 管理员使用，第一版管理员角色为 `ADMIN` 和 `SUPER_ADMIN`。
5. 文档解析、OCR、规范化、切分和文档向量化均为离线一次性处理。
6. 运行时不得重新解析 DOCX/PDF、执行 OCR 或补生成文档向量；运行时只允许执行权限过滤、查询向量化和只读检索。
7. 查询向量化只是检索用户问题，不属于重复处理甲方材料。
8. 材料更新时重新执行完整处理和评测，生成新的整库版本后原子切换。

## 4. 强制维护规则

- 任何 RAG 设计变更必须先更新或新增设计文档。
- 任何代码实施切片开始前，必须在 `development-log.md` 登记 `IN_PROGRESS`。
- 实施切片完成、暂停或阻塞时，必须追加日志，不得删除或覆盖历史记录。
- 日志必须记录改动范围、关键决策、验证命令、验证结果、遗留风险和下一步。
- 只有代码、测试、文档和验收共同支持时，才能把能力标记为 `COMPLETED`。
- 文档完成不等于运行时能力已实现。
- 第一版只提供静态知识查询，不增加任何业务写操作。
- RAG 不能替代库存、库位、托盘、化验和生产进度等实时 MCP 工具。
- `RAG-SEC-01` 管理员门禁已通过工程自动化测试；`RAG-04` 真实浏览器已验证 ADMIN 入口和 STAFF 入口隐藏、直接 API 拒绝，剩余验收与角色门禁无关。
- 所有源码、配置和文档保持 UTF-8 无 BOM。

## 5. 状态定义

| 状态 | 含义 |
| --- | --- |
| `DESIGN_ONLY` | 已有设计，尚未开始代码实施 |
| `IN_PROGRESS` | 已登记开发切片，正在实施 |
| `ENGINEERING_TESTS_PASSED` | 代码和自动化测试已通过，尚未完成真实 Web 验收 |
| `UAT_PASSED` | 已完成管理员真实 Web 场景验收 |
| `COMPLETED` | 文档、代码、测试、权限、审计和验收全部完成 |
| `BLOCKED` | 存在明确阻断条件，日志中必须记录原因 |

## 6. 与现有 Agent 架构的关系

- 主 Agent 继续负责用户目标理解、上下文、安全边界和最终回答。
- RAG 规划增加独立的 `knowledge_expert`，不把知识检索权限扩散到现有业务专家。
- `knowledge_expert` 第一版只持有只读知识检索能力。
- 实时业务事实继续通过现有 MCP 和 Java Internal Agent Gateway 获取。
- 同时需要静态知识和实时事实的问题，后续只能通过已登记配方组合，不允许模型自由创建跨专家 DAG。
- 普通回答只展示业务内容和来源，不展示文件系统路径、索引内部 ID、embedding、原始 JSON 或检索调试数据。
