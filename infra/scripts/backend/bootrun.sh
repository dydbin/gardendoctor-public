#!/usr/bin/env bash
set -euo pipefail

INFRA_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MONOREPO_ROOT="$(cd "$INFRA_DIR/.." && pwd)"
ROOT_DIR="$MONOREPO_ROOT/services/backend"
DEFAULT_ENV_FILE="$INFRA_DIR/.env.example"
ENV_FILE="${1:-$INFRA_DIR/.env}"

if [[ ! -f "$DEFAULT_ENV_FILE" ]]; then
  echo "Missing default env file: $DEFAULT_ENV_FILE" >&2
  exit 1
fi

source "$INFRA_DIR/scripts/load-env.sh"
load_env_file "$DEFAULT_ENV_FILE"

if [[ -f "$ENV_FILE" ]]; then
  load_env_file "$ENV_FILE"
else
  echo "Optional env override file not found: $ENV_FILE" >&2
fi
configure_backend_host_env

cd "$ROOT_DIR"
GRADLE_USER_HOME="${GRADLE_USER_HOME:-/tmp/gd-gradle}" \
  bash ./gradlew bootRun
