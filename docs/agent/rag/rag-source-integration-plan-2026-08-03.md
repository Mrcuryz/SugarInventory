# RAG 源码版本库集成计划

状态：`COMPLETED / SOURCE_INTEGRATED / ENGINEERING_REGRESSION_PASSED`
日期：2026-08-03
基线 Git HEAD：`c9e2274`
适用范围：来宾智能仓储 RAG 源码、测试、文档、Agent/Java/Web 集成和部署示例

## 1. 目标

当前 RAG 已在共享工作区完成工程实现和测试，但 clean checkout 不包含相关能力。本计划用于把
该实现安全转换为可审查、可重建的版本库交付，同时避免夹带其他并行工作流改动。

本切片完成不等于在线部署完成。源码集成后仍需从确定提交构建、配置并重新验收当前实例。

## 2. 基本原则

1. 只纳入能够证明属于 RAG 的文件或 hunk；
2. RAG 专属新增文件可以整文件审查，公共文件必须逐 hunk 审查；
3. 无法安全拆分的公共文件不暂存，记录依赖和阻塞原因；
4. 禁止宽泛暂存、自动覆盖、重置或清理共享工作区；
5. artifact、模型、密钥、日志、缓存和真实环境配置不进入 Git；
6. 暂存区必须可单独审查，且不依赖未记录的隐式文件；
7. 最终以确定提交、完整测试和部署验收作为交付完成依据。

## 3. 文件分类

### 3.1 A 类：RAG 专属新增文件

候选范围：

```text
agent-service/app/rag/**
agent-service/tests/rag/**
docs/agent/rag/**
```

只有在确认不存在缓存、构建产物、真实路径、密钥和大模型文件后，才允许整文件暂存。

### 3.2 B 类：RAG 专属但位于公共目录的新增文件

典型候选包括：

```text
agent-service/tests/__init__.py
webpage/src/components/agent/knowledgeCardPresentation.mjs
webpage/src/components/agent/knowledgeCardPresentation.test.mjs
src/main/java/.../agent/security/AgentAccessPolicy.java
src/test/java/.../agent/security/AgentAccessPolicyTest.java
```

必须检查这些文件是否同时服务其他 Agent 功能；若属于共享基础能力，应记录为依赖而不是假装为
RAG 私有实现。

### 3.3 C 类：包含 RAG 和其他功能的已跟踪共享文件

典型范围：

```text
agent-service/app/*.py
agent-service/tests/*.py
src/main/java/.../agent/**
src/test/java/.../agent/**
webpage/src/components/AgentAssistant.vue
webpage/src/components/Menu.vue
webpage/src/components/agent/AgentBusinessCard.vue
webpage/src/components/agent/AgentMessageBubble.vue
deploy/simple/.env.example
deploy/simple/docker-compose.yml
```

这类文件禁止直接整文件暂存。必须对照 HEAD 和当前工作区逐 hunk 判断：

- 纯 RAG hunk：可单独暂存；
- RAG 与其他功能不可分割：记录组合依赖，待相关改动共同收敛；
- 与 RAG 无关：保留在工作区，不进入本次暂存。

### 3.4 D 类：禁止纳入 Git

```text
agent-service/build/**
deploy/simple/artifacts/**
agent-service/.venv/**
webpage/dist/**
*.env（真实配置）
密钥、模型、进程日志、临时发布锁、pointer 临时文件
```

`current.json` 和正式 release 是部署 artifact，不是源码。它们通过发布摘要和运维记录追踪。

## 4. 实施顺序

1. 获取 RAG 相关 untracked/modified 清单；
2. 扫描文件大小、扩展名、BOM、路径、凭据和生成物；
3. 分析 Python/Java/Web/Deploy 公共文件 diff；
4. 生成逐文件 manifest，标记 `INCLUDE`、`SHARED_HUNK`、`DEPENDENCY`、`EXCLUDE`；
5. 先暂存 A 类和确认安全的 B 类文件；
6. 使用最小 patch 暂存 C 类纯 RAG hunk；
7. 检查 `git diff --cached --check`、文件列表和敏感信息；
8. 从工作区运行 Python、RAG、Java、Web 回归；
9. 追加开发日志，记录暂存范围和未解决依赖；
10. 未经用户明确要求，不创建提交或推送。

## 5. 完成门槛

- staged 文件清单逐项有归属说明；
- staged diff 不含 artifact、模型、密钥、真实 env 或其他工作流改动；
- 公共文件没有用整文件暂存掩盖混合 hunk；
- `git diff --cached --check` 通过；
- 当前工作区回归继续通过；
- 未暂存依赖和部署缺口被明确记录；
- 用户可以基于清晰 staged diff 决定是否提交。

## 6. 非目标

- 不启动或停止当前 8080/8091；
- 不配置当前未知密钥；
- 不执行正式 Compose 部署；
- 不提交或推送；
- 不修改正式 v1 artifact；
- 不创建额外业务材料版本。

## 7. 执行结果

- RAG 源码、测试、文档及 Agent/Java/Web/Deploy 集成点已进入提交 `4150e6d`；
- 该提交由共享工作区中的其他任务创建并推送，本计划执行线程未创建、改写或回退提交；
- 提交同时包含 analytics/reporting 基线，因此提交标题没有单独体现 RAG；
- 集成后的回归结果为：Python `454 passed, 5 skipped`、RAG `119 passed, 5 skipped`、
  Java `316 passed`、Web `67 passed`，Vite 生产构建通过；
- 正式 v1 artifact、模型和真实 env 继续按设计排除在 Git 外；
- 当前在线部署仍未验证，本计划完成不等于 RAG 已上线。
