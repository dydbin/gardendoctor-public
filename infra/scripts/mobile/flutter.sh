#!/usr/bin/env bash
set -euo pipefail

INFRA_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ROOT_DIR="$(cd "$INFRA_DIR/.." && pwd)"
MODE="${1:-run}"
CONFIG_FILE="${2:-$INFRA_DIR/generated/mobile/app.local.json}"

if [[ ! -f "$CONFIG_FILE" ]]; then
  echo "Missing generated mobile config: $CONFIG_FILE" >&2
  echo "Run: make -C infra app-config" >&2
  exit 1
fi

KAKAO_NATIVE_APP_KEY="$(python3 - "$CONFIG_FILE" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as config_file:
    print(json.load(config_file).get("KAKAO_NATIVE_APP_KEY", ""))
PY
)"

cd "$ROOT_DIR/apps/mobile"
case "$MODE" in
  run)
    exec env KAKAO_NATIVE_APP_KEY="$KAKAO_NATIVE_APP_KEY" \
      flutter run --dart-define-from-file="$CONFIG_FILE"
    ;;
  build-local)
    exec env KAKAO_NATIVE_APP_KEY="$KAKAO_NATIVE_APP_KEY" \
      flutter build apk --debug --dart-define-from-file="$CONFIG_FILE"
    ;;
  *)
    echo "Usage: $0 {run|build-local} [mobile-config-file]" >&2
    exit 1
    ;;
esac
