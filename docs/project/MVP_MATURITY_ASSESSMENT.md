# MVP Maturity Assessment

**Status as of 2026-08-27.** This document justifies calling `iperf3KMP` a
**mature MVP** — a minimum viable product in the sense that every core
feature is real (not mocked/simulated) and works end-to-end, and "mature" in
the sense that the rough edges an MVP normally ships with (fake data,
one-platform-only, undocumented failure modes, no persistence, no packaging
story) have each been individually closed and evidenced, not just claimed.
It is not a claim that the project is *finished* — see "What's deliberately
still open" below for the honest remainder.

## What "mature MVP" means here, concretely

A newly-scaffolded KMP app can plausibly claim "MVP" the day it compiles on
three platforms and shows a static screen. That bar is intentionally not
what's being claimed. The bar used here, consistent with this project's own
research thesis (`AI_ASSISTED_DEVELOPMENT_LIMITS.md`) that some correctness
is only verifiable by a human looking at a real running system, is:

1. The core feature (running a real iperf3 network test) is **real
   libiperf**, not a simulated/delayed placeholder, on **all three**
   targets.
2. Each platform has been **manually run and observed**, not just
   compiled.
3. Results **persist** and are **retrievable/exportable**, not just
   displayed and discarded.
4. The app can be **packaged and installed** the way an end user would
   actually receive it, not just run from an IDE.
5. Every hard problem hit along the way has a **root-caused, evidenced**
   writeup — not "it works now" with no record of what broke or why.

## Evidence, by criterion

### 1. Real iperf3 client on all three targets

`sampleOutputData.kt`'s delay-loop simulation is no longer wired into any
real run path (`shared/src/commonMain/.../runner/sampleOutputData.kt` — kept
only for Compose previews). All three `IperfRunner` `actual`s
(`androidMain`, `iosMain`, `jvmMain`) call a real, in-process `libiperf 3.21`
build:

- Android/desktop share one JNI adapter (`native/iperf3_jni.c`) over a
  platform-agnostic core (`native/iperf3_bridge.h`/`.c`).
- iOS reaches that same core directly via cinterop against a real compiled
  static library (`libiperf3bridge.a`, cross-compiled per-target since
  device and simulator arm64 aren't link-compatible despite sharing an
  instruction set).
- Both paths share the exact same threading/stall-detection/`warning:`-
  filter logic — a deliberate JNI/core split (`IPERF3_NATIVE_INTEGRATION.md`
  §8.4/§9) specifically so a bug found and fixed once (§8.3's Skia-stderr
  false-failure bug) can't silently drift out of sync between platforms the
  way it would have with two independent implementations.

This is the second time this project has proven the "one C source, reached
by JNI + cinterop from three targets" pattern (`native_format.h` for
printf-style formatting was the first, `NATIVE_FORMATTING_JUSTIFICATION.md`)
— a proven, repeatable architecture, not a one-off.

### 2. Manually run and observed, per platform

- **Android**: real-device testing surfaced and fixed a genuine bug
  invisible in any simulator/emulator or compile-only check — Skia GL
  shader-setup debug spam being misforwarded through the stderr-capture path
  as a false test failure (`IPERF3_NATIVE_INTEGRATION.md` §8.3). Confirmed
  fixed, documented with the actual failure screenshot
  (`docs/research/evidence/iperf3_2ndrun_failure.png`).
- **Desktop (macOS, Linux)**: `:shared:jvmTest` runs the real libiperf JNI
  path (not a mock) as part of the standard test suite —
  `IperfRunnerJvmTest.kt`. Linux specifically required its own real fix
  (missing `(void **)` cast on `AttachCurrentThread`, only a hard error
  under Linux's default clang, a warning on Apple's — see CLAUDE.md's
  "Native iperf3 client" section), found by an actual clean-machine build,
  not inferred.
