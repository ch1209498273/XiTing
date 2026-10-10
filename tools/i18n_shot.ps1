# 多语言视觉巡检：一次跑 7 语言 × 3 页面截图（免前台切换法）
#
# 用法：
#   powershell -ExecutionPolicy Bypass -File tools\i18n_shot.ps1
#   powershell -ExecutionPolicy Bypass -File tools\i18n_shot.ps1 -Langs zh,ru -Out .cowork-temp\i18n_shots
#
# 前提：
#   1. 手机已连接（adb devices 可见）
#   2. 跑之前确认此刻没有人正在用手机 —— 脚本会把 App 拉到前台并模拟点击
#
# 原理：cmd locale set-app-locales 走系统 per-app 语言通道（与 App 内语言
#       对话框同一条路径）；悬浮球/通知会因 onConfigurationChanged 热刷新，
#       所以截图里顺带把球的每种语言状态也巡检了。
#
# 安全：每次点击前验证前台仍是本 App，否则立刻中止（HANDOFF 坑 5：
#       绝不在用户前台不是本 App 时盲点）。
param(
    [string[]]$Langs = @('zh', 'en', 'ja', 'ko', 'fr', 'de', 'es', 'ru'),
    [string]$Out = ".cowork-temp\i18n_shots",
    [string]$Adb = "C:\Users\MR\AppData\Local\Android\Sdk\platform-tools\adb.exe"
)
$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$pkg = "com.lujinyu.xiting"
$root = Split-Path -Parent (Split-Path -Parent $PSCommandPath)
Set-Location $root
New-Item -ItemType Directory -Force -Path $Out | Out-Null

function TopActivity { ((& $Adb shell "dumpsys activity activities | grep topResumedActivity") -join " ") }
function Assert-Foreground {
    if ((TopActivity) -notmatch [regex]::Escape($pkg)) {
        throw "前台不是 $pkg，已中止（防误点）。当前：$(TopActivity)"
    }
}

$wm = ((& $Adb shell wm size) -join " ")
$m = [regex]::Match($wm, "(\d+)x(\d+)")
if (-not $m.Success) { throw "读不到屏幕尺寸：$wm" }
$w = [int]$m.Groups[1].Value
$h = [int]$m.Groups[2].Value
$tabHome = [int]($w / 6)
$tabStats = [int]($w / 2)
$tabSettings = [int]($w * 5 / 6)
$tabY = $h - 110

Write-Host "先把 App 拉到前台（请确认此刻无人用机）…"
& $Adb shell "am start -n $pkg/.MainActivity" | Out-Null
Start-Sleep -Seconds 2

try {
    foreach ($lang in $Langs) {
        & $Adb shell "cmd locale set-app-locales $pkg --locales $lang" | Out-Null
        Start-Sleep -Milliseconds 2200
        $tabs = [ordered]@{ home = $tabHome; stats = $tabStats; settings = $tabSettings }
        foreach ($k in $tabs.Keys) {
            Assert-Foreground
            & $Adb shell "input tap $($tabs[$k]) $tabY" | Out-Null
            Start-Sleep -Milliseconds 900
            & $Adb shell screencap -p /sdcard/_i18n_shot.png
            & $Adb pull /sdcard/_i18n_shot.png (Join-Path $Out "$lang-$k.png") | Out-Null
            Write-Host ("  已截 {0}/{1}" -f $lang, $k)
        }
    }
} finally {
    Write-Host "恢复中文…"
    & $Adb shell "cmd locale set-app-locales $pkg --locales zh" | Out-Null
    if ((TopActivity) -match [regex]::Escape($pkg)) {
        Start-Sleep -Milliseconds 800
        & $Adb shell "input tap $tabHome $tabY" | Out-Null
    }
}
Write-Host "完成：$((Get-ChildItem $Out -File -ErrorAction SilentlyContinue).Count) 张截图 → $Out"
