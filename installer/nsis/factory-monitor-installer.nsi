; Production Calling System - Windows installer (NSIS 3+, Unicode)
;
; Data is always stored under:  %USERPROFILE%\production-calling-system-data
; If production-calling-system.sqlite exists there, the user is asked to recover or wipe.
; Recover: existing DB and logins are kept. Initial username/password are not written to config.
; Wipe: that folder is removed during install, then normal flow (new admin credentials on the config page).

!ifndef APP_SOURCE_DIR
  !define APP_SOURCE_DIR "stage\ProductionCallingSystem"
!endif

Unicode true
SetCompressor /SOLID lzma
RequestExecutionLevel admin
Name "Production Calling System"
OutFile "..\..\target\production-calling-system-installer.exe"
InstallDir "$PROGRAMFILES64\Production Calling System"
InstallDirRegKey HKLM "Software\ProductionCallingSystem" "InstallDir"

!include "MUI2.nsh"
!include "nsDialogs.nsh"
!include "LogicLib.nsh"
!include "FileFunc.nsh"
!include "StrFunc.nsh"
${StrRep}

!insertmacro GetParameters
!insertmacro GetOptions

!define PRODUCT_VERSION "0.0.1"
!define MUI_ABORTWARNING
; App branding (same as jpackage --icon); path is relative to this .nsi file (installer\nsis\)
!define MUI_ICON "..\windows\app-icon.ico"
!define MUI_UNICON "..\windows\app-icon.ico"

!define MUI_WELCOMEPAGE_TITLE "Welcome to Production Calling System Setup"
!define MUI_WELCOMEPAGE_TEXT "This wizard installs Production Calling System: workstation dashboard, Modbus, and event log.$\r$\n$\r$\nYour data folder is always:$\r$\n%USERPROFILE%\production-calling-system-data$\r$\n$\r$\nIf that folder already has a database, you can keep it or start over.$\r$\n$\r$\nClick Next to continue."

!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_DIRECTORY
Page custom PageConfig PageConfigLeave
Page custom PageAlert PageAlertLeave
Page custom PageModbus PageModbusLeave
Page custom PagePorts PagePortsLeave
!insertmacro MUI_PAGE_INSTFILES
!define MUI_FINISHPAGE_RUN "$INSTDIR\ProductionCallingSystem.exe"
!define MUI_FINISHPAGE_RUN_TEXT "Launch Production Calling System"
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
Var InitialUser
Var InitialPass
Var AlertRepeatMin
Var AlertMaxRepeats
Var ModbusSlaveId
Var ModbusBaud
Var ModbusStopBits
Var ModbusPollMs
Var PortManual
Var HEditPort
Var HEditUser
Var HEditPass
Var HEditRepeat
Var HEditMaxRep
Var HEditSlave
Var HEditBaud
Var HEditStop
Var HEditPoll
Var HLabelRecovery
Var HLabelUser
Var HLabelPass
Var LogFile
Var DeleteRecoveryOnInstall
Var UseExistingDataLock
Var RecoveryPromptDone

Function finishOpenDataDir
  ExecShell "open" "explorer.exe" "$DataDir"
FunctionEnd

Function .onInit
  StrCpy $DataDir "$PROFILE\production-calling-system-data"
  StrCpy $RecoverMode "0"
  StrCpy $InitialUser "admin"
  StrCpy $InitialPass "admin@123"
  StrCpy $AlertRepeatMin "15"
  StrCpy $AlertMaxRepeats "4"
  StrCpy $ModbusSlaveId "1"
  StrCpy $ModbusBaud "9600"
  StrCpy $ModbusStopBits "1"
  StrCpy $ModbusPollMs "5000"
  StrCpy $PortManual "COM1"
  StrCpy $DeleteRecoveryOnInstall "0"
  StrCpy $UseExistingDataLock "0"
  StrCpy $RecoveryPromptDone "0"

  ${If} ${Silent}
    Call SilentFixedPathDefaults
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

; Silent: if SQLite exists at fixed path, recover (no credential lines in config). Else fresh defaults.
Function SilentFixedPathDefaults
  IfFileExists "$PROFILE\production-calling-system-data\production-calling-system.sqlite" 0 silent_fresh
    StrCpy $UseExistingDataLock "1"
    StrCpy $DeleteRecoveryOnInstall "0"
    StrCpy $RecoverMode "1"
    Return
  silent_fresh:
    StrCpy $UseExistingDataLock "0"
    StrCpy $DeleteRecoveryOnInstall "0"
    StrCpy $RecoverMode "0"
FunctionEnd

