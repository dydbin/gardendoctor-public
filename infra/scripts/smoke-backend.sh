#!/usr/bin/env bash
set -euo pipefail

INFRA_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
requested_port="${BACKEND_PORT:-}"
source "$INFRA_DIR/scripts/load-env.sh"
load_infra_env "$INFRA_DIR"

port="${requested_port:-${BACKEND_PORT:-8080}}"
url="http://127.0.0.1:${port}/actuator/health/readiness"

for _ in $(seq 1 45); do
  if python3 - "$url" <<'PY'
import json
import sys
import urllib.request

try:
    with urllib.request.urlopen(sys.argv[1], timeout=2) as response:
        payload = json.load(response)
        if response.status != 200 or payload.get("status") != "UP":
            raise SystemExit(1)
except Exception:
    raise SystemExit(1)
PY
  then
    echo "[backend-smoke] passed"
    exit 0
  fi
  sleep 2
done

echo "[backend-smoke] readiness endpoint did not become ready" >&2
exit 1
