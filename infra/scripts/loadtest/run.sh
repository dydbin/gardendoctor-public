#!/usr/bin/env bash
set -euo pipefail

INFRA_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MONOREPO_ROOT="$(cd "$INFRA_DIR/.." && pwd)"
ROOT_DIR="$MONOREPO_ROOT/services/backend"
DEFAULT_ENV_FILE="$INFRA_DIR/.env.example"
ENV_FILE="${2:-$INFRA_DIR/.env}"
MODE="${1:-smoke}"
RUNTIME_DIR="$INFRA_DIR/.runtime/loadtest"
RUNTIME_ENV_FILE="$RUNTIME_DIR/runtime.env"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Missing env file: $ENV_FILE" >&2
  exit 1
fi

source "$INFRA_DIR/scripts/load-env.sh"
load_env_file "$DEFAULT_ENV_FILE"
load_env_file "$ENV_FILE"
if [[ -f "$RUNTIME_ENV_FILE" ]]; then
  load_env_file "$RUNTIME_ENV_FILE"
fi
configure_backend_host_env

if command -v docker >/dev/null 2>&1; then
  DOCKER_BIN=(docker)
elif [[ -x "/mnt/c/Program Files/Docker/Docker/resources/bin/docker.exe" ]]; then
  DOCKER_BIN=("/mnt/c/Program Files/Docker/Docker/resources/bin/docker.exe")
else
  echo "Docker CLI not found." >&2
  exit 1
fi

case "$MODE" in
  smoke)
    K6_SCRIPT=/scripts/smoke.js
    ENDPOINT_MODE=health
    ;;
  diary-offset)
    K6_SCRIPT=/scripts/diary-read.js
    ENDPOINT_MODE=offset
    LOAD_PROFILE=steady
    ;;
  diary-cursor)
    K6_SCRIPT=/scripts/diary-read.js
    ENDPOINT_MODE=cursor
    LOAD_PROFILE=steady
    if [[ -z "${PERF_CURSOR:-}" ]]; then
      echo "PERF_CURSOR is empty. Run infra/scripts/loadtest/seed.sh first." >&2
      exit 1
    fi
    ;;
  diary-cursor-spike|diary-cursor-soak|diary-cursor-saturation)
    K6_SCRIPT=/scripts/diary-read.js
    ENDPOINT_MODE=cursor
    LOAD_PROFILE="${MODE#diary-cursor-}"
    if [[ -z "${PERF_CURSOR:-}" ]]; then
      echo "PERF_CURSOR is empty. Run infra/scripts/loadtest/seed.sh first." >&2
      exit 1
    fi
    ;;
  *)
    echo "Usage: $0 {smoke|diary-offset|diary-cursor|diary-cursor-spike|diary-cursor-soak|diary-cursor-saturation} [env-file]" >&2
    exit 1
    ;;
esac

LOAD_PROFILE="${LOAD_PROFILE:-smoke}"

if ! curl -fsS "http://127.0.0.1:${SERVER_PORT:-8080}/actuator/health/readiness" >/dev/null; then
  echo "Spring Boot is not ready. Run infra/scripts/loadtest/bootrun.sh first." >&2
  exit 1
fi
if ! curl -fsS "http://127.0.0.1:${PROMETHEUS_HOST_PORT:-9090}/-/ready" >/dev/null; then
  echo "Prometheus is not ready. Run infra/scripts/observability/up.sh first." >&2
  exit 1
fi

TEST_RUN_ID="${PERF_TEST_ID_OVERRIDE:-${PERF_TEST_RUN_ID:-local}-${MODE}-$(date -u +%Y%m%dT%H%M%SZ)}"
mkdir -p "$RUNTIME_DIR"
COMPOSE=(
  "${DOCKER_BIN[@]}" compose
  --env-file "$ENV_FILE"
  -f "$INFRA_DIR/compose.yaml"
  --profile loadtest
)

"${COMPOSE[@]}" run --rm --user "$(id -u):$(id -g)" \
  -e PERF_TEST_RUN_ID="$TEST_RUN_ID" \
  -e PERF_ENDPOINT_MODE="$ENDPOINT_MODE" \
  -e PERF_LOAD_PROFILE="$LOAD_PROFILE" \
  k6 run \
  -o experimental-prometheus-rw \
  --new-machine-readable-summary \
  --summary-export "/results/${TEST_RUN_ID}.json" \
  --summary-trend-stats "avg,med,p(90),p(95),p(99),max" \
  --tag "testid=$TEST_RUN_ID" \
  --tag "mode=$ENDPOINT_MODE" \
  --tag "load_profile=$LOAD_PROFILE" \
  "$K6_SCRIPT"

printf 'k6 test id: %s\nGrafana: http://127.0.0.1:%s/d/gardendoctor-local-performance\n' \
  "$TEST_RUN_ID" "${GRAFANA_HOST_PORT:-3001}"
