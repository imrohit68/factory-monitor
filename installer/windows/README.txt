app-icon.ico
------------
Windows jpackage and NSIS use this file for the installed .exe and setup wizard icon.

The build script also copies app-icon.ico and app-logo.png into the jpackage app folder so:
- Start Menu / Desktop shortcuts can use app-icon.ico
- app-logo.png remains beside the launcher for branding (default run mode opens the system browser, not JavaFX)

Source of truth for the Windows launcher / .exe icon matches the web UI header:
  src/main/resources/static/images/app-logo.svg
(ImageMagick renders it with a transparent background, same as <img src="…app-logo.svg">.)

The desktop JavaFX window icon still loads
  src/main/resources/static/images/app-logo.png
from the classpath or next to the launcher — export an updated PNG from the SVG when the brand changes so both match.

Regenerate installer/windows/app-icon.ico after changing the SVG (from repo root, macOS/Linux with ImageMagick):

  magick -background none src/main/resources/static/images/app-logo.svg \
    -define icon:auto-resize=256,128,96,64,48,32,16 \
    installer/windows/app-icon.ico

On Windows without magick, use any tool that exports a multi-size .ico (16–256 px) from the SVG with transparency preserved.

Do not rename a .png to .ico — NSIS and Windows require a real ICO container (ImageMagick “icon:auto-resize” or an ICO exporter). A PNG file with an .ico extension will fail with “invalid icon file”.

ffmpeg.exe (Windows installer / OGG alert audio)
-----------------------------------------------
The production app transcodes OGG uploads to MP3 using FFmpeg. The Windows build script
(installer\build-windows.ps1) places ffmpeg.exe in the jpackage app folder next to
ProductionCallingSystem.exe so end users do not need a separate FFmpeg install.

- By default the script downloads a win64 GPL build from BtbN FFmpeg-Builds (requires network).
- To skip the download, place a 64-bit ffmpeg.exe at installer\windows\ffmpeg.exe before building.

FFmpeg is licensed under the GPL (or LGPL depending on build). Ensure your distribution
complies with FFmpeg licensing if you ship this binary.
