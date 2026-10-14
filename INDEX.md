# iperf3KMP — Documentation Index

Quick-reference guide to all markdown (and a couple of Word) files in this repository, organized by purpose.

---

## Documentation layout

| Folder | Contents |
|--------|----------|
| [docs/architecture/](docs/architecture) | How the app works: native iperf3 integration, native formatting, a capabilities/architecture overview, diagrams |
| [docs/desktop/](docs/desktop) | Windows/desktop decision history, the Windows ARM64 plan, a desktop-complexity note |
| [docs/platform-builds/](docs/platform-builds) | Per-platform build checklists and the [test matrix](docs/platform-builds/TEST_MATRIX.md) (index: [PLATFORM_BUILD_NOTES.md](PLATFORM_BUILD_NOTES.md)) |
| [docs/research/](docs/research) | Case studies and research notes written while building this: native-interop formatting, a code-sharing measurement, an AI-assisted-development thesis, a local-vs-cloud model benchmark protocol |
| [docs/project/](docs/project) | Status and planning: TODO, MVP maturity assessment |

---

## Project Setup & Operations

| File | Purpose |
|------|---------|
| [README.md](./README.md) | Standard KMP project overview — explains `shared/`, `androidApp/`, `iosApp/`, `desktopApp/` structure and basic run/test commands. |
| [CLAUDE.md](./CLAUDE.md) | Detailed project structure, key Gradle commands, gotchas (minSdk 27, compileSdk/targetSdk 37), architecture notes. |
| [AGENTS.md](./AGENTS.md) | Compact cross-tool entry point (commands + top gotchas); points to `CLAUDE.md` for the full detail. |

---

## Case Studies & Research Documents

### Printf-Style Formatting Across KMP Targets

| File | Purpose |
|------|---------|
| [PRINTF_FORMATTING_CASE_STUDY.md](docs/research/PRINTF_FORMATTING_CASE_STUDY.md) | Three failed local-model attempts at printf-style formatting across KMP targets. Root cause: Kotlin `vararg args: Any` cannot cross FFI boundaries as a native variadic list. Solution: one C header (`native_format.h`) with tagged-union `FormatArg[]`, reached via JNI (Android/desktop) and cinterop (iOS). Also documents two addendums — an IDE-only IntelliJ bug (§12) and an Android-only font-alignment defect (§13). |
| [NATIVE_FORMATTING_JUSTIFICATION.md](docs/architecture/NATIVE_FORMATTING_JUSTIFICATION.md) | Technical justification for the native C approach. Explains why a tagged-argument array was chosen over C varargs, details each platform's interop mechanism, lists supported format specifiers, and documents the `androidHostTest` limitation (Robolectric can't load `.so`). |

### AI-Assisted Development

| File | Purpose |
|------|---------|
| [AI_ASSISTED_DEVELOPMENT_LIMITS.md](docs/research/AI_ASSISTED_DEVELOPMENT_LIMITS.md) | Core thesis: there's a category of correctness (perceptual/visual verification — "does it look right") that no AI model can verify on its own, regardless of strength. Taxonomy of 4 verification categories; argues the gap is structural (missing sensory channel), not capability-dependent. Uses this repo's own history as evidence. |
| [LOCAL_VS_CLOUD_BENCHMARK.md](docs/research/LOCAL_VS_CLOUD_BENCHMARK.md) | Benchmark protocol and 13 real defect cases (with answer keys) for scoring local vs. cloud models; no results recorded yet. |

### iperf3 Native Integration

| File | Purpose |
|------|---------|
| [IPERF3_NATIVE_INTEGRATION.md](docs/architecture/IPERF3_NATIVE_INTEGRATION.md) | Design/implementation doc for real iperf3 3.21 as an in-process native library on all three targets (JNI on Android/desktop, cinterop on iOS) — the approach this repo ships today. Covers the JNI/core split, the iOS static-library build, and the open technical questions resolved along the way. |
| [NATIVE_METHOD_RENAME_COMPLEXITY.md](docs/architecture/NATIVE_METHOD_RENAME_COMPLEXITY.md) | Renaming a JNI/cinterop-reachable native function touches 5 independent name bindings across 2 mechanisms plus 2 stale-cache traps (Gradle cinterop task, IntelliJ klib index) — a checklist derived from a real rename incident. |
| [CODE_SHARING_ANALYSIS.md](docs/research/CODE_SHARING_ANALYSIS.md) | Measures cross-platform code sharing against an 80%-of-custom-code goal, counting the native-interop layer as single-sourced since it's reached from each platform through its own FFI mechanism rather than reimplemented per platform. Recomputed each time the codebase shifts meaningfully; as of 2026-10-13 all three methodologies have dropped below the 80% goal, driven by the desktop CLI's terminal-output rework. |
| [iperf3KMP Capabilities and Architecture Overview.docx](<docs/architecture/iperf3KMP Capabilities and Architecture Overview.docx>) | Standalone feature outline plus the platform-independence/code-sharing case — measured code-sharing metrics, the JNI+cinterop native integration pattern, the multi-threaded bridge architecture, and the third-party `esnet/iperf` dependency, presented despite (not by ignoring) that complexity. |

### Windows Desktop — shipped (decision originally against it, later reversed)

| File | Purpose |
|------|---------|
| [WINDOWS_DECISION_REVERSAL_CASE_STUDY.md](docs/desktop/WINDOWS_DECISION_REVERSAL_CASE_STUDY.md) | How/why the original "not supported" call was reversed, and what it actually cost. |
| [DESKTOP_SUPPORT_COMPLEXITY_NOTE.md](docs/desktop/DESKTOP_SUPPORT_COMPLEXITY_NOTE.md) | What full desktop support (3 OSes, 2 CPU architectures, distro baselines) actually took. |
| [WINDOWS_ARM64_IPERF3_PLAN.md](docs/desktop/WINDOWS_ARM64_IPERF3_PLAN.md) | Planning doc for a native (non-Cygwin) `iperf3.exe` on Windows ARM64 — not started; today's Windows ARM64 build runs the x86_64 `iperf3.exe` under Prism emulation. |
| [WINDOWS_DESKTOP_APP.md](docs/desktop/WINDOWS_DESKTOP_APP.md) | Historical: the original "not supported" rationale, preserved as-written. |

---

### Project Status

| File | Purpose |
|------|---------|
| [MVP_MATURITY_ASSESSMENT.md](docs/project/MVP_MATURITY_ASSESSMENT.md) | Justifies calling the project a mature MVP: real (non-simulated) native execution on all 3 targets — including iOS, manually verified end-to-end against live remote servers on Simulator and real hardware — real persistence/export, real packaging, and a track record of root-caused writeups, plus an honest list of what's still open. |
| [TODO.md](docs/project/TODO.md) | Open items and the release gate for the current version. |

---

## Key Context for This Project

- **Current state**: interactive application for all supported platforms (Android, iOS, desktop: macOS/Linux/Windows) with real iperf3 integration and Room/SQLite persistence.
- **5 modules**: `:shared` (KMP library), `:androidApp`, `:desktopApp`, `:nativeformat` and `:iperf3native` (standalone CMake/NDK modules)
- **Native interop pattern proven end-to-end**: JNI ×2 (Android + desktop JVM), cinterop ×1 (iOS) — all three mechanisms are working and tested.
