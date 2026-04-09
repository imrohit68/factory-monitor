app-icon.ico
------------
Windows jpackage and NSIS use this file for the installed .exe and setup wizard icon.

The build script also copies app-icon.ico and app-logo.png into the jpackage app folder so:
- Start Menu / Desktop shortcuts can use app-icon.ico
- app-logo.png remains beside the launcher for branding (default run mode opens the system browser, not JavaFX)

It was generated from src/main/resources/static/images/app-logo.png (padded to a square
with macOS `sips`, then converted with png-to-ico). To regenerate after changing the logo:

  sips -p WIDTH HEIGHT --padColor FFFFFF app-logo.png --out square.png
  (use WIDTH = HEIGHT = max(original width, height))

  npm install png-to-ico
  npx png-to-ico square.png > installer/windows/app-icon.ico

Or use any tool that exports a multi-size .ico (16–256 px).

ffmpeg.exe (Windows installer / OGG alert audio)
-----------------------------------------------
The production app transcodes OGG uploads to MP3 using FFmpeg. The Windows build script
(installer\build-windows.ps1) places ffmpeg.exe in the jpackage app folder next to
ProductionCallingSystem.exe so end users do not need a separate FFmpeg install.

- By default the script downloads a win64 GPL build from BtbN FFmpeg-Builds (requires network).
- To skip the download, place a 64-bit ffmpeg.exe at installer\windows\ffmpeg.exe before building.

FFmpeg is licensed under the GPL (or LGPL depending on build). Ensure your distribution
complies with FFmpeg licensing if you ship this binary.
