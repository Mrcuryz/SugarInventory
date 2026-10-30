#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
for file in .env artifacts/app.jar artifacts/warehouse-mcp.jar artifacts/agent-service.whl artifacts/dist/index.html artifacts/rag/current.json artifacts/rag-model/model_optimized.onnx database/snapshot.sql; do
  test -f "$file" || { echo "Missing $file"; exit 1; }
done
grep -q '^DB_URL=jdbc:mysql://mysql:3306/laibin_demo?' .env || { echo 'Refusing non-demo database target'; exit 1; }
docker compose config --quiet
docker compose build agent
docker compose run --rm --no-deps agent python -m app.rag.offline.cli validate-runtime --runtime-root /app/rag --model-path /app/rag-model
docker compose up -d mysql redis
docker compose --profile operations run --rm migration
docker compose up -d
docker compose ps
