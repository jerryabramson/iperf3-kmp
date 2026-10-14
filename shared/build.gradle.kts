/*
 * Copyright (c) 2026, Jerold Abramson
 *
 * This source code is licensed under the BSD 3-Clause License found in
 * the LICENSE file in the root directory of this source tree.
 */

import groovy.lang.ExpandoMetaClassCreationHandle.enable
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.concurrent.TimeUnit

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidxRoom)
}

room {
    schemaDirectory("$projectDir/schemas")
}

// Generated Res class defaults to internal visibility — needed public here
// since :desktopApp (a separate module) consumes it directly, e.g. for the
// Window() icon in main.kt, not just call sites within :shared itself.
compose.resources {
    publicResClass = true
}

// Desktop JVM classifier for the native_format JNI library, mirrored at
// runtime by NativeFormatterBridge.kt (jvmMain) so it can locate the
// matching bundled resource.
val hostOsName: String = System.getProperty("os.name").lowercase().let {
    when {
        it.contains("mac") -> "macos"
        it.contains("win") -> "windows"
        else -> "linux"
    }
}
val hostArchName: String = System.getProperty("os.arch").lowercase().let {
    when (it) {
        "aarch64", "arm64" -> "aarch64"
        else -> "x86_64"
    }
}
val nativeFormatLibFileName: String = when (hostOsName) {
    "macos" -> "libnativeformat.dylib"
    "windows" -> "nativeformat.dll"
    else -> "libnativeformat.so"
}
val nativeFormatResourceDir = layout.buildDirectory.dir("generated/nativeformatResources")

val compileNativeFormatJvm = tasks.register<Exec>("compileNativeFormatJvm") {
    val nativeSrcDir = rootProject.layout.projectDirectory.dir("native")
    val jniSource = nativeSrcDir.file("native_format_jni.c")
    val javaHome = System.getProperty("java.home")
    val jniPlatformDir = when (hostOsName) {
        "macos" -> "darwin"
        "windows" -> "win32"
        else -> "linux"
    }
    val outDir = nativeFormatResourceDir.map { it.dir("nativeformat/$hostOsName-$hostArchName") }
    val outFile = outDir.map { it.file(nativeFormatLibFileName) }

    inputs.file(jniSource)
    inputs.file(nativeSrcDir.file("native_format.h"))
    outputs.file(outFile)

    doFirst { outDir.get().asFile.mkdirs() }

    // native_format.c/.h has zero POSIX dependencies (just stdint.h/
    // stdio.h/string.h + jni.h), so - unlike third_party/iperf's
    // POSIX-heavy sources - it doesn't need Cygwin's compatibility layer
    // to build on Windows, and genuinely shouldn't use it: this library
    // gets System.load()ed *in-process* into java.exe (unlike iperf3.exe,
    // which runs as its own separate process via ProcessBuilder/
    // WindowsIperf3ProcessRunner), and a Cygwin-linked DLL loaded into
    // java.exe crashes reliably - the JVM's memory layout collides with
    // Cygwin's fixed-address shared-memory init (docs/desktop/WINDOWS_DESKTOP_APP.md).
    //
    // This task used to route through `bash -c "exec \"\$0\" \"\$@\"" clang
    // ...` (the same pattern compileIperf3Jvm's Windows branch still
    // correctly uses, since that one genuinely needs Cygwin's clang for
    // iperf3's POSIX-heavy sources) on the theory that Windows'
    // CreateProcess can't resolve `clang` directly, only Cygwin's own bash
    // can. That's true for Cygwin's own symlink-based command shims, but
    // bare `bash` on PATH is ambiguous on any machine with WSL installed
    // too - confirmed as the actual root cause of this task silently
    // producing no output at all on a real Windows test machine: `bash`
    // resolved to WSL's bash.exe (C:\Windows\System32\bash.exe, which wins
    // PATH ahead of Cygwin's), so this was compiling native_format_jni.c
    // with WSL's *Linux*-targeting clang and asking it to write to a
    // Windows-style absolute path, which isn't valid from inside WSL's
    // filesystem view - it failed to produce anything, silently.
    //
    // Fixed by using a genuine native Windows clang (LLVM's official
    // Windows release, NOT Cygwin's or WSL's) directly - a real .exe,
    // unlike Cygwin's symlink-based shims, so CreateProcess/Exec resolves
    // it with no bash wrapper needed at all. Resolved by explicit path
    // rather than trusting `clang` on PATH, since a freshly-installed
    // LLVM's PATH entry may not be visible yet to an already-running shell
    // (observed directly: `clang --version` worked via its full path
    // immediately after `winget install LLVM.LLVM`, but `where clang`
    // didn't find it in that same session). Deliberately no fallback to
    // bare `clang` if it's not at the standard install location: same
    // reasoning as compileIperf3Jvm's Cygwin bash resolution - fail
    // loudly and clearly here rather than risk a differently-configured
    // machine's bare `clang` resolving to something unexpected.
    val windowsClang = "C:\\Program Files\\LLVM\\bin\\clang.exe"
    if (hostOsName == "windows" && !File(windowsClang).exists()) {
        throw GradleException(
            "Native Windows clang not found at $windowsClang - install LLVM's " +
                "official Windows release (winget install LLVM.LLVM, or " +
                "https://releases.llvm.org) to the default location, or update " +
                "this path if installed elsewhere. Deliberately not falling back " +
                "to bare `clang` on PATH - see this task's own doc comment above " +
                "for why an ambiguous toolchain resolution is exactly what caused " +
                "this task to previously silently produce no usable output at all."
        )
    }
    val clangExecutable = if (hostOsName == "windows") windowsClang else "clang"

    val clangCommand = listOf(clangExecutable, "-Wall", "-Wextra") +
        (if (hostOsName == "macos") listOf("-dynamiclib") else listOf("-shared")) +
        // -fPIC is meaningless for clang's Windows/MSVC target (Windows
        // DLLs are always relocatable via base relocation tables, unlike
        // ELF/Mach-O shared libs) - clang just warns "argument unused"
        // rather than erroring, but there's no reason to pass it there.
        (if (hostOsName == "windows") emptyList() else listOf("-fPIC")) +
        listOf(
            "-I", nativeSrcDir.asFile.absolutePath,
            "-I", "$javaHome/include",
            "-I", "$javaHome/include/$jniPlatformDir",
            "-o", outFile.get().asFile.absolutePath,
            jniSource.asFile.absolutePath
        )
    commandLine(clangCommand)
}

