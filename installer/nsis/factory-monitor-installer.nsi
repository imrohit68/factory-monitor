; Factory Monitor — Windows installer (NSIS 3+, Unicode)
;
; The OUTPUT .exe is self-contained: at compile time, all files under APP_SOURCE_DIR
; (jpackage app-image: launcher, JAR, embedded JRE) are compressed into this installer.
; People you share the .exe with do NOT need any other files — only this binary.
;
; Build (developer machine):  installer\build-windows.ps1
; Manual NSIS:  cd installer && makensis /DAPP_SOURCE_DIR=stage\FactoryMonitor nsis\factory-monitor-installer.nsi
; (stage\FactoryMonitor is produced by jpackage; only needed while building, not for distribution.)

!ifndef APP_SOURCE_DIR
  !define APP_SOURCE_DIR "stage\FactoryMonitor"
!endif

Unicode true
SetCompressor /SOLID lzma
RequestExecutionLevel admin
Name "Factory Monitor"
OutFile "..\..\target\factory-monitor-installer.exe"
InstallDir "$PROGRAMFILES64\Factory Monitor"
InstallDirRegKey HKLM "Software\FactoryMonitor" "InstallDir"

!include "MUI2.nsh"
!include "nsDialogs.nsh"
!include "LogicLib.nsh"
!include "FileFunc.nsh"

!insertmacro GetParameters
!insertmacro GetOptions

!define PRODUCT_VERSION "0.0.1"
!define MUI_ABORTWARNING
!define MUI_ICON "${NSISDIR}\Contrib\Graphics\Icons\modern-install.ico"
!define MUI_UNICON "${NSISDIR}\Contrib\Graphics\Icons\modern-uninstall.ico"

!define MUI_WELCOMEPAGE_TITLE "Welcome to the Factory Monitor Setup Wizard"
!define MUI_WELCOMEPAGE_TEXT "This will install Factory Monitor (workstation dashboard, Modbus, event log).$\r$\n$\r$\nYou can accept defaults and click through, or adjust data location, security, and serial settings before files are copied.$\r$\n$\r$\nClick Next to continue."

!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_DIRECTORY
Page custom PageConfig PageConfigLeave
Page custom PagePorts PagePortsLeave
!insertmacro MUI_PAGE_INSTFILES
!define MUI_FINISHPAGE_RUN "$INSTDIR\FactoryMonitor.exe"
!define MUI_FINISHPAGE_RUN_TEXT "Launch Factory Monitor"
!define MUI_FINISHPAGE_SHOWREADME "$INSTDIR\install.log"
!define MUI_FINISHPAGE_SHOWREADME_TEXT "Open data directory in Explorer"
!define MUI_FINISHPAGE_SHOWREADME_NOTCHECKED
!define MUI_FINISHPAGE_SHOWREADME_FUNCTION finishOpenDataDir
!insertmacro MUI_PAGE_FINISH

!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES
!insertmacro MUI_UNPAGE_FINISH

!insertmacro MUI_LANGUAGE "English"

Var DataDir
Var RecoverMode
Var RecoveryPath
Var InitialUser
Var InitialPass
Var AlertRepeatMin
Var AlertMaxRepeats
Var ModbusSlaveId
Var ModbusBaud
Var ModbusStopBits
Var ModbusPollMs
Var PortManual
Var HEditDataDir
Var HEditUser
Var HEditPass
Var HEditRepeat
Var HEditMaxRep
Var HEditSlave
Var HEditBaud
Var HEditStop
Var HEditPoll
Var HLabelRecovery
Var LogFile

Function finishOpenDataDir
  ExecShell "open" "explorer.exe" "$DataDir"
FunctionEnd

