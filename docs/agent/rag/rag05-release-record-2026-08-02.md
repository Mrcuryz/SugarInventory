# RAG-05 正式 v1 发布记录

状态：`HISTORICAL_ACTIVATION_VALIDATION_PASSED / CURRENT_ONLINE_STATE_UNVERIFIED`
发布日期：2026-08-02
时区：`Asia/Shanghai`
发布者标识：`codex-rag05`

> 2026-08-03 纠偏：本文件记录 2026-08-02 当时的发布和隔离实例验证，不代表当前进程仍在线。
> 当前只有一套真实甲方材料；自动化中的 `test-corpus-v2` 是与测试 v1 内容相同、版本标识不同的
> 协议测试 release，不是业务 v2。当前权威状态见 `rag-current-state-audit-2026-08-03.md`。

## 1. 发布结论

正式语料 `laibin-rag-2026-07-29-v1` 已从离线构建候选复制到 Git 忽略的正式运行根目录，
并由 `current.json` 原子激活。发布审计为 `COMMITTED`，正式 release 已设为只读。独立 Python
Agent 实例在 28091 端口从正式根目录加载该版本，health 为 `UP`、RAG 为 `READY`。

本次没有修改库存数据、连接生产数据库、重写冻结 corpus manifest，也没有停止或重配原
8080/8091、18080/18091 服务。

## 2. Artifact 与指针

| 项目 | 结果 |
| --- | --- |
| corpus version | `laibin-rag-2026-07-29-v1` |
| document 数 | 10 |
| chunk 数 | 196 |
| 固定评测数 | 83 |
| release 文件数 | 23 |
| release 总大小 | 1,491,225 bytes |
| 发布时间 | `2026-08-02T01:26:59.915161+08:00` |
| 发布动作 | `PUBLISH` |
| pointerChanged | `true` |
| releaseCopied | `true` |
| 审计状态 | `COMMITTED` |
| 审计事件 | `audit/20260802T012659+0800-publish-1ee4fe9b3ade47beb0591ba4264184e7.json` |

摘要：

| 对象 | SHA-256 |
| --- | --- |
| 完整 release | `4a7525328af286d8ca7702ddc52c3f857244745bf0db080a84d426c6392456f6` |
| corpus manifest | `3951fafadf06fd1c8837a321d96d68ed171b08d533367141d55b00a92d045ddf` |
| index manifest | `2a36b5a8d15f129453d5041562b1b56bc19dfab49a795b82e4da7a105b94fd83` |
| evaluation report | `f620d0c82359df5f44b21dbbfdb114eb3a99f3807198854f8ab23d27e1e8013c` |
| evaluation set | `658dca252ecc1dff0ef7a24a61c08e62d0b6a5d960b286ff69c6fb0c0c1bc16d` |
| 激活后 `current.json` | `f6e8f0247160be9542f5a7f942ecddd6222ce5150bce2ee30a9d8d0ac24c5e2b` |

源候选与正式复制后的 release 均重新执行完整契约、血缘、评测和全树摘要校验，结果一致。
正式 corpus manifest 保持离线构建状态 `VALIDATED`；是否激活只由 pointer 和发布审计表达。

## 3. 原子性、恢复和回滚验证

### 3.1 真实候选中断恢复

使用真实 v1 候选和隔离运行根目录，在 pointer 替换前注入中断：

- 首次运行没有生成不完整 `current.json`；
- 完整但未激活的 v1 release 得以保留；
- 没有遗留 staging、pointer 临时文件或 prepared 审计；
- 使用同一候选续跑成功，且复用已核验 release，`releaseCopied=false`。

### 3.2 正式根目录失败闭锁

对正式根目录发起“目标版本就是当前版本”的回滚请求，CLI 返回
`ROLLBACK_TARGET_IS_CURRENT` 并以失败退出。操作前后：

- `current.json` SHA-256 均为
  `f6e8f0247160be9542f5a7f942ecddd6222ce5150bce2ee30a9d8d0ac24c5e2b`；
- 审计 JSON 数量均为 1；
- 锁、pointer 临时文件和 prepared 审计数量为 0。

### 3.3 跨版本机制

自动化使用两个内容相同、version 分别为 `test-corpus-v1` 和 `test-corpus-v2` 的完整有效 fixture
release，验证原子回滚、两个 release 均保留以及回滚目标重新校验。该测试只覆盖 pointer、
compare-and-set 和不可变发布协议，不表示存在第二套甲方材料。

将来材料确有更新时，应生成新的 corpus version，并在当次发布窗口执行重启、readiness、
corpus version、知识冒烟和审计核对；如已有上一真实版本，再验证回滚能力。

## 4. 当时的运行态验证

- 独立 Python Agent：`127.0.0.1:28091`；
- Java Tool Gateway：复用隔离实例 `127.0.0.1:18080`；
- health：`status=UP`、`model=BASIC`、`toolGateway=UP`、`stateStore=UP`、
  `planningMode=DETERMINISTIC`、`rag=READY`；
- capabilities：`rag.enabled=true`、`rag.required=true`、
  `corpusVersion=laibin-rag-2026-07-29-v1`；
- 正式 release golden：管理员 CCP2 查询返回安全证据和正确 corpus version，STAFF 被拒绝，
  结果不暴露路径、内部 ID 或凭据；
- stdout/stderr 位于工作区外 `C:\tmp`，日志仅见正常启动和 HTTP 200 健康请求。

## 5. 最终回归

| 验证 | 结果 |
| --- | --- |
| Python 全量 | `446 passed, 5 skipped` |
| RAG 专项 | `119 passed, 5 skipped` |
| RAG-05 正式 release golden | `2 passed` |
| 跨版本回滚和中断恢复定向 | `2 passed` |
| Java 全量 | `308 passed, 0 failed, 0 errors, 0 skipped` |
| Web Node 全量 | `65 passed` |
| Web 生产构建 | 通过；仅有既有 Sass legacy API 和大 chunk 警告 |
| Python `compileall` | 通过 |
| Compose YAML 及两个只读挂载断言 | 通过 |

当前 `webpage/package.json` 没有 `test:unit` script，因此 Web 使用仓库实际可用的
`node --test src/components/agent/*.test.mjs src/utils/*.test.mjs`。真实 `deploy/simple/.env`
不存在，未把不完整的 `docker compose config` 或容器上线记录为通过；本次验证的是 Compose YAML
语义和 corpus/model 两个只读挂载。容器部署前仍需由运维准备真实 env，并把已核验的本地 embedding
模型放入 `deploy/simple/artifacts/rag-model`。

## 6. 后续发布要求

1. 材料更新时从完整材料集合重新执行 inventory、解析、规范化、切块、索引和固定评测；
2. 生成新的真实 corpus version，不覆盖 v1，不复制旧内容伪造新版本；
3. 使用 expected current version 执行 compare-and-set 发布；
4. 重启 Agent 并核对 readiness、corpus version、知识冒烟和审计；
5. 如存在上一真实版本，在同一发布窗口验证回滚，再切回经确认的目标版本；
6. 追加新的发布记录和开发日志，不改写本记录。
