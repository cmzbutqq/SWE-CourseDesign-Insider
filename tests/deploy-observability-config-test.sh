#!/usr/bin/env bash
set -euo pipefail

assert_contains() {
  local file="$1"
  local pattern="$2"

  if ! grep -Fq -- "$pattern" "$file"; then
    echo "Missing pattern in $file: $pattern" >&2
    exit 1
  fi
}

assert_not_exists() {
  local path="$1"

  if [[ -e "$path" ]]; then
    echo "Unexpected path exists: $path" >&2
    exit 1
  fi
}

assert_contains "deploy/deploy-main.sh" 'wait_for_http "http://localhost:${PROMETHEUS_PORT:-19090}/prometheus/-/healthy"'
assert_contains "deploy/deploy-main.sh" 'wait_for_contains "http://localhost:${PROMETHEUS_PORT:-19090}/prometheus/api/v1/targets" "sample-service"'
assert_contains "docker-compose.yml" 'VITE_SKYWALKING_BASE_URL: /skywalking'
assert_contains "docker-compose.yml" 'VITE_SKYWALKING_PROXY_TARGET: http://skywalking-ui:8080'
assert_contains "frontend/vite.config.js" '"/skywalking"'
assert_not_exists "infra/grafana/dashboards/monitoring-overview.json"

echo "deploy observability config looks good"
