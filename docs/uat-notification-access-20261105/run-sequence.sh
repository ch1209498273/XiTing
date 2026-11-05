#!/usr/bin/env bash
# 通知使用权引导改造的模拟器验收序列（api35 / Android 15，系统语言英文）。
# 证据落在本目录；坐标一律从 uiautomator dump 反查，不硬编码。
# 用法：bash docs/uat-notification-access-20261105/run-sequence.sh
#
# 顺序是有意的：
#   自检面板要在「未授权」状态下取；
#   弹窗自愈必须在「弹窗开着的时候」授权，所以 allow_listener 卡在返回键之前。
set -u
export MSYS_NO_PATHCONV=1
ADB="${ADB:-C:/Users/MR/AppData/Local/Android/Sdk/platform-tools/adb.exe}"
SERIAL=emulator-5554
E="$(cd "$(dirname "$0")" && pwd)"
TMP="$E/.work"
mkdir -p "$TMP"
PKG=com.lujinyu.xiting
COMP=$PKG/$PKG.NotificationListener

adb() { "$ADB" -s "$SERIAL" "$@"; }
log() { echo "[$(date +%H:%M:%S)] $*" | tee -a "$E/sequence.log"; }

# Windows 版 Python 解不了 Git-Bash 风格的中文绝对路径，所以进目录后用相对文件名
tap() {
  adb shell uiautomator dump /sdcard/probe.xml >/dev/null 2>&1
  adb exec-out cat /sdcard/probe.xml > "$TMP/probe.xml"
  ( cd "$TMP" && python - "probe.xml" "$1" <<'PY'
import sys, re, xml.etree.ElementTree as ET
raw = open(sys.argv[1], 'rb').read().decode('utf-8', 'replace')
want = sys.argv[2]
hit = None
for n in ET.fromstring(raw).iter('node'):
    a = n.attrib
    if a.get('text') == want or a.get('resource-id', '').endswith('/' + want) or a.get('content-desc') == want:
        hit = a['bounds']; break
if not hit:
    print('NOT_FOUND'); sys.exit(3)
m = [int(x) for x in re.findall(r'\d+', hit)]
print((m[0]+m[2])//2, (m[1]+m[3])//2)
PY
)
}
tapname() {
  local xy; xy="$(tap "$1")"
  if [ "${xy% *}" = "NOT_FOUND" ]; then log "MISS $1"; return 1; fi
  log "tap $1 -> $xy"; adb shell input tap ${xy% *} ${xy#* }; sleep 3
}
shot() { adb exec-out screencap -p > "$E/$1"; log "saved $1 ($(wc -c < "$E/$1") bytes)"; }
dump() { adb shell uiautomator dump /sdcard/"$1" >/dev/null 2>&1; adb exec-out cat /sdcard/"$1" > "$E/$1"; log "saved $1 ($(wc -c < "$E/$1") bytes)"; }
focus() { adb shell dumpsys window 2>/dev/null | grep -m1 mCurrentFocus | tr -d '\r' | tee -a "$E/$1"; }
perm() {
  adb shell settings get secure enabled_notification_listeners 2>&1 | tr -d '\r' > "$TMP/l.txt"
  { echo "[$(date +%H:%M:%S)] $1"; echo "enabled_notification_listeners = $(cat "$TMP/l.txt")";
    grep -q "$COMP" "$TMP/l.txt" && echo "本App在监听列表中：是" || echo "本App在监听列表中：否"; } | tee -a "$E/permission-state.log"
}
home_permissions() { adb shell input swipe 540 1700 540 700 400; sleep 2; }

log "=== 0. 起始：撤销通知使用权 ==="
adb shell cmd notification disallow_listener "$COMP"; sleep 2
perm "撤销后"
adb shell am force-stop "$PKG"

log "=== 1. 首页权限区（未授权）==="
adb shell am start -n "$PKG/.LauncherDefault" >/dev/null 2>&1; sleep 5
tapname Home
home_permissions
dump home-permissions-off.xml; shot home-permissions-off.png

log "=== 2. 设置页「诊断与自检」入口（未授权）==="
tapname Settings
adb shell input swipe 540 1800 540 500 500; sleep 2
dump settings-diagnostics-row.xml
tapname Diagnostics
dump diagnostics-no-access.xml; shot diagnostics-no-access.png

log "=== 3. 自检面板的修复按钮应落到系统通知使用权页 ==="
tapname "ALLOW NOTIFICATION ACCESS"
focus diagnostics-fix-focus.log

log "=== 4. 睡前定时弹窗（未授权）==="
adb shell input keyevent KEYCODE_BACK; sleep 2
tapname CANCEL
sleep 1
tapname Home
adb shell am start -n "$PKG/.LauncherDefault" >/dev/null 2>&1; sleep 3
home_permissions
tapname timer_chip
dump timer-dialog-no-access.xml; shot timer-dialog-no-access.png

log "=== 5. 弹窗内「去授权」应落到系统通知使用权页 ==="
tapname GRANT
focus grant-jump-focus.log

log "=== 6. 弹窗开着的状态下授权，返回后应自愈（不碰任何控件）==="
adb shell cmd notification allow_listener "$COMP"; sleep 2
perm "授权后"
adb shell input keyevent KEYCODE_BACK; sleep 3
dump timer-dialog-selfheal.xml; shot timer-dialog-selfheal.png

log "=== 7. 回首页：通知使用权状态位应翻转 ==="
tapname CANCEL
sleep 1
tapname Home
home_permissions
dump home-permissions-on.xml; shot home-permissions-on.png

log "=== 8. 回归：撤掉黑幕路径仍能写入定时 ==="
tapname timer_chip
tapname OK
dump home-timer-set.xml; shot home-timer-set.png

log "=== 完成 ==="
