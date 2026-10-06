<#
.SYNOPSIS
    在 ASCII 路径下运行本项目的 JVM 单元测试。

.DESCRIPTION
    为什么需要这个脚本
    ------------------
    本项目位于 D:\AI任务\zcode\息屏听剧 —— 路径含中文。Gradle 执行单测时会
    fork 一个独立的 "Gradle Test Executor" 进程，并通过 @argfile 传递
    classpath。JVM 解析 @argfile 发生在 -Dfile.encoding 等参数生效之前，
    于是 classpath 里的中文路径被解错，测试进程加载不到任何测试类，
    全部报 ClassNotFoundException。

    已用对照实验确认根因：整份项目复制到纯 ASCII 路径后
    `gradle testDebugUnitTest` 正常，42 个测试全绿。因此本脚本的做法是
    把源码镜像到临时 ASCII 目录再跑，跑完读结果、清理现场。

    注意：assembleDebug / assembleRelease 不受影响，只有单测任务受影响，
    所以日常构建无需本脚本。

.PARAMETER Variant
    debug（默认）或 release。

.PARAMETER KeepMirror
    保留镜像目录以便排查（默认跑完即删）。

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File tools\run_tests.ps1

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File tools\run_tests.ps1 -Variant release -KeepMirror
#>
[CmdletBinding()]
param(
    [ValidateSet('debug', 'release')]
    [string]$Variant = 'debug',

    [switch]$KeepMirror
)

$ErrorActionPreference = 'Stop'

$ProjectRoot = Split-Path -Parent $PSScriptRoot
$Task        = "test${Variant}UnitTest"
$Stamp       = Get-Date -Format 'yyyyMMdd-HHmmss'
$MirrorRoot  = Join-Path $env:TEMP ("xiting-test-" + $Stamp)

# 只镜像构建与测试真正需要的文件；密钥、APK、过程物料一律不碰
$IncludeDirs = @('gradle', 'app\src', 'tools', 'docs')
$IncludeFiles = @(
    'settings.gradle.kts',
    'build.gradle.kts',
    'gradle.properties',
    'local.properties',
    'gradlew',
    'gradlew.bat',
    '.gitignore'
)

function Resolve-Gradle {
    # 优先用 wrapper；wrapper 首次运行要下 128MB 发行包（国内较慢），
    # 因此若本机 SDK 里已有同版本 gradle 就直接用它。
    $sdkGradle = 'C:\Users\MR\AppData\Local\Android\Sdk\gradle-9.1.0\bin\gradle.bat'
    if (Test-Path $sdkGradle) { return $sdkGradle }
    $wrapper = Join-Path $ProjectRoot 'gradlew.bat'
    if (Test-Path $wrapper) { return $wrapper }
    throw '找不到 gradle：请安装到 SDK 目录，或先生成 wrapper。'
}

function Write-Step($msg) {
    Write-Host "==> $msg" -ForegroundColor Cyan
}

Write-Step "项目根目录：$ProjectRoot"
Write-Step "目标任务：$Task"

$gradle = Resolve-Gradle
Write-Step "使用 Gradle：$gradle"

New-Item -ItemType Directory -Path $MirrorRoot -Force | Out-Null

