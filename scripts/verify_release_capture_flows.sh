#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APK_PATH="$ROOT_DIR/app/build/outputs/apk/release/app-release.apk"
PACKAGE_NAME="com.mindflow.app"
ENTRY_ACTIVITY="$PACKAGE_NAME/.EntryProxyActivity"
SPLASH_ACTIVITY="com.mindflow.app/.SplashActivity"
ACTION_CAPTURE="com.mindflow.app.action.OPEN_CAPTURE"
ACTION_VOICE="com.mindflow.app.action.OPEN_CAPTURE_VOICE"
ACTION_IMAGE="com.mindflow.app.action.OPEN_CAPTURE_IMAGE"
MODEL_PATH="/data/user/0/$PACKAGE_NAME/files/local-models/gemma-4-E4B-it.litertlm"
NOTES_DIR="/data/user/0/$PACKAGE_NAME/files/notes"
OUT_DIR="${OUT_DIR:-$ROOT_DIR/build/release-capture-flow-verification/$(date +%Y%m%d-%H%M%S)}"
RUN_ID="${RUN_ID:-$(date +%Y%m%d%H%M%S)}"
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
  printf '[release-capture-flow] %s\n' "$*"
}

fail() {
  printf '[release-capture-flow] ERROR: %s\n' "$*" >&2
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
  run_adb shell "ls /data/user/0/com.mindflow.app/files/notes | wc -l" | tr -d '[:space:]'
}

