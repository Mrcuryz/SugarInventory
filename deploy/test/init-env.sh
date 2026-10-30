#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
[ ! -e .env ] || { echo '.env exists; unchanged'; exit 0; }
umask 077
cp .env.example .env
set_value() { sed -i "s|^$1=.*|$1=$2|" .env; }
set_value DB_URL 'jdbc:mysql://mysql:3306/laibin_demo?useUnicode=true\&characterEncoding=UTF-8\&serverTimezone=Asia/Shanghai\&allowPublicKeyRetrieval=true\&useSSL=false'
set_value DB_PASSWORD "$(openssl rand -hex 24)"
set_value WEB_LOGIN_PASSWORD "$(openssl rand -hex 8)"
set_value JWT_SECRET "$(openssl rand -base64 64 | tr -d '\n')"
for key in AGENT_PYTHON_SERVICE_KEY AGENT_INTERNAL_TOOL_SERVICE_KEY AGENT_ENTITY_REF_SECRET REDIS_PASSWORD; do
  set_value "$key" "$(openssl rand -hex 32)"
done
set_value AGENT_RAG_ENABLED true
set_value AGENT_RAG_REQUIRED true
printf '\nDEMO_PUBLIC_IP=43.161.201.12\n' >> .env
echo 'Created test-only .env. Configure model credentials before update.sh.'
