# Build: Windows ARM64 (`.msi`)

**Status:** verified on a Windows 11 ARM64 VM (Parallels): the MSI builds, installs and runs
real network tests. `:desktopApp:run` also verified on real hardware (NVIDIA RTX Spark N1X
laptop, 2026-10-24), from both the terminal and IntelliJ (see "IntelliJ IDEA" below).
Save/History (Room/SQLite) is disabled on this target — see the known limitation below.

**Released builds (from 1.0.2):** no ARM64 `.msi` is published. Windows on Arm users install the
x86_64 `.msi` from GitHub releases (see [windows-x86_64.md](windows-x86_64.md) and README.md),
which runs under Prism and keeps Save/History. Building the ARM64 `.msi` below is for development.
In-process JNI on both architectures, without Cygwin, is planned for desktop 1.0.3
(`WINDOWS_MSVC_IPERF3_PLAN.md` §8a, branch `testing_native_windows_builds`).

The result is a **mixed-architecture** package: the JVM and `nativeformat.dll` are native ARM64, but the bundled `iperf3.exe` and `cygwin1.dll` are x86_64 (Cygwin has no ARM64 port) and run under Prism emulation. Whether emulation affects measured throughput is untested. Native `iperf3.exe` plan: [WINDOWS_ARM64_IPERF3_PLAN.md](../desktop/WINDOWS_ARM64_IPERF3_PLAN.md).

## Prerequisites
Same as [windows-x86_64.md](windows-x86_64.md), with these differences:
- **An ARM64 JDK on `JAVA_HOME`** to run the Gradle launcher, e.g. `winget install Microsoft.OpenJDK.21 --architecture arm64`. Confirm `java -XshowSettings:properties -version 2>&1 | findstr os.arch` prints `aarch64`. The launcher's architecture decides which JDK Gradle provisions for the build itself: `gradle/gradle-daemon-jvm.properties` (JetBrains 25) has a Windows ARM64 download, fetched automatically. No local edit of that file is needed any more (verified 2026-10-18; older notes said to delete a `toolchainVendor=AMAZON` line).
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

## IntelliJ IDEA
Gradle-type run configurations (e.g. `desktopApp [run]`) fail before Gradle even starts with:
```
Unknown host target: windows aarch64
```
This comes from IntelliJ itself, not the build: the bundled Kotlin plugin's native-debugger
hook (`com.jetbrains.mpp.debugger.MPPDebugExecutionAware`) runs before every Gradle task and asks
Kotlin/Native for the host platform, which has no Windows ARM64 support. The same tasks succeed
from a terminal. The full stack trace is in `%LOCALAPPDATA%\JetBrains\IntelliJIdea<version>\log\idea.log`.

**Fix (verified 2026-10-24, IntelliJ 2026.2.3):** Settings → Plugins → Installed → disable
**Native Debugging Support**. IntelliJ also disables **Kotlin Multiplatform**, which depends on
it; accept that. Restart. The hook only loads when Native Debugging Support is present. You
lose the iOS run configuration and the other Kotlin Multiplatform plugin features, none of which
work on Windows anyway. Plugin settings are per machine. The alternative is to keep the plugins
and use the shell-script run configurations (`.run/gradlew_desktopAppRun.run.xml`).

You don't need `org.gradle.java.home`. Because the repo has `gradle/gradle-daemon-jvm.properties`,
IntelliJ's Gradle settings show **Gradle JVM criteria** (25 / JetBrains) instead of a JVM
dropdown; leave those as they are. Set **Distribution** to **Wrapper**, not "Wrapper task". If an
x64 JDK is installed, IntelliJ may fall back to it for the short-lived Gradle launcher ("It is
not possible to use the currently selected Gradle JVM…"). Remove that JDK from File → Project
Structure → SDKs, or uninstall it. While the app runs, check that the Gradle daemon and the app are
on `~\.gradle\jdks\jetbrains_s_r_o_-25-aarch64-windows*`.

## Android development on Windows on ARM
Building the Android app works on this host. On 2026-10-24, `gradlew.bat :androidApp:installDebug`
built the app and installed it on a physical phone (moto g stylus 2025) over USB/adb. That SDK had
platform `android-37.0`, build-tools `37.0.0`, NDK `28.2`/`30.0` and CMake `3.22.1`.

**Use a physical device: no Android emulator runs on Windows on ARM (checked 2026-10-24).**
- **Google's Android Emulator:** no Windows ARM64 host build. The latest release notes (37.2.12,
  Oct 1 2026) don't mention Windows on ARM. ARM64 hosts are supported only on Linux and Apple
  Silicon.
