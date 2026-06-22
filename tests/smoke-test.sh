#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

export DOCKER_BUILDKIT=0
export COMPOSE_DOCKER_CLI_BUILD=0

if [[ ! -f ".env" ]]; then
  cp .env.example .env
fi

cleanup() {
  docker compose down -v >/dev/null 2>&1 || true
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
  local retries="${3:-60}"
  local delay="${4:-5}"

  for ((i=1; i<=retries; i++)); do
    if curl -fsS "$url" 2>/dev/null | grep -q "$needle"; then
      return 0
    fi
    sleep "$delay"
  done
  return 1
}

cleanup
docker compose --profile observability --profile nodes up -d --build

wait_for_http "http://localhost:18081/api/overview"
wait_for_http "http://localhost:15173"
wait_for_http "http://localhost:19090/prometheus/-/healthy"
wait_for_http "http://localhost:13000/api/health"
wait_for_http "http://localhost:18082"

if ! wait_for_contains "http://localhost:18081/api/nodes" "app-node" 24 5; then
  echo "backend did not receive app-node registration"
  exit 1
fi

if ! wait_for_contains "http://localhost:18081/api/nodes" "middleware-node" 24 5; then
  echo "backend did not receive middleware-node registration"
  exit 1
fi

if ! wait_for_contains "http://localhost:18081/api/services" "SPRING_BOOT" 24 5; then
  echo "backend did not record sample-service"
  exit 1
fi

if ! wait_for_contains "http://localhost:18081/api/services" "MYSQL" 24 5; then
  echo "backend did not record mysql"
  exit 1
fi

if ! wait_for_contains "http://localhost:19090/prometheus/api/v1/targets" "sample-service" 24 5; then
  echo "prometheus did not scrape sample-service"
  exit 1
fi

if ! wait_for_contains "http://localhost:18081/api/tracing/summary" "/api/demo-chain" 36 5; then
  echo "skywalking did not surface business traces"
  exit 1
fi

echo "smoke test passed"
