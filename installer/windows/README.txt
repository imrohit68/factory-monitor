app-icon.ico
------------
Windows jpackage and NSIS use this file for the installed .exe and setup wizard icon.

The build script also copies app-icon.ico and app-logo.png into the jpackage app folder so:
- Start Menu / Desktop shortcuts can use app-icon.ico
- app-logo.png remains beside the launcher for branding (default run mode opens the system browser, not JavaFX)

Source of truth for the logo asset is the same as the desktop window icon:
  src/main/resources/static/images/app-logo.png
(has transparency / alpha). Web templates may use app-logo.svg; keep PNG in sync when the brand changes.

Regenerate installer/windows/app-icon.ico after changing the PNG (from repo root, macOS/Linux with ImageMagick):

  magick src/main/resources/static/images/app-logo.png \
    -define icon:auto-resize=256,128,96,64,48,32,16 \
    installer/windows/app-icon.ico

This embeds multiple sizes with alpha (no white padding). On Windows without magick, use any tool that exports a multi-size .ico (16–256 px) from the PNG.

ffmpeg.exe (Windows installer / OGG alert audio)
-----------------------------------------------
The production app transcodes OGG uploads to MP3 using FFmpeg. The Windows build script
(installer\build-windows.ps1) places ffmpeg.exe in the jpackage app folder next to
ProductionCallingSystem.exe so end users do not need a separate FFmpeg install.

- By default the script downloads a win64 GPL build from BtbN FFmpeg-Builds (requires network).
- To skip the download, place a 64-bit ffmpeg.exe at installer\windows\ffmpeg.exe before building.

FFmpeg is licensed under the GPL (or LGPL depending on build). Ensure your distribution
complies with FFmpeg licensing if you ship this binary.
