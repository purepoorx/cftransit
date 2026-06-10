#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
PROJECT_DIR=$(cd "$SCRIPT_DIR/.." && pwd)

echo "[build] Building server..."
cd "$PROJECT_DIR/server"
go build -trimpath -ldflags="-s -w" -o cftransit-server.exe .
echo "[build] Done! Binary at server/cftransit-server.exe"
