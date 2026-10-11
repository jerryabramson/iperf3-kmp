package com.jabramson.iperf3_network_tester_kmp.cli

/**
 * Minimal ANSI terminal helper for [CliRunner]'s live command-line output.
 *
 * Same technique (and the same "is this terminal ANSI-capable" heuristic) as the
 * hand-rolled `AnsiCodes`/`TerminalType` classes in
 * https://github.com/jerryabramson/BandWidthTester, a earlier standalone iperf3
 * CLI wrapper this project's CLI mode is modeled on: a handful of raw escape
 * codes (cursor column positioning, clear-to-end-of-line, carriage return,
 * color/bold/reverse-video), gated by whether `TERM` looks like a real
 * terminal. Deliberately no terminal-UI library dependency -- "simple terminal
 * control codes" is the whole point.
 */
object AnsiTerminal {
    /**
     * True when stdout is a real ANSI-capable terminal. A missing, empty, or
     * "dumb" `TERM` (the same signal BandWidthTester's `TerminalType` checks)
     * means no escape codes -- redirected-to-a-file and CI/log-capture output
     * stays plain text instead of filling up with raw escape bytes.
     */
    val isAnsi: Boolean = run {
        val term = System.getenv("TERM")
        !(term.isNullOrEmpty() || term.equals("dumb", ignoreCase = true))
    }

    private const val ESC = "\u001B"

    fun reset(): String = if (isAnsi) "$ESC[0m" else ""
    fun bold(): String = if (isAnsi) "$ESC[1m" else ""
    fun clearToEol(): String = if (isAnsi) "$ESC[K" else ""
    fun cr(): String = if (isAnsi) "\r" else ""

    /** Move the cursor to the given 1-based column on the current line, so a
     * status line can be redrawn in place instead of scrolling the terminal. */
    fun gotoColumn(col: Int): String = if (isAnsi) "$ESC[${col}G" else ""

    enum class Color(private val code: Int) {
        RED(31), GREEN(32), YELLOW(33), BLUE(34), PURPLE(35), GREY(37);

        /** Plain foreground color. */
        fun fg(): String = if (isAnsi) "$ESC[${code}m" else ""

        /** Foreground color + bold, for things that should stand out
         * (headers, the final return code) without being a full highlight. */
        fun bold(): String = if (isAnsi) "$ESC[${code};1m" else ""

        /** Reverse video (swaps fore/background) in this color, for the
         * short highlighted labels (e.g. "[Running]") BandWidthTester uses
         * to visually separate the status line from scrolled-up history. */
        fun reverse(): String = if (isAnsi) "$ESC[${code};7m" else ""
    }

    /** Clear the current line and return the cursor to column 1 -- used before
     * printing a completed line over an in-progress status line. */
    fun clearLine(): String = cr() + clearToEol()
}
