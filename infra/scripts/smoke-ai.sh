#!/usr/bin/env bash
set -euo pipefail

INFRA_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
requested_port="${AI_PORT:-}"
source "$INFRA_DIR/scripts/load-env.sh"
load_infra_env "$INFRA_DIR"

port="${requested_port:-${AI_PORT:-8000}}"
url="http://127.0.0.1:${port}/health"

for _ in $(seq 1 30); do
  if python3 - "$url" <<'PY'
import json
import sys
import urllib.request

try:
    with urllib.request.urlopen(sys.argv[1], timeout=2) as response:
        payload = json.load(response)
        if response.status != 200 or payload.get("status") not in {
            "ok",
            "degraded",
        }:
            raise SystemExit(1)
except Exception:
    raise SystemExit(1)
PY
  then
    echo "[ai-smoke] passed"
    exit 0
  fi
  sleep 1
done

echo "[ai-smoke] health endpoint did not become ready" >&2
exit 1
