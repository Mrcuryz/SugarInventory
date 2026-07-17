# Agent 生产部署手册

## 推荐：单人维护的最简 Docker Compose 方式

直接使用 `deploy/simple/`。它只维护一个 `.env`，同时启动 Nginx、Java、Python Agent 和 Redis；不需要逐个执行 `docker run`，也不需要 systemd 模板。

首次部署：

```bash
cd deploy/simple
chmod +x init-env.sh update.sh
./init-env.sh
vi .env                    # 只需填写数据库密码；微信/模型按实际使用填写
mkdir -p cert
# 将现有 server.crt、server.key 放入 cert/
./update.sh
```

`init-env.sh` 会自动生成 JWT、两个 Agent 内部密钥和 Redis 密码，因此不需要手工填写每个环境变量。

以后更新分两步：开发机执行 `scripts/prepare-simple-deployment.ps1`，把生成的 `deploy/simple/artifacts/` 上传覆盖服务器同目录；服务器只执行：

```bash
cd deploy/simple && ./update.sh
```

`.env`、证书和 Redis 数据不会被发布产物覆盖。即使本次只改 jar/dist，也可以执行同一条命令；Compose 只重建需要变化的 Agent 镜像并复用其他配置。

下面的 systemd/分离环境文件方案仅作为不使用 Compose 时的备选，当前部署无需采用。

生产路径固定为 Java Gateway → Python Runtime → Java internal Agent Gateway → warehouse-mcp，不配置旧 Agent 回退。

## 必需 Secret

数据库密码、JWT 签名密钥、微信 Secret、模型 API Key、Java↔Python 服务密钥、Python↔Java 工具网关密钥、Redis 密码必须由部署平台注入。不得写入镜像、Compose 文件、命令行参数、日志或 Git。各环境使用独立值；测试配置只能连接隔离测试库。

生产必须设置 `AGENT_ENV=production`、`AGENT_STATE_BACKEND=redis`、带认证的 `AGENT_REDIS_URL`、`AGENT_RUNTIME_MODE=python`、`AGENT_RUNTIME_FALLBACK_ENABLED=false`、`AGENT_ALLOW_INSECURE_MOCK_AUTH=false`。Redis 应使用专用账号、TLS/受控网络、持久化和备份；状态 TTL 按数据保留策略设置。

## 启动顺序与握手

1. 启动 Redis，并验证认证、TTL 和持久化。
2. 启动后端与 warehouse-mcp。首次 MCP 会话绑定会严格核对 47 个只读工具及注册表哈希。
3. 启动 Python Runtime。日志必须显示 Runtime 版本、源码位置、工具注册哈希和配方注册哈希。
4. Java 启用 Agent 前同时检查 health 和 capability；协议、工具或配方任一不一致即 fail-closed。
5. 执行固定数据集成测试，再做真实环境只读 smoke test。

发布包不得包含 `agent-service/build/lib`、`.env`、测试数据库文件或日志。CI 先运行 `scripts/check-agent-build-hygiene.ps1`，再执行 Python、Java、warehouse-mcp 测试和前端构建。

## 不使用 Docker、日常替换产物的部署方式

该方式适合现有服务器：凭据只需在服务器上配置一次，后续发布不需要修改 jar、dist 或重新填写密码。

### 一次性配置

在服务器创建两个仅运行账号可读的环境文件：

```bash
sudo install -d -m 750 /etc/laibin-sugar
sudo install -m 600 deploy/env/backend.env.example /etc/laibin-sugar/backend.env
sudo install -m 600 deploy/env/agent.env.example /etc/laibin-sugar/agent.env
sudo chown root:laibin /etc/laibin-sugar/backend.env /etc/laibin-sugar/agent.env
```

由运维在服务器本地填写真实值。不要将填写后的文件复制回项目、聊天、工单或发布压缩包。JWT Secret 必须是至少 512 bit 的 Base64 密钥；Java↔Python 和 Python↔Java 使用两个不同的随机密钥。生产 Redis 使用认证连接，Python 状态后端必须为 Redis。

systemd 服务通过 `EnvironmentFile=` 读取这些文件。参考模板：

- `deploy/systemd/laibin-backend.service`
- `deploy/systemd/laibin-agent.service`

首次安装 Python Runtime：

```bash
python3.12 -m venv /opt/laibin-sugar/agent-venv
/opt/laibin-sugar/agent-venv/bin/pip install /opt/laibin-sugar/releases/agent-service.whl
```

### 日常发布

如果只修改传统后端或前端，仍可只替换主 jar 和 dist：

```bash
sudo systemctl stop laibin-backend
sudo install -m 640 SugarInventory-1.0-SNAPSHOT.jar /opt/laibin-sugar/backend/app.jar
sudo rsync -a --delete dist/ /var/www/laibin-sugar/
sudo systemctl start laibin-backend
```

如果本次包含 Agent、MCP 工具、能力哈希或配方变化，发布单必须额外包含：

1. `agent-service` wheel；
2. `warehouse-mcp-0.1.0-exec.jar`；
3. 主后端 jar；
4. 如有前端变化再包含 dist。

```bash
sudo systemctl stop laibin-backend laibin-agent
sudo install -m 640 warehouse-mcp-0.1.0-exec.jar /opt/laibin-sugar/warehouse-mcp/warehouse-mcp-0.1.0-exec.jar
sudo install -m 640 SugarInventory-1.0-SNAPSHOT.jar /opt/laibin-sugar/backend/app.jar
/opt/laibin-sugar/agent-venv/bin/pip install --no-deps --force-reinstall agent-service.whl
sudo systemctl start laibin-agent
sudo systemctl start laibin-backend
```

先启动 Python，再启动 Java。Java 会执行协议、工具和配方握手；任何版本未同步都会 fail-closed。不能通过打开 fallback 或修改期望哈希临时绕过。

### 回滚

回滚必须把主 jar、Python wheel 和 warehouse-mcp exec jar 作为同一个兼容版本组一起回滚。环境文件不随版本包覆盖；只有确实轮换凭据时才单独更新。回滚后重新检查 capability/health，并执行固定数据测试和只读 smoke test。
