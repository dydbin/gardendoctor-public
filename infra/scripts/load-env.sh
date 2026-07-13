#!/usr/bin/env bash

# Load a trusted dotenv-style file without executing its contents.
load_env_file() {
  local env_file="$1"
  local line key value

  if [[ ! -f "$env_file" ]]; then
    echo "Missing env file: $env_file" >&2
    return 1
  fi

  while IFS= read -r line || [[ -n "$line" ]]; do
    line="${line%$'\r'}"

    if [[ "$line" =~ ^[[:space:]]*$ || "$line" =~ ^[[:space:]]*# ]]; then
      continue
    fi

    line="${line#export }"
    if [[ "$line" != *=* ]]; then
      echo "Invalid env assignment in $env_file" >&2
      return 1
    fi

    key="${line%%=*}"
    value="${line#*=}"
    key="${key#"${key%%[![:space:]]*}"}"
    key="${key%"${key##*[![:space:]]}"}"
    if [[ ! "$key" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]; then
      echo "Invalid env key in $env_file" >&2
      return 1
    fi
    export "$key=$value"
  done < "$env_file"
}

load_infra_env() {
  local infra_dir="$1"
  load_env_file "$infra_dir/.env.example"
  if [[ -f "$infra_dir/.env" ]]; then
    load_env_file "$infra_dir/.env"
  fi
}

# Translate canonical infrastructure values for a Backend process running on the host.
configure_backend_host_env() {
  export SERVER_PORT="${SERVER_PORT:-${BACKEND_PORT:-8080}}"
  export SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL:-jdbc:mysql://127.0.0.1:${MYSQL_HOST_PORT:-3307}/${MYSQL_DATABASE:-gardendoctor}?serverTimezone=Asia/Seoul&characterEncoding=UTF-8&rewriteBatchedStatements=true}"
  export SPRING_DATASOURCE_USERNAME="${SPRING_DATASOURCE_USERNAME:-${MYSQL_USER:-gardendoctor}}"
  export SPRING_DATASOURCE_PASSWORD="${SPRING_DATASOURCE_PASSWORD:-${MYSQL_PASSWORD:-}}"
  export SPRING_DATA_REDIS_HOST="${SPRING_DATA_REDIS_HOST:-127.0.0.1}"
  export SPRING_DATA_REDIS_PORT="${SPRING_DATA_REDIS_PORT:-${REDIS_HOST_PORT:-6379}}"
  export PYTHON_SERVER_URL="${PYTHON_SERVER_URL:-http://127.0.0.1:${AI_PORT:-8000}}"
}
