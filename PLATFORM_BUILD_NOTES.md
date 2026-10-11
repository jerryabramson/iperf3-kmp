# Platform build notes

One short checklist per target. Each says what was verified and lists the gotchas we actually hit.

| Target | Host you build on | Command (short) | Checklist |
|---|---|---|---|
| Android | any, with the Android SDK | `./gradlew :androidApp:assembleDebug` | [android.md](docs/platform-builds/android.md) |
| iOS | macOS Apple Silicon + Xcode | Xcode / `xcodebuild` | [ios.md](docs/platform-builds/ios.md) |
| macOS desktop (`.dmg`) | macOS Apple Silicon | `./gradlew :desktopApp:packageDmg` | [macos-arm64.md](docs/platform-builds/macos-arm64.md) |
| Linux Debian/Ubuntu (`.deb`) x86_64, aarch64 | Linux, or any host with Podman/Docker | `./gradlew :desktopApp:packageDeb` or `./packageDebianFromContainer.sh` | [linux-deb.md](docs/platform-builds/linux-deb.md) |
| Linux Fedora/RHEL (`.rpm`) x86_64, aarch64 | Linux, or any host with Podman/Docker | `./gradlew :desktopApp:packageRpm` or `./packageRpmFromContainer.sh` | [linux-rpm.md](docs/platform-builds/linux-rpm.md) |
| Windows x86_64 (`.msi`) | Windows x86_64 | `gradlew.bat :desktopApp:packageMsi` | [windows-x86_64.md](docs/platform-builds/windows-x86_64.md) |
| Windows ARM64 (`.msi`) | Windows ARM64 | `gradlew.bat :desktopApp:packageMsi` | [windows-arm64.md](docs/platform-builds/windows-arm64.md) |

Test status of every build/install combination, and the merge gate: [docs/platform-builds/TEST_MATRIX.md](docs/platform-builds/TEST_MATRIX.md).

Not supported: Intel Macs (macOS and the iOS simulator are Apple Silicon only, by policy).

## Common setup (every target)
1. **Clone with the submodule.** `git clone --recursive ...`, or `git submodule update --init --recursive third_party/iperf` (pinned to iperf 3.21). Without it, native compile tasks fail input validation.
2. **Android SDK, even for desktop-only builds.** AGP configures every subproject. Set `ANDROID_HOME` or a gitignored `local.properties` with `sdk.dir=`. Install `platforms;android-37.0` and `build-tools;37.0.0` (`android-37` alone fails).
3. **Gradle wrapper only** (`./gradlew`, `gradlew.bat`), Gradle 9.x. The JDK (21, Amazon) is auto-provisioned via foojay, except on Windows ARM64 (see its checklist).
4. **Desktop packages cannot be cross-compiled.** Each `.dmg`/`.deb`/`.rpm`/`.msi` must be built on that OS and architecture, because native libraries are compiled for the build host at build time. (Linux can use the container scripts, which build for the container's CPU.)
5. **Switching a checkout between OSes/architectures:** delete `third_party/iperf/src/iperf_config.h` and `version.h`, or run `./gradlew cleanIperf3Configure` as its own invocation, before building. `./configure` output is OS/arch-specific and is cached.
6. **Windows only: LF line endings in the submodule.** See [windows-x86_64.md](docs/platform-builds/windows-x86_64.md).

## Tests
`./gradlew :shared:jvmTest` (desktop JVM), `:shared:testAndroidHostTest`, `:shared:iosSimulatorArm64Test`.

More detail: [README.md](README.md), [CLAUDE.md](CLAUDE.md).