Function .onInit
  StrCpy $DataDir "$PROFILE\factory-monitor-data"
  StrCpy $RecoverMode "0"
  StrCpy $RecoveryPath ""
  StrCpy $InitialUser "admin"
  StrCpy $InitialPass "admin@123"
  StrCpy $AlertRepeatMin "15"
  StrCpy $AlertMaxRepeats "4"
  StrCpy $ModbusSlaveId "1"
  StrCpy $ModbusBaud "9600"
  StrCpy $ModbusStopBits "1"
  StrCpy $ModbusPollMs "5000"

  StrCpy $PortManual "COM1"

  ${If} ${Silent}
    Call SilentDefaults
  ${EndIf}

  ${GetParameters} $0
  ClearErrors
  ${GetOptions} $0 "/D=" $R0
  ${If} ${Errors}
    StrCpy $R0 ""
  ${EndIf}
  ${If} $R0 != ""
    StrCpy $INSTDIR $R0
  ${EndIf}
FunctionEnd

Function SilentDefaults
  Call ScanRecoverySilent
  ${If} $RecoveryPath != ""
    StrCpy $DataDir $RecoveryPath
    StrCpy $RecoverMode "1"
  ${EndIf}
FunctionEnd

Function TakeFirstLine
  Exch $0
  Push $1
  Push $2
  Push $3
  StrLen $3 $0
  StrCpy $1 0
  ${Do}
    ${If} $1 >= $3
      StrCpy $2 $0
      ${ExitDo}
    ${EndIf}
    StrCpy $R3 $0 1 $1
    ${If} $R3 == "$\r"
      StrCpy $2 $0 $1
      ${ExitDo}
    ${EndIf}
    ${If} $R3 == "$\n"
      StrCpy $2 $0 $1
      ${ExitDo}
    ${EndIf}
    IntOp $1 $1 + 1
  ${Loop}
  StrCpy $0 $2
  Pop $3
  Pop $2
  Pop $1
  Exch $0
FunctionEnd

Function ScanRecoverySilent
  StrCpy $RecoveryPath ""
  StrCpy $RecoverMode "0"
  GetTempFileName $9
  StrCpy $8 "$9-recover.ps1"
  FileOpen $7 "$8" w
  FileWrite $7 "$$drives = Get-PSDrive -PSProvider FileSystem -ErrorAction SilentlyContinue$\r$\n"
  FileWrite $7 "foreach ($$d in $$drives) {$$p = Join-Path $$d.Root 'factory-monitor-data'; if (Test-Path -LiteralPath $$p) { Write-Output $$p }}$\r$\n"
  FileClose $7
  nsExec::ExecToStack 'powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$8"'
  Pop $0
  Pop $1
  Delete "$8"
  ${If} $1 != ""
    Push $1
    Call TakeFirstLine
    Pop $1
    StrCpy $RecoveryPath $1
    StrCpy $RecoverMode "1"
  ${EndIf}
FunctionEnd

Function ScanRecoveryPaths
  Call ScanRecoverySilent
FunctionEnd