Function PageConfig
  !insertmacro MUI_HEADER_TEXT "Account" "Data folder is fixed under your profile. Set the admin login (next screen: dashboard alert timing)."

  StrCpy $DataDir "$PROFILE\production-calling-system-data"

  ${IfNot} ${Silent}
    ${If} $RecoveryPromptDone != "1"
      StrCpy $RecoveryPromptDone "1"
      IfFileExists "$DataDir\production-calling-system.sqlite" 0 prompt_done
        MessageBox MB_YESNO|MB_ICONQUESTION "Existing Production Calling System data was found at:$\r$\n$DataDir$\r$\n$\r$\nYes: Keep your database and logins. You will not set a new admin password.$\r$\n$\r$\nNo: Delete that folder and create a new database. You will set a new admin user and password next." IDYES recover_yes
        StrCpy $UseExistingDataLock "0"
        StrCpy $DeleteRecoveryOnInstall "1"
        StrCpy $RecoverMode "0"
        MessageBox MB_OK|MB_ICONEXCLAMATION "The folder will be removed during install:$\r$\n$DataDir$\r$\n$\r$\nOn the next screen, enter a new admin username and password."
        Goto prompt_done
        recover_yes:
        StrCpy $UseExistingDataLock "1"
        StrCpy $DeleteRecoveryOnInstall "0"
        StrCpy $RecoverMode "1"
      prompt_done:
    ${EndIf}
  ${EndIf}

  nsDialogs::Create 1018
  Pop $0

  ${NSD_CreateLabel} 0 0 100% 24u "Data folder (cannot be changed):$\r$\n$DataDir"
  ${NSD_CreateHLine} 0 26u 100% 1u ""

  ${NSD_CreateLabel} 0 28u 100% 28u ""
  Pop $HLabelRecovery
  ${If} $UseExistingDataLock == "1"
    ${NSD_SetText} $HLabelRecovery "Using your existing database. Admin user and password are not changed. The fields below are turned off."
  ${Else}
    ${If} $DeleteRecoveryOnInstall == "1"
      ${NSD_SetText} $HLabelRecovery "Old data will be removed. Enter the first admin account for the new database."
    ${Else}
      ${NSD_SetText} $HLabelRecovery "New install: enter the first admin account for the database."
    ${EndIf}
  ${EndIf}

  ${NSD_CreateHLine} 0 58u 100% 1u ""

  ${NSD_CreateLabel} 0 60u 100% 10u "Initial admin username"
  Pop $HLabelUser
  ${NSD_CreateText} 0 72u 100% 12u "$InitialUser"
  Pop $HEditUser
  ${NSD_CreateLabel} 0 86u 100% 10u "Initial admin password"
  Pop $HLabelPass
  ${NSD_CreateText} 0 98u 100% 12u "$InitialPass"
  Pop $HEditPass

  ${If} $UseExistingDataLock == "1"
    ShowWindow $HLabelUser 0
    ShowWindow $HEditUser 0
    ShowWindow $HLabelPass 0
    ShowWindow $HEditPass 0
  ${EndIf}

  nsDialogs::Show
FunctionEnd

Function PageAlert
  !insertmacro MUI_HEADER_TEXT "Dashboard alerts" "How often alert sounds repeat while a workstation input stays on."

  nsDialogs::Create 1018
  Pop $0

  ${NSD_CreateLabel} 0 0 100% 44u "While an alert stays on, the dashboard can replay the workstation sound.$\r$\n$\r$\nSet how many minutes to wait between each replay, and how many extra replays are allowed after the first play (use 0 for no extra repeats)."
  ${NSD_CreateLabel} 0 50u 100% 14u "Minutes between replays"
  ${NSD_CreateText} 0 66u 100% 14u "$AlertRepeatMin"
  Pop $HEditRepeat
  ${NSD_CreateLabel} 0 86u 100% 22u "Maximum extra repeats$\r$\n(0 = only the first play; higher = more replays while the input stays on)"
  ${NSD_CreateText} 0 112u 100% 14u "$AlertMaxRepeats"
  Pop $HEditMaxRep

  nsDialogs::Show
FunctionEnd

Function PageModbus
  !insertmacro MUI_HEADER_TEXT "Modbus device" "Input module ID and serial timing. You will pick the COM port on the next screen."

  nsDialogs::Create 1018
  Pop $0

  ${NSD_CreateLabel} 0 0 100% 18u "These values are saved to the app config file."
  ${NSD_CreateLabel} 0 22u 100% 10u "Input slave ID"
  ${NSD_CreateText} 0 34u 100% 12u "$ModbusSlaveId"
  Pop $HEditSlave
  ${NSD_CreateLabel} 0 50u 100% 10u "Baud rate"
  ${NSD_CreateText} 0 62u 100% 12u "$ModbusBaud"
  Pop $HEditBaud
  ${NSD_CreateLabel} 0 78u 100% 10u "Stop bits"
  ${NSD_CreateText} 0 90u 100% 12u "$ModbusStopBits"
  Pop $HEditStop
  ${NSD_CreateLabel} 0 106u 100% 10u "Poll interval (ms)"
  ${NSD_CreateText} 0 118u 100% 12u "$ModbusPollMs"
  Pop $HEditPoll

  nsDialogs::Show