- **BlueStacks:** supports Windows x86/x64 and macOS, but not Windows on ARM.
- **Community port** [bjorkepoc/android-emulator-winarm64](https://github.com/bjorkepoc/android-emulator-winarm64):
  only boots a plain ARM64 Linux kernel so far. It doesn't boot Android yet and has no release.
- **WSABuilds** (community repackaging of Microsoft's discontinued Windows Subsystem for Android)
  runs Android apps on ARM laptops but isn't a development emulator. It's unofficial.

To re-check, search the [emulator release notes](https://developer.android.com/studio/releases/emulator)
for "Windows" + "ARM64", and look at the community port's Releases page.

## Verify
- Install the MSI and run a test.
- `iperf3.exe` and `cygwin1.dll` are in `<install dir>\app\resources\`; `iperf3.exe --version` prints `CYGWIN_NT-10.0-...-ARM64` (the OS, not the exe's architecture).

## Gotchas
- Kotlin/Native prints "host platform not supported" on windows-aarch64: harmless (iOS targets are ignored).
- Same CRLF, automatic-reconfigure, and configuration-cache notes as x86_64.
- The x64 MSI also runs on Windows ARM64 under Prism (confirmed), and it's the recommended install there when Save/History is needed (see the known limitation below).

## Known limitation: SQLite (Save/History) does not work on Windows ARM64

**Status: accepted gap, documented 2026-10-09.** Everything else works on this target (build,
install, run, real network tests); only Room/SQLite persistence fails.

**Since 2026-10-24 the app handles this itself** instead of failing when it tries to open the
database. `JVMPlatform.isWindowsOnArm` (`os.name` Windows + `os.arch` aarch64) makes
`createSQLiteDriver()`/`getResultDatabase()` return `null`. It logs
`Room Database not supported on Windows on Arm!` and skips every database operation in
`Iperf3RunViewModel`. Tests run normally, results just aren't saved, and the History button
never appears. The symptom below is what happened before that change.

**Workaround (recommended when Save/History is needed): install the x86_64 `.msi` on Windows on
Arm instead of the ARM64 one.** It runs under Prism emulation, and its SQLite works because the
bundled driver has an x86_64 Windows binary. The trade-off is an emulated JVM instead of a native
one. Use the ARM64 `.msi` only when native performance matters more than saved history.

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

**Options considered:**
1. **Adopted as the user-facing workaround (above):** the x64 build on Windows ARM64 (confirmed
   to run there under Prism — see the Gotchas above), which has working SQLite since it's x86_64.
   Drops the native-ARM64-JVM benefit on this one OS.
2. Not done: build a custom native SQLite driver for Windows ARM64: compile `sqlite3.c` with the ARM64
   toolchain and write a JNI shim behind `androidx.sqlite`'s `SQLiteDriver`/`SQLiteConnection`
   interfaces, the same pattern already used for printf formatting and the iperf3 bridge (see
   `IPERF3_NATIVE_INTEGRATION.md`). Real engineering work, not attempted.

**Tracking:** [issuetracker.google.com/issues/426464784](https://issuetracker.google.com/issues/426464784)
("[KMP] IllegalStateException: Cannot find a suitable SQLite binary for windows 11 | aarch64") —
open, unassigned, P3 as of 2026-10-09; commented with this project's repro details.

Re-check this when `androidx.sqlite` or `sqlite-jdbc` cuts a new release, or when the tracking
issue above gets a status change — search their release notes for "Windows" + "arm64"/"aarch64".
