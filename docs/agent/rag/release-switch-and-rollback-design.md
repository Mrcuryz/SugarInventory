# RAG-05 发布、原子切换与回滚设计

状态：`SOURCE_INTEGRATED / ENGINEERING_REGRESSION_PASSED / V1_ARTIFACT_VALIDATED / ISOLATED_RUNTIME_28091_VERIFIED / ISOLATED_WEB_5174_VERIFIED / CURRENT_DEPLOYMENT_NOT_SWITCHED`
日期：2026-08-02
适用范围：来宾智能仓储 Web Agent 静态知识库的部署发布与恢复

## 1. 目标与边界

本切片把已经通过 RAG-01～RAG-04 的完整语料候选发布到正式运行目录，并证明发布失败不会
破坏当前版本。发布能力只管理 Git 忽略的 RAG artifact 和版本指针，不解析 Office/PDF、不执行
OCR、不生成 embedding、不修改仓储业务数据，也不连接生产数据库。

第一版运行时在进程启动时读取一次 `current.json`，不热加载。指针切换后必须重启 Agent Service
并以 readiness、corpus version、知识检索和审计结果作为生效依据。

## 2. 目录与角色

构建候选：

```text
agent-service/build/rag-r02-release-2026-08-01/
  releases/laibin-rag-2026-07-29-v1/
```

正式运行根目录：

```text
deploy/simple/artifacts/rag/
  current.json
  releases/
    <corpus-version>/
  audit/
```

- `agent-service/build/**` 是离线候选区，不由运行时直接读取。
- `deploy/simple/artifacts/rag/releases/**` 是不可变发布区；同名版本绝不覆盖。
- `current.json` 是唯一激活指针，只包含相对 release 名、摘要、发布时间和发布人标识。
- `audit/**` 保存不含绝对路径和凭据的发布事件。
- 容器以 `./artifacts/rag:/app/rag:ro` 挂载正式运行根目录。
- 本地 embedding 模型使用独立只读挂载，不复制进 corpus release。

## 3. 命令契约

### 3.1 `validate-release`

输入一个明确的完整 release，执行只读预检并输出安全摘要。至少验证：

- 路径存在、为普通目录且不含符号链接、junction 或 reparse point；
- corpus、document、chunk、index 和 evaluation 契约；
- corpus version、document/chunk 数量和 allowed roles；
- chunk/document、index/chunk、evaluation/index 血缘；
- corpus manifest、index manifest、lexical/vector artifact 和 evaluation report 摘要；
- 正式评测 `SUCCEEDED`、全部 case 通过且全部门槛满足。

验证失败只返回固定错误码和安全消息，不写 release 或指针。

### 3.2 `publish-release`

流程：

1. 对源 release 执行完整只读预检。
2. 获取正式根目录的排他发布锁。
3. 核对 `current.json`；已有当前版本时必须提供并匹配 `--expected-current-version`。
4. 将源 release 复制到同文件系统临时目录。
5. 对复制结果重新执行完整预检，防止复制期间源文件变化或落盘损坏。
6. 将临时目录原子改名为 `releases/<corpus-version>`，绝不覆盖同名目录。
7. 将 release 文件设为只读；部署时再由容器只读挂载提供第二层保护。
8. 在运行根目录内创建临时 pointer，刷新落盘后以 `os.replace` 原子替换 `current.json`。
9. 写入不含物理路径的结构化发布审计事件。

首次发布没有旧 pointer 时不要求 expected version。后续更新必须使用 compare-and-set，防止操作者基于
过期状态覆盖其他发布结果。

### 3.3 `rollback-release`

回滚只允许指向正式 `releases/` 中已经存在、重新验证通过的不可变版本。命令必须显式提供当前
版本和目标版本，重新生成 pointer 并原子替换；不删除失败版本，不修改目标 release。

## 4. 原子性与中断语义

- release 先完整复制和验证，最后才从 staging 原子改名进入版本目录。
- pointer 临时文件与 `current.json` 位于同一目录，避免跨文件系统替换。
- 中断发生在 pointer 替换前：旧 `current.json` 保持逐字节不变；staging/temp 可审计后清理。
- 中断发生在 pointer 替换后：新 pointer 已是完整 JSON；其中自带发布时间、发布人和全部摘要，可
  在审计事件尚未落盘时仍确定当前状态。
- 锁文件只用于互斥，不作为当前版本依据。发现遗留锁时 fail-closed，由运维确认没有发布进程后再
  清理，程序不自动猜测锁是否过期。

## 5. 发布门禁

发布前必须同时满足：

- RAG-01～RAG-03 工程与语料门禁通过；
- RAG-04 十项真实浏览器 UAT 通过；
- 正式候选的 83 条固定检索评测全部通过；
- Python、Java、Web 回归通过；
- 正式目录没有同名 release；
- 发布者标识明确；
- 正式运行配置启用 `AGENT_RAG_ENABLED=true` 和 `AGENT_RAG_REQUIRED=true`；
- Agent Service 重启后 readiness 为 `READY` 并报告目标 corpus version。

