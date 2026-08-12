#!/usr/bin/env sh
set -eu

: "${DB_URL:?DB_URL is required}"
: "${DB_USERNAME:?DB_USERNAME is required}"
: "${DB_PASSWORD:?DB_PASSWORD is required}"

case "$DB_URL" in
  jdbc:mysql://*) ;;
  *) echo 'Unsupported DB_URL; expected jdbc:mysql://...' >&2; exit 2 ;;
esac

target="${DB_URL#jdbc:mysql://}"
host_port="${target%%/*}"
database_with_query="${target#*/}"
database="${database_with_query%%\?*}"
host="${host_port%%:*}"
port="${host_port#*:}"
if [ "$host" = "$port" ]; then port=3306; fi

case "$database" in
  ''|*[!A-Za-z0-9_]*) echo 'Unsafe database name in DB_URL' >&2; exit 2 ;;
esac

migration_dir="${DB_MIGRATION_DIR:-/migrations}"
baseline_through="${DB_MIGRATION_BASELINE_THROUGH:-}"
lock_timeout="${DB_MIGRATION_LOCK_TIMEOUT_MINUTES:-30}"
owner_token="$(cat /proc/sys/kernel/random/uuid)"
export MYSQL_PWD="$DB_PASSWORD"

mysql_cmd() {
  mysql \
    --host="$host" \
    --port="$port" \
    --user="$DB_USERNAME" \
    --database="$database" \
    --default-character-set=utf8mb4 \
    --batch \
    --skip-column-names \
    --binary-mode=1 \
    "$@"
}

test -d "$migration_dir" || { echo "Migration directory missing: $migration_dir" >&2; exit 2; }
set -- "$migration_dir"/*.sql
test -f "$1" || { echo "No SQL migrations found in $migration_dir" >&2; exit 2; }

mysql_cmd <<'SQL'
CREATE TABLE IF NOT EXISTS schema_migration (
    installed_rank BIGINT NOT NULL AUTO_INCREMENT,
    migration_name VARCHAR(255) NOT NULL,
    checksum_sha256 CHAR(64) NOT NULL,
    execution_type VARCHAR(16) NOT NULL,
    executed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    execution_ms BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (installed_rank),
    UNIQUE KEY uk_schema_migration_name (migration_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS schema_migration_lock (
    lock_name VARCHAR(64) NOT NULL,
    owner_token CHAR(36) NOT NULL,
    acquired_at DATETIME(6) NOT NULL,
    PRIMARY KEY (lock_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
SQL

mysql_cmd --execute="DELETE FROM schema_migration_lock WHERE lock_name='warehouse_schema' AND acquired_at < DATE_SUB(NOW(6), INTERVAL ${lock_timeout} MINUTE);"
if ! mysql_cmd --execute="INSERT INTO schema_migration_lock(lock_name,owner_token,acquired_at) VALUES('warehouse_schema','${owner_token}',NOW(6));"; then
  echo 'Another database migration process owns the warehouse schema lock.' >&2
  exit 3
fi

release_lock() {
  mysql_cmd --execute="DELETE FROM schema_migration_lock WHERE lock_name='warehouse_schema' AND owner_token='${owner_token}';" >/dev/null 2>&1 || true
}
trap release_lock EXIT INT TERM

if [ -n "$baseline_through" ] && [ ! -f "$migration_dir/$baseline_through" ]; then
  echo "Baseline migration does not exist: $baseline_through" >&2
  exit 2
fi

applied=0
baselined=0
baseline_active=false
if [ -n "$baseline_through" ]; then baseline_active=true; fi
for file in "$migration_dir"/*.sql; do
  name="$(basename "$file")"
  checksum="$(sha256sum "$file" | awk '{print $1}')"
  row="$(mysql_cmd --execute="SELECT CONCAT(checksum_sha256,CHAR(9),execution_type) FROM schema_migration WHERE migration_name='${name}';")"
  if [ -n "$row" ]; then
    recorded_checksum="$(printf '%s' "$row" | cut -f1)"
    if [ "$recorded_checksum" != "$checksum" ]; then
      echo "Applied migration checksum mismatch: $name" >&2
      exit 4
    fi
    if [ "$baseline_active" = true ] && [ "$name" = "$baseline_through" ]; then baseline_active=false; fi
    continue
  fi

  if [ "$baseline_active" = true ]; then
    mysql_cmd --execute="INSERT INTO schema_migration(migration_name,checksum_sha256,execution_type,execution_ms) VALUES('${name}','${checksum}','BASELINED',0);"
    baselined=$((baselined + 1))
    if [ "$name" = "$baseline_through" ]; then baseline_active=false; fi
    continue
  fi

  started_at="$(date +%s%3N)"
  mysql_cmd < "$file"
  finished_at="$(date +%s%3N)"
  elapsed=$((finished_at - started_at))
  mysql_cmd --execute="INSERT INTO schema_migration(migration_name,checksum_sha256,execution_type,execution_ms) VALUES('${name}','${checksum}','APPLIED',${elapsed});"
  applied=$((applied + 1))
done

expected_count="$(find "$migration_dir" -maxdepth 1 -type f -name '*.sql' | wc -l | tr -d ' ')"
ledger_count="$(mysql_cmd --execute='SELECT COUNT(*) FROM schema_migration;')"
if [ "$expected_count" != "$ledger_count" ]; then
  echo "Migration ledger is incomplete: expected=$expected_count actual=$ledger_count" >&2
  exit 5
fi

printf 'Database migrations PASS: database=%s total=%s baselined=%s applied=%s\n' "$database" "$expected_count" "$baselined" "$applied"
