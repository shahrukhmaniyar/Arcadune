# Arcadune on-phone smoke test: opens every Settings screen and dialog, the library actions and a
# game's pause menu, in portrait and landscape, and fails if the app crashes anywhere.
#
# Run it with the phone connected over adb, against the build you are about to release:
#   powershell -ExecutionPolicy Bypass -File scripts/smoke-test.ps1 -Package io.github.shahrukhmaniyar.arcadune.debug
# The app must already have a ROMs folder chosen, so a game can be opened.
param(
    [string]$Package = "io.github.shahrukhmaniyar.arcadune.debug",
    [string]$Adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
)
$ErrorActionPreference = "Stop"
$script:failures = New-Object System.Collections.Generic.List[string]

function Get-Nodes {
    & $Adb shell uiautomator dump /sdcard/arcadune-smoke.xml | Out-Null
    $raw = (& $Adb exec-out cat /sdcard/arcadune-smoke.xml) -join "`n"
    $i = $raw.IndexOf('<?xml'); if ($i -ge 0) { $raw = $raw.Substring($i) }
    $xml = [xml]$raw
    foreach ($n in $xml.SelectNodes('//node')) {
        $t = $n.text; if (-not $t) { $t = $n.'content-desc' }
        if ($t -and $n.bounds -match '\[(\d+),(\d+)\]\[(\d+),(\d+)\]') {
            [pscustomobject]@{ Text = $t; X = [int](([int]$Matches[1] + [int]$Matches[3]) / 2); Y = [int](([int]$Matches[2] + [int]$Matches[4]) / 2) }
        }
    }
}

function Assert-NoCrash([string]$step) {
    $crash = & $Adb logcat -d -b crash
    if ($crash) {
        $line = ($crash | Select-String 'Exception|FATAL' | Select-Object -First 2) -join ' / '
        $script:failures.Add("CRASH after '$step': $line")
        & $Adb logcat -c -b crash
        Start-App
    }
}

$script:wantLandscape = $false

function Start-App {
    & $Adb shell am force-stop $Package
    & $Adb shell monkey -p $Package -c android.intent.category.LAUNCHER 1 | Out-Null
    Start-Sleep 6
    # Samsung's Game Booster resets the rotation when a game app starts, so the orientation is set
    # after every launch. Turning auto-rotate on and off again makes the lock take effect, and
    # rotation 3 (270 degrees) is used because some phones refuse rotation 1 while locked.
    $rotation = if ($script:wantLandscape) { 3 } else { 0 }
    & $Adb shell settings put system accelerometer_rotation 1
    Start-Sleep 1
    & $Adb shell settings put system accelerometer_rotation 0
    Start-Sleep 1
    & $Adb shell settings put system user_rotation $rotation
    Start-Sleep 3
}

function Tap([string]$label, [int]$waitSeconds = 2) {
    $node = Get-Nodes | Where-Object { $_.Text -eq $label } | Select-Object -First 1
    if (-not $node) { $node = Get-Nodes | Where-Object { $_.Text -like "*$label*" } | Select-Object -First 1 }
    if (-not $node) { return $false }
    & $Adb shell input tap $node.X $node.Y
    Start-Sleep $waitSeconds
    Assert-NoCrash $label
    return $true
}

function Find-AndTap([string]$label, [int]$w, [int]$h) {
    # Scroll to the top, then down until the label is on screen.
    1..4 | ForEach-Object { & $Adb shell input swipe ($w / 2) ($h * 0.3) ($w / 2) ($h * 0.8) 200 }
    for ($k = 0; $k -lt 20; $k++) {
        $node = Get-Nodes | Where-Object { $_.Text -eq $label -and $_.Y -gt ($h * 0.12) -and $_.Y -lt ($h * 0.92) } | Select-Object -First 1
        if ($node) {
            & $Adb shell input tap $node.X $node.Y
            Start-Sleep 2
            Assert-NoCrash $label
            return $true
        }
        & $Adb shell input swipe ($w / 2) ($h * 0.7) ($w / 2) ($h * 0.4) 300
        Start-Sleep 1
    }
    $script:failures.Add("Not found: '$label'")
    return $false
}

