package com.jabramson.iperf3_network_tester_kmp.cli

import com.jabramson.iperf3_network_tester_kmp.model.Iperf3RunningState
import com.jabramson.iperf3_network_tester_kmp.runner.Iperf3Executor
import com.jabramson.iperf3_network_tester_kmp.utils.formatTime
import com.jabramson.iperf3_network_tester_kmp.utils.getAverage
import com.jabramson.iperf3_network_tester_kmp.utils.getMaximum
import com.jabramson.iperf3_network_tester_kmp.utils.getMedian
import com.jabramson.iperf3_network_tester_kmp.utils.getMinimum
import com.jabramson.iperf3_network_tester_kmp.utils.getSampleSize
import com.jabramson.iperf3_network_tester_kmp.utils.getStandardDeviation
import com.jabramson.iperf3_network_tester_kmp.viewmodel.UiInputData
import kotlinx.coroutines.runBlocking

/**
 * Command-line entry point for running a real iperf3 test without the GUI,
 * printing live terminal-updating output via [AnsiTerminal].
 *
 * Deliberately thin: [Iperf3Executor] already does all the real work --
 * running libiperf, parsing its output into an [Iperf3RunningState], stall
 * detection, cancellation -- identically to what the GUI's ViewModel calls.
 * This just renders that same state to a terminal with ANSI escape codes
 * instead of to Compose widgets, the same engine driving two different
 * outputs. See `AnsiTerminal.kt`'s doc comment for the reference this mode
 * is modeled on.
 *
 * Scope note: unlike [BandWidthTester](https://github.com/jerryabramson/BandWidthTester),
 * this doesn't (yet) port its `-l`/repeat-loop mode or its Android-over-`adb`
 * mode -- Android already has its own real in-app UI, and repeat/loop mode
 * is a natural follow-up rather than part of this first pass.
 */
object CliRunner {
    private val lock = Any()
    private val tick = charArrayOf('-', '\\', '|', '/')

    /** @return the process exit code: iperf3's own return code on a real run,
     * 0 after printing `--help`, 1 on a command-line usage error. */
    fun run(args: Array<String>): Int {
        val options = CliArgs.parse(args)

        if (options.help) {
            println(CliArgs.usage())
            return 0
        }
        if (options.error != null) {
            System.err.println("${AnsiTerminal.Color.RED.fg()}${options.error}${AnsiTerminal.reset()}")
            System.err.println()
            System.err.println(CliArgs.usage())
            return 1
        }

        return runTest(options.input)
    }

    /** Minimum real time between status-line redraws. The native bridge's
     * `onTime` ticks far faster than this (observed: roughly every 10-15ms) --
     * nobody can perceive updates faster than this anyway, and redrawing on
     * every single tick is a lot of wasted terminal I/O for a long-running test. */
    private const val STATUS_REDRAW_INTERVAL_MS = 150L

    private fun runTest(input: UiInputData): Int {
        var lastMessageCount = 0
        var tickIndex = 0
        var statusShown = false
        var lastState: Iperf3RunningState? = null
        var lastStatusRedrawAt = 0L

        printHeader(input)

        val executor = Iperf3Executor(
            updateProgress = { _, _ -> /* status line is driven by updateElapsedTime below instead */ },
            updateElapsedTime = { elapsedSeconds ->
                synchronized(lock) {
                    val now = System.currentTimeMillis()
                    if (now - lastStatusRedrawAt >= STATUS_REDRAW_INTERVAL_MS) {
                        printStatusLine(elapsedSeconds, input.durationSecs, tick[tickIndex % tick.size])
                        tickIndex++
                        statusShown = true
                        lastStatusRedrawAt = now
                    }
                }
            },
            stdout = { state, newMessage ->
                synchronized(lock) {
                    lastState = state
                    if (statusShown) {
                        print(AnsiTerminal.clearLine())
                        statusShown = false
                    }
                    if (newMessage) {
                        for (j in lastMessageCount until state.messages.size) {
                            println("  ${state.messages[j]}")
                        }
                        lastMessageCount = state.messages.size
                    } else if (state.formattedOutputLine.isNotBlank()) {
                        if (input.isDebugging) {
                            println("${AnsiTerminal.Color.GREY.fg()}RAW: ${state.rawOutputLine}${AnsiTerminal.reset()}")
                        }
                        println(state.formattedOutputLine)
                    }
                }
            },
            stderr = { state, message ->
                synchronized(lock) {
                    lastState = state
                    if (statusShown) {
                        print(AnsiTerminal.clearLine())
                        statusShown = false
                    }
                    println("${AnsiTerminal.Color.RED.fg()}$message${AnsiTerminal.reset()}")
                    if (state.rawOutputLine.isNotBlank() && input.isDebugging) {
                        println("${AnsiTerminal.Color.GREY.fg()}RAW: ${state.rawOutputLine}${AnsiTerminal.reset()}")
                    }
                }
            },
            onTestComplete = { }
        )

        val returnCode = runBlocking { executor.startTest(input) }

        synchronized(lock) {
            if (statusShown) print(AnsiTerminal.clearLine())
            printSummary(lastState, returnCode)
        }

        return returnCode
    }

