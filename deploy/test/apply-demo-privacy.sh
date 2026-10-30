#!/bin/sh
set -eu
cd /home/ubuntu/laibin-demo
test "$(pwd -P)" = /home/ubuntu/laibin-demo
grep -q '^DEMO_PUBLIC_IP=43.161.201.12$' .env
grep '^DB_URL=' .env | grep -q 'mysql:3306/laibin_demo'
test ! -e .privacy-v1-applied
patch=/home/ubuntu/demo-privacy-update
backup=/home/ubuntu/laibin-demo-privacy-backup-v1
test ! -e "$backup"
mkdir -m 700 "$backup"
sudo docker compose stop agent backend nginx
sudo docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump -uroot --single-transaction --no-tablespaces laibin_demo' > "$backup/database.sql"
cp artifacts/app.jar "$backup/app.jar"
cp docker-compose.yml "$backup/docker-compose.yml"
python3 "$patch/apply-demo-privacy.py"
sudo docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -Dlaibin_demo' < "$patch/privacy.sql"
mv artifacts/rag "$backup/rag"
mv artifacts/dist "$backup/dist"
cp -a "$patch/rag" artifacts/rag
cp -a "$patch/dist" artifacts/dist
cp "$patch/docker-compose.yml" docker-compose.yml
sudo docker compose up -d redis
sudo docker compose run --rm --no-deps agent python -m app.rag.offline.cli validate-runtime --runtime-root /app/rag --model-path /app/rag-model
sudo docker compose up -d agent backend nginx
touch .privacy-v1-applied
printf 'DEMO_PRIVACY_APPLIED\n'