任何预检、复制后复检、compare-and-set 或运行时 readiness 失败都不得把发布记录为成功。

## 6. 首次上线与回滚验收口径

当前只有一个真实且经甲方材料完整构建的版本 `laibin-rag-2026-07-29-v1`，此前正式目录没有
`current.json`。本阶段不篡改 corpus version，也不人为构造第二个业务语料版本。

因此现场验收分为：

- 实际执行 v1 首次原子激活和新实例只读加载；
- 实际制造“替换前中断”条件，确认当前 pointer 和已激活 v1 不受影响；
- 自动化使用内容相同、版本标识分别为 `test-corpus-v1` 和 `test-corpus-v2` 的完整有效 fixture
  release，验证原子回滚、失败目标不切换以及旧 release 不被覆盖；
- 该自动化只验证 pointer、compare-and-set 和不可变 release 协议，不代表存在第二套甲方材料，
  也不构成当前交付的业务版本待办；
- 将来材料确有更新时，按相同协议生成新的 corpus version，并在当次发布窗口核对更新和恢复能力。

这一区分避免把“机制已验证”错误记录成“真实旧版本现场回滚已完成”。

## 7. 运行与回滚顺序

发布：

1. `validate-release`；
2. `publish-release`；
3. 检查 pointer 和审计事件；
4. 以新进程和正式只读根目录启动 Agent；
5. 检查 health/readiness/corpus version；
6. 执行管理员知识问题和审计抽查；
7. 记录发布结果。

材料更新后的版本回滚（如存在上一真实版本）：

1. 确认当前版本和目标旧版本；
2. `rollback-release --expected-current-version <current-version> --target-version <previous-version>`；
3. 重启 Agent；
4. 检查 readiness、corpus version、冒烟问题和审计；
5. 保留失败的新 release 供复盘。

## 8. 完成标准

- validate、publish、rollback CLI 和单元/故障测试完成；
- Docker 部署配置包含 corpus/model 只读挂载和 RAG env 示例；
- 正式 v1 release 不可变复制成功，`current.json` 原子激活成功；
- 独立端口的新 Agent 实例从正式根目录加载 v1 并通过 readiness、检索和审计验证；
- 中断保护现场验证通过，跨版本回滚自动化通过；
- 测试 fixture 与真实业务材料的边界被准确记录；
- RAG 源码、测试和文档纳入版本控制，可从确定提交重建；
- 当前部署实例通过认证 health、目标 corpus version 和知识冒烟验证；
- 设计、运维手册、开发日志和最终发布记录完整。

## 9. 2026-08-02 实施结论

- 正式候选 `laibin-rag-2026-07-29-v1` 通过完整预检，并以不可变目录复制到
  `deploy/simple/artifacts/rag/releases/`；
- `current.json` 首次原子激活成功，发布审计为 `COMMITTED`；
- 独立 Agent 实例在 28091 端口从正式根目录加载，health 为 `UP`、RAG 为 `READY`，报告的
  corpus version 与 pointer 一致；
- 真实候选在隔离运行根目录完成替换前中断和同版本续跑验证；正式根目录对“目标就是当前版本”
  的回滚请求 fail-closed，pointer、审计和临时文件状态均未变化；
- 两个内容相同、版本标识不同的完整 fixture 已完成原子回滚，证明旧 release 保留且回滚目标会
  重新校验；
- 全量 Python、Java、Web 回归及 RAG 专项测试均通过；
- 当前真实语料始终只有 v1；测试中的版本编号不表示第二套甲方材料。

实施证据和完整哈希见 `rag05-release-record-2026-08-02.md`。

## 10. 2026-08-03 状态纠偏

2026-08-03 重新核查确认：

- 正式 v1 artifact、pointer 和 `COMMITTED` 发布审计仍完整且通过校验；
- RAG 源码、测试和文档已进入 `4150e6d`，集成后全量回归通过；
- `deploy/simple/artifacts/rag-model` 已准备为 7 个逐文件哈希一致的只读模型文件；
- 当前提交在 28091 从正式 v1 根目录启动，认证 health、`RAG=READY`、corpus version、管理员
  成功/无证据、STAFF 拒绝及安全检查通过；
- 28091 使用隔离 test/memory/deterministic/mock 配置，没有连接数据库、Redis 或外部模型；
- 从精确 `4150e6d` 导出干净 Java/Web，在 5174→28080→28091 完成 ADMIN 数值、无证据、
  静态/实时混合路由和 STAFF 双层门禁真实浏览器验收；
- 28080/5174 验收后停止，28091 保持运行；当前 8080/8091/5173 未由本轮停止或重配；
- RAG-05 的源码、artifact、隔离 Python 和隔离 Web 全链路门禁已关闭；真实部署 env、现有服务
  切换和切换后冒烟门禁仍未关闭。

当前权威状态见 `rag-current-state-audit-2026-08-03.md`，隔离实例证据见
`rag-controlled-runtime-validation-2026-08-03.md` 和
`rag-controlled-web-runtime-validation-2026-08-03.md`。
