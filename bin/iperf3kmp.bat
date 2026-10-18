@echo off
rem Finds and runs the installed iperf3KMP desktop launcher (from the .msi
rem package), forwarding all arguments and its real exit code -- so with
rem the app installed via the MSI, this just works:
rem
rem     iperf3kmp.bat my-iperf3-server.example.com -t 10 -P 4
rem     iperf3kmp.bat --help
rem
rem See iperf3kmp.ps1's doc comment for why this doesn't need the
rem $Env:TERM workaround https://github.com/jerryabramson/BandWidthTester's
rem own Windows wrapper uses -- iperf3KMP's own ANSI detection already
rem treats Windows as always ANSI-capable.
rem
rem Override: set IPERF3KMP_BIN to the launcher's .exe path directly to
rem skip the search below -- useful for a non-default install location.

if defined IPERF3KMP_BIN (
    "%IPERF3KMP_BIN%" %*
    exit /b %ERRORLEVEL%
)

rem Per-user install location first: the .msi installs there since 1.0.1, so a
rem leftover per-machine 1.0.0 in Program Files must not win.
set "CANDIDATE=%LOCALAPPDATA%\iperf3KMP\iperf3KMP.exe"
if exist "%CANDIDATE%" (
    "%CANDIDATE%" %*
    exit /b %ERRORLEVEL%
)

set "CANDIDATE=%ProgramFiles%\iperf3KMP\iperf3KMP.exe"
if exist "%CANDIDATE%" (
    "%CANDIDATE%" %*
    exit /b %ERRORLEVEL%
)

set "CANDIDATE=%ProgramFiles(x86)%\iperf3KMP\iperf3KMP.exe"
if exist "%CANDIDATE%" (
    "%CANDIDATE%" %*
    exit /b %ERRORLEVEL%
)

rem Not at a known install location -- maybe it's already on PATH under its own name.
where iperf3KMP.exe >nul 2>nul
if %ERRORLEVEL%==0 (
    iperf3KMP.exe %*
    exit /b %ERRORLEVEL%
)

echo iperf3kmp: could not find the installed iperf3KMP launcher. 1>&2
echo Checked "%LOCALAPPDATA%\iperf3KMP", "%ProgramFiles%\iperf3KMP", and "%ProgramFiles(x86)%\iperf3KMP". 1>&2
echo If it's installed somewhere else, set IPERF3KMP_BIN to the .exe path and re-run. 1>&2
exit /b 1
