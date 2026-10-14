# iperf3KMP — Code Sharing Analysis

**Measured Cross-Platform Code Sharing Against the 80% Project Goal**

Jerold Abramson • Independent Research Study • Prepared 2026-08-03 • Updated 2026-09-12 • Updated 2026-10-13

> **Result: 70.4% – 73.5% shared**, depending on methodology — down from 76.6% – 81.7% in September and 82.0% – 87.7% in August. **All three methodologies are now below the 80% goal**, for the first time since this analysis began. See "What Changed Since September" below — the cause this time is concentrated almost entirely in one place, not spread across the codebase.

One of the project's founding goals was at least 80% code sharing across the three delivered platforms (Android, iOS, Desktop/JVM). This document measures that goal directly against the current tracked source tree, rather than estimating it. Three methodologies are reported side by side, from strictest to most inclusive of this project's specific architecture.

## Results by Methodology

| Methodology | Shared lines | Platform-specific lines | Total | Shared % |
|---|---|---|---|---|
| Kotlin production code only — `commonMain` vs. all `androidMain`/`iosMain`/`jvmMain` actuals plus the three app-shell entry points (`MainActivity.kt`, `main.kt`, `iOSApp.swift`) | 5,995 | 2,161 | 8,156 | **73.52%** |
| Same as above, with unit/instrumented tests included (`commonTest`, `androidHostTest`, `iosTest`, `jvmTest`) | 6,276 | 2,376 | 8,652 | **72.54%** |
| Same as row 2, plus the native C interop layer — the platform-agnostic bridge (`native_format.h`, `iperf3_bridge.c`/`.h`) counted as shared; the JNI adapters and per-OS config headers counted as platform-specific | 7,112 | 2,982 | 10,094 | **70.44%** |

## Raw Line Counts by Source Set

Physical line counts (`wc -l`) per Gradle source set / native file, from the tracked source tree (`git ls-files`), as of this update.

| Source set / file | Lines | Scope |
|---|---|---|
| `shared/src/commonMain` (Kotlin) | 5,995 | Shared — all 3 platforms |
| `shared/src/commonTest` (Kotlin) | 281 | Shared — all 3 platforms |
| `native/native_format.h` + `iperf3_bridge.c`/`.h` | 836 | Shared — all 3 platforms (JNI + cinterop) |
| `shared/src/androidMain` (Kotlin) | 232 | Android only |
| `shared/src/androidHostTest` (Kotlin) | 11 | Android only (test) |
| `androidApp/` (Kotlin, `MainActivity.kt`) | 26 | Android only |
| `shared/src/iosMain` (Kotlin) | 498 | iOS only |
| `shared/src/iosTest` (Kotlin) | 58 | iOS only (test) |
| `iosApp/` (Swift, `ContentView.swift` + `iOSApp.swift`) | 30 | iOS only |
| `shared/src/jvmMain` (Kotlin) | 523 | Desktop only |
| `shared/src/jvmTest` (Kotlin) | 146 | Desktop only (test) |
| `desktopApp/` (Kotlin: `main.kt` + `cli/AnsiTerminal.kt`, `cli/CliArgs.kt`, `cli/CliRunner.kt`) | 852 | Desktop only |
| `native/iperf3_jni.c` + `native_format_jni.c` | 371 | Android + Desktop only (JNI adapters) |
| `native/iperf_config_android.h` | 91 | Android only (native build config) |
| `native/iperf_config_ios.h` | 100 | iOS only (native build config) |
| `native/native_log.h` | 44 | iOS only (`os_log()` shim for developer-mode logging) |

## Notes on Methodology

**Source of truth.** All counts are physical lines (`wc -l`) over files returned by `git ls-files` against the current tracked tree — i.e. exactly what's tracked in version control, not an estimate or a build-tool report.

**The native C layer is the one architectural wrinkle.** Most of this project's cross-platform logic is Kotlin, sharable via `commonMain` in the ordinary KMP way. The one exception is the real iperf3 client integration, written in C for performance/library-compatibility reasons. Its core logic (test lifecycle, threading, stall detection, output filtering) is written once and reached identically by JNI on Android/desktop and by Kotlin/Native cinterop on iOS — genuinely shared source, just not Kotlin source. Only the thin JNI glue and two per-OS config headers are truly single-platform.

**Deliberately excluded from every row: build/infrastructure code.** `third_party/iperf` (esnet's own upstream C source — not code this project wrote, so it says nothing about this project's own sharing discipline either way), and all pure build/packaging configuration — `*.gradle.kts`, `CMakeLists.txt`, `Info.plist`, `.pbxproj`, Android XML resources, Room's generated schema JSON, the new `install.sh`/`install.ps1` installer scripts, and CI/signing tooling. None of that is *runtime* code — it never ships inside the app or runs on a user's device — and none of it is shareable in any multi-platform project, KMP or otherwise, so folding it into the ratio would understate sharing without reflecting anything about the architecture. This is the runtime-code vs. build/infrastructure-code split: the table above measures only the former; the latter is real engineering effort (and has grown this cycle too — see the macOS notarization Gradle task and the installer scripts) but isn't counted here because it was never a candidate for cross-platform sharing in the first place.