Write-Step "镜像源码到 ASCII 路径：$MirrorRoot"
foreach ($d in $IncludeDirs) {
    $src = Join-Path $ProjectRoot $d
    if (-not (Test-Path $src)) { continue }
    # 注意：Copy-Item <dir> -Destination <已存在目录> -Recurse 会落到 <目标>\<叶子名>，
    # 直接指向 MirrorRoot 会把 app\src 放成 mirror\src。这里统一复制到「父目录」下。
    $parent = Split-Path $d -Parent
    $destRoot = if ($parent) { Join-Path $MirrorRoot $parent } else { $MirrorRoot }
    New-Item -ItemType Directory -Path $destRoot -Force | Out-Null
    Copy-Item -Path $src -Destination $destRoot -Recurse -Force
}
foreach ($f in $IncludeFiles) {
    $src = Join-Path $ProjectRoot $f
    if (Test-Path $src) {
        Copy-Item -Path $src -Destination $MirrorRoot -Force
    }
}
# app 模块的构建脚本与混淆规则（它们在 app/ 根下，不在 app/src 里）
foreach ($f in @('build.gradle.kts', 'proguard-rules.pro')) {
    $src = Join-Path $ProjectRoot "app\$f"
    if (Test-Path $src) {
        $dst = Join-Path $MirrorRoot "app\$f"
        New-Item -ItemType Directory -Path (Split-Path $dst -Parent) -Force | Out-Null
        Copy-Item -Path $src -Destination $dst -Force
    }
}

Write-Step '执行单测（首次可能要拉取依赖，请稍候）…'
Push-Location $MirrorRoot
try {
    & $gradle $Task --console=plain --rerun-tasks
    $exitCode = $LASTEXITCODE
} finally {
    Pop-Location
}

# ---- 汇总结果 ----
$resultDir = Join-Path $MirrorRoot "app\build\test-results\${Task}"
$total = 0; $failed = 0; $errored = 0; $skipped = 0
$rows = @()

if (Test-Path $resultDir) {
    foreach ($xml in Get-ChildItem $resultDir -Filter '*.xml' -ErrorAction SilentlyContinue) {
        [xml]$doc = Get-Content $xml.FullName -Raw -Encoding UTF8
        $suite = $doc.testsuite
        if (-not $suite) { continue }
        $t = [int]$suite.tests; $f = [int]$suite.failures; $e = [int]$suite.errors; $s = [int]$suite.skipped
        $total += $t; $failed += $f; $errored += $e; $skipped += $s
        $status = if (($f + $e) -gt 0) { 'FAIL' } else { 'PASS' }
        $rows += [PSCustomObject]@{
            Suite   = $suite.name
            Tests   = $t
            Failures= $f
            Errors  = $e
            Status  = $status
        }
    }
}

Write-Host ''
Write-Step '测试结果'
$rows | Format-Table -AutoSize | Out-String | Write-Host

if ($total -eq 0) {
    Write-Host "没有收集到任何测试用例 —— 这通常意味着镜像不完整。" -ForegroundColor Red
} else {
    Write-Host ("合计 {0} 个：失败 {1}，错误 {2}，跳过 {3}" -f $total, $failed, $errored, $skipped) -ForegroundColor $(if (($failed + $errored) -gt 0) { 'Red' } else { 'Green' })
}

# ---- 失败用例明细 ----
if (($failed + $errored) -gt 0 -and (Test-Path $resultDir)) {
    Write-Host ''
    Write-Step '失败明细'
    foreach ($xml in Get-ChildItem $resultDir -Filter '*.xml' -ErrorAction SilentlyContinue) {
        [xml]$doc = Get-Content $xml.FullName -Raw -Encoding UTF8
        foreach ($tc in $doc.testsuite.testcase) {
            if ($tc.failure -or $tc.error) {
                $msg = if ($tc.failure) { $tc.failure.message } else { $tc.error.message }
                Write-Host ("  [{0}] {1}`n      {2}" -f $doc.testsuite.name, $tc.name, $msg) -ForegroundColor Red
            }
        }
    }
}

# ---- 现场清理 ----
if ($KeepMirror) {
    Write-Host ''
    Write-Step "已保留镜像目录：$MirrorRoot（-KeepMirror）" -ForegroundColor Yellow
} else {
    Remove-Item -LiteralPath $MirrorRoot -Recurse -Force -ErrorAction SilentlyContinue
    Write-Host ''
    Write-Step '镜像目录已清理'
}

if ($total -eq 0 -or ($failed + $errored) -gt 0 -or $exitCode -ne 0) { exit 1 }
exit 0