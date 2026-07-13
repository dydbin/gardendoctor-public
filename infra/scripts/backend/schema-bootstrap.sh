#!/usr/bin/env bash
set -euo pipefail

INFRA_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MONOREPO_ROOT="$(cd "$INFRA_DIR/.." && pwd)"
ROOT_DIR="$MONOREPO_ROOT/services/backend"
DEFAULT_ENV_FILE="$INFRA_DIR/.env.example"
ENV_FILE="${1:-$INFRA_DIR/.env}"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Missing env file: $ENV_FILE" >&2
  echo "Copy $DEFAULT_ENV_FILE to $INFRA_DIR/.env and replace password placeholders first." >&2
  exit 1
fi

source "$INFRA_DIR/scripts/load-env.sh"
load_env_file "$DEFAULT_ENV_FILE"
load_env_file "$ENV_FILE"
configure_backend_host_env

required_variables=(
  MYSQL_DATABASE MYSQL_USER MYSQL_PASSWORD MYSQL_ROOT_PASSWORD
  SPRING_DATASOURCE_URL SPRING_DATASOURCE_USERNAME SPRING_DATASOURCE_PASSWORD
)
for variable in "${required_variables[@]}"; do
  if [[ -z "${!variable:-}" || "${!variable}" == replace-with-* ]]; then
    echo "$variable must be set to a non-placeholder value in $ENV_FILE" >&2
    exit 1
  fi
done

COMPOSE=(
  docker compose
  --env-file "$ENV_FILE"
  -f "$INFRA_DIR/compose.yaml"
)

wait_for_mysql() {
  for _ in {1..30}; do
    if "${COMPOSE[@]}" exec -T mysql sh -c \
        'MYSQL_PWD="$MYSQL_PASSWORD" mysqladmin ping -u"$MYSQL_USER" --silent' >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  echo "MySQL did not become ready within 30 seconds." >&2
  return 1
}

count_schema_tables() {
  "${COMPOSE[@]}" exec -T mysql sh -c \
    'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u"$MYSQL_USER" -Nse \
    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '\''$MYSQL_DATABASE'\''"'
}

"${COMPOSE[@]}" up -d mysql redis
wait_for_mysql

table_count="$(count_schema_tables)"

if [[ "$table_count" != "0" ]]; then
  echo "Schema bootstrap skipped: $MYSQL_DATABASE already contains $table_count table(s)."
  echo "No existing table or data was changed."
  exit 0
fi

export SPRING_JPA_HIBERNATE_DDL_AUTO=create
export APP_INIT_SEED_DATA_ENABLED=false
export APP_SCHEMA_BOOTSTRAP_ENABLED=true
export FIREBASE_ENABLED=false
export APP_FCM_OUTBOX_WORKER_ENABLED=false
export APP_USERPLANT_CARE_SCHEDULER_ENABLED=false
export APP_CHAT_DELETION_WORKER_ENABLED=false

cd "$ROOT_DIR"
for attempt in 1 2 3; do
  # Docker Desktop WSL port forwarding can lag immediately after container recreation.
  "${COMPOSE[@]}" restart mysql >/dev/null
  wait_for_mysql
  sleep 2

  if ! timeout "${SCHEMA_BOOTSTRAP_TIMEOUT_SECONDS:-90}" \
      env GRADLE_USER_HOME="${GRADLE_USER_HOME:-/tmp/gd-gradle}" \
      bash ./gradlew --no-daemon --console=plain bootRun \
      --args='--server.port=0 --spring.devtools.restart.enabled=false'; then
    echo "Schema bootstrap application attempt $attempt did not exit cleanly." >&2
  fi

  table_count="$(count_schema_tables)"
  if [[ "$table_count" != "0" ]]; then
    break
  fi
  echo "Schema bootstrap attempt $attempt created no tables; retrying after MySQL restart." >&2
done

if [[ "$table_count" == "0" ]]; then
  echo "Schema bootstrap failed: no tables were created." >&2
  exit 1
fi

echo "Schema bootstrap complete: $MYSQL_DATABASE contains $table_count table(s)."
