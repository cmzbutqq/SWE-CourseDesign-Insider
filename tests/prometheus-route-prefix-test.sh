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

assert_contains "docker-compose.yml" "--web.route-prefix=/prometheus"
assert_contains "tests/smoke-test.sh" 'wait_for_http "http://localhost:19090/prometheus/-/healthy"'
assert_contains "tests/smoke-test.sh" 'wait_for_contains "http://localhost:19090/prometheus/api/v1/targets" "sample-service" 24 5'

echo "prometheus route-prefix smoke config looks good"
