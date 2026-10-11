package com.jabramson.iperf3_network_tester_kmp.cli

import com.jabramson.iperf3_network_tester_kmp.model.Iperf3RunningState
import com.jabramson.iperf3_network_tester_kmp.runner.Iperf3Executor
import com.jabramson.iperf3_network_tester_kmp.utils.UnitConvertedData
import com.jabramson.iperf3_network_tester_kmp.utils.getSampleSize
import com.jabramson.iperf3_network_tester_kmp.viewmodel.UiInputData
import kotlinx.coroutines.runBlocking
import java.util.Date

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
 * Visual design (the boxed timestamp/labels, the red/green/blue color
 * scheme, the `[Avg]`/`[Min]`/`[Max]` summary block) is modeled directly on
 * [BandWidthTester](https://github.com/jerryabramson/BandWidthTester)'s own
 * `newTestBandWidth.sh` output -- a deliberately closer visual match than
 * the first pass, per owner feedback with real captured examples. One thing
 * NOT reproduced: BandWidthTester's final summary shows separate sender and
 * receiver interval ranges (e.g. `0.00-10.03` vs `0.00-10.00`); that level
 * of per-direction detail isn't in [Iperf3RunningState] -- the GUI itself
 * doesn't show it either, it's aggregated earlier in the shared output
 * parser -- so this uses one consolidated results line instead of
 * fabricating a second range.
 *
 * Scope note: also unlike BandWidthTester, this doesn't (yet) port its
 * `-l`/repeat-loop mode or its Android-over-`adb` mode -- Android already
 * has its own real in-app UI, and repeat/loop mode is a natural follow-up
 * rather than part of this first pass.
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
        var tickIndex = 0
        var statusShown = false
        var lastState: Iperf3RunningState? = null
        var lastStatusRedrawAt = 0L
        var connectionLinePrinted = false
        var lastMessageCount = 0

        printHeader(input)

        val executor = Iperf3Executor(
            updateProgress = { _, _ -> /* status line is driven by updateElapsedTime below instead */ },
            updateElapsedTime = { elapsedSeconds ->
                synchronized(lock) {
                    val now = System.currentTimeMillis()
                    if (now - lastStatusRedrawAt >= STATUS_REDRAW_INTERVAL_MS) {
                        printStatusLine(input, elapsedSeconds, tick[tickIndex % tick.size])
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

                    if (!connectionLinePrinted && state.localHost.isNotEmpty() && state.remoteHost.isNotEmpty()) {
                        printConnectionLine(state)
                        connectionLinePrinted = true
                    }

                    if (newMessage) {
                        for (j in lastMessageCount until state.messages.size) {
                            // The native/JNI side's own text processing leaves a trailing
                            // newline on these -- without trim(), that embedded '\n' ends
                            // the line before our own color-reset code, breaking it into an
                            // unwanted extra line every time.
                            val message = state.messages[j].trim()
                            // Skip the raw "Local Host:port .../Remote Host:port ..." entries --
                            // printConnectionLine() above already shows that information, colorized.
                            if (!message.startsWith("Local Host:port") &&
                                !message.startsWith("Remote Host:port")
                            ) {
                                println("  $message")
                            }
                        }
                        lastMessageCount = state.messages.size
                    } else if (state.formattedOutputLine.isNotBlank()) {
                        if (input.isDebugging) {
                            println("${AnsiTerminal.Color.GREY.fg()}RAW: ${state.rawOutputLine.trim()}${AnsiTerminal.reset()}")
                        }
                        printIntervalLine(input, state)
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
                    println("${AnsiTerminal.Color.RED.fg()}${message.trim()}${AnsiTerminal.reset()}")
                    if (state.rawOutputLine.isNotBlank() && input.isDebugging) {
                        println("${AnsiTerminal.Color.GREY.fg()}RAW: ${state.rawOutputLine.trim()}${AnsiTerminal.reset()}")
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

    /** The exact argv [Iperf3Executor.startTest] builds internally, rebuilt
     * here only for display -- this doesn't invoke anything, so a flag
     * actually being blank (e.g. `--forceflush` off) is simply left out
     * rather than shown as a stray empty `''` the way the real array
     * (which does include empty-string placeholders) would render. */
    private fun displayArgs(input: UiInputData): List<String> = buildList {
        add("iperf3")
        add("--client"); add(input.hostName)
        add("--port"); add(input.portNumber.toString())
        if (input.isReverse) add("--reverse")
        if (input.isForceFlush) add("--forceflush")
        add("--parallel"); add(input.parallelStreams.toString())
        add("--connect-timeout"); add((if (input.timeout > 0) input.timeout else 3000L).toString())
        add("--time"); add(input.durationSecs.toString())
        add("--omit"); add(input.skip.toString())
    }

    /** How many quoted argv tokens to print per line in the header -- enough
     * that a typical token ("--connect-timeout", a hostname) still keeps
     * each line comfortably under 80 columns, the one terminal width that's
     * safe to assume with no way to query the caller's actual one. */
    private const val ARGS_PER_LINE = 4

    private fun printHeader(input: UiInputData) {
        val timestamp = AnsiTerminal.box(AnsiTerminal.Color.GREEN, Date().toString())
        println("[$timestamp] Executing Command-line:")
        // Printed in short, fixed-size chunks rather than one long logical line --
        // a single line here wraps wherever the caller's terminal happens to be
        // narrow, splitting a colored escape sequence (or even a token itself)
        // wherever the terminal's own column count cuts it off, which this process
        // has no way to query or control. Chunking to a safe width sidesteps
        // relying on the terminal's own auto-wrap at all.
        displayArgs(input).chunked(ARGS_PER_LINE).forEach { chunk ->
            val line = chunk.joinToString(" ") { "'${AnsiTerminal.Color.PURPLE.fg()}$it${AnsiTerminal.reset()}'" }
            println("  $line")
        }
    }

    /** Printed once, the first time [Iperf3RunningState.localHost]/[Iperf3RunningState.remoteHost]
     * are populated -- built from those structured fields directly (not by
     * re-parsing the equivalent plain-text message the shared output parser
     * also adds to [Iperf3RunningState.messages], which [runTest] skips to
     * avoid printing the same information twice). */
    private fun printConnectionLine(state: Iperf3RunningState) {
        println(
            "  Local Host/IP: ${AnsiTerminal.Color.RED.fg()}${state.localHost.trim()}${AnsiTerminal.reset()} " +
                "remote Host/IP: ${AnsiTerminal.Color.GREEN.fg()}${state.remoteHost.trim()}${AnsiTerminal.reset()} " +
                "Remote Port: ${AnsiTerminal.Color.GREEN.fg()}${state.remotePort}${AnsiTerminal.reset()}"
        )
    }

    /** Column the tick/bracket block always starts at, regardless of the
     * preceding label's width -- fixed via [AnsiTerminal.gotoColumn] rather
     * than left-padding with spaces, the same technique
     * `MonitorIPerf3Output`'s own `leftColumnMarker`/`columnSet` jumps use,
     * so the track lines up the same whether the label is "Running "or
     * "Skipping" or the live line has no label yet at all. */
    private const val TRACK_COLUMN = 14

    private fun printStatusLine(input: UiInputData, elapsedSeconds: String, spinner: Char) {
        val elapsed = elapsedSeconds.toDoubleOrNull() ?: 0.0
        print(AnsiTerminal.clearLine())
        print(
            "  ${AnsiTerminal.bold()}Running${AnsiTerminal.reset()}" +
                AnsiTerminal.gotoColumn(TRACK_COLUMN) +
                "${AnsiTerminal.Color.YELLOW.bold()}$spinner${AnsiTerminal.reset()} " +
                progressTrack(input, elapsed, AnsiTerminal.Color.YELLOW)
        )
        System.out.flush()
    }

    /** A completed interval, locked into scrollback -- unlike the transient
     * status line above, this is never overwritten. Labeled "Skipping"/
     * "Running" from [Iperf3RunningState.omitted] (the shared output
     * parser's own real determination, not a guess based on elapsed time),
     * colors the progress track red for an omitted interval and green for a
     * real one (matching BandWidthTester's own red=skip/green=data scheme),
     * and uses [Iperf3RunningState.formattedOutputLine] verbatim for the
     * interval range/bitrate/streams text -- the same validated text the
     * GUI shows, not re-derived here. The tick character sits between the
     * label and the bracket (matching the reference's own
     * `Skipping 1.00-2.00   + (...)` layout), not at the start of the line.
     */
    private fun printIntervalLine(input: UiInputData, state: Iperf3RunningState) {
        val label = if (state.omitted) "Skipping" else "Running "
        val trackColor = if (state.omitted) AnsiTerminal.Color.RED else AnsiTerminal.Color.GREEN
        val elapsed = (state.intervalNumber + 1).toDouble().coerceAtMost(input.durationSecs.toDouble())
        // intervalNumber is -1 for an omitted interval (it doesn't count toward the
        // real numbered sequence) -- Kotlin's % can return a negative result for a
        // negative operand (unlike mod()), so % here would throw indexing tick[-1].
        val spinner = tick[state.intervalNumber.mod(tick.size)]
        println(
            "  ${AnsiTerminal.bold()}$label${AnsiTerminal.reset()}" +
                AnsiTerminal.gotoColumn(TRACK_COLUMN) +
                "${trackColor.fg()}$spinner${AnsiTerminal.reset()} " +
                "${progressTrack(input, elapsed, trackColor)}  " +
                // trim() -- formattedOutputLine carries a trailing '\n' from the native
                // side's own text processing; left in, it ends the line before our
                // color-reset code runs, surfacing as a stray extra line every time.
                "${trackColor.fg()}${state.formattedOutputLine.trim()}${AnsiTerminal.reset()}"
        )
    }

    /** Widest the `[##>   ]`/`(   <##)` progress track below is allowed to
     * get, regardless of test duration. BandWidthTester's own
     * `printProgress()` -- the design this is adapted from -- draws one
     * track column per second of the test, which works fine for its typical
     * ~10s runs but gets absurd for a long one (a one-hour test would need a
     * 3600-column-wide terminal). Scaling position proportionally within a
     * capped width avoids that. */
    private const val PROGRESS_TRACK_WIDTH = 24

    /**
     * A bracketed track with a solid-color filled run and a rounded leading
     * cap marking how far through the test's duration [elapsedSeconds] is --
     * adapted from `MonitorIPerf3Output.printProgress()`'s own technique
     * (a Nerd Font `arrow` glyph as the leading cap, [AnsiTerminal.Color.fillCell]
     * solid-background cells trailing behind it) to a fixed, capped width
     * instead of one column per second of real elapsed time. Direction-aware
     * like the original: `(   <##)` with the cap pointing left and the fill
     * growing right-to-left for a reverse-mode (download) test, `[##>   ]`
     * left-to-right otherwise. When the test is complete, a second cap is
     * drawn at the trailing end too, closing the bar into a solid rounded
     * pill -- matching `printProgress()`'s own extra cap it adds once
     * `resultEntry >= args.times`.
     */
    private fun progressTrack(input: UiInputData, elapsedSeconds: Double, color: AnsiTerminal.Color): String {
        val totalSeconds = input.durationSecs
        if (totalSeconds <= 0) return ""
        val width = PROGRESS_TRACK_WIDTH
        val fraction = (elapsedSeconds / totalSeconds).coerceIn(0.0, 1.0)
        val filled = (fraction * (width - 1)).toInt().coerceIn(0, width - 1)
        val pos = if (input.isReverse) width - 1 - filled else filled
        val complete = fraction >= 1.0
        val arrow = if (input.isReverse) AnsiTerminal.ARROW_LEFT else AnsiTerminal.ARROW_RIGHT

        val sb = StringBuilder(width + 2)
        sb.append(if (input.isReverse) '(' else '[')
        for (i in 0 until width) {
            val filledCell = (!input.isReverse && i < pos) || (input.isReverse && i > pos)
            val endCap = complete && ((!input.isReverse && i == width - 1) || (input.isReverse && i == 0))
            when {
                i == pos || endCap -> sb.append(color.fg()).append(AnsiTerminal.bold()).append(arrow).append(AnsiTerminal.reset())
                filledCell -> sb.append(color.fillCell()).append(' ').append(AnsiTerminal.reset())
                else -> sb.append(' ')
            }
        }
        sb.append(if (input.isReverse) ')' else ']')
        return sb.toString()
    }

    /** Fixed columns for the stat block below, the same `gotoColumn`-over-
     * space-padding technique [TRACK_COLUMN] uses -- padding with a literal
     * string of spaces only lines up by coincidence (it assumes every label
     * renders at exactly the width its character count suggests, which
     * isn't guaranteed once escape codes and variable terminal fonts are
     * involved); an absolute column jump doesn't depend on that. */
    private const val STAT_LABEL_COLUMN = 18
    private const val STAT_VALUE_COLUMN = 30

    private fun formatStat(data: UnitConvertedData): String =
        if (data.value <= 0.0) "N/A" else "%,.2f".format(data.value)

    private fun printStatRow(label: String, labelColor: AnsiTerminal.Color, valueColor: String, data: UnitConvertedData) {
        println(
            AnsiTerminal.gotoColumn(STAT_LABEL_COLUMN) + AnsiTerminal.box(labelColor, label) +
                AnsiTerminal.gotoColumn(STAT_VALUE_COLUMN) +
                "$valueColor${formatStat(data)}${AnsiTerminal.reset()}  " +
                "${AnsiTerminal.bold()}${data.unit}${AnsiTerminal.reset()}"
        )
    }

    private fun printSummary(state: Iperf3RunningState?, returnCode: Int) {
        println()
        if (state != null && state.intervalNumber >= 0) {
            println("  ${getSampleSize(state)}")
            println()
            printStatRow("Avg", AnsiTerminal.Color.GREY, "", state.currentAvg)
            printStatRow("Min", AnsiTerminal.Color.RED, AnsiTerminal.Color.RED.fg(), state.currentMin)
            printStatRow("Max", AnsiTerminal.Color.BLUE, AnsiTerminal.Color.BLUE.fg(), state.currentMax)
            printStatRow("Median", AnsiTerminal.Color.PURPLE, AnsiTerminal.Color.PURPLE.fg(), state.currentMedian)
            printStatRow("StdDev", AnsiTerminal.Color.GREY, "", state.currentStandardDeviation)
        }
        println(AnsiTerminal.horizontalRule(80))
        val rcColor = if (returnCode == 0) AnsiTerminal.Color.GREEN else AnsiTerminal.Color.RED
        val avgSuffix = state?.currentAvg?.takeIf { it.value > 0.0 }?.let { " [Avg=${formatStat(it)} ${it.unit}]" } ?: ""
        println("Return Code: ${rcColor.fg()}${"%03d".format(returnCode)}${AnsiTerminal.reset()}$avgSuffix")
    }
}
