/*
 * Copyright (c) 2026, Jerold Abramson
 *
 * This source code is licensed under the BSD 3-Clause License found in
 * the LICENSE file in the root directory of this source tree.
 */

package com.jabramson.iperf3_network_tester_kmp.cli

import com.jabramson.iperf3_network_tester_kmp.cli.AnsiTerminal.ARROW_LEFT
import com.jabramson.iperf3_network_tester_kmp.cli.AnsiTerminal.ARROW_RIGHT
import com.jabramson.iperf3_network_tester_kmp.cli.CliRunner.TRACK_COLUMN
import com.jabramson.iperf3_network_tester_kmp.cli.CliRunner.runTest
import com.jabramson.iperf3_network_tester_kmp.model.Iperf3RunningState
import com.jabramson.iperf3_network_tester_kmp.runner.Iperf3Executor
import com.jabramson.iperf3_network_tester_kmp.utils.UnitConvertedData
import com.jabramson.iperf3_network_tester_kmp.utils.formatString
import com.jabramson.iperf3_network_tester_kmp.utils.toHumanUnit
import com.jabramson.iperf3_network_tester_kmp.viewmodel.UiInputData
import jdk.internal.org.jline.utils.Colors.s
import kotlinx.coroutines.runBlocking
import java.util.*


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
 * Scope note: also unlike BandWidthTester, this doesn't port its
 * Android-over-`adb` mode -- Android already has its own real in-app UI.
 * `-l`/`--loop` below is this mode's own repeat-loop support, not a port of
 * BandWidthTester's; see [CliArgs]'s `loopCount`/`portPool` doc comments.
 */
object CliRunner {
    private val lock = Any()
    private val tick = listOf("-", "\\", "|", "/")

    /** The only place this module appends a line break -- everywhere else
     * writes to stdout/stderr go through this (or [AnsiTerminal.clearLine]'s
     * own explicit `\r` for in-place status-line redraws), never `println()`,
     * so every newline in this output is one this code chose to emit, not
     * one contributed implicitly by `println()`'s platform line separator
     * or left over in a string this code didn't write itself. */
    private fun writeLine(text: String = "") = print(text + AnsiTerminal.NL)
    private fun write(text: String = "") = print(text)
    private fun writeErrLine(text: String = "") = System.err.print(text + AnsiTerminal.NL)

    /** @return the process exit code: iperf3's own return code on a real run,
     * 0 after printing `--help`, 1 on a command-line usage error. */
    fun run(args: Array<String>): Int {
        val resultStatistics = ResultStatistics()

            val options = CliArgs.parse(args)

        if (options.help) {
            writeLine(CliArgs.usage())
            return -1
        }
        if (options.version) {
            writeLine("iperf3KMP ${AppVersion.VERSION}")
            return -1
        }
        if (options.error != null) {
            writeErrLine("${AnsiTerminal.Color.RED.fg()}${options.error}${AnsiTerminal.reset()}")
            writeErrLine()
            writeErrLine(CliArgs.usage())
            return -1
        }

        printHeader(options.input)

        if (options.loopCount <= 1 && options.portPool == null) {
            return runTest(options.input).iperf3ReturnCode

        }
        return runLoop(options)
    }