Function PageConfig
  Call ScanRecoveryPaths

  nsDialogs::Create 1018
  Pop $0

  ${NSD_CreateLabel} 0 0 100% 24u "Choose where application data (SQLite DB, alert audio) is stored (system.data-dir)."
  ${NSD_CreateHLine} 0 28u 100% 1u ""
  ${NSD_CreateLabel} 0 36u 38% 10u "Data directory"
  ${NSD_CreateText} 38% 34u 52% 12u "$DataDir"
  Pop $HEditDataDir
  ${NSD_CreateBrowseButton} 92% 33u 8% 14u "..."
  Pop $R9
  ${NSD_OnClick} $R9 BrowseDataDir

  ${NSD_CreateLabel} 0 52u 100% 24u ""
  Pop $HLabelRecovery
  ${If} $RecoveryPath != ""
    ${NSD_SetText} $HLabelRecovery "Existing data folder detected: $RecoveryPath$\r$\nUse $\"Recover$\" below or edit the path for a fresh database."
  ${Else}
    ${NSD_SetText} $HLabelRecovery "No existing factory-monitor-data folder was found on any drive (fresh install)."
  ${EndIf}

  ${NSD_CreateButton} 0 82u 48% 14u "Recover detected data"
  Pop $R9
  ${NSD_OnClick} $R9 OnRecoverClick

  ${NSD_CreateButton} 52% 82u 48% 14u "Fresh setup"
  Pop $R9
  ${NSD_OnClick} $R9 OnFreshClick

  ${NSD_CreateHLine} 0 102u 100% 1u ""

  ${NSD_CreateLabel} 0 110u 48% 10u "Initial admin username"
  ${NSD_CreateText} 52% 108u 48% 12u "$InitialUser"
  Pop $HEditUser
  ${NSD_CreateLabel} 0 126u 48% 10u "Initial admin password (first DB only)"
  ${NSD_CreateText} 52% 124u 48% 12u "$InitialPass"
  Pop $HEditPass

  ${NSD_CreateLabel} 0 142u 48% 10u "Dashboard alert repeat (minutes)"
  ${NSD_CreateText} 52% 140u 48% 12u "$AlertRepeatMin"
  Pop $HEditRepeat
  ${NSD_CreateLabel} 0 158u 48% 10u "Dashboard alert max repeats"
  ${NSD_CreateText} 52% 156u 48% 12u "$AlertMaxRepeats"
  Pop $HEditMaxRep

  ${NSD_CreateHLine} 0 174u 100% 1u ""
  ${NSD_CreateLabel} 0 182u 100% 12u "Modbus RTU (serial port on next page)"
  ${NSD_CreateLabel} 0 198u 48% 10u "Input slave ID"
  ${NSD_CreateText} 52% 196u 48% 12u "$ModbusSlaveId"
  Pop $HEditSlave
  ${NSD_CreateLabel} 0 214u 48% 10u "Baud rate"
  ${NSD_CreateText} 52% 212u 48% 12u "$ModbusBaud"
  Pop $HEditBaud
  ${NSD_CreateLabel} 0 230u 48% 10u "Stop bits"
  ${NSD_CreateText} 52% 228u 48% 12u "$ModbusStopBits"
  Pop $HEditStop
  ${NSD_CreateLabel} 0 246u 48% 10u "Poll interval (ms)"
  ${NSD_CreateText} 52% 244u 48% 12u "$ModbusPollMs"
  Pop $HEditPoll

  nsDialogs::Show
FunctionEnd

Function BrowseDataDir
  nsDialogs::SelectFolderDialog /NOUNLOAD "$DataDir" "Select data directory"
  Pop $0
  ${If} $0 != error
    StrCpy $DataDir $0
    ${NSD_SetText} $HEditDataDir $DataDir
  ${EndIf}
FunctionEnd

Function OnRecoverClick
  ${If} $RecoveryPath != ""
    StrCpy $DataDir $RecoveryPath
    StrCpy $RecoverMode "1"
    ${NSD_SetText} $HEditDataDir $DataDir
    MessageBox MB_OK "Data directory set to:$\r$\n$DataDir$\r$\n$\r$\nExisting database will be used. Initial username/password apply only when no users exist yet."
  ${Else}
    MessageBox MB_ICONEXCLAMATION "No existing factory-monitor-data folder was found."
  ${EndIf}
FunctionEnd

Function OnFreshClick
  StrCpy $RecoverMode "0"
  MessageBox MB_OK "Fresh setup: use a new or empty data folder. Folders are created on first run."
FunctionEnd

Function PageConfigLeave
  ${NSD_GetText} $HEditDataDir $DataDir
  ${NSD_GetText} $HEditUser $InitialUser
  ${NSD_GetText} $HEditPass $InitialPass
  ${NSD_GetText} $HEditRepeat $AlertRepeatMin
  ${NSD_GetText} $HEditMaxRep $AlertMaxRepeats
  ${NSD_GetText} $HEditSlave $ModbusSlaveId
  ${NSD_GetText} $HEditBaud $ModbusBaud
  ${NSD_GetText} $HEditStop $ModbusStopBits
  ${NSD_GetText} $HEditPoll $ModbusPollMs

  ${If} $DataDir == ""
    MessageBox MB_ICONEXCLAMATION "Data directory cannot be empty."
    Abort
  ${EndIf}

  Call ValidatePositiveInt
  Pop $0
  StrCmp $0 ok +3
    MessageBox MB_ICONEXCLAMATION "Enter valid positive whole numbers for intervals, Modbus, and poll fields."
    Abort
FunctionEnd