// Desktop JVM build of real libiperf 3.21 (native/iperf3_jni.c + the
// vendored third_party/iperf sources), mirroring compileNativeFormatJvm's
// resource-bundling approach but with two extra steps first: iperf3 needs
// its own ./configure run (generates src/iperf_config.h and src/version.h —
// both gitignored by the esnet/iperf submodule itself, so running this
// in-tree doesn't dirty `git status`) before its sources can compile at
// all. Client-only build (no auth/OpenSSL, no SCTP, no server mode) — see
// IPERF3_NATIVE_INTEGRATION.md §6.6.
val iperf3LibFileName: String = when (hostOsName) {
    "macos" -> "libiperf3native.dylib"
    "windows" -> "iperf3native.dll"
    else -> "libiperf3native.so"
}
val iperf3ResourceDir = layout.buildDirectory.dir("generated/iperf3nativeResources")
val iperf3SrcDir = rootProject.layout.projectDirectory.dir("third_party/iperf/src")
// ./configure (and config.sub/config.guess/install-sh...) are shell scripts:
// with CRLF line endings bash fails with `$'\r': command not found` / "syntax
// error near unexpected token `newline'". That is exactly what a Windows Git
// checkout with core.autocrlf=true does to the third_party/iperf submodule
// (the parent repo's .gitattributes can't reach into a submodule). Fail early
// with the actual fix instead of a cryptic bash error from inside configure.
// Builds one bash command line from an argv, so Java's Windows command-line
// quoting only ever sees a single (-c) argument. Passing each argument
// separately through `bash -c 'exec "$0" "$@"'` lost a `-I` path containing a
// space (`C:\Program Files\Microsoft\jdk-...` - the default location for the
// Microsoft/Oracle JDK installers) and gcc then failed with "jni.h: No such
// file". Backslashes become forward slashes (Cygwin gcc accepts `C:/...`).
object Iperf3Shell {
fun cygwinShellQuote(arg: String): String =
    "'" + arg.replace('\\', '/').replace("'", "'\\''") + "'"
fun assertUnixLineEndings(srcDir: File) {
    val configure = File(srcDir.parentFile, "configure")
    if (configure.exists() && configure.readBytes().any { it == '\r'.code.toByte() }) {
        throw GradleException(
            "${configure.absolutePath} has Windows (CRLF) line endings, which breaks ./configure " +
                "under Cygwin. Git converted them on checkout (core.autocrlf=true). Fix, then rebuild:\n" +
                "  git -C third_party/iperf config core.autocrlf false\n" +
                "  git -C third_party/iperf rm -rf --cached -q .\n" +
                "  git -C third_party/iperf reset --hard -q\n" +
                "(or set `git config --global core.autocrlf input` before cloning/updating submodules)."
        )
    }
}
}
val iperf3Sources = listOf(
    "cjson.c", "iperf_api.c", "iperf_client_api.c", "iperf_error.c",
    "iperf_locale.c", "iperf_pthread.c", "iperf_tcp.c", "iperf_time.c",
    "iperf_udp.c", "iperf_util.c", "dscp.c", "net.c", "tcp_info.c",
    "timer.c", "units.c"
)

