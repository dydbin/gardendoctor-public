#!/usr/bin/env python3
"""Render the mobile-safe subset of infra/.env without exposing server secrets."""

from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import re
import tempfile


MOBILE_KEYS = (
    "API_BASE_URL",
    "KAKAO_NATIVE_APP_KEY",
    "KAKAO_MAP_APP_KEY",
    "FIREBASE_API_KEY",
    "FIREBASE_APP_ID",
    "FIREBASE_MESSAGING_SENDER_ID",
    "FIREBASE_PROJECT_ID",
    "FIREBASE_STORAGE_BUCKET",
)
ENV_KEY_PATTERN = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*$")


def parse_env_file(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for line_number, raw_line in enumerate(
        path.read_text(encoding="utf-8").splitlines(), start=1
    ):
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("export "):
            line = line.removeprefix("export ").lstrip()
        if "=" not in line:
            raise ValueError(f"invalid assignment at {path}:{line_number}")

        key, value = line.split("=", 1)
        key = key.strip()
        value = value.strip()
        if not ENV_KEY_PATTERN.fullmatch(key):
            raise ValueError(f"invalid environment key at {path}:{line_number}")
        if len(value) >= 2 and value[0] == value[-1] and value[0] in {"'", '"'}:
            value = value[1:-1]
        values[key] = value
    return values


def render(env_file: Path, output_file: Path) -> None:
    if not env_file.is_file():
        raise FileNotFoundError(
            f"missing infra env file: {env_file}; copy infra/.env.example to infra/.env first"
        )

    env_values = parse_env_file(env_file)
    api_base_url = env_values.get("API_BASE_URL", "").strip()
    if not api_base_url:
        raise ValueError(f"API_BASE_URL must be set in {env_file}")

    mobile_config = {key: env_values.get(key, "") for key in MOBILE_KEYS}
    output_file.parent.mkdir(parents=True, exist_ok=True)

    descriptor, temp_name = tempfile.mkstemp(
        dir=output_file.parent,
        prefix=f".{output_file.name}.",
        text=True,
    )
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8") as temp_file:
            json.dump(mobile_config, temp_file, ensure_ascii=False, indent=2)
            temp_file.write("\n")
        os.chmod(temp_name, 0o600)
        os.replace(temp_name, output_file)
    except Exception:
        try:
            os.unlink(temp_name)
        except FileNotFoundError:
            pass
        raise

    print(f"[mobile-config] rendered {output_file}")


def main() -> None:
    infra_dir = Path(__file__).resolve().parent.parent
    parser = argparse.ArgumentParser()
    parser.add_argument("--env-file", type=Path, default=infra_dir / ".env")
    parser.add_argument(
        "--output",
        type=Path,
        default=infra_dir / "generated/mobile/app.local.json",
    )
    args = parser.parse_args()
    render(args.env_file.resolve(), args.output.resolve())


if __name__ == "__main__":
    main()