    /**
     * Runs [CliOptions.loopCount] consecutive tests, each a full, independent
     * [runTest] call (own header/summary, like a single run would print).
     * When [CliOptions.portPool] is set, each iteration's port round-robins
     * through that range instead of reusing the single `--port` value --
     * see [CliOptions.portPool]'s doc comment for why.
     *
     * Exit code: 0 only if every iteration returned 0, else 1 -- individual
     * iterations' real iperf3 return codes aren't meaningfully combinable
     * into one number, so this reports pass/fail across the whole loop
     * instead, alongside the printed per-iteration summary line below.
     */
    private fun runLoop(options: CliOptions): Int {
        val pool = options.portPool
        var failures = 0
        val resultStatistics: MutableList<ResultStatistics> = emptyList<ResultStatistics>().toMutableList()
        for (i in 0 until options.loopCount) {
            val port = pool?.let { it.first + (i % (it.last - it.first + 1)) } ?: options.input.portNumber
            val iterationInput = options.input.copy(portNumber = port)
            writeLine()
            val timestamp = AnsiTerminal.box(AnsiTerminal.Color.GREEN, Date().toString())
            writeLine("[$timestamp] Loop ${i + 1}/${options.loopCount}  (port $port)")
            writeLine()
            val result = runTest(iterationInput)
            resultStatistics.add(result)
            if (result.iperf3ReturnCode != 0) failures++
            if (i < options.loopCount - 1) {
                write("pause between runs: 2 seconds ...")
                System.out.flush()
                try {
                    Thread.sleep(2000)
                } catch (_: InterruptedException) { /**/
                }
                write(AnsiTerminal.clearLine())
                System.out.flush()
            }
        }
        writeLine()
        writeLine("Loop(s) complete: ${options.loopCount - failures}/${options.loopCount} succeeded")
        writeLine()
        resultStatistics.forEach {
            val statColor = if (it.iperf3ReturnCode == 0) AnsiTerminal.Color.GREEN.fg() else AnsiTerminal.Color.RED.bold()
            writeLine(
                formatString("| ${AnsiTerminal.Color.BLUE.fg()}%s${AnsiTerminal.reset()} | $statColor%4.4s${AnsiTerminal.reset()}" +
                        " | RC = $statColor%-03d${AnsiTerminal.reset()} |  min = %s |  max = %s |  avg = %s |",
                    it.timeStamp.toString(),
                    if (it.iperf3ReturnCode == 0) "OK" else "FAIL",
                    it.iperf3ReturnCode,
                    colorizeStat(AnsiTerminal.Color.RED.fg(), it.minimum),
                    colorizeStat(AnsiTerminal.Color.BLUE.fg(), it.maximum),
                    colorizeStat(AnsiTerminal.Color.PURPLE.fg(), it.average))
            )
        }
        return if (failures == 0) 0 else 1
    }

    /** Minimum real time between status-line redrawing. The native bridge's
     * `onTime` ticks far faster than this (observed: roughly every 10-15ms) --
     * nobody can perceive updates faster than this anyway, and redrawing on
     * every single tick is a lot of wasted terminal I/O for a long-running test. */
    private const val STATUS_REDRAW_INTERVAL_MS = 50L

    /** Formats one message the shared parser added to [Iperf3RunningState.messages]
     * for terminal display -- a "Bridge" status line (e.g. "Test completed
     * successfully.") gets the blue-boxed treatment, anything else prints plain. */
    private fun formatMessage(message: String): String =
        if (message.contains("Bridge")) "[" + AnsiTerminal.box(AnsiTerminal.Color.BLUE, message) + "]" else message