- **iOS**: build **and** link are verified through Gradle — not just
  `compileKotlinIosArm64` (which only proves Kotlin type-checks against the
  cinterop klib) but `linkDebugFrameworkIosArm64`/`IosSimulatorArm64` (which
  proves the real static library's symbols actually resolve) and
  `iosSimulatorArm64Test` (which links and runs a real test binary on a
  Simulator process). Beyond that, the packaged app has been run end-to-end
  against real remote iperf3 servers — confirmed 2026-08-27 across several
  iOS Simulators plus real hardware, an iPhone 17 Pro and an iPad M4,
  installed via the user's Apple Developer Program device-install
  entitlement (`IPERF3_NATIVE_INTEGRATION.md` §9). iOS clears the same
  manual-verification bar as Android and desktop.

### 3. Real persistence, retrieval, and export

Room + KMP SQLite persistence (`ROOMDBPLAN.md`) is implemented and, per
CLAUDE.md, **manually confirmed to actually persist data** on all three
platforms — not just "compiles," a real Save-button-click followed by
opening the actual on-disk `.db` file per platform. Built on top of that:

- A History screen (`view/HistoryScreen.kt`) reads saved runs back out of
  Room.
- CSV export (`utils/CsvExport.kt`, with Android's `ShareCsv.android.kt`
  going through a real `FileProvider`) turns persisted results into a
  portable artifact a user can actually take out of the app.
- The schema has iterated 13 times since Phase 1 (`shared/schemas/.../
  ResultDatabase/6.json` through `18.json`, each committed) — the kind of
  churn that's normal for a schema under real, continued use, not a
  write-once-and-forget prototype table.

### 4. Real packaging story

Desktop packaging goes through the actual `jpackage` pipeline the app would
ship through, not a dev-only run task: `.dmg` (macOS) and `.deb` (Linux) are
both built, installed, and run from a clean checkout, with real bugs found
and fixed along the way (missing `-fPIC`, wrong icon format per OS,
`AttachCurrentThread` cast — `docs/research/COSTS.md`/CLAUDE.md's desktop packaging
section). `.msi` (Windows) is implemented but not yet verified on a real
Windows host — tracked, not hidden. Windows as a **desktop-app target**
(not packaging format) is a deliberate, documented non-goal
(`WINDOWS_DESKTOP_APP.md`) — Cygwin-built native DLLs loaded into `java.exe`
proved unreliable, a real technical finding, not a shortcut.

Application icons are wired through both mechanisms every desktop app
needs — the in-window icon (`Window(icon = ...)`) and the packaged
installer/Dock/taskbar icon (`jpackage`'s per-OS `iconFile`) — after an
initial bug where only the former worked.

### 5. Every hard problem has a root-caused writeup

This is the project's most distinctive maturity signal relative to a
typical student MVP: nothing in `docs/` says "fixed a bug" without also
saying *why* it happened and *how* it was confirmed fixed.
`PRINTF_FORMATTING_CASE_STUDY.md`, `NATIVE_FORMATTING_JUSTIFICATION.md`,
`IPERF3_NATIVE_INTEGRATION.md`, and the newest addition,
`NATIVE_METHOD_RENAME_COMPLEXITY.md` (a same-day incident writeup, not
retrospective), all follow this pattern. `docs/research/evidence/` holds the actual
screenshots (`ios-printf-gibberish-output.png`,
`iperf3_2ndrun_failure.png`, `long_startup_delay.png`,
`iperf3_desktop_linux.png`) behind several of these — claims are backed by
artifacts, not just prose.

## What's deliberately still open

An honest MVP assessment names its own gaps. None of these block calling
the project a mature MVP — each is a scoped, tracked next step, not an
unknown unknown:

- **Windows `.msi` packaging is unverified** end-to-end (needs an actual
  Windows host) — the build path exists and is documented, just not yet
  confirmed to produce a working installer.
- **No automated Room round-trip test.** Persistence is manually verified
  on all three platforms, but there's no regression test guarding it —
  a future schema or mapper change could silently break saving without a
  test failing to catch it.
- **Text-output parsing, not `--json-stream`.** A deliberate, documented
  scope decision (`IPERF3_NATIVE_INTEGRATION.md` §3.2), not an oversight —
  investigated and found more complex than the current approach for the
  value delivered.
- **Automated test coverage is modest** relative to the manual-verification
  work — `commonTest`/`androidHostTest`/`jvmTest`/`iosTest` together hold a
  handful of test files, not a large suite. Consistent with, and named
  explicitly as a research point in, `AI_ASSISTED_DEVELOPMENT_LIMITS.md`:
  this project's verification strategy leans on real human-observed
  execution for the class of bugs (visual, platform-specific,
  hardware-triggered) that automated tests structurally can't catch, not on
  test-count as the maturity signal.

## Bottom line

Every load-bearing claim in "mature MVP" — real native execution (not
simulation), real persistence (not a stub), real packaging (not just
`:run`), and a track record of real problems being root-caused instead of
patched over — has direct evidence in this repository, most of it its own
committed doc or artifact, and all three platforms, including iOS, are now
held to the same manually-verified-end-to-end bar. That combination — real
functionality on real hardware across all three targets, plus an honest,
evidenced account of exactly where the remaining edges are (above) — is
what "mature" adds to "MVP" here.
