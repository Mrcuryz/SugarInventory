# RAG 当前真实状态审计

状态：`AUTHORITATIVE_CURRENT_BASELINE`
审计日期：2026-08-03
时区：`Asia/Shanghai`
审计方式：只读核对工作区、Git、原始材料、artifact、进程、配置、测试和既有文档

## 1. 结论

当前 RAG 已完成真实材料的离线处理、本地工作区实现、固定评测、隔离浏览器 UAT 和 v1 artifact
发布验证，但尚未完成源码版本库集成和当前在线部署验收。因此不得标记为“整体完成上线”。

权威状态为：

```text
WORKTREE_ENGINEERING_VALIDATED
V1_ARTIFACT_VALIDATED
SOURCE_INTEGRATION_PENDING
ONLINE_DEPLOYMENT_UNVERIFIED
DOCUMENTATION_CORRECTED
```

## 2. 已完成且证据有效的部分

### 2.1 材料与语料

- 原始材料目录存在，共 10 份文件：9 个 DOCX、1 个 PDF；
- corpus version 为 `laibin-rag-2026-07-29-v1`；
- corpus 状态为 `VALIDATED`；
- 10 份 document、196 个 chunk；
- allowed roles 为 `ADMIN`、`SUPER_ADMIN`；
- 不存在第二套真实材料或业务 v2。

### 2.2 索引与固定评测

- 固定评测 83/83 通过，失败 0；
- Recall@5、访问边界、精确产品与控制点、数值证据和 Top3 数值准确率均为 1.0；
- 相似文档混淆数和缺失 source text 数均为 0；
- 评测 p95 为约 8.71 ms；
- 候选和正式副本的 release SHA-256 均为
  `4a7525328af286d8ca7702ddc52c3f857244745bf0db080a84d426c6392456f6`。

### 2.3 正式 artifact

- `deploy/simple/artifacts/rag/current.json` 存在并指向 v1；
- pointer 发布时间为 `2026-08-02T01:26:59.915161+08:00`；
- 发布审计状态为 `COMMITTED`、动作为 `PUBLISH`；
- 正式 release 为只读，再次执行完整 `validate-release` 通过；
- 正式目录只有一个真实 release。

### 2.4 当前工作区工程验证

2026-08-03 重新执行：

- Python 全量：`449 passed, 5 skipped`；
- RAG 专项：`119 passed, 5 skipped`；
- Java 全量：`312 passed, 0 failed, 0 errors, 0 skipped`；
- Web Node：`65 passed`；
- Web `npm run build`：通过，仅有既有 Sass legacy API 和大 chunk 警告；
- `git diff --check`：通过；
- RAG 相关 74 个文本文件通过 UTF-8 严格解码且无 BOM。

这些结果证明当前工作区实现具备工程可用性，不证明 clean checkout 或当前在线服务已经具备该能力。

## 3. 尚未完成的部分

### 3.1 源码版本库集成

- 当前 Git HEAD 为 `c9e2274`；
- Git HEAD 在 Agent、Java、Web 和简单部署目录中没有 RAG 集成引用；
- `agent-service/app/rag`、RAG 测试和 RAG 文档被 Git 跟踪的文件数为 0；
- 当前 RAG 范围有 74 个未跟踪文件，另有部署示例和 Compose 的已跟踪修改；
- 当前共享工作区还包含其他并行功能改动，不能把整个 dirty worktree 直接视为 RAG 交付物。

结论：换机、清理工作区或从当前 HEAD 构建时无法重现 RAG，源码交付尚未完成。

### 3.2 当前在线部署

- 2026-08-02 的 28091 隔离实例曾以 `UP/READY` 加载 v1并完成验收；
- 2026-08-03 复核时 28091、18080、18091 均已停止；
- 当前 8080 和 8091 正在监听，但 8091 使用已知验收密钥返回 401，不能确认其配置或 corpus；
- 没有读取进程内存、环境或日志绕过认证；
- `deploy/simple/.env` 不存在；
- `deploy/simple/artifacts/rag-model` 不存在；
- 本地构建模型仍存在于 Git 忽略的 `agent-service/build/rag-models/fast-bge-small-zh-v1.5`。

结论：当前没有一个可由本审计凭据验证为加载正式 v1 的在线 RAG 实例。

### 3.3 当前快照验收可追溯性

- RAG-04 曾在隔离环境完成 10/10 浏览器 UAT；
- 该结果是有效历史证据；
- 由于 RAG 源码未进入 Git，且当前工作区测试数量已继续变化，历史 UAT 未绑定可重建提交；
- 当前部署完成后仍需对确定提交和确定 artifact 重新执行受控验收。

## 4. 测试版本纠偏

用户没有提供、也没有要求真实 v2 材料。自动化中的 `test-corpus-v1` 和 `test-corpus-v2`：

- 都由相同的白砂糖/红糖简化测试文档生成；
- 内容相同，版本标识不同；
- 只用于验证 pointer、compare-and-set、不可变 release、中断恢复和回滚协议；
- 不属于业务知识库，不进入正式 release，不构成当前项目待办。

未来材料确有更新时，按照整库重建规则生成新的 corpus version 即可；这属于通用运维流程，不是
当前缺失的“v2 阶段”。

## 5. 正确的交付收口顺序

1. 纠正文档中的完成状态和业务 v2 表述；
2. 从共享 dirty worktree 中识别 RAG 所需源码、测试、Java/Web 集成和部署配置；
3. 在不混入其他功能的前提下纳入版本控制，并记录确定提交；
4. 从该提交运行 Python、RAG、Java、Web 和构建回归；
5. 准备真实部署 env 和只读模型目录；
6. 启动受控 Agent 实例并验证认证 health、`RAG=READY`、v1 corpus version 和知识冒烟；
7. 对同一提交和 artifact 执行管理员/非管理员 Web 验收；
8. 记录部署事实后，才能将整体状态改为 `COMPLETED`。

## 6. 状态解释

- `V1_ARTIFACT_VALIDATED` 只表示语料发布目录和 pointer 完整，不等于进程在线；
- `WORKTREE_ENGINEERING_VALIDATED` 只表示当前工作区测试通过，不等于代码已提交；
- 历史隔离 UAT 通过不等于当前 8091 已加载 RAG；
- 当前没有业务 v2，也没有真实 v2 待办。
