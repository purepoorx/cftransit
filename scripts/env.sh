#!/usr/bin/env bash
# 编译环境配置 - cftransit 项目
SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
ROOT_DIR="/root/demo"

export JAVA_HOME="$ROOT_DIR/sdk/jdk"
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_HOME="$ROOT_DIR/sdk/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/26.3.11579264"
export PATH="$ROOT_DIR/sdk/go/bin:$PATH"
export GOPATH="$ROOT_DIR/.go"
export PATH="$GOPATH/bin:$PATH"
export PATH="$ROOT_DIR/sdk/gradle/bin:$PATH"
export GRADLE_USER_HOME="$SCRIPT_DIR/.gradle"
export GOCACHE="$SCRIPT_DIR/.cache/go-build"
export GOMOBILECACHE="$SCRIPT_DIR/.cache/gomobile"
export TMPDIR="$SCRIPT_DIR/.tmp"

mkdir -p "$GRADLE_USER_HOME" "$GOCACHE" "$GOMOBILECACHE" "$TMPDIR" "$SCRIPT_DIR/../android/app/libs"

echo "[env] java: $(java -version 2>&1 | head -1)"
echo "[env] go: $(go version)"
echo "[env] gomobile: $($GOPATH/bin/gomobile version 2>&1)"
echo "[env] gradle: $(gradle --version 2>&1 | grep 'Gradle ')"
echo "[env] ANDROID_HOME: $ANDROID_HOME"
echo "[env] ANDROID_NDK_HOME: $ANDROID_NDK_HOME"
