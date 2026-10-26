# Native Windows iperf3 (MSVC) — integration plan

**Status:** plan, written 2026-10-25. Nothing implemented yet.
**Supersedes:** the option analysis in [WINDOWS_ARM64_IPERF3_PLAN.md](WINDOWS_ARM64_IPERF3_PLAN.md).
Its Spike C (MinGW) failed and Spike D (community MSVC port) succeeded; see §2.

## 1. Goal

Ship a **native** iperf3 on Windows, built from source in this repo, with no Cygwin:

- **Windows ARM64:** the app becomes fully native (ARM64 JVM, `nativeformat.dll` *and*
  iperf3). Today `iperf3.exe` + `cygwin1.dll` are x86_64 and run under Prism emulation.
- **Windows x64 (later, separate decision):** the same port builds x64, which would remove
  Cygwin from the project entirely.

Two delivery shapes, done in this order:

| Phase | Shape | Summary |
|---|---|---|
| **1** | Separate `iperf3.exe`, run via `ProcessBuilder` (as today) | Smallest change: swap which exe gets built and bundled on ARM64 |
| **2** | In-process `iperf3native.dll` via JNI, like macOS/Linux | Windows joins the shared desktop code path; `WindowsIperf3ProcessRunner` goes away |

Phase 1 is the low-risk step that ships first. Phase 2 is feasible (§6) but needs more porting
work, and Phase 1 gives it a working baseline to compare against.

## 2. Background: how we got here (2026-10-25)

- **Why Windows uses a separate exe today:** the Cygwin-built iperf3 can't be loaded into
  `java.exe`, because Cygwin's runtime DLL crashes there. So `WindowsIperf3ProcessRunner`
  spawns `iperf3.exe` + `cygwin1.dll` as a child process instead. Cygwin (and MSYS2's fork of
  it, `msys-2.0.dll`) exists only for x86_64, so on ARM64 that exe is emulated.
- **Spike C, MSYS2 CLANGARM64 (MinGW): failed.** The toolchain works (`configure` detects
  `aarch64-w64-mingw32` once `CC=clang` and `LIBS=-lws2_32` are set), but nearly every iperf3
  source file stops on Unix-only headers (`sys/socket.h`, `netinet/in.h`, `arpa/inet.h`,
  `netdb.h`, `sys/select.h`, `termios.h`). Upstream iperf3 has no native-Windows support, so
  this route means writing a Winsock port ourselves. Two earlier "successful" MSYS2 builds were
  actually built by MSYS's own `/usr/bin/gcc`: x86_64 and dependent on `msys-2.0.dll`, the same
  kind of exe as the Cygwin one.
