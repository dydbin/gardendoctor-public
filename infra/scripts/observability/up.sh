#!/usr/bin/env bash
set -euo pipefail

INFRA_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MONOREPO_ROOT="$(cd "$INFRA_DIR/.." && pwd)"
DEFAULT_ENV_FILE="$INFRA_DIR/.env.example"
ENV_FILE="${1:-$INFRA_DIR/.env}"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Missing env file: $ENV_FILE" >&2
  echo "Copy $DEFAULT_ENV_FILE to $INFRA_DIR/.env first." >&2
  exit 1
fi

source "$INFRA_DIR/scripts/load-env.sh"
load_env_file "$DEFAULT_ENV_FILE"
load_env_file "$ENV_FILE"

if command -v docker >/dev/null 2>&1; then
  DOCKER_BIN=(docker)
elif [[ -x "/mnt/c/Program Files/Docker/Docker/resources/bin/docker.exe" ]]; then
  DOCKER_BIN=("/mnt/c/Program Files/Docker/Docker/resources/bin/docker.exe")
else
  echo "Docker CLI not found." >&2
  exit 1
fi

if [[ ! "${MYSQL_EXPORTER_USER:-}" =~ ^[A-Za-z0-9_]+$ ]]; then
  echo "MYSQL_EXPORTER_USER must contain only letters, numbers, and underscores." >&2
  exit 1
fi
if [[ ${#MYSQL_EXPORTER_PASSWORD} -lt 16 \
      || "$MYSQL_EXPORTER_PASSWORD" == *"'"* \
      || "$MYSQL_EXPORTER_PASSWORD" == *"\\"* \
      || "$MYSQL_EXPORTER_PASSWORD" == *$'\n'* ]]; then
  echo "MYSQL_EXPORTER_PASSWORD must be at least 16 characters and cannot contain quotes, backslashes, or newlines." >&2
  exit 1
fi

COMPOSE=(
  "${DOCKER_BIN[@]}" compose
  --env-file "$ENV_FILE"
  -f "$INFRA_DIR/compose.yaml"
  --profile observability
)

"${COMPOSE[@]}" up -d mysql redis ai backend

for attempt in {1..60}; do
  if "${COMPOSE[@]}" exec -T mysql sh -c \
      'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqladmin ping -uroot --silent' >/dev/null 2>&1; then
    break
  fi
  if [[ "$attempt" -eq 60 ]]; then
    echo "MySQL did not become ready within 60 seconds." >&2
    exit 1
  fi
  sleep 1
done

# exporter 계정은 로컬 실행 시점에 만들며 비밀번호를 파일/로그에 남기지 않습니다.
"${COMPOSE[@]}" exec -T mysql sh -c \
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot' >/dev/null <<SQL
CREATE USER IF NOT EXISTS '${MYSQL_EXPORTER_USER}'@'%'
  IDENTIFIED BY '${MYSQL_EXPORTER_PASSWORD}' WITH MAX_USER_CONNECTIONS 3;
ALTER USER '${MYSQL_EXPORTER_USER}'@'%'
  IDENTIFIED BY '${MYSQL_EXPORTER_PASSWORD}' WITH MAX_USER_CONNECTIONS 3;
GRANT PROCESS, REPLICATION CLIENT, SELECT ON *.* TO '${MYSQL_EXPORTER_USER}'@'%';
FLUSH PRIVILEGES;
SQL

"${COMPOSE[@]}" up -d mysqld-exporter prometheus grafana

PROMETHEUS_URL="http://127.0.0.1:${PROMETHEUS_HOST_PORT:-9090}"
GRAFANA_URL="http://127.0.0.1:${GRAFANA_HOST_PORT:-3001}"

for attempt in {1..60}; do
  if curl -fsS "$PROMETHEUS_URL/-/ready" >/dev/null 2>&1; then
    break
  fi
  if [[ "$attempt" -eq 60 ]]; then
    echo "Prometheus did not become ready within 60 seconds." >&2
    exit 1
  fi
  sleep 1
done

for attempt in {1..60}; do
  if curl -fsS "$GRAFANA_URL/api/health" >/dev/null 2>&1; then
    break
  fi
  if [[ "$attempt" -eq 60 ]]; then
    echo "Grafana did not become ready within 60 seconds." >&2
    exit 1
  fi
  sleep 1
done

"${COMPOSE[@]}" ps
printf 'Prometheus: %s\nGrafana: %s\n' "$PROMETHEUS_URL" "$GRAFANA_URL"
