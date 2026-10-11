# iperf3KMP — Documentation Index

Quick-reference guide to all markdown files in this repository, organized by purpose.

---

# Tracking - Project Complete

---

## Documentation layout

| Folder | Contents |
|--------|----------|
| [docs/architecture/](docs/architecture) | How the app works: native iperf3 integration, native formatting, Room persistence, diagrams, capabilities overview |
| [docs/desktop/](docs/desktop) | Windows/desktop decision history, Windows ARM64 plan, the desktop-complexity research note |
| [docs/platform-builds/](docs/platform-builds) | Per-platform build checklists and the [test matrix](docs/platform-builds/TEST_MATRIX.md) (index: [PLATFORM_BUILD_NOTES.md](PLATFORM_BUILD_NOTES.md)) |
| [docs/research/](docs/research) | Project research artifacts — case studies, AI-assisted-development thesis, code-sharing analysis, retrospective, costs |
| [docs/project/](docs/project) | Status and planning: TODO, MVP assessment, store and ICSE tracking |

---

## Project Setup & Operations

| File | Purpose |
|------|---------|
| [README.md](./README.md) | Standard KMP project overview — explains `shared/`, `androidApp/`, `iosApp/`, `desktopApp/` structure and basic run/test commands. |
| [CLAUDE.md](./CLAUDE.md) | Detailed project structure, key Gradle commands, gotchas (minSdk 26, compileSdk 36), architecture notes (ViewModel, SimulatedRun runner). |
| [AGENTS.md](./AGENTS.md) | Superseeded by CLAUDE.md |

---

## Case Studies & Research Documents

### Printf-Style Formatting Across KMP Targets

| File | Purpose |
|------|---------|
| [PRINTF_FORMATTING_CASE_STUDY.md](docs/research/PRINTF_FORMATTING_CASE_STUDY.md) | Three failed local-model attempts at printf-style formatting across KMP targets. Root cause: Kotlin `vararg args: Any` cannot cross FFI boundaries as a native variadic list. Solution: one C header (`native_format.h`) with tagged-union `FormatArg[]`, reached via JNI (Android/desktop) and cinterop (iOS). Also documents two addendums — an IDE-only IntelliJ bug (§12) and an Android-only font-alignment defect (§13). |
| [NATIVE_FORMATTING_JUSTIFICATION.md](docs/architecture/NATIVE_FORMATTING_JUSTIFICATION.md) | Technical justification for the native C approach. Explains why a tagged-argument array was chosen over C varargs, details each platform's interop mechanism, lists supported format specifiers, and documents the `androidHostTest` limitation (Robolectric can't load `.so`). |

### MCP Tool Access & Local LLM Debugging

| File | Purpose |
|------|---------|
| [LMSTUDIO_MCP_CASE_STUDY.md](docs/research/LMSTUDIO_MCP_CASE_STUDY.md) | Debugging MCP tool access for local LLMs across 3 machines (macOS, Nvidia GB10, AMD Strix Halo). Three stacked root causes: GUI PATH resolution (§4), macOS Local Network privacy permission (§5), and a personal `.zshrc` bug that silently dropped non-interactive shell commands (§6). Also discusses safety implications of local-model agents with unrestricted tool access (§12) and preliminary local-vs-cloud capability comparison (§11). |

### AI-Assisted Development — Core Thesis

| File | Purpose |
|------|---------|
| [AI_ASSISTED_DEVELOPMENT_LIMITS.md](docs/research/AI_ASSISTED_DEVELOPMENT_LIMITS.md) | Core research thesis: there's a category of correctness (perceptual/visual verification — "does it look right") that no AI model can verify on its own, regardless of strength. Taxonomy of 4 verification categories; argues the gap is structural (missing sensory channel), not capability-dependent. Uses this repo's own history as evidence. |
| [COSTS.md](docs/research/COSTS.md) | Some Claude code costs |

### iperf3 Native Integration — Planning

| File | Purpose |
|------|---------|
| [IPERF3_NATIVE_INTEGRATION.md](docs/architecture/IPERF3_NATIVE_INTEGRATION.md) | Planning doc for integrating real iperf3 3.21 as a native library (not yet implemented). Architecture: in-process `libiperf` on all three targets, using `--json-stream` output, with the same JNI/cinterop pattern as printf work. Lists open technical questions to resolve during implementation. |
| [NATIVE_METHOD_RENAME_COMPLEXITY.md](docs/architecture/NATIVE_METHOD_RENAME_COMPLEXITY.md) | Renaming a JNI/cinterop-reachable native function touches 5 independent name bindings across 2 mechanisms plus 2 stale-cache traps (Gradle cinterop task, IntelliJ klib index) — checklist derived from a real 2026-08-27 incident renaming `iperf3_bridge_run_live`. |