// third_party/iperf's ./configure output (iperf_config.h/version.h,
// gitignored) is cached across runs so compileIperf3Jvm doesn't re-run
// ./configure every single build - see this task's own comments and
// CLAUDE.md's "No cross-compiling" section for why that's a real problem
// when reusing the same checkout across OSes/toolchain changes (a stale
// config can silently miscompile, e.g. defining HAVE_SYS_ENDIAN_H from a
// prior macOS run when now building on Linux/Windows). Deleting these
// files is all "force a real reconfigure" actually requires - run as TWO
// separate Gradle invocations, not one combined command:
//   ./gradlew cleanIperf3Configure
//   ./gradlew compileIperf3Jvm
// (Tested both ways: a single `./gradlew cleanIperf3Configure
// compileIperf3Jvm` does NOT work, even with mustRunAfter ordering below -
// Gradle decides the whole task graph's up-to-date status before any task
// in it actually executes, so a file this task deletes doesn't get
// noticed by compileIperf3Jvm's own up-to-date check until a later,
// separate invocation actually re-evaluates it against the now-changed
// third_party/iperf/src tree.) Same idea/name works identically on every
// platform - there's nothing OS-specific about deleting two files.
val cleanIperf3Configure = tasks.register("cleanIperf3Configure") {
    group = "build setup"
    description = "Deletes third_party/iperf's cached ./configure output " +
        "(iperf_config.h/version.h) so compileIperf3Jvm reconfigures from " +
        "scratch on its next run, instead of reusing a possibly stale config."
    // Resolved at configuration time so doLast doesn't capture the build
    // script object (see compileIperf3Jvm below).
    val srcDir = iperf3SrcDir.asFile
    doLast {
        listOf("iperf_config.h", "version.h").forEach { name ->
            val file = File(srcDir, name)
            if (file.exists()) {
                logger.lifecycle("cleanIperf3Configure: deleting ${file.absolutePath}")
                file.delete()
            }
        }
    }
}

