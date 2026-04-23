#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MYSTORE_ROOT="${MYSTORE_ROOT:-/home/dpthinker/MyStore}"
ANDROID_HOME="${ANDROID_HOME:-/home/dpthinker/Android/Sdk}"
ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"
JAVA_HOME="${JAVA_HOME:-$HOME/.jdks/temurin-21}"
DEFAULT_MYSTORE_ENV_FILE="$HOME/.config/mystore/publish.env"
MYSTORE_ENV_FILE="${MYSTORE_ENV_FILE:-$DEFAULT_MYSTORE_ENV_FILE}"

if [ -f "$MYSTORE_ENV_FILE" ]; then
  set -a
  # shellcheck disable=SC1090
  . "$MYSTORE_ENV_FILE"
  set +a
fi

cd "$PROJECT_ROOT"
ANDROID_HOME="$ANDROID_HOME" ANDROID_SDK_ROOT="$ANDROID_SDK_ROOT" JAVA_HOME="$JAVA_HOME" ./gradlew --no-daemon :app:assembleRelease
python3 "$MYSTORE_ROOT/scripts/publish_release.py" --project "$PROJECT_ROOT" "$@"
