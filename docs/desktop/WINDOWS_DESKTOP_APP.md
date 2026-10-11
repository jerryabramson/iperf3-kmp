# Windows Desktop App: not supported (historical — decision later reversed)

> **Update (2026-09):** This decision was revisited and reversed — Windows
> desktop is now shipped (`./gradlew.bat :desktopApp:packageMsi`), via a
> `ProcessBuilder`-driven subprocess architecture rather than the in-process
> JNI design this document explains rejecting below. See
> `WINDOWS_DECISION_REVERSAL_CASE_STUDY.md` for the full story — what
> changed, why the original call below was reasonable at the time, and what
> it actually cost. The rest of this document is preserved unedited as the
> honest record of that original decision.

Windows was not a supported target for `:desktopApp`. Android, iOS, and
macOS/Linux desktop are unaffected — this was a Windows-only limitation.

## Why

`iperf3native.dll`/`nativeformat.dll` would need to be built via Cygwin's
clang (no viable POSIX-compatible toolchain exists for `third_party/iperf`'s
POSIX-heavy C sources on Windows otherwise — MinGW/MSVC don't provide enough
header compatibility). Cygwin-built DLLs dynamically link against Cygwin's
own runtime (`cygwin1.dll`, `cyggcc_s-seh-1.dll`), and loading that runtime
directly into `java.exe` — required to keep this project's in-process JNI
design, see `IPERF3_NATIVE_INTEGRATION.md` §3.1 — is unreliable: the JVM's
memory layout is large and unusual compared to a typical native process, and
Cygwin's runtime initialization (which needs specific shared-memory regions
at specific addresses) collides with it, crashing the JVM
(`EXCEPTION_ACCESS_VIOLATION`, `MapViewOfFileEx` shared-memory
base-address-collision aborts). This reproduced consistently even after
`rebaseall` (Cygwin's standard remedy for exactly this class of error).

The only known way around it is to not load `cygwin1.dll` into `java.exe` at
all — run the Cygwin-compiled iperf3 code as a genuinely separate child
process instead (`ProcessBuilder`, communicating over stdin/stdout), the same
way the officially distributed Windows `iperf3.exe` actually works
(`cygwin1.dll` lives *next to* a small, dedicated native process, never
inside an unrelated host process). This project already built and rejected
exactly that architecture once before, for Android (a `ProcessBuilder`
subprocess against a real `iperf3` binary on a rooted phone) — it was
replaced by the current in-process JNI design specifically to get away from
subprocess management (process lifecycle, output parsing, permission
requirements). Reintroducing it as a Windows-only special case would mean
maintaining two fundamentally different execution architectures for the same
feature, one of them being the exact pattern already judged not worth
keeping.

Full build/troubleshooting history (the build itself was eventually made to
work, and the app UI does launch — it's specifically running an iperf3 test
that crashes) is preserved on the `win11_build` branch's own
`WINDOWS_DESKTOP_APP.md`, not reproduced here since none of it is actionable
without revisiting this decision.
