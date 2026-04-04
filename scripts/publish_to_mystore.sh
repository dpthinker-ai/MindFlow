#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MYSTORE_ROOT="${MYSTORE_ROOT:-/home/dpthinker/MyStore}"
ANDROID_HOME="${ANDROID_HOME:-/home/dpthinker/Android/Sdk}"
ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"

cd "$PROJECT_ROOT"
ANDROID_HOME="$ANDROID_HOME" ANDROID_SDK_ROOT="$ANDROID_SDK_ROOT" ./gradlew --no-daemon :app:assembleRelease
python3 "$MYSTORE_ROOT/scripts/publish_release.py" --project "$PROJECT_ROOT" "$@"
