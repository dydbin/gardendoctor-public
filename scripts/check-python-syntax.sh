#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

python3 - "$ROOT_DIR/services/ai" <<'PY'
import ast
import pathlib
import sys

root = pathlib.Path(sys.argv[1])
files = sorted(root.rglob("*.py"))
for path in files:
    ast.parse(path.read_text(encoding="utf-8"), filename=str(path))
print(f"[ai-syntax] parsed {len(files)} Python files")
PY

