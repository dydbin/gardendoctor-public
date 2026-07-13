#!/usr/bin/env bash
set -euo pipefail

INFRA_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MONOREPO_ROOT="$(cd "$INFRA_DIR/.." && pwd)"
ROOT_DIR="$MONOREPO_ROOT/services/backend"
DEFAULT_ENV_FILE="$INFRA_DIR/.env.example"
ENV_FILE="${1:-$INFRA_DIR/.env}"
RUNTIME_ENV_FILE="$INFRA_DIR/.runtime/loadtest/runtime.env"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Missing env file: $ENV_FILE" >&2
  exit 1
fi

source "$INFRA_DIR/scripts/load-env.sh"
load_env_file "$DEFAULT_ENV_FILE"
load_env_file "$ENV_FILE"
configure_backend_host_env

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
)

if [[ ! "${MYSQL_DATABASE:-}" =~ ^[A-Za-z0-9_]+$ ]]; then
  echo "MYSQL_DATABASE contains unsupported characters." >&2
  exit 1
fi
if [[ ! "${PERF_USER_EMAIL:-}" =~ ^[A-Za-z0-9._+-]+@[A-Za-z0-9.-]+$ ]]; then
  echo "PERF_USER_EMAIL is invalid." >&2
  exit 1
fi
if [[ "$PERF_USER_EMAIL" != *@gardendoctor.local ]]; then
  echo "PERF_USER_EMAIL must use the reserved @gardendoctor.local test domain." >&2
  exit 1
fi
if [[ ! "${PERF_USER_NICKNAME:-}" =~ ^[A-Za-z0-9_-]{2,10}$ ]]; then
  echo "PERF_USER_NICKNAME must be 2-10 ASCII letters, numbers, underscores, or hyphens." >&2
  exit 1
