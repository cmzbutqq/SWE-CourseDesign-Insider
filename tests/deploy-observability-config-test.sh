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
assert_contains "deploy/deploy-main.sh" 'wait_for_business_traces "http://localhost:${BACKEND_PORT:-18081}/api/tracing/summary" "/api/demo-chain"'
assert_contains "deploy/deploy-main.sh" 'docker compose exec -T app-node sh -c'
assert_contains "deploy/deploy-main.sh" '/api/demo-chain?user=deploy-warmup-'
assert_contains "deploy/deploy-main.sh" 'Still waiting for business traces'
assert_contains "docker-compose.yml" 'VITE_SKYWALKING_BASE_URL: ${PUBLIC_SCHEME:-http}://${PUBLIC_HOST:-localhost}:${SKYWALKING_UI_PORT:-18082}'
assert_contains "docker-compose.yml" 'VITE_SKYWALKING_PROXY_TARGET: http://skywalking-ui:8080'
assert_contains "frontend/vite.config.js" '"/skywalking"'
assert_contains ".github/workflows/deploy-main.yml" 'ServerAliveInterval=30'
assert_contains ".github/workflows/deploy-main.yml" 'ServerAliveCountMax=20'
assert_contains "demo-nodes/app-node/Dockerfile" 'apache-skywalking-java-agent-${SKYWALKING_AGENT_VERSION}.tgz'
assert_contains "demo-nodes/app-node/Dockerfile" 'COPY --from=skywalking-agent-source /skywalking-agent /opt/skywalking'
assert_contains "demo-nodes/middleware-node/Dockerfile" 'apache-skywalking-java-agent-${SKYWALKING_AGENT_VERSION}.tgz'
assert_contains "demo-nodes/middleware-node/Dockerfile" 'COPY --from=skywalking-agent-source /skywalking-agent /opt/skywalking'
assert_not_exists "infra/grafana/dashboards/monitoring-overview.json"

echo "deploy observability config looks good"