Function ValidatePositiveInt
  Push $AlertRepeatMin
  Call IsPosInt
  Pop $0
  ${If} $0 != 1
    Push "bad"
    Return
  ${EndIf}
  Push $AlertMaxRepeats
  Call IsPosInt
  Pop $0
  ${If} $0 != 1
    Push "bad"
    Return
  ${EndIf}
  Push $ModbusSlaveId
  Call IsPosInt
  Pop $0
  ${If} $0 != 1
    Push "bad"
    Return
  ${EndIf}
  Push $ModbusBaud
  Call IsPosInt
  Pop $0
  ${If} $0 != 1
    Push "bad"
    Return
  ${EndIf}
  Push $ModbusStopBits
  Call IsPosInt
  Pop $0
  ${If} $0 != 1
    Push "bad"
    Return
  ${EndIf}
  Push $ModbusPollMs
  Call IsPosInt
  Pop $0
  ${If} $0 != 1
    Push "bad"
    Return
  ${EndIf}
  Push "ok"
FunctionEnd

Function IsPosInt
  Exch $0
  Push $1
  Push $2
  StrLen $1 $0
  ${If} $1 == 0
    Pop $2
    Pop $1
    Exch $0
    Push 0
    Return
  ${EndIf}
  StrCpy $2 0
  ${Do}
    ${If} $2 >= $1
      Pop $2
      Pop $1
      Exch $0
      Push 1
      Return
    ${EndIf}
    StrCpy $R3 $0 1 $2
    ${If} $R3 < "0"
      Pop $2
      Pop $1
      Exch $0
      Push 0
      Return
    ${EndIf}
    ${If} $R3 > "9"
      Pop $2
      Pop $1
      Exch $0
      Push 0
      Return
    ${EndIf}
    IntOp $2 $2 + 1
  ${Loop}
FunctionEnd

Function PagePorts
  nsDialogs::Create 1018
  Pop $0

  ${NSD_CreateLabel} 0 0 100% 32u "Modbus serial port (system.modbus.port-name). Click Detect to list ports from Windows, or type e.g. COM1, COM3."
  ${NSD_CreateLabel} 0 40u 100% 10u "Port name"
  ${NSD_CreateText} 0 52u 100% 12u "COM1"
  Pop $PortManual
  ${NSD_CreateButton} 0 72u 100% 14u "Detect serial ports"
  Pop $R9
  ${NSD_OnClick} $R9 OnDetectPorts

  nsDialogs::Show
FunctionEnd

Function OnDetectPorts
  nsExec::ExecToStack 'powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "([System.IO.Ports.SerialPort]::getportnames()) | Select-Object -First 1"'
  Pop $0
  Pop $1
  ${If} $1 != ""
    ${NSD_SetText} $PortManual $1
  ${Else}
    MessageBox MB_OK "No serial ports reported by Windows. Enter the port manually (e.g. COM1)."
  ${EndIf}
FunctionEnd

Function PagePortsLeave
  ${NSD_GetText} $PortManual $0
  StrCpy $PortManual $0
  ${If} $PortManual == ""
    MessageBox MB_ICONEXCLAMATION "Enter a serial port name (e.g. COM3)."
    Abort
  ${EndIf}
FunctionEnd

Function WriteAppConfig
  ; Overrides only — merged with application.properties inside the Spring Boot JAR (SQLite path uses ${system.data-dir}).
  CreateDirectory "$INSTDIR\config"
  FileOpen $0 "$INSTDIR\config\application.properties" w
  FileWrite $0 "# Generated by Factory Monitor installer (merged with defaults in the application JAR)$\r$\n"
  FileWrite $0 "system.data-dir=$DataDir$\r$\n"
  FileWrite $0 "system.security.initial-username=$InitialUser$\r$\n"
  FileWrite $0 "system.security.initial-password=$InitialPass$\r$\n"
  FileWrite $0 "system.dashboard-alert-repeat-interval-minutes=$AlertRepeatMin$\r$\n"
  FileWrite $0 "system.dashboard-alert-max-repeats=$AlertMaxRepeats$\r$\n"
  FileWrite $0 "system.modbus.input-slave-id=$ModbusSlaveId$\r$\n"
  FileWrite $0 "system.modbus.port-name=$PortManual$\r$\n"
  FileWrite $0 "system.modbus.baud-rate=$ModbusBaud$\r$\n"
  FileWrite $0 "system.modbus.stop-bits=$ModbusStopBits$\r$\n"
  FileWrite $0 "system.modbus.poll-interval-ms=$ModbusPollMs$\r$\n"
  FileClose $0
