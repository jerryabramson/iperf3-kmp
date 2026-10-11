# KMPCOMPARISONS.md

Plan for the next phase of the research project: comparing `iperf3KMP` (the shared-code Kotlin Multiplatform app) against a **pure native Android** app and a **pure native iOS** app, on code size, code complexity, and runtime resource usage. This is a plan only — no implementation yet, and it intentionally leaves some decisions open rather than pre-deciding them.

## Goal

Produce a defensible, evidence-based answer to: *what does choosing KMP actually cost or save, relative to writing the same app twice, once per platform, in each platform's native language?* The [Code Sharing Analysis](./Code%20Sharing%20Analysis.docx) work already answered "how much code is shared" for the KMP app in isolation; this phase compares that KMP app against two genuinely independent native implementations of the same feature set, not just against itself.

Three metric categories, all three apps:

1. **Code size** — lines of code, by language and by layer.
2. **Code complexity** — a cross-platform-comparable complexity metric (see "Code complexity methodology" below).
3. **Runtime metrics** — CPU and memory usage while running the same iperf3 test scenario.

## Current state of the three codebases (as of 2026-08-09)

This isn't a green-field comparison — two of the three apps already exist, at different levels of staleness relative to current KMP:

- **`iperf3KMP`** (this repo) — current, complete, feature set as of the History/CSV-export work just shipped: real iperf3 execution on all 3 targets, Room-backed result persistence, a History screen (expandable cards, delete, disabled mid-run), and CSV export/share. This is the **reference feature set** both native apps need to match before any comparison is valid.
- **`iperf3Android`** (private repo, collaborator access already granted) — a pure native Android app, reportedly **~6 weeks behind** current KMP. Described as "close" — likely missing at minimum the History screen and CSV export, possibly other recent work. Needs a real gap inventory, not an assumption, before estimating effort.
- **`iperf3iOS`** (private repo, collaborator access already granted) — a pure native iOS app, reportedly **months behind** current KMP. Judged not worth incrementally updating — **planned to be scrapped and rewritten from scratch** as a new, complete Swift port of the current KMP feature set.

## Phase A — bring both native apps to feature parity (prerequisite, not optional)

Comparing code size/complexity/runtime against a stale app would measure the wrong thing (an old feature set, not this one). Both native apps must reach the *same* feature set as current KMP before Phase B's measurements mean anything.

### A1. `iperf3Android` — incremental update

Approach: diff the existing app's feature set against current KMP, get a concrete gap list, then close it. Likely candidate gaps (to be confirmed, not assumed):
- History screen (list, expand/collapse detail cards, delete)
- Room-backed auto-save (vs. whatever save mechanism the native app currently has, if any)
- CSV export/share

Real gap analysis (reading the actual `iperf3Android` source, not guessing from staleness alone) is the first concrete task here.

### A2. `iperf3iOS` — full rewrite in Swift

Approach: new SwiftUI app, written to match current KMP's feature set from scratch, not an incremental patch of the existing stale app.

**Open question — not yet decided, deliberately left open for now:**

Does the Swift rewrite call the **real `libiperf` C library directly** (Swift/C interop via a bridging header, linking `third_party/iperf` + `native/iperf3_bridge.c` — the same platform-agnostic C core the KMP app already uses, just reached without Kotlin/Native cinterop), or does it **reimplement the iperf3 client protocol logic in Swift**?

This matters for what the comparison actually measures:
- Reusing the C core keeps all three apps driving the *same* real iperf3 engine — the comparison stays about UI/architecture/state-management code, not about three different implementations of the wire protocol.
- Reimplementing in Swift introduces a fourth independent implementation of iperf3's client logic as a variable, and is a substantially larger undertaking.

**Decide this before starting the iOS rewrite**, not during it — it changes the shape of the project (what needs to be built, what "pure Swift" is scoped to mean for this comparison).

## Phase B — comparison methodology

### B1. Code size

