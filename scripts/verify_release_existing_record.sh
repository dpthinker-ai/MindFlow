#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APK_PATH="$ROOT_DIR/app/build/outputs/apk/release/app-release.apk"
PACKAGE_NAME="com.mindflow.app"
TARGET_TITLE="${TARGET_TITLE:-现代高压社会下的身心枯竭与生存危机}"
OUT_DIR="${OUT_DIR:-$ROOT_DIR/build/release-existing-record-verification/$(date +%Y%m%d-%H%M%S)}"
SKIP_BUILD=0

ADB="${ADB:-${ANDROID_HOME:-}/platform-tools/adb}"
if [[ ! -x "$ADB" ]]; then
  ADB="$(command -v adb || true)"
fi

while [[ $# -gt 0 ]]; do
  case "$1" in
    --skip-build)
      SKIP_BUILD=1
      shift
      ;;
    --target-title)
      TARGET_TITLE="$2"
      shift 2
      ;;
    --out-dir)
      OUT_DIR="$2"
      shift 2
      ;;
    *)
      echo "Unknown argument: $1" >&2
      exit 2
      ;;
  esac
done

log() {
  printf '[release-existing-record] %s\n' "$*"
}

fail() {
  printf '[release-existing-record] ERROR: %s\n' "$*" >&2
  exit 1
}

run_adb() {
  "$ADB" "$@"
}

prepare_device() {
  [[ -n "$ADB" && -x "$ADB" ]] || fail "adb not found. Set ANDROID_HOME or ADB."
  mkdir -p "$OUT_DIR"
  run_adb wait-for-device
  if [[ "$SKIP_BUILD" -eq 0 ]]; then
    (cd "$ROOT_DIR" && ./gradlew --no-daemon :app:assembleRelease)
  fi
  [[ -f "$APK_PATH" ]] || fail "Release APK missing: $APK_PATH"
  case "$APK_PATH" in
    *release*.apk) ;;
    *) fail "APK path is not the release artifact: $APK_PATH" ;;
  esac
  log "Installing release APK: $APK_PATH"
  run_adb install -r "$APK_PATH"
  run_adb root >/dev/null 2>&1 || true
  run_adb wait-for-device
}

note_count() {
  run_adb shell "ls /data/user/0/$PACKAGE_NAME/files/notes | wc -l" | tr -d '[:space:]'
}

e2e_count() {
  run_adb shell "grep -rl '主题: E2E_' /data/user/0/$PACKAGE_NAME/files/notes | wc -l" | tr -d '[:space:]'
}

dump_ui() {
  run_adb shell uiautomator dump /sdcard/window.xml >/dev/null
  run_adb exec-out cat /sdcard/window.xml > "$OUT_DIR/window.xml"
}

ui_has_text() {
  local target="$1"
  python3 - "$OUT_DIR/window.xml" "$target" <<'PY'
import sys
import xml.etree.ElementTree as ET

path, target = sys.argv[1], sys.argv[2]
root = ET.parse(path).getroot()
for node in root.iter("node"):
    if node.attrib.get("text") == target or node.attrib.get("content-desc") == target:
        sys.exit(0)
sys.exit(1)
PY
}

wait_for_text() {
  local target="$1"
  local timeout="${2:-15}"
  local elapsed=0
  while (( elapsed < timeout )); do
    dump_ui
    if ui_has_text "$target"; then
      return 0
    fi
    sleep 1
    elapsed=$((elapsed + 1))
  done
  cp "$OUT_DIR/window.xml" "$OUT_DIR/missing-${target//[^[:alnum:]]/_}.xml" 2>/dev/null || true
  fail "Timed out waiting for text/content description: $target"
}

tap_text() {
  local target="$1"
  wait_for_text "$target" 15
  local point
  point="$(python3 - "$OUT_DIR/window.xml" "$target" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

path, target = sys.argv[1], sys.argv[2]
root = ET.parse(path).getroot()
pattern = re.compile(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]")
for node in root.iter("node"):
    if node.attrib.get("text") == target or node.attrib.get("content-desc") == target:
        match = pattern.fullmatch(node.attrib.get("bounds", ""))
        if not match:
            continue
        left, top, right, bottom = map(int, match.groups())
        print(f"{(left + right) // 2} {(top + bottom) // 2}")
        sys.exit(0)
sys.exit(1)
PY
)"
  run_adb shell input tap "$point"
}

assert_text_absent_now() {
  local target="$1"
  dump_ui
  if ui_has_text "$target"; then
    cp "$OUT_DIR/window.xml" "$OUT_DIR/unexpected-${target//[^[:alnum:]]/_}.xml" 2>/dev/null || true
    fail "Unexpected text/content description is visible: $target"
  fi
}

main() {
  prepare_device
  local before_notes before_e2e after_notes after_e2e
  before_notes="$(note_count)"
  before_e2e="$(e2e_count)"
  log "Before verification: notes=$before_notes e2e=$before_e2e"

  run_adb shell am start -n "$PACKAGE_NAME/.SplashActivity" >/dev/null
  wait_for_text "最近记录" 25
  wait_for_text "$TARGET_TITLE" 20
  tap_text "$TARGET_TITLE"
  wait_for_text "文本记录" 20

  for text in \
    "标题" \
    "重新生成标题" \
    "AI 洞察" \
    "正文" \
    "润色正文" \
    "相关主题"
  do
    wait_for_text "$text" 15
  done

  assert_text_absent_now "AI 整理"
  assert_text_absent_now "归档与时间"
  assert_text_absent_now "标题（可编辑）"
  assert_text_absent_now "生成标题"
  assert_text_absent_now "润色标题"
  assert_text_absent_now "正文内容（可全文编辑）"
  assert_text_absent_now "AI 摘要"
  assert_text_absent_now "重新生成摘要与要点"
  assert_text_absent_now "关键要点"
  assert_text_absent_now "B"
  assert_text_absent_now "I"
  assert_text_absent_now "U"
  assert_text_absent_now "☑"
  run_adb exec-out screencap -p > "$OUT_DIR/text-record-existing.png"

  after_notes="$(note_count)"
  after_e2e="$(e2e_count)"
  [[ "$before_notes" == "$after_notes" ]] || fail "Note count changed: before=$before_notes after=$after_notes"
  [[ "$after_e2e" == "0" ]] || fail "Generated E2E notes remain: $after_e2e"
  log "Existing-record verification passed. Artifacts: $OUT_DIR"
}

main
