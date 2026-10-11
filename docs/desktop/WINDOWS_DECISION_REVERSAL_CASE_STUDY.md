# From "Not Supported" to Shipped: Reversing the Windows Decision, and What It Actually Cost

## 1. Overview

`docs/desktop/WINDOWS_DESKTOP_APP.md` once opened with the line "Windows is not a
supported target for `:desktopApp`." As of this writing, Windows desktop
works: real network tests run through a real, Gradle-built `iperf3.exe`,
manually verified end-to-end (start/stop cycles the developer called
"pretty bulletproof") on an actual Windows machine.

This is not a story about the original decision being wrong. Re-reading
`WINDOWS_DESKTOP_APP.md`'s own "Why" section is the interesting part: it
had already identified the exact architecture that eventually shipped, and
talked itself out of it — not because it wouldn't work, but because its
*cost* was unknown and assumed to be high. This document is about what
changed: not the facts on the ground, but the decision to actually
*measure* the thing the original document could only guess at, before
writing a line of code. The measurement was made explicit as a prediction
first, then checked against reality once the code existed. Both numbers
are in this document, from real `wc -l`/`git diff --stat` output, not
recollection.

## 2. The Original Decision, and Why It Was Reasonable

`WINDOWS_DESKTOP_APP.md`'s stated reasoning, in brief: `third_party/iperf`'s
POSIX-heavy C sources need Cygwin's compatibility layer to build on
Windows at all (no viable POSIX-compatible toolchain exists otherwise —
MinGW/MSVC don't provide enough header compatibility). But Cygwin-built
DLLs dynamically link against Cygwin's own runtime (`cygwin1.dll`), and
loading that runtime *in-process* into `java.exe` — required by this
project's JNI design, the same one Android/macOS/Linux desktop all
share — crashes reliably. The JVM's memory layout collides with Cygwin's
own fixed-address shared-memory initialization
(`EXCEPTION_ACCESS_VIOLATION`, `MapViewOfFileEx` base-address collisions).
This reproduced consistently even after `rebaseall`, Cygwin's own standard
remedy for exactly this class of problem.

The document then states, verbatim: *"The only known way around it is to
not load `cygwin1.dll` into `java.exe` at all — run the Cygwin-compiled
iperf3 code as a genuinely separate child process instead (`ProcessBuilder`,
communicating over stdin/stdout), the same way the officially distributed
Windows `iperf3.exe` actually works."* That is exactly the architecture
that shipped. The original analysis was not incomplete — it named the
right answer.

What it rejected the answer on was this: *"This project already built and
rejected exactly that architecture once before, for Android (a
`ProcessBuilder` subprocess against a real `iperf3` binary on a rooted
phone) — it was replaced by the current in-process JNI design specifically
to get away from subprocess management. Reintroducing it as a
Windows-only special case would mean maintaining two fundamentally
different execution architectures for the same feature, one of them being
the exact pattern already judged not worth keeping."*

That's a real concern, and a reasonable one to weigh — but notice what it
is not: it is not a measurement. It's a judgment call made on the strength
of "this sounds architecturally expensive," carried over from a *different*
prior decision (Android's own JNI-over-ProcessBuilder migration) whose
actual cost/benefit was never quantified for *this* case either. The
document doesn't say how much code a Windows-only ProcessBuilder path
would actually require, or what fraction of the app it would touch. It
couldn't have — nobody had built it yet.

## 3. Reopening the Question: Measure First, Then Decide

This project already has an established discipline for exactly this kind
of question: a code-sharing metric (commonMain + the single-sourced
`native/` C bridge, divided by the whole app), used elsewhere in this
project's own research documentation to characterize what KMP's
"write once" promise actually delivers here (~88% of custom code
single-sourced, per `INDEX.md`'s summary of the project's Word-document
capabilities writeup). That metric existed before any Windows work
started. Reusing it to evaluate the Windows question specifically — rather
than re-litigating "is ProcessBuilder architecturally acceptable" in the
abstract — turns a values judgment into an arithmetic one.

The prediction, made explicitly before any Windows-specific code was
written: `IperfRunner`'s existing `expect fun executeNativeIperf3Client(
arguments: Array<String>, ...)` seam already passes a ready-built `argv`
array (constructed once, in `commonMain`'s `Iperf3Executor.kt`, in the
exact CLI shape a real `iperf3.exe -c host -p port ...` invocation wants).
A Windows-only actual implementation would only need to replace *how* the
test executes (spawn+pipe vs. a JNI call into C threads), not *what* gets
asked of iperf3 — meaning zero changes to `commonMain`'s argument
construction, zero changes to `Iperf3OutputMonitor.kt`'s text-output
parsing (real `iperf3.exe`'s stdout is the same textual format that
already parses correctly), zero changes to `androidMain` or `iosMain` at
all. The estimate at the time: roughly 200–350 new lines, confined
entirely to one new file in `jvmMain`, dropping the project's overall
code-sharing figure from an estimated ~89% to ~86%.

That was a bet. Section 5 checks it against what was actually built.

## 4. What Actually Got Built (Brief — See `docs/desktop/WINDOWS_DESKTOP_APP.md`'s History and Project Memory for the Full Bug List)

Two things had to work, and they turned out to be almost entirely
independent problems:

**The runtime execution path** (branch `windows_exe`): `IperfRunner`
(`jvmMain`) branches at runtime — `jvm()` has no OS-specific child source
sets the way `iosArm64`/`iosSimulatorArm64` do, so telling Windows apart
from macOS/Linux is a `System.getProperty("os.name")` check, not a
compile-time split — routing to a new `WindowsIperf3ProcessRunner` instead
of the existing JNI bridge. It spawns the real `iperf3.exe` via
`ProcessBuilder`, reads its stdout/stderr on background coroutines, and
forwards lines into the exact same `IperfCallback` interface the JNI path
already targets. Real bugs surfaced and fixed here: a stray duplicated
`argv[0]`; a classic Java `Process` pipe-drain deadlock (an uncaught
exception in the output callback silently killed the reader coroutine,
which stopped draining `iperf3.exe`'s stdout pipe, which eventually made
the *child process itself* block on `write()`, which meant
`Process.waitFor()` never returned — a real hang, not a crash, diagnosed
by reasoning through the actual mechanism rather than guessing); and a
`@Volatile`/race-condition gap in the stop logic, found by code review and
confirmed fixed by the developer's own repeated manual start/stop testing
("pretty bulletproof").

**Building `iperf3.exe` itself from source** (branch
`windows_iperf3_build`): this is where nearly all of the actual difficulty
lived, and none of it was in application code. Getting Cygwin's real
toolchain to compile `third_party/iperf` correctly took seven distinct,
separately-diagnosed root causes — bare `bash` on `PATH` silently
resolving to WSL's Linux environment instead of Cygwin's (twice, once for
`nativeformat.dll`'s build and once for `iperf3.exe`'s); a non-interactive
`bash -c` invocation not inheriting Cygwin's own `PATH` setup; a
Windows-environment-variable-casing bug that made the first fix for that
silently not take effect; Cygwin's bundled `clang-5.0.1` hard-erroring on
`-fPIC` (meaningless for Windows targets, but only a warning on a newer
LLVM clang); the same old clang failing to correctly interoperate with
Cygwin's own (newer) system headers, fixed by switching to Cygwin's own
`gcc` instead; and a missing `-I` flag for a sibling header that had been
silently relying on a compiler search-path fallback that happened to work
on macOS's clang but not Cygwin's gcc. Every one of these was found by
running the real build on the real machine (via direct SSH access this
session had to the actual Windows test box) and reading the actual error,
not by inference alone.

## 5. Checking the Bet: Predicted vs. Actual

The prediction from Section 3: dropping from ~89% to ~86% app-wide,
confined entirely to `jvmMain`. Measured directly, from the current
codebase (`wc -l` across `commonMain`, `native/`, and each platform
source set):

| | LOC |
|---|---|
| `commonMain` | 5,953 |
| `native/` (single-sourced C, JNI+cinterop) | 1,442 |
| **shared** | **7,395** |
| `androidMain` | 232 |
| `iosMain` | 498 |
| `jvmMain` | 501 |
| **platform-specific** | **1,231** |
| **Total / shared %** | **8,626 / 85.73%** |

`git diff --stat` against the pre-Windows baseline isolates exactly how
much of that `jvmMain` growth is Windows-specific:
`WindowsIperf3ProcessRunner.kt` (212 new lines) plus the routing changes
to `IperfRunner.kt` (+40/-5) — **247 net lines**, in exactly two files,
both in `jvmMain`. Zero lines in `commonMain`, `androidMain`, or `iosMain`.
Subtracting that delta to reconstruct what the ratio would be without it:
**88.26%**. The actual drop the Windows work caused, isolated from
everything else that changed in this codebase around the same time
(the developer-logging feature, native bridge refinements, etc.), is
**88.26% → 85.73% — 2.53 percentage points**, against a prediction of
roughly 3 points (~89% → ~86%). Close enough to call the bet won: the
"two execution architectures" cost `WINDOWS_DESKTOP_APP.md` was worried
about is real, measurable, and small.

Scoped differently — not "how much did this cost the whole app" but "how
shared is the code that actually runs when the app runs on Windows" — the
number is more striking: of the 247 new lines, *all* of them are the
Windows-specific execution mechanism. The `commonMain` code that decides
what iperf3 should do (host, port, duration, parallel streams — all of
`Iperf3Executor.kt`'s argument construction) and the code that makes sense
of what it reports back (`Iperf3OutputMonitor.kt`'s text parsing) needed
zero Windows-specific changes to work correctly the first time a real
`iperf3.exe` produced real output. That's not a coincidence; it's the
seam (`IperfRunner`'s `expect`/`actual` boundary) doing exactly the job an
`expect`/`actual` boundary is supposed to do.

## 6. The Finding Nobody Predicted: Build-Time Complexity, Not Runtime Complexity

The code-sharing metric — and the prediction built on it — only measures
*shipped application code*. It has nothing to say about what it costs to
actually produce a working binary for a new target, and that cost turned
out to be the real story here.

`git diff --stat` for the whole Windows effort so far, split by kind:

| | Insertions | Deletions |
|---|---|---|
| Runtime app code (`IperfRunner.kt`, `WindowsIperf3ProcessRunner.kt`) | 247 | 5 |
| Build orchestration (`shared/build.gradle.kts`, `gradle.properties`) | 343 | 34 |

The build script changed by more than the application code did — and
that comparison actually *understates* the gap, since a meaningful
fraction of the runtime-code insertions are Kotlin doc comments explaining
*why*, matching this project's existing documentation density, not new
logic. A cleaner proxy than raw line counts: the number of genuinely
distinct root causes found and fixed. Three, in the runtime path (the
`argv[0]` duplicate, the pipe-drain deadlock, the stop-logic race). Seven,
in the build toolchain (enumerated in Section 4) — more than double, and
every one of them was a toolchain-resolution problem (which shell, which
compiler, which environment variable, which header) that had nothing to
do with this project's own application logic at all.

This is worth stating plainly, because it cuts against the usual framing
of cross-platform code-sharing work: KMP's `expect`/`actual` seam did
exactly what it promised at the application layer — the thing this
project already had a metric for. But standing up a genuinely new
*target* (not just a new source set, but a new toolchain: Cygwin, its own
`bash`, its own `gcc`, its own environment quirks, all invoked from a
build script that has to correctly tell Cygwin's `bash` apart from WSL's
on a machine that has both) is a cost the code-sharing metric is
structurally blind to, because Gradle build scripts aren't part of the
shipped app. A KMP project's "percentage of code shared" can stay high
and stable while the *engineering effort* to support one more platform is
dominated entirely by build-time toolchain archaeology. Anyone using this
project's own code-sharing metric as a proxy for "how hard was it to add
this platform" would have badly underestimated the actual work, precisely
because the metric was designed to answer a different, narrower, and
genuinely well-answered question.

## 7. What This Does and Doesn't Vindicate

This is not a claim that `WINDOWS_DESKTOP_APP.md`'s original authors made
a mistake. Given what was known at the time — no measurement, only the
memory of a prior architecture reversal on a different platform — treating
"two execution architectures" as a real cost worth avoiding was a
defensible, conservative call. What changed was not the facts about
Cygwin, `java.exe`, or `ProcessBuilder` — every one of those was already
correctly understood in the original document. What changed was replacing
an unquantified worry with a number, using a measurement tool this project
already trusted for exactly this kind of question, and then building the
thing to check the number against reality.

The result holds up a specific claim precisely, not generally: *for this
feature, in this codebase, with this particular `expect`/`actual` seam
already in place*, maintaining a second execution architecture for one
platform cost about 2.5 points of code-sharing and 247 lines in two files.
It does not claim that adding platform-specific execution paths is always
cheap, or that architectural consistency concerns are never worth
weighing on their own terms independent of a line-count metric. It claims
only that this specific concern, raised in good faith with the right
instincts, was answerable — and once answered, was small enough not to be
the blocker it looked like from the outside.

The `.msi` packaging work (bundling the Gradle-built `iperf3.exe` and its
`cygwin1.dll` dependency into a real Windows installer) is still ahead,
on its own branch. Whatever that costs, it will also be measured before
it's asserted.
