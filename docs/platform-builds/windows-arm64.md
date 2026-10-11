# Build: Windows ARM64 (`.msi`)

**Status:** verified on a Windows 11 ARM64 VM (Parallels): the MSI builds, installs and runs
real network tests. Save/History (Room/SQLite) does not work on this target — see the known
limitation below.

The result is a **mixed-architecture** package: the JVM and `nativeformat.dll` are native ARM64, but the bundled `iperf3.exe` and `cygwin1.dll` are x86_64 (Cygwin has no ARM64 port) and run under Prism emulation. Whether emulation affects measured throughput is untested. Native `iperf3.exe` plan: [WINDOWS_ARM64_IPERF3_PLAN.md](../desktop/WINDOWS_ARM64_IPERF3_PLAN.md).

## Prerequisites
Same as [windows-x86_64.md](windows-x86_64.md), with these differences:
- **JDK 21, ARM64 build.** The repo's `gradle/gradle-daemon-jvm.properties` asks for JDK 21 vendor Amazon and has no Windows ARM64 download entry. Install `winget install Microsoft.OpenJDK.21 --architecture arm64`, set `JAVA_HOME`, and confirm `java -XshowSettings:properties -version 2>&1 | findstr os.arch` prints `aarch64`. Delete the `toolchainVendor=AMAZON` line locally (do not commit). `gradlew.bat --version` should then say "Compatible with Java 21, any vendor".
- **Do not** install an x64 JDK (e.g. winget's Corretto): Gradle would accept it and run the whole build emulated, producing x64 output.
- **LLVM: ARM64 build.** `clang --version` should show `Target: aarch64-pc-windows-msvc`.
- **Visual Studio Build Tools with the ARM64 build tools** (`Microsoft.VisualStudio.Component.VC.Tools.ARM64`).
- **Cygwin: the x86_64 installer** (runs under Prism), at `C:\cygwin64`, with `gcc-core` and `make`.
- WiX Toolset, as on x86_64.

## Build
```shell
gradlew.bat :desktopApp:run
gradlew.bat :desktopApp:packageMsi          # desktopApp\build\compose\binaries\main\msi\iperf3KMP-1.0.0.msi
```
The exe is staged under `appResources\windows-arm64\`; the Compose plugin chooses that folder name from the host architecture.

## Verify
- Install the MSI and run a test.
- `iperf3.exe` and `cygwin1.dll` are in `<install dir>\app\resources\`; `iperf3.exe --version` prints `CYGWIN_NT-10.0-...-ARM64` (the OS, not the exe's architecture).

## Gotchas
- Kotlin/Native prints "host platform not supported" on windows-aarch64: harmless (iOS targets are ignored).
- Same CRLF, `cleanIperf3Configure`, and configuration-cache notes as x86_64.
- The x64 MSI also runs on Windows ARM64 under Prism (confirmed), if a native-JVM build isn't needed.

## Known limitation: SQLite (Save/History) does not work on Windows ARM64

**Status: accepted gap, documented 2026-10-09.** Everything else works on this target (build,
install, run, real network tests); only Room/SQLite persistence fails.

**Symptom** (`:desktopApp:run`, trimmed):
```
Caused by: java.lang.IllegalStateException: Cannot find a suitable SQLite binary for windows 11 | aarch64. Please file a bug at https://issuetracker.google.com/issues/new?component=460784
        at androidx.sqlite.driver.bundled.NativeLibraryLoader.loadLibrary(NativeLibraryLoader.jvm.kt:104)
        at androidx.sqlite.driver.bundled.BundledSQLiteDriver$NativeLibraryObject.<clinit>(BundledSQLiteDriver.jvmAndAndroid.kt:129)
```
(The `[SKIKO] Failed to create DirectX12 device` warning right before it is unrelated — Skiko
falls back to a software renderer and the app keeps running; only the SQLite open fails.)

**Root cause — upstream, not this project's code.** `jvmMain` uses `androidx.sqlite:sqlite-bundled`
(`BundledSQLiteDriver`, see the `CLAUDE.md` Room section for why that driver was chosen for JVM).
That driver ships prebuilt native SQLite binaries for a fixed set of platforms, and as of its
`2.7.0-alpha02` release notes (the latest checked, 2026-10-09), Windows ARM64 isn't one of them —
only `linuxArm64` was added for JVM/Desktop (`2.5.0-alpha07`, August 2024). The alternative JVM
driver, `org.xerial:sqlite-jdbc`, has the same gap: its own supported-platforms table lists
Windows only for x86, x86_64, armv5 and armv6 — `arm64` is Linux and Android only. So on a real
Windows ARM64 JVM there is currently no native SQLite binary either library can load — this isn't
fixable from this project's build scripts.

**Options considered, not done:**
1. Fall back to the x64 build on Windows ARM64 (already confirmed to run there under Prism — see
   the Gotchas above), which has working SQLite since it's x86_64. Drops the native-ARM64-JVM
   benefit entirely on this one OS.
2. Build a custom native SQLite driver for Windows ARM64: compile `sqlite3.c` with the ARM64
   toolchain and write a JNI shim behind `androidx.sqlite`'s `SQLiteDriver`/`SQLiteConnection`
   interfaces, the same pattern already used for printf formatting and the iperf3 bridge (see
   `IPERF3_NATIVE_INTEGRATION.md`). Real engineering work, not attempted.

**Tracking:** [issuetracker.google.com/issues/426464784](https://issuetracker.google.com/issues/426464784)
("[KMP] IllegalStateException: Cannot find a suitable SQLite binary for windows 11 | aarch64") —
open, unassigned, P3 as of 2026-10-09; commented with this project's repro details.

Re-check this when `androidx.sqlite` or `sqlite-jdbc` cuts a new release, or when the tracking
issue above gets a status change — search their release notes for "Windows" + "arm64"/"aarch64".
