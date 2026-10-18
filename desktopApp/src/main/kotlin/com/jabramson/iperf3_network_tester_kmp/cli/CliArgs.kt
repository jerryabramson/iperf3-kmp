/*
 * Copyright (c) 2026, Jerold Abramson
 *
 * This source code is licensed under the BSD 3-Clause License found in
 * the LICENSE file in the root directory of this source tree.
 */

package com.jabramson.iperf3_network_tester_kmp.cli

import com.jabramson.iperf3_network_tester_kmp.viewmodel.DefaultInputData
import com.jabramson.iperf3_network_tester_kmp.viewmodel.UiInputData

/**
 * Result of parsing the command line: either a ready-to-run [input], or
 * [help]/[error] for the caller to report and exit on instead of running a test.
 */
data class CliOptions(
    val input: UiInputData = UiInputData(),
    val help: Boolean = false,
    val version: Boolean = false,
    val error: String? = null,
    /** Number of consecutive tests to run. 1 (the default) is today's single-run behavior. */
    val loopCount: Int = 1,
    /**
     * Optional port range to cycle through across loop iterations (round-robin,
     * wrapping back to [IntRange.first] once past [IntRange.last]), instead of
     * every iteration reusing the single `--port` value. Lets a personal
     * multi-instance test server (e.g. iperf3's own documented systemd
     * `iperf3-server@.service` pattern, one process per port) back repeated
     * CLI test runs without hitting "server is busy running a test" from
     * landing back-to-back on the same still-finishing instance.
     */
    val portPool: IntRange? = null,
)

/**
 * Hand-rolled command-line argument parser for CLI mode -- no third-party
 * dependency, matching both this project's existing no-extra-dependencies
 * style and the flag conventions of
 * [BandWidthTester](https://github.com/jerryabramson/BandWidthTester)
 * (`-c`/`-t`/`-P`/`-O`/`-R`/`-v`), an earlier standalone iperf3 CLI wrapper
 * this mode is modeled on.
 *
 * Builds a [UiInputData] -- the same input type
 * [com.jabramson.iperf3_network_tester_kmp.runner.Iperf3Executor] already
 * accepts from the GUI's ViewModel, so CLI mode runs the exact same engine
 * instead of re-implementing iperf3 argument handling or output parsing.
 */
object CliArgs {

