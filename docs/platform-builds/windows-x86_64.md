# Build: Windows x86_64 (`.msi`)

**Status:** verified (MSI installed and run on a clean x86_64 machine with no separate `iperf3.exe`).

Windows does not use the in-process JNI iperf3 path. The app spawns a bundled `iperf3.exe` (built from `third_party/iperf` with Cygwin) as a subprocess. Background: [WINDOWS_DECISION_REVERSAL_CASE_STUDY.md](../desktop/WINDOWS_DECISION_REVERSAL_CASE_STUDY.md).

**Released builds (from 1.0.2):** this `.msi` is published on GitHub releases, unsigned, and is the
only Windows package (Windows on Arm uses it too). It installs per user, with no UAC prompt.
`install.ps1` downloads and installs the newest one. README.md's "Installing the Windows `.msi`" has
the SmartScreen / "Unblock" steps for browser downloads.

## Prerequisites
- Common setup: [PLATFORM_BUILD_NOTES.md](../../PLATFORM_BUILD_NOTES.md).
- **Cygwin (x86_64)** at exactly `C:\cygwin64`, with `gcc-core` and `make`. Builds `iperf3.exe`. The build stops with a clear error if `C:\cygwin64\bin\bash.exe` is missing. It never falls back to `bash` on PATH, which may be WSL's.
- **LLVM clang**: `winget install LLVM.LLVM` (expected at `C:\Program Files\LLVM\bin\clang.exe`). Builds the JNI printf helper `nativeformat.dll`.
- **Visual Studio Build Tools**, "Desktop development with C++" workload. `clang` takes its C headers and libraries from it (missing: `'stdio.h' file not found`).
- **WiX Toolset**: nothing to install. The Compose Gradle plugin downloads its own copy into the build directory the first time `packageMsi` runs.
- Git line endings: the `third_party/iperf` submodule must have LF endings (see Gotchas).

## Build
```shell
gradlew.bat :desktopApp:run                 # dev run (stages iperf3.exe first)
gradlew.bat :desktopApp:packageMsi          # desktopApp\build\compose\binaries\main\msi\iperf3KMP-1.0.0.msi
```

## Verify
Install the MSI on a machine without any iperf3 and run a test. `iperf3.exe` and `cygwin1.dll` should be in `<install dir>\app\resources\`. Or admin-extract without installing: `msiexec /a <msi> /qn TARGETDIR=C:\x`.

## Gotchas
- **Per-user install, no UAC.** `perUserInstall = true` (`desktopApp/build.gradle.kts`, since 1.0.1) installs into the user's `%LOCALAPPDATA%` instead of Program Files, so the unsigned `.msi` never asks for elevation. Before this, UAC showed "Do you want to allow this app from an unknown publisher to make changes to your device?" with **No** as the default. Each Windows user installs separately. A per-machine install from an older `.msi` (1.0.0) isn't upgraded by a per-user one, so uninstall it first (Settings → Apps) to avoid two copies.
- **Command line works from a terminal (since 1.0.1).** `console = true` makes `iperf3KMP.exe` a console program, so `iperf3KMP.exe <server> ...` prints in cmd/PowerShell, the shell waits, and exit codes are real. Before this, the launcher was a GUI program and printed nothing. A Start-menu launch briefly flashes a console window (about one JVM startup): `main()` starts a console-less copy of the app for the GUI and exits (`WindowsConsole.detachForGui()`), because Windows Terminal, the default terminal on Windows 11, keeps the tab open until the process exits, so `FreeConsole()` alone left the window up. Starting the GUI from a terminal with no arguments returns the prompt immediately. The install folder (`%LOCALAPPDATA%\iperf3KMP`) isn't on PATH, so use the full path or add it. CLI mode also switches the console to UTF-8 with ANSI processing for the run (and restores the previous code page on exit); otherwise the `─` rules print as `?` under the default code page 437. `bin/iperf3kmp.ps1`/`.bat` find the per-user install first.
- **Edge download warning (unsigned `.msi`).** Edge still says "isn't commonly downloaded" and requires ⋯ → Keep → Delete ▾ → Keep anyway. Only code signing plus download reputation removes that.
- **CRLF:** Git with `core.autocrlf=true` converts the submodule's shell scripts to CRLF, and `./configure` fails with `$'\r': command not found`. The build detects this and prints the fix: `git -C third_party/iperf config core.autocrlf false`, then `git -C third_party/iperf rm -rf --cached -q .` and `git -C third_party/iperf reset --hard -q`.
- Switching a checkout between OS/arch needs no manual step: the cached iperf3 `./configure` output is rebuilt automatically when the host OS/arch changes (marker: `build/iperf3-configure-host.txt`). `gradlew cleanIperf3Configure` (its own invocation) still forces it.
- Cygwin's own `gcc` is used for `iperf3.exe`; Cygwin's bundled `clang` fails against Cygwin's newer headers.
- `Configuration cache entry discarded ... cannot serialize Gradle script object references` for `compileIperf3ExeWindows` is expected and harmless (that task is marked cache-incompatible).
- Stop uses a hard `Process.destroy()` (TerminateProcess), not a graceful stop.