function Back { & $Adb shell input keyevent 4; Start-Sleep 1; Assert-NoCrash "Back" }

# The screen size as it is now (width, height), so taps and swipes match the real orientation.
function Get-ScreenSize {
    $line = & $Adb shell dumpsys window displays | Select-String -Pattern 'cur=(\d+)x(\d+)' | Select-Object -First 1
    if (-not $line) { throw "Could not read the screen size" }
    return @([int]$line.Matches[0].Groups[1].Value, [int]$line.Matches[0].Groups[2].Value)
}

function Test-Settings([int]$w, [int]$h, [string]$orientation) {
    Write-Host "== Settings ($orientation)"
    Start-App
    [void](Tap "Settings")
    # Items that open a choice dialog: open it, pick nothing, close it.
    foreach ($dialog in @("Display filter", "Viewport alignment", "Vibrate on touch")) {
        if (Find-AndTap $dialog $w $h) { Back }
    }
    # Screens: open, then come back.
    foreach ($screen in @("Change Cores", "Saves & backups", "RetroAchievements", "Controllers",
            "External devices", "BIOS", "Advanced settings", "Cover manager")) {
        if (Find-AndTap $screen $w $h) {
            if ($screen -eq "Change Cores") { if (Find-AndTap "Game Boy Advance" $w $h) { Back } }
            if ($screen -eq "Advanced settings") {
                foreach ($d in @("Cache size limit", "Factory reset")) { if (Find-AndTap $d $w $h) { Back } }
            }
            Back
        }
    }
    [void](Tap "Help"); Back
}

function Test-Game([int]$w, [int]$h) {
    Write-Host "== Game and pause menu"
    Start-App
    & $Adb shell input swipe ($w * 0.85) ($h / 2) ($w * 0.15) ($h / 2) 250; Start-Sleep 2
    $tile = Get-Nodes | Where-Object { $_.Y -gt ($h * 0.3) -and $_.Y -lt ($h * 0.8) -and $_.Text.Length -gt 3 } | Select-Object -First 1
    if (-not $tile) { $script:failures.Add("No game tile found (is a ROMs folder chosen?)"); return }
    & $Adb shell input swipe $tile.X $tile.Y $tile.X $tile.Y 900; Start-Sleep 2; Assert-NoCrash "long press"
    foreach ($action in @("Rename game")) { if (Tap $action) { Back } }
    if (-not (Tap "Resume" 12)) { $script:failures.Add("Could not start a game"); return }
    # The Menu button sits in the bottom-left of the controller on every skin.
    $menu = Get-Nodes | Where-Object { $_.Text -eq "Save" }
    if (-not $menu) { & $Adb shell input tap ([int]($w * 0.16)) ([int]($h * 0.91)); Start-Sleep 3 }
    foreach ($tileName in @("Save", "Load", "Achievements", "Cheats", "Fast-Forward", "Settings")) {
        if (Tap $tileName) { Back }
    }
    Back; Back
}

& $Adb logcat -c
& $Adb logcat -c -b crash
& $Adb shell svc power stayon true
& $Adb shell input keyevent 224
& $Adb shell settings put system accelerometer_rotation 0
try {
    & $Adb shell settings put system user_rotation 0
    Start-Sleep 2
    $size = Get-ScreenSize
    Test-Settings $size[0] $size[1] "portrait"
    Test-Game $size[0] $size[1]
    # From now on Start-App turns the phone sideways after each launch.
    $script:wantLandscape = $true
    Start-App
    $size = Get-ScreenSize
    if ($size[0] -le $size[1]) { $script:failures.Add("The phone did not turn sideways; landscape not tested") }
    else { Test-Settings $size[0] $size[1] "landscape" }
} finally {
    & $Adb shell settings put system user_rotation 0
    & $Adb shell settings put system accelerometer_rotation 1
    & $Adb shell svc power stayon false
}

if ($script:failures.Count -gt 0) {
    Write-Host "SMOKE TEST FAILED:" -ForegroundColor Red
    $script:failures | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
    exit 1
}
Write-Host "Smoke test passed: no crash in Settings, dialogs, library actions or the pause menu." -ForegroundColor Green
