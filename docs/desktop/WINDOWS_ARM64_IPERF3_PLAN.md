# Native iperf3 on Windows ARM64 — Planning Document

**Status:** planning / not started · **Written:** 2026-09-21 · **Branch context:** `windows_msi_bundling`
**Audience:** this project's contributors, and anyone else who needs `iperf3.exe` running natively on Windows on Arm. The
project-specific parts are isolated in §7 so the rest can be read (and reused) on its own.

Claims in this document are tagged **[verified]** (observed first-hand in this project), **[sourced]** (from a linked
external source, checked 2026-09-21, not independently reproduced), or **[unverified]** (background knowledge or an
expectation that still needs confirming). Please keep that convention when editing.

---

## 1. Summary

- Windows on Arm (WoA) can already run this project's app: the x64 MSI installs and runs real tests under Prism
  emulation **[verified]** (2026-09-21, user-confirmed).
- What is *not* native is `iperf3.exe`: it is built with x86_64 Cygwin, and **there is no released Cygwin for ARM64**
  **[sourced]** (see §3.2). So even a fully native ARM64 build of the app would still spawn an emulated `iperf3.exe`.
- Upstream ESnet does not support iperf3 on Windows at all **[sourced]**, so there is no official native build to adopt.
  Two realistic routes exist: an existing community MSVC port that already lists ARM64 builds, or a build via MSYS2's
  CLANGARM64 toolchain. Neither has been evaluated by us yet.
- Recommended first step is cheap and decisive: **measure what emulation actually costs** (§6, Phase 0) before investing
  in a port. If emulated throughput matches native on the links people really test, this whole effort is optional.

## 2. Why this matters

- WoA hardware is growing, and the user expects more of it soon, including systems with NVIDIA RTX GPUs **[unverified —
  product plans not checked here]**. A network *measurement* tool is a poor fit for an emulation layer if the emulation
  adds overhead or timing noise to the thing being measured.
- Others building WoA tooling will hit the same gap: iperf3 is a de facto standard, and "run it on the new Arm laptop"
  is an obvious first request. A shared, well-documented path is more valuable than N private ports.
- For this project specifically: the app's JVM, Compose UI, and `nativeformat.dll` can all be native ARM64 today
  **[verified]** — `iperf3.exe` is the one emulated component.

## 3. Landscape (as of 2026-09-21)

### 3.1 Upstream iperf3 on Windows

