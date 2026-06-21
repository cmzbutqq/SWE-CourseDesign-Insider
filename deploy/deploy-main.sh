#!/usr/bin/env bash
set -euo pipefail

DEPLOY_PATH="${DEPLOY_PATH:-/opt/scut-monitoring}"
ENV_BACKUP="$(mktemp)"

cleanup() {
  rm -f "$ENV_BACKUP"
}

show_diagnostics() {
  docker compose ps || true
  docker compose logs --tail=80 backend frontend app-node middleware-node prometheus grafana skywalking-oap skywalking-ui || true
}

wait_for_http() {
  local url="$1"
  local retries="${2:-60}"
  local delay="${3:-5}"

  for ((i=1; i<=retries; i++)); do
    if curl -fsS "$url" >/dev/null 2>&1; then
      return 0
    fi
    sleep "$delay"
  done
  return 1
}

wait_for_contains() {
  local url="$1"
  local needle="$2"
  local retries="${3:-24}"
  local delay="${4:-5}"

  for ((i=1; i<=retries; i++)); do
    if curl -fsS "$url" 2>/dev/null | grep -q "$needle"; then
      return 0
    fi
    sleep "$delay"
  done
  return 1
}

trap cleanup EXIT
trap show_diagnostics ERR

cd "$DEPLOY_PATH"

if [[ ! -d .git ]]; then
  echo "Expected a git repository at $DEPLOY_PATH" >&2
  exit 1
fi

if [[ -f .env ]]; then
  cp .env "$ENV_BACKUP"
fi

git fetch origin main
git checkout -f main
git reset --hard origin/main

if [[ -s "$ENV_BACKUP" ]]; then
  cp "$ENV_BACKUP" .env
elif [[ ! -f .env ]]; then
  cp .env.example .env
fi

set -a
# shellcheck disable=SC1091
source .env
set +a

export DOCKER_BUILDKIT=0
export COMPOSE_DOCKER_CLI_BUILD=0

docker compose --profile observability --profile nodes up -d --build --force-recreate --remove-orphans

wait_for_http "http://localhost:${BACKEND_PORT:-18081}/api/overview"
wait_for_http "http://localhost:${FRONTEND_PORT:-15173}"
wait_for_http "http://localhost:${PROMETHEUS_PORT:-19090}/prometheus/-/healthy"
wait_for_http "http://localhost:${GRAFANA_PORT:-13000}/api/health"
wait_for_http "http://localhost:${SKYWALKING_UI_PORT:-18082}"

wait_for_contains "http://localhost:${BACKEND_PORT:-18081}/api/nodes" "app-node"
wait_for_contains "http://localhost:${BACKEND_PORT:-18081}/api/nodes" "middleware-node"
wait_for_contains "http://localhost:${BACKEND_PORT:-18081}/api/services" "SPRING_BOOT"
wait_for_contains "http://localhost:${BACKEND_PORT:-18081}/api/services" "MYSQL"
wait_for_contains "http://localhost:${PROMETHEUS_PORT:-19090}/prometheus/api/v1/targets" "sample-service"

echo "Deploy succeeded at commit $(git rev-parse --short HEAD)"
