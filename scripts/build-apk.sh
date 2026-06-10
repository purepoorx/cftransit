#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
. "$SCRIPT_DIR/env.sh"

PROJECT_DIR=$(cd "$SCRIPT_DIR/.." && pwd)
GOMOBILE="$GOPATH/bin/gomobile"

if ! command -v go >/dev/null 2>&1; then
  echo "[build] go not found in PATH"
  exit 1
fi
if ! command -v gradle >/dev/null 2>&1; then
  echo "[build] gradle not found in PATH"
  exit 1
fi

# 写入 local.properties
printf "sdk.dir=%s\n" "$ANDROID_HOME" > "$PROJECT_DIR/android/local.properties"

echo "[build] Binding AAR..."
$GOMOBILE bind \
  -target=android \
  -androidapi 24 \
  -javapkg com.cftransit.app \
  -trimpath \
  -ldflags="-s -w" \
  -o "$PROJECT_DIR/android/app/libs/cftransit.aar" \
  "$PROJECT_DIR/core"

echo "[build] Assembling APK (debug)..."
cd "$PROJECT_DIR/android"
gradle clean assembleDebug --no-daemon
echo "[build] Done! APK at android/app/build/outputs/apk/debug/"