- ESnet's iperf3 targets POSIX systems; it makes no native Windows API calls, and Windows use relies on Cygwin as an
  emulation layer **[sourced]** ([iperf3 on Windows discussion](https://github.com/esnet/iperf/issues/421),
  [ESnet repo](https://github.com/esnet/iperf)).
- Microsoft has publicly cautioned against using iperf3 for Windows network measurements, recommending its own tools
  instead **[sourced]**
  ([Three Reasons Why You Should Not Use iPerf3 on Windows](https://techcommunity.microsoft.com/blog/networkingblog/three-reasons-why-you-should-not-use-iperf3-on-windows/4117876)).
  Worth reading before making accuracy claims about any Windows iperf3 build, native or not.

### 3.2 Cygwin on ARM64

- Package-level porting has not started because the `aarch64-pc-cygwin` GNU toolchain and runtime are not available
  upstream; active work exists, with a tracking issue for a working `bash.exe` on that target showing progress as of
  September 2026 **[sourced]**
  ([tracking issue](https://github.com/dennisameling/gnu-toolchain-windows-arm64/issues/2),
  [Linaro WoA notes](https://linaro.atlassian.net/wiki/spaces/WOAR/pages/29484384264/Cygwin+Runtime),
  [mailing-list thread](https://sourceware.org/pipermail/cygwin/2025-July/258543.html)).
- Consequence: the current build strategy (Cygwin gcc → `iperf3.exe` + `cygwin1.dll`) cannot produce an ARM64 binary
  today. Revisit if the port lands.

### 3.3 MSYS2 CLANGARM64

- MSYS2 has preliminary ARM64 support via the CLANGARM64 environment: many packages have native ARM64 builds, but
  the Unix-y tooling (bash, pacman, …) itself runs under x64 emulation; Windows 11 ARM64 required; ARM64EC unsupported
  **[sourced]** ([MSYS2 ARM64 docs](https://www.msys2.org/docs/arm64/)).
- This gives a native clang/MinGW-style toolchain that emits plain Win32 ARM64 binaries with **no `cygwin1.dll`**. Whether
  iperf3's POSIX-isms compile and behave correctly there is **[unverified]** and is the main risk of this route.

### 3.4 Community native MSVC port

- A community project, [Active-Software/iperf3-windows-msvc](https://github.com/Active-Software/iperf3-windows-msvc),
  describes a native Visual Studio 2022 / MSVC port of `iperf3.exe` for Windows 11+ with **x64 and ARM64** Release
  builds, BSD-licensed, with a `windows/compat` shim directory **[sourced]**.
- Caveats found on a quick look — all need real vetting: only a handful of commits and no visible community
  ([sourced]); the iperf3 version it is based on, and whether it is a patch-set or a fork, were not stated in what we
  could read **[unverified]**; scope appears to include server as well as client.
- Treat as a **candidate to evaluate, not a dependency to adopt**: we would ship the binary inside a commercial app
  (see project goals), so provenance, reproducibility, and a way to rebuild from source matter more than convenience.

## 4. Options

| # | Option | Native ARM64 iperf3 | Effort | Main risk |
|---|--------|:---:|:---:|-----------|
| A | Status quo: x64 Cygwin `iperf3.exe` under Prism | No (emulated) | None | Emulation overhead / timing noise on fast links |
| B | Wait for / adopt an ARM64 Cygwin | Yes | Low once it exists | Not available; timeline unknown |
| C | Build our pinned `third_party/iperf` 3.21 with MSYS2 CLANGARM64 | Yes | Medium | POSIX gaps under MinGW (threads, sockets, `select`, signals) — unknown until tried |
| D | Vet and adopt (or rebuild from source) the community MSVC port | Yes | Low–Medium | Maintenance, version drift from our pinned 3.21, trust/provenance |
| E | Write our own Win32 compat layer / port | Yes | High | Ongoing burden; duplicates D |
| F | llvm-mingw cross-toolchain (aarch64-w64-mingw32) | Yes | Medium | Same POSIX-gap risk as C **[unverified — not researched]** |

**Recommendation:** A now; run Phase 0 to decide whether a native build is worth doing; if yes, time-box spikes on **D**
(cheapest if it holds up) and **C** (closest to our existing "build from the pinned submodule" model), in that order,
and pick on the criteria in §5. B is a "watch" item.

## 5. Evaluation criteria

1. **Builds from a known upstream version** — ideally our pinned `third_party/iperf` (3.21), unmodified or with a small
   documented patch set; otherwise we must own a fork.
2. **Client mode correct on Windows ARM64** — our app only ever runs `--client` (server code is linked only because
   `main.c` references it; see `compileIperf3ExeWindows` notes in `shared/build.gradle.kts`).
3. **Measurement fidelity** — throughput and interval timing indistinguishable from (or better than) option A on the
   same hardware; no new artifacts in UDP loss/jitter reporting.
4. **Reproducible build in Gradle** on a WoA host, failing loudly with a clear message when a tool is missing (project
   convention: no silent fallbacks).
5. **Licensing / provenance** — BSD-3 compatible, source available, binary rebuildable by us.
6. **Small maintenance surface** — how many patches must survive each upstream iperf3 bump.

## 6. Plan

### Phase 0 — Measure before building (gate)

Goal: find out whether emulation overhead is real for the workloads that matter.

- Hardware: a WoA machine with the fastest NIC available (ideally ≥ 2.5 GbE; 10 GbE if possible), plus a known-good
  server on the same LAN (Linux, native iperf3).
- Compare on the *same* machine and link: x64 Cygwin `iperf3.exe` under Prism (option A) vs. a native ARM64 `iperf3.exe`
  from whichever candidate builds first (even an unvetted community binary is fine **for measurement only**).
- Metrics: single-stream and `-P 4` TCP throughput, UDP at fixed `-b` rates with loss/jitter, CPU utilisation of the
  iperf3 process, run-to-run variance (≥ 10 runs each).
- **Decision gate:** if A is within measurement noise of native on the links users realistically test, stop here, document
  the result, and mark native ARM64 as optional. If not, continue.

### Phase 1 — Time-boxed feasibility spikes

Each spike ends with a written result appended to this document (worked / didn't / what patches were needed).

- **Spike D:** build the community MSVC port from source on a WoA host; identify its upstream base version; run the
  Phase-0 measurements; assess how it would track our pinned 3.21.
- **Spike C:** in MSYS2 CLANGARM64, run our pinned `third_party/iperf` through `./configure` + `make` client-only
  (`--without-sctp --with-openssl=no`, as in the existing Cygwin tasks). Record every compile/link error and every
  runtime misbehaviour; classify as "needs a patch" vs. "fundamental".
- Common checks for both: `--version`, a 10 s TCP test, `-R` (reverse), `-u`, `-6`, `-P 4`, stop-mid-run (our
  `forceStop()` is a hard `Process.destroy()`), and output format parity with what `Iperf3OutputMonitor.kt` parses.

### Phase 2 — Integration (only after a spike passes)

See §7 for the concrete change list in this repo.

### Phase 3 — Verification matrix

x64 Windows (native x64 host), WoA (native ARM64 exe), WoA running the x64 MSI under Prism, clean machine with no
developer tools installed (as done for the x64 MSI on 2026-09-21), plus the manual start/stop soak used for the
x64 exe ("pretty bulletproof" bar).

### Phase 4 — Share upstream-facing results

Publish measurements and any patches. If a small, clean patch set makes upstream iperf3 build natively on Windows
ARM64, it is worth offering to ESnet even though Windows is unsupported there — and to the community MSVC port's
maintainers if we build on theirs.

## 7. Integration points in this repo (project-specific)

Already arch-aware **[verified in code]**: `hostArchName` in `shared/build.gradle.kts`; `NativeFormatterBridge.kt` /
`NativeIperfBridge.kt` select resources by `os.arch`; `desktopApp/build.gradle.kts` stages into `windows-arm64/` on
ARM64 hosts (commit `a5ccf75`, **not yet exercised on real hardware**).

Needed for a native exe:

1. A new task (e.g. `compileIperf3ExeWindowsArm64`) alongside `compileIperf3ExeWindows`, using the chosen toolchain.
   Same conventions: `notCompatibleWithConfigurationCache`, explicit tool paths, `GradleException` on missing tools,
   no fallback to whatever is on `PATH`.
2. Select the task by host arch; on ARM64 hosts stage the native exe instead of the Cygwin one, and stop copying
   `cygwin1.dll` (a native build should not need it — confirm with `dumpbin /dependents` or `llvm-objdump -p`).
3. `WindowsIperf3ProcessRunner` needs no change if the exe is still named `iperf3.exe` in
   `compose.application.resources.dir`.
4. Decide the distribution story: separate x64 and ARM64 MSIs (jpackage bundles the host JVM, so an MSI built on an ARM64
   host contains an ARM64 JVM) vs. shipping only x64 and relying on Prism.
5. Update `CLAUDE.md`'s packaging section (it currently describes x64/Cygwin only).

### Toolchain setup notes for Windows ARM64 build hosts (learned 2026-09-21)

- **JDK:** the repo's `gradle/gradle-daemon-jvm.properties` pins JDK 21, vendor Amazon, with no `WINDOWS.AARCH64`
  download URL, and Gradle's `updateDaemonJvm` (9.7.1) did not generate one **[verified]**. `winget` installed the x64
  Corretto, which would run the daemon emulated and make the build report x64 — remove it. Use a genuine ARM64 JDK 21
  (e.g. Microsoft's) and loosen the vendor line locally; consider dropping `toolchainVendor` in the repo permanently.
- **Kotlin/Native:** "host platform not supported" warning on windows-aarch64 — harmless for desktop builds (iOS targets
  are ignored) **[verified]**.
- **LLVM:** `nativeformat.dll` needs a native clang at `C:\Program Files\LLVM\bin\clang.exe`; the ARM64 LLVM installer
  produces an ARM64 DLL to match the ARM64 JVM.
- **Cygwin (x64):** still required today for the current `iperf3.exe`, at `C:\cygwin64`.

## 8. Risks and open questions

- Emulation overhead may be negligible → native work becomes optional. (Phase 0 answers this.)
- Winsock vs. POSIX semantics: expected differences in areas like `select`/socket options, TCP info reporting, CPU
  affinity, UDP `SO_RCVBUF` behaviour and DSCP/TOS setting **[unverified — a checklist to test, not findings]**.
- Community port drift: if we adopt D, our pinned iperf3 version and theirs will diverge.
- Supply chain: a commercial app bundling a third-party-built binary needs a rebuildable, reviewable source.
- Hard-kill stop (`TerminateProcess`) behaves the same for native and Cygwin builds, but the remote server's reaction is
  unchanged either way — unrelated to this work.
- Windows 10 ARM64 is not supported by MSYS2's ARM64 environment **[sourced]**; decide our minimum supported OS.
- Anti-virus / SmartScreen reactions to an unsigned native `iperf3.exe` inside an MSI — untested.

## 9. How others can help

- Run Phase 0 measurements on WoA hardware and post numbers (hardware, NIC, OS build, command lines).
- Try Spike C or D independently and append results.
- Report Cygwin ARM64 progress if you see it land upstream.

## 10. References

- [ESnet iperf3 repository](https://github.com/esnet/iperf) · [Windows build question, issue #421](https://github.com/esnet/iperf/issues/421)
- [Microsoft: Three Reasons Why You Should Not Use iPerf3 on Windows](https://techcommunity.microsoft.com/blog/networkingblog/three-reasons-why-you-should-not-use-iperf3-on-windows/4117876)
- [Active-Software/iperf3-windows-msvc](https://github.com/Active-Software/iperf3-windows-msvc)
- [MSYS2 ARM64 support](https://www.msys2.org/docs/arm64/) · [Windows-on-ARM-Experiments/msys2-woarm64-build](https://github.com/Windows-on-ARM-Experiments/msys2-woarm64-build)
- Cygwin ARM64: [tracking issue](https://github.com/dennisameling/gnu-toolchain-windows-arm64/issues/2) ·
  [Linaro notes](https://linaro.atlassian.net/wiki/spaces/WOAR/pages/29484384264/Cygwin+Runtime) ·
  [mailing-list thread](https://sourceware.org/pipermail/cygwin/2025-July/258543.html)
- In this repo: `docs/desktop/WINDOWS_DECISION_REVERSAL_CASE_STUDY.md`, `shared/build.gradle.kts` (`compileIperf3ExeWindows`),
  `desktopApp/build.gradle.kts` (`stageWindowsIperf3`), `WindowsIperf3ProcessRunner.kt`.