    fun parse(args: Array<String>): CliOptions {
        var hostName = ""
        var portNumber = DefaultInputData.PORT_NUMBER
        var durationSecs = DefaultInputData.DURATION
        var parallelStreams = DefaultInputData.PARALLEL_STREAMS
        var skip = DefaultInputData.SKIP
        var timeout = DefaultInputData.TIMEOUT
        var isForceFlush = DefaultInputData.FORCE_FLUSH
        var isReverse = false
        var isDebugging = DefaultInputData.IS_DEBUGGING
        var loopCount = 1
        var portPool: IntRange? = null

        var i = 0
        var sawHost = false

        fun nextValue(): String? {
            if (i + 1 >= args.size) return null
            i++
            return args[i]
        }

        while (i < args.size) {
            val arg = args[i]
            when {
                arg == "-h" || arg == "--help" -> return CliOptions(help = true)
                arg == "-V" || arg == "--version" -> return CliOptions(version = true)

                arg == "-c" || arg == "--client" -> {
                    hostName = nextValue() ?: return missing(arg)
                    sawHost = true
                }

                arg == "-p" || arg == "--port" -> {
                    val v = nextValue() ?: return missing(arg)
                    portNumber = v.toIntOrNull() ?: return invalid(arg, v)
                }

                arg == "-t" || arg == "--time" -> {
                    val v = nextValue() ?: return missing(arg)
                    durationSecs = v.toIntOrNull() ?: return invalid(arg, v)
                }

                arg == "-P" || arg == "--parallel" -> {
                    val v = nextValue() ?: return missing(arg)
                    parallelStreams = v.toIntOrNull() ?: return invalid(arg, v)
                }

                arg == "-O" || arg == "--omit" -> {
                    val v = nextValue() ?: return missing(arg)
                    skip = v.toIntOrNull() ?: return invalid(arg, v)
                }

                arg == "--timeout" -> {
                    val v = nextValue() ?: return missing(arg)
                    timeout = v.toLongOrNull() ?: return invalid(arg, v)
                }

                arg == "-R" || arg == "--reverse" -> isReverse = true
                arg == "--upload" -> isReverse = false
                arg == "--no-flush" -> isForceFlush = false
                arg == "-v" || arg == "--verbose" -> isDebugging = true

                arg == "-l" || arg == "--loop" -> {
                    val v = nextValue() ?: return missing(arg)
                    loopCount = v.toIntOrNull()?.takeIf { it >= 1 } ?: return invalid(arg, v)
                }

                arg == "--port-pool" -> {
                    val v = nextValue() ?: return missing(arg)
                    val match = Regex("""^(\d+)-(\d+)$""").find(v) ?: return invalid(arg, v)
                    val (startStr, endStr) = match.destructured
                    val start = startStr.toIntOrNull()
                    val end = endStr.toIntOrNull()
                    if (start == null || end == null || start !in 1..65535 || end !in 1..65535 || start > end) {
                        return invalid(arg, v)
                    }
                    portPool = start..end
                }

                !arg.startsWith("-") && !sawHost -> {
                    hostName = arg
                    sawHost = true
                }

                else -> return CliOptions(error = "Unrecognized argument: $arg")
            }
            i++
        }

        if (!sawHost || hostName.isBlank()) {
            return CliOptions(error = "Must supply an iperf3 server hostname.")
        }

        return CliOptions(
            input = UiInputData(
                hostName = hostName,
                portNumber = portNumber,
                durationSecs = durationSecs,
                parallelStreams = parallelStreams,
                skip = skip,
                timeout = timeout,
                isForceFlush = isForceFlush,
                isReverse = isReverse,
                isDebugging = isDebugging,
            ),
            loopCount = loopCount,
            portPool = portPool,
        )
    }

    private fun missing(flag: String) = CliOptions(error = "Missing value for $flag")
    private fun invalid(flag: String, value: String) = CliOptions(error = "Invalid value for $flag: '$value'")

    fun usage(): String = """
        Usage: iperf3KMP <server> [options]
               iperf3KMP -c <server> [options]

        Runs a real iperf3 client test and prints live, terminal-updating
        output -- the same test engine the GUI uses, driven from the command
        line instead. With no arguments at all, the GUI launches instead.

        Options:
          -c, --client <host>     iperf3 server hostname or IP (or give it positionally)
          -p, --port <port>       server port                  (default: ${DefaultInputData.PORT_NUMBER})
          -t, --time <seconds>    test duration                (default: ${DefaultInputData.DURATION})
          -P, --parallel <n>      parallel streams              (default: ${DefaultInputData.PARALLEL_STREAMS})
          -O, --omit <seconds>    seconds of warm-up to omit    (default: ${DefaultInputData.SKIP})
          -R, --reverse           server sends, client receives (default: matches the GUI's default)
              --upload            client sends, server receives
              --timeout <ms>      connection timeout in ms      (default: ${DefaultInputData.TIMEOUT})
              --no-flush          disable --forceflush
          -v, --verbose           also print each raw iperf3 output line
          -l, --loop <n>          run n consecutive tests        (default: 1)
              --port-pool <a>-<b> cycle ports a..b across loop iterations instead of
                                   reusing --port every time -- pairs with a personal
                                   multi-instance test server (e.g. iperf3's own
                                   documented systemd iperf3-server@.service pattern)
                                   to avoid "server is busy" when looping fast
          -V, --version           print the version and exit
          -h, --help              show this help and exit
    """.trimIndent()
}