val compileIperf3Jvm = tasks.register("compileIperf3Jvm") {
    // Values the closures below need, resolved here at configuration time.
    // Referencing script-level vals/functions (osName, iperf3SrcDir,
    // iperf3Sources, ...) from onlyIf/doFirst makes the closure capture the
    // build script object, which the configuration cache can't serialize.
    val osName = hostOsName
    val srcDir = iperf3SrcDir.asFile
    val libFileName = iperf3LibFileName
    val sources = iperf3Sources
    // Not needed on Windows: IperfRunner routes Windows to
    // WindowsIperf3ProcessRunner (a bundled iperf3.exe subprocess, built by
    // compileIperf3ExeWindows), so this in-process JNI library is never
    // loaded there - and a Cygwin-linked DLL loaded into java.exe crashes
    // anyway (see docs/desktop/WINDOWS_DECISION_REVERSAL_CASE_STUDY.md). Building it
    // just cost a Cygwin compile per build and shipped an unused x86_64 DLL,
    // even inside a windows-aarch64 folder on ARM64 hosts.
    onlyIf { osName != "windows" }
    // Ordering only (not a dependency - a normal build shouldn't force a
    // reconfigure every time) - when both tasks are requested together,
    // this guarantees cleanIperf3Configure's deletions happen before
    // compileIperf3Jvm's own up-to-date check runs, so that check actually
    // sees the changed input.
    mustRunAfter(cleanIperf3Configure)
    // Shells out to configure/clang directly via ProcessBuilder (see doFirst
    // below) rather than Project.exec {} (removed as a script-accessible
    // API in Gradle 9.6.1). Configuration-cache safe: every value the
    // closures use is resolved at configuration time above, and helpers
    // are called via the Iperf3Shell object, not script-level functions.

    val nativeSrcDir = rootProject.layout.projectDirectory.dir("native")
    val jniSource = nativeSrcDir.file("iperf3_jni.c")
    val javaHome = System.getProperty("java.home")
    val jniPlatformDir = when (osName) {
        "macos" -> "darwin"
        "windows" -> "win32"
        else -> "linux"
    }
    val outDir = iperf3ResourceDir.map { it.dir("iperf3native/$osName-$hostArchName") }
    val outFile = outDir.map { it.file(libFileName) }
    val objDir = layout.buildDirectory.dir("intermediates/iperf3nativeObjects")
    val configuredMarker = iperf3SrcDir.file("iperf_config.h")

    inputs.file(jniSource)
    inputs.file(nativeSrcDir.file("iperf3_bridge.c"))
    inputs.file(nativeSrcDir.file("iperf3_bridge.h"))
    inputs.dir(iperf3SrcDir)
    outputs.file(outFile)

    doFirst {
        // Plain ProcessBuilder, not Project.exec {} — Gradle 9.6.1 removed
        // Project.exec() as a script-accessible API (configuration-cache
        // compatibility); compileNativeFormatJvm sidesteps this by being a
        // proper Exec task type, but this task needs several sequential
        // process steps (configure, N compiles, one link), so it shells out
        // directly instead.
        fun runProcess(command: List<String>, workingDir: java.io.File) {
            // On Windows this is expected to run under Cygwin: neither
            // `./configure` (a #!/bin/sh script) nor `clang` (a Cygwin
            // "alternatives" symlink, not a literal clang.exe on disk) can
            // be launched directly by Windows' CreateProcess — it does its
            // own literal-filename/PATHEXT search with no shebang or
            // symlink-following support. Routing every command through
            // `bash -c` gets Cygwin's own PATH/symlink resolution instead,
            // the same resolution `which clang` already succeeds under.
            //
            // Resolved by explicit path to Cygwin's own bash.exe, NOT bare
            // `bash` on PATH: confirmed on a real Windows test machine that
            // `bash` there resolves to WSL's bash.exe
            // (C:\Windows\System32\bash.exe, which wins PATH ahead of
            // Cygwin's own C:\cygwin64\bin\bash.exe) - WSL's bash runs a
            // *Linux* environment, not Cygwin's, so third_party/iperf's
            // POSIX-heavy sources would need translating Windows-style
            // absolute paths through WSL's /mnt/c/... view, which none of
            // this task's paths do (this is the exact same root cause
            // compileNativeFormatJvm hit - see its own doc comment - fixed
            // there by switching to a genuine native Windows clang instead,
            // since that library has no POSIX dependencies; not an option
            // here, since these sources genuinely need Cygwin specifically).
            // Deliberately no fallback to bare `bash` if Cygwin isn't at
            // its default install location: that's exactly the silent
            // wrong-toolchain failure mode this fix exists to prevent
            // (bare `bash` finding WSL's instead) - fail loudly here
            // instead of risking the same silent-wrong-bash class of bug
            // resurfacing under a different Cygwin install path.
            val cygwinBash = "C:\\cygwin64\\bin\\bash.exe"
            if (osName == "windows" && !File(cygwinBash).exists()) {
                throw GradleException(
                    "Cygwin's bash.exe not found at $cygwinBash - " +
                        "third_party/iperf's POSIX-heavy sources need real Cygwin " +
                        "to build on Windows (install from https://cygwin.com/ to " +
                        "the default location, or update this path if installed " +
                        "elsewhere). Deliberately not falling back to bare `bash` " +
                        "on PATH: on a machine with WSL installed, that resolves to " +
                        "WSL's bash.exe instead, which silently produces no usable " +
                        "output - see this task's own doc comment above."
                )
            }
            val actualCommand = if (osName == "windows") {
                listOf(cygwinBash, "-c", "exec " + command.joinToString(" ") { Iperf3Shell.cygwinShellQuote(it) })
            } else {
                command
            }
            // This task's own doFirst has no per-command progress output, so
            // even --info shows nothing between "Task :shared:compileIperf3Jvm"
            // starting and the task finishing/failing. Log each command so a
            // stall is diagnosable (which exact step it's on) instead of a
            // silent multi-hour hang.
            logger.lifecycle("compileIperf3Jvm: ${command.joinToString(" ")}")
            val processBuilder = ProcessBuilder(actualCommand)
                .directory(workingDir)
                .redirectErrorStream(true)
            if (osName == "windows") {
                // A non-login, non-interactive `bash -c` invocation doesn't
                // reliably source /etc/profile or set up Cygwin's own PATH
                // the way an interactive Cygwin terminal session does - it
                // just inherits whatever PATH this JVM process itself has
                // (the plain Windows PATH), which has no reason to include
                // Cygwin's bin dir. Confirmed on a real Windows machine:
                // every Cygwin utility (sed, expr, id - all genuinely
                // present at C:\cygwin64\bin) failed "command not found"
                // during ./configure despite bash.exe itself resolving
                // correctly. Prepending it explicitly here sidesteps
                // relying on bash's own login/profile behavior entirely.
                // Confirmed working when tested directly (cmd's `set
                // "PATH=...;%PATH%" && bash.exe -c ...`), but the naive
                // env["PATH"] = ... version of this fix (an exact-case
                // "PATH" key) didn't take effect when run through Gradle -
                // Windows env vars are case-insensitive, but if this JVM's
                // inherited environment already has the key as "Path" (its
                // usual casing on Windows), inserting a separate "PATH"
                // entry can leave both present, with CreateProcess/Cygwin
                // picking whichever one it sees first - not necessarily
                // ours. Finding and updating whatever case the existing key
                // actually has (falling back to "PATH" only if truly
                // absent) avoids that ambiguity.
                val env = processBuilder.environment()
                val cygwinBin = "C:\\cygwin64\\bin"
                val pathKey = env.keys.firstOrNull { it.equals("PATH", ignoreCase = true) } ?: "PATH"
                env[pathKey] = cygwinBin + File.pathSeparator + (env[pathKey] ?: "")
            }
            if (osName == "macos" && System.getenv("MACOSX_DEPLOYMENT_TARGET").isNullOrBlank()) {
                // Same workaround as msTime's Makefile (`export MACOSX_DEPLOYMENT_TARGET ?=
                // $(sw_vers -productVersion | cut -d. -f1-2)`). On Darwin 27 the MacPorts
                // gcc/clang toolchain derives -mmacosx-version-min from the Darwin kernel
                // version (27 -> "18.0"), which clang rejects ("invalid version number"),
                // so ./configure's "C compiler cannot create executables" probe fails.
                // Pin it to the real macOS product version (JVM os.version is e.g. "27.0.1").
                val productVersion = System.getProperty("os.version").split(".").take(2).joinToString(".")
                processBuilder.environment()["MACOSX_DEPLOYMENT_TARGET"] = productVersion
            }
            val process = processBuilder.start()
            // ProcessBuilder defaults stdin to an open, never-written-to pipe.
            // If anything in the chain (bash, configure's autoconf probes,
            // clang/ld) ever reads stdin, it blocks forever waiting for input
            // that will never arrive since nothing closes that pipe. Closing
            // it immediately gives the child EOF instead.
            process.outputStream.close()
            // Drain output on a separate thread — readText() blocks until the
            // child closes its stdout, which would starve waitFor()'s timeout
            // below if done inline on a genuinely hung process.
            //
            // Still captured into outputBuilder either way (for the failure
            // message below), but also streamed live via logger.info - so a
            // normal run (no extra flags) stays exactly as quiet as before,
            // while `--info` shows every line as it happens (configure's
            // "checking for X... yes/no" probes included) without needing
            // to change this script. Deliberately not logger.lifecycle: that
            // verbose on every successful run too would be pure noise for
            // anyone not actively debugging the toolchain.
            val outputBuilder = StringBuilder()
            val readerThread = Thread {
                process.inputStream.bufferedReader().forEachLine {
                    outputBuilder.appendLine(it)
                    logger.info(it)
                }
            }
            readerThread.isDaemon = true
            readerThread.start()
            val finished = process.waitFor(15, TimeUnit.MINUTES)
            if (!finished) {
                process.destroyForcibly()
                throw GradleException("Command timed out after 15m: ${command.joinToString(" ")}\n$outputBuilder")
            }
            readerThread.join(5000)
            val exitCode = process.exitValue()
            if (exitCode != 0) {
                throw GradleException("Command failed ($exitCode): ${command.joinToString(" ")}\n$outputBuilder")
            }
        }

        outDir.get().asFile.mkdirs()
        objDir.get().asFile.mkdirs()

        if (!configuredMarker.asFile.exists()) {
            Iperf3Shell.assertUnixLineEndings(srcDir)
            runProcess(
                listOf("./configure", "--without-sctp", "--with-openssl=no"),
                srcDir.parentFile
            )
        }

        // Cygwin's bundled clang (a much older clang-5.0.1) doesn't
        // correctly interoperate with Cygwin's own (newer, actively
        // maintained) system headers - confirmed on the real Windows
        // machine: compiling iperf_api.c (which pulls in <stdatomic.h> via
        // iperf.h/iperf_api.h, needed for the same C11 atomics
        // native/iperf3_bridge.c also uses) failed with "unknown type name
        // '___wchar_t'; did you mean '__wchar_t'?" inside Cygwin's own
        // /usr/include/stdatomic.h. Cygwin's own gcc (14.4.0, much more
        // current, and the compiler Cygwin's ecosystem is actually built
        // and tested against) has no such issue - used here instead of
        // clang for this Windows/Cygwin path specifically (unrelated to
        // compileNativeFormatJvm's native Windows clang, which stays as
        // clang - that library never touches Cygwin at all).
        val compiler = if (osName == "windows") "gcc" else "clang"

        // -fPIC is meaningless for Windows targets (PE/COFF DLLs are always
        // relocatable via base relocation tables, unlike ELF/Mach-O shared
        // libs - same reasoning compileNativeFormatJvm's own clang
        // invocation already documents).
        val fPicFlags = if (osName == "windows") emptyList() else listOf("-fPIC")

        val objectFiles = mutableListOf<String>()
        for (source in sources) {
            val objFile = objDir.get().file(source.removeSuffix(".c") + ".o").asFile.absolutePath
            runProcess(
                listOf(compiler, "-c") + fPicFlags + listOf(
                    "-I", srcDir.absolutePath,
                    "-DHAVE_CONFIG_H",
                    File(srcDir, source).absolutePath,
                    "-o", objFile
                ),
                objDir.get().asFile
            )
            objectFiles.add(objFile)
        }
        // Both iperf3_bridge.c and iperf3_jni.c #include "iperf3_bridge.h",
        // which lives right next to them in native/, not in
        // third_party/iperf/src/ - this used to rely entirely on the
        // compiler's fallback of also searching a quoted #include's own
        // source file directory, with no explicit -I for nativeSrcDir at
        // all. That fallback is what actually broke on the real Windows
        // machine with Cygwin's gcc ("iperf3_bridge.h: No such file or
        // directory") - passing -I explicitly here is correct on every
        // platform regardless of why the fallback stopped working on this
        // one, so it's not conditional on osName.
        val bridgeSource = nativeSrcDir.file("iperf3_bridge.c")
        val bridgeObjFile = objDir.get().file("iperf3_bridge.o").asFile.absolutePath
        runProcess(
            listOf(compiler, "-c") + fPicFlags + listOf(
                "-I", srcDir.absolutePath,
                "-I", nativeSrcDir.asFile.absolutePath,
                "-DHAVE_CONFIG_H",
                bridgeSource.asFile.absolutePath,
                "-o", bridgeObjFile
            ),
            objDir.get().asFile
        )
        objectFiles.add(bridgeObjFile)

        val jniObjFile = objDir.get().file("iperf3_jni.o").asFile.absolutePath
        runProcess(
            listOf(compiler, "-c") + fPicFlags + listOf(
                "-I", srcDir.absolutePath,
                "-I", nativeSrcDir.asFile.absolutePath,
                "-DHAVE_CONFIG_H",
                "-I", "$javaHome/include",
                "-I", "$javaHome/include/$jniPlatformDir",
                jniSource.asFile.absolutePath,
                "-o", jniObjFile
            ),
            objDir.get().asFile
        )
        objectFiles.add(jniObjFile)

        runProcess(
            buildList {
                add(compiler)
                add(if (osName == "macos") "-dynamiclib" else "-shared")
                addAll(fPicFlags)
                addAll(objectFiles)
                add("-lpthread")
                add("-o")
                add(outFile.get().asFile.absolutePath)
            },
            objDir.get().asFile
        )
    }
}