    private fun printHeader(input: UiInputData) {
        val direction = if (input.isReverse) "download (server sends)" else "upload (client sends)"
        println(
            "${AnsiTerminal.bold()}iperf3${AnsiTerminal.reset()} -> " +
                "${AnsiTerminal.Color.GREEN.bold()}${input.hostName}${AnsiTerminal.reset()}:${input.portNumber} " +
                "[$direction, ${input.parallelStreams} stream(s), ${input.durationSecs}s, omit ${input.skip}s]"
        )
    }

    private fun printStatusLine(elapsedSeconds: String, totalSeconds: Int, spinner: Char) {
        val elapsed = elapsedSeconds.toDoubleOrNull() ?: 0.0
        print(AnsiTerminal.clearLine())
        print(
            "  ${AnsiTerminal.Color.YELLOW.bold()}$spinner${AnsiTerminal.reset()} " +
                "Running ${AnsiTerminal.bold()}${formatTime(elapsed)}${AnsiTerminal.reset()} / ${totalSeconds}s  " +
                progressTrack(elapsed, totalSeconds)
        )
        System.out.flush()
    }

    /** Widest the `[---|   ]` progress track below is allowed to get, regardless
     * of test duration. BandWidthTester's own `printProgress()` -- the design
     * this is adapted from -- draws one track column per second of the test,
     * which works fine for its typical ~10s runs but gets absurd for a long
     * one (a one-hour test would need a 3600-column-wide terminal). Scaling
     * position proportionally within a capped width avoids that. */
    private const val PROGRESS_TRACK_WIDTH = 30

    /**
     * A `[---|      ]`-style bracketed track with a single reverse-video
     * highlighted cell marking how far through the test's duration the
     * current elapsed time is -- the same bracket-and-highlighted-cell
     * technique `MonitorIPerf3Output.printProgress()` uses, adapted to a
     * fixed, capped width instead of one column per second.
     */
    private fun progressTrack(elapsedSeconds: Double, totalSeconds: Int): String {
        if (totalSeconds <= 0) return ""
        val width = PROGRESS_TRACK_WIDTH
        val fraction = (elapsedSeconds / totalSeconds).coerceIn(0.0, 1.0)
        val pos = (fraction * (width - 1)).toInt().coerceIn(0, width - 1)
        val sb = StringBuilder(width + 2)
        sb.append('[')
        for (i in 0 until width) {
            when {
                i == pos -> sb.append(AnsiTerminal.Color.YELLOW.reverse()).append(' ').append(AnsiTerminal.reset())
                i < pos -> sb.append(AnsiTerminal.Color.YELLOW.fg()).append('-').append(AnsiTerminal.reset())
                else -> sb.append(' ')
            }
        }
        sb.append(']')
        return sb.toString()
    }

    private fun printSummary(state: Iperf3RunningState?, returnCode: Int) {
        println()
        if (state != null && state.intervalNumber >= 0) {
            println(getSampleSize(state))
            println(getMaximum(state))
            println(getMinimum(state))
            println(getAverage(state))
            println(getMedian(state))
            println(getStandardDeviation(state))
        }
        val color = if (returnCode == 0) AnsiTerminal.Color.GREEN else AnsiTerminal.Color.RED
        println("${color.bold()}Return Code: $returnCode${AnsiTerminal.reset()}")
    }
}
