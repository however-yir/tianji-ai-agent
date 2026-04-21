#!/usr/bin/env bash
set -euo pipefail

WITH_NACOS=0
WITH_SEARCH=0
DETACH=0
RESET_ENV=0

usage() {
  cat <<'EOF'
Usage: scripts/quick-start-mac.sh [options]

Options:
  --with-nacos   Enable nacos profile
  --with-search  Enable elasticsearch profile
  --detach       Run docker compose in detached mode
  --reset-env    Overwrite .env from .env.example
  -h, --help     Show this help message
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --with-nacos)
      WITH_NACOS=1
      shift
      ;;
    --with-search)
      WITH_SEARCH=1
      shift
      ;;
    --detach)
      DETACH=1
      shift
      ;;
    --reset-env)
      RESET_ENV=1
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage
      exit 1
      ;;
  esac
done

if [[ "$(uname -s)" != "Darwin" ]]; then
  echo "Warning: this script is intended for macOS. Continuing anyway." >&2
fi

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_ROOT"

if ! command -v docker >/dev/null 2>&1; then
  echo "Error: docker is not installed or not in PATH." >&2
  exit 1
fi

if docker compose version >/dev/null 2>&1; then
  COMPOSE_CMD=(docker compose)
elif command -v docker-compose >/dev/null 2>&1; then
  COMPOSE_CMD=(docker-compose)
else
  echo "Error: docker compose (v2) or docker-compose (v1) is required." >&2
  exit 1
fi

if [[ ! -f .env.example ]]; then
  echo "Error: .env.example not found in project root." >&2
  exit 1
fi

if [[ ! -f .env || $RESET_ENV -eq 1 ]]; then
  cp .env.example .env
  echo "Created .env from .env.example"
else
  echo ".env already exists. Keeping current file (use --reset-env to overwrite)."
fi

COMPOSE_ARGS=(-f docker-compose.dev.yml)

if [[ $WITH_NACOS -eq 1 ]]; then
  COMPOSE_ARGS+=(--profile nacos)
fi

if [[ $WITH_SEARCH -eq 1 ]]; then
  COMPOSE_ARGS+=(--profile search)
fi

COMPOSE_ARGS+=(up --build)

if [[ $DETACH -eq 1 ]]; then
  COMPOSE_ARGS+=(-d)
fi

echo "Running: ${COMPOSE_CMD[*]} ${COMPOSE_ARGS[*]}"
"${COMPOSE_CMD[@]}" "${COMPOSE_ARGS[@]}"

cat <<'EOF'

Services are ready (or starting) at:
- Frontend: http://127.0.0.1:5173
- Backend:  http://127.0.0.1:8094
- Hot API:  http://127.0.0.1:8094/session/hot
EOF
