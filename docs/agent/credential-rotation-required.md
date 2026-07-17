# 凭据轮换清单

状态：需要人工处理，不自动改写 Git 历史。

当前工作树中的真实凭据已改为环境变量或 Secret 注入。以下凭据曾以明文形式存在于受 Git 管理的配置文件中，应视为可能已经泄露：

| 凭据类别 | 历史位置 | 建议动作 |
| --- | --- | --- |
| MySQL 应用账号密码 | `src/main/resources/application.yml` | 创建最小权限应用账号，轮换密码，撤销旧密码。测试使用独立数据库和账号。 |
| 微信应用 Secret | `src/main/resources/application.yml` | 在微信平台轮换 Secret，并更新部署 Secret。 |
| 模型服务 API Key | `src/main/resources/application.yml` | 在模型供应商控制台撤销旧 Key，创建最小权限新 Key，配置调用限额和告警。 |
| JWT 签名 Secret | `src/main/resources/application.yml` | 轮换签名密钥；评估是否使现有登录令牌全部失效。 |

Agent Java↔Python 内部服务密钥当前使用环境变量占位。仍应在各环境使用独立随机值，不得复用数据库、JWT 或模型密钥。

只检查提交元数据、不输出凭据内容的历史扫描确认，`application.yml` 的敏感字段在以下提交中发生过变更，应纳入人工轮换与泄露影响评估：

- `44b0291`、`b7b21e5`、`04831aa`、`8aafab9`、`4b068d7`、`15171e4`、`4342761`、`5f23d3c`

该清单不表示每个提交都包含每一种凭据，也不替代在托管平台开启 secret scanning。按照本阶段约束，不自动重写 Git 历史；如决定清理历史，应另开变更窗口并协调所有仓库副本重新同步。

轮换完成后应记录负责人、时间、受影响环境和验证结果。不要把新值填写到本文件、工单正文、普通日志或 Git 提交中。
