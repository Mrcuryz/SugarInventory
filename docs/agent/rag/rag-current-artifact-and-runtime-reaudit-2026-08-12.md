# RAG 当前 artifact、部署与运行时复审

状态：`SOURCE_PRESENT / FORMAL_ARTIFACT_AND_MODEL_MISSING / REQUIRED_FAIL_CLOSED / OPTIONAL_BROWSER_DEGRADED_PASS / PRODUCTION_NO_GO`

日期：2026-08-12

## 1. 结论

2026-08-02 至 2026-08-04 的记录能证明当时的 v1 发布、隔离运行和浏览器验收通过，不能证明
2026-08-12 工作区仍保有同一份正式 artifact。当前真实目录是：

- `deploy/simple/artifacts/` 只有 `.gitkeep`，没有 `rag/current.json`、release 或 `rag-model`；
- `agent-service/build/` 只有 Python 打包目录，没有历史离线候选和模型源；
- RAG 原始材料按项目约定由仓库外部保管和交付，不属于本地仓库应提交内容；历史记录中的 `D:\Users\Mrcury\Desktop\laibin RAG` 只代表当时构建机路径；
- 当前尚未收到可按历史摘要核验的正式 v1 release、匹配模型或完整原材料交付包。

因此不能以测试 fixture 代替外部材料、手写 pointer，或继续宣称当前本地 `RAG=READY`。当前生产发布结论是
`NO_GO`；正式材料/不可变 release 与匹配模型交付前，只能验证源码、发布保护、必需模式失败关闭和可选模式安全降级。

## 2. 历史恢复身份

如从备份或原构建机恢复，必须逐项核对而不是只看目录名：

| 对象 | 历史已验证身份 |
| --- | --- |
| corpus version | `laibin-rag-2026-07-29-v1` |
| 文档 | 10（DOCX 9、PDF 1） |
| chunks | 196 |
| 固定评测 | 83/83 |
| release 文件 | 23 |
| release 大小 | 1,491,225 bytes |
| release SHA-256 | `4a7525328af286d8ca7702ddc52c3f857244745bf0db080a84d426c6392456f6` |
| corpus manifest SHA-256 | `3951fafadf06fd1c8837a321d96d68ed171b08d533367141d55b00a92d045ddf` |
| index manifest SHA-256 | `2a36b5a8d15f129453d5041562b1b56bc19dfab49a795b82e4da7a105b94fd83` |
| evaluation report SHA-256 | `f620d0c82359df5f44b21dbbfdb114eb3a99f3807198854f8ab23d27e1e8013c` |
| evaluation set SHA-256 | `658dca252ecc1dff0ef7a24a61c08e62d0b6a5d960b286ff69c6fb0c0c1bc16d` |
| 模型 | 7 个文件、95,332,206 bytes |

上述数字来自当时发布记录，只是恢复验真基准，不表示当前文件存在。

## 3. 本轮修复

1. 简单部署打包不再删除整个 `agent-service/build`，仅清理本次 Python 打包目录，避免误删独立的
   `build/rag` 和 `build/rag-models` 恢复源。
2. 新增 `validate-runtime` 离线命令，按正式 pointer、release、评测、索引和本地模型契约加载，
   只输出安全摘要。
3. `deploy/simple/update.sh` 在 RAG 启用时要求 `AGENT_RAG_REQUIRED=true`，检查 pointer 和三项
   模型必需文件，并在 Compose 启动前执行容器内 `validate-runtime`；失败不迁移、不切换服务。
4. 保持 corpus/model 两个只读挂载，并新增部署契约测试。
5. LLM 模式对已由有界静态知识分类器确认的问题执行安全路由纠偏，只能进入
   `knowledge_expert + PROCESS/ENTERPRISE_KNOWLEDGE_QUERY`，不能误入产品解析或实时化验工具。
6. `RAG_UNAVAILABLE` 在当前调用内立即结束并返回专用降级响应，同时保留未完成 Goal 与
   `knowledge_search` 审计；审计从锁定的 GoalContract 恢复固定 knowledge domain。

第 5 项不是普通业务关键词 Router：它只覆盖既有、已登记的静态知识边界，仍由知识专家决定并
调用唯一进程内知识工具；混合实时问题继续要求拆分，实时化验结果也不会进入 RAG。

## 4. 自动化验证

- RAG 专项：`122 passed, 8 skipped`；
- 其中 8 个 skip 都需要当前缺失的真实 DOCX/PDF、规范化 release、索引、active release 或模型；
- 定向路由/运行时/发布/部署合同：`35 passed`；
- 源码与 `agent-service/build/lib/app/runtime.py` SHA-256 一致；
- `git diff --check` 无 whitespace error。

skip 不能算作正式 corpus 通过；它们正是本轮 `FORMAL_ARTIFACT_AND_MODEL_MISSING` 的自动化表现。

## 5. 真实故障注入与浏览器验收

使用用户授权的 shadow env，仅在进程内加载配置：

- `AGENT_RAG_ENABLED=true / AGENT_RAG_REQUIRED=true`：Python 启动因
  `RAG_ROOT_MISSING` 退出，没有监听端口，证明必需模式 fail-closed；
- `AGENT_RAG_ENABLED=true / AGENT_RAG_REQUIRED=false`：Java/Python 可启动，Python 明确记录
  `RAG_ROOT_MISSING required=False`；
- Chromium 输入“白砂糖金属检测限值是什么？”后，处理链路显示“检索现行知识材料”，页面显示
  “知识库暂不可用”，没有返回虚构的金属限值，也没有伪装成 `NO_DATA`；
- 干净导航后浏览器 console 为 0 error；
- shadow 审计为 `knowledge_search / ERROR / RAG_UNAVAILABLE`，goal 为
  `PROCESS_KNOWLEDGE_QUERY`、domain 为 `PROCESS`、evidenceCount 为 0；最近记录没有产品解析、
  库存或化验业务工具。

本轮只产生临时登录用户、Agent 会话、复核和审计；没有库存、库位、托盘、化验、生产或报表业务写入。

## 6. 正式恢复和发布门禁

恢复顺序必须是：

1. 从可信备份或原构建机取回完整原材料，或取回不可变 v1 release 与匹配的本地模型；
2. 对 release 执行 `validate-release`，核对本文件第 2 节全部身份；
3. 如果只能恢复原材料，按 runbook 整库重建，不局部拼接、不覆盖同名 v1；
4. 将经核验 release 通过 `publish-release` 原子发布，模型独立放入 `artifacts/rag-model`；
5. 执行新增 `validate-runtime`，再以 required 模式启动隔离实例；
6. 重跑 health/capabilities、83/83 固定评测、ADMIN 数值证据、`NO_DATA`、混合问题、STAFF 门禁、
   引用安全、Java 审计和真实 Chromium；
7. 演练回滚后，才可单独发起生产发布审批。

缺少可信 artifact 或模型时，禁止以测试 fixture、历史文档中的 SHA 字符串、空 pointer 或模型名称
代替文件本身。
