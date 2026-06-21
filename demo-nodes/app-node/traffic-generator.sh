#!/usr/bin/env bash
set -euo pipefail

while true; do
  curl -fsS "http://127.0.0.1:8081/api/demo-chain?user=demo-user" >/dev/null || true
  sleep 20
done
