# AGENTS.md

Compact agent guide for `iperf3KMP`. **`CLAUDE.md` at the repo root is the single source of truth** (full architecture, native-bridge design, Room wiring history, packaging details) — this file holds only the facts most likely to trip up a session. If they disagree, CLAUDE.md wins.

## What this is

Kotlin Multiplatform network-testing app (Android / iOS / desktop JVM), Compose Multiplatform UI, driving **real iperf3 3.21 in-process** via `libiperf` — JNI on Android + desktop, cinterop on iOS. Windows desktop is **supported**, but not via the in-process JNI path: it spawns a bundled, Cygwin-built `iperf3.exe` as a real subprocess instead (`docs/desktop/WINDOWS_DECISION_REVERSAL_CASE_STUDY.md`; `docs/desktop/WINDOWS_DESKTOP_APP.md` is the earlier, since-reversed "not supported" decision, kept for history).

Toolchain: Gradle **9.6.1** wrapper (always `./gradlew`, never system gradle), Kotlin **2.4.0**, Compose MP **1.11.1**, AGP 9.1.1, KSP 2.3.6. foojay-resolver auto-provisions the JDK — no manual install needed.

## Setup

- After clone: `git submodule update --init --recursive third_party/iperf` (pinned at iperf 3.21) is required by every target; without it, native compile tasks fail input validation.
- Android SDK (`sdk.dir` in gitignored `local.properties`, or `ANDROID_HOME`) is needed **even for desktop-only builds** — AGP configures all subprojects. Package-ID gotcha: `platforms;android-37.0`, not `platforms;android-37`.
- Desktop builds need `clang` on PATH (native JNI bridges compile at build time). Windows additionally needs Cygwin at `C:\cygwin64` (builds the real `iperf3.exe` subprocess it bundles — gcc, not clang, see `WINDOWS_DECISION_REVERSAL_CASE_STUDY.md`) plus a native Windows LLVM clang for the JNI printf helper, and the WiX Toolset for `packageMsi`.
- iOS deployment target is **18.2** (`shared/build.gradle.kts:iperf3IosDeploymentTarget` + xcodeproj's `IPHONEOS_DEPLOYMENT_TARGET`) — don't lower it without updating both.

## Commands

| Task | Command |
|------|---------|
| Run desktop app | `./gradlew :desktopApp:run` |
| Desktop hot reload | `./gradlew :desktopApp:hotRun --auto` |
| Build / install debug APK | `./gradlew :androidApp:assembleDebug` / `installDebug` |
| iOS build/run | Open `iosApp/iosApp.xcodeproj` in Xcode — Gradle only builds the Shared framework, not the signed `.app` |
| Tests per target | `./gradlew :shared:jvmTest` · `testAndroidHostTest` · `iosSimulatorArm64Test` |
| All tests | `./gradlew :shared:allTests` |
| Desktop packaging | `./gradlew :desktopApp:packageDmg` (macOS) / `packageDeb` or `packageRpm` (Linux, amd64 + arm64) / `gradlew.bat :desktopApp:packageMsi` (Windows) — **no cross-compiling**, build on the target OS. Linux arm64 can also be built reproducibly via Podman using `Dockerfile.deb` + `packageDebianFromContainer.sh`. The `.msi` bundles a real Cygwin-built `iperf3.exe` (x64 only — runs under Prism emulation on Windows ARM64, see `docs/desktop/WINDOWS_ARM64_IPERF3_PLAN.md`) |

No lint/typecheck commands are configured. Test sources: `shared/src/{commonTest,androidHostTest,jvmTest,iosTest}/`.

## Modules

- `:shared` — all app logic + UI (KMP library). Source sets: `commonMain`, `androidMain`, `iosMain`, `jvmMain` (+ test variants).
- `:androidApp`, `:desktopApp` — thin platform shells.
- `:nativeformat`, `:iperf3native` — Android CMake/NDK JNI modules.
- `native/` (repo root, **not** a Gradle module) — single source of truth for shared C code (`native_format*`, `iperf3_bridge.*`, `iperf3_jni.c`), referenced by the modules via relative paths outside their own directories.

## Gotchas that will bite you

- **Stale native `.so` on Android**: AGP's CMake up-to-date check misses edits to `native/*.c`. After touching those files, run `./gradlew cleanNativeCxxCaches` before building (task defined in root `build.gradle.kts`).
- **iOS is deliberately ARM-only** (`iosArm64` + `iosSimulatorArm64`; xcodeproj hardcodes `ARCHS = arm64`). Don't "fix" it back to a universal arch list — modern KMP deps no longer publish Intel klibs.
- In `shared/build.gradle.kts`, **`sourceSets.getByName("iosMain")` throws** — add iOS-only dependencies to both leaf source sets (`iosArm64Main` + `iosSimulatorArm64Main`).
- **No `Dispatchers.IO` in commonMain** — it's internal on the coroutines version resolved for the iOS targets; use `Dispatchers.Default`.
- Room: every DAO method must be `suspend` (KSP fails otherwise on non-Android); bump `@Database(version = ...)` when entity schema changes and commit `shared/schemas/`; sqlite driver artifacts are per-source-set — `commonMain` gets only `room-runtime`, never `sqlite-framework` (no jvm variant; breaks `:desktopApp`).
- `AppUUID` / `AppLocalDateTime` (`Platform.kt`) have **no `toString()` override** — use `.name`, or you silently get `ClassName@hashcode`.
- In androidApp/desktopApp use `implementation(project(":shared"))`, not the typesafe `projects.shared` accessor.
- Desktop native libs compile for host OS/arch only (no cross-compiling, no fat binaries). `compileIperf3Jvm` caches iperf3's `./configure` via `third_party/iperf/src/iperf_config.h` — delete it before building on a different OS than the last configure ran on.
- `StringFormatterTest` is excluded from `androidHostTest` (Robolectric can't load a real `.so`) — covered by `jvmTest` + `iosSimulatorArm64Test` instead.
- iOS Xcode link needs `-lsqlite3` in `OTHER_LDFLAGS` at the **target level** of `project.pbxproj` (Shared.framework is static) — don't remove without re-testing.
- Don't remove `kotlin.mpp.enableCInteropCommonization=true` from `gradle.properties` — per-target Gradle compiles succeed without it, but the IDE then shows false unresolved-reference errors for cinterop declarations referenced from `iosMain`.
- Not currently compatible with Kotlin Multiplatform 1.12, do not update libs.version.toml
- Cleaning iperf3 configuration on Windows: `./gradlew cleanIperf3Configure`

## Current state

- Real iperf3 runs on all three targets; output parsing is text-based, not `--json-stream`.
- Room persistence, auto-save on run completion, History screen (`view/HistoryScreen.kt`), and CSV export (`utils/CsvExport.kt`) are implemented. `ResultDataMappers.toDomain()` is still an unpopulated stub — history reads `ResultDataEntity` directly.
- `INDEX.md` is stale — it misstates minSdk/compileSdk (26/36 vs actual 27/37), lists only 4 modules (there are 5), and calls the iperf3 integration "not yet implemented". Trust CLAUDE.md over it.

## Docs map

- `CLAUDE.md` — **single source of truth**: architecture, native bridge design, Room wiring + bug history, packaging, fresh-host setup.
- `docs/architecture/IPERF3_NATIVE_INTEGRATION.md` §8–9 — iperf3 JNI/cinterop design rationale.
- `docs/architecture/ROOMDBPLAN.md` — persistence plan; `docs/desktop/WINDOWS_DECISION_REVERSAL_CASE_STUDY.md` — how/why Windows desktop went from unsupported to shipped, and what it cost; `docs/desktop/WINDOWS_ARM64_IPERF3_PLAN.md` — native ARM64 `iperf3.exe` planning (not started; today's `.msi` runs the x64 build under Prism emulation on ARM64).
- `docs/project/TODO.md` — open-work tracker (currently: automated Room round-trip test).
- Case studies in `docs/research/` (`PRINTF_FORMATTING_CASE_STUDY.md`, `LMSTUDIO_MCP_CASE_STUDY.md`, …) are research artifacts, not build/architecture docs.
