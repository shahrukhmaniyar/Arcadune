<#
.SYNOPSIS
  Repairs the emulator cores after cloning on Windows.

.DESCRIPTION
  lemuroid-cores/bundled-cores/src/main/jniLibs contains git symlinks that point at the real
  core libraries. Git on Windows (core.symlinks=false) checks them out as tiny text files that
  contain the target path, so the built app ships with fake cores and every game fails with
  "libretro core was not loaded".

  This script replaces each of those text files with a copy of the file it points to. It is safe
  to run more than once. Run it after `git submodule update --init`, before building.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts\fix-bundled-cores.ps1
#>

$repoRoot = Split-Path -Parent $PSScriptRoot
$jniLibs = Join-Path $repoRoot "lemuroid-cores\bundled-cores\src\main\jniLibs"

if (-not (Test-Path $jniLibs)) {
    Write-Error "Not found: $jniLibs. Run 'git submodule update --init --recursive' first."
    exit 1
}

$fixed = 0
$missing = 0

foreach ($file in Get-ChildItem -Path $jniLibs -Recurse -Filter *.so) {
    # Real cores are megabytes. Symlink placeholders are a few dozen bytes of path text.
    if ($file.Length -eq 0 -or $file.Length -gt 1000) { continue }

    $target = (Get-Content -Path $file.FullName -Raw).Trim()
    $targetPath = Join-Path $file.DirectoryName $target

    if (-not (Test-Path $targetPath -PathType Leaf)) {
        Write-Warning "Target not found for $($file.FullName): $target"
        $missing++
        continue
    }

    Copy-Item -Path $targetPath -Destination $file.FullName -Force
    $fixed++
}

Write-Host "Repaired $fixed core file(s); $missing could not be resolved."
if ($missing -gt 0) { exit 1 }
