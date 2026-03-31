; Factory Monitor — Windows installer (NSIS 3+, Unicode)
;
; Data is always stored under:  %USERPROFILE%\factory-monitor-data
; If factory-monitor.sqlite exists there, the user is asked to recover or wipe.
; Recover: existing DB and logins are kept — initial username/password are not written to config.
; Wipe: that folder is removed during install, then normal flow (new admin credentials on the config page).

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
!include "StrFunc.nsh"
${StrRep}

!insertmacro GetParameters
!insertmacro GetOptions

!define PRODUCT_VERSION "0.0.1"
!define MUI_ABORTWARNING
!define MUI_ICON "${NSISDIR}\Contrib\Graphics\Icons\modern-install.ico"
!define MUI_UNICON "${NSISDIR}\Contrib\Graphics\Icons\modern-uninstall.ico"

!define MUI_WELCOMEPAGE_TITLE "Welcome to the Factory Monitor Setup Wizard"
!define MUI_WELCOMEPAGE_TEXT "This will install Factory Monitor (workstation dashboard, Modbus, event log).$\r$\n$\r$\nApplication data is always stored in:$\r$\n%USERPROFILE%\factory-monitor-data$\r$\n$\r$\nIf a previous database is found there, you can recover it or start fresh.$\r$\n$\r$\nClick Next to continue."

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
  StrCpy $DataDir "$PROFILE\factory-monitor-data"
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
  IfFileExists "$PROFILE\factory-monitor-data\factory-monitor.sqlite" 0 silent_fresh
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
  StrCpy $DataDir "$PROFILE\factory-monitor-data"

  ${IfNot} ${Silent}
    ${If} $RecoveryPromptDone != "1"
      StrCpy $RecoveryPromptDone "1"
      IfFileExists "$DataDir\factory-monitor.sqlite" 0 prompt_done
        MessageBox MB_YESNO|MB_ICONQUESTION "Existing Factory Monitor data was found at:$\r$\n$DataDir$\r$\n$\r$\nYes — Recover: keep your database and existing logins (you will not be asked for a new admin password).$\r$\n$\r$\nNo — Start fresh: delete that folder and create a new empty database (you will set a new admin user and password next)." IDYES recover_yes
        StrCpy $UseExistingDataLock "0"
        StrCpy $DeleteRecoveryOnInstall "1"
        StrCpy $RecoverMode "0"
        MessageBox MB_OK|MB_ICONEXCLAMATION "The folder will be permanently removed during install:$\r$\n$DataDir$\r$\n$\r$\nAfter installation, set a new admin username and password on this page."
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

  ${NSD_CreateLabel} 0 0 100% 28u "Data location (fixed — cannot be changed):$\r$\n$DataDir"
  ${NSD_CreateHLine} 0 32u 100% 1u ""

  ${NSD_CreateLabel} 0 40u 100% 48u ""
  Pop $HLabelRecovery
  ${If} $UseExistingDataLock == "1"
    ${NSD_SetText} $HLabelRecovery "Recovering existing database. Admin username and password in the database are unchanged — do not set new credentials below (fields are disabled)."
  ${Else}
    ${If} $DeleteRecoveryOnInstall == "1"
      ${NSD_SetText} $HLabelRecovery "Previous data will be removed. Enter a new initial admin account for the new database."
    ${Else}
      ${NSD_SetText} $HLabelRecovery "Fresh install: enter the initial admin account for the new database."
    ${EndIf}
  ${EndIf}

  ${NSD_CreateHLine} 0 96u 100% 1u ""

  ${NSD_CreateLabel} 0 104u 48% 10u "Initial admin username"
  Pop $HLabelUser
  ${NSD_CreateText} 52% 102u 48% 12u "$InitialUser"
  Pop $HEditUser
  ${NSD_CreateLabel} 0 120u 48% 10u "Initial admin password"
  Pop $HLabelPass
  ${NSD_CreateText} 52% 118u 48% 12u "$InitialPass"
  Pop $HEditPass

  ${If} $UseExistingDataLock == "1"
    ShowWindow $HLabelUser 0
    ShowWindow $HEditUser 0
    ShowWindow $HLabelPass 0
    ShowWindow $HEditPass 0
  ${EndIf}

  ${NSD_CreateLabel} 0 136u 48% 10u "Dashboard alert repeat (minutes)"
  ${NSD_CreateText} 52% 134u 48% 12u "$AlertRepeatMin"
  Pop $HEditRepeat
  ${NSD_CreateLabel} 0 152u 48% 10u "Dashboard alert max repeats"
  ${NSD_CreateText} 52% 150u 48% 12u "$AlertMaxRepeats"
  Pop $HEditMaxRep

  ${NSD_CreateHLine} 0 168u 100% 1u ""
  ${NSD_CreateLabel} 0 176u 100% 12u "Modbus RTU (serial port on next page)"
  ${NSD_CreateLabel} 0 192u 48% 10u "Input slave ID"
  ${NSD_CreateText} 52% 190u 48% 12u "$ModbusSlaveId"
  Pop $HEditSlave
  ${NSD_CreateLabel} 0 208u 48% 10u "Baud rate"
  ${NSD_CreateText} 52% 206u 48% 12u "$ModbusBaud"
  Pop $HEditBaud
  ${NSD_CreateLabel} 0 224u 48% 10u "Stop bits"
  ${NSD_CreateText} 52% 222u 48% 12u "$ModbusStopBits"
  Pop $HEditStop
  ${NSD_CreateLabel} 0 240u 48% 10u "Poll interval (ms)"
  ${NSD_CreateText} 52% 238u 48% 12u "$ModbusPollMs"
  Pop $HEditPoll

  nsDialogs::Show
