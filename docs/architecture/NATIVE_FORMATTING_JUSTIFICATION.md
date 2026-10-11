# Justification: Native C String Formatting for KMP iperf3 Project

## Problem Statement

The `Iperf3OutputMonitor` class parses raw iperf3 output lines that use fixed-width, column-aligned formatting (e.g., `%12.12s %4.4s %-9.9s %10.10s`). These format strings require:
- Field width specification with padding/truncation
- Left/right alignment control (`%-`)
- Decimal precision for floating-point values (`%.2f`, `%5.2d`)

Kotlin's common library does not provide `String.format()` or equivalent printf-style formatting — these APIs are JVM/Android-specific and unavailable on iOS/native targets.

## Design: One C Function, Reached Natively From Every Target

Rather than reimplementing printf in Kotlin, or letting each platform format independently through its own library (`java.lang.String.format`, `NSString.stringWithFormat:`), there is exactly one implementation of the formatting logic: `format_string()` in [`native/native_format.h`](../../native/native_format.h). Every platform calls into *that same function* through its native interop mechanism:

```
                    native/native_format.h
                    format_string() — static inline, single source of truth
                    /            |             \
              JNI (Android)   JNI (desktop)   cinterop (iOS)
                    |               |               |
         :nativeformat module   shared/build    native_format.def
         CMake + NDK build      .gradle.kts     (headers only, no link step)
                    |          compileNativeFormatJvm
              androidMain          |               |
            NativeFormatterBridge.kt          jvmMain              iosMain
                                NativeFormatterBridge.kt      StringFormatter.kt
```

- **Android** — `:nativeformat` is a standalone `com.android.library` Gradle module with an `externalNativeBuild`/CMake step that compiles `native/native_format_jni.c` into `libnativeformat.so` for `arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86`. `shared`'s `androidMain` depends on it and calls `NativeFormatterBridge.nativeFormat()`. This module exists because AGP 9's Kotlin Multiplatform Android plugin (`com.android.kotlin.multiplatform.library`) does not support `externalNativeBuild`/JNI compilation directly — a known AGP 9 limitation whose documented workaround is a separate classic `com.android.library` module.
- **Desktop (JVM)** — the `compileNativeFormatJvm` Gradle task (`shared/build.gradle.kts`) compiles the same `native_format_jni.c` against the host JDK's `jni.h`, for the host OS/arch, into a resource bundled with the jar. `jvmMain`'s `NativeFormatterBridge` extracts it to a temp file at startup and `System.load()`s it.
- **iOS** — `native_format.def` hands `native_format.h` directly to Kotlin/Native's cinterop. `format_string()` is declared `static inline` and fully defined in the header, so cinterop compiles it in place — there's no separate library to build or link, unlike the Android/desktop JNI paths.

### Why a tagged-argument array instead of C varargs

A C variadic function (`vsnprintf(fmt, ...)`) can't be called generically across a JNI or cinterop boundary — the caller and callee have to agree on argument types and count at compile time, which is exactly what a generic `vararg args: Any` from Kotlin breaks. So `format_string()` takes an explicit array of tagged `FormatArg` values (one per conversion specifier) instead of `...`:

```c
int format_string(char* out, int out_size, const char* fmt,
                   const FormatArg* args, int arg_count);
```

Internally it only tokenizes `fmt` into literal runs and single conversion specifiers, matching each specifier to the next `FormatArg` by position. For each specifier it rebuilds a minimal one-conversion format string and calls the platform's own `snprintf()` on exactly one correctly-typed argument — so width, precision, flags, rounding, and zero-padding all come from the real libc printf implementation. The wrapper code only does tokenizing and argument dispatch, not numeric formatting.

Each platform's JNI/cinterop layer is responsible only for building that `FormatArg` array from platform-native values (boxed `Object[]` via JNI reflection on Android/desktop, a `CArrayPointer<FormatArg>` via cinterop on iOS) — none of them contain formatting logic of their own.

## Files

| File | Purpose |
|------|---------|
| `native/native_format.h` | The single source of truth: tokenizer + per-specifier `snprintf` dispatch, `static inline`. |
| `native/native_format_jni.c` | JNI wrapper shared verbatim by Android and desktop; binds via `RegisterNatives()` in `JNI_OnLoad`. |
| `nativeformat/` | Standalone Android library module owning the CMake/NDK build for `libnativeformat.so`. |
| `shared/src/androidMain/.../utils/NativeFormatterBridge.kt` | Android `System.loadLibrary` + `external fun` declaration. |
| `shared/src/androidMain/.../utils/StringFormatter.kt` | Android `actual fun formatString()`. |
| `shared/src/jvmMain/.../utils/NativeFormatterBridge.kt` | Desktop: extracts the bundled native lib to a temp file and `System.load()`s it. |
| `shared/src/jvmMain/.../utils/StringFormatter.kt` | Desktop `actual fun formatString()`. |
| `shared/src/nativeInterop/cinterop/native_format.def` | cinterop definition pointing directly at `native_format.h`. |
| `shared/src/iosMain/.../utils/StringFormatter.kt` | iOS `actual fun formatString()`, builds the `FormatArg` array via cinterop. |
| `shared/build.gradle.kts` | Wires the `nativeFormat` cinterop into both iOS targets and the `compileNativeFormatJvm` task into the desktop JVM build. |
| `commonTest/.../utils/StringFormatterTest.kt` | Shared test suite exercising the format string contract, run against the real native path on both `jvmTest` and `iosSimulatorArm64Test`. |

### Supported format specifiers

`%s`, `%d`/`%i`/`%u`, `%x`/`%X`/`%o`, `%c`, `%f`/`%F`/`%e`/`%E`/`%g`/`%G`, and `%%`. Flags `-`, `0`, `+`, space, `#`; width and precision; length modifiers (`l`, `ll`, `h`, etc.) are accepted but ignored, since `format_string()` always promotes integers to `int64_t` and floats to `double` before dispatch. Dynamic width/precision (`%*d`) is not supported.

## Known limitation: androidHostTest

`StringFormatterTest` calls the real native library through JNI. `androidHostTest` runs on a plain Robolectric JVM host with no Android runtime and no packaged `.so` to load — `System.loadLibrary()` simply cannot succeed there, independent of correctness. `shared/build.gradle.kts` excludes `StringFormatterTest` from that task for this reason. Real end-to-end coverage of the native path comes from:
- `./gradlew :shared:jvmTest` — real JNI, compiled and loaded on the host JVM.
- `./gradlew :shared:iosSimulatorArm64Test` — real cinterop, compiled and linked into the simulator test binary.
- `./gradlew :androidApp:assembleDebug` — verifies `libnativeformat.so` for all four ABIs is present in the built APK (`lib/<abi>/libnativeformat.so`).

Android's JNI path itself is only exercisable on a real device/emulator (`connectedAndroidTest`), which is outside what this environment can run.