check_durable_data() {
  local label="$1"
  local model_info
  model_info="$(run_adb shell "ls -lh '$MODEL_PATH'" | tr -d '\r')" || fail "Missing Gemma model after $label"
  local count
  count="$(note_count)"
  [[ "$count" =~ ^[0-9]+$ ]] || fail "Could not read note count after $label: $count"
  log "$label durable data: $model_info; notes=$count"
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
  local timeout="${2:-12}"
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

assert_text_absent_now() {
  local target="$1"
  dump_ui
  if ui_has_text "$target"; then
    cp "$OUT_DIR/window.xml" "$OUT_DIR/unexpected-${target//[^[:alnum:]]/_}.xml" 2>/dev/null || true
    fail "Unexpected text/content description is visible: $target"
  fi
}

tap_text() {
  local target="$1"
  wait_for_text "$target" "${2:-12}"
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
  read -r x y <<< "$point"
  run_adb shell input tap "$x" "$y"
}

screenshot() {
  local name="$1"
  run_adb exec-out screencap -p > "$OUT_DIR/$name.png"
}

scroll_down() {
  run_adb shell input swipe 540 2200 540 900 700
  sleep 1
}

scroll_down_small() {
  run_adb shell input swipe 540 1500 540 1100 350
  sleep 1
}

scroll_until_text() {
  local target="$1"
  local attempts="${2:-4}"
  local attempt
  for (( attempt = 0; attempt < attempts; attempt++ )); do
    dump_ui
    if ui_has_text "$target"; then
      return 0
    fi
    scroll_down
  done
  wait_for_text "$target" 1
}

launch_splash() {
  run_adb shell am start -n "$SPLASH_ACTIVITY" >/dev/null
  wait_for_text "记录" 20
}

launch_capture() {
  local action="$1"
  local topic="$2"
  local content="$3"
  run_adb shell am start \
    -n "$ENTRY_ACTIVITY" \
    -a "$action" \
    --es extra_capture_topic "$topic" \
    --es extra_capture_content "$content" >/dev/null
}

prepare_image_fixture() {
  local host_image="$OUT_DIR/release_image_$RUN_ID.png"
  local device_dir="/data/user/0/$PACKAGE_NAME/files/captures/images"
  local device_image="$device_dir/release_image_$RUN_ID.png"
  python3 - "$host_image" <<'PY'
import binascii
import struct
import sys
import zlib

path = sys.argv[1]
w, h = 640, 360
pixels = bytearray([248, 251, 255] * w * h)

def rect(x0, y0, x1, y1, color):
    x0, y0 = max(0, x0), max(0, y0)
    x1, y1 = min(w, x1), min(h, y1)
    for y in range(y0, y1):
        row = y * w * 3
        for x in range(x0, x1):
            idx = row + x * 3
            pixels[idx:idx + 3] = bytes(color)

def line(x0, y0, x1, y1, color, thickness=3):
    if x0 == x1:
        rect(x0 - thickness // 2, y0, x0 + thickness // 2 + 1, y1, color)
    elif y0 == y1:
        rect(x0, y0 - thickness // 2, x1, y0 + thickness // 2 + 1, color)

rect(0, 260, w, h, (227, 235, 247))
rect(54, 38, 430, 248, (255, 255, 255))
rect(54, 38, 430, 44, (172, 190, 220))
rect(54, 242, 430, 248, (172, 190, 220))
rect(54, 38, 60, 248, (172, 190, 220))
rect(424, 38, 430, 248, (172, 190, 220))
for x in (150, 248, 340):
    line(x, 38, x, 248, (190, 204, 226), 3)
for y in (88, 138, 188):
    line(54, y, 430, y, (190, 204, 226), 3)
for i, color in enumerate(((200, 246, 230), (223, 238, 255), (255, 244, 180))):
    rect(360, 58 + i * 58, 412, 102 + i * 58, color)
rect(472, 92, 590, 210, (236, 246, 238))
rect(504, 52, 558, 122, (88, 156, 111))
rect(462, 190, 606, 218, (207, 165, 106))
rect(486, 126, 574, 188, (255, 255, 255))
rect(510, 148, 550, 178, (194, 145, 80))
raw = b"".join(
    b"\x00" + bytes(pixels[y * w * 3:(y + 1) * w * 3])
    for y in range(h)
)

def chunk(kind, data):
    return (
        struct.pack(">I", len(data))
        + kind
        + data
        + struct.pack(">I", binascii.crc32(kind + data) & 0xFFFFFFFF)
    )

png = (
    b"\x89PNG\r\n\x1a\n"
    + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0))
    + chunk(b"IDAT", zlib.compress(raw, 9))
    + chunk(b"IEND", b"")
)
with open(path, "wb") as fh:
    fh.write(png)
PY
  run_adb shell "mkdir -p '$device_dir'"
  run_adb push "$host_image" "$device_image" >/dev/null
  local owner
  owner="$(run_adb shell "stat -c '%u:%g' /data/user/0/$PACKAGE_NAME/files" | tr -d '\r')"
  run_adb shell "chown '$owner' '$device_image' && chmod 600 '$device_image'"
  printf '%s\n' "$device_image"
}

assert_all_texts() {
  local text
  for text in "$@"; do
    wait_for_text "$text" 12
  done
}

verify_text_flow() {
  local topic="E2E_TEXT_$RUN_ID"
  local content="release_text_${RUN_ID}_write_check"
  log "Verifying text capture write and content page: $topic"
  launch_capture "$ACTION_CAPTURE" "$topic" "$content"
  assert_all_texts "纯文本输入" "内容（可编辑）" "AI 建议标题" "类型识别" "标签" "附件" "完成记录"
  assert_text_absent_now "存为草稿"
  assert_text_absent_now "AI 建议"
  assert_text_absent_now "更多选项"
  assert_text_absent_now "语音输入"
  screenshot "01-text-input"
  tap_text "完成记录"
  wait_for_text "最近记录" 20
  wait_for_text "$topic" 20
  tap_text "$topic"
  wait_for_text "文本记录" 20
  assert_all_texts "文本记录" "标题" "重新生成标题" "AI 洞察" "正文" "润色正文" "$content"
  assert_text_absent_now "Markdown 预览"
  assert_text_absent_now "编辑正文"
  assert_text_absent_now "已手动确认"
  assert_text_absent_now "先存下这颗火花"
  assert_text_absent_now "AI 整理"
  assert_text_absent_now "归档与时间"
  assert_text_absent_now "标题（可编辑）"
  assert_text_absent_now "生成标题"
  assert_text_absent_now "润色标题"
  assert_text_absent_now "正文内容（可全文编辑）"
  assert_text_absent_now "AI 摘要"
  assert_text_absent_now "重新生成摘要与要点"
  assert_text_absent_now "关键要点"
  assert_text_absent_now "AI 润色预览"
  screenshot "04-text-content-top"
  assert_all_texts "相关主题" "附件"
  scroll_until_text "插入今天" 4
  assert_all_texts "插入今天" "链接任务" "导入项目"
  screenshot "04-text-content-actions"
}

verify_voice_flow() {
  local topic="E2E_VOICE_$RUN_ID"
  local content="原始内容：release_voice_$RUN_ID"
  log "Verifying voice capture write and content page: $topic"
  launch_capture "$ACTION_VOICE" "$topic" "$content"
  assert_all_texts "语音输入" "原始内容信息" "AI 洞察" "删除" "继续录入" "完成解析"
  screenshot "02-voice-input"
  tap_text "完成解析"
  wait_for_text "最近记录" 20
  wait_for_text "$topic" 20
  tap_text "$topic"
  wait_for_text "语音记录" 20
  assert_all_texts "语音记录" "语音暂存音频（可回放）" "标题" "语音转写（可编辑）" "AI 洞察" "$content"
  assert_text_absent_now "Markdown 预览"
  assert_text_absent_now "编辑正文"
  assert_text_absent_now "已手动确认"
  assert_text_absent_now "标题（可编辑）"
  assert_text_absent_now "归档与时间"
  assert_text_absent_now "AI 整理"
  assert_text_absent_now "原始录音："
  assert_text_absent_now "相关主题"
  screenshot "05-voice-content-top"
  assert_all_texts "关键信息"
  scroll_until_text "插入今天" 4
  assert_all_texts "插入今天" "链接任务" "导入项目"
  screenshot "05-voice-content-actions"
}

verify_image_flow() {
  local topic="E2E_IMAGE_$RUN_ID"
  local image_path
  image_path="$(prepare_image_fixture)"
  local content="图片：$image_path"
  log "Verifying image capture write and content page: $topic"
  launch_capture "$ACTION_IMAGE" "$topic" "$content"
  assert_all_texts "图片输入" "图片预览" "本地文件：release_image_$RUN_ID.png" "图像理解结果" "关键信息提取" "结构化识别" "继续解析"
  screenshot "03-image-input-top"
  run_adb shell input swipe 540 2050 540 900 400
  assert_all_texts "OCR 文本(可选)" "重新拍摄" "从相册导入" "继续解析"
  screenshot "03-image-input"
  tap_text "继续解析"
  wait_for_text "最近记录" 20
  wait_for_text "$topic" 20
  tap_text "$topic"
  wait_for_text "图片记录" 20
  assert_all_texts "图片记录" "图片预览" "图片理解摘要（可编辑）" "release_image_$RUN_ID.png"
  assert_text_absent_now "Markdown 预览"
  assert_text_absent_now "编辑正文"
  assert_text_absent_now "已手动确认"
  assert_text_absent_now "标题（可编辑）"
  assert_text_absent_now "$content"
  assert_text_absent_now "归档与时间"
  screenshot "06-image-content-top"
  assert_all_texts "图片预览" "release_image_$RUN_ID.png" "关键信息（可编辑）" "视觉识别结果"
  scroll_down_small
  assert_all_texts "文字" "待 OCR" "OCR 全文（可选）"
  scroll_until_text "插入今天" 4
  assert_all_texts "插入今天" "链接任务" "导入项目"
  scroll_until_text "记录信息（可修改）" 5
  screenshot "06-image-content-actions"
}

assert_logcat_clean() {
  if run_adb logcat -d -t 500 | grep -E "E AndroidRuntime|FATAL EXCEPTION|ANR in com\\.mindflow|Process com\\.mindflow\\.app has died|signal 11|liblitertlm_jni" > "$OUT_DIR/logcat-failures.txt"; then
    fail "Crash-like logcat entries found; see $OUT_DIR/logcat-failures.txt"
  fi
}

main() {
  prepare_device
  local notes_before
  notes_before="$(note_count)"
  check_durable_data "before flows"
  launch_splash
  verify_text_flow
  verify_voice_flow
  verify_image_flow
  check_durable_data "after flows"
  local notes_after
  notes_after="$(note_count)"
  if (( notes_after < notes_before + 3 )); then
    fail "Expected at least 3 new notes. before=$notes_before after=$notes_after"
  fi
  assert_logcat_clean
  log "Release capture flow verification passed. Artifacts: $OUT_DIR"
}

main "$@"
