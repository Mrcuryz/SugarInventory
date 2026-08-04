# RAG 当前真实状态审计

状态：`AUTHORITATIVE_CURRENT_BASELINE / SOURCE_INTEGRATED / ISOLATED_RUNTIME_28091_VERIFIED / ISOLATED_WEB_5174_VERIFIED / CURRENT_DEPLOYMENT_NOT_SWITCHED`
审计日期：2026-08-03
时区：`Asia/Shanghai`
审计方式：只读核对工作区、Git、原始材料、artifact、进程、配置、测试和既有文档

## 1. 结论

当前 RAG 已完成真实材料的离线处理、源码版本库集成、固定评测和 v1 artifact 发布验证，并已
基于集成后的当前提交完成全量工程回归、28091 隔离 Python 运行态验收及
5174→28080→28091 隔离真实浏览器全链路验收；现有 Web/Java/8091 尚未切换到该配置。因此不得
标记为“当前部署已整体上线”。

权威状态为：

```text
V1_ARTIFACT_VALIDATED
SOURCE_INTEGRATED
ENGINEERING_REGRESSION_PASSED
ISOLATED_RUNTIME_28091_VERIFIED
ISOLATED_WEB_5174_VERIFIED
CURRENT_DEPLOYMENT_NOT_SWITCHED
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

- Python 全量：`454 passed, 5 skipped`；
- RAG 专项：`119 passed, 5 skipped`；
- Java 全量：`316 passed, 0 failed, 0 errors, 0 skipped`；
- Web Node：`67 passed`；
- Web `npm run build`：通过，仅有既有 Sass legacy API 和大 chunk 警告；
- 回归开始前 `git status --short` 为空，测试基于当前 `HEAD=4150e6d` 执行；
- RAG 专项首次收集因系统 Python 缺少 `pypdf` 失败，补齐项目声明的 `test` 依赖后为
  `117 passed, 5 skipped, 2 failed`；两个失败均为缺少 `fastembed`，补齐 `rag-runtime` 依赖后
  复跑为 `119 passed, 5 skipped`。

这些结果证明当前提交中的源码具备工程可用性，不证明当前在线服务已经加载该能力。

## 3. 已收口与尚未完成的部分

### 3.1 源码版本库集成已收口

- 当前 Git HEAD 为 `4150e6d`，并与 `origin/dev` 一致；
- RAG 离线管线、Runtime、专项测试、设计文档及 Agent/Java/Web/Deploy 集成点均已进入该提交；
- 当前提交包含 81 个匹配 RAG 专属目录或关键集成点的路径；
- 工程回归开始前工作区和索引均为空；
- 该提交由共享工作区中的其他任务创建并推送，提交标题为 analytics/reporting 基线，未单独体现
  RAG 集成；本审计只记录事实，不重写或回退该提交。

结论：RAG 源码交付已可由当前提交重建；Git 忽略的 v1 artifact 和本地模型仍需按发布记录及部署
手册单独供应，这是既定部署边界，不属于源码缺失。

### 3.2 隔离运行态与当前 Web 部署

- 从 `4150e6d` 在 `127.0.0.1:28091` 启动新的隔离 Python Agent；
- 实例使用 test、memory、deterministic、mock tool gateway，不连接数据库、Redis 或外部模型；
- 无密钥和错误密钥均返回 401，正确密钥 health 为 `UP`、RAG 为 `READY`；
- capabilities 报告 `laibin-rag-2026-07-29-v1`；
- ADMIN 成功及数值证据、SUPER_ADMIN 无证据、STAFF 403、静态与实时混合问题拆分均通过；
- 进程非回环连接 0，日志凭据匹配 0；正式 release 验收后复检仍通过；
- 部署模型目录已准备为 7 个只读、逐文件哈希一致且无 reparse point 的文件；
- 从精确 `4150e6d` 导出干净 Java/Web，临时 5174→28080→28091 链路完成 ADMIN 成功、三项数值、
  无证据、静态/实时混合问题及 STAFF 双层门禁真实浏览器验收；
- Java/Web 临时进程验收后已停止，28091 保持运行；当前 8080/8091/5173 未由本轮停止或重配；
- 真实 `deploy/simple/.env`、现有服务切换及切换后冒烟仍未执行。

结论：当前提交和正式 v1 已通过隔离 Python 与隔离 Web 全链路；现有 Web/Java/8091 尚未切换，
不能标记为当前部署上线。完整证据见 `rag-controlled-runtime-validation-2026-08-03.md` 和
`rag-controlled-web-runtime-validation-2026-08-03.md`。

### 3.3 当前快照验收可追溯性

- RAG-04 的 10/10 浏览器 UAT 仍是有效历史证据；
- 当前源码已绑定到可重建提交 `4150e6d`；
- 2026-08-03 已从该提交干净导出并对确定 v1 artifact 重做 ADMIN/STAFF 关键路径受控验收；
- 本地没有 SUPER_ADMIN 账号，未伪造 Web 身份；该角色由同日 28091 可信上下文验收覆盖；
- 当前部署切换后仍需执行 health、corpus version、ADMIN/STAFF 和回滚准备冒烟。

## 4. 测试版本纠偏

用户没有提供、也没有要求真实 v2 材料。自动化中的 `test-corpus-v1` 和 `test-corpus-v2`：

- 都由相同的白砂糖/红糖简化测试文档生成；
- 内容相同，版本标识不同；
- 只用于验证 pointer、compare-and-set、不可变 release、中断恢复和回滚协议；
- 不属于业务知识库，不进入正式 release，不构成当前项目待办。

未来材料确有更新时，按照整库重建规则生成新的 corpus version 即可；这属于通用运维流程，不是
当前缺失的“v2 阶段”。

## 5. 正确的交付收口顺序

1. 已完成：纠正文档中的完成状态和业务 v2 表述；
2. 已完成：识别并集成 RAG 源码、测试、Java/Web 集成和部署配置；
3. 已完成：记录确定提交 `4150e6d`；
4. 已完成：从该提交运行 Python、RAG、Java、Web 和构建回归；
5. 部分完成：只读模型目录已准备；持久化真实部署 env 待完成；
6. 已完成：启动 28091 受控 Agent 并验证认证 health、`RAG=READY`、v1 corpus version 和知识冒烟；
7. 已完成：对同一提交和 artifact 执行管理员/非管理员隔离 Web 验收；
8. 待完成：准备持久化真实部署 env、切换现有服务并执行切换后冒烟；
9. 待完成：记录部署事实后，将当前部署状态改为 `COMPLETED`。

## 6. 状态解释

- `V1_ARTIFACT_VALIDATED` 只表示语料发布目录和 pointer 完整，不等于进程在线；
- `SOURCE_INTEGRATED` 表示源码已进入确定提交，不表示 Git 忽略的 artifact、模型或真实 env 已部署；
- `ENGINEERING_REGRESSION_PASSED` 表示当前提交的自动化测试和构建通过，不等于在线实例通过验收；
- `ISOLATED_RUNTIME_28091_VERIFIED` 表示隔离 Python Agent 通过；
- `ISOLATED_WEB_5174_VERIFIED` 表示同一提交的隔离 Browser/Java/Python 全链路通过，不等于现有
  Web/Java/8091 已切换；
- `CURRENT_DEPLOYMENT_NOT_SWITCHED` 表示正式运行流量尚未指向本轮验证配置；
- 当前没有业务 v2，也没有真实 v2 待办。