    private fun runTest(input: UiInputData): ResultStatistics {


        var tickIndex = 0
        var statusShown = false
        var lastState: Iperf3RunningState? = null
        var lastStatusRedrawAt = 0L
        var connectionLinePrinted = false
        var lastMessageCount = 0
        var lastInterval = 0
        var lastOmittedCount = 0L
        var firstBridge = true
        val startTime = System.currentTimeMillis()
        // Once the live single-row progress redrawing starts, a message line can no
        // longer be writeLine()'d into the scroll stream the way it could during
        // setup -- scrolling the terminal invalidates the absolute-column
        // assumptions the live row's gotoColumn() redrawing depends on, and the two
        // styles (append-a-permanent-line vs. redraw-the-same-row) fight each
        // other visually. Once this flips true, a message is deferred instead and
        // shown as its own block right before the final summary -- matching what
        // BandWidthTester's own reference effectively does too, since in its
        // design connection/setup messages are always finished printing before
        // the live row's first output.
        var liveRowStarted = false
        val deferredMessages = mutableListOf<String>()
        val deferredErrorMessages = mutableListOf<String>()


        val executor = Iperf3Executor(
            updateProgress = { _, _ -> /* status line is driven by updateElapsedTime below instead */ },
            updateElapsedTime = {
                synchronized(lock) {
                    val now = System.currentTimeMillis()
                    if (now - lastStatusRedrawAt >= STATUS_REDRAW_INTERVAL_MS) {
                        liveRowStarted = true
                        tickIndex++
                        val spinner = tick[tickIndex.mod(tick.size)]
                        write("${AnsiTerminal.gotoColumn(TRACK_COLUMN)}$spinner${getStatusLabel(elapsedMillis = now - startTime, state =  lastState)}${gotoRestingColumn(input, lastState)}")
                        System.out.flush()
                        statusShown = true
                        lastStatusRedrawAt = now
                    }
                }
            },
            stdout = { state, newMessage ->
                synchronized(lock) {
                    lastState = state
                    if (statusShown) {
                        statusShown = false
                    }

                    if (!connectionLinePrinted && state.localHost.isNotEmpty() && state.remoteHost.isNotEmpty()) {
                        write(AnsiTerminal.clearLine() + gotoRestingColumn(input, lastState))
                        System.out.flush()
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
                                if (message.contains("Bridge")) {
                                    if (firstBridge) {
                                        writeLine()
                                        writeLine(formatMessage(message))
                                        firstBridge = false
                                    } else {
                                            deferredMessages.add(formatMessage(message))
                                    }
                                } else if (firstBridge) {
                                    writeLine()
                                    writeLine(formatMessage(message))
                                } else if (liveRowStarted) {
                                    deferredMessages.add(formatMessage(message))
                                }
                            }
                        }
                        lastMessageCount = state.messages.size
                    } else if (state.formattedOutputLine.isNotBlank()) {
                        // intervalNumber is always -1 for an omitted interval (see the
                        // tick[] crash fix a few commits back), so it never satisfies
                        // "> lastInterval" on its own -- that silently dropped every
                        // "Skipping"/omitted line. totalOmitted is the shared parser's
                        // own running count, bumped once per real omitted interval, so
                        // it's a safe second way to detect "this is a new one to show".
                        val isNewOmitted = state.omitted && state.totalOmitted > lastOmittedCount
                        if (state.intervalNumber > lastInterval || isNewOmitted || state.stalled) {
                            liveRowStarted = true
                            if (input.isDebugging) {
                                writeLine("${AnsiTerminal.Color.GREY.fg()}RAW: ${state.rawOutputLine.trim()}${AnsiTerminal.reset()}")
                            }
                            printIntervalLine(input, state, elapsedMillis =  (System.currentTimeMillis() - startTime))
                            lastInterval = state.intervalNumber.toInt()
                            lastOmittedCount = state.totalOmitted
                        }
                    }
                }
            },
            stderr = { state, message ->
                synchronized(lock) {
                    lastState = state
                    deferredErrorMessages.add(message)
                }
            },
            onTestComplete = { }
        )

        val returnCode = runBlocking { executor.startTest(input) }

        synchronized(lock) {
            write("${AnsiTerminal.gotoColumn(TRACK_COLUMN)} ${AnsiTerminal.gotoColumn(1)}")
            writeLine()
            if (deferredMessages.isNotEmpty()) {
                writeLine(AnsiTerminal.horizontalRule(80))
                deferredMessages.forEach { writeLine(it) }
            }
            writeLine(AnsiTerminal.horizontalRule(80))
            printSummary(lastState, returnCode)
            if (statusShown) {
                writeLine()
                writeLine(AnsiTerminal.horizontalRule(80))
                statusShown = false
            }
            if (deferredErrorMessages.isNotEmpty()) {
                writeLine(AnsiTerminal.horizontalRule(80))
                deferredErrorMessages.forEach { writeLine("${AnsiTerminal.Color.RED.fg()}${it.trim()}${AnsiTerminal.reset()}") }
            }

        }
        if (lastState != null) {
            val result = ResultStatistics(
                iperf3ReturnCode = returnCode,
                average = lastState.currentAvg,
                minimum = lastState.currentMin,
                maximum = lastState.currentMax,
                median = lastState.currentMedian,
                standardDeviation = lastState.currentStandardDeviation
            )
            return result
        }
        return ResultStatistics()
    }

    /** The exact argv [Iperf3Executor.startTest] builds internally, rebuilt
     * here only for display -- this doesn't invoke anything, so a flag
     * actually being blank (e.g. `--forceflush` off) is simply left out
     * rather than shown as a stray empty `''` the way the real array
     * (which does include empty-string placeholders) would render. */
    private fun displayArgs(input: UiInputData): List<String> = buildList {
        add("iperf3")
        add("--client")
        add(input.hostName)
        add("--port")
        add(input.portNumber.toString())
        if (input.isReverse) add("--reverse")
        if (input.isForceFlush) add("--forceflush")
        add("--parallel")
        add(input.parallelStreams.toString())
        add("--connect-timeout")
        add((if (input.timeout > 0) input.timeout else 3000L).toString())
        add("--time")
        add(input.durationSecs.toString())
        add("--omit")
        add(input.skip.toString())
    }

    /** How many quoted argv tokens to print per line in the header -- enough
     * that a typical token ("--connect-timeout", a hostname) still keeps
     * each line comfortably under 80 columns, the one terminal width that's
     * safe to assume with no way to query the caller's actual one. */
    private const val ARGS_PER_LINE = 5

    private fun printHeader(input: UiInputData) {
        val timestamp = AnsiTerminal.box(AnsiTerminal.Color.GREEN, Date().toString())
        writeLine("iperf3KMP version ${AppVersion.VERSION}\n\n[$timestamp] Executing Command-line: ")
        // Printed in short, fixed-size chunks rather than one long logical line --
        // a single line here wraps wherever the caller's terminal happens to be
        // narrow, splitting a colored escape sequence (or even a token itself)
        // wherever the terminal's own column count cuts it off, which this process
        // has no way to query or control. Chunking to a safe width sidesteps
        // relying on the terminal's own auto-wrap at all.
        displayArgs(input).chunked(ARGS_PER_LINE).forEach { chunk ->
            val line = chunk.joinToString(" ") { "'${AnsiTerminal.Color.PURPLE.fg()}$it${AnsiTerminal.reset()}'" }
            writeLine("  $line")
        }
    }

    /** Printed once, the first time [Iperf3RunningState.localHost]/[Iperf3RunningState.remoteHost]
     * are populated -- built from those structured fields directly (not by
     * re-parsing the equivalent plain-text message the shared output parser
     * also adds to [Iperf3RunningState.messages], which [runTest] skips to
     * avoid printing the same information twice). */
    private fun printConnectionLine(state: Iperf3RunningState) {
        writeLine(
            AnsiTerminal.gotoColumn(1) + AnsiTerminal.clearToEol() +
            "  Local Host/IP: ${AnsiTerminal.Color.RED.fg()}${state.localHost.trim()}${AnsiTerminal.reset()} " +
                "remote Host/IP: ${AnsiTerminal.Color.GREEN.fg()}${state.remoteHost.trim()}${AnsiTerminal.reset()} " +
                "Remote Port: ${AnsiTerminal.Color.GREEN.fg()}${state.remotePort}${AnsiTerminal.reset()}"
                    + AnsiTerminal.clearToEol()
        )
    }

    /** Column the tick/bracket block always starts at, regardless of the
     * preceding label's width -- fixed via [AnsiTerminal.gotoColumn] rather
     * than left-padding with spaces, the same technique
     * `MonitorIPerf3Output`'s own `leftColumnMarker`/`columnSet` jumps use,
     * so the track lines up the same whether the label is "Running "or
     * "Skipping" or the live line has no label yet at all. */
    private const val TRACK_COLUMN = 34

    /** Widest of "Skipping" "STALLED" "Running" "Starting".
     * Every label is padded out to this with [statusLabel].
     * A shorter label drawn over a longer one (both via absolute
     * [AnsiTerminal.gotoColumn] positioning.
     * This avoids the need for a clear-to-EOL.
     * */
    private const val LABEL_WIDTH = 12

    private fun statusLabel(omitted: Boolean, stalled: Boolean): String =
        (if (omitted) "  Skipping" else if (stalled) "  STALLED" else "  Running").padEnd(LABEL_WIDTH)

    private fun getStatusLabel(elapsedMillis: Long, state: Iperf3RunningState?): String {
        val sb = StringBuilder()
        val label =
            if (state == null || (!state.omitted && !state.stalled && state.intervalNumber < 0)) "  Starting".padEnd(LABEL_WIDTH)
            else statusLabel(omitted = state.omitted, stalled = state.stalled)
        sb.append(AnsiTerminal.gotoColumn(1))
        val color =
            if (state == null) AnsiTerminal.Color.PURPLE.fg()
            else if (state.stalled) AnsiTerminal.Color.RED.reverse()
            else if (state.omitted) AnsiTerminal.Color.YELLOW.fg()
            else AnsiTerminal.Color.GREY.bold()
        sb.append("$color$label${AnsiTerminal.reset()}")
        sb.append("[")
        sb.append(AnsiTerminal.Color.BLUE.fg())
        sb.append(formatString("%-5.2f", elapsedMillis / 1000.toDouble()))
        sb.append(AnsiTerminal.reset())
        sb.append(" secs")
        sb.append("]")
        return sb.toString()
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
    private fun printIntervalLine(input: UiInputData, state: Iperf3RunningState, elapsedMillis: Long) {
        val trackColor = if (state.omitted || state.stalled) AnsiTerminal.Color.RED else AnsiTerminal.Color.GREEN
        write(newProgressTrack(input, elapsedMillis, trackColor, state))
        System.out.flush()
    }



    /**
     * Fixed columns for the stat block below, the same `gotoColumn`-over-
     * space-padding technique [TRACK_COLUMN] uses -- padding with a literal
     * string of spaces only lines up by coincidence (it assumes every label
     * renders at exactly the width its character count suggests, which
     * isn't guaranteed once escape codes and variable terminal fonts are
     * involved); an absolute column jump doesn't depend on that. */
    private const val STAT_LABEL_COLUMN = 18
    private const val STAT_VALUE_COLUMN = 30

    private fun formatStat(data: UnitConvertedData): String =
        if (data.value <= 0.0) "  N/A" else "%,5.2f".format(data.value)

    /**
     * Prints a formatted statistical row with a labeled value and its unit, applying color customizations.
     *
     * @param label The text label describing the statistic.
     * @param labelColor The color to apply to the label text.
     * @param valueColor The color to apply to the value text.
     * @param data The statistical data which includes the value and its unit.
     */
    private fun printStatRow(label: String, labelColor: AnsiTerminal.Color, valueColor: String, data: UnitConvertedData) {
        writeLine(
            AnsiTerminal.gotoColumn(STAT_LABEL_COLUMN) + "[" + AnsiTerminal.box(labelColor, label) + "]" +
                    AnsiTerminal.gotoColumn(STAT_VALUE_COLUMN) +
                    colorizeStat(valueColor, data)
        )
    }

    private fun colorizeStat(valueColor: String, data: UnitConvertedData): String {
        return "$valueColor${formatStat(data)}${AnsiTerminal.reset()}  " +
                "${AnsiTerminal.bold()}${data.unit}${AnsiTerminal.reset()}"
    }

    /**
     * Prints a formatted summary of the iperf3 test execution statistics and results,
     * followed by the return code of the operation.
     *
     * @param state The current running state of the iperf3 test, containing interval statistics
     * and other execution details. If null, no statistics will be displayed.
     * @param returnCode The return code representing the outcome of the iperf3 test execution.
     * A value of 0 typically indicates success, while non-zero values indicate errors.
     */
    private fun printSummary(state: Iperf3RunningState?, returnCode: Int) {
        writeLine()
        if (state != null && state.intervalNumber >= 0) {
            //writeLine(getSampleSize(state))
            printStatRow("Avg", AnsiTerminal.Color.GREY, "", state.currentAvg)
            printStatRow("Min", AnsiTerminal.Color.RED, AnsiTerminal.Color.RED.fg(), state.currentMin)
            printStatRow("Max", AnsiTerminal.Color.BLUE, AnsiTerminal.Color.BLUE.fg(), state.currentMax)
            printStatRow("Median", AnsiTerminal.Color.PURPLE, AnsiTerminal.Color.PURPLE.fg(), state.currentMedian)
            printStatRow("StdDev", AnsiTerminal.Color.GREY, "", state.currentStandardDeviation)
        }
        writeLine(AnsiTerminal.horizontalRule(80))
        val rcColor = if (returnCode == 0) AnsiTerminal.Color.GREEN else AnsiTerminal.Color.RED
        val avgSuffix = state?.currentAvg?.takeIf { it.value > 0.0 }?.let { " [Avg=${formatStat(it)} ${it.unit}]" } ?: ""
        writeLine("Return Code: ${rcColor.fg()}${"%03d".format(returnCode)}${AnsiTerminal.reset()}$avgSuffix")
    }

    private const val LEFT_COLUMN_MARKER: Int = 38
    private var RIGHT_COLUMN_MARKER: Int = 80

    /**
     * Calculates the resting column position in the terminal based on the provided input data and the current interval state,
     * and returns the corresponding ANSI escape sequence to move the cursor to that position.
     *
     * @param input An instance of [UiInputData] containing user-defined parameters, such as the direction of the test (`isReverse`).
     * @return A [String] containing the ANSI escape sequence to move the cursor to the computed resting column.
     */
    private fun gotoRestingColumn(input: UiInputData?, lastState: Iperf3RunningState?): String {
        val sb = StringBuilder()
        val left = LEFT_COLUMN_MARKER
        val right = left + (input?.durationSecs ?: 0)
        if (lastState == null || lastState.stalled || lastState.omitted) {
            val restingColumn = if (input?.isReverse ?: false) right else left - 1
            sb.append(AnsiTerminal.gotoColumn(restingColumn + 1))
        } else {
            val intVal = if (lastState.intervalNumber.toInt() >= 0) lastState.intervalNumber.toInt() else  0
            val restingColumn = if (input?.isReverse ?: false) right - intVal - 2 else left + intVal + 1
            sb.append(AnsiTerminal.gotoColumn(restingColumn + 1))
        }
        return sb.toString()
    }

    /**
     * Generates a formatted progress track string to be displayed in the terminal during
     * an iperf3 network performance test. The progress track includes visual indicators
     * for the test's current state, elapsed time, and relevant output data.
     *
     * @param input The user-provided configuration parameters encapsulated in a [UiInputData] object.
     * @param elapsedMilliSeconds The elapsed time in milliseconds since the start of the test.
     * @param color The [AnsiTerminal.Color] used to style the progress track's visual components.
     * @param state The current execution state of the iperf3 test, represented as [Iperf3RunningState].
     * @return A string containing the ANSI-formatted progress track ready for terminal output.
     */
    private fun newProgressTrack(input: UiInputData,
                                 elapsedMilliSeconds: Long,
                                 color: AnsiTerminal.Color,
                                 state: Iperf3RunningState) : String
    {
        if (input.isDebugging) return ""
        // intervalNumber is always -1 for an omitted interval, so "<= 0" alone
        // silently dropped every omitted line too, not just an uninitialized
        // state -- only bail out when this is neither a real interval nor an
        // omitted one.
        if (!state.omitted && state.intervalNumber < 0) return ""


        // +1 is a one-column buffer (matching IPerf3Monitor.java's own
        // `rightColumnMarker = leftColumnMarker + conn.getTimePeriod() + 1`), not
        // +skip -- adding the omit window here instead inflates the right edge by
        // the omit window's size (invisible at -O 1, since 1 happens to equal the
        // intended buffer, but very visible at e.g. -O 5).
        RIGHT_COLUMN_MARKER = LEFT_COLUMN_MARKER + input.durationSecs + 1
        val sb = StringBuilder()
        var leftBracket = "["
        var rightBracket = "]"

        var column1: Int = LEFT_COLUMN_MARKER + state.intervalNumber.toInt()
        var arrow = ARROW_RIGHT
        if (input.isReverse) {
            leftBracket = "("
            column1 = RIGHT_COLUMN_MARKER - state.intervalNumber.toInt()
            arrow = ARROW_LEFT

        } else {
            rightBracket = ")"
        }
        sb.append(AnsiTerminal.reset())
        sb.append(AnsiTerminal.gotoColumn(1))
        sb.append(getStatusLabel(elapsedMillis = elapsedMilliSeconds, state =  state))
        // No +1 here -- the reference draws both brackets directly at the markers,
        // the same coordinate system column1 (the arrow below) uses. Offsetting
        // just the brackets by +1 put them one column off from where the arrow's
        // own math assumes the track edges are.
        if (!input.isReverse) {
            sb.append(AnsiTerminal.gotoColumn(LEFT_COLUMN_MARKER - 1))
            sb.append(leftBracket)
            sb.append(AnsiTerminal.gotoColumn(RIGHT_COLUMN_MARKER - 1))
            sb.append(rightBracket)
        } else {
            sb.append(AnsiTerminal.gotoColumn(LEFT_COLUMN_MARKER + 1))
            sb.append(leftBracket)
            sb.append(AnsiTerminal.gotoColumn(RIGHT_COLUMN_MARKER+1))
            sb.append(rightBracket)
        }


        if (!state.omitted) {
            val c = if (!state.stalled) color else AnsiTerminal.Color.RED
            // Fill every cell already passed, not just the one next to the arrow --
            // explicit each redraw (not relying on earlier draws' cells persisting
            // on screen untouched), so the bar reads as "[-->   ]" growing to
            // "[--->  ]" as the test progresses, regardless of terminal quirks.
            if (input.isReverse) {
                sb.append(AnsiTerminal.gotoColumn(column1-1))
                sb.append(c.fg())
                sb.append(AnsiTerminal.bold())
                sb.append(arrow)
                sb.append(AnsiTerminal.reset())
                sb.append(c.reverse())
                sb.append(" ")
                sb.append(AnsiTerminal.reset())
            } else {
                sb.append(AnsiTerminal.gotoColumn(column1))
                sb.append(c.reverse())
                sb.append(" ")
                sb.append(AnsiTerminal.reset())
                sb.append(AnsiTerminal.gotoColumn(column1+1))
                sb.append(c.fg())
                sb.append(AnsiTerminal.bold())
                sb.append(arrow)
                sb.append(AnsiTerminal.reset())
            }
        }
        sb.append(AnsiTerminal.gotoColumn(RIGHT_COLUMN_MARKER + 10 + 1))
        sb.append("[" + AnsiTerminal.box(AnsiTerminal.Color.CYAN, state.formattedOutputLine.trim()) + "]")
        sb.append(getStatusLabel(elapsedMillis = elapsedMilliSeconds, state =  state))
        return sb.toString()
    }
}
