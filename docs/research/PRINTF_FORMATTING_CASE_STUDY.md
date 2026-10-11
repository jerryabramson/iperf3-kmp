# Implementing printf()-Style Formatting Across Kotlin Multiplatform: A Case Study in Underestimated Complexity

**Author:** Jerry Abramson (with Claude Code)
**Date:** 2026-07-18
**Context:** Independent research on local LLM tooling for agentic workflows, `iperf3KMP` project. Companion document to `LMSTUDIO_MCP_CASE_STUDY.md` — that document covers an infrastructure-debugging task class; this one covers a task that looked like routine cross-platform coding and was not.

## 1. Overview

`Iperf3OutputMonitor` needs to render iperf3-style fixed-width tabular output (`%-12s %5d %-9.2f`, etc.) identically on Android, iOS, and desktop JVM. On paper this is a one-line requirement: "add printf-style string formatting." In practice, implementing it correctly — as a single source of truth reachable from all three platforms — required three independent native-interop mechanisms (JNI on two different JVM flavors, Kotlin/Native cinterop on a third), a Gradle module that exists solely to route around a documented AGP 9 limitation, and a from-scratch C dispatch routine, because none of the three platforms' interop layers can pass a *generic, variably-typed argument list* across their FFI boundary. That last fact is the load-bearing complexity of this entire task, and it is not visible from the requirement as originally stated.

This document records three failed local-model attempts at the same requirement (§3–§5), diagnoses *why* each one failed in structural terms rather than "the model made a mistake" (§6), and describes the architecture that was ultimately needed (§7–§8) — a considerably heavier lift than the original one-line requirement suggested. It is offered as evidence for a specific claim: **some requirements that read as simple are simple only until an implementation has to cross a real interop boundary**, and recognizing that in advance — rather than discovering it three attempts in — is itself a nontrivial piece of engineering judgment.

## 2. Task and Environment

- **Repo:** `iperf3KMP`, a Kotlin Multiplatform (Compose Multiplatform) app targeting Android, iOS, and desktop JVM.
- **Local tooling used for the first three attempts** (per the credit left in the code itself, `commonMain/.../utils/StringFormatter.kt`, unmodified by this session — see §7): `qwen/qwen3.6-35b-a3b`, running locally in LM Studio on a Dell Pro Max with an Nvidia GB10 "Spark" devkit (128 GB), driven via `opencode` v1.18.3 from a MacBook Pro 14" (M5 Max, 128 GB). This is the same model and hardware profile used in the local-model comparison in `LMSTUDIO_MCP_CASE_STUDY.md` §11.1.
- **Branch history:** `b894e92` (pre-task baseline) → `2962f6f` "Beginning the implementation of full printf() style formatting." → `0779988` "partial implementation of platform dependent string formatting." At that point the session was handed off with the working tree in an **uncommitted, in-progress state**.

## 3. Attempt 1: Reinventing `printf()` in Pure Kotlin

The first instinct was to write a printf-compatible parser directly in `commonMain` Kotlin: tokenize the format string, and hand-implement width padding, left/right alignment, zero-padding, precision/rounding, and sign handling. No trace of this attempt survives in git — it was abandoned before being committed — but its existence is corroborated directly by `NATIVE_FORMATTING_JUSTIFICATION.md` as of `2962f6f`, which opens with a section titled **"Why Not Reimplement in Pure Kotlin?"**:

> A pure-Kotlin implementation would require: (1) Parsing format specifiers from strings at runtime (2) Implementing width padding, truncation, zero-padding flags, and decimal rounding manually (3) Handling edge cases (negative numbers with zero-padding, overflow, locale differences). This is a reinvention of `printf`, which has been battle-tested for 50+ years across Unix systems.