fi
if [[ ${#PERF_USER_PASSWORD} -lt 8 || ${#PERF_USER_PASSWORD} -gt 20 \
      || "$PERF_USER_PASSWORD" == *"'"* \
      || "$PERF_USER_PASSWORD" == *'"'* \
      || "$PERF_USER_PASSWORD" == *"\\"* \
      || "$PERF_USER_PASSWORD" == *$'\n'* ]]; then
  echo "PERF_USER_PASSWORD must be 8-20 JSON-safe characters." >&2
  exit 1
fi
if [[ ! "${PERF_DATASET_SIZE:-}" =~ ^[0-9]+$ \
      || "$PERF_DATASET_SIZE" -lt 100 \
      || "$PERF_DATASET_SIZE" -gt 1000000 ]]; then
  echo "PERF_DATASET_SIZE must be between 100 and 1000000." >&2
  exit 1
fi
if [[ ! "${PERF_DEEP_PAGE:-}" =~ ^[0-9]+$ || ! "${PERF_PAGE_SIZE:-}" =~ ^[0-9]+$ ]]; then
  echo "PERF_DEEP_PAGE and PERF_PAGE_SIZE must be non-negative integers." >&2
  exit 1
fi

DEEP_OFFSET=$((PERF_DEEP_PAGE * PERF_PAGE_SIZE))
if (( PERF_DATASET_SIZE <= DEEP_OFFSET + PERF_PAGE_SIZE )); then
  echo "PERF_DATASET_SIZE must be larger than the selected deep page." >&2
  exit 1
fi

APP_URL="http://127.0.0.1:${SERVER_PORT:-8080}"
if ! curl -fsS "$APP_URL/actuator/health/readiness" >/dev/null; then
  echo "Spring Boot is not ready. Run infra/scripts/loadtest/bootrun.sh first." >&2
  exit 1
fi

mysql_query() {
  local query="$1"
  "${COMPOSE[@]}" exec -T -e GD_QUERY="$query" mysql sh -c \
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -Nse "$GD_QUERY"'
}

USER_ID="$(mysql_query "SELECT user_id FROM \`${MYSQL_DATABASE}\`.users WHERE email = '${PERF_USER_EMAIL}' LIMIT 1;")"
if [[ -z "$USER_ID" ]]; then
  REGISTER_STATUS="$(curl -sS -o /dev/null -w '%{http_code}' \
    -H 'Content-Type: application/json' \
    --data-binary @- "$APP_URL/auth/register" <<JSON
{"email":"${PERF_USER_EMAIL}","password":"${PERF_USER_PASSWORD}","nickname":"${PERF_USER_NICKNAME}"}
JSON
)"
  if [[ "$REGISTER_STATUS" != "201" ]]; then
    echo "Failed to create the local k6 user (HTTP $REGISTER_STATUS)." >&2
    exit 1
  fi
  USER_ID="$(mysql_query "SELECT user_id FROM \`${MYSQL_DATABASE}\`.users WHERE email = '${PERF_USER_EMAIL}' LIMIT 1;")"
fi

if [[ ! "$USER_ID" =~ ^[0-9]+$ ]]; then
  echo "Could not resolve the local k6 user id." >&2
  exit 1
fi

echo "Replacing the dedicated performance user's Diary dataset (${PERF_DATASET_SIZE} rows)..."
"${COMPOSE[@]}" exec -T -e GD_DATABASE="$MYSQL_DATABASE" mysql sh -c \
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot "$GD_DATABASE"' >/dev/null <<SQL
DELETE dup
FROM \`${MYSQL_DATABASE}\`.diary_user_plant dup
JOIN \`${MYSQL_DATABASE}\`.diaries d ON d.diary_id = dup.diary_id
WHERE d.user_id = ${USER_ID};

DELETE FROM \`${MYSQL_DATABASE}\`.diaries WHERE user_id = ${USER_ID};

INSERT INTO \`${MYSQL_DATABASE}\`.diaries (
  user_id, title, content, diary_date, created_at, updated_at,
  watered, pruned, fertilized, version
)
SELECT
  ${USER_ID},
  CONCAT('k6-diary-', sequence_number),
  'deterministic local k6 performance dataset',
  DATE(DATE_SUB('2026-07-10 12:00:00.000000', INTERVAL FLOOR(sequence_number / 4) SECOND)),
  DATE_SUB('2026-07-10 12:00:00.000000', INTERVAL FLOOR(sequence_number / 4) SECOND),
  DATE_SUB('2026-07-10 12:00:00.000000', INTERVAL FLOOR(sequence_number / 4) SECOND),
  MOD(sequence_number, 3) = 0,
  MOD(sequence_number, 5) = 0,
  MOD(sequence_number, 7) = 0,
  0
FROM (
  SELECT d0.n + d1.n * 10 + d2.n * 100 + d3.n * 1000 + d4.n * 10000 + d5.n * 100000 AS sequence_number
  FROM (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d0
  CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d1
  CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d2
  CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d3
  CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d4
  CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d5
) sequence_source
WHERE sequence_number < ${PERF_DATASET_SIZE};
SQL

CURSOR_ROW="$(mysql_query "
SELECT DATE_FORMAT(created_at, '%Y-%m-%dT%H:%i:%s.%f'), diary_id
FROM \`${MYSQL_DATABASE}\`.diaries FORCE INDEX (idx_diary_user_created)
WHERE user_id = ${USER_ID}
ORDER BY created_at DESC, diary_id DESC
LIMIT 1 OFFSET $((DEEP_OFFSET - 1));")"
IFS=$'\t' read -r CURSOR_TIME CURSOR_ID <<< "$CURSOR_ROW"

if [[ -z "$CURSOR_TIME" || ! "$CURSOR_ID" =~ ^[0-9]+$ ]]; then
  echo "Failed to calculate the deep-page cursor." >&2
  exit 1
fi

CURSOR="$(printf '%s' "${CURSOR_TIME}|${CURSOR_ID}" | base64 -w 0 | tr '+/' '-_' | tr -d '=')"
mkdir -p "$(dirname "$RUNTIME_ENV_FILE")"
if [[ -f "$RUNTIME_ENV_FILE" ]] && grep -q '^PERF_CURSOR=' "$RUNTIME_ENV_FILE"; then
  sed -i "s|^PERF_CURSOR=.*$|PERF_CURSOR=${CURSOR}|" "$RUNTIME_ENV_FILE"
else
  printf 'PERF_CURSOR=%s\n' "$CURSOR" > "$RUNTIME_ENV_FILE"
fi
chmod 600 "$RUNTIME_ENV_FILE"

ACTUAL_COUNT="$(mysql_query "SELECT COUNT(*) FROM \`${MYSQL_DATABASE}\`.diaries WHERE user_id = ${USER_ID};")"
printf 'Seeded Diary rows: %s\nDeep offset: %s\nUpdated PERF_CURSOR in %s\n' \
  "$ACTUAL_COUNT" "$DEEP_OFFSET" "$RUNTIME_ENV_FILE"
