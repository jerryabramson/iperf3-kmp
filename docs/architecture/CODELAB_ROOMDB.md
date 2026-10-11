# ROOMDB.md

## Repository context

This document was written using a codelab exercise to convert a Room-based Android app to KMP.

This directory (`migrate-room/`) is one codelab sample inside the `codelab-android-kmp` monorepo (sibling: `../get-started/`). It is **not** its own git root — the git root is one level up. It is the starter/working project for the [Migrate Room To KMP codelab](https://developer.android.com/codelabs/kmp-migrate-room): a "Fruitties" shopping app that starts as an Android app (Room + CoreData duplicated per-platform) and is migrated so the Room database is shared between Android and iOS via Kotlin Multiplatform.

This file consolidates two documents from that migration: the codelab's own repo notes, and a separate writeup of the specific fixes applied to get the KMP Room setup building. Read the "Known pitfalls" and "Room + KMP SQLite driver pattern" sections below before touching database/driver wiring elsewhere — they cover several non-obvious issues.

All Gradle commands below must be run from this directory (`migrate-room/`), which contains its own `gradlew`.

## Problem statement

The original project had several issues preventing it from building as a KMP module:

1. **Apple dependency resolution error**: `sqlite-bundled` (Android-only) was declared in `commonMain`, causing Apple source sets to fail resolving the dependency.
2. **Java version mismatch**: Kotlin 2.4.10 compiled with Java 22 bytecode (version 66), but the system had Java 17 (version 61).
3. **Package structure issues**: Model classes were incorrectly placed in `android.model` instead of `shared.model`.
4. **Missing platform-specific SQLite drivers**: no KMP-compatible driver setup for iOS.

The key insight is that Room supports Kotlin Multiplatform through the **SQLite Driver API** introduced in Room 2.7+. Instead of Android-only dependencies, the fix uses:

| Platform | Dependency | Driver class |
|----------|-----------|--------------|
| `commonMain` | `androidx.sqlite:sqlite-framework` | KMP interfaces (`SQLiteDriver`) |
| Android | `androidx.sqlite:sqlite-framework-android` | `AndroidSQLiteDriver` |
| iOS | (included in `sqlite-framework`) | `NativeSQLiteDriver` |

## Build, lint, test

```bash
# Full debug build + lint (what CI runs)
./gradlew assembleDebug lintDebug --stacktrace

# Spotless formatting check / auto-fix (ktlint + license headers; CI runs spotlessCheck)
./gradlew spotlessCheck
./gradlew spotlessApply

# Unit tests (androidApp JVM tests)
./gradlew :androidApp:testDebugUnitTest
./gradlew :androidApp:testDebugUnitTest --tests "com.example.fruitties.kmptutorial.android.repository.DefaultFruittieRepositoryTest"

# Shared module tests
./gradlew :shared:testDebugUnitTest          # androidHostTest
./gradlew :shared:connectedAndroidTest       # androidDeviceTest, needs an emulator/device

# Instrumented Android tests (androidApp/src/androidTest, needs an emulator/device)
./gradlew :androidApp:connectedDebugAndroidTest
```

**Build verification** after a driver/dependency change:

```bash
cd migrate-room
./gradlew clean :androidApp:assembleDebug
```

Expected output: `BUILD SUCCESSFUL`.

iOS: open `iosApp/Fruitties.xcodeproj` in Xcode (or `xcodebuild -scheme Fruitties -sdk iphonesimulator build` from `iosApp/`). Requires Xcode ≥16. CI builds it with `mxcl/xcodebuild` targeting the `Fruitties` scheme.

CI (`.github/workflows/migrate-room.yml`) only triggers on changes under `migrate-room/**`, runs `spotlessCheck` then `assembleDebug lintDebug` for Android, and a separate macOS job builds the iOS app.

Room schema JSON files live under `shared/schemas/` (configured via `room { schemaDirectory(...) }` in `shared/build.gradle.kts`) — these are generated/checked artifacts, keep them in sync when changing `@Entity`/`@Database` definitions rather than hand-editing.

## Module architecture

Three modules, reflecting the KMP migration in progress:

- **`shared/`** — Kotlin Multiplatform library (`androidKotlinMultiplatformLibrary` + `kotlinMultiplatform` plugins), targets `android`, `iosArm64`, `iosSimulatorArm64`. `iosX64` (Intel simulator) is deliberately not declared — this is an intentional ARM-only policy, not a temporary gap: `sqlite-framework` 2.7.0+ doesn't publish `ios_x64`/Intel-Apple klib variants at all, and dropping x86_64 avoids that whole class of problem. Owns the Room database: entities/DAOs/`AppDatabase` in `commonMain`, with platform-specific SQLite driver wiring in `androidMain`/`iosMain`. Exposed to iOS as the `sharedKit` XCFramework (via SKIE for better Swift interop of Kotlin coroutines/flows).
- **`androidApp/`** — the Android application. Uses Hilt for DI (`di/` package: `DatabaseModule`, `BindsModule`, `NetworkModule`, `CoroutineScopeModule`), Jetpack Compose UI, Ktor for networking. Depends on `shared` for the database and domain models.
- **`iosApp/`** — native SwiftUI app (`iosApp/Sources/`), manually wired DI via `AppContainer.swift` (no DI framework). At present it still uses **CoreData** (`Database/DataController.swift`, `Fruitties.xcdatamodeld`) rather than the shared Room database — wiring `iosApp` to consume `shared`'s `AppDatabase` instead of CoreData is expected follow-up work of this codelab, not yet done.

### Package layout

Shared domain code lives under `com.example.fruitties.kmptutorial.shared` (models in `shared.model`, DB in `shared.database`). Android-only code lives under `com.example.fruitties.kmptutorial.android`. **Do not put model/database classes back under `android.*`** — mixing the two package roots is exactly the bug the migration fixed (see pitfall #3 below) and causes cascading import errors between `androidApp` and `shared`.

Migration step: model classes moved from `com.example.fruitties.kmptutorial.android.model` to `com.example.fruitties.kmptutorial.shared.model` (`Fruittie.kt`, `CartItem.kt`), with all imports in DAOs, repositories, and UI code updated accordingly.

## Changes made to reach a working KMP Room setup

### 1. Dependencies (`gradle/libs.versions.toml`)

```toml
[versions]
androidx-sqlite = "2.7.0"

[libraries]
androidx-sqlite-framework = { module = "androidx.sqlite:sqlite-framework", version.ref = "androidx-sqlite" }
androidx-sqlite-framework-android = { module = "androidx.sqlite:sqlite-framework-android", version.ref = "androidx-sqlite" }
```

### 2. Shared module build configuration (`shared/build.gradle.kts`)

Added JVM toolchain (Kotlin 2.4.10 defaulted to newer bytecode than the installed JDK):

```kotlin
kotlin {
    jvmToolchain(17)
    // ... targets
}
```

Updated source sets:

```kotlin
sourceSets {
    commonMain {
        dependencies {
            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.sqlite.framework)  // KMP-compatible
        }
    }

    androidMain {
        dependencies {
            implementation(libs.androidx.sqlite.framework.android)  // Android driver
        }
    }
}
```

### 3. Room + KMP SQLite driver pattern (expect/actual)

Room's KMP support (2.7+) requires an explicit `SQLiteDriver` per platform instead of the Android-only `sqlite-bundled` artifact.

**commonMain** (`shared/src/commonMain/kotlin/com/example/fruitties/kmptutorial/shared/Database.kt`):

```kotlin
expect fun createSQLiteDriver(): androidx.sqlite.SQLiteDriver

fun getRoomDatabase(
    builder: RoomDatabase.Builder<AppDatabase>,
): AppDatabase {
    return builder.setDriver(createSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
}
```

**androidMain** (`shared/src/androidMain/kotlin/com/example/fruitties/kmptutorial/shared/SQLiteDriver.android.kt`):

```kotlin
package com.example.fruitties.kmptutorial.shared

import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.AndroidSQLiteDriver

actual fun createSQLiteDriver(): SQLiteDriver {
    return AndroidSQLiteDriver()
}
```

Android database setup (`shared/src/androidMain/kotlin/com/example/fruitties/kmptutorial/android/database/database.kt`):

```kotlin
fun database(context: Context): AppDatabase {
    val dbFile = context.getDatabasePath("sharedfruits.db")
    return Room.databaseBuilder<AppDatabase>(context, dbFile.absolutePath)
        .setDriver(createSQLiteDriver())  // Uses AndroidSQLiteDriver
        .build()
}
```

**iosMain** (`shared/src/iosMain/kotlin/com/example/fruitties/kmptutorial/shared/database.kt`):

```kotlin
package com.example.fruitties.kmptutorial.shared

import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.NativeSQLiteDriver

actual fun createSQLiteDriver(): SQLiteDriver {
    return NativeSQLiteDriver()
}
```

iOS database setup (`shared/src/iosMain/kotlin/com/example/fruitties/kmptutorial/shared/AppDatabase.ios.kt`):

```kotlin
fun getPersistentDatabase(): AppDatabase {
    val dbFilePath = documentDirectory() + "/" + "fruits.db"
    return Room.databaseBuilder<AppDatabase>(name = dbFilePath)
        .setDriver(NativeSQLiteDriver())
        .build()
}
```

Dependency split: `sqlite-framework` (KMP interfaces, resolves on all platforms) goes in `commonMain`; `sqlite-framework-android` (the Android driver impl) goes only in `androidMain`. **Never put an Android-only sqlite artifact (`sqlite-bundled`, `sqlite-framework-android`) in `commonMain`** — it will fail to resolve for the iOS source sets.

#### Why `sqlite-framework` instead of `sqlite-bundled`?

| Artifact | Android | iOS | Description |
|----------|---------|-----|-------------|
| `sqlite-bundled` | Yes | No | SQLite compiled from source (Android only) |
| `sqlite-framework` | Yes | Yes | Uses OS-native SQLite (KMP-compatible) |
| `sqlite-framework-android` | Yes | N/A | Android-specific driver implementation |

#### Source set hierarchy for dependencies

```
commonMain          → sqlite-framework (interfaces + iOS driver)
├── androidMain     → sqlite-framework-android (Android driver)
└── iosMain         → NativeSQLiteDriver (from sqlite-framework)
```

### 4. Hilt dependency injection (`androidApp/src/main/kotlin/.../di/DatabaseModule.kt`)

Uncommented the Hilt module to provide the database and DAOs:

```kotlin
@Module
@InstallIn(SingletonComponent::class)
internal object DatabaseModule {
    @Provides
    @Singleton
    fun providesAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return database(context)
    }

    @Provides
    fun providesFruittieDao(appDatabase: AppDatabase) = appDatabase.fruittieDao()

    @Provides
    fun providesCartDao(appDatabase: AppDatabase) = appDatabase.cartDao()
}
```

### 5. Android app dependencies (`androidApp/build.gradle.kts`)

Room dependencies come transitively from `shared` but are also needed directly for KSP codegen at the app level:

```kotlin
dependencies {
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
}
```

## iOS Xcode build (Room/sqlite-specific)

Getting `iosApp` to actually build in Xcode against the `shared` framework required two fixes beyond dropping the `iosX64` target:

1. **`EXCLUDED_ARCHS[sdk=iphonesimulator*] = x86_64`** is set on the Xcode project's Debug/Release build settings (`iosApp/Fruitties.xcodeproj/project.pbxproj`). Without it, Xcode requests a universal (`arm64 x86_64`) simulator build regardless of `ONLY_ACTIVE_ARCH` when building for a generic/non-booted simulator destination, and the KMP Gradle plugin's `validateArchitecturesForEmbedAndSignAppleFrameworkForXcode` task fails because `iosX64` isn't a declared target.
2. **`linkerOpts.add("-lsqlite3")`** is set on both the `iosArm64` and `iosSimulatorArm64` framework blocks in `shared/build.gradle.kts`. `sqlite-framework`'s cinterop declares the `sqlite3` C API but doesn't bundle an implementation — the final binary must link the OS's `libsqlite3`. Without this, linking fails with "Undefined symbols for architecture arm64" for `sqlite3_bind_*`/`sqlite3_stmt_busy`/etc.

If iOS build/link errors resurface (e.g. after a further dependency bump), reproduce directly rather than guessing from Xcode's generic "ld invocation reported errors" message: run `./gradlew :shared:embedAndSignAppleFrameworkForXcode --stacktrace` with `CONFIGURATION`, `PLATFORM_NAME`, `SDK_NAME`, `ARCHS`, `BUILT_PRODUCTS_DIR`, `TARGET_BUILD_DIR`, and `FRAMEWORKS_FOLDER_PATH` env vars set to mirror what Xcode's "Compile Kotlin Multiplatform" run-script build phase passes — the real error is in that Gradle output, not in Xcode's summarized failure.

## Known pitfalls

1. Android-only deps in `commonMain` break iOS dependency resolution — always check which source set a dependency belongs in (`sqlite-bundled`/`sqlite-framework-android` are Android-only; `sqlite-framework` is the KMP-safe one).
2. `shared/build.gradle.kts` needs `kotlin { jvmToolchain(17) }` explicitly — Kotlin's default target bytecode version can outrun the system JDK (Kotlin 2.4.10 vs. a Java 17 system JDK was the concrete failure here).
3. Keep `shared.model`/`shared.database` and `android.*` packages separate; don't reintroduce `android.model` — mixing the two causes cascading import errors between `androidApp` and `shared`.
4. KSP needs the Room compiler applied on **both** `shared` (`kspAndroid`/`kspIosArm64`/`kspIosSimulatorArm64`) and `androidApp` for code generation to work on each module.
5. Bumping `androidx-sqlite` doesn't restore `iosX64` — this project intentionally stays ARM-only (`iosArm64` + `iosSimulatorArm64`); see the Xcode build note above for the two settings this requires.
6. `iosApp`'s Swift repository code (`CartRepository.swift`, `FruittieRepository.swift`) still targets **CoreData**-generated model types, not the shared Room `AppDatabase` — expect Swift compile errors there (`value of type 'Fruittie' has no member 'name'`, etc.) until `iosApp` is rewired off CoreData. This is unrelated to Room/sqlite build config and is the expected next step of the migration, not a regression.

## Dependency versions

Central catalog: `gradle/libs.versions.toml`. Notable pins: Kotlin 2.4.10, AGP 9.1.1, Room 2.8.4 (`androidx-room`), SQLite **2.7.0** (`androidx-sqlite`; the `sqlite-framework*` artifacts used by the KMP driver setup ride this version — chosen deliberately, publishes `ios_arm64`/`ios_simulator_arm64` klibs but no `ios_x64`), Hilt 2.60.1, Ktor 3.5.1, min/target/compile SDK 27/37/37.

## Formatting

Spotless enforces ktlint (`gradle/../spotless`) and Apache 2.0 license headers (`spotless/copyright.kt`, `spotless/copyright.xml`) on all `.kt`, `.gradle.kts`, and `.xml` files across every subproject — new files need the license header or `spotlessCheck` fails in CI.

## References

- [Room KMP Documentation](https://developer.android.com/kotlin/multiplatform/room)
- [SQLite KMP Documentation](https://developer.android.com/kotlin/multiplatform/sqlite)
- [Kotlin Multiplatform Source Sets](https://kotlinlang.org/docs/multiplatform-hierarchy.html)
- [Migrate Room To KMP codelab](https://developer.android.com/codelabs/kmp-migrate-room)