// Builds a real, standalone iperf3.exe (a genuine CLI executable, not the
// JNI shared library compileIperf3Jvm produces) via the same Cygwin
// toolchain fixes proven working there - see that task's own comments for
// the full history (bash resolution, PATH injection, -fPIC, gcc-vs-clang).
// This is what WindowsIperf3ProcessRunner (jvmMain) actually spawns via
// ProcessBuilder - see its own doc comment for why Windows needs a real
// separate-process executable instead of the in-process JNI path the
// other two desktop OSes use.
//
// main.c (iperf3's real CLI entry point - never touched by the JNI/bridge
// path, which calls iperf_run_client() directly instead) unconditionally
// calls iperf_run_server() too, even though WindowsIperf3ProcessRunner's
// argv always passes --client and so never actually reaches that branch
// at runtime - the linker still needs that symbol resolved regardless,
// so iperf_server_api.c has to be compiled in here (confirmed clean: no
// iperf_auth.c/HAVE_SSL dependency, consistent with the client-only
// build's own --with-openssl=no already working).
//
// Output goes to a build directory (see iperf3ExeOutDir), together with
// the one DLL it needs (cygwin1.dll - confirmed via `ldd`; everything else
// is a Windows system DLL). :desktopApp picks both up from there and
// bundles them into the app's resources dir, which is where
// WindowsIperf3ProcessRunner looks for them at runtime.
val iperf3ExeOutDir = layout.buildDirectory.dir("generated/iperf3exe/windows-x64")
val compileIperf3ExeWindows = tasks.register("compileIperf3ExeWindows") {
    group = "build setup"
    description = "Builds a standalone iperf3.exe via Cygwin, plus the cygwin1.dll " +
        "it needs, into build/generated/iperf3exe/windows-x64 for :desktopApp to bundle."
    // Resolved at configuration time, captured below - see compileIperf3Jvm.
    val osName = hostOsName
    val srcDir = iperf3SrcDir.asFile
    val sources = iperf3Sources
    onlyIf { osName == "windows" }

    val objDir = layout.buildDirectory.dir("intermediates/iperf3ExeObjects")
    val exeSources = sources + listOf("main.c", "iperf_server_api.c")
    val configuredMarker = iperf3SrcDir.file("iperf_config.h")
    val outDirProvider = iperf3ExeOutDir
    val outExeFile = outDirProvider.get().file("iperf3.exe").asFile
    val cygwinDll = File("C:\\cygwin64\\bin\\cygwin1.dll")

    inputs.dir(iperf3SrcDir)
    outputs.dir(outDirProvider)

    doFirst {
        val cygwinBash = "C:\\cygwin64\\bin\\bash.exe"
        if (!File(cygwinBash).exists()) {
            throw GradleException(
                "Cygwin's bash.exe not found at $cygwinBash - see " +
                    "compileIperf3Jvm's own doc comment for why this task needs " +
                    "real Cygwin specifically, no fallback."
            )
        }

        fun runProcess(command: List<String>, workingDir: java.io.File) {
            val actualCommand = listOf(cygwinBash, "-c", "exec " + command.joinToString(" ") { Iperf3Shell.cygwinShellQuote(it) })
            logger.lifecycle("compileIperf3ExeWindows: ${command.joinToString(" ")}")
            val processBuilder = ProcessBuilder(actualCommand)
                .directory(workingDir)
                .redirectErrorStream(true)
            val env = processBuilder.environment()
            val cygwinBin = "C:\\cygwin64\\bin"
            val pathKey = env.keys.firstOrNull { it.equals("PATH", ignoreCase = true) } ?: "PATH"
            env[pathKey] = cygwinBin + File.pathSeparator + (env[pathKey] ?: "")

            val process = processBuilder.start()
            process.outputStream.close()
            val outputBuilder = StringBuilder()
            val readerThread = Thread {
                process.inputStream.bufferedReader().forEachLine {
                    outputBuilder.appendLine(it)
                    logger.info(it)
                }
            }
            readerThread.isDaemon = true
            readerThread.start()
            val finished = process.waitFor(15, TimeUnit.MINUTES)
            if (!finished) {
                process.destroyForcibly()
                throw GradleException("Command timed out after 15m: ${command.joinToString(" ")}\n$outputBuilder")
            }
            readerThread.join(5000)
            val exitCode = process.exitValue()
            if (exitCode != 0) {
                throw GradleException("Command failed ($exitCode): ${command.joinToString(" ")}\n$outputBuilder")
            }
        }

        objDir.get().asFile.mkdirs()
        outExeFile.parentFile.mkdirs()
        if (!cygwinDll.exists()) {
            throw GradleException("cygwin1.dll not found at $cygwinDll - required next to iperf3.exe.")
        }
        cygwinDll.copyTo(File(outExeFile.parentFile, "cygwin1.dll"), overwrite = true)

        if (!configuredMarker.asFile.exists()) {
            Iperf3Shell.assertUnixLineEndings(srcDir)
            runProcess(
                listOf("./configure", "--without-sctp", "--with-openssl=no"),
                srcDir.parentFile
            )
        }

        val objectFiles = mutableListOf<String>()
        for (source in exeSources) {
            val objFile = objDir.get().file(source.removeSuffix(".c") + ".o").asFile.absolutePath
            runProcess(
                listOf(
                    "gcc", "-c",
                    "-I", srcDir.absolutePath,
                    "-DHAVE_CONFIG_H",
                    File(srcDir, source).absolutePath,
                    "-o", objFile
                ),
                objDir.get().asFile
            )
            objectFiles.add(objFile)
        }

        runProcess(
            listOf("gcc") + objectFiles + listOf("-lpthread", "-o", outExeFile.absolutePath),
            objDir.get().asFile
        )
    }
}

