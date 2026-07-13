#!/usr/bin/env bash
set -euo pipefail

INFRA_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MONOREPO_ROOT="$(cd "$INFRA_DIR/.." && pwd)"
ROOT_DIR="$MONOREPO_ROOT/services/backend"
DEFAULT_ENV_FILE="$INFRA_DIR/.env.example"
ENV_FILE="${1:-$INFRA_DIR/.env}"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Missing env file: $ENV_FILE" >&2
  exit 1
fi

source "$INFRA_DIR/scripts/load-env.sh"
load_env_file "$DEFAULT_ENV_FILE"
load_env_file "$ENV_FILE"
configure_backend_host_env

cd "$ROOT_DIR"
# performance profile은 Prometheus endpoint를 열고, seed-data는 성능 전용 계정 등록에
# 필요한 기본 ImageFile/Plant/Farm catalog만 초기화합니다.
MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,info,prometheus \
GRADLE_USER_HOME="${GRADLE_USER_HOME:-/tmp/gd-gradle}" \
  bash ./gradlew bootRun --args="--spring.profiles.active=performance --app.init.seed-data.enabled=true"
