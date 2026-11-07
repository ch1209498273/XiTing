param([Parameter(Mandatory=$true)][string]$Device)
$adb='C:/Users/MR/AppData/Local/Android/Sdk/platform-tools/adb.exe'
$uiTemp=Join-Path $env:TEMP 'xiting-physical-ui.xml'
function Read-UatUi {
    & $adb -s $Device shell uiautomator dump /sdcard/xiting-uat.xml | Out-Null
    if($LASTEXITCODE -ne 0){throw 'UI dump failed'}
    & $adb -s $Device pull /sdcard/xiting-uat.xml $uiTemp 2>&1 | Out-Null
    if($LASTEXITCODE -ne 0){throw 'UI pull failed'}
    [xml]$document=Get-Content $uiTemp -Raw
    return $document
}
function Tap-UatNode($Node) {
    if(!$Node -or $Node.enabled -ne 'true'){throw 'Missing/disabled UI node'}
    $n=[regex]::Matches($Node.bounds,'\d+') | ForEach-Object {[int]$_.Value}
    if($n.Count -ne 4){throw 'Invalid bounds'}
    & $adb -s $Device shell input tap ([int](($n[0]+$n[2])/2)) ([int](($n[1]+$n[3])/2))
}
function Save-BiliState([string]$Path,[string]$Phase) {
    $lines=& $adb -s $Device shell dumpsys media_session
    $inBili=$false
    $state=foreach($line in $lines){
        if($line -match '^\s+package='){$inBili=$line -match 'package=tv.danmaku.bili'}
        if($inBili -and $line -match '^\s+(active=|state=PlaybackState)'){$line.Trim()}
    }
    @{time=(Get-Date -Format o);phase=$Phase;state=$state} | ConvertTo-Json | Set-Content $Path
    return ($state -join "`n")
}
