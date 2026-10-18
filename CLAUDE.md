# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

> This file is the extended reference for repo/agent guidance. `AGENTS.md` at the root is the compact cross-tool entry point (commands + top gotchas) that points here for detail — keep AGENTS.md short; deep rationale and history belong in this file. If they disagree, trust the code, then this file.

## Project overview

`iperf3KMP` is a Kotlin Multiplatform (Android / iOS / Desktop-JVM) network-testing app UI built with Compose Multiplatform, developed as part of an independent software-engineering research study. All three targets now drive **real** iperf3 3.21 in-process via `libiperf` (JNI on Android/desktop, cinterop on iOS) — see "Native iperf3 client" below and `IPERF3_NATIVE_INTEGRATION.md`.

Root project name: `iperf3KMP` (`settings.gradle.kts`). Gradle **9.6.1** (wrapper present) — use `./gradlew`, never system `gradle`. Kotlin **2.4.0**, Compose Multiplatform **1.11.1**, AGP **9.1.1**.

## Modules

- **`:shared`** — KMP library, all app logic and UI. Source dirs on disk: `commonMain`, `commonTest`, `androidMain`, `androidHostTest`, `iosMain`, `iosTest`, `jvmMain`, `jvmTest`. Quirk verified by testing: `kotlin.sourceSets.findByName("iosMain")` returns `null` throughout `shared/build.gradle.kts` evaluation (including `afterEvaluate`) — there's no named `iosMain` `KotlinSourceSet` reachable from the build script, even though `shared/src/iosMain/`'s content (`MainViewController.kt`, `Platform.ios.kt`, etc.) does end up compiled into both iOS targets' klibs (confirmed by inspecting compiled symbols) via some other mechanism. **Practical consequence:** to add an iOS-only Gradle dependency, don't try `sourceSets.getByName("iosMain") { ... }` in `shared/build.gradle.kts` — it throws "not found". Add it to both real leaf source sets instead: `getByName("iosArm64Main").dependencies { ... }` and `getByName("iosSimulatorArm64Main").dependencies { ... }` (see the Room section below for a working example).
- **`:androidApp`** — Android application shell.
- **`:desktopApp`** — JVM desktop application shell.
- **`:nativeformat`** — standalone Android library, CMake/NDK build for JNI native formatting (see below).
- `native/` (repo root, not a Gradle module) — single source of truth C code for printf-style formatting (`native_format.h`, `native_format_jni.c`), reached via JNI from Android/desktop and cinterop from iOS.
- `iosApp/iosApp.xcodeproj` — iOS app entry point. **Open in Xcode, never build via Gradle**; don't edit `iosApp/` Gradle files, the iOS build is managed entirely by Xcode.

## Commands

- Desktop run: `./gradlew :desktopApp:run`
- Desktop hot reload: `./gradlew :desktopApp:hotRun --auto`
- Desktop tests: `./gradlew :shared:jvmTest`
- Build debug APK: `./gradlew :androidApp:assembleDebug`
- Install debug APK: `./gradlew :androidApp:installDebug`
- Android host tests: `./gradlew :shared:testAndroidHostTest` (excludes `StringFormatterTest` — see native formatting note below)
- iOS: open `iosApp/iosApp.xcodeproj` in Xcode and run from there
- iOS simulator tests: `./gradlew :shared:iosSimulatorArm64Test`
- Test sources live in `shared/src/commonTest/`, `shared/src/androidHostTest/`, `shared/src/jvmTest/`, `shared/src/iosTest/`

No lint/typecheck commands are configured for this project — none needed.

## Architecture

- **ViewModel**: `shared/src/commonMain/.../viewmodel/Iperf3RunViewModel.kt` — extends `ViewModel`, exposes UI state via `StateFlow`.
- **View**: `shared/src/commonMain/.../view/Iperf3View.kt` — Compose UI composable, composed from smaller pieces in the same `view/` package (`InputFields.kt`, `RunningColumnSection.kt`, `ResultsRow.kt`, `IperfTopBar.kt`/`IperfBottomBar.kt`, etc.).
- **Runner**: `shared/src/commonMain/.../runner/` (`Iperf3Executor.kt`, `IperfRunner.kt` [`expect`], `IperfCallback.kt`) — drives real iperf3 on all three targets via platform `actual`s, see "Native iperf3 client" below. `sampleOutputData.kt` remains commonMain (used by Compose previews) but is no longer wired into any real run path.
- **Unit conversion**: `shared/src/commonMain/.../utils/UnitConverter.kt`.
- **Theme**: `shared/src/commonMain/.../ui/theme/` — `Color.kt`, `Type.kt`, `Theme.kt`, `mesloFontFamily.kt`.
- Desktop main class: `com.jabramson.iperf3_network_tester_kmp.MainKt` (`desktopApp/build.gradle.kts:20`).

### Native printf-style formatting (JNI + cinterop)

Kotlin's `vararg args: Any` cannot cross an FFI boundary as a native variadic list, so all platforms share one C implementation (`native/native_format.h`, `native_format_jni.c`) via a tagged-union `FormatArg[]` instead of C varargs:

- Android/desktop reach it through JNI (`:nativeformat` CMake/NDK module for Android; `shared/build.gradle.kts` compiles a host `clang` dylib/so/dll for desktop, wired through `NativeFormatterBridge.kt` in `androidMain`/`jvmMain`).
- iOS reaches it through cinterop (`shared/src/nativeInterop/cinterop/native_format.def`).
- `StringFormatterTest` (`shared/src/commonTest/.../utils/StringFormatterTest.kt`) is excluded from `androidHostTest` — Robolectric runs on a plain JVM host and can't `System.loadLibrary()` a real `.so`. Coverage for that path comes from `jvmTest` (JNI, host-compiled) and `iosSimulatorArm64Test` (cinterop) instead.
- Full history/rationale: `PRINTF_FORMATTING_CASE_STUDY.md`, `NATIVE_FORMATTING_JUSTIFICATION.md`.

