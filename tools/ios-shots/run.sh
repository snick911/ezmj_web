#!/bin/bash
# iPhone 模擬器裡真的 Safari 截圖，在 macOS 上跑，由 .github/workflows/ios-screenshots.yml 呼叫。見 README 的「iPhone 模擬器截圖」。
#
#   bash tools/ios-shots/run.sh <網站資料夾> <服務器的 classpath> <輸出資料夾>
#
# 開一台本機服務器（不碰線上的 Render）與一個只給模擬器用的網站，網站的 index.html 副本多載入 harness.js，
# 由它在 Safari 裡自己走過大廳、牌桌、全螢幕說明與橫向，每一步報告量到的大小，這裡收到就截圖。
# 橫向沒有 simctl 指令可以轉，用 AppleScript 按 Simulator 的選單。輸出資料夾留下截圖、reports.jsonl、summary.md 與紀錄。
set -uo pipefail

SITE=$1
SERVER_CP=$2
OUT=$3
HERE=$(cd "$(dirname "$0")" && pwd)
WORK=$(mktemp -d)
ACKS=$WORK/acks
REPORTS=$OUT/reports.jsonl
mkdir -p "$OUT" "$ACKS" "$WORK/site"
: > "$REPORTS"
FAILED=0

log() {
  echo "[$(date +%H:%M:%S)] $*" | tee -a "$OUT/run.log"
}

# 網站：照原樣複製，index.html 的副本在 ezmj.js 前面先載入 harness.js（同一個來源，CSP 准）
cp -R "$SITE"/. "$WORK/site/"
cp "$HERE/harness.js" "$WORK/site/harness.js"
perl -pe 's#(<script src="ezmj\.js)#<script src="harness.js"></script>\n$1#' "$SITE/index.html" > "$WORK/site/harness.html"

EZMJ_HONOR_FILE="$WORK/honor.properties" java -Dfile.encoding=UTF-8 -cp "$SERVER_CP" ezmj.server.EzmjServer 7788 \
  > "$OUT/server.log" 2>&1 &
SERVER_PID=$!
python3 "$HERE/serve.py" "$WORK/site" "$REPORTS" "$ACKS" > "$OUT/serve.log" 2>&1 &
SITE_PID=$!

# 最新的 iOS 裡號碼最大的基本款 iPhone（例如 iPhone 16），沒有就隨便一台 iPhone
read -r UDID NAME <<< "$(xcrun simctl list devices available -j | python3 -c '
import json, re, sys
best = None
for runtime, devices in json.load(sys.stdin)["devices"].items():
    if ".iOS-" not in runtime:
        continue
    version = tuple(int(n) for n in re.findall(r"\d+", runtime.split(".iOS-")[-1]))
    for d in devices:
        m = re.fullmatch(r"iPhone (\d+)", d["name"])
        rank = (1 if m else 0, version, int(m.group(1)) if m else 0)
        if d["name"].startswith("iPhone") and (best is None or rank > best[0]):
            best = (rank, d["udid"], d["name"] + " / " + runtime.split(".")[-1])
print(best[1], best[2])
')"
log "裝置：$NAME ($UDID)"
xcrun simctl boot "$UDID" 2>/dev/null
xcrun simctl bootstatus "$UDID" -b > /dev/null
# 轉橫向要按 Simulator 的選單，所以要開視窗
open -a Simulator --args -CurrentDeviceUDID "$UDID"
sleep 8

wait_report() {
  local step=$1 seconds=$2
  for _ in $(seq 1 $((seconds * 2))); do
    if grep -q "\"step\":\"$step\"" "$REPORTS"; then
      return 0
    fi
    if grep -q '"step":"error"' "$REPORTS"; then
      log "網頁回報錯誤：$(grep '"step":"error"' "$REPORTS" | head -1 | cut -c1-300)"
      return 1
    fi
    sleep 0.5
  done
  log "等太久沒有 $step"
  return 1
}

shot() {
  xcrun simctl io "$UDID" screenshot "$OUT/$1.png" > /dev/null 2>&1
  # 模擬器存的是直向的畫面，橫向的轉正（往逆時針轉 90 度）
  case $1 in
    *-landscape) sips -r 270 "$OUT/$1.png" > /dev/null 2>&1 ;;
  esac
  touch "$ACKS/$1"
  log "截圖 $1"
}

