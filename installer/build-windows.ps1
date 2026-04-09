#Requires -Version 5.1
<#
  Builds ONE self-contained Windows installer .exe you can share as-is.

  At compile time, NSIS embeds the full jpackage app (JAR + bundled JRE + launcher)
  inside the installer binary. End users only download/run that single file - no
  separate zip, stage folder, or extra downloads.

  Prerequisites (local Windows only): JDK 17+ (jpackage), Maven, NSIS 3+ (makensis).

  Building on Mac (M1/M2): Oracle jpackage cannot create Windows installers on macOS.
  Use GitHub Actions instead: push the repo, open Actions, run "Windows installer" workflow,
  then download the artifact (one .exe). See .github/workflows/windows-installer.yml

  Usage (from repo root, on Windows):
    .\installer\build-windows.ps1
    .\installer\build-windows.ps1 -CleanStage   # remove installer\stage after success

  Output to distribute:  target\production-calling-system-installer.exe
#>
param(
    [switch] $CleanStage
)
Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$RepoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $RepoRoot

$JarName = "production-calling-system-0.0.1-SNAPSHOT.jar"
$JarPath = Join-Path (Join-Path $RepoRoot "target") $JarName
$StageDir = Join-Path $PSScriptRoot "stage"
$AppImageDir = Join-Path $StageDir "ProductionCallingSystem"

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

$IconIco = Join-Path $PSScriptRoot "windows\app-icon.ico"
# Packaged .exe: JavaFX WebView UI; window close does not exit (Admin → Shut down application).
$JpkgArgs = @(
    '--type', 'app-image',
    '--name', 'ProductionCallingSystem',
    '--input', (Join-Path $RepoRoot "target"),
    '--main-jar', $JarName,
    '--main-class', 'org.springframework.boot.loader.launch.JarLauncher',
    '--dest', $StageDir,
    '--java-options', '-Dfile.encoding=UTF-8',
    '--java-options', '-Dsystem.desktop-mode=true',
    '--java-options', '-Dsystem.launch-browser=false',
    '--java-options', '-Dsystem.desktop-allow-window-close=false',
    '--app-version', '0.0.1'
)
if (Test-Path -LiteralPath $IconIco) {
    Write-Host "==> Using application icon: $IconIco"
    $JpkgArgs += @('--icon', $IconIco)
} else {
    Write-Warning "No app-icon.ico at $IconIco - exe will use default Java icon. See installer/windows/README.txt"
}

# Do not add --win-console here - the launcher should not show a console window.
& jpackage @JpkgArgs

if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$Exe = Join-Path $AppImageDir "ProductionCallingSystem.exe"
if (-not (Test-Path -LiteralPath $Exe)) {
    Write-Error "jpackage did not produce: $Exe"
}

# Ship branding next to the launcher: shortcuts and JavaFX load app-icon.ico / app-logo.png from here.
# (jpackage --icon embeds in .exe, but a separate .ico keeps Start Menu / taskbar reliable.)
$LogoPng = Join-Path $RepoRoot "src\main\resources\static\images\app-logo.png"
if (Test-Path -LiteralPath $IconIco) {
    Copy-Item -LiteralPath $IconIco -Destination (Join-Path $AppImageDir "app-icon.ico") -Force
    Write-Host "==> Copied app-icon.ico into app image"
}
if (Test-Path -LiteralPath $LogoPng) {
    Copy-Item -LiteralPath $LogoPng -Destination (Join-Path $AppImageDir "app-logo.png") -Force
    Write-Host "==> Copied app-logo.png into app image (optional branding beside launcher)"
}

# FFmpeg next to ProductionCallingSystem.exe — Java discovers user.dir\ffmpeg.exe first (OGG -> MP3).
$DestFfmpeg = Join-Path $AppImageDir "ffmpeg.exe"
$ManualFfmpeg = Join-Path $PSScriptRoot "windows\ffmpeg.exe"
if (Test-Path -LiteralPath $ManualFfmpeg) {
    Copy-Item -LiteralPath $ManualFfmpeg -Destination $DestFfmpeg -Force
    Write-Host "==> Bundled FFmpeg from installer\windows\ffmpeg.exe"
} else {
    Write-Host "==> Downloading FFmpeg win64 GPL (BtbN) for OGG transcoding..."
    $zipUrl = "https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-win64-gpl.zip"
    $zipPath = Join-Path $StageDir "ffmpeg-win64-gpl.zip"
    $extractDir = Join-Path $StageDir "ffmpeg-extract-bundle"
    try {
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
        Invoke-WebRequest -Uri $zipUrl -OutFile $zipPath -UseBasicParsing
        if (Test-Path -LiteralPath $extractDir) { Remove-Item -LiteralPath $extractDir -Recurse -Force }
        New-Item -ItemType Directory -Path $extractDir -Force | Out-Null
        Expand-Archive -LiteralPath $zipPath -DestinationPath $extractDir -Force
        $ff = Get-ChildItem -LiteralPath $extractDir -Recurse -Filter "ffmpeg.exe" -File | Select-Object -First 1
        if (-not $ff) {
            Write-Error "Downloaded FFmpeg zip did not contain ffmpeg.exe"
        }
        Copy-Item -LiteralPath $ff.FullName -Destination $DestFfmpeg -Force
        Write-Host "==> Bundled FFmpeg: $DestFfmpeg"
    } finally {
        if (Test-Path -LiteralPath $zipPath) { Remove-Item -LiteralPath $zipPath -Force -ErrorAction SilentlyContinue }
        if (Test-Path -LiteralPath $extractDir) { Remove-Item -LiteralPath $extractDir -Recurse -Force -ErrorAction SilentlyContinue }
    }
}

Write-Host "==> NSIS (makensis)"
Set-Location $PSScriptRoot
$makensis = Get-Command makensis -ErrorAction SilentlyContinue
if (-not $makensis) {
    Write-Error "makensis not found. Install NSIS 3+ and add it to PATH."
}

& makensis "/DAPP_SOURCE_DIR=$AppImageDir" (Join-Path $PSScriptRoot "nsis\factory-monitor-installer.nsi")
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$Out = Join-Path $RepoRoot "target\production-calling-system-installer.exe"
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
