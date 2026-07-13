#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

failed=0

report_paths() {
  local title="$1"
  shift
  local -a command=("$@")
  local output
  output="$("${command[@]}" 2>/dev/null || true)"
  if [[ -n "$output" ]]; then
    echo "[public-check] $title"
    echo "$output"
    failed=1
  fi
}

report_paths "nested Git metadata is not allowed" \
  find apps services -type d -name .git -print

report_paths "service-level env templates are not allowed; use infra/.env.example" \
  find apps services -type f -name '.env.example' -print

report_paths "nested Compose files are not allowed; use infra/compose.yaml" \
  find apps services -type f \( -iname '*compose*.yml' -o -iname '*compose*.yaml' \) -print

report_paths "service Docker definitions are not allowed; use infra/docker" \
  find apps services -type f \( -name 'Dockerfile*' -o -name '.dockerignore' \) -print

report_paths "Backend operations must live under infra" \
  find services/backend -maxdepth 1 -type d \( \
    -name scripts -o -name observability -o -name performance \
  \) -print

report_paths "Mobile runtime config must live under infra/config or infra/generated" \
  find apps/mobile -maxdepth 1 -type d -name config -print

report_paths "legacy root env template is not allowed; use infra/.env.example" \
  find . -maxdepth 1 -type f -name '.env.example' -print

report_paths "legacy root Compose file is not allowed; use infra/compose.yaml" \
  find . -maxdepth 1 -type f \( -iname '*compose*.yml' -o -iname '*compose*.yaml' \) -print

report_paths "private configuration or credentials found" \
  find apps services -type f \( \
    -name '.env' -o -name '.env.local' -o -name '.env.production' -o \
    -name 'client_secret_*.json' -o -name 'google-services.json' -o \
    -name 'GoogleService-Info.plist' -o -name 'key.properties' -o \
    -iname '*firebase*admin*.json' -o -iname '*service-account*.json' -o \
    -name '*.jks' -o -name '*.keystore' -o -name '*.pem' -o -name '*.key' \
  \) -print

report_paths "Firebase service-account JSON must remain outside infra" \
  find infra -type f \( \
    -iname '*firebase*admin*.json' -o -iname '*service-account*.json' \
  \) -print

report_paths "Backend runtime configuration outside infra found" \
  find services/backend/src/main/resources -maxdepth 1 -type f \
    -name 'application*.properties' -print

report_paths "service code must receive environment variables from infra, not load dotenv files" \
  rg --files-with-matches --glob '*.py' 'load_dotenv[[:space:]]*\(' services

report_paths "Backend IDE metadata found" \
  find services/backend -type d -name .idea -print

report_paths "private AI artifacts found" \
  find services/ai -type f \( \
    -name '*.pt' -o -name '*.pth' -o -name '*.db' -o \
    -name '*.sqlite' -o -name '*.sqlite3' -o -name '*.pdf' \
  \) -print

report_paths "non-public farm seed export found; use farms-public.tsv" \
  find services/backend/src/main/resources/data -maxdepth 1 -type f \( \
    -name 'farmList.xlsx' -o -name 'farmList2.xls' \
  \) -print

report_paths "files larger than 20 MiB require explicit artifact review" \
  find . -type f -size +20M \
    ! -path './apps/mobile/.dart_tool/*' \
    ! -path './apps/mobile/build/*' \
    ! -path './apps/mobile/**/build/*' \
    ! -path './apps/mobile/android/gradle/wrapper/gradle-wrapper.jar' \
    ! -path './services/backend/build/*' \
    -print

report_paths "private-network URL found in source" \
  rg --files-with-matches --glob '!scripts/check-public-tree.sh' \
    'https?://(10\.|192\.168\.|172\.(1[6-9]|2[0-9]|3[01])\.)' .

report_paths "common secret signature found; rotate and review without printing the value" \
  rg --files-with-matches --hidden \
    --glob '!.git/**' --glob '!scripts/check-public-tree.sh' \
    '(BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY|AKIA[0-9A-Z]{16}|gh[pousr]_[A-Za-z0-9_]{20,}|AIza[0-9A-Za-z_-]{30,})' .

report_paths "hard-coded client app key found" \
  rg --files-with-matches --glob '*.dart' --glob '*.xml' --glob '*.plist' \
    '\b[0-9a-fA-F]{32}\b' apps/mobile

if [[ "$failed" -ne 0 ]]; then
  echo "[public-check] failed"
  exit 1
fi

echo "[public-check] passed"
