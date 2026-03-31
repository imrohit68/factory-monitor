#Requires -Version 5.1
<#
  Builds ONE self-contained Windows installer .exe you can share as-is.

  At compile time, NSIS embeds the full jpackage app (JAR + bundled JRE + launcher)
  inside the installer binary. End users only download/run that single file — no
  separate zip, stage folder, or extra downloads.

  Prerequisites (local Windows only): JDK 17+ (jpackage), Maven, NSIS 3+ (makensis).

  Building on Mac (M1/M2): Oracle jpackage cannot create Windows installers on macOS.
  Use GitHub Actions instead: push the repo, open Actions → "Windows installer" → Run workflow,
  then download the artifact (one .exe). See .github/workflows/windows-installer.yml

  Usage (from repo root, on Windows):
    .\installer\build-windows.ps1
    .\installer\build-windows.ps1 -CleanStage   # remove installer\stage after success

  Output to distribute:  target\factory-monitor-installer.exe

  App icon: installer\windows\app-icon.ico (jpackage exe, NSIS wizard). Copy the same file to
  src\main\resources\static\favicon.ico when you change the logo.
  Regenerate from SVG (from repo root): Python venv with cairosvg + Pillow, or ImageMagick from installer\windows:
    magick -background none ..\..\src\main\resources\static\images\app-logo.svg -define icon:auto-resize=256,128,64,48,32,16 app-icon.ico
#>
param(
    [switch] $CleanStage
)
Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$RepoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $RepoRoot

$JarName = "factory-monitor-0.0.1-SNAPSHOT.jar"
$JarPath = Join-Path (Join-Path $RepoRoot "target") $JarName
$StageDir = Join-Path $PSScriptRoot "stage"
$AppImageDir = Join-Path $StageDir "FactoryMonitor"

$Mvnw = Join-Path $RepoRoot "mvnw.cmd"
if (Test-Path -LiteralPath $Mvnw) {
    Write-Host "==> Maven package (mvnw.cmd)"
    & $Mvnw -q -DskipTests package
} else {
    Write-Host "==> Maven package (mvn)"
    & mvn -q -DskipTests package
}
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

if (-not (Test-Path -LiteralPath $JarPath)) {
    Write-Error "Expected JAR not found: $JarPath"
}

if (-not (Get-Command jpackage -ErrorAction SilentlyContinue)) {
    Write-Error "jpackage not found. Install JDK 17+ and ensure JAVA_HOME\bin is on PATH."
}

Write-Host "==> jpackage app-image (bundled runtime)"
if (Test-Path -LiteralPath $AppImageDir) {
    Remove-Item -LiteralPath $AppImageDir -Recurse -Force
}
New-Item -ItemType Directory -Path $StageDir -Force | Out-Null

# Do not add --win-console here — the desktop launcher should not show a console window.
$IconPath = Join-Path $PSScriptRoot "windows\app-icon.ico"
$jpackageArgs = @(
    "--type", "app-image",
    "--name", "FactoryMonitor",
    "--input", (Join-Path $RepoRoot "target"),
    "--main-jar", $JarName,
    "--main-class", "org.springframework.boot.loader.launch.JarLauncher",
    "--dest", $StageDir,
    "--java-options", "-Dfile.encoding=UTF-8",
    "--app-version", "0.0.1"
)
if (Test-Path -LiteralPath $IconPath) {
    $jpackageArgs += @("--icon", $IconPath)
} else {
    Write-Warning "No app icon at $IconPath - exe uses default Java icon. Regenerate from app-logo.svg if needed."
}

& jpackage @jpackageArgs

if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$Exe = Join-Path $AppImageDir "FactoryMonitor.exe"
if (-not (Test-Path -LiteralPath $Exe)) {
    Write-Error "jpackage did not produce: $Exe"
}

Write-Host "==> NSIS (makensis)"
Set-Location $PSScriptRoot
$makensis = Get-Command makensis -ErrorAction SilentlyContinue
if (-not $makensis) {
    Write-Error "makensis not found. Install NSIS 3+ and add it to PATH."
}

$IconSrc = Join-Path $PSScriptRoot "windows\app-icon.ico"
$IconNsis = Join-Path $PSScriptRoot "nsis\app-icon.ico"
if (Test-Path -LiteralPath $IconSrc) {
    Copy-Item -LiteralPath $IconSrc -Destination $IconNsis -Force
} else {
    Write-Warning "Missing $IconSrc - NSIS will fail if app-icon.ico is not in nsis folder. Generate from src\main\resources\static\images\app-logo.svg (see comment in this script)."
}

& makensis "/DAPP_SOURCE_DIR=$AppImageDir" (Join-Path $PSScriptRoot "nsis\factory-monitor-installer.nsi")
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$Out = Join-Path $RepoRoot "target\factory-monitor-installer.exe"
Write-Host ""
Write-Host "================================================================" -ForegroundColor Green
Write-Host "  SINGLE FILE TO SHARE (nothing else required for end users):" -ForegroundColor Green
Write-Host "  $Out" -ForegroundColor Cyan
Write-Host "================================================================" -ForegroundColor Green
Write-Host ""

if ($CleanStage -and (Test-Path -LiteralPath $StageDir)) {
    Write-Host "==> Removing $StageDir (-CleanStage)"
    Remove-Item -LiteralPath $StageDir -Recurse -Force
}
