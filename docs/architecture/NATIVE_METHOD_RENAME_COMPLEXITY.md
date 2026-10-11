# Renaming a Native Bridge Function Is a Multi-Layer Refactor

## Why this is its own document

A plain Kotlin rename (IDE "Rename Symbol") is safe because every reference
lives in one language, resolved by one compiler, indexed by one cache. A
rename of a function reachable through this project's native bridges
(`native/iperf3_bridge.h`/`iperf3_jni.c`, or the sibling
`native/native_format.h`/`native_format_jni.c` pair — see
[NATIVE_FORMATTING_JUSTIFICATION.md](NATIVE_FORMATTING_JUSTIFICATION.md) and
[IPERF3_NATIVE_INTEGRATION.md](IPERF3_NATIVE_INTEGRATION.md)) is not that:
one logical function has up to **five independent name bindings, using two
different naming mechanisms**, each with its own build/IDE cache. Treat it
as a checklist, not a find-and-replace.

## Incident: `iperf3_bridge_run_live` → `iperf3_bridge_native_iperf3`

On 2026-08-27, renaming the core bridge function surfaced errors on iOS
only, even after the rename looked complete:

```
e: NativeIperfBridge.kt:5:78 Unresolved reference 'iperf3_bridge_native_iperf3'.
e: NativeIperfBridge.kt:101:17 Unresolved reference 'iperf3_bridge_native_iperf3'.
```

The Kotlin and C sides were already consistent — the problem was a stale
Gradle cinterop cache (below). Separately, a real bug was found in the
JNI path that a compile-only check wouldn't have caught: `native/
iperf3_jni.c`'s call site still invoked the *old* name. That's legal C
(implicit declaration of a function is a warning, not an error) and so it
type-checked fine — it only fails at **link** time. "It compiled" is not
proof a native rename is complete.

## The five bindings

1. **The C function itself** — declared in `native/iperf3_bridge.h`,
   defined in `native/iperf3_bridge.c`.
2. **Every call site** in `native/iperf3_jni.c` — a stale call site
   compiles fine and only fails at link time (see incident above).
3. **The JNI-exported name** — an independent string, unrelated to the C
   function name. Both the `JNINativeMethod` table entry in `iperf3_jni.c`
   and the matching Kotlin `external fun` in `NativeIperfBridge.kt`
   (`androidMain`/`jvmMain`) must agree with *each other*, but neither has
   to match the C name.
4. **The cinterop binding on iOS** — the opposite convention: cinterop
   generates a Kotlin declaration with the *same* name as the C function
   (parsed straight out of the header via the `.def` file), so
   `NativeIperfBridge.kt` (`iosMain`) must track the C name exactly — but
   only once the cinterop klib has actually been regenerated (see next
   point).
5. **Two caches that don't invalidate on a header edit:**
   - Gradle's `cinteropIperf3BridgeIos<Target>` task can report UP-TO-DATE
     against a header that changed underneath it. Fix: force it explicitly —
     `./gradlew :shared:cinteropIperf3BridgeIosArm64 --rerun`.
   - IntelliJ's Kotlin plugin indexes the generated klib independently of
     Gradle's own task-output cache. A clean Gradle build does **not**
     imply the IDE has caught up — clear via *File → Invalidate Caches*.

## Checklist for the next native rename

When renaming any function reachable through `iperf3_bridge.h`/
`iperf3_jni.c` (or `native_format.h`/`native_format_jni.c`):

1. Update the C declaration/definition and **every** call site, including
   `native/iperf3_jni.c` itself — grep for the old name across `native/`,
   don't rely on the compiler to find it.
2. If it's JNI-exported: update the `JNINativeMethod` table entry *and* the
   matching Android/JVM `external fun` name — these are independent of the
   C name and of each other's history.
3. If it's reachable via cinterop (iOS): update the iOS-side Kotlin call to
   match the new C name, then force-rerun the `cinteropIperf3BridgeIos*`
   Gradle task before trusting `compileKotlinIos*` output.
4. Sweep comments and `IPERF_LOG(...)`-style log strings for the old name —
   cosmetic, but drift here is a sign the rename wasn't done carefully and
   makes future debugging (grepping logs) miss the current name.
5. Verify with a **real link/run**, not just a compile: `:shared:jvmTest`
   (JNI, exercises the actual symbol at runtime) plus
   `:shared:compileKotlinIosArm64`/`IosSimulatorArm64` (cinterop).
6. Clear IntelliJ's caches before concluding "still broken" if the IDE
   disagrees with a green Gradle build — it's checking a different cache.