tasks.matching { it.name == "processJvmMainResources" || it.name == "jvmProcessResources" }.configureEach {
    dependsOn(compileNativeFormatJvm)
    dependsOn(compileIperf3Jvm)
}

// iOS build of real libiperf 3.21, one static library per iOS target
// (device arm64 vs simulator arm64 — different SDKs, not interchangeable
// despite sharing an instruction set) — consumed via the iperf3Bridge
// cinterop below (staticLibrary/libraryPath extraOpts, not the JNI adapter:
// native/iperf3_jni.c is Android/desktop-only, iOS talks to
// native/iperf3_bridge.c directly with zero JNI involved — see
// IPERF3_NATIVE_INTEGRATION.md §8.4/§5). Cross-compiled from this macOS
// host via `xcrun -sdk <sdk> clang`/`libtool`, same client-only source list
// as compileIperf3Jvm/the Android CMake build, plus iperf3_bridge.c instead
// of iperf3_jni.c. iOS has no on-target ./configure run, so
// iperf_config_ios.h (hand-written, mirrors the real macOS-generated
// config — see its own header comment) and a templated version.h stand in,
// same idea as Android's CMake-generated headers.
val iperf3IosSdkByTarget = mapOf(
    "iosArm64" to "iphoneos",
    "iosSimulatorArm64" to "iphonesimulator"
)
val iperf3IosDeploymentTarget = "18.2" // matches iosApp.xcodeproj's IPHONEOS_DEPLOYMENT_TARGET
val iperf3IosGeneratedDir = layout.buildDirectory.dir("generated/iperf3iosBridge")

