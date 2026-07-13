#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
INFRA_DIR="$ROOT_DIR/infra"
cd "$ROOT_DIR"

make verify
make app-build
make backend-build

VERIFY_PROJECT_NAME="${VERIFY_PROJECT_NAME:-gardendoctor-verify-$$}"
export BACKEND_PORT="${VERIFY_BACKEND_PORT:-18080}"
export AI_PORT="${VERIFY_AI_PORT:-18000}"
export MYSQL_HOST_PORT="${VERIFY_MYSQL_PORT:-13307}"
export REDIS_HOST_PORT="${VERIFY_REDIS_PORT:-16379}"

COMPOSE=(
  docker compose
  --project-name "$VERIFY_PROJECT_NAME"
  --env-file "$INFRA_DIR/.env.example"
  -f "$INFRA_DIR/compose.yaml"
)

cleanup() {
  "${COMPOSE[@]}" down -v
}
trap cleanup EXIT

"${COMPOSE[@]}" up -d --build
./infra/scripts/smoke-ai.sh
./infra/scripts/smoke-backend.sh
