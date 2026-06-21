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

assert_contains "demo-nodes/app-node/Dockerfile" "dependency:get -Dartifact=org.apache.skywalking:apm-agent:"
assert_contains "demo-nodes/app-node/Dockerfile" "MAVEN_MIRROR_URL"
assert_not_contains "demo-nodes/app-node/Dockerfile" "repo1.maven.org/maven2/org/apache/skywalking/apm-agent"

assert_contains "demo-nodes/middleware-node/Dockerfile" "dependency:get -Dartifact=org.apache.skywalking:apm-agent:"
assert_contains "demo-nodes/middleware-node/Dockerfile" "MAVEN_MIRROR_URL"
assert_not_contains "demo-nodes/middleware-node/Dockerfile" "repo1.maven.org/maven2/org/apache/skywalking/apm-agent"

echo "skywalking agent source looks stable"
