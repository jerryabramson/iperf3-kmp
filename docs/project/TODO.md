# TODO

Planning/tracking doc for open work on `iperf3KMP`. Case-study write-ups live in the dedicated `*_CASE_STUDY.md` / `AI_ASSISTED_DEVELOPMENT_LIMITS.md` docs — this file just tracks what's still open.

## Verification

- [x] Visually confirm the Android font-alignment fix (`mesloFontFamily.kt`, `PRINTF_FORMATTING_CASE_STUDY.md` §13) on an actual Android emulator/device. **Confirmed 2026-07-20** — user verified columns render aligned on Android, iOS, and desktop.
- [x] ~~If misalignment persists after the font fix: check `letterSpacing = 0.2.sp`...~~ Moot — alignment confirmed correct with no further change needed.

## Research: AI comparison experiments (see `AI_ASSISTED_DEVELOPMENT_LIMITS.md` §2–§6)

- [ ] **Experiment 1 — Claude cold-start on the printf task.** Check out the pre-printf baseline (`b894e92`, per `PRINTF_FORMATTING_CASE_STUDY.md` §2) in a fresh session/worktree with no visibility into this repo's later history, and give Claude the same original requirement the local model (`qwen/qwen3.6-35b-a3b`) got. Measure: does it avoid the `NSString.stringWithFormat` vararg bug (§5–§6)? Real `/cost` for the run? Must be a genuinely fresh session — continuing from a session that already knows the fix invalidates the comparison.
- [ ] **Experiment 2 — Full Android→KMP port race.** Give the *complete* original native Android app to a local model and to Claude, independently, and have each port it to full KMP (Android + iOS + Desktop) from scratch. Bigger, more realistic companion data point to `LMSTUDIO_MCP_CASE_STUDY.md` §11's local-vs-cloud comparison.

## Research: experience level and AI-assisted development effectiveness

**Context.** The developer directing this project has 40+ years of industry software experience, most of it building and operating extremely large, complex enterprise systems in mission-critical environments — banking, insurance, NASA, healthcare. That background is directly relevant to the project's own thesis (`AI_ASSISTED_DEVELOPMENT_LIMITS.md`): the category-3 verification gap (§3 there — correctness that only a human looking at a running system can confirm) is closed by a *human*, and how effectively it gets closed plausibly depends heavily on that human's own accumulated failure-pattern experience, not just on which AI model is doing the work.

- [ ] Reflect on / write up how 40+ years of mission-critical enterprise experience shapes effective, skeptical use of AI coding assistants — specifically, knowing *what* to independently verify and *when* a plausible-looking, compiling, test-passing AI claim still warrants suspicion — versus a hypothetical fresh CS graduate given the same AI tooling but none of that background. Concrete examples already in this repo's own history to draw on: catching the printf case study's original mischaracterized failure mode from a screenshot (`PRINTF_FORMATTING_CASE_STUDY.md` §5), insisting on real execution over compilation as the bar for "done" (§8), and flagging the Android-only misalignment bug in the first place (§13) rather than accepting "looks fine on the platforms I checked."
- [ ] If pursued as an actual comparison rather than a reflection: design a way to test it directly — e.g., observe a junior/fresh-graduate developer working through a comparable task (a seeded vararg/FFI-style bug, or a platform-specific rendering bug) with the same AI tools, and compare what they do and don't catch, and why.

## Open work

- [ ] **Automated Room round-trip test.** Persistence is manually verified end-to-end on all three platforms, but no regression test guards it yet (insert → read back by guid → assert field equality; `ROOMDBPLAN.md`'s testing section has the minimum bar).

- [ ] **Native (non-Cygwin) `iperf3.exe` for Windows ARM64.** Today's `.msi` bundles an x64 Cygwin-built `iperf3.exe`, confirmed working under Prism emulation on a real ARM64 machine — but there's no released ARM64 Cygwin, so a genuinely native build is a separate effort. Plan, options (community MSVC port vs. MSYS2 CLANGARM64), and a measure-first phase-0 gate: `WINDOWS_ARM64_IPERF3_PLAN.md`.

- **Save/History doesn't work on Windows ARM64 (accepted gap, documented 2026-10-09; not actionable right now).** Neither `androidx.sqlite:sqlite-bundled` nor `org.xerial:sqlite-jdbc` ships a native SQLite binary for Windows ARM64. Everything else on this target works, including real network tests. Detail and options considered: `platform-builds/windows-arm64.md`. Re-check when either library adds support.

## Release gate: 1.0.0 from `master`

The release criteria live in one place: [`../platform-builds/TEST_MATRIX.md`](../platform-builds/TEST_MATRIX.md), section F. Do not track them here.

Open items still needed for the gate (as of 2026-10-04):
- Windows x64 and ARM64 rebuilds from a clean snapshot.
- Podman run of the `.rpm` container script (Rocky 9 base).
- Debian or Ubuntu x86_64 machine, for the x86_64 `.deb` container cell.
- Install tests on the baseline distros (Ubuntu 22.04, Debian 12, Rocky/Alma 9; arm64 and x86_64).

## Done

- [x] **iperf3 native integration** — real iperf3 3.21 in-process on all three targets (JNI on Android/desktop, cinterop on iOS), manually verified end-to-end including iOS device + simulator (iOS confirmed 2026-08-27). Design rationale: `IPERF3_NATIVE_INTEGRATION.md` §8–9.
- [x] **Windows desktop support**, reversing the earlier "not supported" decision (`WINDOWS_DESKTOP_APP.md`) — a `ProcessBuilder`-driven subprocess spawns a real, Gradle-built-from-source `iperf3.exe` (Cygwin toolchain), bundled into a working `.msi` via `desktopApp`'s `stageWindowsIperf3` task. Manually verified end-to-end: real network tests, repeated start/stop cycles, and a clean-machine `.msi` install with no separately-installed `iperf3.exe`. Full story: `WINDOWS_DECISION_REVERSAL_CASE_STUDY.md`.
