#!/usr/bin/env bash
# 中文界面补证：把 App 内语言切到简体中文后，重取本轮改造涉及的关键界面。
# 只动 App 内设置（设置→语言），不改系统 locale，符合真机约束里"不为验收改系统设置"的口径。
# 用法：bash docs/uat-notification-access-20261105/run-zh-locale.sh
#
# 前两版脚本跑不通，原因记在这里以免重犯：
#   1) 语言行 row_lang 在设置页折线以下，不在当前画面里，按 resource-id 反查会 MISS；
#   2) recreate 后 App 记住原页签，脚本必须先显点回目标页签再找控件；
#   3) 切语言触发重启，会弹「恢复历史数据」引导，需先点「不用了」；
#   4) Windows 版 Python 解不了 Git-Bash 风格的中文绝对路径，控制台默认 GBK 也会吞中文，
#      所以统一进临时目录用相对文件名，并显式按 UTF-8 读写。
set -u
export MSYS_NO_PATHCONV=1
ADB="${ADB:-C:/Users/MR/AppData/Local/Android/Sdk/platform-tools/adb.exe}"
SERIAL=emulator-5554
E="$(cd "$(dirname "$0")" && pwd)"
TMP="$(mktemp -d "${TMPDIR:-/tmp}/xiting-zh-XXXX")"
PKG=com.lujinyu.xiting
COMP=$PKG/$PKG.NotificationListener
cat > "$TMP/drive.py" <<'PY'
import sys, re, time, subprocess, os
sys.stdout.reconfigure(encoding="utf-8", errors="replace")
ADB, SER, E = os.environ["ADB"], os.environ["SERIAL"], os.environ["EWIN"]
def sh(*a): return subprocess.run([ADB,"-s",SER]+list(a),capture_output=True).stdout.decode("utf-8","replace")
def dump():
    sh("shell","uiautomator","dump","/sdcard/p.xml"); return sh("shell","cat","/sdcard/p.xml")
def nodes(): return list(ET_parse(dump()).iter("node"))
def ET_parse(raw):
    import xml.etree.ElementTree as ET; return ET.fromstring(raw)
def center(match, mode="text"):
    for n in nodes():
        a=n.attrib
        if (mode=="text" and match in (a.get("text") or "")) or (mode=="id" and a.get("resource-id","").endswith("/"+match)):
            m=[int(x) for x in re.findall(r"\d+",a["bounds"])]; return (m[0]+m[2])//2,(m[1]+m[3])//2
    return None
def tap(match, mode="text", wait=3):
    xy=center(match,mode)
    if not xy: print("MISS",mode,match); return False
    sh("shell","input","tap",str(xy[0]),str(xy[1])); time.sleep(wait); print("tap",mode,match,"->",xy); return True
def swipe(a,b,c,d): sh("shell","input","swipe",str(a),str(b),str(c),str(d),"500"); time.sleep(2)
def texts(): return [(n.attrib.get("text") or "") for n in nodes() if n.attrib.get("text")]
def save(name):
    raw=dump(); open(os.path.join(E,name),"wb").write(raw.encode("utf-8","replace"))
    png=subprocess.run([ADB,"-s",SER,"exec-out","screencap","-p"],capture_output=True).stdout
    open(os.path.join(E,name.replace(".xml",".png")),"wb").write(png); print("saved",name,len(raw),"xml",len(png),"png")
PY
export ADB SERIAL="$SERIAL" EWIN="$(cygpath -w "$E")"
run() { ( cd "$TMP" && python -c "$1" ); }

echo "=== 0. 起始：撤销通知使用权 ==="
"$ADB" -s "$SERIAL" shell cmd notification disallow_listener "$COMP"; sleep 2
L1="$("$ADB" -s "$SERIAL" shell settings get secure enabled_notification_listeners 2>&1 | tr -d '\r')"
{ echo "[$(date +%H:%M:%S)] 撤销后"; echo "enabled_notification_listeners = $L1"
  case "$L1" in *"$COMP"*) echo "本App在监听列表中：是（异常）";; *) echo "本App在监听列表中：否";; esac; } > "$E/permission-state-zh.log"
"$ADB" -s "$SERIAL" shell am force-stop "$PKG"
"$ADB" -s "$SERIAL" shell am start -n "$PKG/.LauncherDefault" >/dev/null 2>&1; sleep 6

echo "=== 1. 切中文：进设置页签 → 滚到语言行 → 简体中文 → 关掉恢复引导 ==="
run '
from drive import *
tap("nav_label_settings","id")
swipe(540,1800,540,500); swipe(540,1800,540,500)
tap("row_lang","id"); tap("简体中文")
if center("不用了"): tap("不用了")
else: print("无恢复引导")
'
echo "=== 2. 设置页数据组出现「诊断与自检」 ==="
run '
from drive import *
import time; time.sleep(3)
if center("不用了"): tap("不用了")
swipe(540,1800,540,500); swipe(540,1800,540,500)
save("settings-diagnostics-row-zh.xml")
tap("诊断与自检"); save("diagnostics-no-access-zh.xml")
if center("取消"): tap("取消")
'
echo "=== 3. 首页权限区四行（未授权） ==="
run '
from drive import *
tap("首页"); swipe(540,1700,540,700)
save("home-permissions-off-zh.xml")
'
echo "=== 4. 定时弹窗（未授权）与「去授权」跳转 ==="
run '
from drive import *
tap("睡前定时"); save("timer-dialog-no-access-zh.xml")
tap("去授权")
'
"$ADB" -s "$SERIAL" shell dumpsys window 2>/dev/null | grep -m1 mCurrentFocus | tr -d '\r' > "$E/grant-jump-focus-zh.log"
cat "$E/grant-jump-focus-zh.log"
echo "=== 5. 弹窗开着时授权 → 返回后自愈 ==="
"$ADB" -s "$SERIAL" shell cmd notification allow_listener "$COMP"; sleep 2
"$ADB" -s "$SERIAL" shell input keyevent KEYCODE_BACK; sleep 3
run 'from drive import *; save("timer-dialog-selfheal-zh.xml"); tap("取消")'
echo "=== 6. 首页状态位翻转 ==="
run '
from drive import *
tap("首页"); swipe(540,1700,540,700)
save("home-permissions-on-zh.xml")
'
L2="$("$ADB" -s "$SERIAL" shell settings get secure enabled_notification_listeners 2>&1 | tr -d '\r')"
{ echo "[$(date +%H:%M:%S)] 授权后"; echo "enabled_notification_listeners = $L2"
  case "$L2" in *"$COMP"*) echo "本App在监听列表中：是";; *) echo "本App在监听列表中：否（异常）";; esac; } >> "$E/permission-state-zh.log"
rm -rf "$TMP"
echo "=== 完成 ==="
