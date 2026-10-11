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
     * True when stdout is a real ANSI-capable terminal.
     *
     * On Windows, this is unconditionally true, the same override
     * BandWidthTester's `TerminalType` constructor already uses -- Windows
     * consoles (PowerShell, cmd, Windows Terminal) support ANSI VT escape
     * sequences natively since Windows 10 but don't set a `TERM` env var at
     * all, so checking `TERM` there would always read as "dumb" and silently
     * disable every escape code this object produces, which is wrong, not
     * conservative. Everywhere else, a missing, empty, or "dumb" `TERM`
     * means no escape codes -- redirected-to-a-file and CI/log-capture
     * output stays plain text instead of filling up with raw escape bytes.
     */
    val isAnsi: Boolean = run {
        val osName = System.getProperty("os.name") ?: ""
        if (osName.startsWith("Windows", ignoreCase = true)) {
            true
        } else {
            val term = System.getenv("TERM")
            !(term.isNullOrEmpty() || term.equals("dumb", ignoreCase = true))
        }
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

        /** Reverse video (swaps fore/background) in this color -- used for
         * the live progress track's filled cells, where what's wanted is a
         * solid block of color rather than readable boxed text. */
        fun reverse(): String = if (isAnsi) "$ESC[${code};7m" else ""

        /** A solid background color block with the text still readable on
         * top (true SGR background code, not reverse-video) -- the
         * "highlighted label" look (e.g. a boxed `sender`/`receiver` or
         * `[Avg]`/`[Min]`/`[Max]`) real terminal dashboards use, as opposed
         * to [reverse], which recolors whatever the terminal's own default
         * foreground/background happen to be. */
        fun bg(): String = if (isAnsi) "$ESC[${code + 10}m" else ""
    }

    /** Clear the current line and return the cursor to column 1 -- used before
     * printing a completed line over an in-progress status line. */
    fun clearLine(): String = cr() + clearToEol()

    /** Wraps [text] in a solid [color] background box, e.g. `box(BLUE, "sender")`. */
    fun box(color: Color, text: String): String = "${color.bg()}$text${reset()}"

    /** A full-width horizontal rule, the same `─` character BandWidthTester's
     * own `TerminalType.ANSI_LINE` uses (falls back to `-` when [isAnsi] is
     * false, since the box-drawing character itself isn't an escape code and
     * would otherwise still print on a genuinely dumb/non-UTF terminal). */
    fun horizontalRule(width: Int): String {
        val ch = if (isAnsi) '─' else '-'
        return ch.toString().repeat(width)
    }
}
