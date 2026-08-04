# RAG 当前提交隔离运行态验收记录

状态：`COMPLETED / ISOLATED_RUNTIME_28091_VERIFIED / CURRENT_WEB_DEPLOYMENT_UNVERIFIED`
日期：2026-08-03
时区：`Asia/Shanghai`
源码提交：`4150e6d`
corpus version：`laibin-rag-2026-07-29-v1`

## 1. 验收范围

本记录验证当前源码提交能从正式 v1 artifact 和核验模型启动独立 Python Agent，并通过认证、
readiness、知识检索、无证据、权限及安全边界检查。该实例不替换当前 8091，不接入当前 Web/Java
完整链路，因此不能作为现有 Web 部署已上线的证据。

隔离实例配置：

- 监听：`127.0.0.1:28091`；
- 启动时 PID：`28764`；
- `AGENT_ENV=test`；
- 状态：memory；
- 规划：deterministic；
- 模型：basic，不调用外部模型；
- Tool Gateway：mock，未启用免认证；
- RAG：enabled + required；
- stdout/stderr：工作区外 `C:\tmp\laibin-rag-uat-28091-4150e6d`；
- 服务密钥：随机生成，只保存于上述目录的受限文件，未记录值。

## 2. Artifact 与模型

正式 release 在启动前和验收后均通过 `validate-release`：

| 项目 | 结果 |
| --- | --- |
| document | 10 |
| chunk | 196 |
| 固定评测 | 83 |
| 发布文件 | 23 |
| release SHA-256 | `4a7525328af286d8ca7702ddc52c3f857244745bf0db080a84d426c6392456f6` |
| release 可写文件 | 0 |
| pointer 临时文件/发布锁 | 0 |
| prepared 审计残留 | 0 |

本地核验模型复制到 Git 忽略的 `deploy/simple/artifacts/rag-model`：

- 7 个文件，95,332,206 字节；
- 与构建模型逐文件 SHA-256 一致，差异 0；
- 可写文件 0；
- reparse point 0；
- 运行时只使用 `specific_model_path` 和 `local_files_only=true`。

第一次模型复制命令使用 `Copy-Item -LiteralPath <path>\*`，PowerShell 不展开通配符，目标保持空；
校验正确报告 7 个缺失文件。随后仅在已验证为空的目标目录中显式枚举源子项重新复制，最终校验
通过。正式 release 和源模型均未被改写。

## 3. 启动与认证

| 检查 | 结果 |
| --- | --- |
| 28091 启动前空闲 | 通过 |
| Uvicorn 启动 | 通过 |
| 无服务密钥访问 health | HTTP 401 |
| 错误服务密钥访问 health | HTTP 401 |
| 正确服务密钥访问 health | HTTP 200 |
| health | `UP` |
| model | `BASIC` |
| toolGateway | `UP`（隔离 mock） |
| stateStore | `UP`（memory） |
| planningMode | `DETERMINISTIC` |
| RAG readiness | `READY` |
| capabilities corpus version | `laibin-rag-2026-07-29-v1` |

临时服务密钥文件关闭 ACL 继承，仅授权当前 Windows 用户。验收日志中服务密钥原文、
`X-Agent-Service-Key`、API key、密码和 Bearer 匹配数均为 0。

## 4. 知识与权限验收

| 场景 | 结果 |
| --- | --- |
| ADMIN 工艺知识 | `PROCESS_KNOWLEDGE_QUERY / SUCCEEDED`，5 条安全引用 |
| ADMIN 数值证据 | 同时包含 `Φ1.5mm`、`Φ2.0mm`、`Φ2.5mm` |
| SUPER_ADMIN 无证据 | `ENTERPRISE_KNOWLEDGE_QUERY / NO_DATA`，0 引用，安全限定文案完整 |
| STAFF 直接调用 | HTTP 403 |
| 静态知识 + 实时库存混合问题 | `needsUserSelection=true`，0 卡片，要求拆成两个问题 |
| 成功响应敏感路径/凭据模式 | 0 |
| 无证据响应内部 ID/路径/凭据模式 | 0 |

第一次任意“月球仓库”无证据问题没有命中知识目标，因此未计入 RAG 无证据验收；随后使用既有
冻结 UAT 问法“白砂糖的企业认证资料是什么”完成 `NO_DATA` 验收。第一次省略“生产工艺”语境的
数值问法也未命中知识目标；补全明确工艺语境后命中冻结数值证据。这说明确定性路由的验收输入
必须明确表达静态知识目标，不应把未进入知识检索的普通回答误记为 RAG 结果。

## 5. 指标、连接和日志

- `knowledge_search_total{role="ADMIN",status="SUCCEEDED"}=2`；
- `knowledge_search_total{role="SUPER_ADMIN",status="NO_DATA"}=1`；
- 成功检索 p95 为约 32.956 ms，无证据检索为约 2.957 ms；
- 进程检查时有 2 条已建立连接，远端均为 loopback；非回环连接 0；
- stdout 为 0 字节，stderr 只有 203 字节正常 Uvicorn 启动日志；
- 日志中临时服务密钥、认证头及常见凭据模式匹配均为 0；
- 验收结束时进程仍在运行，未停止或重配 8080/8091。

## 6. 当前结论与后续门禁

当前提交、正式 v1 artifact 和部署模型已通过隔离 Python Agent 在线验证。仍未完成：

- 准备持久化、受控分发的真实部署 env；
- 将确定的 Java 隔离实例指向 28091；
- 从 Web 完成当前提交的 ADMIN/SUPER_ADMIN/STAFF 受控验收；
- 决定是否切换现有 8091，并在变更窗口内完成上线后检查。

在这些门禁关闭前，当前状态保持：

```text
SOURCE_INTEGRATED
ENGINEERING_REGRESSION_PASSED
V1_ARTIFACT_VALIDATED
ISOLATED_RUNTIME_28091_VERIFIED
CURRENT_WEB_DEPLOYMENT_UNVERIFIED
```
