# This is a Kotlin Multiplatform project

* The project targets Android, iOS, and Desktop (JVM — macOS/Linux/Windows)

**Quick recipe — clone to running app on a fresh Linux machine:**
```shell
git clone https://github.com/<your-username>/iperf3-kmp
cd iperf3-kmp/
```
* (or clone with `git clone --recursive` to get it in one step).

**The full `esnet/iperf` source tree isn't included in this repo — it's a git submodule pinned to the `3.21` tag, required for every target (Android, iOS, and desktop all link against it).**
* After cloning:
```shell
git submodule update --init --recursive
```
**Alternatively, if the application is downloaded directly from github as a release, the following commands are required:**
```shell
cd third_party
rmdir iperf
git clone https://github.com/esnet/iperf.git
```

**Run desktop app (Linux, macOS, or Windows — on Windows use `gradlew.bat`, see the Windows section below)**
```shell
./gradlew :desktopApp:run
```

## Code Layout
* [/iosApp](./iosApp/iosApp) contains an iOS application.
  - Even if you’re sharing your UI with Compose Multiplatform, you need this entry point for your iOS app.
  - This is also where you should add SwiftUI code for your project.
<br>  
* [/shared](./shared/src) is for code that will be shared across your Compose Multiplatform applications.
  - subfolders
    - [commonMain](./shared/src/commonMain/kotlin) is for code that’s common for all targets.
<br>  
       - Sub-folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.  
           - [androidMain](./shared/src/androidMain/kotlin) Android specific code.
           - [jvmMain](./shared/src/jvmMain/kotlin) Desktop (JVM) specific code.
           - [iosMain](./shared/src/iosMain/kotlin) iOS versions (Kotlin code).
              - *For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app, this folder would be the right place for such calls.*

## Building and running, per platform
Short per-platform build checklists (prerequisites, commands, gotchas) are in [PLATFORM_BUILD_NOTES.md](./PLATFORM_BUILD_NOTES.md).
### Preferable 
* Use the run configurations provided by the run widget in your IDE's toolbar, or these Gradle commands directly.
### Android
Needs the Android SDK (`sdk.dir` in `local.properties`, or `ANDROID_HOME`). To build only:
```shell
./gradlew :androidApp:assembleDebug
```
To also boot an emulator and run the app on it, entirely from the command line (`$ANDROID_HOME/emulator/emulator
-list-avds` to see available AVDs — swap `Pixel_8` for one of your own):
```shell
$ANDROID_HOME/emulator/emulator -avd Pixel_8 &                     # boots with a visible window
$ANDROID_HOME/platform-tools/adb wait-for-device
until [ "$($ANDROID_HOME/platform-tools/adb shell getprop sys.boot_completed | tr -d '\r')" = "1" ]; do sleep 2; done
./gradlew :androidApp:installDebug                                  # builds + installs to the running emulator
$ANDROID_HOME/platform-tools/adb shell am start \
  -n com.jabramson.iperf3_network_tester_kmp/.MainActivity
```