FunctionEnd

Function WriteInstallLog
  FileOpen $LogFile "$INSTDIR\install.log" a
  FileWrite $LogFile "--- $Date $Time ---$\r$\n"
  FileWrite $LogFile "INSTDIR=$INSTDIR$\r$\n"
  FileWrite $LogFile "DataDir=$DataDir$\r$\n"
  FileWrite $LogFile "RecoverMode=$RecoverMode$\r$\n"
  FileWrite $LogFile "Port=$PortManual$\r$\n"
  FileClose $LogFile
FunctionEnd

Section "Application" SecApp
  SetOutPath "$INSTDIR"
  FileOpen $LogFile "$INSTDIR\install.log" w
  FileWrite $LogFile "Factory Monitor installer log$\r$\n"
  FileClose $LogFile

  File /r "${APP_SOURCE_DIR}\*.*"

  Call WriteAppConfig

  SetOutPath "$INSTDIR"
  WriteRegStr HKLM "Software\FactoryMonitor" "InstallDir" "$INSTDIR"
  WriteRegStr HKLM "Software\FactoryMonitor" "DataDir" "$DataDir"

  Call WriteInstallLog

  WriteUninstaller "$INSTDIR\Uninstall.exe"
  WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\FactoryMonitor" "DisplayName" "Factory Monitor"
  WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\FactoryMonitor" "DisplayVersion" "${PRODUCT_VERSION}"
  WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\FactoryMonitor" "Publisher" "Factory Monitor"
  WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\FactoryMonitor" "UninstallString" "$INSTDIR\Uninstall.exe"
  WriteRegDWORD HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\FactoryMonitor" "NoModify" 1
  WriteRegDWORD HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\FactoryMonitor" "NoRepair" 1
SectionEnd

Section "Shortcuts" SecShortcuts
  CreateDirectory "$SMPROGRAMS\Factory Monitor"
  CreateShortCut "$SMPROGRAMS\Factory Monitor\Factory Monitor.lnk" "$INSTDIR\FactoryMonitor.exe" "" "$INSTDIR" 0 SW_SHOWNORMAL "" "Factory Monitor"
  CreateShortCut "$SMPROGRAMS\Factory Monitor\Uninstall Factory Monitor.lnk" "$INSTDIR\Uninstall.exe" "" "$INSTDIR" 0
  CreateShortCut "$DESKTOP\Factory Monitor.lnk" "$INSTDIR\FactoryMonitor.exe" "" "$INSTDIR" 0 SW_SHOWNORMAL "" "Factory Monitor"
SectionEnd

Section Uninstall
  Delete "$INSTDIR\install.log"
  Delete "$INSTDIR\Uninstall.exe"
  RMDir /r "$INSTDIR"
  Delete "$SMPROGRAMS\Factory Monitor\Factory Monitor.lnk"
  Delete "$SMPROGRAMS\Factory Monitor\Uninstall Factory Monitor.lnk"
  RMDir "$SMPROGRAMS\Factory Monitor"
  Delete "$DESKTOP\Factory Monitor.lnk"
  DeleteRegKey HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\FactoryMonitor"
  DeleteRegKey HKLM "Software\FactoryMonitor"
SectionEnd

!insertmacro MUI_FUNCTION_DESCRIPTION_BEGIN
  !insertmacro MUI_DESCRIPTION_TEXT ${SecApp} "Application and bundled Java runtime (jpackage app-image)."
  !insertmacro MUI_DESCRIPTION_TEXT ${SecShortcuts} "Start Menu and Desktop shortcuts."
!insertmacro MUI_FUNCTION_DESCRIPTION_END

Function un.onInit
  MessageBox MB_YESNO|MB_ICONQUESTION "Remove Factory Monitor from this computer?" IDYES +2
  Abort
FunctionEnd