FunctionEnd

Function PageConfigLeave
  ${IfNot} ${Silent}
    ${If} $UseExistingDataLock != "1"
      ${NSD_GetText} $HEditUser $InitialUser
      ${NSD_GetText} $HEditPass $InitialPass
    ${EndIf}
  ${EndIf}

  StrCpy $DataDir "$PROFILE\production-calling-system-data"

  ${If} $UseExistingDataLock != "1"
    ${If} $InitialUser == ""
      MessageBox MB_ICONEXCLAMATION "Enter an initial admin username."
      Abort
    ${EndIf}
    ${If} $InitialPass == ""
      MessageBox MB_ICONEXCLAMATION "Enter an initial admin password."
      Abort
    ${EndIf}
  ${EndIf}
FunctionEnd

Function PageAlertLeave
  ${IfNot} ${Silent}
    ${NSD_GetText} $HEditRepeat $AlertRepeatMin
    ${NSD_GetText} $HEditMaxRep $AlertMaxRepeats
  ${EndIf}

  Call ValidateAlertInts
  Pop $0
  StrCmp $0 ok +3
    MessageBox MB_ICONEXCLAMATION "Enter valid positive numbers for repeat interval and max repeats."
    Abort
FunctionEnd

Function PageModbusLeave
  ${IfNot} ${Silent}
    ${NSD_GetText} $HEditSlave $ModbusSlaveId
    ${NSD_GetText} $HEditBaud $ModbusBaud
    ${NSD_GetText} $HEditStop $ModbusStopBits
    ${NSD_GetText} $HEditPoll $ModbusPollMs
  ${EndIf}

  Call ValidateModbusInts
  Pop $0
  StrCmp $0 ok +3
    MessageBox MB_ICONEXCLAMATION "Enter valid positive numbers for Modbus slave, baud, stop bits, and poll interval."
    Abort
FunctionEnd

Function ValidateAlertInts
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
  Push "ok"
FunctionEnd

Function ValidateModbusInts
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
  !insertmacro MUI_HEADER_TEXT "Serial port" "Choose the COM port for Modbus. Use Detect or type the port name."

  nsDialogs::Create 1018
  Pop $0

  ${NSD_CreateLabel} 0 0 100% 36u "Modbus uses this COM port name in the config file.$\r$\nClick Detect to fill the first port Windows reports, or type a name such as COM1 or COM3."
  ${NSD_CreateLabel} 0 42u 100% 10u "Port name"
  ${NSD_CreateText} 0 54u 100% 12u "$PortManual"
  Pop $HEditPort
  ${NSD_CreateButton} 0 74u 100% 14u "Detect serial ports"
  Pop $R9
  ${NSD_OnClick} $R9 OnDetectPorts

  nsDialogs::Show
FunctionEnd

Function OnDetectPorts
  nsExec::ExecToStack 'powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "([System.IO.Ports.SerialPort]::getportnames()) | Select-Object -First 1"'
  Pop $0
  Pop $1
  ${If} $1 != ""
    ${NSD_SetText} $HEditPort $1
  ${Else}
    MessageBox MB_OK "Windows did not report a serial port. Type the port name yourself, for example COM1."
  ${EndIf}
FunctionEnd

Function PagePortsLeave
  ${NSD_GetText} $HEditPort $0
  StrCpy $PortManual $0
  ${If} $PortManual == ""
    MessageBox MB_ICONEXCLAMATION "Enter a serial port name, for example COM3."
    Abort
  ${EndIf}
FunctionEnd

