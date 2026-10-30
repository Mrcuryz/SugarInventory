# 香港测试和演示环境

目标：2026-09-08 新购腾讯云香港轻量服务器，2 核 / 8GB，公网 IP `43.161.201.12`。
仅部署测试副本，使用 HTTP + IP；正式服务器和本地数据库不作修改。

## 发布内容

沿用 `deploy/simple` 的 Java 21、Python 3.12 Agent、warehouse-mcp、Redis 和 Nginx 发布方式。
测试发布包单独生成到 `output/hk-test-deployment`，增加内部 MySQL 8.0，替换为本目录 HTTP Nginx 配置。
必须携带正式 `artifacts/rag` 和 `artifacts/rag-model`，并启用 RAG required 模式。
不打包本地 `.env`、历史日志、生产证书或 SSH 私钥。

## 数据副本

`scripts/export-test-database.py` 只允许连接本地数据库，以只读一致性快照导出。
导出时排除所有 Agent 会话、历史回答、预览和授权状态、操作日志与迁移锁数据。
用户表和员工名册不导出原数据，分别生成唯一的 `admin` / `ADMIN` 测试身份。
业务记录中的操作用户 ID 归并为该测试账号，操作姓名标记为 admin；因此副本不代表真实人员审计。
库存数量、业务记录及其业务关联保持导出时的值。迁移台账保留并核对脚本摘要。
生成 SQL 仅供新建的 `laibin_demo` 数据库导入，不能用于覆盖已有实例。

## 验收

必须验证：构建和测试、RAG validate-runtime、容器健康、schema 校验、admin 登录、用户/名册数量各为 1、
Agent 工具握手、真实模型只读查询、带引用的 RAG 回答、浏览器页面访问。
发布包准备完成不等于服务器已部署；实际结果在部署记录中补充。

## 2026-09-08 部署进度

迁移客户端使用 `--batch --raw`：读取 `CONCAT(checksum_sha256,CHAR(9),execution_type)` 时保留真正的制表符，
避免 MySQL 将其转义为文字 `\\t` 后导致 shell 错报摘要不一致。

- 腾讯云实例 `lhins-2ysb9p53`，Ubuntu 24.04；Docker、Compose 和五个基础镜像已安装。
- RAG 资源已上传到 `/home/ubuntu/laibin-demo/artifacts`，压缩包 SHA-256 与本地一致。
- 最终 Java 后端、MCP、Web 构建通过；Python 556 项通过、5 项跳过。
- 最终数据副本本地导入验证通过：74 张表、63 个外键，用户和员工名册各一条 admin。
- 用户明确授权后，应用包和独立凭据配置已上传；包 SHA-256 及 177 个文件摘要校验通过。
- 已启动 nginx、backend、agent、mysql、redis；MySQL/Redis 健康。MCP 由后端以子进程运行。
- 数据库结构校验通过：48 项迁移、60 张必需表、33 个关键列、7 个关键索引。
- HTTP 公网浏览器 admin 登录成功，首页正常显示业务数据；服务器再次核对用户和员工名册各一条。
- 真实模型完成库存台账第 1 页 5 条查询；RAG 完成问答并返回 3 张来源卡片，knowledge_search 审计为 SUCCESS。
- 运行目录 `/home/ubuntu/laibin-demo`；部署日志 `deploy-retry.log`，验收日志 `smoke-specific.log`。
- 本地登录信息单独保存在忽略目录 `output/hk-test-deployment/login.txt`，不包含模型 API 密钥。
- 当前 Agent 已知存在偶发超时、结构化响应失败及会话过期后的连接状态显示问题，来自同期 Agent 验收任务。
# 演示脱敏发布要求

2026-09-08 起，演示发布必须遵循 [PRIVACY.md](PRIVACY.md)。最初的快照与 RAG 包只用于内部保留，不能再次直接发布到公开演示环境。旧版 prepare-test-deployment.py 已停用，避免覆盖脱敏结果。