val compileIperf3IosTasks = iperf3IosSdkByTarget.mapValues { (targetName, sdk) ->
    val capitalizedTarget = targetName.replaceFirstChar { it.uppercase() }
    tasks.register("compileIperf3Bridge$capitalizedTarget") {
        notCompatibleWithConfigurationCache("shells out to xcrun/clang/libtool directly, not via Project.exec")
        onlyIf { org.gradle.internal.os.OperatingSystem.current().isMacOsX }

        val nativeSrcDir = rootProject.layout.projectDirectory.dir("native")
        val bridgeSource = nativeSrcDir.file("iperf3_bridge.c")
        val outDir = iperf3IosGeneratedDir.map { it.dir(targetName) }
        val outFile = outDir.map { it.file("libiperf3bridge.a") }
        val objDir = layout.buildDirectory.dir("intermediates/iperf3iosObjects/$targetName")
        val genHeaderDir = layout.buildDirectory.dir("intermediates/iperf3iosGenerated/$targetName")
        val minVersionFlag = if (sdk == "iphonesimulator") {
            "-mios-simulator-version-min=$iperf3IosDeploymentTarget"
        } else {
            "-mios-version-min=$iperf3IosDeploymentTarget"
        }

        inputs.file(bridgeSource)
        inputs.file(nativeSrcDir.file("iperf3_bridge.h"))
        inputs.file(nativeSrcDir.file("iperf_config_ios.h"))
        inputs.dir(iperf3SrcDir)
        outputs.file(outFile)

        doFirst {
            fun runProcess(command: List<String>, workingDir: java.io.File) {
                val process = ProcessBuilder(command)
                    .directory(workingDir)
                    .redirectErrorStream(true)
                    .start()
                val output = process.inputStream.bufferedReader().readText()
                val exitCode = process.waitFor()
                if (exitCode != 0) {
                    throw GradleException("Command failed ($exitCode): ${command.joinToString(" ")}\n$output")
                }
            }

            outDir.get().asFile.mkdirs()
            objDir.get().asFile.mkdirs()
            genHeaderDir.get().asFile.mkdirs()

            nativeSrcDir.file("iperf_config_ios.h").asFile
                .copyTo(genHeaderDir.get().file("iperf_config.h").asFile, overwrite = true)
            val versionHIn = iperf3SrcDir.file("version.h.in").asFile.readText()
            genHeaderDir.get().file("version.h").asFile
                .writeText(versionHIn.replace("@PACKAGE_VERSION@", "3.21"))

            val objectFiles = mutableListOf<String>()
            for (source in iperf3Sources) {
                val objFile = objDir.get().file(source.removeSuffix(".c") + ".o").asFile.absolutePath
                runProcess(
                    listOf(
                        "xcrun", "-sdk", sdk, "clang", "-c", "-arch", "arm64", minVersionFlag,
                        "-I", genHeaderDir.get().asFile.absolutePath,
                        "-I", iperf3SrcDir.asFile.absolutePath,
                        "-DHAVE_CONFIG_H",
                        iperf3SrcDir.file(source).asFile.absolutePath,
                        "-o", objFile
                    ),
                    objDir.get().asFile
                )
                objectFiles.add(objFile)
            }

            val bridgeObjFile = objDir.get().file("iperf3_bridge.o").asFile.absolutePath
            runProcess(
                listOf(
                    "xcrun", "-sdk", sdk, "clang", "-c", "-arch", "arm64", minVersionFlag,
                    "-I", genHeaderDir.get().asFile.absolutePath,
                    "-I", iperf3SrcDir.asFile.absolutePath,
                    "-DHAVE_CONFIG_H",
                    bridgeSource.asFile.absolutePath,
                    "-o", bridgeObjFile
                ),
                objDir.get().asFile
            )
            objectFiles.add(bridgeObjFile)

            runProcess(
                buildList {
                    add("xcrun"); add("-sdk"); add(sdk)
                    add("libtool"); add("-static")
                    add("-o"); add(outFile.get().asFile.absolutePath)
                    addAll(objectFiles)
                },
                objDir.get().asFile
            )
        }
    }
}

