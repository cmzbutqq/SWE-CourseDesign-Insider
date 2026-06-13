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

assert_contains ".env.example" "GOPROXY="
assert_contains ".env.example" "MAVEN_MIRROR_URL="
assert_contains ".env.example" "NPM_REGISTRY="

assert_contains "docker-compose.yml" 'sh -c "sh /workspace/infra/maven/mvn-with-mirror.sh -q -DskipTests dependency:go-offline &&'
assert_contains "docker-compose.yml" 'MAVEN_MIRROR_URL: ${MAVEN_MIRROR_URL:-'
assert_contains "docker-compose.yml" 'NPM_CONFIG_REGISTRY: ${NPM_REGISTRY:-'
assert_contains "docker-compose.yml" 'GOPROXY: ${GOPROXY:-'
assert_contains "docker-compose.yml" 'MAVEN_MIRROR_URL: ${MAVEN_MIRROR_URL:-'

assert_contains "demo-nodes/app-node/Dockerfile" "ARG GOPROXY="
assert_contains "demo-nodes/app-node/Dockerfile" "ARG MAVEN_MIRROR_URL="
assert_contains "demo-nodes/middleware-node/Dockerfile" "ARG GOPROXY="

assert_contains "infra/maven/mvn-with-mirror.sh" 'MAVEN_MIRROR_URL'

echo "network mirror config looks good"
