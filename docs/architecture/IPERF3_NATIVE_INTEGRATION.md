# Integrating Real iperf3 3.21 as a Native Library Across Android, iOS, and Desktop

**Status:** Android, desktop JVM, and iOS all done (text-output pass, no JSON-stream — see §3.2's note). Submodule at `third_party/iperf`, pinned to tag `3.21` (commit `d39cf41`, 2026-04-09). iOS build/link verified via Gradle (`compileKotlinIosArm64`, `linkDebugFrameworkIosArm64`/`IosSimulatorArm64`, `iosSimulatorArm64Test` all pass) **and** manually verified end-to-end against real remote iperf3 servers — confirmed 2026-08-27 across several iOS Simulators plus real hardware (iPhone 17 Pro, iPad M4, installed via the user's Apple Developer Program device-install entitlement).

## 8. Android/JVM Implementation (done)

Ported from a prior Android-only project's working JNI bridge
(`iperf3Android/.../cpp/iperf/iperf_jni.c`, against iperf 3.19), adapted for
3.21 and this project's conventions:

- **`native/iperf3_jni.c`** — the JNI bridge, shared verbatim between Android
  and desktop JVM (same convention as `native/native_format_jni.c`): binds via
  `RegisterNatives()` in `JNI_OnLoad` (not javah-style mangled symbol names,
  which are fragile for this project's underscore-containing package) against
  `com.jabramson.iperf3_network_tester_kmp.utils.NativeIperfBridge`.
  Three native methods: `nativeSetTempDir`, `nativeForceStop`,
  `nativeIperf3`. Redirects `test->outfile` to a pipe and forwards each
  line to `IperfCallback.onOutput()` on a dedicated reader thread — real
  text-output parsing, matching the existing `Iperf3OutputMonitor`. Dropped
  from the reference implementation: the stall-detection watchdog thread
  (redundant with `Iperf3Executor`'s own `consecutiveStalled >= 5` check in
  Kotlin, and the original's logic looked non-functional on inspection).
- **`native/iperf_config_android.h`** — hand-written `iperf_config.h` for the
  Android NDK cross-compile (no `./configure` run against the NDK toolchain).
  Socket-option availability is carried over from the prior project's
  Android config, empirically verified on real hardware, not derived from a
  host `./configure` run (whose BSD/Darwin socket options don't reflect
  Android's Linux/NDK surface). Updated for 3.21: version strings bumped,
  new 3.21 macros (`CAN_BIND_TO_DEVICE`, `HAVE_SOCKET_SHUTDOWN_SHUT_WR`,
  `HAVE_UDP_GRO`, `HAVE_UDP_SEGMENT`) left undefined (safe — just disables
  those optional features).
- **`:iperf3native`** (new Android module) — CMake/NDK build, same structural
  pattern as `:nativeformat`. Compiles `native/iperf3_jni.c` plus 14 vendored
  `third_party/iperf/src/*.c` files (client-only: no `iperf_auth.c`, no
  `iperf_sctp.c`, no `iperf_server_api.c`, no `main.c` — this app is a client
  only, matching the reference implementation). Generated headers
  (`iperf_config.h`, `version.h`) are written into the CMake build dir via
  `configure_file`, never into the submodule's own tree.
- **`compileIperf3Jvm`** (new Gradle task, `shared/build.gradle.kts`) —
  desktop JVM equivalent, mirroring `compileNativeFormatJvm`'s
  resource-bundling pattern. Runs iperf3's real `./configure --without-sctp
  --with-openssl=no` once (confirmed safe: the generated `iperf_config.h`/
  `version.h`/`Makefile` are already covered by the submodule's own
  `.gitignore`, so this never dirties `git status`), then compiles all
  sources + `iperf3_jni.c` and links a `.dylib`/`.so`/`.dll` via
  `ProcessBuilder` (not `Project.exec {}` — removed as a script-accessible
  API under Gradle 9.6.1's configuration-cache rules; this task also needed
  `notCompatibleWithConfigurationCache(...)` since its closures capture
  script-level `val`s that aren't cache-serializable — same tradeoff any
  task shelling out to an external build system like this makes).
- **`NativeIperfBridge.kt`** (androidMain + jvmMain) — thin `external fun`
  declarations, same split as `NativeFormatterBridge.kt`
  (`System.loadLibrary` on Android; resource-extract-and-`System.load` on
  desktop).
- **`IperfRunner`** converted from a single `commonMain object` (previously
  always-simulated) to `expect object` — Android/JVM `actual`s call
  `NativeIperfBridge` for real; **iOS's `actual` keeps the exact previous
  simulated body**, unchanged, since iOS native integration hasn't been
  started yet.
- **`getPlatformTempDir()`** (new `expect`/`actual`) — `Iperf3Executor`'s
  temp-directory line was a hardcoded, never-real placeholder
  (`"/data/data/tmp"`) before this work; it's now load-bearing (real
  `mkstemp()` needs a genuinely writable directory — see the `tmp_template`
  note below), so it now resolves to a real platform temp/cache dir:
  `Context.cacheDir` (Android, via the existing `AndroidAppContextHolder`),
  `java.io.tmpdir` (JVM), `NSTemporaryDirectory()` (iOS).
- **The Android tempdir workaround still applies under 3.21** — confirmed
  directly against the vendored source, not assumed carried over from 3.19.
  3.19's direct `test->tempDir` field write was replaced by 3.21 with
  `test->tmp_template` (`iperf_api.c:4789`), set via the public
  `iperf_set_test_template()` API rather than a raw field write. It's still
  required: `iperf_new_stream()`'s fallback path
  (`iperf_api.c:4792`-`4808`) still can't write to Android's sandbox
  (`/data/local/tmp` isn't writable by a normal app), so
  `nativeSetTempDir()` builds `"<dir>/iperf3.XXXXXX"` and calls
  `iperf_set_test_template()` before parsing arguments.

**Verified, not just compiled:**
1. Standalone JNI harness (plain Java, no Gradle) proved the C bridge itself:
   a real connection-refused error came back through `onError`, and — with a
   real local `iperf3 -s` server — a complete real client run streamed
   genuine bandwidth output (`78.8 Gbits/sec` etc.) line-by-line through
   `onOutput`, then `onComplete`.
2. `shared/src/jvmTest/.../runner/IperfRunnerJvmTest.kt` (new, automated) —
   exercises the actual public `IperfRunner.runIperfLive()` API
   `Iperf3Executor` calls, against a real spawned local `iperf3 -s`, asserting
   real bandwidth output was received. Passes (soft-skips if no `iperf3`
   binary is on `PATH`, so it doesn't fail environments without one).
3. `:desktopApp:build` — full build succeeds, both native libs bundled as
   jvmMain resources.
4. `:androidApp:assembleDebug` — full build succeeds; `libiperf3native.so`
   confirmed present in the built APK for all four ABIs
   (arm64-v8a/armeabi-v7a/x86/x86_64), alongside `libnativeformat.so`.

**Not yet done:** iOS native integration untouched. JSON-stream wiring untouched (see §3.2).

### 8.1 On-device bug found and fixed: `i_errno` reentrancy (upstream issue filed)

On-device Android testing (emulator, real LAN server) surfaced a real bug:
the second test run in the same app process would fail — `iperf_parse_arguments()`
itself returning `-1` — even against the exact same host/args that had just
worked. Root cause, confirmed via `adb logcat` diagnostic logging added to
`native/iperf3_jni.c` (tag `iperf3_jni`), not guessed: `i_errno` (`iperf_error.c:160`,
`int i_errno = 0;`) is a bare global, zero-initialized once at load and never
reset anywhere in the library. Helpers like `unit_atoi()`/`unit_atof()`
(`units.c`) only ever *set* `i_errno` on their own failure, never clear it on
success. Several `iperf_parse_arguments()` option cases — including
`--connect-timeout`, always present in this app's argv — check
`if (i_errno != 0) return -1;` right after calling one of those helpers,
trusting `i_errno` was `0` beforehand. That's true for iperf3's own CLI
(`main.c` parses argv exactly once per process) but false for a library
caller that re-parses repeatedly in one process: a normal test's own
completion leaves `i_errno` holding `IESTREAMREAD` (206) as an ordinary
side effect of how the receive path detects end-of-stream — not a real
error — and that stale value then gets misread as an immediate parse
failure on the *next* run, before any actual parsing happens. Confirmed
this doesn't affect 3.19 (the reference project's version): 3.19's
`OPT_CONNECT_TIMEOUT` case has no `i_errno` check at all — the guard was
added later upstream, without accounting for repeated-call/library-embedding
use.

**Fix:** `i_errno = 0;` added in `native/iperf3_jni.c`, right before every
`iperf_parse_arguments()` call. Verified against the real logcat evidence
(stale `i_errno=206` present after a run logged as successful; the next
run's `parse_result=-1` with that exact same unchanged value) — could not
be independently reproduced via a synthetic macOS/loopback test (a real
platform difference in exactly when `i_errno` gets left dirty at end of a
normal run, not fully understood), so this fix's evidence is grounded in
the real device's own logcat output specifically, not a from-scratch local
repro.

**Filed upstream:** [esnet/iperf#2063](https://github.com/esnet/iperf/issues/2063).

### 8.2 Known cosmetic issue: emoji render as garbage on desktop only

Status/error strings throughout `native/iperf3_jni.c`, `Iperf3Executor.kt`, and elsewhere use emoji (❌, 🚀, etc.) as visual markers. These render correctly on Android and iOS but show up as garbled/tofu glyphs on desktop specifically. Root cause, not yet fixed — **left alone deliberately, emoji are being kept**, this is just so the reason doesn't have to be re-derived later:

- This isn't the JNI/native string path — that's independently verified byte-correct earlier in this doc (real `NewStringUTF` round-trips, real UTF-8 content arriving intact in Kotlin `String`s). It's purely a **desktop text-rendering** problem.
- The app's desktop UI font (`ui/theme/mesloFontFamily.kt`, `Res.font.jetbrainsmononerdfont`) is a *Nerd Font* — a monospace programming font patched with extra icon glyphs mapped into Private-Use-Area codepoints (git/language/editor icons, Powerline symbols, etc.). That's a fundamentally different thing from real Unicode color-emoji support: standard Emoji-block codepoints like 🚀 (U+1F680) need a font with an actual color glyph table (COLR/CPAL or similar), which Nerd Font patches don't add to their base font.
- Why it's harder than it sounds to just fix: Android and iOS both have automatic, per-character system-level font fallback baked into their text-shaping pipelines — if the app's chosen font lacks a glyph, the OS transparently substitutes one from a system emoji font, with no code needed. Compose Multiplatform for Desktop (Skia/Skiko-based) doesn't reliably do this for a custom-loaded `FontFamily` — missing glyphs can render as tofu instead of falling back. Compose Multiplatform doesn't expose a simple, portable, documented API for "use font X, fall back to the OS's real color-emoji font for anything X lacks" — and even a manual fix would mean bundling/loading a different, OS-specific emoji font per desktop platform (Apple Color Emoji on macOS, Segoe UI Emoji on Windows, Noto Color Emoji on Linux), which may not even be loadable through Compose's normal font-resource APIs depending on their internal format. This is a known rough edge in Compose Multiplatform Desktop generally, not something specific to a misconfiguration in this project.
- Straightforward alternative fix, not applied: replace the emoji in these strings with plain ASCII markers (`[ERROR]`, `[OK]`, etc.) — guaranteed correct everywhere, no font dependency at all. Decided against for now since the emoji are wanted; revisit this trade-off if the desktop garbling becomes more than cosmetic.

### 8.3 On-device bug found and fixed: unrelated process stderr misattributed as iperf3 errors

Real-device Android testing surfaced a second on-device-only bug, visible even on an otherwise-successful run: the output panel showed correct iperf3 results (Min/Avg/Max, "iPerf JNI Test completed successfully") immediately followed by a wall of unrelated red `Error:` lines like `s_glBindAttribLocation: bind attrib 0 name position` — Skia/OpenGL shader-setup debug spam, not anything iperf3 itself emitted.

Root cause: `stderrReaderThreadFunc` (`native/iperf3_jni.c`) reads from a pipe that `nativeIperf3` `dup2()`s the process's real stderr fd (2) into for the run's duration — added earlier (§8) specifically to catch iperf3's own `warning()` calls (`iperf_api.c`), which bypass `test->outfile` entirely. `dup2()` on fd 2 redirects the *whole process's* stderr, not just iperf3's own writes — on Android, other threads sharing the same process (notably Skia's GL renderer, backing Compose's own UI rendering) can write their own unrelated diagnostics to stderr concurrently with a running test. Every line on that pipe was being forwarded through `onError` unconditionally, and `Iperf3Executor.kt` treats any `onError` call as a run failure (`rc = -1`) — so this wasn't just cosmetic, it could silently mark an otherwise-successful test as failed.

**Fix:** `warning()` (`iperf_api.c:126-129`) always writes with a fixed `"warning: "` prefix. `stderrReaderThreadFunc` now only forwards lines matching that prefix through `onError`; everything else on the redirected-stderr pipe is silently dropped.

**Why this is a good single-codebase case study:** per the project author's own recollection, this exact class of bug — unrelated stderr bleeding into a redirected-fd capture — had already been independently solved in the standalone Swift iOS port of this app, while the original standalone Android app apparently never caught it. Two separate native codebases, two different outcomes on the same underlying problem, with no way for either fix to benefit the other. That's exactly the drift a single shared codebase is meant to eliminate: `native/iperf3_jni.c` is the one source of truth for this logic across Android (CMake/NDK, `:iperf3native`) and desktop (`compileIperf3Jvm`) alike, so this fix applies to both from one edit, with nothing to keep in sync by hand.

### 8.4 `iperf3_jni.c` needed a JNI/core split before iOS could reuse it (done — see §9)

Originally written as a forward-looking design note before any iOS work started; the split described below has since been implemented exactly as planned, see §9 for the iOS side.

`native/native_format.h`/`native_format_jni.c` (the printf work, §7) already solved reusing one C source across JNI (Android/desktop) and cinterop (iOS): `native_format.h` holds a plain, stateless core function, and `native_format_jni.c` is a thin JNI wrapper around it, so cinterop just calls the core function directly with no JNI involved at all.

`iperf3_jni.c` doesn't split that cleanly, because it isn't stateless. Its real logic — `monitorThreadFunc`'s stall/progress tracking, `readerThreadFunc`/`stderrReaderThreadFunc`'s pipe-reading loops, the `warning: `-prefix filter (§8.3) — is three background threads calling back into Kotlin *mid-flight*, and those callbacks are currently made directly as raw JNI calls (`JNIEnv`, `jmethodID`, `CallVoidMethod`) threaded through the logic itself, not sitting at a clean edge the way printf's single return value did. iOS has no `JNIEnv` at all — it'll need the same threads calling back into Swift/Kotlin-Native via cinterop instead — so as written today, none of this logic is reusable as-is; a real port would mean re-implementing the same threading/stall-detection/filtering logic a second time in an iOS-specific file, which is exactly the kind of drift §8.3 is about.

**Planned split, when iOS work starts:** give the core logic a generic callback interface instead of calling JNI directly — a small context struct of C function pointers, e.g. `struct IperfCallbacks { void *ctx; void (*on_output)(void *ctx, const char *line); void (*on_error)(void *ctx, const char *line); };`. The threading/progress/filtering logic takes one of these and calls through it, with zero JNI types anywhere in that code. `iperf3_jni.c` shrinks to a thin adapter: JNI entry points that build an `IperfCallbacks` whose function pointers call `CallVoidMethod` under the hood. A new iOS-side adapter does the equivalent, filling in function pointers that call back through cinterop instead. Same shape as the printf split — the difference is just where the platform-specific "thin wrapper" boundary has to be drawn, around a callback struct instead of around a single return value.

**Companion documents:** `NATIVE_FORMATTING_JUSTIFICATION.md` (the printf integration this design deliberately mirrors), `PRINTF_FORMATTING_CASE_STUDY.md` (why that mirroring matters — see §7 below).

## 9. iOS Implementation (done)

Implements §8.4's planned JNI/core split and wires iOS up via cinterop against a real compiled static library — the first cinterop integration in this project that needs one (`native_format.h`'s cinterop was header-only, nothing to link).

- **`native/iperf3_bridge.h`/`iperf3_bridge.c`** — the JNI/core split from §8.4, implemented as designed: all of `iperf3_jni.c`'s threading, stall-detection, and `warning: `-prefix filtering logic moved here, with zero JNI types. Platform adapters talk to it through `struct IperfCallbacks { void *ctx; void (*on_thread_attach)(void *ctx); void (*on_thread_detach)(void *ctx); void (*on_output)(void *ctx, const char *line); void (*on_error)(void *ctx, const char *line); void (*on_complete)(void *ctx); }`. `on_thread_attach`/`on_thread_detach` are one addition beyond §8.4's original sketch, needed because this bridge (not the platform adapter) owns creation of the monitor/reader/stderr-reader threads: JNI requires each new pthread to `AttachCurrentThread`/`DetachCurrentThread` before/after calling back into Kotlin, so those two hooks exist for the JNI adapter to do that once per thread. Kotlin/Native's cinterop has no such requirement (its default memory model allows calling into Kotlin from any thread), so the iOS adapter leaves both `NULL`.
- **`native/iperf3_jni.c`** — shrank to exactly the thin JNI adapter §8.4 predicted: translates `struct IperfCallbacks` into real `CallVoidMethod` calls. Uses `_Thread_local JNIEnv *tls_env` (one pointer per adapter, not per callback struct) so `on_output`/`on_error`/`on_complete` work correctly whichever thread invokes them — the original calling thread's `env` is stashed directly (no attach needed, it's already attached), and each spawned thread's `on_thread_attach` fills in its own via `AttachCurrentThread`. This is a slight simplification over the original code's three independent `NewGlobalRef`s (one shared global ref now, plus one lookup of the three `jmethodID`s, both safe to reuse across threads per the JNI spec).
- Android's CMake build (`iperf3native/src/main/cpp/CMakeLists.txt`) and desktop's `compileIperf3Jvm` (`shared/build.gradle.kts`) both updated to compile `iperf3_bridge.c` alongside `iperf3_jni.c` — verified via `:shared:compileIperf3Jvm` and a full `:shared:jvmTest` pass (the existing `IperfRunnerJvmTest.kt` still runs a real libiperf test end to end through the split).
- **`native/iperf_config_ios.h`** — hand-written, analogous to `iperf_config_android.h`, but a direct copy of the *real* `./configure`-generated `third_party/iperf/src/iperf_config.h` this project's own macOS desktop build already produces and has verified compiles/links cleanly (§6.6) — not derived from scratch. iOS shares macOS's Darwin/XNU kernel and BSD socket-option surface, so the same `HAVE_*` values apply; not yet runtime-verified against a real device/simulator run, only compile/link.
- **New Gradle tasks `compileIperf3BridgeIosArm64`/`compileIperf3BridgeIosSimulatorArm64`** (`shared/build.gradle.kts`) — cross-compile the client-only iperf3 source list + `iperf3_bridge.c` into `libiperf3bridge.a`, one per iOS target, via `xcrun -sdk <iphoneos|iphonesimulator> clang`/`libtool -static`. Device and simulator arm64 are built separately despite sharing an instruction set — different SDKs, not link-compatible. `-mios-version-min=18.2`/`-mios-simulator-version-min=18.2` matches `iosApp.xcodeproj`'s `IPHONEOS_DEPLOYMENT_TARGET`. `onlyIf { OperatingSystem.current().isMacOsX }` since this only ever runs from a macOS host, same as the rest of the iOS toolchain.
- **`shared/src/nativeInterop/cinterop/iperf3_bridge.def`** — parses `iperf3_bridge.h` directly (same pattern as `native_format.def`), but unlike that one, doesn't hardcode `staticLibraries`/`libraryPaths` in the `.def` file itself, since the correct path differs per target. Instead, `shared/build.gradle.kts`'s existing iOS target loop passes `extraOpts("-libraryPath", ..., "-staticLibrary", "libiperf3bridge.a")` per target, and a `tasks.matching { it.name == "cinteropIperf3Bridge$capitalizedTarget" }` block wires the dependency on the matching `compileIperf3Bridge*` task so the `.a` exists before cinterop needs it. Confirmed via `nm` that both built archives export `_iperf3_bridge_run_live`/`_iperf3_bridge_force_stop`, and — more importantly — confirmed the link actually resolves cleanly by running `linkDebugFrameworkIosArm64`/`IosSimulatorArm64` (not just `compileKotlinIos*`, which only proves the Kotlin side type-checks against the cinterop klib, not that linking against the real `.a` succeeds).
- **`shared/src/iosMain/.../utils/NativeIperfBridge.kt`** (new) — the iOS analog of the JNI `NativeIperfBridge.kt`, same public method shape (`nativeSetTempDir`/`nativeSetStallTimeoutSeconds`/`nativeForceStop`/`nativeIperf3`). Uses `StableRef<IperfCallback>` to pass the Kotlin callback object across the C boundary as `void *ctx` (the standard Kotlin/Native mechanism), and `staticCFunction` for the three callback function pointers — `on_thread_attach`/`on_thread_detach` are left `null` since cinterop needs no per-thread attach step. One real API-shape surprise found only by compiling against the generated bindings, not predictable from the header alone: cinterop maps a plain `const char *` parameter (`iperf3_bridge_set_temp_dir`) to an idiomatic `String?` Kotlin parameter directly — no `.cstr`/`memScoped` needed for that one — while `char **argv` still needs the expected `CValuesRef`-style handling (`arguments.map { it.cstr.ptr }.toCValues()`).
- **`shared/src/iosMain/.../runner/IperfRunner.kt`** — the `actual object` now delegates to `NativeIperfBridge` exactly like Android/JVM's actuals, replacing the old `sampleOutputData.kt` delay-loop simulation entirely. Uses `Dispatchers.Default`, not `Dispatchers.IO` — see the Room section of `CLAUDE.md` for why `Dispatchers.IO` isn't public on this project's resolved iOS coroutines version.
- **`iosApp/iosApp/Info.plist`** — added `NSLocalNetworkUsageDescription`, per §5's prediction. No `NSBonjourServices` entry added — this app only opens raw TCP/UDP sockets to a user-specified host, it doesn't do mDNS/Bonjour service discovery, so that key isn't needed.
- **Verification performed:** `:shared:compileKotlinIosArm64`, `:shared:compileKotlinIosSimulatorArm64`, `:shared:linkDebugFrameworkIosArm64`, `:shared:linkDebugFrameworkIosSimulatorArm64`, and `:shared:iosSimulatorArm64Test` (which links and actually runs a test binary on a real Simulator process) all pass. **Also done, confirmed 2026-08-27:** the actual app run end-to-end against real remote iperf3 servers, across several iOS Simulators plus real hardware — an iPhone 17 Pro and an iPad M4, installed via the user's Apple Developer Program device-install entitlement (personal-device installs, no App Store distribution needed). This clears the same "manually verified end-to-end" bar Room/SQLite and desktop packaging were held to before being called done — iOS is no longer behind Android/desktop on this.

## 1. Current State

`IperfRunner` (`shared/src/commonMain/.../runner/IperfRunner.kt`) is a stub: `runIperfLive()` replays canned lines from `sampleOutputData.kt` on a delay loop, `forceStop()` and `setTempDir()` are no-ops that just `println`. `Iperf3Executor` — the layer above it — is otherwise fully built out: it constructs a real iperf3-style argv array from `UiInputData` (`--client`, `--port`, `--reverse`, `--parallel`, `--connect-timeout`, `--time`, `--omit`, etc.), drives the test through a `CoroutineScope(Dispatchers.IO)`, and threads output through `IperfCallback` (`onOutput(line)` / `onError(error)` / `onComplete()`) into `Iperf3OutputMonitor.processLine()`, which parses each line into an `Iperf3RunningState` (interval number, bandwidth, running min/max/avg/median/stddev, stall detection, etc.) that the Compose UI observes. `IperfCallback`'s `onOutput` signature (a single `String` per call) and `Iperf3OutputMonitor.processLine`'s job (parse one line into one state update) both assume iperf3's traditional human-readable columnar output — the exact format `native_format.h`'s `format_string()` was built to reproduce.

**The task:** replace `IperfRunner`'s simulation with real iperf3 3.21, without changing `Iperf3Executor`'s or the UI's contract any more than necessary.

## 2. Why This Is a Different Class of Problem Than `format_string()`

The printf integration (`NATIVE_FORMATTING_JUSTIFICATION.md`) had an escape hatch on every platform: JNI, cinterop, or a same-language stdlib call, freely mixed per platform, because `formatString()` is a single stateless function call with a bounded argument list. iperf3 breaks both of those properties:

1. **No subprocess fallback on mobile.** iOS sandboxing forbids `fork`/`exec` of a bundled executable outright — there is no way to ship an `iperf3` binary and spawn it as a child process on iOS, full stop. Modern Android (W^X enforcement since Android 10) similarly blocks executing an arbitrary bundled ELF binary that wasn't loaded as a proper `.so` via the standard native-library loader. **This means the mobile targets have exactly one viable path: link iperf3 in-process as a library and call its C API directly — there is no simpler fallback to retreat to if that gets hard**, unlike printf where `NSString.stringWithFormat:` was always sitting there as an (ultimately broken, see the case study) escape hatch. Desktop is the only target where a real subprocess would trivially work — see §3's decision to not special-case it anyway.
2. **Stateful and long-running, not a single call.** `format_string()` returns a value once, synchronously. iperf3 runs a test for seconds to hours, and this app needs live progress during that time — one or more values need to flow *out* of the native side, continuously, from a background thread, into a Kotlin callback, for the whole run.
3. **Needs real cancellation.** `forceStop()` has to actually interrupt an in-flight, blocking native call. printf never had a "stop halfway through formatting" concept.
4. **The vendored source is enormous by comparison.** `native_format.h` was ~160 lines this project wrote from scratch. iperf3 3.21 is a real, independently-maintained C codebase (`esnet/iperf` on GitHub) with its own build system, platform `#ifdef`s, and release cadence — this project is *consuming* it, not authoring it.

Despite all of that, §5 below shows the actual shape of the fix is closer to the printf work than this list makes it sound — see §7.

## 3. Decisions

### 3.1 In-process library linking on every target — not subprocess-on-desktop

Even though desktop could trivially spawn a real `iperf3` binary via `ProcessBuilder`, the integration uses `libiperf` in-process on **all three** platforms (JNI on Android and desktop JVM, cinterop on iOS) — the same target/mechanism split as `format_string()`. Rationale: iOS and Android have no choice (§2.1), and giving desktop a structurally different code path (subprocess + stdout scraping) means maintaining two independent implementations of "run an iperf3 test and report progress" that can drift, rather than one. The cost is more upfront work on desktop than strictly necessary; the benefit is one code path, one set of bugs, one thing to test.

### 3.2 `--json-stream` for live output, not text-column scraping (deferred — see note)

**Note (current phase):** JSON-stream wiring (§6.1's `iperf_set_test_json_callback` hook) is deliberately **not** being implemented yet. The immediate focus is (a) getting `libiperf` building and linking on at least one platform with no I/O wiring at all, then (b) a first working end-to-end run using **simpler traditional text-output parsing** — capturing `test->outfile`'s classic columnar output and reusing/adapting the existing `Iperf3OutputMonitor` text parser — rather than switching to JSON-stream immediately. The rationale and design below for JSON-stream remains the intended long-term direction (still simpler than expected — see §6.1 — a real API hook, not stdout-scraping), but is out of scope until the basic build/link/run path is proven.

iperf3 3.17+ supports `-J`/`--json-stream` mode: one JSON object per line, emitted as the test progresses, specifically designed for programmatic consumption — as opposed to the traditional human-readable columnar output that `native_format.h` exists to reproduce and that `Iperf3OutputMonitor` currently regex/column-parses. The integration uses JSON-stream output as the actual data contract between native iperf3 and Kotlin.

**This has a real, direct consequence for the printf work just finished:** `format_string()` and its fixed-width iperf3-style column formatting (`"%-12.12s %4.4s %-9.9s %10.10s"`, as seen in `Iperf3OutputMonitor.kt` and its test suite) is now purely a **display concern** — reproducing iperf3's classic human-readable look for whatever renders the running log to the user — not a *parsing* concern. `Iperf3OutputMonitor.processLine()`'s job changes from "regex a fixed-width text line" to "parse one JSON object per interval," which is a substantially simpler and more robust parser, and removes an entire class of fragility (column width assumptions breaking on unusual hostnames/units) that the current text-based parser has to handle. `format_string()` itself is not wasted work — it's still exactly what's needed to render iperf3-style output for the user — but it is no longer on the critical path for *understanding* iperf3's output, only for *displaying* it.

### 3.3 iperf3 source as a git submodule

`esnet/iperf` is added as a git submodule (pinned to the `3.21` tag) rather than a vendored copy, so upstream security fixes/version bumps are a submodule bump + rebuild, not a manual re-diff against a committed copy. Cost: contributors need `git submodule update --init` after cloning (to be called out in `AGENTS.md`/`README.md` once implemented).

## 4. Proposed Architecture

Same three-interop-mechanism shape as `native_format.h`, but wrapping `libiperf`'s existing C API (`iperf_api.h`) instead of a from-scratch C function:

```
                         third_party/iperf (git submodule, esnet/iperf @ 3.21)
                                        |
                         native/iperf3_bridge.{h,c}  (this project's shim —
                         owns test lifecycle, callback marshaling, cancellation;
                         NOT a reimplementation of iperf3 itself)
                    /                   |                    \
           JNI (Android)         JNI (desktop)          cinterop (iOS)
                |                       |                       |
      :iperf3native module      compileIperf3Jvm task    iperf3_bridge.def
      (new module, CMake/NDK    (new Gradle task,         (cinterop against
       building libiperf +       host toolchain)           the shim + a real,
       the JNI shim into one         |                      compiled static
       .so — same AGP-9            jvmMain                  lib this time —
       workaround pattern as     IperfNativeBridge.kt        unlike
       :nativeformat)                  |                      format_string(),
           |                      jvmMain                     this can't be
      androidMain               IperfRunner.kt (actual)      static inline;
      IperfNativeBridge.kt                                    see §6.1)
           |                                                       |
      androidMain                                              iosMain
      IperfRunner.kt (actual)                                  IperfNativeBridge.kt
                                                                     |
                                                                 iosMain
                                                                 IperfRunner.kt (actual)
```

`iperf3_bridge.c` is this project's own code, and its job is narrow and specific — it is *not* a reimplementation of any iperf3 logic, only the glue needed to make `libiperf`'s blocking, callback-poor C API usable from a Kotlin `expect`/`actual` boundary:

- Own a `struct iperf_test*` per running test, built from the same argv-shaped input `Iperf3Executor` already constructs.
- Set `test->json_stream = 1` and call `iperf_set_test_json_callback(test, shim_json_callback)` (confirmed public API — see §6.1) before `iperf_run_client`/`iperf_run_server`; `shim_json_callback` is a plain C function this shim owns, invoked once per JSON-stream event with an already-rendered `char*`.
- Bridge that C callback across the interop boundary to Kotlin's `IperfCallback` — via the same "no generic vararg, keep the payload to a fixed, simple shape" principle as `native_format.h` (§7): each JSON-stream line is just a `const char*`, a single fixed-type value, so — unlike printf's argument-list problem — there is no tagged-union marshaling needed here at all. This part is *easier* than printf, not harder.
- Run the test on a background thread (all three platforms), and provide a real cancellation entry point — `forceStop()` sets `test->done = 1` directly on the running test's struct (confirmed public field, no signal needed — see §6.4).

`IperfRunner`'s `expect`/`actual` split stays exactly where it already is in `commonMain`; only the `actual` bodies change, from the current simulation to a call into the corresponding platform's `IperfNativeBridge`.

## 5. Platform-Specific Notes

- **Android:** New `:iperf3native` module, same structural reason as `:nativeformat` — AGP 9's `com.android.kotlin.multiplatform.library` plugin still can't run `externalNativeBuild`/CMake, so this is another standalone classic `com.android.library` module. Unlike `:nativeformat`, this one's CMake build is nontrivial on its own terms: it has to build `libiperf` itself (with its own `configure`/autotools or CMake, whichever iperf3 3.21 actually ships — to confirm once the submodule is added) for all 4 ABIs, not just compile one small shim file. Requires `INTERNET` permission (already presumably present, given this is a network-testing app) and needs checking whether any iperf3 features used depend on capabilities unavailable in Android's app sandbox (e.g., certain socket options).
- **iOS:** Needs an `NSLocalNetworkUsageDescription` entry in `Info.plist` and will hit the same Local Network privacy permission prompt documented in `LMSTUDIO_MCP_CASE_STUDY.md` §5 (that case study was about a different app, but the underlying OS mechanism — a distinct local-network permission separate from general internet access — applies here directly, and is actually *more* likely to bite for an app whose entire purpose is opening raw sockets to arbitrary hosts). Also: per §4's diagram, `iperf3_bridge` cannot be `static inline` the way `format_string()` was, because it needs to link against the compiled `libiperf` static library, not just be textually included — this brings back exactly the "build a static lib per iOS arch and link it via cinterop" step that `native_format.h`'s header-only design let this project skip entirely (see `NATIVE_FORMATTING_JUSTIFICATION.md`'s iOS row). That step needs real design work: which Kotlin/Native cinterop mechanism (`staticLibraries`/`libraryPaths` in the `.def` file vs. a Gradle task invoking `xcrun`/`clang` directly per iOS arch, mirroring `iosApp`'s existing Xcode-managed build) is used to actually produce that library before cinterop links against it.
- **Desktop (JVM):** Closest to the Android JNI path structurally (§3.1), but the *build* is closer to `:nativeformat`'s `compileNativeFormatJvm` task — compiled at Gradle build time against the host toolchain for the host OS/arch, bundled as a resource, `System.load()`ed at runtime — except this time the task also has to build `libiperf` itself for the host, not just compile one shim file against `jni.h`.

## 6. Open Technical Questions — Resolved Against the Real Vendored Source

The submodule is in (`third_party/iperf` @ tag `3.21`, commit `d39cf41`). All five questions below were answered by reading the actual `src/iperf_api.h`/`iperf_api.c`/`iperf.h`/`main.c` in that checkout — not from memory of older iperf3 versions. File/line references below are against that commit.

1. **What does `libiperf` 3.21 actually expose for JSON-stream output redirection? — Answer: a first-class callback hook, simpler than assumed.** `iperf_api.h:208` declares `void iperf_set_test_json_callback(struct iperf_test *ipt, void (*callback)(struct iperf_test *, char *))`. When set, every JSON-stream event — `"start"` (`iperf_api.c:939`), `"interval"` (`iperf_api.c:4060`), `"error"` (`iperf_api.c:5313`), `"server_output_json"`/`"server_output_text"` (`iperf_api.c:5316`/`5319`), and `"end"` (`iperf_api.c:5321`) — is delivered via `JSONStream_Output()` (`iperf_api.c:3086`) calling `(test->json_callback)(test, str)` with `str` a fully-rendered, already-JSON-encoded `char*` (one event object per call, produced by `cJSON_PrintUnformatted`). **This means §4's shim does not need to hook/redirect `test->outfile`/stdout at all** — no memstream, no pipe, no FILE* trickery. It just needs to: set `test->json_stream = 1` (or use `-J`/`--json-stream` in the constructed argv, which sets it internally — `iperf_api.c:1282`), call `iperf_set_test_json_callback()` with a C function pointer before `iperf_run_client`/`iperf_run_server`, and have that C function marshal the `char*` across the interop boundary to Kotlin's `IperfCallback.onOutput(line)`. This is *more* favorable than §4 assumed — one clean public API call, not an output-stream interception.
2. **iperf3's own build system vs. this project's per-platform build. — Answer: autotools, confirmed.** The submodule root has `bootstrap.sh`, `configure.ac`, `Makefile.am`, `configure`, `Makefile.in` — no CMake anywhere in the tree. Still to decide during implementation: whether to drive `./configure && make` per target (cross-compiling via `--host=` for Android NDK toolchains, host-native for desktop) and link the resulting `libiperf.a`, or write CMake-based builds that compile iperf3's `src/*.c` directly and bypass autotools. Given iperf3's source list is stable and modest (`src/*.c`, listed above — ~35 files), compiling the sources directly (option b) is likely simpler to integrate into Android's existing CMake/NDK (`:nativeformat`-style) and a desktop Gradle task than cross-compiling autotools output, but this is not yet decided — same category of build-system-archaeology work `NATIVE_FORMATTING_JUSTIFICATION.md` needed for AGP 9's KMP plugin limitation (§7).
3. **Reentrancy/thread-safety of `struct iperf_test`. — Partially confirmed.** No file-scope mutable globals holding test state were found in `iperf_api.c` (only `static const char iperf_version[]`, a constant). Test state lives entirely in the heap-allocated `struct iperf_test *` returned by `iperf_new_test()` and released by `iperf_free_test()`, which supports the plan's assumption of clean sequential reuse (new/run/free per test, never reused). Not yet runtime-verified — full confirmation needs an actual sequential-run test once the bridge exists, since static analysis alone can't rule out OS-level resource leakage (sockets, threads) across runs.
4. **Cancellation mechanism specifics — Answer: no signal needed.** `test->done` (`iperf.h:391`) is a plain public `int` field on the (non-opaque) `struct iperf_test`, polled throughout the client/server run loops (`iperf_client_api.c`, `iperf_server_api.c` — e.g. `test->done = 1` at `iperf_client_api.c:808`, `iperf_server_api.c:327`). `iperf_got_sigend()` (`iperf_api.c:5144`) — iperf3 CLI's own `SIGINT` handler — does nothing more than `test->done = 1` (`iperf_api.c:5155`) before returning. Since this integration runs in-process (not as a subprocess reacting to a real OS signal), `forceStop()`'s shim implementation can just set `test->done = 1` directly on the running test's struct from another thread — no signal machinery needed at all.
5. **Android/iOS permission and entitlement requirements** for the specific socket operations iperf3 performs (raw TCP/UDP client and server modes, `SO_REUSEADDR`, etc.) — still open, not resolvable from reading iperf3's own source alone; needs to be checked against each platform's sandbox docs once a build exists to actually test against.
6. **OpenSSL dependency — decided: not supported, dropped entirely.** `libiperf`'s only use of OpenSSL is `iperf_auth.c` (RSA-based `-A`/`--authorized-users-path` client/server authentication), and every OpenSSL include/call site across the codebase (`iperf.h`, `iperf_api.c`, `iperf_api.h`, `iperf_auth.{c,h}`) is cleanly gated behind `#if defined(HAVE_SSL)` — this is a real, clean compile-time toggle, not a hack. This project doesn't use iperf3's auth feature, so OpenSSL is dropped project-wide: `./configure --with-openssl=no --without-sctp` (confirmed: emits `Building without OpenSSL; disabling iperf_auth functionality.` and does not define `HAVE_SSL`), and `iperf_auth.c` is excluded from the source list entirely (17 files instead of 18). Verified end-to-end on macOS host: all 17 sources compile with zero OpenSSL headers pulled in, `libiperf.a` links a trivial `iperf_new_test()`/`iperf_defaults()`/`iperf_free_test()` round-trip with **no OpenSSL linkage at all** (confirmed via `otool -L`). This removes an entire dependency (and its per-platform sourcing problem — no OpenSSL-for-Android/iOS build needed) from every platform's native build, not just desktop's.

## 7. Why This Design Deliberately Mirrors `native_format.h`

`PRINTF_FORMATTING_CASE_STUDY.md` documents, with direct evidence, a failure pattern from three earlier local-model attempts at the printf task: each one either avoided a real interop boundary at the cost of reimplementing logic that already existed elsewhere (Attempt 1), or hit the boundary and quietly narrowed the problem's contract into something that merely compiled rather than surfacing that the correct approach couldn't be executed as planned (Attempt 3 — the `NSArray`-as-vararg bug, undetected until it hung). The generalizable lesson from that case study — confirmed again by §2 of this document — is that **the hard part of "just call this native thing from Kotlin" is almost never the domain logic; it's getting a value (or a stream of values) across a real FFI/interop boundary without silently violating that boundary's actual contract.** This design responds to that lesson directly: keep the interop payload as simple as it can possibly be (a `const char*` per JSON line, not a generic argument list — see §4), reuse the same three concrete mechanisms already proven end-to-end for `format_string()` (JNI ×2, cinterop ×1), and resolve every genuinely open technical question (§6) against the real vendored source once it's available, rather than against assumption.
