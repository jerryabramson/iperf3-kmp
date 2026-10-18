# Platform build notes

One short checklist per target. Each says what was verified and lists the gotchas we actually hit.

| Target | Host you build on | Command (short) | Checklist |
|---|---|---|---|
| Android | any, with the Android SDK | `./gradlew :androidApp:assembleDebug` | [android.md](docs/platform-builds/android.md) |
| iOS | macOS Apple Silicon + Xcode | Xcode / `xcodebuild` | [ios.md](docs/platform-builds/ios.md) |
| macOS desktop (`.dmg`) | macOS Apple Silicon | `./gradlew :desktopApp:packageDmg` (use `packageDmgLocal` for local iteration -- see checklist, notarization rate limits) | [macos-arm64.md](docs/platform-builds/macos-arm64.md) |
| Linux Debian/Ubuntu (`.deb`) x86_64, aarch64 | Linux, or any host with Podman/Docker | `./gradlew :desktopApp:packageDeb` or `scripts/packageDebianFromContainer.sh` | [linux-deb.md](docs/platform-builds/linux-deb.md) |
| Linux Fedora/RHEL (`.rpm`) x86_64, aarch64 | Linux, or any host with Podman/Docker | `./gradlew :desktopApp:packageRpm` or `scripts/packageRpmFromContainer.sh` | [linux-rpm.md](docs/platform-builds/linux-rpm.md) |
| Windows x86_64 (`.msi`) | Windows x86_64 | `gradlew.bat :desktopApp:packageMsi` | [windows-x86_64.md](docs/platform-builds/windows-x86_64.md) |
| Windows ARM64 (`.msi`) | Windows ARM64 | `gradlew.bat :desktopApp:packageMsi` | [windows-arm64.md](docs/platform-builds/windows-arm64.md) |

Test status of every build/install combination, and the merge gate: [docs/platform-builds/TEST_MATRIX.md](docs/platform-builds/TEST_MATRIX.md).

Not supported: Intel Macs (macOS and the iOS simulator are Apple Silicon only, by policy).

## Common setup (every target)
1. **Clone with the submodule.** `git clone --recursive ...`, or `git submodule update --init --recursive third_party/iperf` (pinned to iperf 3.21). Without it, native compile tasks fail input validation.
2. **Android SDK, even for desktop-only builds.** AGP configures every subproject. Set `ANDROID_HOME` or a gitignored `local.properties` with `sdk.dir=`. Install `platforms;android-37.0` and `build-tools;37.0.0` (`android-37` alone fails).
3. **Gradle wrapper only** (`./gradlew`, `gradlew.bat`), Gradle 9.x. The JDK (21, Amazon) is auto-provisioned via foojay, except on Windows ARM64 (see its checklist).
4. **Desktop packages cannot be cross-compiled.** Each `.dmg`/`.deb`/`.rpm`/`.msi` must be built on that OS and architecture, because native libraries are compiled for the build host at build time. (Linux can use the container scripts, which build for the container's CPU.)
5. **Switching a checkout between OSes/architectures:** nothing to do: the cached iperf3 `./configure` output is rebuilt automatically when the host OS/arch changes (marker: `build/iperf3-configure-host.txt`). To force it anyway, run `./gradlew cleanIperf3Configure` as its own invocation. `./configure` output is OS/arch-specific and is cached.
6. **Windows only: LF line endings in the submodule.** See [windows-x86_64.md](docs/platform-builds/windows-x86_64.md).

## Tests
`./gradlew :shared:jvmTest` (desktop JVM), `:shared:testAndroidHostTest`, `:shared:iosSimulatorArm64Test`.

## Versioning
The repo-root `VERSION` file (a plain one-line semver string, e.g. `1.0.0`) is the single source of truth for the app version. Everything else reads from it instead of carrying its own literal:
- **Android** (`androidApp/build.gradle.kts`): `versionName` = `VERSION`'s contents; `versionCode` = a derived integer (`major*10000 + minor*100 + patch`, computed in root `build.gradle.kts`'s `buildNumberFromSemver`).
- **Desktop** (`desktopApp/build.gradle.kts`): `packageVersion` reads `VERSION` directly; the CLI's `-V`/`--version` flag and startup banner print it via a generated `AppVersion.kt` (see `generateVersionKt` task).
- **iOS**: not wired automatically (iOS isn't a Gradle-built target) — run `./gradlew syncVersions` before building/archiving in Xcode to write `VERSION`'s value into `iosApp/Configuration/Config.xcconfig`'s `MARKETING_VERSION`/`CURRENT_PROJECT_VERSION`.

**To bump the version:** edit `VERSION` at the repo root, then run `./gradlew syncVersions` to update `Config.xcconfig` (Android/Desktop pick it up automatically at the next Gradle build, no extra step needed). **The GitHub release tag for that version should then match `VERSION`'s new value** (e.g. tag `1.0.0` for `VERSION` containing `1.0.0`) — tagging/pushing a release is a manual step the maintainer does deliberately, not something any build task does automatically. Note the existing `0.1` pre-release tag predates this scheme and doesn't match `VERSION`'s current `1.0.0` — the next real release's tag is what should bring these back in sync.

More detail: [README.md](README.md), [CLAUDE.md](CLAUDE.md).