- **Spike D, community MSVC port: succeeded.**
  [Active-Software/iperf3-windows-msvc](https://github.com/Active-Software/iperf3-windows-msvc)
  built on the Windows ARM64 laptop with Visual Studio 18 (2026):
  ```powershell
  & $msbuild iperf3.sln /p:Configuration=Release /p:Platform=ARM64 `
      /p:PlatformToolset=v145 /p:WindowsTargetPlatformVersion=10.0
  ```
  The project asks for VS 2022's `v143`. On VS 18 the ARM64 toolset is `v145` (`v180` is only
  the MSBuild folder name, not a toolset). Result:
  - `dumpbin /headers` → `AA64 machine (ARM64)`.
  - `iperf3 --version` → `iperf 3.21 (cJSON 1.7.15)`, `Windows ... MSVC arm64`.
  - `dumpbin /dependents` → only `WS2_32`, `bcrypt`, `ADVAPI32`, `KERNEL32`. The C runtime is
    linked statically: one self-contained exe, with no VC++ redistributable and no `cygwin1.dll`.

## 3. The community port

- 4 commits (2026-07-14 to 07-18), single maintainer, 1 star, no published release binaries.
  Unofficial. BSD-3-Clause, the same license as upstream.
- Based on the **official ESnet iperf 3.21 tarball**, the same version as our
  `third_party/iperf` submodule (tag `3.21`).
- Diff against our pinned 3.21 (generated files excluded):
  - **~150 lines in 8 upstream files:** `iperf_api.c` (+68), `tcp_info.c` (+29), `main.c` (+17),
    `iperf_client_api.c` (+16), `iperf_locale.c` (+9), `iperf_server_api.c` (+8),
    `iperf_util.c` (+8), `iperf.h` (+2).
  - **`windows/compat/`, ~1,250 lines in 29 files:** stand-in Unix headers (`sys/socket.h`,
    `netinet/*.h`, `arpa/inet.h`, `netdb.h`, `pthread.h`, `unistd.h`, `poll.h`, `termios.h`, …)
    that map onto Winsock/Win32, plus `win32_compat.c`, `getopt_long.c` and `win32_service.c`.
  - Hand-written `iperf_config.h` / `version.h`, and `iperf3.sln` / `iperf3.vcxproj`.
- Not supported (none needed by our client-only use): SCTP, OpenSSL auth, Linux FQ pacing,
  `sendfile` zero-copy, `-D` daemon mode (replaced by a Windows service).
- `main.c` calls `iperf_win32_startup()` (Winsock `WSAStartup`) before anything else. This
  matters for Phase 2.

## 4. Decision: where the source lives

| Option | How | Pros | Cons |
|---|---|---|---|
| (a) Fork + second submodule | Fork the port to our GitHub, add `third_party/iperf-windows` pinned to a commit | Simplest; the fork protects against the original disappearing | Two copies of iperf3 3.21 in the repo, which drift on every version bump |
| **(b) Keep ESnet as the only iperf3 source, carry the port's delta** | Copy `windows/compat/` to `native/windows/compat/`; keep the ~150-line change as `native/windows/iperf3-3.21-msvc.patch`, applied to a copy of the sources under `build/` (never to the submodule) | One upstream source; small change set that we own and review; matches how `native/iperf_config_ios.h` and the bridge already adapt upstream | We re-apply/refresh the patch on each iperf3 upgrade |

**Recommendation: (b).** Keep the port's copyright and license text on its files; don't add our
own headers to them (see the copyright-header rule: never touch third-party notices). Credit the
port in the third-party notices.

Build-tool choice for (b): call **MSBuild** with our own small `.vcxproj` (the port's project,
pointed at the patched copy), or compile directly with the **LLVM clang + Visual Studio**
toolchain the build already uses for `nativeformat.dll`. MSBuild is the proven path, since the
port is only tested with Microsoft's compiler. clang avoids a project file but is untested.
**Start with MSBuild.**

## 5. Phase 1 — native `iperf3.exe` (ProcessBuilder, unchanged runtime model)

1. **Vet the port** before copying anything: read the ~150-line diff and `windows/compat/` in
   full (it ships inside a commercial app).
2. **Bring in the delta** per §4(b): `native/windows/compat/`, the patch, and our own
   `iperf_config.h`/`version.h` for MSVC, with the port's license notice.
3. **Gradle task `compileIperf3ExeWindowsMsvc`** in `shared/build.gradle.kts`:
   - Copy `third_party/iperf/src` to `build/iperf3-msvc/src`, apply the patch, add the compat
     sources.
   - Find Visual Studio with `vswhere` (don't hardcode `...\18\Community`); run MSBuild with
     `Platform=ARM64`, `PlatformToolset` taken from the installed VS (`v145` today), and
     `WindowsTargetPlatformVersion=10.0`.
   - Output: `shared/build/generated/iperf3exe/windows-arm64/iperf3.exe`. Only the exe, no DLLs.
   - Check the output with `dumpbin /headers` (`AA64`) and `/dependents` (system DLLs only), and
     fail the build otherwise. Fail loudly when VS, MSBuild or the ARM64 C++ tools are missing.
     No silent fallback (the existing Windows build-script rule).
   - Only on Windows ARM64 hosts in Phase 1. x64 keeps `compileIperf3ExeWindows` (Cygwin).
4. **Staging:** `desktopApp`'s `stageWindowsIperf3` currently always copies
   `generated/iperf3exe/windows-x64` and depends on the Cygwin task. Make it pick the task and
   folder from the host architecture (it already computes `windows-arm64` vs `windows-x64`).
5. **Runtime:** `WindowsIperf3ProcessRunner` only checks that `iperf3.exe` exists, so no logic
   change is expected. Update its comments (no more `cygwin1.dll` on ARM64). **Verify the
   output still parses.** The port changes 68 lines in `iperf_api.c`, which writes the text
   output that `Iperf3OutputMonitor` parses. Run real tests, including `-R`, `-P 4`, and errors
   (bad host, refused port).
6. **Docs:** `windows-arm64.md` (no Cygwin prerequisite on ARM64, no longer
   "mixed-architecture"), `WINDOWS_ARM64_IPERF3_PLAN.md` (spike results, point here),
   CLAUDE.md's Windows paragraph, `TEST_MATRIX.md`.
7. **Real-machine verification:** build the MSI on the ARM64 laptop; install it on a **clean**
   Windows 11 ARM64 machine (the Parallels VM counts); confirm there's no `cygwin1.dll` in the
   install folder and that `iperf3.exe` shows as ARM64 in Task Manager; run real tests, start/stop
   cycles, and error cases.

Estimated code: roughly 100–200 lines of Gradle/Kotlin, plus the imported port code. Most of the
time goes into steps 1, 5 and 7.

## 6. Phase 2 — in-process JNI (no separate exe)

**Is it possible? Yes, most likely.** The reason Windows uses a separate exe was Cygwin, not
Windows. Cygwin's runtime crashes inside `java.exe`. An MSVC-built DLL has no Cygwin, and
`nativeformat.dll` already proves that MSVC-ABI JNI libraries load fine into this app's JVM on
both Windows architectures. So Windows could use the same `native/iperf3_bridge.c` +
`native/iperf3_jni.c` path as macOS and Linux, built as `iperf3native.dll`.

**What has to change** (from reading `iperf3_bridge.c`/`iperf3_jni.c`):

| Bridge dependency | Windows status | Work |
|---|---|---|
| `pthread_create`/`pthread_join` (3 threads) | Port's compat `pthread.h` shim | Check that the shim covers create/join; otherwise use Win32 threads behind `#ifdef` |
| `pipe()`, `dup()`, `dup2(…, STDERR_FILENO)` for capturing iperf3's output/stderr | Microsoft's C runtime has `_pipe`/`_dup`/`_dup2` | `#ifdef` names/flags (`_O_BINARY`); verify stderr redirection works for a GUI launcher |
| `signal(SIGPIPE, SIG_IGN)` | No `SIGPIPE` on Windows (Winsock returns errors instead) | `#ifndef _WIN32` it out |
| `optind = 1; optreset = 1;` (getopt reset before each parse) | Port's `getopt.h` declares `optind` but no `optreset` | Confirm the port's `getopt_long.c` fully resets on `optind = 1`; otherwise add a reset |
| `<stdio_ext.h>` / `__fsetlocking` | glibc-only (already conditional) | Keep excluded |
| `_Thread_local JNIEnv *` in `iperf3_jni.c` | MSVC supports C11 `_Thread_local` (`/std:c11`) | Compile flag |
| Winsock initialisation | `main.c` calls `iperf_win32_startup()`; a library caller never runs `main` | Call it once in the bridge or in `JNI_OnLoad` |
| `compileIperf3Jvm` is skipped on Windows | | Add a Windows branch: MSBuild DLL project, or clang + VS, with the JDK's `include/win32` |
| `IperfRunner` routes Windows to `WindowsIperf3ProcessRunner` | | Route Windows to `NativeIperfBridge` like the other desktop OSes; delete the process runner |

**What Phase 2 gains:**
- Windows uses the same code path as macOS/Linux desktop, so `WindowsIperf3ProcessRunner`
  (~250 lines of Windows-only code) goes away. That raises the code-sharing figure, which is
  relevant to the research write-up.
- A **graceful stop**: the bridge's stop flag replaces `Process.destroy()`'s hard kill (the known
  "may leave the remote server stuck" limitation).
- Nothing to bundle in `app\resources\`; no separate process.

**What Phase 2 costs or risks:**
- **No crash isolation.** iperf3 calls `exit()` on some fatal errors (`iperf_errexit` →
  `iperf_exit`, `iperf_api.c`). In-process, that ends the whole app, as it already would on
  macOS/Linux/Android. The separate exe currently contains that on Windows.
- **More porting surface:** the bridge itself, not just iperf3, needs Windows `#ifdef`s, and the
  port's compat layer has only been exercised in the exe, not reused as a library across many runs
  in one process (global state, getopt, Winsock startup).
- **Threads:** iperf3 3.21 creates a worker thread per stream. That works in the exe; in-process it
  also has to clean up properly between runs.

**Recommendation:** do Phase 1 first, then Phase 2 as a separate branch, comparing against Phase
1's exe on the same machine. If Phase 2 works, decide then whether x64 should switch to it as well
(that would remove Cygwin and the process runner from the project completely).

## 7. Risks

- **Single-maintainer upstream for the port.** Mitigated by (b): we own the delta.
- **Upgrades:** each new iperf3 version means refreshing the patch and compat layer ourselves.
  Pinning 3.21 is fine for now.
- **Output-format drift** from the port's `iperf_api.c` changes breaking `Iperf3OutputMonitor`
  (§5 step 5).
- **Toolset names change with Visual Studio versions** (`v143` → `v145`); the build task must
  detect them, not hardcode.
- **Microsoft has published a caution about using iperf3 on Windows**
  ([Three Reasons Why You Should Not Use iPerf3 on Windows](https://techcommunity.microsoft.com/blog/networkingblog/three-reasons-why-you-should-not-use-iperf3-on-windows/4117876)).
  Native or not, Windows results may not match Linux results on the same hardware. Worth stating
  in user-facing docs.

## 8. Open decisions

1. Source strategy: (a) fork + submodule, or (b) carry the delta (recommended).
2. Build tool: MSBuild (recommended to start) or clang + VS.
3. After Phase 1 on ARM64: also replace Cygwin on **x64**?
4. Whether and when to do Phase 2 (JNI).

## 9. References

- [Active-Software/iperf3-windows-msvc](https://github.com/Active-Software/iperf3-windows-msvc) and its `README_WINDOWS.md`
- [WINDOWS_ARM64_IPERF3_PLAN.md](WINDOWS_ARM64_IPERF3_PLAN.md): original options and spike definitions
- [WINDOWS_ARM64_PERFORMANCE_TEST.md](WINDOWS_ARM64_PERFORMANCE_TEST.md): emulated vs native throughput
- [WINDOWS_DECISION_REVERSAL_CASE_STUDY.md](WINDOWS_DECISION_REVERSAL_CASE_STUDY.md): why Windows uses a separate process
- [../platform-builds/windows-arm64.md](../platform-builds/windows-arm64.md)
