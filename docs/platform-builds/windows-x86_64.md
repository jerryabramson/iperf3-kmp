# Build: Windows x86_64 (`.msi`)

**Status:** verified (MSI installed and run on a clean x86_64 machine with no separate `iperf3.exe`).

Windows does not use the in-process JNI iperf3 path. The app spawns a bundled `iperf3.exe` (built from `third_party/iperf` with Cygwin) as a subprocess. Background: [WINDOWS_DECISION_REVERSAL_CASE_STUDY.md](../desktop/WINDOWS_DECISION_REVERSAL_CASE_STUDY.md).

## Prerequisites
- Common setup: [PLATFORM_BUILD_NOTES.md](../../PLATFORM_BUILD_NOTES.md).
- **Cygwin (x86_64)** at exactly `C:\cygwin64`, with `gcc-core` and `make`. Builds `iperf3.exe`. The build stops with a clear error if `C:\cygwin64\bin\bash.exe` is missing. It never falls back to `bash` on PATH, which may be WSL's.
- **LLVM clang**: `winget install LLVM.LLVM` (expected at `C:\Program Files\LLVM\bin\clang.exe`). Builds the JNI printf helper `nativeformat.dll`.
- **Visual Studio Build Tools**, "Desktop development with C++" workload. `clang` takes its C headers and libraries from it (missing: `'stdio.h' file not found`).
- **WiX Toolset**, required by `jpackage` to emit an `.msi`.
- Git line endings: the `third_party/iperf` submodule must have LF endings (see Gotchas).

## Build
```shell
gradlew.bat :desktopApp:run                 # dev run (stages iperf3.exe first)
gradlew.bat :desktopApp:packageMsi          # desktopApp\build\compose\binaries\main\msi\iperf3KMP-1.0.0.msi
```

## Verify
Install the MSI on a machine without any iperf3 and run a test. `iperf3.exe` and `cygwin1.dll` should be in `<install dir>\app\resources\`. Or admin-extract without installing: `msiexec /a <msi> /qn TARGETDIR=C:\x`.

## Gotchas
- **CRLF:** Git with `core.autocrlf=true` converts the submodule's shell scripts to CRLF, and `./configure` fails with `$'\r': command not found`. The build detects this and prints the fix: `git -C third_party/iperf config core.autocrlf false`, then `git -C third_party/iperf rm -rf --cached -q .` and `git -C third_party/iperf reset --hard -q`.
- After switching a checkout between OS/arch, run `gradlew cleanIperf3Configure` as its own invocation (not combined with the build in one command line).
- Cygwin's own `gcc` is used for `iperf3.exe`; Cygwin's bundled `clang` fails against Cygwin's newer headers.
- `Configuration cache entry discarded ... cannot serialize Gradle script object references` for `compileIperf3ExeWindows` is expected and harmless (that task is marked cache-incompatible).
- Stop uses a hard `Process.destroy()` (TerminateProcess), not a graceful stop.
