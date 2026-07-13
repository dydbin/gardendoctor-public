#!/usr/bin/env bash
set -euo pipefail

INFRA_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MONOREPO_ROOT="$(cd "$INFRA_DIR/.." && pwd)"
ROOT_DIR="$MONOREPO_ROOT/services/backend"
DEFAULT_ENV_FILE="$INFRA_DIR/.env.example"
ENV_FILE="${1:-$INFRA_DIR/.env}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RESULT_DIR="$INFRA_DIR/.runtime/loadtest"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Missing env file: $ENV_FILE" >&2
  exit 1
fi
if ! command -v python3 >/dev/null 2>&1; then
  echo "python3 is required to aggregate k6 JSON summaries." >&2
  exit 1
fi

source "$INFRA_DIR/scripts/load-env.sh"
load_env_file "$DEFAULT_ENV_FILE"
load_env_file "$ENV_FILE"

ROUNDS="${PERF_COMPARE_ROUNDS:-3}"
if [[ ! "$ROUNDS" =~ ^[0-9]+$ || "$ROUNDS" -lt 1 || "$ROUNDS" -gt 10 ]]; then
  echo "PERF_COMPARE_ROUNDS must be between 1 and 10." >&2
  exit 1
fi

BATCH_ID="${PERF_TEST_RUN_ID:-local}-compare-$(date -u +%Y%m%dT%H%M%SZ)"
mkdir -p "$RESULT_DIR"

run_scenario() {
  local round="$1"
  local mode="$2"
  local scenario="diary-${mode}"
  local test_id="${BATCH_ID}-r${round}-${mode}"
  local log_file="$RESULT_DIR/${test_id}.log"

  printf 'Round %s/%s: running %s...\n' "$round" "$ROUNDS" "$mode"
  if ! PERF_TEST_ID_OVERRIDE="$test_id" \
      bash "$SCRIPT_DIR/run.sh" "$scenario" "$ENV_FILE" \
      >"$log_file" 2>&1; then
    cat "$log_file" >&2
    return 1
  fi
  printf 'Round %s/%s: %s complete.\n' "$round" "$ROUNDS" "$mode"
}

# 홀수/짝수 라운드의 실행 순서를 뒤집어 순서·warm cache 편향을 줄입니다.
for ((round = 1; round <= ROUNDS; round++)); do
  if (( round % 2 == 1 )); then
    run_scenario "$round" offset
    run_scenario "$round" cursor
  else
    run_scenario "$round" cursor
    run_scenario "$round" offset
  fi
done

python3 "$INFRA_DIR/loadtest/aggregate_k6.py" \
  --result-dir "$RESULT_DIR" \
  --batch-id "$BATCH_ID" \
  --rounds "$ROUNDS" \
  --baseline "$INFRA_DIR/loadtest/baselines/diary-read-local.json" \
  --cursor-p95-limit-ms "${PERF_CURSOR_P95_MS:-100}" \
  --cursor-p99-limit-ms "${PERF_CURSOR_P99_MS:-200}" \
  --min-p95-improvement "${PERF_MIN_P95_IMPROVEMENT:-3}" \
  --min-p99-improvement "${PERF_MIN_P99_IMPROVEMENT:-3}" \
  --max-baseline-regression-ratio "${PERF_MAX_BASELINE_REGRESSION_RATIO:-1.20}"
