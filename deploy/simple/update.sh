#!/usr/bin/env sh
set -eu

cd "$(dirname "$0")"
test -f .env || { echo '缺少 .env，请先执行 ./init-env.sh'; exit 1; }
test -f artifacts/app.jar || { echo '缺少 artifacts/app.jar'; exit 1; }
test -f artifacts/warehouse-mcp.jar || { echo '缺少 artifacts/warehouse-mcp.jar'; exit 1; }
test -f artifacts/agent-service.whl || { echo '缺少 artifacts/agent-service.whl'; exit 1; }
test -f artifacts/dist/index.html || { echo '缺少 artifacts/dist/index.html'; exit 1; }
test -f artifacts/database/migrations/2026-08-12-auto-inbound-execution-idempotency.sql || { echo '缺少数据库迁移产物'; exit 1; }
test -f apply-migrations.sh || { echo '缺少数据库迁移执行器'; exit 1; }
test -f cert/server.crt || { echo '缺少 cert/server.crt'; exit 1; }
test -f cert/server.key || { echo '缺少 cert/server.key'; exit 1; }

rag_enabled="$(sed -n 's/^AGENT_RAG_ENABLED=//p' .env | tail -n 1 | tr '[:upper:]' '[:lower:]')"
rag_required="$(sed -n 's/^AGENT_RAG_REQUIRED=//p' .env | tail -n 1 | tr '[:upper:]' '[:lower:]')"
if [ "$rag_enabled" = "true" ]; then
  [ "$rag_required" = "true" ] || { echo '生产启用 RAG 时 AGENT_RAG_REQUIRED 必须为 true'; exit 1; }
  test -f artifacts/rag/current.json || { echo 'RAG 已启用但缺少 artifacts/rag/current.json'; exit 1; }
  test -f artifacts/rag-model/model_optimized.onnx || { echo 'RAG 已启用但缺少本地 embedding 模型'; exit 1; }
  test -f artifacts/rag-model/tokenizer.json || { echo 'RAG 已启用但模型 tokenizer 不完整'; exit 1; }
  test -f artifacts/rag-model/config.json || { echo 'RAG 已启用但模型 config 不完整'; exit 1; }
fi

# 兼容只替换构建产物的旧部署目录：新密钥缺失时自动生成，不要求手工补变量。
if ! grep -q '^AGENT_ENTITY_REF_SECRET=' .env; then
  printf '\nAGENT_ENTITY_REF_SECRET=%s\n' "$(openssl rand -hex 32)" >> .env
  chmod 600 .env
  echo '已为受控业务实体引用自动生成独立密钥。'
fi

docker compose config >/dev/null
docker compose build agent
if [ "$rag_enabled" = "true" ]; then
  docker compose run --rm --no-deps agent \
    python -m app.rag.offline.cli validate-runtime \
    --runtime-root /app/rag \
    --model-path /app/rag-model
fi
docker compose --profile operations run --rm migration
docker compose up -d --remove-orphans
docker compose ps
