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
