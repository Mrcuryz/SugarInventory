#!/usr/bin/env sh
set -eu

cd "$(dirname "$0")"
if [ -f .env ]; then
  echo '.env 已存在，未覆盖。'
  exit 0
fi

cp .env.example .env
jwt_secret="$(openssl rand -base64 64 | tr -d '\n')"
python_key="$(openssl rand -hex 32)"
tool_key="$(openssl rand -hex 32)"
entity_ref_key="$(openssl rand -hex 32)"
redis_password="$(openssl rand -hex 32)"
web_login_password="$(openssl rand -hex 12)"

sed -i "s|JWT_SECRET=GENERATED_BY_INIT|JWT_SECRET=$jwt_secret|" .env
sed -i "s|AGENT_PYTHON_SERVICE_KEY=GENERATED_BY_INIT|AGENT_PYTHON_SERVICE_KEY=$python_key|" .env
sed -i "s|AGENT_INTERNAL_TOOL_SERVICE_KEY=GENERATED_BY_INIT|AGENT_INTERNAL_TOOL_SERVICE_KEY=$tool_key|" .env
sed -i "s|AGENT_ENTITY_REF_SECRET=GENERATED_BY_INIT|AGENT_ENTITY_REF_SECRET=$entity_ref_key|" .env
sed -i "s|REDIS_PASSWORD=GENERATED_BY_INIT|REDIS_PASSWORD=$redis_password|" .env
sed -i "s|WEB_LOGIN_PASSWORD=GENERATED_BY_INIT|WEB_LOGIN_PASSWORD=$web_login_password|" .env
chmod 600 .env

echo '已生成 .env、Web 登录口令和随机密钥。现在只需编辑 DB_PASSWORD，以及实际使用的微信/模型配置。'
