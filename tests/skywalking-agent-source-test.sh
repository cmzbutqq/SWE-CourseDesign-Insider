#!/usr/bin/env bash
set -euo pipefail

assert_contains() {
  local file="$1"
  local pattern="$2"

  if ! grep -Fq "$pattern" "$file"; then
    echo "Missing pattern in $file: $pattern" >&2
    exit 1
  fi
}

assert_not_contains() {
  local file="$1"
  local pattern="$2"

  if grep -Fq "$pattern" "$file"; then
    echo "Unexpected pattern in $file: $pattern" >&2
    exit 1
  fi
}

assert_contains "demo-nodes/app-node/Dockerfile" "archive.apache.org/dist/skywalking/java-agent/"
assert_contains "demo-nodes/app-node/Dockerfile" "apache-skywalking-java-agent-\${SKYWALKING_AGENT_VERSION}.tgz"
assert_contains "demo-nodes/app-node/Dockerfile" "COPY --from=skywalking-agent-source /skywalking-agent /opt/skywalking"
assert_not_contains "demo-nodes/app-node/Dockerfile" "org.apache.skywalking:apm-agent"

assert_contains "demo-nodes/middleware-node/Dockerfile" "archive.apache.org/dist/skywalking/java-agent/"
assert_contains "demo-nodes/middleware-node/Dockerfile" "apache-skywalking-java-agent-\${SKYWALKING_AGENT_VERSION}.tgz"
assert_contains "demo-nodes/middleware-node/Dockerfile" "COPY --from=skywalking-agent-source /skywalking-agent /opt/skywalking"
assert_not_contains "demo-nodes/middleware-node/Dockerfile" "org.apache.skywalking:apm-agent"

echo "skywalking agent source looks stable"
