# AGENTS.md

## Cursor Cloud specific instructions

### Project overview

SCUT Unified Monitoring Platform (课题二一体化监控平台) — a containerized infrastructure monitoring system. All services run inside Docker containers via `docker-compose.yml`. See `README.md` for directory layout and quick-start commands.

### Prerequisites

- Docker and docker compose must be available. In Cloud Agent VMs, Docker requires `fuse-overlayfs` storage driver and `iptables-legacy` (see environment setup).
- Copy `.env.example` to `.env` before first run (if `.env` does not already exist).

### Starting services

```bash
# Core dev environment (mysql + backend + frontend)
DOCKER_BUILDKIT=0 COMPOSE_DOCKER_CLI_BUILD=0 docker compose up -d mysql backend frontend

# Full E2E with observability + simulated nodes
DOCKER_BUILDKIT=0 COMPOSE_DOCKER_CLI_BUILD=0 docker compose --profile observability --profile nodes up -d --build
```

`DOCKER_BUILDKIT=0 COMPOSE_DOCKER_CLI_BUILD=0` is required in Cloud Agent VMs to avoid BuildKit issues.

### Key ports

| Service  | Port  |
|----------|-------|
| Frontend | 15173 |
| Backend  | 18081 |
| MySQL    | 13306 |

### Running tests

- **Backend (Java/Maven):** `docker compose exec backend mvn -q test` — uses H2 in-memory DB for tests, no MySQL needed.
- **Agent (Go):** `cd agent && go test ./...` — Go 1.22+ with toolchain auto-download to 1.23.
- **Frontend build:** `docker compose exec frontend npm run build` — no dedicated lint/test scripts; `vite build` validates compilation.
- **Smoke test:** `bash tests/smoke-test.sh` — requires all profiles (observability + nodes) running.

### API notes

- Agent registration: `POST /api/agents/register` (see `AgentRegisterRequest` DTO for payload shape)
- Heartbeat: `POST /api/agents/heartbeat`
- Portal read endpoints: `GET /api/overview`, `GET /api/nodes`, `GET /api/services`
- `/api` root returns 404 — this is expected; use specific endpoints above.

### Gotchas

- Backend container runs `mvn dependency:go-offline` on first start, which can take a few minutes. Wait for `http://localhost:18081/api/overview` to respond before testing.
- Frontend container runs `npm install` on every start. Modules are cached in a Docker named volume (`frontend-node-modules`).
- When stopping: `docker compose down -v` removes volumes (data loss). Use `docker compose down` without `-v` to preserve data.
