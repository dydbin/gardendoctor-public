#!/usr/bin/env bash
set -euo pipefail

INFRA_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ROOT_DIR="$(cd "$INFRA_DIR/.." && pwd)"
DEFAULT_ENV_FILE="$INFRA_DIR/.env.example"
ENV_FILE="${1:-$INFRA_DIR/.env}"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Missing infra env file." >&2
  exit 1
fi

source "$INFRA_DIR/scripts/load-env.sh"
load_env_file "$DEFAULT_ENV_FILE"
load_env_file "$ENV_FILE"

secret_path="${FIREBASE_SERVICE_ACCOUNT_HOST_PATH:-}"
if [[ -z "$secret_path" || "$secret_path" != /* ]]; then
  echo "FIREBASE_SERVICE_ACCOUNT_HOST_PATH must be an absolute path." >&2
  exit 1
fi
if [[ ! -f "$secret_path" || ! -r "$secret_path" ]]; then
  echo "Firebase service-account file is missing or unreadable." >&2
  exit 1
fi

resolved_secret_path="$(realpath "$secret_path")"
case "$resolved_secret_path" in
  "$ROOT_DIR"/*)
    echo "Firebase service-account JSON must remain outside the repository." >&2
    exit 1
    ;;
esac

echo "[firebase-secret] external read-only source validated"