FunctionEnd

Function PageConfigLeave
  ${IfNot} ${Silent}
    ${If} $UseExistingDataLock != "1"
      ${NSD_GetText} $HEditUser $InitialUser
      ${NSD_GetText} $HEditPass $InitialPass
    ${EndIf}
    ${NSD_GetText} $HEditRepeat $AlertRepeatMin
    ${NSD_GetText} $HEditMaxRep $AlertMaxRepeats
    ${NSD_GetText} $HEditSlave $ModbusSlaveId
    ${NSD_GetText} $HEditBaud $ModbusBaud
    ${NSD_GetText} $HEditStop $ModbusStopBits
    ${NSD_GetText} $HEditPoll $ModbusPollMs
  ${EndIf}

  StrCpy $DataDir "$PROFILE\factory-monitor-data"

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
  ${NSD_CreateText} 0 52u 100% 12u "$PortManual"
  Pop $HEditPort
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
    ${NSD_SetText} $HEditPort $1
  ${Else}
    MessageBox MB_OK "No serial ports reported by Windows. Enter the port manually (e.g. COM1)."
  ${EndIf}
FunctionEnd

Function PagePortsLeave
  ${NSD_GetText} $HEditPort $0
  StrCpy $PortManual $0
  ${If} $PortManual == ""
    MessageBox MB_ICONEXCLAMATION "Enter a serial port name (e.g. COM3)."
    Abort
  ${EndIf}
FunctionEnd

Function WriteAppConfig
  ; Spring Boot .properties: backslashes like \f in \factory-... are escapes — use forward slashes.
  ${StrRep} $R9 $DataDir "\" "/"
  CreateDirectory "$INSTDIR\config"
  FileOpen $0 "$INSTDIR\config\application.properties" w
  FileWrite $0 "# Generated by Factory Monitor installer$\r$\n"
  FileWrite $0 "system.data-dir=$R9$\r$\n"
  FileWrite $0 "logging.pattern.console=$\r$\n"
  ${If} $UseExistingDataLock != "1"
    FileWrite $0 "system.security.initial-username=$InitialUser$\r$\n"
    FileWrite $0 "system.security.initial-password=$InitialPass$\r$\n"
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
  FileWrite $LogFile "Factory Monitor installer log$\r$\n"
  FileClose $LogFile

  StrCpy $DataDir "$PROFILE\factory-monitor-data"
  ${If} $DeleteRecoveryOnInstall == "1"
    RMDir /r "$DataDir"
  ${EndIf}

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
