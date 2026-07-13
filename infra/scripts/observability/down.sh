#!/usr/bin/env bash
set -euo pipefail

INFRA_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="${1:-$INFRA_DIR/.env}"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Missing env file: $ENV_FILE" >&2
  exit 1
fi

if command -v docker >/dev/null 2>&1; then
  DOCKER_BIN=(docker)
elif [[ -x "/mnt/c/Program Files/Docker/Docker/resources/bin/docker.exe" ]]; then
  DOCKER_BIN=("/mnt/c/Program Files/Docker/Docker/resources/bin/docker.exe")
else
  echo "Docker CLI not found." >&2
  exit 1
fi

COMPOSE=(
  "${DOCKER_BIN[@]}" compose
  --env-file "$ENV_FILE"
  -f "$INFRA_DIR/compose.yaml"
  --profile observability
)

# MySQL/Redis와 named volume은 유지하고 관측성 컨테이너만 제거합니다.
"${COMPOSE[@]}" stop grafana prometheus mysqld-exporter
"${COMPOSE[@]}" rm -f grafana prometheus mysqld-exporter