### Transition an Android-only application with RoomDB and DI to KMP
| File | Purpose |
|------|---------|
| [CODELAB_ROOMDB.md](docs/architecture/CODELAB_ROOMDB.md) | This document was written using a codelab exercise to convert a Room-based Android app to KMP. |
| [ROOMDBPLAN.md](docs/architecture/ROOMDBPLAN.md) | Plan for adding Room + KMP SQLite persistence to  iperf3KMP , using the pattern proven in the  migrate-room  codelab |

### Windows Desktop — shipped (decision originally against it, later reversed)
| File | Purpose |
|------|---------|
| [WINDOWS_DECISION_REVERSAL_CASE_STUDY.md](docs/desktop/WINDOWS_DECISION_REVERSAL_CASE_STUDY.md) | How/why the original "not supported" call was reversed, and what it actually cost (current) |
| [DESKTOP_SUPPORT_COMPLEXITY_NOTE.md](docs/desktop/DESKTOP_SUPPORT_COMPLEXITY_NOTE.md) | Research note: what full desktop support (3 OSes, 2 CPU architectures, distro baselines) actually took |
| [WINDOWS_ARM64_IPERF3_PLAN.md](docs/desktop/WINDOWS_ARM64_IPERF3_PLAN.md) | Planning doc for a native (non-Cygwin) `iperf3.exe` on Windows ARM64 — not started |
| [WINDOWS_DESKTOP_APP.md](docs/desktop/WINDOWS_DESKTOP_APP.md) | Historical: the original "not supported" rationale, preserved as-written |

---


### Project Status

| File | Purpose |
|------|---------|
| [AI_TEAM_COSTING.md](docs/project/AI_TEAM_COSTING.md) | Estimate of team size, skills, hardware lab and AI costs (metered vs subscription) for a team build, with a local-vs-cloud break-even model; recorded figures separated from estimates |
| [LOCAL_VS_CLOUD_BENCHMARK.md](docs/research/LOCAL_VS_CLOUD_BENCHMARK.md) | Benchmark protocol and 13 real defect cases (with answer keys) for scoring local vs cloud models; no results yet |
| [MVP_MATURITY_ASSESSMENT.md](docs/project/MVP_MATURITY_ASSESSMENT.md) | Justifies calling the project a mature MVP: real (non-simulated) native execution on all 3 targets — including iOS, manually verified end-to-end against live remote servers on Simulator, iPhone 17 Pro, and iPad M4 — real persistence/export, real packaging, and a track record of root-caused writeups, plus an honest list of what's still open (Windows `.msi` verification, automated Room round-trip test). |
| [iperf3KMP Capabilities and Architecture Overview.docx](<docs/architecture/iperf3KMP Capabilities and Architecture Overview.docx>) | Word document for course submission: comprehensive feature outline plus the platform-independence/code-sharing case — measured code-sharing metrics (~88% of custom code single-sourced), the JNI+cinterop native integration pattern, the multi-threaded bridge architecture, and the third-party `esnet/iperf` dependency, presented despite (not by ignoring) that complexity. |
| [STORE_TODOS.md](docs/project/STORE_TODOS.md) | Scoping/tracking doc for commercial distribution on 3 stores (Apple App Store iOS + macOS, Google Play): branding/naming constraints from `third_party/iperf`'s license, the app-identifier rebrand, per-store submission requirements, and the near-term-per-store vs. eventual-independent-channel purchase strategy. Nothing started yet — scoping only. |
| [ICSE_SUBMISSION_TRACKING.md](docs/project/ICSE_SUBMISSION_TRACKING.md) | Tracking doc for the ICSE 2027 conference-presentation goal: verified deadlines/criteria fetched directly from the official CFP pages, the finding that the main Research Track deadline has already passed, and a fit comparison across the real still-open candidates (NIER, Tool Demonstration, SEIP, the UISE workshop) plus MOBILESoft (not yet confirmed for 2027). No track chosen yet. |
| [ICSE 2027 Submission Tracking.docx](<docs/project/ICSE 2027 Submission Tracking.docx>) | Word version of the above, for the professor's review. |

---

## Key Context for This Project

- **Current state**: Complete: Interactive application for all supported platforms with iperf3 integration and roomdb persistence.
- **4 modules**: `:shared` (KMP library), `:androidApp`, `:desktopApp`, `:nativeformat` (standalone CMake/NDK module)
- **Native interop pattern proven end-to-end**: JNI ×2 (Android + desktop JVM), cinterop ×1 (iOS) — all three mechanisms are working and tested
- **Research context**: Part of Independent research on local LLM tooling for agentic workflows across heterogeneous hardware
