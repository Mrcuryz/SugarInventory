#!/usr/bin/env sh
set -eu

cd "$(dirname "$0")"
test -f .env || { echo '缺少 .env，请先执行 ./init-env.sh'; exit 1; }
test -f artifacts/app.jar || { echo '缺少 artifacts/app.jar'; exit 1; }
test -f artifacts/warehouse-mcp.jar || { echo '缺少 artifacts/warehouse-mcp.jar'; exit 1; }
test -f artifacts/agent-service.whl || { echo '缺少 artifacts/agent-service.whl'; exit 1; }
test -f artifacts/dist/index.html || { echo '缺少 artifacts/dist/index.html'; exit 1; }
test -f cert/server.crt || { echo '缺少 cert/server.crt'; exit 1; }
test -f cert/server.key || { echo '缺少 cert/server.key'; exit 1; }

# 兼容只替换构建产物的旧部署目录：新密钥缺失时自动生成，不要求手工补变量。
if ! grep -q '^AGENT_ENTITY_REF_SECRET=' .env; then
  printf '\nAGENT_ENTITY_REF_SECRET=%s\n' "$(openssl rand -hex 32)" >> .env
  chmod 600 .env
  echo '已为受控业务实体引用自动生成独立密钥。'
fi

docker compose config >/dev/null
docker compose build agent
docker compose up -d --remove-orphans
docker compose ps
