# 一键发版：bump 版本号 → 测试 → 构建 → 提交推送 → GitHub Release → 蒲公英
#
# 用法：
#   powershell -ExecutionPolicy Bypass -File tools\release.ps1 -Version 3.6.1 -Notes "更新说明（可多行）"
#
# 前提：
#   - 文档（README / HANDOFF）请在跑本脚本前自行更新并提交（本脚本只管发版）
#   - 网络：GitHub/蒲公英 需要 HTTPS_PROXY 正常
#   - 有 keystore.properties 时 release 用正式签名；没有则回落 debug 签名（会先确认）
#
# 版本号在 app/build.gradle.kts 的 `val appVersion = "x.y.z"` 与 `versionCode = N`。
# 本脚本把 versionCode +1、替换 versionName；两个正则都要求恰好命中一次。
param(
    [Parameter(Mandatory = $true)][string]$Version,
    [string]$Notes = "版本更新",
    [string]$GradlePath = "C:\Users\MR\AppData\Local\Android\Sdk\gradle-9.1.0\bin\gradle.bat",
    [switch]$SkipTests
)
$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$root = Split-Path -Parent (Split-Path -Parent $PSCommandPath)
Set-Location $root
New-Item -ItemType Directory -Force -Path ".cowork-temp" | Out-Null

if ($Version -notmatch '^\d+\.\d+\.\d+$') { throw "版本号格式应为 x.y.z：$Version" }

# ---- 1. bump ----
$kts = "app\build.gradle.kts"
$text = [System.IO.File]::ReadAllText($kts)
$vm = [regex]::Match($text, 'val appVersion = "(\d+\.\d+\.\d+)"')
$vc = [regex]::Match($text, 'versionCode = (\d+)')
if (-not $vm.Success) { throw "在 $kts 找不到 val appVersion" }
if (-not $vc.Success) { throw "在 $kts 找不到 versionCode" }
$newCode = [int]$vc.Groups[1].Value + 1
Write-Host ("版本 {0} → {1} ｜ versionCode {2} → {3}" -f $vm.Groups[1].Value, $Version, $vc.Groups[1].Value, $newCode)
$text2 = $text.Replace($vm.Value, "val appVersion = `"$Version`"")
$text2 = $text2.Replace($vc.Value, "versionCode = $newCode")
if ($text2 -eq $text) { throw "版本号替换未生效，手动检查" }
[System.IO.File]::WriteAllText((Resolve-Path $kts), $text2, (New-Object System.Text.UTF8Encoding($false)))

if (-not (Test-Path "keystore.properties")) {
    Write-Host "!! 没有 keystore.properties：release 包会用 debug 签名。"
    if ((Read-Host "继续？(y/N)") -ne "y") { throw "已取消" }
}

# ---- 2. 测试 ----
if (-not $SkipTests) {
    Write-Host "== 单测 =="
    powershell -ExecutionPolicy Bypass -File tools\run_tests.ps1 *> .cowork-temp\release_tests.txt
    if ($LASTEXITCODE -ne 0) { throw "测试失败，见 .cowork-temp\release_tests.txt" }
    Get-Content .cowork-temp\release_tests.txt -Encoding UTF8 | Select-String "合计" | Select-Object -First 1
}

# ---- 3. 构建 ----
Write-Host "== 构建 =="
& $GradlePath assembleRelease --console=plain *> .cowork-temp\release_build.txt
if ($LASTEXITCODE -ne 0) { throw "构建失败，见 .cowork-temp\release_build.txt" }
Get-Content .cowork-temp\release_build.txt -Encoding UTF8 | Select-String "BUILD SUCCESSFUL" | Select-Object -First 1
$apk = "app\build\outputs\apk\release\app-release.apk"
if (-not (Test-Path $apk)) { throw "找不到 APK：$apk" }

# ---- 4. 提交 + 推送 ----
Write-Host "== 提交 =="
$msgFile = ".cowork-temp\release_msg.txt"
[System.IO.File]::WriteAllText(
    (Join-Path $root $msgFile),
    ("release: v$Version`n`n$Notes`n"),
    (New-Object System.Text.UTF8Encoding($false)))
git add app/build.gradle.kts
git commit -F $msgFile
if ($LASTEXITCODE -ne 0) { throw "提交失败" }
git push origin master
if ($LASTEXITCODE -ne 0) { throw "推送失败（代理又挂了？）" }

# ---- 5. GitHub Release ----
Write-Host "== GitHub Release =="
python tools\release_github.py $Version "v$Version" $Notes
if ($LASTEXITCODE -ne 0) { throw "GitHub Release 失败" }

# ---- 6. 蒲公英 ----
Write-Host "== 蒲公英 =="
python tools\upload_pgyer.py $apk $Notes
if ($LASTEXITCODE -ne 0) { throw "蒲公英上传失败" }

Write-Host "`n全部完成 ✔  v$Version" -ForegroundColor Green
Write-Host "别忘了：更新 README / HANDOFF 的版本与待办（如有变化）"
