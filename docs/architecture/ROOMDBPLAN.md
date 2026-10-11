# ROOMDBPLAN.md

Plan for adding Room + KMP SQLite persistence to `iperf3KMP`, using the pattern proven in the `migrate-room` codelab (`ROOMDB.md`, `ROOMDB_MIGRATE_EXAMPLE.md`) as prior art. This is a plan only — no implementation yet.

## Goal

Persist a completed `ResultData` run to a local SQLite database on all three targets (Android, iOS, Desktop), replacing the simulated `delay(3.seconds)` in `Iperf3RunViewModel.saveResult()` with a real Room insert, and read it back reliably (guid, timestamps, and numeric results survive a round trip).

CSV export (the `TODO("Export as CSV")` branch already in `saveResult()`) and a results-history/browse screen are **explicitly out of scope for phase 1** — see "Phasing" below. Getting one row saved and re-readable end-to-end on all three targets is the hard part; export/browsing are then straightforward additions.

## What's already scaffolded (read this before writing new code)

This isn't a green field — someone already sketched the shape of this feature and then backed it out, leaving useful signal:

- `model/ResultData.kt` — the target entity's fields are fully designed (guid, start/end times, test inputs, human-readable + raw-bps results). `@Entity(tableName="result_data")`, `@PrimaryKey`, and `@TypeConverters(UuidTypeConverter::class)` are present as **comments**, referencing a `UuidTypeConverter` that doesn't exist yet.
- `viewmodel/Iperf3RunViewModel.kt`:
  - Commented imports for `database.ResultDataRepository` and `database.ResultDatabase` — i.e. the intended package is `.../database/`, class names `ResultDatabase` and `ResultDataRepository`.
  - Commented `init{}` block builds `Room.databaseBuilder(application, ResultDatabase::class.java, "result_data-db")` — Android-only, pre-KMP-driver style (won't work as-is; see driver notes below).
  - `saveResult()` already has the full save *shape*: guards on `isSaved`/`totalSamples > 0`, sets `isSaving = true`, (stub) `delay(3.seconds)`, commented `resultDatabase.resultDataDao().insert(current)`, then `isSaved = true`. Real implementation is a near-mechanical swap of the delay for a suspend DAO call.
  - Second click on Save (`isSaved == true`) is meant to trigger `TODO("Export as CSV")` — confirms Save→Export is the intended UX, matching `SaveButton.kt`'s state machine (`isSaved && isFinished` → button relabels to "Export").
  - `UiExecutionData.isDatabaseCreated: Boolean` already exists as a field, currently always `false` — clearly meant to gate UI on DB readiness.
- `model/Iperf3RunningState.kt` has a `// db keys` comment above `guid`/`startTimeMillis`, confirming this was always meant to be the persisted run identity.

Bottom line: don't re-derive the schema or the save-button UX — both are already decided. The actual work is wiring, type conversion, and per-platform driver setup.

## Key design decisions

### 1. Entity ≠ domain model — don't annotate `ResultData` directly

The commented-out `@Entity`/`@PrimaryKey` on `ResultData` is the naive path, but `ResultData` has fields Room can't store as columns without help:

- `guid: AppUUID`, `startDateTime`/`endDateTime: AppLocalDateTime` — these are `expect`/`actual` **interfaces** (`Platform.kt`), not concrete types. Worse, `getAppUUID()`/`getAppLocalDateTime()` always *generate a new value* (`AndroidUUID()` calls `java.util.UUID.randomUUID()` in its constructor) — there is currently no `actual` function to reconstruct an `AppUUID`/`AppLocalDateTime` from a stored string. A `@TypeConverter` that only knows how to serialize, not deserialize, is broken.
- `max`/`min`/`avg`/`median`/`standardDeviation: UnitConvertedData` — a `(value: Double, unit: String)` pair, and largely *redundant* with the raw `maxbps`/`minbps`/`avgbps`/`medianbps`/`stdDevbps: Long` fields already on the same class (`UnitConverter.toHumanUnit()` derives one from the other).

**Recommendation:** define a separate Room `@Entity` (`ResultDataEntity` in the new `database` package) with only primitive/String/Long columns — `guid: String`, `startTimeMillis/endTimeMillis: Long`, `startDateTime/endDateTime: String`, the raw `*bps: Long` fields, plus the small set of input params (`direction`, `duration`, `parallelStreams`, `skip`, host details as `String`). Drop the `UnitConvertedData` columns entirely from the entity — recompute them from the raw bps `Long` via `UnitConverter.toHumanUnit()` when displaying a saved row. Add `ResultData.toEntity()` / `ResultDataEntity.toDomain()` mapper functions (`database/ResultDataMappers.kt`) instead of `@TypeConverters`.

This needs **zero** new `TypeConverter`s and zero changes to the `AppUUID`/`AppLocalDateTime` expect/actual surface — mapping happens once, in one file, using `.name` (both interfaces already expose `val name: String`).

### 2. SQLite driver per platform (the codelab's proven pattern, plus a fourth platform)

The codelab covers Android + iOS only; this project also has a JVM desktop target (`:desktopApp`, `jvm()` in `shared/build.gradle.kts`), so there's a third driver to add beyond the codelab's two:

| Platform | Driver | Artifact |
|---|---|---|
| Android | `AndroidSQLiteDriver()` | `androidx.sqlite:sqlite-framework-android` |
| iOS | `NativeSQLiteDriver()` | `androidx.sqlite:sqlite-framework` (cinterop, uses OS-native sqlite3) |
| Desktop (JVM) | `BundledSQLiteDriver()` | `androidx.sqlite:sqlite-bundled` |

`commonMain` gets `expect fun createSQLiteDriver(): SQLiteDriver` (mirrors the existing `expect fun getPlatform()`/`getAppUUID()` pattern in `Platform.kt` — this codebase already has the muscle memory for this). `sqlite-bundled` statically links its own SQLite for JVM/Native rather than shelling out to the OS, so no extra desktop packaging step should be needed (unlike the hand-rolled per-OS/arch native lib build `shared/build.gradle.kts` already does for `native_format` — this is *not* the same kind of problem, don't reuse that machinery).

**Open risk to verify early** (see "Risks" below): confirm the exact Room/`androidx-sqlite` version pin actually publishes a working `jvm()` target — the codelab's pinned versions (Room 2.8.4, sqlite 2.7.0) were only exercised against Android + iOS there.

### 3. Android `Context` — this app has no DI container to lean on

The codelab's Android driver setup takes a `Context` via Hilt (`@ApplicationContext`). This project has **no DI framework** — `App()` is called with zero arguments on all three platforms (`MainActivity.kt`, `main.kt`, `MainViewController.kt`), and `Iperf3RunViewModel` is created via `viewModel { Iperf3RunViewModel() }` with no factory/params. Room's Android driver needs a `Context` to open a database file, so something has to supply one.

**Recommendation:** add one explicit line to `MainActivity.onCreate()` (Android only) that stashes `applicationContext` in a small androidMain-only singleton (e.g. `AndroidAppContextHolder`) before `setContent { App() }` runs. This is simpler and more transparent than pulling in `androidx.startup` for auto-initialization (which would require adding a `provider` entry to an `AndroidManifest.xml` inside `:shared`, which doesn't currently exist), and it costs one line in exactly one file. iOS and Desktop don't need this at all — they open a plain file path.

### 4. Migrations — skip them for now, be explicit about it

This is a research/coursework project, not a shipped app with real user data to protect across schema changes. Use `.fallbackToDestructiveMigration(dropAllTables = true)` (or equivalent) on the `RoomDatabase.Builder`, and say so in a comment. Do not hand-write `Migration` objects until there's an actual reason to (e.g. a graded milestone that requires it, or real on-device data worth preserving across a schema change). Revisit if that changes.

### 5. Schema export

Follow the codelab's convention: `room { schemaDirectory("$projectDir/schemas") }` in `shared/build.gradle.kts`, commit the generated JSON under `shared/schemas/`. Cheap, and gives a diffable record of schema changes even without migrations.

## Gradle / dependency changes

None of this exists in the project yet — this is a from-scratch addition, not an upgrade. Current `gradle/libs.versions.toml` has no `ksp`, `room`, or `sqlite` entries at all, and `settings.gradle.kts`'s `pluginManagement` repos (`google()`, `mavenCentral()`, `gradlePluginPortal()`) already cover everything needed — no repo changes required.

**`gradle/libs.versions.toml`** — add:
```toml
[versions]
ksp = "<version matching Kotlin 2.4.0>"
androidx-room = "2.8.4"          # or later; verify jvm() target support first, see Risks
androidx-sqlite = "2.7.0"        # or later

[libraries]
androidx-room-runtime = { module = "androidx.room:room-runtime", version.ref = "androidx-room" }
androidx-room-compiler = { module = "androidx.room:room-compiler", version.ref = "androidx-room" }
androidx-sqlite-framework = { module = "androidx.sqlite:sqlite-framework", version.ref = "androidx-sqlite" }
androidx-sqlite-framework-android = { module = "androidx.sqlite:sqlite-framework-android", version.ref = "androidx-sqlite" }
androidx-sqlite-bundled = { module = "androidx.sqlite:sqlite-bundled", version.ref = "androidx-sqlite" }

[plugins]
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
androidxRoom = { id = "androidx.room", version.ref = "androidx-room" }
```

**`shared/build.gradle.kts`**:
- Apply `alias(libs.plugins.ksp)` and `alias(libs.plugins.androidxRoom)`.
- Add `kotlin { jvmToolchain(17) }` at the top level of the `kotlin {}` block — the codelab hit a Kotlin-2.4.10-defaults-to-newer-bytecode-than-installed-JDK problem (pitfall #2 in `ROOMDB_MIGRATE_EXAMPLE.md`); this project is on Kotlin 2.4.0, close enough that the same class of mismatch is worth guarding against proactively rather than debugging it later.
- `room { schemaDirectory("$projectDir/schemas") }`.
- `commonMain.dependencies`: `androidx-room-runtime`, `androidx-sqlite-framework`.
- `androidMain.dependencies`: `androidx-sqlite-framework-android`.
- `jvmMain.dependencies` (new): `androidx-sqlite-bundled`.
- KSP processor wiring — per the codelab's pitfall #4, Room's KSP compiler must be registered per target: `add("kspAndroid", libs.androidx.room.compiler)`, `add("kspIosArm64", ...)`, `add("kspIosSimulatorArm64", ...)`, and `add("kspJvm", ...)` (this project's fourth target the codelab didn't have).
- iOS framework blocks (`iosArm64`/`iosSimulatorArm64` `binaries.framework {}`): add `linkerOpts.add("-lsqlite3")` — **`AGENTS.md` already documents this exact requirement** in the "iOS target policy" gotcha, written in anticipation of this work. `sqlite-framework`'s cinterop declares the sqlite3 C API but doesn't bundle an implementation.

**`androidApp/build.gradle.kts`**: add `implementation(libs.androidx.room.runtime)` and `ksp(libs.androidx.room.compiler)` — needed transitively for KSP codegen at the app level (codelab pitfall #4 again).

**Correction (found during implementation): `iosApp/*.xcodeproj` DOES need a change beyond `ARCHS = arm64`.** The Gradle-side `linkerOpts.add("-lsqlite3")` only resolves symbols when Gradle links `Shared.framework` itself — but that framework is built `isStatic = true`, so it's just an object-file archive with unresolved externals until Xcode's own final app-link step, which needs its own `-lsqlite3`. Without it, `xcodebuild` fails at the `Ld .../iperf3KMP.app/iperf3KMP.debug.dylib` step with `Undefined symbols for architecture arm64: "_sqlite3_bind_blob"` etc. — `:shared:compileKotlinIosArm64` succeeds regardless, since that's a Gradle-only step that never reaches Xcode's link phase, so this is easy to miss without an actual `xcodebuild`/Xcode run. Fix: added `OTHER_LDFLAGS = ("$(inherited)", "-lsqlite3")` to the `iosApp` **target's** two build configurations in `project.pbxproj` (not the project-level ones — those don't govern the target's link flags). Verified via `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator build` → `BUILD SUCCEEDED`.

## New files (package: `com.jabramson.iperf3_network_tester_kmp.database`, matching the commented-out import path already in `Iperf3RunViewModel.kt`)

**Status: built and building clean on all three targets.** Actual names/shapes drifted slightly from the original plan below — noted inline.

- `commonMain/.../database/ResultDataEntity.kt` — the `@Entity` (see decision #1). ✅ as planned.
- `commonMain/.../database/ResultDataEntityDao.kt` (named `ResultDataEntityDao`, not `ResultDataDao` as originally planned) — `@Dao` interface, **every method `suspend`** (`insert`, `delete`, `update`, `count`, `getById`, `getByIds`, `getAll`, `deleteAll`, `deleteById`, `getByDateRange`, `getRecent`). This ended up broader than the plan's minimal `insert`/`getAll`/`getByGuid` sketch. Important gotcha hit here: Room only permits non-suspend blocking DAO methods on Android — `jvm()`/iOS require `suspend` on every method or KSP fails (`Only suspend functions are allowed in DAOs declared in source sets targeting non-Android platforms`). The DAO was first written with plain blocking `fun`s, which compiled fine on Android and only broke when `kspKotlinJvm`/`kspKotlinIosArm64` ran — worth remembering if a DAO method is ever added without `suspend` again.
- `commonMain/.../database/ResultDatabase.kt` — `@Database(entities = [ResultDataEntity::class], version = 6)` abstract class (version bumped past 1 during development), `abstract fun resultDataEntityDao(): ResultDataEntityDao`, `@ConstructedBy(ResultDatabaseConstructor::class)` + the matching `expect object ResultDatabaseConstructor` (required by Room's KSP-generated multiplatform constructor pattern — not explicitly called out in the original plan, but needed).
- `commonMain/.../database/Database.kt` — `expect fun createSQLiteDriver(): SQLiteDriver`, `expect fun getResultDatabase(): ResultDatabase`, common `getRoomDatabase(builder: RoomDatabase.Builder<ResultDatabase>): ResultDatabase` helper. One deviation from plan: uses `.setQueryCoroutineContext(Dispatchers.Default)`, not `Dispatchers.IO` — see the `Dispatchers.IO` gotcha below.
- `commonMain/.../database/ResultDataMappers.kt` — top-level `toEntity(iperf3RunningState: Iperf3RunningState): ResultDataEntity` and `toDomain(e: ResultDataEntity): Iperf3RunningState` functions (not extension functions as originally sketched — functional difference is cosmetic). **`toDomain()` is currently a stub returning `Iperf3RunningState()` unpopulated** — fine for now since nothing reads results back yet (no history screen), but will need real field mapping before Phase 2's history screen can show anything. Bug fixed here: `toEntity()` originally called `.toString()` on `AppUUID`/`AppLocalDateTime` values (`iperf3RunningState.guid.toString()`, `iperf3RunningState.startDateTime.toString()`, `getAppLocalDateTime().toString()`) — those interfaces (`Platform.kt`) only declare `val name: String`, no `toString()` override, so `.toString()` silently fell back to `Any`'s default (`ClassName@hashcode`) instead of the actual UUID/timestamp string. Every saved row would have gotten a garbage, non-unique `guid` and unparseable date strings. Fixed by using `.name` instead of `.toString()` in all three spots.
- `commonMain/.../database/ResultDataRepository.kt` — thin suspend wrapper over the DAO, as planned. One fix made here: the constructor originally had two **eager, non-suspend** `val allResults`/`val count` properties that called the (now-suspend) DAO methods directly in the constructor body — this doesn't compile once the DAO methods are suspend, so they became `suspend fun getAllResults()`/`suspend fun getCount()` instead.
- `androidMain/.../database/Database.android.kt` — `actual fun createSQLiteDriver() = AndroidSQLiteDriver()`, `actual fun getResultDatabase()` delegating to a private `AndroidResultDatabaseHolder` object with a `by lazy` instance (built from `AndroidAppContextHolder.requireContext()` + `context.getDatabasePath("${ResultDatabase.DBNAME}.db")`) — matches the plan's intent, named slightly differently (`requireContext()` not `get()`).
- `androidMain/.../AndroidAppContextHolder.kt` — as planned, plus one line added in `MainActivity.onCreate()`: `AndroidAppContextHolder.init(applicationContext)` before `setContent { App() }`.
- `iosMain/.../database/Database.ios.kt` — `actual fun createSQLiteDriver() = NativeSQLiteDriver()`, `actual fun getResultDatabase()` delegating to a private `IosResultDatabaseHolder` with a `by lazy` instance, document directory resolved via `NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, expandTilde = true)` (the plan referenced a `documentDirectory()` helper from the codelab but never showed its implementation, so this was written fresh).
- `jvmMain/.../database/Database.jvm.kt` — `actual fun createSQLiteDriver() = BundledSQLiteDriver()` (import: `androidx.sqlite.driver.bundled.BundledSQLiteDriver` — verified against the actual jar contents, since this exact package path isn't obvious from the artifact name), `actual fun getResultDatabase()` via a `by lazy` `JvmResultDatabaseHolder`, DB file at `~/.iperf3kmp/result_data-db.db` as planned.

All the per-platform `getResultDatabase()` actuals cache their instance behind `by lazy` — the database is constructed exactly once per process regardless of how many times it's accessed, satisfying "don't recreate it if it already exists."

## ViewModel/UI wiring

**Status: done**, using a **third option** beyond the two the plan originally weighed:

- Neither constructor injection (touching all three `viewModel { ... }` call sites) nor an `expect object ...Provider` was used. Instead: `commonMain/.../database/Database.kt` exposes `expect fun getResultDatabase(): ResultDatabase` (see "New files" above), and `Iperf3RunViewModel` reaches it via `private val resultDataRepository: ResultDataRepository by lazy { ResultDataRepository(getResultDatabase().resultDataEntityDao()) }`. This touches **zero** of the three platform entry points beyond the one-line `AndroidAppContextHolder.init(...)` call already needed in `MainActivity`, and still guarantees single construction via Kotlin's `by lazy`. Trade-off versus the plan's constructor-injection recommendation: the repository isn't swappable for a test double via the constructor, so a future test would need to either use a real (in-memory) database or introduce a seam later — acceptable for now given no ViewModel tests exist yet.
- `saveResult()`'s `delay(3.seconds)` stub is gone, replaced with `resultDataRepository.insert(current)` inside the existing `isSaving = true` / `isSaved = true` bracketing, wrapped in try/catch — on success `isSaved = true, isSaving = false, isDatabaseCreated = true`; on failure `isSaving = false` and the error is appended to `errorLines` (previously any failure would have been silently swallowed — now at least visible in the UI's error output).
- `TODO("Export as CSV")` left as-is — Phase 2.

## Testing

**Status: not started — this is the main gap remaining before Phase 1 is fully "done" per this plan's own bar.**

- `androidHostTest` — unlike the JNI-based `StringFormatterTest` (excluded from `androidHostTest` because Robolectric can't load a real `.so`), Room's `AndroidSQLiteDriver` is pure JVM/framework code that Robolectric *does* support. A Room round-trip test should be able to run here — worth confirming early since it'd be the fastest test loop for this feature.
- `jvmTest` — exercise `BundledSQLiteDriver` directly, in-memory (`Room.inMemoryDatabaseBuilder`) or a temp file.
- `iosSimulatorArm64Test` — exercise `NativeSQLiteDriver`; this is also where the `-lsqlite3` linker flag first gets proven out (it'll fail to link before it fails to run, if missing).
- No `commonTest` DB test — the driver is platform-specific by construction, so a shared-source test can't instantiate one without an `actual`.
- Minimum bar for "done": insert a `ResultDataEntity` built from a real `ResultData` (via `createResultData()`/`toEntity()` from a populated `Iperf3RunningState`), read it back by guid, assert field equality — run on all three test targets. Not yet written. Also blocked in spirit (not literally) by `ResultDataMappers.toDomain()` still being a stub — a round-trip test can assert on the `ResultDataEntity` fields directly without needing `toDomain()`, but `toDomain()` will need real field mapping before this is useful beyond a raw entity-equality check.
- Also not yet manually verified: that a saved row survives an actual app restart on any platform (only "insert succeeds without throwing, `isSaved` flips true" has been confirmed via `:desktopApp:run` launching cleanly — no click-through save was performed).

## Phasing

1. **Phase 1 (this plan's actual scope): done, manually verified.** Schema, drivers, repository, and `saveResult()` wiring to a real insert are all in place, compiling/running on all three targets, and confirmed by actually clicking Save on Android, iOS, and desktop and inspecting each platform's real `.db` file afterward (DB Browser for SQLite) — the row is there with correct data. **Remaining, not blocking Phase 1 but worth doing before calling this fully closed out:** an automated round-trip read-back test (`androidHostTest`/`jvmTest`/`iosSimulatorArm64Test` — see Testing above), since right now nothing guards against a future regression the way manual testing just did.
2. **Phase 2 (follow-on, not started here):** CSV export (`TODO` already in code), and/or a results-history list screen backed by `resultDataEntityDao().getAll()`. Both are straightforward once phase 1's plumbing exists — no new architectural decisions needed, just new UI (and finishing `ResultDataMappers.toDomain()`, which the history screen would actually need).

## Risks / open questions to resolve early (spike before committing to the full plan)

All five items below have been spiked against the actual Gradle wiring (`gradle/libs.versions.toml`, `shared/build.gradle.kts`, `androidApp/build.gradle.kts`) and verified via real builds on all three targets — `./gradlew :androidApp:assembleDebug`, `:shared:compileKotlinIosArm64`/`:shared:compileKotlinIosSimulatorArm64`, and `:desktopApp:build`/`:desktopApp:run` all succeed, each triggering its own `ksp*` codegen task.

1. **RESOLVED — jvm() desktop target works, with a caveat.** Room 2.8.4 + `androidx-sqlite` 2.7.0 support `jvm()` fine, but only if `androidx-sqlite-framework` is kept **out of `commonMain`**: that artifact only publishes Android + Apple/Native variants (it wraps each platform's *native* SQLite framework — JVM has none to wrap, so no `jvm` variant exists). The `SQLiteDriver` interface `commonMain` needs comes in transitively via `androidx-room-runtime` → the base `androidx.sqlite:sqlite` module, which does publish `jvm`. Original attempt put `sqlite-framework` in `commonMain` (per the codelab, which never built a `jvm()` target) and broke `:desktopApp:build` with `KMP Dependencies Resolution Failure ... Unresolved platforms: [jvm]` — `:androidApp:assembleDebug`/`:shared:tasks` succeeded regardless since neither forces full `jvm` variant resolution, so the break was easy to miss without running `:desktopApp:build` directly. Fix: `sqlite-framework` moved to `iosArm64Main`/`iosSimulatorArm64Main` explicitly (added to each — see the note in `CLAUDE.md`'s `:shared` module entry on why the `iosMain` name can't be targeted directly in this build script), `androidx-sqlite-framework-android` stays in `androidMain`, `androidx-sqlite-bundled` stays in `jvmMain`.
2. **RESOLVED — `com.android.kotlin.multiplatform.library` + KSP/Room works.** `kspAndroid`, `kspIosArm64`, `kspIosSimulatorArm64`, and `kspJvm` all run and generate code correctly under this project's combined AGP plugin; no issues observed.
3. **RESOLVED — not actually a discrepancy.** `gradle/libs.versions.toml` already had `android-minSdk = "27"` (matching the codelab) before this work started; the "minSdk 26" figure in earlier docs was stale. No `libs.versions.toml` change was needed.
4. **RESOLVED — no regression.** `jvmToolchain(17)` alongside the existing `androidRuntimeClasspath(libs.compose.uiTooling)` and per-target `compilerOptions { jvmTarget = JvmTarget.JVM_11 }` builds cleanly on Android; no conflict observed.
5. **RESOLVED — `ksp = "2.3.6"`** is the version pinned in `gradle/libs.versions.toml` for Kotlin 2.4.0; confirmed working across all four `ksp*` codegen tasks.