Function WriteAppConfig
  ; Spring Boot .properties: backslashes in paths can act like escapes. Use forward slashes.
  ${StrRep} $R9 $DataDir "\" "/"
  CreateDirectory "$INSTDIR\config"
  FileOpen $0 "$INSTDIR\config\application.properties" w
  FileWrite $0 "# Generated by Production Calling System installer$\r$\n"
  FileWrite $0 "system.data-dir=$R9$\r$\n"
  FileWrite $0 "logging.pattern.console=$\r$\n"
  ${If} $UseExistingDataLock != "1"
    FileWrite $0 "system.security.initial-username=$InitialUser$\r$\n"
    FileWrite $0 "system.security.initial-password=$InitialPass$\r$\n"
    FileWrite $0 "system.desktop.maintenance-gate-username=$InitialUser$\r$\n"
    FileWrite $0 "system.desktop.maintenance-gate-password=$InitialPass$\r$\n"
  ${EndIf}
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
  FileWrite $LogFile "INSTDIR=$INSTDIR$\r$\n"
  FileWrite $LogFile "DataDir=$DataDir$\r$\n"
  FileWrite $LogFile "RecoverMode=$RecoverMode$\r$\n"
  FileWrite $LogFile "UseExistingDataLock=$UseExistingDataLock$\r$\n"
  FileWrite $LogFile "DeleteRecoveryOnInstall=$DeleteRecoveryOnInstall$\r$\n"
  FileWrite $LogFile "Port=$PortManual$\r$\n"
  FileClose $LogFile
FunctionEnd

Section "Application" SecApp
  SetOutPath "$INSTDIR"
  FileOpen $LogFile "$INSTDIR\install.log" w
  FileWrite $LogFile "Production Calling System installer log$\r$\n"
  FileClose $LogFile

  StrCpy $DataDir "$PROFILE\production-calling-system-data"
  ${If} $DeleteRecoveryOnInstall == "1"
    RMDir /r "$DataDir"
  ${EndIf}

  File /r "${APP_SOURCE_DIR}\*.*"

  Call WriteAppConfig

  SetOutPath "$INSTDIR"
  WriteRegStr HKLM "Software\ProductionCallingSystem" "InstallDir" "$INSTDIR"
  WriteRegStr HKLM "Software\ProductionCallingSystem" "DataDir" "$DataDir"

  Call WriteInstallLog

  WriteUninstaller "$INSTDIR\Uninstall.exe"
  WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\ProductionCallingSystem" "DisplayName" "Production Calling System"
  WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\ProductionCallingSystem" "DisplayVersion" "${PRODUCT_VERSION}"
  WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\ProductionCallingSystem" "Publisher" "Production Calling System"
  WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\ProductionCallingSystem" "UninstallString" "$INSTDIR\Uninstall.exe"
  WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\ProductionCallingSystem" "DisplayIcon" "$INSTDIR\app-icon.ico"
  WriteRegDWORD HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\ProductionCallingSystem" "NoModify" 1
  WriteRegDWORD HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\ProductionCallingSystem" "NoRepair" 1
SectionEnd

Section "Shortcuts" SecShortcuts
  CreateDirectory "$SMPROGRAMS\Production Calling System"
  ; Icon path must be an .exe or .ico file (was wrongly $INSTDIR folder, which yields blank icons).
  CreateShortCut "$SMPROGRAMS\Production Calling System\Production Calling System.lnk" "$INSTDIR\ProductionCallingSystem.exe" "" "$INSTDIR\app-icon.ico" 0 SW_SHOWNORMAL "" "Production Calling System"
  CreateShortCut "$SMPROGRAMS\Production Calling System\Uninstall Production Calling System.lnk" "$INSTDIR\Uninstall.exe" "" "$INSTDIR\app-icon.ico" 0 SW_SHOWNORMAL
  CreateShortCut "$DESKTOP\Production Calling System.lnk" "$INSTDIR\ProductionCallingSystem.exe" "" "$INSTDIR\app-icon.ico" 0 SW_SHOWNORMAL "" "Production Calling System"
SectionEnd

Section Uninstall
  Delete "$INSTDIR\install.log"
  Delete "$INSTDIR\Uninstall.exe"
  RMDir /r "$INSTDIR"
  Delete "$SMPROGRAMS\Production Calling System\Production Calling System.lnk"
  Delete "$SMPROGRAMS\Production Calling System\Uninstall Production Calling System.lnk"
  RMDir "$SMPROGRAMS\Production Calling System"
  Delete "$DESKTOP\Production Calling System.lnk"
  DeleteRegKey HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\ProductionCallingSystem"
  DeleteRegKey HKLM "Software\ProductionCallingSystem"
SectionEnd

!insertmacro MUI_FUNCTION_DESCRIPTION_BEGIN
  !insertmacro MUI_DESCRIPTION_TEXT ${SecApp} "Application files and bundled Java runtime."
  !insertmacro MUI_DESCRIPTION_TEXT ${SecShortcuts} "Start Menu and Desktop shortcuts."
!insertmacro MUI_FUNCTION_DESCRIPTION_END

Function un.onInit
  MessageBox MB_YESNO|MB_ICONQUESTION "Remove Production Calling System from this computer?" IDYES +2
  Abort
FunctionEnd