This diagnosis was correct — reimplementing printf's numeric formatting is exactly the wrong place to spend engineering effort, since every target platform already ships a correct, battle-tested implementation. What's notable is that the model reached the right conclusion (don't reimplement it) but, as §4–§5 show, could not then execute the *alternative* it correctly identified.

## 4. Attempt 2: `String.format()` on JVM/Android — Worked, As Expected

For Android and desktop JVM, `java.lang.String.format(Locale.US, format, *args)` is a direct, one-line substitute for printf, and it is exactly what a correct implementation should use on those two targets. This part of the local model's work was correct and needed no rework — `androidMain`'s and `jvmMain`'s `String.format`-based `actual fun formatString()` (as committed at `0779988`) passed cleanly against the model's own 34-test suite. This is unsurprising: `java.lang.String.format` is a same-language, same-runtime API call with no FFI boundary to cross, which is precisely the condition under which the local model succeeded consistently across this task and the companion MCP case study.

## 5. Attempt 3: iOS via Kotlin's Foundation Bindings — Infinite Loop

For iOS, `commonMain`'s `expect fun formatString()` needs a platform implementation with no `java.lang.String.format` equivalent available. The model's original plan (per `NATIVE_FORMATTING_JUSTIFICATION.md` at `2962f6f`) was in fact the architecturally correct one:

```
nativeMain (Kotlin + C interop)
  ├── nativeUtils.c    — vsnprintf wrapper
  └── actual fun formatString() — calls native code via cinterop/FFI
```

But by `0779988`, the plan had changed, with the doc now explicitly justifying the reversal under a section titled **"No cinterop needed"**:

> The implementation uses platform-native APIs on each target — no cinterop required... iOS: Uses `NSString.stringWithFormat()` which internally calls the same vsnprintf-based code path as C, accessed through Apple's Foundation framework.

The actual code committed at `0779988` (`iosMain/.../utils/StringFormatter.kt`, before this session replaced it) was:

```kotlin
actual fun formatString(format: String, vararg args: Any): String {
    val nsArgs = buildNSArray(args)                       // Kotlin vararg -> NSArray of NSNumber/NSString
    val result = NSString.stringWithFormat(format, nsArgs) ?: ""
    ...
}
```

This compiles, because Kotlin/Native's cinterop generates a real binding for Objective-C's `+stringWithFormat:` as `stringWithFormat(format: String?, vararg args: Any?)` — a *bona fide* variadic Objective-C selector, backed by the same C `va_list` calling convention as C's own `vsnprintf`. The bug is semantic, not syntactic: the code above passes exactly **one** vararg — a single `NSArray` object — regardless of how many arguments `args` actually contains. For a format string like `"%-12s %5d"` (two specifiers), Objective-C's format-string engine consumes the first (and only) vararg slot for `%s` — where the *NSArray itself* gets substituted as if it were the string, since any object is a valid `%@`/`%s`-adjacent argument — and then, for the second specifier, reads whatever happens to occupy the next slot in the C variadic-argument convention (an adjacent register or stack slot never populated by this call), which is undefined behavior. This is consistent with the actual observed symptom: the app did not crash or hang — it ran to completion and rendered garbled, gibberish text (mangled host/port fields, nonsense numeric columns, stray non-ASCII characters in place of formatted numbers) live in the "iperf3 Output" panel on-device, as captured in `docs/research/evidence/ios-printf-gibberish-output.png`. That is exactly the "garbled output" branch of undefined behavior, not the "runs off into an unbounded read" branch — the garbage value(s) landing in the unconsumed variadic slots happened to be interpretable as *something* printable (or the format engine substituted its own type-mismatch placeholder) often enough to keep producing bounded, if meaningless, output for every line rather than ever crashing.

**This is inferred from static analysis of the code actually committed, cross-checked against the on-device screenshot in `docs/research/evidence/ios-printf-gibberish-output.png`** — the mechanism is a well-known, real class of Objective-C variadic-bridging bug, and it is fully consistent with both the observed symptom (garbled but bounded, non-crashing, non-hanging output, rendered continuously as new lines arrived) and the state the working tree was left in (uncommitted, mid-edit).

## 6. Root Cause: Generic Variadic Arguments Do Not Cross FFI Boundaries

All three attempts above are best understood as three different reactions to the same underlying wall, which none of them ever named explicitly: **none of Kotlin Multiplatform's native interop layers (JNI, Kotlin/Native cinterop to C, Kotlin/Native cinterop to Objective-C) can accept a Kotlin `vararg args: Any` and forward it, generically, as a native variadic argument list.**

A C or Objective-C variadic function (`vsnprintf(fmt, ...)`, `+stringWithFormat:`) is not "a function that takes an array" — it is a function whose caller and callee agree, *at the individual call site, at compile time*, on the exact number, order, and machine type (int vs. double vs. pointer, each promoted per that platform's C ABI) of the trailing arguments. That agreement is baked into the call instruction itself. There is no way to satisfy it generically from a boundary where the argument list's shape is only known at runtime — which describes every one of: a JNI native method receiving a `vararg args: Any` from Kotlin, a Kotlin/Native cinterop call into a C variadic function, and a Kotlin/Native cinterop call into an Objective-C variadic selector.

- Attempt 1 (§3) avoided the wall by not crossing any boundary at all — at the cost of reimplementing printf's numeric formatting, correctly identified as not worth doing.
- Attempt 2 (§4) never hit the wall, because `java.lang.String.format` has no boundary to cross.
- Attempt 3 (§5) hit the wall and produced code that *type-checks* against a real variadic binding while violating its actual calling contract — the single most dangerous outcome, since it fails silently at compile time and only manifests as undefined behavior at runtime.

**This is worth dwelling on, because it is not incidental to this bug — it is structural.** Kotlin/Native's cinterop binding for `stringWithFormat(format:vararg args:)` is, from the type checker's point of view, a completely ordinary function call: one `String?` and one `vararg args: Any?`, both satisfied. Nothing in the signature encodes "the number of trailing arguments must equal the number of `%` specifiers in `format`" — that invariant lives entirely in the *runtime* string content and is invisible to any static check (compiler, linter, or IDE). The same is true of `native_format.h`'s own `format_string(out, out_size, fmt, args, arg_count)` in §7's fix — it is a plain, statically-typed C function, but nothing stops a caller from passing the wrong `arg_count` for `fmt`; the correct fix moved the argument-shape mismatch from "unrecoverable, silent UB inside a variadic ABI call" to "a bounds-checked mismatch the C dispatcher can detect and reject," it did not make the underlying "does this format string match this argument list" invariant statically checkable. **There is no way to catch this class of bug without actually executing the exact code path, with the exact argument shape, and inspecting the output** — which is precisely what did *not* happen before Attempt 3's code was handed off: the record shows the pre-existing 270-line test suite (`commonTest/StringFormatterTest.kt`, §8) being run and passing against JVM/Android's `String.format` implementation (§4, "passed cleanly against the model's own 34-test suite"), but no analogous claim exists anywhere for `iosSimulatorArm64Test` against Attempt 3's iOS code — consistent with the working tree being left uncommitted, mid-edit. The bug shipped past every static check that existed and was only discovered because a human happened to run the actual app on an actual simulator and read the actual screen.

This reframes the original one-line requirement. "Add printf-style formatting on every platform" is actually two requirements bundled together, and only one of them is easy: (a) do the actual number/string formatting — solved everywhere by each platform's built-in printf-family implementation, and (b) get a variably-shaped argument list from common Kotlin code to that implementation — which is the part with no native, generic solution on any of the three platforms, and is the entire source of the complexity documented in §7.

## 7. The Actual Solution: One Header, Three Interop Mechanisms, No Generic Varargs Anywhere

The fix taken in this session's work (`nativeprintf` branch, commit `a301158`) does not try to defeat §6's wall — it routes around it, by never asking any interop layer to forward a variadic argument list at all.

**`native/native_format.h`** defines `format_string()`, a single, `static inline`, header-only C function — the sole implementation of the formatting logic, used identically by all three platforms:

```c
int format_string(char* out, int out_size, const char* fmt,
                   const FormatArg* args, int arg_count);
```

`FormatArg` is a plain tagged-union struct (`{ type; int64_t i; double d; const char* s; }`) — an ordinary, fixed-shape C type, not a variadic parameter list. `format_string()` tokenizes `fmt` itself (flags/width/precision/conversion char), and for *each* specifier it encounters, rebuilds a minimal one-specifier format string and calls the platform's real `snprintf()` with exactly one, statically-typed argument pulled from `args[i]`. Every actual formatting decision — rounding, zero-padding, alignment — is still delegated to libc's own `snprintf`, exactly as Attempt 1 correctly argued it should be. The only code that is genuinely novel here is the tokenizer/dispatcher, which is a fixed-shape problem with no variadic boundary anywhere in it.

Each platform's job is reduced to building a `FormatArg[]` array from its own native argument representation — none of which requires passing a variadic list across an interop boundary:

| Platform | Mechanism | What crosses the boundary |
|---|---|---|
| Android | JNI (`native/native_format_jni.c`, built by a new standalone `:nativeformat` Gradle module) | a `jobjectArray` of boxed `Object`s, inspected element-by-element via `IsInstanceOf`/`CallLongMethod`/`CallDoubleMethod` |
| Desktop JVM | JNI (same `native_format_jni.c`, compiled at build time against the host JDK's `jni.h`, bundled as a resource, loaded via `System.load()`) | identical `jobjectArray` marshaling — same C file, same fixed-shape JNI signature, different JVM |
| iOS | Kotlin/Native cinterop directly against the header (`native_format.def`) — `format_string` is `static inline`, so cinterop compiles it in place with no separate library to build or link | a `CArrayPointer<FormatArg>` built field-by-field in a `memScoped` block |

The Android path required a structural workaround beyond the interop problem itself: AGP 9's Kotlin Multiplatform Android plugin (`com.android.kotlin.multiplatform.library`, used by `:shared`) does not support `externalNativeBuild`/CMake/JNI compilation — a documented AGP 9 limitation, confirmed via web search against current JetBrains/Android tooling docs during this session, not assumed. The workaround (also the documented recommendation) is a second, classic `com.android.library` module (`:nativeformat`) whose only job is owning the CMake/NDK build and getting its `.so` output pulled transitively into the final APK by AGP's normal dependency-based native-library merging — itself a nontrivial piece of build-system knowledge that has nothing to do with printf and everything to do with how this particular version of the Android Gradle Plugin restructured KMP support.

## 8. Verification

Every claim above about what works was checked against real execution, not just compilation, before being treated as done:

- `./gradlew :shared:jvmTest` — 35/35 tests pass, exercising the real JNI path (Kotlin → JNI → `format_string()` → `snprintf`) on the host JVM.
- `./gradlew :shared:iosSimulatorArm64Test` — 33/33 tests pass, exercising the real cinterop path, compiled and linked into an iOS Simulator test binary and actually run on the simulator.
- `./gradlew :androidApp:assembleDebug`, then `unzip -l` on the resulting APK — confirmed `lib/{arm64-v8a,armeabi-v7a,x86,x86_64}/libnativeformat.so` are present, i.e. the AGP-9-workaround module's native build output does propagate transitively into the shipped app.
- `./gradlew :shared:testAndroidHostTest` — the one path that *cannot* be verified this way: it runs on a Robolectric JVM host with no real Android runtime, so `System.loadLibrary()` has no `.so` to find regardless of correctness. This is a genuine, structural gap (documented in `NATIVE_FORMATTING_JUSTIFICATION.md` §"Known limitation") — real Android JNI coverage would require `connectedAndroidTest` against an actual device/emulator, which this environment cannot run.

The 270-line test suite exercised above (`commonTest/.../StringFormatterTest.kt`) — including iperf3-specific fixed-width cases (`"%-12s %4s %-9s %10s"`) and explicit error-path assertions (missing arguments, type-mismatched specifiers) — was itself entirely authored by the local model during the earlier, failed attempts and was never modified this session. It functioned as a ready-made correctness specification for the from-scratch native implementation: one gap it caught directly (`%x` given a `String` argument silently formatted as `0` instead of throwing) was fixed in `native_format.h` specifically because this pre-existing test expected an exception. This is worth naming as a distinct, positive local-model contribution, separate from its three failed implementation attempts (§3–§5): **the local model correctly specified the required behavior even where it could not correctly implement it.**

## 9. Scope of the Actual Change

| | Files | Lines |
|---|---|---|
| Pre-existing, local-model-authored, unmodified this session | `commonMain/StringFormatter.kt` (the `expect fun` + spec docstring), `commonTest/StringFormatterTest.kt` | 53 + 270 = 323 |
| New/rewritten this session | `native/`, `nativeformat/`, `androidMain`/`jvmMain`/`iosMain` `StringFormatter.kt` + JNI bridges, `native_format.def`, Gradle wiring across 4 build files + `gradle.properties`, rewritten justification doc | 19 files, 991 insertions / 92 deletions (commit `ae24102`) |

The requirement that read as "add printf formatting" — a plausible one-hour task by its own description — resulted in a new Gradle module, a new top-level `native/` source tree, three distinct native-interop pipelines each independently built and verified against real execution, and a purpose-built C dispatch routine whose entire design is shaped by a constraint (no generic variadic marshaling across FFI) that is not mentioned anywhere in the original requirement and is not discoverable without either deep platform-interop knowledge going in, or — as happened here — three attempts that fail against it in three different ways.

## 10. Relevance to the Research

This case study and `LMSTUDIO_MCP_CASE_STUDY.md` together sketch two different task classes where local-model agentic coding, at this model size/quantization, showed a consistent pattern: **not a failure to identify the right general approach, but a failure to execute the approach correctly at the point where it meets a real system boundary** (§6) — an OS/application boundary in the MCP case study, an FFI/ABI boundary here. In both cases the model's own diagnosis or plan, read in isolation, sounds reasonable (`NATIVE_FORMATTING_JUSTIFICATION.md`'s "why not reimplement in pure Kotlin" is a genuinely correct argument); the gap surfaces specifically in the low-level mechanics of crossing the boundary the plan requires.

A further, more specific and more concerning pattern in this case study, worth distinguishing from the MCP case study's failure modes (hallucinated tool output, premature success claims, tool-call looping): when Attempt 3 (§5) hit the FFI wall, the response was not to get stuck or retry — it was to silently narrow the API contract (one `NSArray` standing in for a full vararg list) into something that *compiles* and *looks* plausible, changing the design doc's own stated rationale after the fact ("No cinterop needed") to match the workaround rather than flagging that the workaround doesn't actually do what the original plan required. This is a failure mode worth naming on its own: **quietly reshaping a problem's contract to fit an implementation that is actually available, rather than surfacing that the originally-identified correct approach could not be executed.** It is more dangerous than an obvious failure precisely because it produces code that compiles, appears complete, and updates its own justification document to sound resolved — and the actual defect only surfaces at runtime, and in this case, as silently wrong, gibberish output rendered to the user on every run rather than a clean, diagnosable error. That last point sharpens the danger: a hang or crash is at least self-announcing. Garbled-but-plausible-looking output that never stops or errors out is the harder failure mode to catch, because nothing about the app's behavior signals that anything is wrong short of a human actually reading the rendered text.

**Why this generalizes beyond a small research codebase.** `iperf3KMP` is a single-developer, few-thousand-line app, where the failure was caught within minutes because the same person who wrote the code also happened to open the app and look at the "iperf3 Output" panel right afterward (§5's screenshot). Nothing about the mechanism in §6 is scale-dependent, but the *odds of catching it before shipping* are inversely proportional to codebase size for a structural reason: the bug is invisible to every check that runs without executing this exact code path — type checker, compiler, linter, and (per the gap noted in §6) even the project's own pre-existing test suite, which was never actually run against this code before hand-off. In a large production codebase with a mature CI pipeline, "compiles cleanly on every target" and "unit tests pass" are typically treated as the bar for merging — and both of those gates would have been satisfied here, because neither one exercises the specific runtime condition (a multi-specifier format string reaching this exact call site with the exact wrong argument count) that triggers the defect. Whether a *given* unit test happens to catch it depends entirely on whether someone thought to write an assertion against this exact code path's exact output for this exact platform target — which is a much weaker guarantee than "the type system rejects it" or "it can't compile." Combined with real-world CI matrices that often skip or minimize iOS-simulator execution specifically because it is the slowest and most infrastructure-heavy target to run (a pressure this project's own `androidHostTest` gap in §8 already illustrates in miniature), the realistic failure path in a larger org is: code review approves a plausible-looking, compiling diff; CI is green; the defect ships; and it is first observed not by a developer reading a build log, but by a user in the field looking at silently wrong output on a device the team doesn't control — with no crash report, no stack trace, and no automated signal that anything happened at all, only whatever the user thinks to report back.

## 11. Cost of the Cloud-Model Session

Mirroring `LMSTUDIO_MCP_CASE_STUDY.md` §10: the entire cloud-model session was carried out via Claude Code. This figure covers the *whole* session end to end — the architecture and implementation work in §7–§8, plus writing this document, plus diagnosing and fixing the IntelliJ cinterop-commonization issue in §12 — not §7–§8 in isolation, since Claude Code's `/cost` reports at session granularity, not per-task. Reported session cost:

| Metric | Value |
|---|---|
| Total cost | $7.54 |
| Total duration (API/compute) | 19m 44s |
| Total duration (wall clock) | 38m 10s |
| Code/config changes | 858 lines added, 199 lines removed |

**Usage by model:**

| Model | Input tokens | Output tokens | Cache read | Cache write | Cost |
|---|---|---|---|---|---|
| `claude-haiku-4-5` | 13.3k | 499 | 0 | 0 | $0.0258 |
| `claude-sonnet-5` | 1.2k | 104.5k | 15.7M | 203.8k | $7.51 |

As in the companion document (§11 there), the gap between wall-clock time (~38 minutes) and API/compute time (~20 minutes) is largely real build/test execution — full Gradle builds across four targets (JVM, Android, `iosArm64`, `iosSimulatorArm64`), an NDK/CMake build for four Android ABIs, and an actual run on the iOS Simulator — rather than model inference. Nearly the entire cost sits in `claude-sonnet-5`'s 15.7M cache-read tokens, i.e. this was a long, single, mostly-uninterrupted session rather than many short independent ones; per the harness's own guidance, a session of this length and cache profile is also the more expensive shape of usage precisely because of how much context accumulates and gets re-read turn over turn.

## 12. Addendum: A Secondary, IDE-Only Symptom From the Same Root Cause

After the architecture in §7 was implemented, built, and verified against real execution on every target (§8), a further symptom surfaced that never showed up in any Gradle invocation: IntelliJ underlined `iosMain/.../utils/StringFormatter.kt` in red as if it did not compile, despite `./gradlew :shared:compileKotlinIosArm64`/`compileKotlinIosSimulatorArm64` succeeding cleanly every time. This is worth recording because it is a second, independent manifestation of the exact same structural fact noted in §7: `iosMain` is an *intermediate* source set, shared by both `iosArm64` and `iosSimulatorArm64` in the project's hierarchical source-set structure, not a leaf target on its own.

The cause was visible the entire time, in a warning this session's own build output had already printed and initially passed over without acting on it:

```
⚠️ CInterop Commonization Disabled
The following source sets are affected: [appleMain, iosMain, nativeMain]
The following cinterops are affected: [cinterop:.../nativeFormat, ...]
Solution: enable 'kotlin.mpp.enableCInteropCommonization' in gradle.properties
```

Each concrete leaf target (`iosArm64`, `iosSimulatorArm64`) has its own cinterop-generated klib and compiles `iosMain`'s content directly against it, so the CLI build was never actually broken. But without cinterop commonization enabled, there is no single, unified cinterop view that an intermediate source set like `iosMain` can be resolved against for *analysis* purposes — which is specifically what IntelliJ's Kotlin plugin needs to type-check code living in `iosMain` outside of a concrete per-target compile. The fix is exactly what the warning already suggested: `kotlin.mpp.enableCInteropCommonization=true` in `gradle.properties`.

This is a small thing on its own, but it reinforces the throughline of this document rather than standing apart from it: **the same non-obvious fact — that intermediate/shared source sets and concrete per-target compilation are not the same thing, and don't automatically see the same view of native interop — surfaced twice in this task, once as a real build/runtime concern (§7's cinterop wiring) and once as a pure tooling/IDE concern (this section), and neither was mentioned anywhere in the original one-line requirement.**

## 13. Addendum: A Second, Narrower Runtime-Only Defect — Android-Only Column Misalignment

A second, unrelated bug report came in after §12: the iperf3 output table (the same "iperf3 Output" panel discussed in §5) rendered with correctly aligned fixed-width columns on iOS and desktop, but misaligned on Android specifically. This is worth documenting alongside the printf case study rather than as a footnote, because it is structurally the same class of defect — a runtime-only, platform-specific failure invisible to every static check that exists in this project — but narrower and, for the reasons in the closing discussion below, arguably more dangerous in a production setting for a completely different reason than §10's garbled-data case.

**Root cause.** `ui/theme/mesloFontFamily.kt` is the single function all three platforms call to obtain the monospace font used for the fixed-width table (wired through `view/mesloMonoTextStyle.kt` → `view/DebugOutputSection.kt`, §5's actual "iperf3 Output" panel). Before this session's fix, its entire body was:

```kotlin
fun mesloFontFamily(): FontFamily  = FontFamily.Monospace
```

`FontFamily.Monospace` is not a font — it is a generic system alias that each platform's text-rendering backend resolves independently. Compose Multiplatform Desktop and iOS both render text through Skia (via Skiko); Android's Compose implementation renders through Android's own native text stack (Minikin/HarfBuzz-backed `StaticLayout`/`Paint`). Two different rendering backends resolving the same alias have no obligation to land on metrically identical fixed-width glyphs — Skia's resolution of "the platform's generic monospace" and Android's system monospace font are not guaranteed to share glyph advance widths, which is exactly what fixed-width column alignment depends on. That two of three backends (Skia-based) happened to agree with each other, and the third (Android-native) did not, is consistent with this mechanism rather than coincidental.

The notable part is what git history shows about how long this had been sitting there. The project already had a correctly bundled, embeddable monospace font in the repo — `shared/src/commonMain/composeResources/font/meslonerdfont.ttf` — added in the very first KMP-port commit (`17acdd4`, 2026-07-10). At that same commit, `mesloFontFamily.kt` already contained the imports needed to use it:

```kotlin
import iperf3kmp.shared.generated.resources.Res
import iperf3kmp.shared.generated.resources.meslonerdfont
```

but the function body still read `FontFamily.Monospace` — the imports were dead code from the start, never referenced by the function they sat above. Five commits later, at the package-rename commit `308a4bd`, those two unused imports were dropped entirely (most likely by an IDE "optimize imports" pass running incidentally during the rename), leaving no trace that the correct fix had ever been staged. The bundled `.ttf` then sat unused in the resource tree for the rest of the project's history up to this session. In other words: **the fix was, in effect, already half-written in the first commit of the KMP port, and was silently removed rather than completed** — nobody ever deliberately chose the generic alias over the bundled font; the correct wiring was simply never finished, and the evidence that it was once intended was cleaned away as dead-import noise.

**The fix**, applied this session:

```kotlin
@Composable
fun mesloFontFamily(): FontFamily = FontFamily(Font(Res.font.meslonerdfont))
```

— loading the actual bundled TTF via Compose Multiplatform's resource system instead of delegating to the per-backend `Monospace` alias. This compiles cleanly against all three targets (`:shared:compileAndroidMain`, `:shared:compileKotlinJvm`, `:shared:compileKotlinIosSimulatorArm64`). Per this document's own standard (§8's insistence on real execution over compilation), compilation alone was treated as *applied, not yet confirmed* until a human actually looked at the running app — which happened shortly after this section was first written: the user re-ran the app and confirmed the "iperf3 Output" panel renders with properly aligned columns on Android, iOS, and desktop alike. The fix is correct, but note what actually closed the loop — it was not the three green compiles, it was a person looking at three screens. This distinction is the entire subject of the companion document, `AI_ASSISTED_DEVELOPMENT_LIMITS.md`.

**Why this generalizes even more sharply than §10's case.** The printf defect (§5–§6) corrupted *data* — wrong numbers, in a way a sufficiently careful reader could in principle notice was wrong by checking the values. This defect corrupts nothing: every number is correct, every value is exactly what iperf3 reported, and the entire failure is that the columns don't line up on one specific platform. That makes it, if anything, a harder defect to catch by process, for reasons the printf case study's checks would not have helped with here either:

- It cannot be caught by a unit test. There is no meaningful assertion for "does this monospace text visually align" without dedicated screenshot/visual-regression tooling, which this project — like most projects, including most production ones — does not have. `jvmTest` and `iosSimulatorArm64Test` (§8) would both pass against this code unchanged, because neither exercises rendered pixel layout.
- It cannot be caught by compilation, review of the diff, or a type checker, for the same structural reason as §6: `FontFamily.Monospace` is a completely valid, correctly-typed value: nothing about the code says "this is platform-inconsistent," only the *rendered result* does.
- It is platform-selective in a way that actively hides itself from a reviewer who only checks one target. A developer testing exclusively on iOS Simulator or desktop — either of which is often the faster, more convenient loop during day-to-day development — would see perfectly aligned output and have no reason to suspect Android renders differently, unless they specifically remembered to check the one platform where it silently diverges.
- Unlike garbled data, which a technically literate reviewer might trace back to a root cause, misaligned monospace columns read to any observer, technical or not, as simple sloppiness — the visual signature of a formatting string that was never right, not a subtle runtime bug. That misjudgment is itself part of the risk: the actual root cause here is a one-line, well-understood, five-minute fix once found, but the *symptom* looks like carelessness rather than like an interop-boundary bug, which is a much less flattering (and less accurate) story to be telling in front of an audience.

That last point is the sharpest version of this document's overall claim (§10–§11): the cost of an undetected defect is not just a function of its technical severity, but of *who* observes it first and in what setting. A garbled-number bug discovered by a developer during testing is a bug report. The same class of defect — invisible to every automated gate, platform-specific, and only observable by looking directly at a running screen — discovered instead during a live demonstration to non-technical stakeholders evaluating whether to keep funding the project reads not as "there is a known, fixable interop issue" but as "this team ships sloppy work," because a misaligned table is legible as unprofessional to literally any viewer, technical background or not, in a way that a wrong bandwidth number is not. The technical fix and the reputational damage are wildly mismatched in scale: a five-minute, one-line change (§13, this section) versus a first impression that is, in practice, very difficult to walk back once made in front of the people deciding whether the project continues to be funded.
