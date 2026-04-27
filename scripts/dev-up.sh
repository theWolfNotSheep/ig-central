#!/usr/bin/env bash
#
# scripts/dev-up.sh — one-command bring-up for the local dev stack.
#
# Wraps `docker compose up --build -d` with sanity checks the user
# would otherwise hit one at a time:
#   • Docker daemon is running.
#   • .env exists and the always-required keys are populated.
#   • The compose stack is healthy after start.
#
# Usage:
#   scripts/dev-up.sh                    # build + start the full stack
#   scripts/dev-up.sh api mongo          # start only the named services
#   scripts/dev-up.sh --no-build         # start without rebuilding images
#
# Exit codes:
#   0 = stack is up and healthy
#   1 = pre-flight check failed
#   2 = compose up failed
#   3 = healthcheck timed out

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${REPO_ROOT}"

# --- Pre-flight ---------------------------------------------------------

step() { printf "\n→ %s\n" "$1"; }

step "Checking Docker daemon"
if ! docker info >/dev/null 2>&1; then
    echo "  docker daemon not reachable. Start Docker Desktop and re-run." >&2
    exit 1
fi
echo "  ok"

step "Checking .env"
if [ ! -f "${REPO_ROOT}/.env" ]; then
    echo "  .env missing. Copy .env.example to .env and fill in the empty keys before running." >&2
    exit 1
fi
required_keys=(MONGO_PASSWORD ADMIN_PASSWORD JWT_SECRET)
missing=()
for key in "${required_keys[@]}"; do
    value="$(grep -E "^${key}=" .env | head -1 | cut -d= -f2- || true)"
    if [ -z "${value}" ]; then
        missing+=("${key}")
    fi
done
if [ ${#missing[@]} -gt 0 ]; then
    echo "  .env keys empty: ${missing[*]}" >&2
    echo "  fill them in before continuing — JWT_SECRET in particular must be set in any non-throwaway env." >&2
    exit 1
fi
echo "  ok"

# --- Compose up --------------------------------------------------------

step "docker compose up"
build_flag="--build"
services=()
for arg in "$@"; do
    case "${arg}" in
        --no-build) build_flag="" ;;
        *) services+=("${arg}") ;;
    esac
done

if ! docker compose up ${build_flag} -d "${services[@]}"; then
    echo "  docker compose up failed." >&2
    exit 2
fi

# --- Health wait -------------------------------------------------------

step "Waiting for services to report healthy"
timeout_secs=120
deadline=$(($(date +%s) + timeout_secs))
while true; do
    state="$(docker compose ps --format '{{.Service}} {{.State}} {{.Health}}' 2>/dev/null || true)"
    if [ -z "${state}" ]; then
        echo "  no services running yet" >&2
        sleep 2
        continue
    fi
    unhealthy=$(printf '%s\n' "${state}" | awk '$2 == "running" && $3 != "" && $3 != "healthy" { print $1 }')
    if [ -z "${unhealthy}" ]; then
        printf '%s\n' "${state}" | sed 's/^/  /'
        break
    fi
    if [ "$(date +%s)" -ge "${deadline}" ]; then
        echo "  timed out after ${timeout_secs}s waiting on:" >&2
        printf '%s\n' "${unhealthy}" | sed 's/^/    /' >&2
        echo "  inspect with: docker compose ps && docker compose logs <service>" >&2
        exit 3
    fi
    sleep 3
done

# --- Summary -----------------------------------------------------------

step "Stack is up"
cat <<EOF
  Frontend:        http://localhost
  API actuator:    http://localhost:8080/actuator/health
  Prometheus:      http://localhost:9090
  Grafana:         http://localhost:3003 (admin / admin)
  RabbitMQ admin:  http://localhost:15672 (guest / guest)
  MinIO console:   http://localhost:9001

  Tail logs:       docker compose logs -f api
  Tear down:       docker compose down
EOF