// StringFormatterTest calls the real native_format JNI library, which
// System.loadLibrary() can only resolve on an actual Android
// device/emulator (via the packaged .so) — androidHostTest runs on a plain
// Robolectric JVM host with no such library available, so it can't pass
// here regardless of correctness. Real coverage for this path comes from
// jvmTest (JNI, host-compiled) and iosSimulatorArm64Test (cinterop), both
// of which exercise the same native_format.h logic end to end.
tasks.matching { it.name.contains("AndroidHostTest", ignoreCase = false) }.configureEach {
    if (this is Test) {
        filter {
            excludeTestsMatching("com.jabramson.iperf3_network_tester_kmp.utils.StringFormatterTest")
            isFailOnNoMatchingTests = false
        }
    }
}

kotlin {
    jvmToolchain(17)

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
            // sqlite-framework's cinterop declares the sqlite3 C API but
            // doesn't bundle an implementation; NativeSQLiteDriver needs
            // the OS-provided library linked in explicitly.
            linkerOpts.add("-lsqlite3")
        }
        iosTarget.compilations.getByName("main") {
            cinterops {
                create("nativeFormat") {
                    defFile(project.file("src/nativeInterop/cinterop/native_format.def"))
                    compilerOpts("-I${rootProject.layout.projectDirectory.dir("native").asFile.absolutePath}")
                }
                create("nativeLog") {
                    defFile(project.file("src/nativeInterop/cinterop/native_log.def"))
                    compilerOpts("-I${rootProject.layout.projectDirectory.dir("native").asFile.absolutePath}")
                }
                create("iperf3Bridge") {
                    defFile(project.file("src/nativeInterop/cinterop/iperf3_bridge.def"))
                    compilerOpts("-I${rootProject.layout.projectDirectory.dir("native").asFile.absolutePath}")
                    val libDir = iperf3IosGeneratedDir.get().dir(iosTarget.name).asFile.absolutePath
                    extraOpts("-libraryPath", libDir, "-staticLibrary", "libiperf3bridge.a")
                }
            }
        }
        // The generated cinterop task's exact name (KMP convention:
        // "cinterop<InteropName><TargetName>", both capitalized) needs the
        // matching per-target static lib to already exist — wire the
        // dependency here since compileIperf3IosTasks is keyed by the same
        // target name string used above.
        val capitalizedTarget = iosTarget.name.replaceFirstChar { it.uppercase() }
        tasks.matching { it.name == "cinteropIperf3Bridge$capitalizedTarget" }.configureEach {
            dependsOn(compileIperf3IosTasks.getValue(iosTarget.name))
        }
    }

    jvm()

    android {
        namespace = "com.jabramson.iperf3_network_tester_kmp.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        androidResources {
            enable = true
        }
        withHostTest {
            isIncludeAndroidResources = true
        }
    }

    sourceSets {
        getByName("jvmMain") {
            resources.srcDir(nativeFormatResourceDir)
            resources.srcDir(iperf3ResourceDir)
            dependencies {
                implementation(libs.androidx.sqlite.bundled)
            }
        }
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(project(":nativeformat"))
            implementation(project(":iperf3native"))
            implementation(libs.androidx.sqlite.framework.android)
            // FileProvider, for CSV export's Share sheet (see ShareCsv.android.kt)
            implementation(libs.androidx.core.ktx)
        }
        // sqlite-framework only publishes Android + Apple/Native target
        // variants (it wraps each platform's *native* SQLite framework) —
        // no jvm variant exists, so it can't live in commonMain. The
        // SQLiteDriver interface commonMain actually needs comes in
        // transitively via androidx-room-runtime (which depends on the
        // base androidx.sqlite:sqlite module, which does publish jvm).
        // sourceSets.getByName("iosMain") throws "not found" here (see
        // CLAUDE.md's :shared module note), so add the dependency to both
        // real iOS leaf source sets directly instead.
        getByName("iosArm64Main").dependencies {
            implementation(libs.androidx.sqlite.framework)
        }
        getByName("iosSimulatorArm64Main").dependencies {
            implementation(libs.androidx.sqlite.framework)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.compose.material3.adaptive)
            implementation(libs.androidx.room.runtime)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}


dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
    // Room's KSP compiler must be registered per target explicitly — it
    // isn't picked up automatically from commonMain in a KMP module.
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspIosArm64", libs.androidx.room.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room.compiler)
    add("kspJvm", libs.androidx.room.compiler)
}