**Why three rows instead of one number.** Including tests and the native layer both pull the shared percentage down further from the strictest, most conventional definition of "KMP code sharing" (production Kotlin only). The most inclusive, most conservative row (70.4%) is the number this document has always said to cite if asked to defend the figure under scrutiny.

## What Changed Since September, and Why This Time Is Different

The September measurement and this one used the identical methodology (`git ls-files`, `wc -l`, the same three-row structure) against a codebase that kept growing in the interim. Unlike the August→September drop — which was attributable to two separate developments (Windows desktop support, diagnostic logging) spread across multiple platforms — this drop is concentrated almost entirely in one place: **the desktop CLI's terminal-output rework.**

`desktopApp/`'s Kotlin grew from 50 lines to 852 (+802), almost all of it in a new `cli/` package:

- `cli/CliRunner.kt` (525 lines) — the CLI's test-execution loop
- `cli/CliArgs.kt` (144 lines) — argument parsing
- `cli/AnsiTerminal.kt` (117 lines) — ANSI escape-code rendering for live terminal output
- `main.kt` itself grew by 16 lines (50 → 66) to dispatch into the CLI path when arguments are passed

None of this is shareable by nature: it exists specifically because the desktop build offers a CLI mode in addition to its GUI, which Android and iOS don't have and have no reason to have. `commonMain` (+42 lines) and `jvmMain` (+22 lines) moved only slightly in comparison — this cycle's growth is almost entirely platform-specific, not shared.

This is a cleaner example of the same tension the September update raised: a real, user-facing capability (rich, properly-formatted terminal output for the CLI) that is correctly platform-specific by nature, pushing every methodology's number down together rather than trading off against the others. As before, this isn't an architectural regression — nobody wrote unnecessarily platform-specific code to hit a number — but it is the first time all three rows have landed below the project's original 80% goal at the same time.

### A Second Sharing Axis: the CLI Is Itself Shared Across All Desktop OSes

Every table above measures sharing along one axis only — Android vs. iOS vs. Desktop as three siblings. All 852 lines of the new CLI code count as "platform-specific" on that axis, because Android and iOS have no CLI at all. But there's a second, orthogonal sharing claim worth stating explicitly: *within* the Desktop target, that same CLI code is itself single-sourced across macOS, Linux, and Windows — the same `.jar`, unmodified, is what `packageDmg`/`packageDeb`/`packageMsi` each bundle. Checked directly: of `CliRunner.kt`, `CliArgs.kt`, `AnsiTerminal.kt`, and `main.kt`'s CLI dispatch, exactly one OS-conditional branch exists in the whole set — `AnsiTerminal.kt` checks `System.getProperty("os.name")` to decide whether to assume ANSI escape-code support on Windows versus checking the `TERM`-style convention elsewhere. Every other line — argument parsing, the test-execution loop, output formatting — runs identically on all three desktop OSes.

So the 852 "platform-specific" lines the main tables charge against Desktop are almost entirely *shared across Desktop's own three OS targets*, in exactly the same sense that `commonMain` is shared across Android/iOS/Desktop. It's a legitimate sharing argument, just answering a different question than the headline percentage does — "how much of the Desktop-only code is itself reused across macOS/Linux/Windows" rather than "how much of the whole app is reused across Android/iOS/Desktop." The two claims aren't in tension; they're just orthogonal axes of the same underlying discipline (write it once, run it everywhere that axis covers), and this document's headline numbers only ever measure the first one.

There's a sharper version of this same tension worth stating plainly, not just implying: this app's code-sharing percentage is partly a function of how uniform its UI and feature surface were allowed to stay, not purely a measure of engineering discipline. The GUI is entirely Compose Multiplatform text and form fields, with no charts/graphs for visualizing results over time, and the desktop build's GUI is a Compose window, not a deeply OS-integrated application — no native menu bar, no OS-specific chrome. A version of this app with real result-history graphs, a native macOS/Windows menu bar, or platform-idiomatic navigation instead of one shared screen layout would need meaningfully more platform-specific code — and would likely score lower on every row of the table above. That wouldn't be a worse app. It would be a more ambitious one, spending its platform-specific budget on native richness (the CLI being exactly one such example) instead of holding it entirely in reserve for a uniform cross-platform surface.

This suggests a broader thesis worth stating alongside the numbers, even though this document cannot fully test it alone: full commercial application development still requires a skilled, multi-disciplinary development team — iOS, Android, and desktop UI/CLI specialists among them, not just the native-systems engineering this project's own hardest work represents. AI assistance, frontier or local, is a genuine force multiplier for that team, as this project's development history documents in detail. But augmentation is not substitution: the scope of a real commercial release — richer, platform-idiomatic experiences across three targets, and the breadth of manual verification a small team can parallelize and a single person cannot — appears to exceed what one human working with current AI tooling can produce or credibly validate alone.