### Native iperf3 client (JNI + cinterop)

Real libiperf 3.21 (`third_party/iperf` submodule, client-only build — no OpenSSL/SCTP/server support) in-process on all three targets, same JNI-on-Android/desktop + cinterop-on-iOS split as the printf work, but split into two C files instead of one because iperf3's logic is stateful/callback-driven mid-flight, not a single stateless return value — see `IPERF3_NATIVE_INTEGRATION.md` §8.4/§9 for the full design rationale:

- **`native/iperf3_bridge.h`/`iperf3_bridge.c`** — the platform-agnostic core: owns test lifecycle, the monitor/reader/stderr-reader background threads, stall detection, and the `warning: `-prefix stderr filter. Zero JNI (or Kotlin/Native) types — platform adapters talk to it only through `struct IperfCallbacks` (a `void *ctx` plus five function pointers: thread attach/detach hooks, output/error/complete).
- **`native/iperf3_jni.c`** — thin JNI adapter over the bridge, shared verbatim between Android (`:iperf3native` CMake/NDK module) and desktop (`compileIperf3Jvm` in `shared/build.gradle.kts`), wired through `NativeIperfBridge.kt` in `androidMain`/`jvmMain`.
- iOS reaches `iperf3_bridge.c` directly through cinterop (`shared/src/nativeInterop/cinterop/iperf3_bridge.def`) — no JNI adapter involved. Unlike `native_format.h` (header-only, nothing to link), this needs a real compiled static library: `shared/build.gradle.kts`'s `compileIperf3BridgeIosArm64`/`IosSimulatorArm64` tasks cross-compile the client-only iperf3 sources + `iperf3_bridge.c` via `xcrun -sdk <iphoneos|iphonesimulator> clang`/`libtool -static` into a per-target `libiperf3bridge.a`, linked in via cinterop `extraOpts("-staticLibrary", ..., "-libraryPath", ...)` (device and simulator arm64 need separate builds — different SDKs, not link-compatible despite sharing an instruction set). `native/iperf_config_ios.h` stands in for iOS's missing on-target `./configure` run — it's a direct copy of the real `./configure`-generated config this project's own macOS desktop build already produces (iOS shares macOS's Darwin/BSD sockopt surface).
- `shared/src/iosMain/.../utils/NativeIperfBridge.kt` uses `StableRef<IperfCallback>` to pass the Kotlin callback across the C boundary as `void *ctx`, and `staticCFunction` for the callback function pointers. No JNI-style thread attach/detach needed on this side — Kotlin/Native's default memory model allows calling into Kotlin from any thread.
- **Signing is per-developer and never committed:** `project.pbxproj` keeps `DEVELOPMENT_TEAM = "${TEAM_ID}"`; `Config.xcconfig` sets defaults (`TEAM_ID=` empty, `BUNDLE_ID_PREFIX=com.jabramson`) then `#include?`s a gitignored `iosApp/Configuration/LocalSigning.xcconfig` (template: `LocalSigning.xcconfig.template`). Bundle ID is `$(BUNDLE_ID_PREFIX).iperf3-network-tester-kmp`. Never write a real team ID into `project.pbxproj` — choosing a team in Xcode's Signing & Capabilities UI does exactly that, so check the diff. Setup steps for other developers: `docs/platform-builds/ios.md`.
- `iosApp/iosApp/Info.plist` has `NSLocalNetworkUsageDescription` (needed for raw socket access to local-network hosts; no `NSBonjourServices` since this app doesn't do mDNS discovery).
- Full history/rationale, including the JNI/core-split design and every iOS-specific build/link detail: `IPERF3_NATIVE_INTEGRATION.md` §8–§9.

### iOS target policy — deliberately ARM-only

`shared/build.gradle.kts` declares **only** `iosArm64()` and `iosSimulatorArm64()` — no `iosX64`/Intel simulator. This is intentional project policy, not a gap: several modern KMP libraries (e.g. `androidx.sqlite:sqlite-framework` 2.7.0+) no longer publish Intel-Apple klib variants at all, so dropping x86_64 avoids that whole class of dependency/linker problem. `iosApp.xcodeproj` build settings hardcode `ARCHS = arm64;` (not Xcode's default `arm64 x86_64`) so Xcode never requests an x86_64 slice even for a generic/non-booted simulator destination — **don't "fix" this back to a universal arch list.**

If Room/`androidx.sqlite` is ever added to `:shared` for iOS, also add `linkerOpts.add("-lsqlite3")` to the `iosArm64`/`iosSimulatorArm64` framework blocks in `shared/build.gradle.kts` — `sqlite-framework`'s cinterop declares the sqlite3 C API but doesn't bundle an implementation; without that flag the linker fails with "Undefined symbols for architecture arm64" (`sqlite3_bind_*` etc.). **This has been done** — see the Room section below. **That Gradle-side flag alone was not enough** — see the `OTHER_LDFLAGS` note there too, since `Shared.framework` is built `isStatic = true`.

### Room + KMP SQLite persistence (Phase 1 done and manually verified on all three platforms)

The original plan doc (`docs/architecture/ROOMDBPLAN.md`) was deleted in `9f53f8c` — this section supersedes it; recover it with `git show 1a44be8:docs/architecture/ROOMDBPLAN.md` only if the planning history matters. Both the Gradle/dependency side **and** the `database/` package's Kotlin sources are in place, building clean, and manually confirmed to actually persist data on Android, iOS (device + simulator), and desktop JVM — not just "compiles," real Save-button-click end-to-end verification on each. `Iperf3RunViewModel.saveResult()` (`shared/src/commonMain/.../viewmodel/Iperf3RunViewModel.kt`) inserts a real `ResultDataEntity` instead of the old `delay(3.seconds)` stub. The old `model/ResultData.kt` domain class (superseded by `ResultDataEntity`) has been deleted — nothing referenced it anymore.

Where each platform's data actually lands, if you need to inspect it directly (e.g. via DB Browser for SQLite):
- **Desktop**: `~/.iperf3kmp/result_data-db.db` — a plain file, open directly.
- **iOS simulator**: `xcrun simctl get_app_container booted com.jabramson.iperf3-network-tester-kmp data` prints the container path; `Documents/result_data-db.db` inside it. (Bundle ID comes from `iosApp/Configuration/Config.xcconfig`'s `PRODUCT_BUNDLE_IDENTIFIER`.)
- **iOS device**: Xcode → Window → Devices and Simulators → select device → select app → gear icon → "Download Container…" → `AppData/Documents/result_data-db.db` inside the downloaded `.xcappdata`.
- **Android**: `context.getDatabasePath("${ResultDatabase.DBNAME}.db")` — standard `run-as`/Device File Explorer territory, not covered here in detail since it's the familiar Android flow.

- `database/` package (commonMain): `ResultDataEntity.kt` (`@Entity`), `ResultDataEntityDao.kt` (`@Dao`, all methods `suspend` — see gotcha below), `ResultDatabase.kt` (`@Database`, `@ConstructedBy`), `ResultDataMappers.kt` (`toEntity()`/`toDomain()` — **`toDomain()` is currently a stub that returns `Iperf3RunningState()` unpopulated**, fine for now since nothing reads results back yet), `ResultDataRepository.kt` (thin suspend wrapper over the DAO), `Database.kt` (`expect fun createSQLiteDriver()`, `expect fun getResultDatabase()`, common `getRoomDatabase(builder)` helper).
- Per-platform `database/Database.<platform>.kt` (`androidMain`/`iosMain`/`jvmMain`): each `actual fun getResultDatabase()` delegates to a private object with a `by lazy` singleton — the database and its wrapping `ResultDataRepository` are built exactly once per process, on first access, not recreated on subsequent calls.
- `AndroidAppContextHolder` (androidMain, new) + one line in `MainActivity.onCreate()` (`AndroidAppContextHolder.init(applicationContext)`) — Room's Android driver needs a `Context`; this app has no DI framework, so this is the minimal way to supply one.
- `Iperf3RunViewModel` reaches the repository via `private val resultDataRepository: ResultDataRepository by lazy { ResultDataRepository(getResultDatabase().resultDataEntityDao()) }` — first touched inside `saveResult()`'s coroutine, never in `init{}`, so the blocking Room setup never runs on the main/UI thread. This is a **fourth wiring option** beyond the two the original plan weighed (constructor injection vs. an `expect object ...Provider`) — an `expect fun` returning a per-platform lazy singleton — chosen because it touches zero of the three platform entry points (`MainActivity`/`main.kt`/`MainViewController`) beyond the one Context-holder line, while still guaranteeing single construction.
- `saveResult()` failures are no longer silently swallowed — a try/catch appends `"Failed to save result: ..."` to `errorLines` and resets `isSaving`, and `isDatabaseCreated` is set `true` on successful insert.
- **Bugs fixed while wiring this up, all worth knowing about if touched again:**
  1. `AppUUID`/`AppLocalDateTime` (`Platform.kt`) only declare `val name: String` — no `toString()` override — so calling `.toString()` on one silently returns `Any`'s default (`ClassName@hashcode`), not the real UUID/timestamp. `ResultDataMappers.toEntity()` originally did exactly this for `guid`, `startDateTime`, and `endDateTime`, which would have made every saved row's `guid` garbage and non-unique. Always use `.name` on these types, never `.toString()`.
  2. Room only allows non-`suspend` (blocking) DAO methods on Android — `jvm()`/iOS source sets **require** every DAO method to be `suspend`, or KSP fails with `Only suspend functions are allowed in DAOs declared in source sets targeting non-Android platforms`. `ResultDataEntityDao` was originally written with plain blocking `fun`s (compiled fine on Android, broke `kspKotlinJvm`/`kspKotlinIosArm64`) — all methods are now `suspend`.
  3. `getRoomDatabase()`'s builder never actually called `.fallbackToDestructiveMigration(dropAllTables = true)` despite the original plan calling for it — added now. Deliberate: no hand-written `Migration` objects until there's real on-device data worth preserving across a schema change. `ResultDatabase`'s `@Database(version = ...)` needs bumping any time the entity schema changes (or to force-clear bad data, e.g. rows written under bug #1 above before it was fixed) — without the destructive-migration fallback, a version bump alone throws at runtime instead of dropping/recreating the tables. Schema JSON for each version lands in `shared/schemas/com.jabramson.iperf3_network_tester_kmp.database.ResultDatabase//database.ResultDatabase/<version>.json` — bump `@Database(version = ...)` in `ResultDatabase.kt` any time `ResultDataEntity`'s columns change.
  4. `Dispatchers.IO` is **not public** for `iosArm64`/`iosSimulatorArm64` under the `kotlinx-coroutines-core` version Room 2.8.4's own transitive dependency graph resolves to here (`1.10.2`, pulled below this project's declared `1.11.0`) — compiling `commonMain` code that references `Dispatchers.IO` fails on iOS only with `Cannot access 'val IO: CoroutineDispatcher': it is internal`. Fixed by using `Dispatchers.Default` instead (in `getRoomDatabase()`'s `setQueryCoroutineContext(...)` and in `saveResult()`'s `viewModelScope.launch(...)`) — it's guaranteed public on all three targets. If you see this error again elsewhere in `commonMain`, this is why; don't reach for `Dispatchers.IO` in shared code in this project.
  5. The Xcode build (not `:shared:compileKotlinIosArm64` — that step succeeds fine) failed at the final app-link step with `Undefined symbols for architecture arm64: "_sqlite3_bind_blob"` etc., **even with** `linkerOpts.add("-lsqlite3")` already set in `shared/build.gradle.kts` (see the ARM-only section above). Root cause: `Shared.framework` is built `isStatic = true` — a static framework is just an archive of object files with unresolved external symbols, so `-lsqlite3` in Gradle's `linkerOpts` only helps when Gradle itself links something; it doesn't propagate to Xcode's own final link of the `iosApp` binary, which is a separate step that needs its own `-lsqlite3`. Fixed by adding `OTHER_LDFLAGS = ("$(inherited)", "-lsqlite3")` directly to `iosApp.xcodeproj/project.pbxproj`'s two `iosApp` **target**-level build configurations (Debug: config `8B33C09D6C7E0932847C8C0D`; Release: `4BB6AABBCB8908F1AEB9559F`) — not the project-level Debug/Release configs, which don't govern the app target's actual link flags. Verified with `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator build`. If a dynamic framework (`isStatic = false`) is ever adopted instead, this Xcode-side flag likely becomes unnecessary — don't remove it without re-testing if that switch is ever made.
- **Still not done:** no *automated* read-back/round-trip test on any target yet — manual verification confirmed saving works, but there's no regression test guarding it. Minimum bar: insert a `ResultDataEntity` built via `toEntity()` from a populated `Iperf3RunningState`, read it back by guid, assert field equality — on `jvmTest` (`BundledSQLiteDriver`, in-memory), `iosSimulatorArm64Test` (`NativeSQLiteDriver`), and `androidHostTest` (unlike the JNI `.so` case, Robolectric does support Room's `AndroidSQLiteDriver`). No `commonTest` version — the driver is platform-specific by construction. Phase 2 is now implemented: the History screen (`view/HistoryScreen.kt`) and CSV export (`utils/CsvExport.kt`, Android share via `ShareCsv.android.kt`'s FileProvider) both read `ResultDataEntity` directly from Room — so `ResultDataMappers.toDomain()` remains an unpopulated stub that nothing consumes; it's only needed if a domain-model-based view is ever built.

- Versions (`gradle/libs.versions.toml`): `androidx-room = "2.8.4"`, `androidx-sqlite = "2.7.0"`, `ksp = "2.3.6"` (the KSP release pinned to Kotlin 2.4.0 — KSP version strings are tied to a specific Kotlin version, re-check this pin if Kotlin is ever bumped).
- Plugins applied in `shared/build.gradle.kts`: `alias(libs.plugins.ksp)`, `alias(libs.plugins.androidxRoom)`, plus `room { schemaDirectory("$projectDir/schemas") }` and `kotlin { jvmToolchain(17) }` (Kotlin 2.4.0 can target newer bytecode than the installed JDK otherwise).
- SQLite driver artifact is **per source set, not one-size-fits-all** — putting the wrong one in the wrong source set either fails to resolve or silently uses the wrong driver:
  - `commonMain`: only `androidx-room-runtime`. **Not** `androidx-sqlite-framework` — that artifact only publishes Android + Apple/Native target variants (it wraps each platform's *native* SQLite framework; JVM has no equivalent to wrap, so no `jvm` variant exists at all). The `SQLiteDriver` interface `commonMain`'s `expect fun createSQLiteDriver(): SQLiteDriver` needs still resolves fine there — it comes in transitively via `room-runtime` → the base `androidx.sqlite:sqlite` module, which does publish `jvm`.
  - `androidMain`: `androidx-sqlite-framework-android` (`AndroidSQLiteDriver`).
  - `iosArm64Main` **and** `iosSimulatorArm64Main` (added separately to each — see the `:shared` module note above on why `iosMain` can't be targeted directly): `androidx-sqlite-framework` (`NativeSQLiteDriver`).
  - `jvmMain`: `androidx-sqlite-bundled` (`BundledSQLiteDriver`) — **not** `sqlite-framework-android`, that's Android-only.
- Root cause of the original `:desktopApp:build` failure (`KMP Dependencies Resolution Failure ... Couldn't resolve dependency 'androidx.sqlite:sqlite-framework:2.7.0' in 'commonMain' ... Unresolved platforms: [jvm]`): `sqlite-framework` had been placed directly in `commonMain` (matching Google's `migrate-room` codelab's advice, which only ever built Android + iOS and never hit this). `:androidApp:assembleDebug` and `:shared:tasks` both succeeded regardless — they don't force full `jvm` variant resolution — so the break was only visible via a real `:desktopApp:build`/`:desktopApp:run`, which is why it's worth running those explicitly after touching sqlite/Room dependencies rather than trusting the other two targets as a proxy.
- Room's KSP compiler must be registered **per target** — it is not picked up automatically from `commonMain` in a KMP module: `add("kspAndroid", ...)`, `add("kspIosArm64", ...)`, `add("kspIosSimulatorArm64", ...)`, `add("kspJvm", ...)` in `shared/build.gradle.kts`'s top-level `dependencies {}` block, plus `implementation(libs.androidx.room.runtime)` + `ksp(libs.androidx.room.compiler)` in `androidApp/build.gradle.kts` for app-level codegen.

### Application icon

Deployed to all three targets from `ic_launcher-playstore.png` (512×512, **not** 1024×1024 — so the iOS 1024×1024 files below are `sips` upscales and may look slightly soft on Retina):

| Target | Icon file |
|--------|-----------|
| Android | `androidApp/src/main/res/mipmap-{density}/ic_launcher*.webp` + `mipmap-anydpi-v26/ic_launcher*.xml` + `values/ic_launcher_background.xml` (#0F1E31 navy) |
| iOS | `iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/app-icon-1024{,-dark,-tinted}.png` — Xcode's single-size "universal" icon slot **only** accepts 1024×1024; a 512×512 entry is silently ignored and the build then warns that the 60x60@2x / 76x76@2x / 83.5x83.5@2x / 1024 App Store icons are all missing |
| Desktop | `desktopApp/src/main/resources/app-icon.png` (in-app window icon, via `Window(icon = ...)` in `main.kt` — see below) |

**In-app window icon vs. packaged installer icon are two separate mechanisms — both must be set, and jpackage's icon needs a platform-specific format, not a raw PNG:**
- The running window's icon (title bar/taskbar/Dock while the app is open) is set in code: `main.kt`'s `Window(icon = painterResource(Res.drawable.app_icon), ...)`, where `Res` is `:shared`'s generated Compose-resources class (`shared/src/commonMain/composeResources/drawable/app_icon.png`, `compose.resources { publicResClass = true }` in `shared/build.gradle.kts` makes it visible outside `:shared`). Without this, AWT/Compose defaults to the generic Java Duke/coffee-cup icon — most visible on Linux.
- The packaged app's installer/menu/Dock icon (`.app`/`.dmg`, `.exe`/`.msi`, `.deb`) is set separately, in `desktopApp/build.gradle.kts`'s `nativeDistributions { macOS/windows/linux { iconFile.set(...) } }`, and **jpackage requires a different file format per OS** — passing the wrong format doesn't fail the build, it silently falls back to no/generic icon:
  - **macOS**: `.icns` (`desktopApp/src/main/resources/app-icon.icns`) — generated from the 512×512 source via `sips` (resize to the standard iconset sizes) + `iconutil -c icns`. This was the original bug: it was pointed at the raw `.png`, so the packaged `.app`/`.dmg` had no icon even though the in-app window icon was fine.
  - **Windows**: `.ico` (`desktopApp/src/main/resources/app-icon.ico`) — generated via ImageMagick (`convert app-icon.png -define icon:auto-resize=256,128,64,48,32,16 app-icon.ico`). Same underlying bug, fixed the same way, and `packageMsi` now builds on real Windows hosts, but the installed app's icon hasn't been specifically checked yet.
  - **Linux**: `.png` is correct as-is — jpackage's deb backend accepts it directly, no conversion needed.

### Packaging the desktop app for distribution

Desktop packaging goes through Compose Multiplatform's `compose.desktop.application` plugin (`desktopApp/build.gradle.kts`), which wraps `jpackage`:

- `./gradlew :desktopApp:packageDmg` — macOS `.dmg`
- `./gradlew :desktopApp:packageMsi` — Windows `.msi`
- `./gradlew :desktopApp:packageDeb` — Linux `.deb`
- `./gradlew :desktopApp:createDistributable` — unpackaged app directory, for testing the bundle without a full installer

Build-and-install from this checkout: `scripts/installMacFromPackage.sh` (a wrapper for `./gradlew :desktopApp:installDmg`) on macOS, `scripts/installLinuxFromPackage.sh [--uninstall] [rpm|deb]` on Linux (auto-picks rpm when `dnf` exists, else deb; `--uninstall` also clears the menu entry that jpackage's rpm `%preun` leaves behind under dnf5). The Linux one is deliberately a script, not a Gradle task: installing a package needs `sudo`, and the Gradle daemon has no terminal for it to prompt on (`installDmg` only copies into `/Applications`, no root needed). Developer scripts live in `scripts/` (also the `package*FromContainer.sh` builders, which `cd` to the repo root themselves); the repo root keeps only the end-user `install.sh`/`install.ps1`, which download a published GitHub release -- don't move or rename those, the README's `curl .../master/install.sh | bash` one-liner depends on the path.

macOS signing/notarization is opt-in via properties in the developer's own `~/.gradle/gradle.properties` (`desktopApp.macSigningIdentity`, `desktopApp.macNotarizationProfile` — a `notarytool store-credentials` keychain profile name — and optional `desktopApp.macBundleId`, default `com.jabramson.iperf3-network-tester-kmp`). Never put these in the repo's committed `gradle.properties`. Without them, `packageDmg` builds unsigned. Setup steps: `docs/platform-builds/macos-arm64.md`.

`packageName` (`desktopApp/build.gradle.kts`) is `"iperf3KMP"`. Output lands under `desktopApp/build/compose/binaries/main/<format>/`, one subdirectory per format actually built:
- `dmg/iperf3KMP-1.0.0.dmg`
- `msi/iperf3KMP-1.0.0.msi` — confirmed by real builds on Windows x86_64 and Windows ARM64
- `deb/iperf3kmp_1.0.0_amd64.deb` — **lowercased**, confirmed by an actual build/install. Debian package-name policy forbids uppercase, so jpackage lowercases `packageName` for the `.deb`'s `Package:` field and filename; it does **not** lowercase it for `.dmg`/`.app`/(likely) `.msi`, since those platforms have no such restriction. Don't be thrown by the mismatch against `packageName` in `desktopApp/build.gradle.kts` — it's `jpackage`'s doing, not a misconfiguration.
- `app/iperf3KMP.app` (or `.app`-equivalent per OS) — the unpackaged bundle from `createDistributable`

**Quick recipe — clone to running app on a fresh Linux machine:**
```
git clone https://github.com/<your-username>/iperf3-kmp
cd iperf3-kmp/
git submodule update --init --recursive
./gradlew :desktopApp:packageDeb
sudo apt install ./desktopApp/build/compose/binaries/main/deb/iperf3kmp_1.0.0_amd64.deb
/opt/iperf3kmp/bin/iperf3KMP
```
(No `--branch` needed — this clones `master`, the default branch.) The leading `./` on the `apt install` argument matters — without it, `apt` treats the argument as a repo package name instead of a local file path. `sudo dpkg -i <file>.deb` also works but won't resolve/install missing dependencies the way `apt install` does. To remove it later: `sudo apt remove iperf3kmp` (the lowercased package name, not the `.deb` filename or `packageName`). You may see a harmless `Notice: Download is performed unsandboxed as root ... couldn't be accessed by user '_apt'` during install — that's just `apt`'s sandboxed download user being unable to read a file under your home directory, not a failure.

Where it lands on Linux (confirmed via `dpkg -L iperf3kmp` after install) — note the naming is inconsistent between the two, which is jpackage's doing, not a bug:
- Install root: `/opt/iperf3kmp/` (lowercased, matches the `.deb` package name)
- Launcher binary: `/opt/iperf3kmp/bin/iperf3KMP` — keeps `packageName`'s exact casing, unlike its own parent directory. Run this path directly to launch the app from a terminal (see the quick recipe above).
- `.desktop` entry and icon live under `.../lib/`, not directly under `/usr/share/applications/` — `dpkg -L` only lists files the package itself owns, not ones a `postinst` maintainer script might copy/symlink elsewhere, so whether the app shows up in your desktop's app-launcher menu depends on that script actually running; the binary path above always works regardless.

**No cross-compiling — each target format must be built on that OS.** The app isn't pure JVM: `shared/build.gradle.kts` compiles two native C libraries at build time — `compileNativeFormatJvm` (printf-style JNI helper) and `compileIperf3Jvm` (the real iperf3 JNI bridge, from `third_party/iperf` + `native/iperf3_jni.c`; skipped on Windows, see below) — both invoking a C compiler for whatever OS/arch Gradle is currently running on, bundling the resulting `.dylib`/`.so`/`.dll` as a `:shared` jar resource that's `System.load()`ed at runtime (see "Native printf-style formatting" above). Both compile tasks are already wired as dependencies of `:shared:jvmProcessResources`, which the packaging tasks depend on transitively — so e.g. `packageDmg` alone triggers the full native build correctly, but only produces a working artifact for the host it ran on.

Practical requirements per host:
- **macOS** → `packageDmg`, needs Xcode Command Line Tools for `clang`.
- **Linux** → `packageDeb`, needs `clang` plus `dpkg`/`fakeroot` for jpackage's deb backend (normally already present on Debian/Ubuntu).
- **Windows** → `gradlew.bat :desktopApp:packageMsi`. Different toolchain from the other hosts; see `docs/platform-builds/windows-x86_64.md` / `windows-arm64.md` for the checklists. In short: Cygwin at exactly `C:\cygwin64` (`gcc-core`, `make`; the x86_64 installer, also on ARM64) builds the bundled `iperf3.exe`; a native Windows LLVM `clang` plus Visual Studio Build Tools (clang takes its C headers/libs from VS; ARM64 build tools on ARM64) builds `nativeformat.dll`; the WiX Toolset makes the `.msi`. Windows does **not** use the in-process JNI iperf3 path: `IperfRunner` routes it to `WindowsIperf3ProcessRunner`, which spawns the bundled `iperf3.exe` (+ `cygwin1.dll`) from `compose.application.resources.dir`. `compileIperf3ExeWindows` builds them, `:desktopApp:stageWindowsIperf3` stages them into the Compose resources folder (`windows-x64` or `windows-arm64` by host arch), and `compileIperf3Jvm` is skipped on Windows. On ARM64 the MSI is mixed-architecture: ARM64 JVM/`nativeformat.dll`, x86_64 `iperf3.exe` under Prism (Cygwin has no ARM64 port; plan for a native exe: `docs/desktop/WINDOWS_ARM64_IPERF3_PLAN.md`). Build-script rules for the Cygwin tasks: never fall back to a bare `bash`/`clang` from PATH (WSL's `bash.exe` wins PATH and silently miscompiles); the tasks fail loudly instead. A Windows checkout with `core.autocrlf=true` gives the submodule CRLF scripts and `./configure` fails; the tasks detect this and print the fix. The Windows ARM64 JDK must be a real aarch64 build (an x64 JDK would run Gradle emulated and produce x64 output).

Two more things to know before packaging on a new host:
- **No fat binaries / no arch matrix** — only the architecture of the build machine gets compiled in (unlike the Android module, which builds all four ABIs via `abiFilters`). An Apple Silicon `.dmg` won't run on an Intel Mac and vice versa; there's no equivalent of Android's per-ABI splitting for desktop.
- `compileIperf3Jvm`/`compileIperf3ExeWindows` cache `third_party/iperf`'s `./configure` output (`src/iperf_config.h`/`version.h`) and record which host made it in `build/iperf3-configure-host.txt` (`<os>-<arch>`). When that doesn't match the current host, or is missing (e.g. after `clean`), they delete the cached config and reconfigure automatically (`Iperf3Shell.configureForHost` in `shared/build.gradle.kts`). This matters for checkouts shared across hosts — VM shared folders and the container scripts' bind mount — where another OS's config otherwise fails (`'linux/tcp.h' file not found` on macOS) or silently miscompiles. The marker is in the root `build/`, not the submodule, so it never shows in `git status`. `./gradlew cleanIperf3Configure` (its own invocation) remains as a manual override.

#### Setting up a fresh build host (new machine/OS)

Bringing up a clone on a new machine (e.g. Linux, for `packageDeb`) hits three one-time issues, all surfaced by trying `:desktopApp:packageDmg`/`packageMsi`/`packageDeb` there for the first time:

1. **Submodule not checked out.** A plain `git clone` doesn't pull `third_party/iperf`'s contents, so `compileIperf3Jvm` fails Gradle's task-input validation with `Input file does not exist ... 'third_party/iperf/src' which doesn't exist`. Fix:
   ```
   git submodule update --init --recursive
   ```

2. **Android SDK required even for desktop-only builds.** Gradle configures every subproject by default, including `:androidApp`, `:shared`'s Android target, `:nativeformat`, and `:iperf3native` — so AGP needs a valid SDK location just to *configure* the build, even though no Android compile task runs on this path. A full Android Studio install isn't needed — the SDK command-line tools alone are enough:
   1. Download "command line tools only" from https://developer.android.com/studio#command-tools for the target OS.
   2. The zip extracts flat; the tools require one extra level of nesting — move it so the binary ends up at `<sdk-root>/cmdline-tools/latest/bin/sdkmanager` (not `<sdk-root>/cmdline-tools/bin/sdkmanager`).
   3. Set `ANDROID_HOME=<sdk-root>` and add `$ANDROID_HOME/cmdline-tools/latest/bin` + `$ANDROID_HOME/platform-tools` to `PATH` (or create a gitignored `local.properties` at the repo root with `sdk.dir=<sdk-root>` instead).
   4. `sdkmanager --licenses`, then install packages. **Package-naming gotcha**: Android's shift to quarterly "minor API level" platform releases (starting with API 36) means `sdkmanager` platform IDs are now `platforms;android-<major>.<minor>`, not just the major number — `platforms;android-37` fails with "Failed to find package"; the correct id for this project's compileSdk **37** is `platforms;android-37.0`:
      ```
      sdkmanager "platform-tools" "platforms;android-37.0" "build-tools;37.0.0"
      ```
   5. The NDK isn't needed for desktop-only builds — `:nativeformat`/`:iperf3native` only build when Android-specific tasks run, which `packageDmg`/`packageMsi`/`packageDeb` never trigger.

3. **`native/iperf3_jni.c` had a real (now-fixed) portability bug**, only surfaced by a Linux clang, not macOS's: all three `AttachCurrentThread` calls (`monitorThreadFunc`, `readerThreadFunc`, `stderrReaderThreadFunc`) passed `&env` (a `JNIEnv **`) directly where the JNI spec's signature is `void **penv`, missing the standard `(void **)` cast that the file's own `JNI_OnLoad`/`GetEnv` call already used correctly. Apple's shipped clang only warns about this mismatch; the clang on a typical Linux distro treats `-Wincompatible-pointer-types` as a hard error by default, so the desktop build only fails there — fixed by adding the missing `(void **)` cast at all three sites. Worth knowing if this file is touched again: always cast the second arg of `AttachCurrentThread`/`GetEnv` to `(void **)`, don't rely on it compiling clean just because it built fine on macOS.

### Versioning

Before this was added, every platform carried its own independent, hardcoded version literal, and they'd already drifted (Desktop/README said `1.0.0`, Android's `versionName` and iOS's `MARKETING_VERSION` both said `1.0`, and the one published GitHub release is tagged `0.1`). The repo-root `VERSION` file (a plain one-line semver string) is now the single source of truth:

- **Root `build.gradle.kts`** reads `VERSION` once (`val appVersion = rootProject.file("VERSION").readText().trim()`), exposes it via `extra["appVersion"]`, and derives an integer build number via `buildNumberFromSemver()` (`major*10000 + minor*100 + patch`, exposed via `extra["appBuildNumber"]`) for the two platforms that need a plain monotonic integer rather than the dotted string.
- **Android** (`androidApp/build.gradle.kts`): `versionName = rootProject.extra["appVersion"] as String`, `versionCode = rootProject.extra["appBuildNumber"] as Int`.
- **Desktop** (`desktopApp/build.gradle.kts`): `packageVersion` reads `rootProject.extra["appVersion"]` directly (available at Gradle-configuration time, which is all packaging needs). The CLI needs the version as an actual runtime constant, not just a Gradle-configuration-time value, so a separate `generateVersionKt` task writes `com.jabramson.iperf3_network_tester_kmp.cli.AppVersion` (a generated `.kt` file under `build/generated/version/kotlin/`) and the file is wired into `:desktopApp`'s own `main` source set via `kotlin.srcDir(generateVersionKt)` — passing a `TaskProvider` directly to `srcDir()` is a real Gradle idiom; it auto-wires the task dependency from the declared `outputs.dir(...)`, no explicit `dependsOn` needed. `CliArgs`/`CliRunner` expose it via `-V`/`--version` and print it in the startup banner.
- **iOS**: not wired automatically — iOS isn't a Gradle-built target (`iosApp.xcodeproj` is Xcode-only, see the ARM-only policy section above), so there's no Gradle task graph to hook a sync into. A standalone `syncVersions` task (root `build.gradle.kts`) rewrites `iosApp/Configuration/Config.xcconfig`'s `MARKETING_VERSION`/`CURRENT_PROJECT_VERSION` lines via regex replace — run it manually before building/archiving in Xcode. It only rewrites plain text in a config file; it doesn't add any Gradle involvement to how Xcode actually builds the app, so it doesn't violate the "never build iOS via Gradle" policy.
- **Gotcha hit while wiring this up**: inside a `tasks.register("x") { ... }` configuration block, a bare `extra[...]` reference resolves to that *Task's own* `ExtraPropertiesExtension`, not the project's — every `Task` has its own separate `extra`. Writing `rootProject.extra["appBuildNumber"]` explicitly (not just `extra[...]`) inside `syncVersions`'s task body was required; the ambiguous form fails at task-creation time with `Cannot get property 'appBuildNumber' on extra properties extension as it does not exist`, which is a confusing error since the property very much does exist — just on the wrong `extra`.
- **To bump the version**: edit `VERSION`, then run `./gradlew syncVersions` for iOS (Android/Desktop pick it up automatically on their next Gradle build/configuration, no extra step). The GitHub release tag for that version should then be created to match (e.g. tag `1.0.0` for a `VERSION` of `1.0.0`) — tagging and pushing a release is a deliberate manual step, not something any build task does automatically. The existing `0.1` pre-release tag predates this scheme and doesn't match `VERSION`'s current `1.0.0`; the next real release's tag is what brings these back in sync.
- `install.sh`/`install.ps1` (repo root) are unaffected by any of this — they already match GitHub release assets by filename *pattern* (`.dmg$`, `_arm64\.deb$`, etc.), not by a hardcoded version string, so they work the same regardless of what `VERSION` says.

## Gotchas

- foojay-resolver plugin auto-provisions the JDK — no manual install needed.
- Android minSdk **27**, compileSdk/targetSdk **37**.
- `shared/build.gradle.kts` uses `androidRuntimeClasspath(libs.compose.uiTooling)` for Android debug tooling.
- iOS simulator console spam (`[UIKBFeedbackGenerator] ... hapticpatternlibrary.plist ...`) is a harmless system-level keyboard-haptics error — safe to ignore.
- Use `implementation(project(":shared"))`, not the typesafe `implementation(projects.shared)` accessor, in `androidApp`/`desktopApp` — `settings.gradle.kts` no longer has `enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")` (removed; it was triggering a "Using a Project object as a dependency notation has been deprecated" warning under Gradle 9.6.1's newer dependency APIs).
- A separate "Using a Project object as a dependency notation has been deprecated" warning still fires during `:androidApp` configuration even with the above fixed — `--warning-mode all` traces it to AGP 9.1.1's own internals (`VariantDependenciesBuilder.build` → `VariantManager.createTestComponents`, wiring Android test-component dependencies), not to anything in this repo's build scripts. It's harmless (build succeeds) and not fixable here — only an AGP upgrade resolves it. Don't spend time chasing it in our `.gradle.kts` files.
- Not currently compatible with Kotlin Multiplatform 1.12, do not update libs.version.toml
> - RPM packaging can fail with this error on certain Redhat-like Linux Distros with below errors:
```
Execution failed for task ':desktopApp:packageRpm' (registered by plugin 'org.jetbrains.compose').
External tool execution failed:
  * Command: [......../jpackage, @...../packageRpm.args.txt]
  * Working dir: []
  * Exit code: 1
  * Standard output log: ...jpackage-....-.txt
  * Error log: ..jpackage-...err.txt
```
>  - Install the 'rpm-build' package (notice the required dash)
```
sudo dnf install -y rpm-build
```
## Current state and planned work
- Real iperf3 3.21 now runs on all three targets — see "Native iperf3 client" above and `IPERF3_NATIVE_INTEGRATION.md` §8–§9 (branch `iperf3-ios-hooks`, off `iperf3-hooks` where Android/desktop were done first). Still text-output parsing, not `--json-stream` (§3.2's deferred note still applies). iOS's build/link is verified via Gradle (`compileKotlinIosArm64`, `linkDebugFrameworkIosArm64`/`IosSimulatorArm64`, `iosSimulatorArm64Test` all pass) **and** manually verified end-to-end against real remote iperf3 servers — confirmed 2026-08-27 by the user across several iOS Simulators plus real hardware (iPhone 17 Pro, iPad M4), installed via their Apple Developer Program device-install entitlement. iOS is done to the same "manually verified end-to-end" bar as Android/desktop.
- Room/SQLite persistence exists in `:shared` now — see "Room + KMP SQLite persistence" above. Phase 1 is done and manually verified end-to-end on Android, iOS, and desktop: `saveResult()` does a real insert and the row is confirmed present in each platform's actual `.db` file. Still open: an automated read-back/round-trip test. Phase 2 (CSV export + History screen) is implemented — see the Room section above for how it reads data. `shared/schemas/` (Room's exported schema JSON, one file per `@Database(version = ...)`) should be committed — it's the diffable schema-history record, cheap to keep even without migrations.
- `TODO.md` tracks open items; `docs/README.md` indexes the case-study docs (`PRINTF_FORMATTING_CASE_STUDY.md`, `LMSTUDIO_MCP_CASE_STUDY.md`, `NATIVE_FORMATTING_JUSTIFICATION.md`, `AI_ASSISTED_DEVELOPMENT_LIMITS.md`) written as part of this project's research thread on AI-assisted development. These are research artifacts, not build/architecture docs — consult them only if asked about that research context.

## Output format
For any artifact intended for human consumption (reports, briefings, summaries, 
plans, retros, status updates, memos, decks):
- Default to a single self-contained HTML file with inline CSS
- Use semantic HTML (h1, h2, sections) and embedded styles
- Visual style: clean typography, generous whitespace, restrained color use
- Always include a print-friendly stylesheet (@media print)
- Light Mode is the default.
- File extension .html, save in artifacts/ alongside the markdown source
For reference material that will only be edited (specs, configs, READMEs, 
project notes): keep markdown as the primary format.
When unsure: ask "is this for someone to read or someone to edit?" 
Read = HTML. Edit = markdown.