### iOS
macOS + Xcode only — build/run is managed by Xcode's own tooling, not Gradle (Gradle's Kotlin Multiplatform plugin only builds the shared Kotlin framework, not the final signed `.app`). Easiest is opening [/iosApp/iosApp.xcodeproj](./iosApp/iosApp.xcodeproj) in Xcode and running from there — but it can also be built and launched on a Simulator entirely from the command line via `xcodebuild`/`xcrun`, no Xcode UI required:
```shell
open -a Simulator                                  # opens the Simulator window first, so it's visible throughout
xcrun simctl boot "iPhone 17"                      # skip if a simulator is already booted
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator -configuration Debug \
  -derivedDataPath build/ios build
xcrun simctl install booted build/ios/Build/Products/Debug-iphonesimulator/iperf3KMP.app
xcrun simctl launch booted com.jabramson.iperf3-network-tester-kmp
```
(`simctl boot` alone starts the device headlessly with no visible window — `open -a Simulator` first is what actually
puts it on screen.)
### Desktop — macOS/Linux
Needs `clang` on `PATH` (compiles the native iperf3/printf-formatting JNI bridges at build time; see `CLAUDE.md`'s "Native iperf3 client" section):
```shell
./gradlew :desktopApp:run
./gradlew :desktopApp:hotRun --auto   # hot reload during development
```
### Desktop — Windows
_Windows is now supported._ It doesn't reuse the in-process JNI path the other desktop
targets use — instead, a bundled, Gradle-built-from-source `iperf3.exe` runs as a real
subprocess (`ProcessBuilder`). Requires:
* [Cygwin](https://cygwin.com/) installed at `C:\cygwin64` with the `gcc-core` and `make`
  packages — builds `iperf3.exe` itself (Cygwin's own `gcc`, not `clang`; Cygwin's bundled
  clang can't build against Cygwin's own newer headers).
* A native Windows LLVM `clang` (e.g. `winget install LLVM.LLVM`) — builds the JNI
  printf-formatting helper, same as macOS/Linux above. On Windows, `clang` takes its C headers
  and libraries from Visual Studio, so also install the
  [Visual Studio Build Tools](https://visualstudio.microsoft.com/downloads/) with the
  "Desktop development with C++" workload (add the ARM64 build tools on a Windows-on-Arm
  machine). Without it the build fails with `'stdio.h' file not found`.
* The [WiX Toolset](https://wixtoolset.org/) — required by `jpackage` to emit the `.msi`.
```shell
gradlew.bat :desktopApp:run
gradlew.bat :desktopApp:packageMsi
```
See [WINDOWS_DECISION_REVERSAL_CASE_STUDY.md](docs/desktop/WINDOWS_DECISION_REVERSAL_CASE_STUDY.md) for
the full story of why this was originally rejected and what changed. An MSI built on a Windows ARM64 machine has a native ARM64 JVM (built and installed on a
Parallels VM); the x64 MSI also runs there under Prism. Only `iperf3.exe` is x86_64 in both
(Cygwin has no ARM64 port); a native ARM64 `iperf3.exe` is planned but not started — see [WINDOWS_ARM64_IPERF3_PLAN.md](docs/desktop/WINDOWS_ARM64_IPERF3_PLAN.md).
### Running tests
Use the run button in your IDE's editor gutter, or run tests using Gradle tasks:

- Android tests: `./gradlew :shared:testAndroidHostTest`
- Desktop tests (macOS/Linux): `./gradlew :shared:jvmTest`
- iOS simulator tests: `./gradlew :shared:iosSimulatorArm64Test`


# Desktop packaging
| Platform | task |
|------|---------|
| macOS  | ./gradlew :desktopApp:packageDmg|
| Debian/Ubuntu Linux x86_64  | ./gradlew :desktopApp:packageDeb|
| Fedora/Red Hat Linux x86_64 * | ./gradlew :desktopApp:packageRpm|
| Fedora/Red Hat aarch64 * | ./gradlew :desktopApp:packageRpm|
| Debian/Ubuntu Linux x86_64 or aarch64 - on a non-Debian host (Fedora/RHEL, or macOS with Docker Desktop) | packageDebianFromContainer.sh |
| Fedora/RHEL x86_64 or aarch64 - on a non-RPM host (Debian/Ubuntu, or macOS with Docker Desktop) | packageRpmFromContainer.sh |
| Windows x86_64 | gradlew.bat :desktopApp:packageMsi |
| Windows ARM64 (native ARM64 JVM; bundled `iperf3.exe` is x86_64 under Prism emulation — see WINDOWS_ARM64_IPERF3_PLAN.md) | gradlew.bat :desktopApp:packageMsi |
* See Gotcha's in CLAUDE.md

Both container-based scripts auto-detect Podman or Docker (whichever is on `PATH`) and build a native, non-emulated Linux container matching the host's CPU - including on Apple Silicon or Intel Macs via Docker Desktop, since it runs a real Linux VM matching the host architecture. Verified via Podman on Fedora x86_64 and Fedora aarch64, and via Docker Desktop on macOS (Apple Silicon) producing both `iperf3kmp_1.0.0_arm64.deb` and `iperf3kmp-1.0.0-1.aarch64.rpm`.

**Gotcha when switching between a native host build and a container build on the same checkout:** both scripts now clear `third_party/iperf/src/iperf_config.h`/`version.h` before running, since those are gitignored `./configure` output that's OS/arch-specific and would otherwise leak in via the bind-mounted repo dir from a prior native build (e.g. a macOS-generated config defines `HAVE_SYS_ENDIAN_H`, which doesn't exist on Linux, breaking the container build with `'sys/endian.h' file not found`). See the "No cross-compiling" note above for the same underlying issue on native builds.
#  Access
This repository is **public**. Clone it with either protocol; you only need your own credentials (never share a token or SSH key):

* **SSH (recommended for interactive/day-to-day use):** add your own SSH public key under [github.com/settings/keys](https://github.com/settings/keys), then (replace `<your-username>` with the account that hosts this repo):
  ```shell
  git clone git@github.com:<your-username>/iperf3-kmp.git
  ```
* **HTTPS (no token needed for public reads):** clone anonymously, or use a [credential helper](https://git-scm.com/docs/gitcredentials) when you push. For CI on a private fork, generate your own fine-grained token ([github.com/settings/personal-access-tokens](https://github.com/settings/personal-access-tokens)) scoped to that fork with `Contents: Read-only`:
  ```shell
  git clone https://<your-own-token>@github.com/<your-username>/iperf3-kmp.git
  ```
* **No command line:** browse/download from github.com in a browser, or use `gh auth login` (GitHub CLI) for a browser-based login.

**Contributing:** fork the repo, branch off `main`, and open a pull request. Prefix local branch names with your own prefix so they stay distinguishable.

A token or SSH key only ever authenticates the person who generated it — don't email or share a personal access token; each contributor should generate and hold their own.

> Note: the `third_party/iperf` submodule points at the public [`esnet/iperf`](https://github.com/esnet/iperf) project and needs no authentication. Run `git submodule update --init --recursive` after cloning — every target links against it.


---
Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…