# iOS 26 的 Safari 第一次打開時跳出「⋯ 選單裡有分享與書籤」的提示，蓋住網頁下半部。
# 在大廳正中間（空的房間列表）點一下把它收掉，點到網頁也沒關係。
dismiss_tip() {
  if osascript -e 'tell application "Simulator" to activate' -e 'delay 0.5' \
      -e 'tell application "System Events" to tell process "Simulator"' \
      -e 'set {x, y} to position of window 1' -e 'set {w, h} to size of window 1' \
      -e 'click at {x + w / 2, y + h / 2}' -e 'end tell' >> "$OUT/run.log" 2>&1; then
    log "點一下收掉 Safari 的提示"
  else
    log "收不掉 Safari 的提示，見上面 osascript 的錯誤"
  fi
  sleep 1.5
}

# 先按 Device 選單的 Rotate Left，不行再按 ⌘←
rotate() {
  if osascript -e 'tell application "Simulator" to activate' -e 'delay 1' \
      -e 'tell application "System Events" to tell process "Simulator" to click menu item "Rotate Left" of menu "Device" of menu bar 1' \
      >> "$OUT/run.log" 2>&1; then
    log "用選單轉成橫向"
  elif osascript -e 'tell application "Simulator" to activate' -e 'delay 1' \
      -e 'tell application "System Events" to key code 123 using command down' >> "$OUT/run.log" 2>&1; then
    log "用 ⌘← 轉成橫向"
  else
    log "轉不了橫向，見上面 osascript 的錯誤"
  fi
}

xcrun simctl openurl "$UDID" "http://localhost:8080/harness.html?server=ws://localhost:7788/ws"
log "Safari 打開測試頁"

for step in lobby-portrait table-portrait; do
  if wait_report "$step" 180; then
    if [ "$step" = lobby-portrait ]; then dismiss_tip; fi
    shot "$step"
  else
    FAILED=1
    break
  fi
done
if [ $FAILED -eq 0 ]; then
  # 有全螢幕按鈕時是說明視窗；沒有的話網頁報 no-fullscreen-button
  for _ in $(seq 1 40); do
    if grep -q '"step":"tip-portrait"' "$REPORTS"; then shot tip-portrait; break; fi
    if grep -q '"step":"no-fullscreen-button"' "$REPORTS"; then log "沒有跳出加入主畫面的說明"; break; fi
    sleep 0.5
  done
  if wait_report rotate-please 30; then
    rotate
    for step in table-landscape menu-landscape; do
      if wait_report "$step" 90; then shot "$step"; else FAILED=1; break; fi
    done
  else
    FAILED=1
  fi
fi
wait_report done 20 || true
xcrun simctl spawn "$UDID" log show --last 10m --predicate 'process == "MobileSafari" OR process CONTAINS "WebContent"' \
  --style compact > "$OUT/safari.log" 2>/dev/null || true

# 一頁摘要：每一步的畫面大小、字的大小、讓開的寬度與按鈕的位置
python3 - "$REPORTS" "$NAME" > "$OUT/summary.md" <<'PY'
import json, sys
rows = [json.loads(line) for line in open(sys.argv[1], encoding="utf-8") if line.strip()]
print(f"# iPhone 模擬器截圖\n\n裝置：{sys.argv[2]}\n")
print("| 步驟 | 畫面 | html 字 | 讓開（上 右 下 左） | 選單 | 全螢幕 | 說話 | 提示 | 畫布 |")
print("|---|---|---|---|---|---|---|---|---|")
def b(x):
    return "" if not x else f"{x['w']}×{x['h']} @{x['x']},{x['y']} {x['font']}"
for r in rows:
    m = r.get("metrics", {})
    boxes = m.get("boxes", {})
    print(f"| {r['step']} | {m.get('inner', '')} | {m.get('rootFont', '')} | {m.get('safe', '')} | {b(boxes.get('menu'))} | "
          f"{b(boxes.get('fullscreen'))} | {b(boxes.get('talk'))} | {b(boxes.get('hint'))} | {b(boxes.get('canvas'))} |")
for r in rows:
    if r["step"] == "error":
        print(f"\n錯誤：{r.get('extra', {}).get('message')}")
if rows:
    print(f"\nUser agent：{rows[0]['metrics'].get('userAgent')}")
PY
cat "$OUT/summary.md"
if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
  cat "$OUT/summary.md" >> "$GITHUB_STEP_SUMMARY"
fi
kill "$SERVER_PID" "$SITE_PID" 2>/dev/null || true
exit $FAILED