- **Tool**: `cloc` — handles per-language line/comment/blank breakdown consistently across Kotlin, Swift, and Java/Objective-C if any remains, which is more defensible for a cross-language comparison than a raw `wc -l` (different languages have different comment/blank conventions that `wc -l` doesn't distinguish).
- **Scope rules** (mirroring the rigor already used in the [Code Sharing Analysis](./Code%20Sharing%20Analysis.docx) work): exclude `third_party/`-style vendored/submodule code, generated code (Room schema JSON, KSP output, Xcode-generated files), and pure build/packaging configuration (`*.gradle.kts`, `.xcodeproj`/`.pbxproj`, `Info.plist`, Android XML resources). Include app/business logic and UI code only, split by layer where the layering is comparable (data/persistence, networking/native-bridge, UI, view-model/state).
- **Report**: total production LOC per app, plus a breakdown showing what fraction of each native app's code is logic that KMP unifies into a single shared implementation (this is the number that actually demonstrates KMP's code-sharing payoff in a 3-way comparison, not just the 2-platform-vs-1-shared framing the earlier analysis used).

### B2. Code complexity

- **Tool**: `lizard` — computes cyclomatic complexity (CCN), function length (NLOC), and parameter count uniformly across Kotlin, Swift, Java, Objective-C, and C in one pass. Using a single tool/algorithm across all three codebases is what makes the complexity numbers actually comparable to each other; per-language idiomatic tools (`detekt` for Kotlin, `SwiftLint`/`Periphery` for Swift) measure complexity differently and aren't apples-to-apples against each other even if each is more idiomatic for its own language.
- **Metrics to report per app**: average CCN per function, max CCN, % of functions over a fixed threshold (e.g. CCN > 10 — a common default "worth a second look" line), total NLOC.
- **Open question**: whether to *also* run per-language idiomatic tools as a supplementary/sanity-check layer, not the primary comparison number. Worth doing if time allows, not required for the headline comparison.

### B3. Runtime metrics (CPU, memory)

Per earlier discussion: **captured manually**, not via in-app instrumentation. You run the platform's real profiler (Android Studio Profiler / Xcode Instruments) against each app on real hardware and report the numbers back for analysis/write-up — this repo/session can't drive those profilers interactively.

Needed before this is actionable:
- **A standardized test scenario**: identical iperf3 parameters (host, port, direction, duration, parallel streams, skip) run against each of the 3 apps, so the workload itself isn't a variable.
- **Capture points**: idle baseline (before starting a test), during active test execution (peak + average), and immediately after completion (does memory/CPU return to baseline, or is there a leak/retention difference between implementations).
- **Metrics**: peak memory (MB), average memory during the active test, average/peak CPU%, and (if convenient) cold-start time and installed app size, since those piggyback naturally off the same profiling session.
- **A reporting template** (table or CSV) for you to fill in per app/run, so the numbers land in a consistent, analyzable shape rather than as free-form notes.
- **Known limitation to state plainly in any write-up**: Android and iOS runtime numbers come from genuinely different hardware (different device, different chipset) — this comparison can show relative overhead/behavior *within* a platform (KMP vs. native, same device) far more rigorously than it can claim an apples-to-apples Android-vs-iOS number. Say this explicitly rather than let a reader assume otherwise.

## Deliverables

- This plan document (living, gets updated as decisions above get made).
- A gap-analysis note for `iperf3Android` (Phase A1) before work starts there.
- An architecture note for the `iperf3iOS` rewrite (Phase A2) once the C-reuse-vs-reimplement question is resolved.
- A final comparison write-up once Phase B data exists — per this repo's existing convention (see `AGENTS.md`/`CLAUDE.md` "Output format"), a report meant to be *read* goes out as a self-contained HTML artifact with real measured numbers, not markdown notes and not estimates.

## Open questions (tracked here, not resolved yet)

- [ ] **iOS rewrite**: reuse the real C `libiperf`/`iperf3_bridge.c` core via Swift/C interop, or reimplement the protocol logic in Swift? (See Phase A2.)
- [ ] **Sequencing**: which starts first — inventorying the real gap in `iperf3Android` against current KMP, scaffolding the `iperf3iOS` Swift rewrite, or something else? Not yet decided.
- [ ] **Complexity tooling depth**: `lizard` alone, or also per-language idiomatic tools as a supplementary check?
- [ ] **Runtime test protocol specifics**: exact iperf3 parameters and device list to standardize on haven't been chosen yet.
