/*
 * Copyright (c) 2026, Jerold Abramson
 *
 * This source code is licensed under the BSD 3-Clause License found in
 * the LICENSE file in the root directory of this source tree.
 */

package com.jabramson.iperf3_network_tester_kmp.cli

import com.jabramson.iperf3_network_tester_kmp.cli.AnsiTerminal.ARROW_LEFT
import com.jabramson.iperf3_network_tester_kmp.cli.AnsiTerminal.ARROW_RIGHT
import com.jabramson.iperf3_network_tester_kmp.model.Iperf3RunningState
import com.jabramson.iperf3_network_tester_kmp.runner.Iperf3Executor
import com.jabramson.iperf3_network_tester_kmp.utils.UnitConvertedData
import com.jabramson.iperf3_network_tester_kmp.utils.formatString
import com.jabramson.iperf3_network_tester_kmp.viewmodel.UiInputData
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

    /** Set once [run] returns by any path. The shutdown hook only prints its
     * "interrupted" notice when this is still false, i.e. on Ctrl-C/SIGTERM. */
    @Volatile
    private var cleanExit = false

    /** Set (under [lock]) by the shutdown hook so the live status-line callbacks,
     * which keep running on other threads until the JVM halts, stop drawing
     * over the hook's final message. */
    private var shuttingDown = false

    /** Column the tick/bracket block always starts at, regardless of the
     * preceding label's width -- fixed via [AnsiTerminal.gotoColumn] rather
     * than left-padding with spaces, the same technique
     * `MonitorIPerf3Output`'s own `leftColumnMarker`/`columnSet` jumps use,
     * so the track lines up the same whether the label is "Running "or
     * "Skipping" or the live line has no label yet at all. */
    private const val TRACK_COLUMN = 30

    /** Widest of "Skipping" "STALLED" "Running" "Starting".
     * Every label is padded out to this with [statusLabel].
     * A shorter label drawn over a longer one (both via absolute
     * [AnsiTerminal.gotoColumn] positioning.
     * This avoids the need for a clear-to-EOL.
     * */
    private const val LABEL_WIDTH = 12

    /**
     * The absolute value of the spacing between the percentage output
     * and the tick/bracket.
     */
    private const val PERCENTAGE_COLUMN_OFFSET = 4

    /** How many quoted argv tokens to print per line in the header -- enough
     * that a typical token ("--connect-timeout", a hostname) still keeps
     * each line comfortably under 80 columns, the one terminal width that's
     * safe to assume with no way to query the caller's actual one. */
    private const val ARGS_PER_LINE = 5

    /** Minimum real time between status-line redrawing. The native bridge's
     * `onTime` ticks far faster than this (observed: roughly every 10-15ms) --
     * nobody can perceive updates faster than this anyway, and redrawing on
     * every single tick is a lot of wasted terminal I/O for a long-running test. */
    private const val STATUS_REDRAW_INTERVAL_MS = 50L

    /**
     * Fixed columns for printStatRow(), the same `gotoColumn`-over-
     * space-padding technique [TRACK_COLUMN] uses -- padding with a literal
     * string of spaces only lines up by coincidence (it assumes every label
     * renders at exactly the width its character count suggests, which
     * isn't guaranteed once escape codes and variable terminal fonts are
     * involved); an absolute column jump doesn't depend on that. */
    private const val STAT_LABEL_COLUMN = 18
    private const val STAT_VALUE_COLUMN = 30

    /**
     * The left column marker for the progress bar is fixed, but the
     * right column marker is dynamic based on the duration of the test
     */
    private const val LEFT_COLUMN_MARKER: Int = TRACK_COLUMN + 2
    private const val RIGHT_OUTPUT_LINE_SPACING = 2
    private var rightColumnMarker: Int = 69
    private var formattedOutputLineMarker: Int = LEFT_COLUMN_MARKER + RIGHT_OUTPUT_LINE_SPACING + 1

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
     * 0 after printing `--help`/`--version`, 1 on a command-line usage error. */
    fun run(args: Array<String>): Int {
        Runtime.getRuntime().addShutdownHook(Thread {
            if (!cleanExit) {
                synchronized(lock) {
                    shuttingDown = true
                    write(AnsiTerminal.clearLine() + AnsiTerminal.reset())
                    writeLine()
                    writeLine("${AnsiTerminal.Color.RED.bold()}Shutdown hook called${AnsiTerminal.reset()}! Exiting.")
                    writeLine()
                    System.out.flush()
                }
            }
        })
        try {
            return runCli(args)
        } finally {
            cleanExit = true
        }
    }

    private fun runCli(args: Array<String>): Int {
        AnsiTerminal.setColumnWidth()
        val terminalWidth = AnsiTerminal.width
        if (terminalWidth < 80) {
            writeLine("${AnsiTerminal.Color.RED.fg()}Terminal is too narrow${AnsiTerminal.reset()}!  The minimum required width is 80 columns.")
            return -1
        }

        val options = CliArgs.parse(args)

        if (options.help) {
            writeLine(CliArgs.usage())
            return 0
        }
        if (options.version) {
            writeLine("iperf3KMP ${AppVersion.VERSION}")
            return 0
        }
        if (options.error != null) {
            writeErrLine("${AnsiTerminal.Color.RED.fg()}${options.error}${AnsiTerminal.reset()}")
            writeErrLine()
            writeErrLine(CliArgs.usage())
            return 1
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
     * into one number, so these reports pass/fail across the whole loop
     * instead, alongside the printed per-iteration summary line below.
     */
    private fun runLoop(options: CliOptions): Int {
        val pool = options.portPool
        var failures = 0

        writeLine(AnsiTerminal.horizontalRule())
        val resultStatistics: MutableList<ResultStatistics> = emptyList<ResultStatistics>().toMutableList()
        for (i in 0 until options.loopCount) {
            val port = pool?.let { it.first + (i % (it.last - it.first + 1)) } ?: options.input.portNumber
            val iterationInput = options.input.copy(portNumber = port)
            writeLine()
            val startedAt = Date()
            val timestamp = AnsiTerminal.box(AnsiTerminal.Color.GREEN, startedAt.toString())
            writeLine("[$timestamp] Loop ${i + 1}/${options.loopCount}  (port $port)")
            writeLine()
            val result = runTest(iterationInput, startedAt)
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
            val elapsedTestTimeMs = it.endTimeStamp.time - it.startTimeStamp.time
            val statColor = if (it.iperf3ReturnCode == 0) AnsiTerminal.Color.GREEN.fg() else AnsiTerminal.Color.RED.bold()
            writeLine(
                // Thousands grouping via Kotlin's own format, passed in as %s: the shared
                // native formatString (native_format.h) only accepts C printf flags
                // (- + space # 0), so a Java-style "%,d" is a malformed specifier there.
                formatString("| Test Time: ${AnsiTerminal.Color.BLUE.fg()}%10.10s${AnsiTerminal.reset()} ms | $statColor%4.4s${AnsiTerminal.reset()} " +
                             "| RC = $statColor%-03d${AnsiTerminal.reset()} |  min = %s |  max = %s |  avg = %s |",
                    "%,d".format(elapsedTestTimeMs),
                    if (it.iperf3ReturnCode == 0) "OK" else "FAIL",
                    it.iperf3ReturnCode,
                    colorizeStat(AnsiTerminal.Color.RED.fg(), it.minimum),
                    colorizeStat(AnsiTerminal.Color.BLUE.fg(), it.maximum),
                    colorizeStat(AnsiTerminal.Color.GREEN.fg(), it.average))
            )
        }
        return if (failures == 0) 0 else 1
    }


    /** Formats one message the shared parser added to [Iperf3RunningState.messages]
     * for terminal display -- a "Bridge" status line (e.g. "Test completed
     * successfully.") gets the blue-boxed treatment, anything else prints plain. */
    private fun formatMessage(message: String): String =
        if (message.contains("Bridge")) "[" + AnsiTerminal.box(AnsiTerminal.Color.BLUE, message) + "]" else message

    /**
     * Main runtime loop for command-line execution
     * @param startedAt when this run began -- [runLoop] passes the same time
     * its loop header shows, so the final summary table matches it. */
    private fun runTest(input: UiInputData, startedAt: Date = Date()): ResultStatistics {


        var tickIndex = 0
        var lastState: Iperf3RunningState? = null
        var lastStatusRedrawAt = 0L
        var connectionLinePrinted = false
        var lastMessageCount = 0
        // -1, not 0: interval 0 ("0.00-1.00") is a real interval and must pass
        // the "> lastInterval" check below (an omit period already leaves it at -1).
        var lastInterval = -1
        var lastOmittedCount = 0L
        // Where this run's progress-track arrow is and which lap it's on, so each
        // update only draws the new block and moves the arrow (see newProgressTrack).
        val track = TrackState()
        var firstBridge = true
        val startTime = System.currentTimeMillis()
        var percentageComplete: Long = 0.toLong()
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
        val terminalWidth = AnsiTerminal.width
        if (terminalWidth < 80) {
            writeLine("${AnsiTerminal.Color.RED.fg()}Terminal is too narrow${AnsiTerminal.reset()} because it was too narrow !!")
            return ResultStatistics(startTimeStamp = startedAt, endTimeStamp = Date(), iperf3ReturnCode = -55)
        }
        // Based on an 80-column display. 68, not 65: leaves room for a >= 100 s
        // elapsed time and a 3-digit rate (each one column wider) without the
        // progress line wrapping, which breaks its in-place redraw.
        val maxTrackSize = terminalWidth - 68 // >= 12: narrower than 80 columns is refused earlier
        val maximumProgressBarSize = input.durationSecs.coerceAtMost(maxTrackSize).toLong()
        val lastBridgeMessages = mutableListOf<String>()
        val executor = Iperf3Executor(
            updateProgress = { _, percentComplete ->
                percentageComplete = percentComplete
            },
            updateElapsedTime = {
                synchronized(lock) {
                    if (shuttingDown) return@synchronized
                    val now = System.currentTimeMillis()
                    if (now - lastStatusRedrawAt >= STATUS_REDRAW_INTERVAL_MS) {
                        liveRowStarted = true
                        tickIndex++
                        val spinner = tick[tickIndex.mod(tick.size)]
                        write("${AnsiTerminal.gotoColumn(TRACK_COLUMN)}$spinner" +
                                AnsiTerminal.Color.YELLOW.fg() +
                                AnsiTerminal.gotoColumn(TRACK_COLUMN - PERCENTAGE_COLUMN_OFFSET) +
                                "$percentageComplete%" +
                                AnsiTerminal.reset() +
                                getStatusLabel(elapsedMillis = now - startTime, state =  lastState) +
                                gotoRestingColumn(
                                    lastState =  lastState,
                                    lastState?.intervalNumber ?: 0,
                                    maxSize = maximumProgressBarSize,
                                    isReverse = input.isReverse
                                )
                        )
                        System.out.flush()
                        lastStatusRedrawAt = now
                    }
                }
            },
            stdout = { state, newMessage ->
                synchronized(lock) {
                    if (shuttingDown) return@synchronized
                    lastState = state

                    if (!connectionLinePrinted && state.localHost.isNotEmpty() && state.remoteHost.isNotEmpty()) {
                        printConnectionLine(state)
                        write(
                            AnsiTerminal.clearLine() +
                                    gotoRestingColumn(
                                        lastState = lastState,
                                        state.intervalNumber,
                                        maxSize = maximumProgressBarSize,
                                        isReverse = input.isReverse
                                    )
                        )
                        System.out.flush()
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
                                if (message.lowercase().contains("bridge")) {
                                    if (firstBridge) {
                                        write(AnsiTerminal.clearLine() + AnsiTerminal.gotoColumn(1))
                                        writeLine(formatMessage(message))
                                        System.out.flush()
                                        firstBridge = false
                                    } else {
                                        lastBridgeMessages.add(message)
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
                            printIntervalLine(input, maximumProgressBarSize, state = state, track = track,
                                elapsedMillis =  (System.currentTimeMillis() - startTime))
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
            if (lastBridgeMessages.isNotEmpty()) {
                lastBridgeMessages.forEach { writeLine(formatMessage(it)) }
            }
            if (deferredMessages.isNotEmpty()) {
                writeLine(AnsiTerminal.horizontalRule())
                writeLine("[${AnsiTerminal.Color.PURPLE.reverse()}iPerf3 Messages${AnsiTerminal.reset()}]")
                deferredMessages.forEach {
                        writeLine("\t$it")
                }
            }
            if (deferredErrorMessages.isNotEmpty()) {
                writeLine(AnsiTerminal.horizontalRule())
                writeLine("[${AnsiTerminal.Color.YELLOW.reverse()}iPerf3 Errors${AnsiTerminal.reset()}]")
                deferredErrorMessages.forEach { writeLine("\t${AnsiTerminal.Color.RED.fg()}${it.trim()}${AnsiTerminal.reset()}") }
            }
            writeLine(AnsiTerminal.horizontalRule())
            printSummary(lastState, returnCode)
        }
        if (lastState != null && returnCode == 0) {
            val result = ResultStatistics(
                startTimeStamp = startedAt,
                endTimeStamp = Date(),
                iperf3ReturnCode = returnCode,
                average = lastState.currentAvg,
                minimum = lastState.currentMin,
                maximum = lastState.currentMax,
                median = lastState.currentMedian,
                standardDeviation = lastState.currentStandardDeviation,
            )
            return result
        }
        return ResultStatistics(startTimeStamp = startedAt, endTimeStamp = Date())
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



    private fun statusLabel(omitted: Boolean, stalled: Boolean): String =
        (if (omitted) "  Skipping" else if (stalled) "  STALLED" else "  Running").padEnd(LABEL_WIDTH)

    private fun getStatusLabel(elapsedMillis: Long, state: Iperf3RunningState?): String {
        val sb = StringBuilder()
        val starting = (state == null || (!state.omitted && !state.stalled && state.intervalNumber < 0))
        val label = if (starting) "  Starting".padEnd(LABEL_WIDTH)
            else statusLabel(omitted = state.omitted, stalled = state.stalled)
        sb.append(AnsiTerminal.gotoColumn(1))
        val color =
            if (starting) AnsiTerminal.Color.PURPLE.fg()
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
    private fun printIntervalLine(input: UiInputData, maxSize: Long, state: Iperf3RunningState,
                                  track: TrackState, elapsedMillis: Long) {
        write(
            newProgressTrack(input, maxSize = maxSize, elapsedMilliSeconds = elapsedMillis,
                track = track, state = state)
                    + gotoRestingColumn(state, state.intervalNumber, maxSize, input.isReverse)
        )
        System.out.flush()
    }




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
            AnsiTerminal.gotoColumn(STAT_LABEL_COLUMN) +
            "[" + AnsiTerminal.box(labelColor, label) + "]" +
            AnsiTerminal.gotoColumn(STAT_VALUE_COLUMN) +
            colorizeStat(valueColor, data)
        )
    }

    private fun colorizeStat(valueColor: String, data: UnitConvertedData): String {
        return valueColor + data.getValue() + AnsiTerminal.reset() + " " +
        AnsiTerminal.bold() + data.getUnit() + AnsiTerminal.reset()
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
        // The stat block (and its own closing rule) only when there's something
        // to show -- a run that failed before any interval goes straight to the
        // return code instead of printing an empty section between two rules.
        if (state != null && state.intervalNumber >= 0) {
            writeLine()
            //writeLine(getSampleSize(state))
            printStatRow("Avg", AnsiTerminal.Color.GREEN, AnsiTerminal.Color.GREEN.fg(), state.currentAvg)
            printStatRow("Min", AnsiTerminal.Color.RED, AnsiTerminal.Color.RED.fg(), state.currentMin)
            printStatRow("Max", AnsiTerminal.Color.BLUE, AnsiTerminal.Color.BLUE.fg(), state.currentMax)
            printStatRow("Median", AnsiTerminal.Color.PURPLE, AnsiTerminal.Color.PURPLE.fg(), state.currentMedian)
            printStatRow("StdDev", AnsiTerminal.Color.GREY, "", state.currentStandardDeviation)
            writeLine(AnsiTerminal.horizontalRule())
        }
        val rcColor = if (returnCode == 0) AnsiTerminal.Color.GREEN else AnsiTerminal.Color.RED
        val avgSuffix = state?.currentAvg?.takeIf { it.value > 0.0 }?.let { " [Avg: ${colorizeStat(AnsiTerminal.Color.GREEN.fg(), it)}]" } ?: ""
        writeLine("Return Code: ${rcColor.fg()}${"%03d".format(returnCode)}${AnsiTerminal.reset()}$avgSuffix")
    }


    /**
     * Calculates the resting column position in the terminal based on the provided input data and the current interval state,
     * and returns the corresponding ANSI escape sequence to move the cursor to that position.
     * @param lastState: IperfRunningState
     *
     * @return A [String] containing the ANSI escape sequence to move the cursor to the computed resting column.
     */
    private fun gotoRestingColumn(lastState: Iperf3RunningState?, currentInterval: Long, maxSize: Long, isReverse: Boolean): String {
        val cells = maxSize.toInt()
        val rightBracket = LEFT_COLUMN_MARKER + cells + 1
        // Rest just past the arrow (see newProgressTrack), or at the start of the
        // track while there's no arrow yet (omitted/stalled/not started).
        val restingColumn = if (lastState == null || lastState.stalled || lastState.omitted || currentInterval <= 0) {
            if (isReverse) rightBracket - 1 else LEFT_COLUMN_MARKER + 1
        } else {
            val arrowCell = ((currentInterval - 1) % cells).toInt() + 1
            if (isReverse) (trackCellColumn(arrowCell, cells, true) - 1).coerceAtLeast(LEFT_COLUMN_MARKER + 1)
            else (trackCellColumn(arrowCell, cells, false) + 1).coerceAtMost(rightBracket)
        }
        return AnsiTerminal.gotoColumn(restingColumn)
    }

    /** Per-run progress-track state: the arrow's current column and the lap being drawn. */
    private class TrackState(var arrowColumn: Int? = null, var lap: Long = 0)

    /** Terminal column of track cell [i] (0-based from the starting end). */
    private fun trackCellColumn(i: Int, cells: Int, isReverse: Boolean): Int =
        if (isReverse) LEFT_COLUMN_MARKER + cells - i else LEFT_COLUMN_MARKER + 1 + i

    /**
     * Generates a formatted progress track string to be displayed in the terminal during
     * an iperf3 network performance test. The progress track includes visual indicators
     * for the test's current state, elapsed time, and relevant output data.
     *
     * Since the duration of a run is an argument that can be passed at the command-line, it is very
     * likely that the progress bar will be too wide for a terminal window.
     *
     * We need two options for the size of the track bar, which then result in the floating track
     * restarting at the beginning.
     *
     * since we now have a percentage output, that serves as the real gauge on test execution.
     *
     * The LEFT_COLUMN_MARKER is fixed (currently at 38). The rightColumnMarker is dynamically determined
     * with this formula below:
     *     rightColumnMarker = LEFT_COLUMN_MARKER + input.durationSecs + 1
     *
     * We need to take into account terminal width (which we now have access to, or fallback to 80).
     *
     * However, we also have the output from iperf3 set at
     *
     * @param input The user-provided configuration parameters encapsulated in a [UiInputData] object.
     * @param elapsedMilliSeconds The elapsed time in milliseconds since the start of the test.
     * @param track This run's [TrackState]: where the arrow is and which lap is being drawn.
     * @param state The current execution state of the iperf3 test, represented as [Iperf3RunningState].
     * @return A string containing the ANSI-formatted progress track ready for terminal output.
     */
    private fun newProgressTrack(input: UiInputData,
                                 elapsedMilliSeconds: Long,
                                 maxSize: Long,
                                 track: TrackState,
                                 state: Iperf3RunningState) : String
    {

        if (input.isDebugging) return ""

        // intervalNumber is always -1 for an omitted interval, so "<= 0" alone
        // silently dropped every omitted line too, not just an uninitialized
        // state -- only bail out when this is neither a real interval nor an
        // omitted one.
        if (!state.omitted && state.intervalNumber < 0) return ""

        // The track is `cells` columns between two brackets, the same width in
        // both directions; interval k fills cell (k-1) % cells. Each update draws
        // only what changed -- the new interval's block, the arrow in the next
        // cell, and a blank over the previous arrow when the new block didn't
        // land on it (an interval was skipped) -- so cells for intervals iperf3
        // never reported stay blank. When the test runs past the last cell, the
        // track is blanked once and the next lap is drawn in purple.
        val cells = maxSize.toInt()
        rightColumnMarker = LEFT_COLUMN_MARKER + cells + 1
        formattedOutputLineMarker = rightColumnMarker + RIGHT_OUTPUT_LINE_SPACING
        val (leftBracket, rightBracket) = if (input.isReverse) "(" to "]" else "[" to ")"

        val sb = StringBuilder()
        sb.append(AnsiTerminal.reset())
        sb.append(AnsiTerminal.gotoColumn(1))
        sb.append(getStatusLabel(elapsedMillis = elapsedMilliSeconds, state =  state))
        sb.append(AnsiTerminal.gotoColumn(LEFT_COLUMN_MARKER))
        sb.append(leftBracket)
        sb.append(AnsiTerminal.gotoColumn(rightColumnMarker))
        sb.append(rightBracket)

        val current = state.intervalNumber
        if (!state.omitted && current > 0) {
            val cell = ((current - 1) % cells).toInt()
            val lap = (current - 1) / cells
            if (lap != track.lap) {
                sb.append(AnsiTerminal.gotoColumn(LEFT_COLUMN_MARKER + 1)).append(" ".repeat(cells))
                track.lap = lap
                track.arrowColumn = null
            }
            val blockColumn = trackCellColumn(cell, cells, input.isReverse)
            track.arrowColumn?.takeIf { it != blockColumn }?.let {
                sb.append(AnsiTerminal.gotoColumn(it)).append(" ")
            }
            val c = when {
                state.stalled -> AnsiTerminal.Color.RED
                lap > 0 -> AnsiTerminal.Color.PURPLE
                else -> AnsiTerminal.Color.GREEN
            }
            sb.append(AnsiTerminal.gotoColumn(blockColumn))
            sb.append(c.reverse()).append(" ").append(AnsiTerminal.reset())
            // Arrow in the next cell; none once the lap is full.
            track.arrowColumn = if (cell + 1 < cells) {
                val arrowColumn = trackCellColumn(cell + 1, cells, input.isReverse)
                sb.append(AnsiTerminal.gotoColumn(arrowColumn))
                sb.append(c.fg()).append(AnsiTerminal.bold())
                sb.append(if (input.isReverse) ARROW_LEFT else ARROW_RIGHT)
                sb.append(AnsiTerminal.reset())
                arrowColumn
            } else null
        }
        sb.append(AnsiTerminal.gotoColumn(formattedOutputLineMarker))
        sb.append("[")
        sb.append(if (state.intervalNumber > 0) AnsiTerminal.Color.BLUE.reverse() else AnsiTerminal.Color.RED.reverse())
        sb.append("Interval ")
        sb.append("%04d".format(state.intervalNumber))
        sb.append(AnsiTerminal.reset())
        sb.append(AnsiTerminal.box(AnsiTerminal.Color.CYAN," ${state.currentBandWidth}"))
        sb.append("]")
        // Erase what a longer previous rate left behind ("1.32 Gbits/sec" ->
        // "1.3 Gbits/sec" otherwise shows a stray "]"); doesn't move the cursor.
        sb.append(AnsiTerminal.clearToEol())
        sb.append(getStatusLabel(elapsedMillis = elapsedMilliSeconds, state =  state))
        return sb.toString()
    }
}
