#!/usr/bin/env bash
set -euo pipefail

# Usage: ./scripts/run-tests-container.sh [image] [volume-name]
# Defaults:
#   image -> localhost/base-angular-<cwd-lower>
#   volume -> angular-node-mods-<cwd-lower>

SERVICE_DIR=$(basename "$(pwd)")
SERVICE_LC=${SERVICE_DIR,,}
IMAGE=${1:-localhost/base-angular-${SERVICE_LC}}
VOLUME=${2:-angular-node-mods-${SERVICE_LC}}

echo "Using image: ${IMAGE}"
echo "Using node_modules volume: ${VOLUME}"

# Create volume if not exists
if ! podman volume inspect "${VOLUME}" > /dev/null 2>&1; then
  podman volume create "${VOLUME}"
fi

podman run --rm \
  -v "$(pwd)/src:/app/src" \
  -v "$(pwd)/public:/app/public" \
  -v "$(pwd)/dist:/app/dist" \
  -v "$(pwd)/test-results:/app/test-results" \
  -v "${VOLUME}:/app/node_modules" \
  -w /app \
  ${IMAGE} sh -lc '
    if [ ! -f /app/package-lock.json ]; then
      echo "package-lock.json not found in project root. Aborting."; exit 1;
    fi

    if [ ! -f /app/node_modules/.package-lock.json ] || ! cmp -s /app/package-lock.json /app/node_modules/.package-lock.json; then
      echo "Changes in dependencies detected or node_modules uninitialized. Running npm ci..."
      npm ci
      cp /app/package-lock.json /app/node_modules/.package-lock.json || true
    else
      echo "Dependencies up-to-date. Skipping npm ci."
    fi

    echo "Running tests..."
    npm run test:ci
  '

echo "Container finished."
