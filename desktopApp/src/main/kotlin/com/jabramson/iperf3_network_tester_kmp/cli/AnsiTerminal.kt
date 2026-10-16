/*
 * Copyright (c) 2026, Jerold Abramson
 *
 * This source code is licensed under the BSD 3-Clause License found in
 * the LICENSE file in the root directory of this source tree.
 */

package com.jabramson.iperf3_network_tester_kmp.cli

/**
 * Minimal ANSI terminal helper for [CliRunner]'s live command-line output.
 *
 * The same technique (and the same "is this terminal ANSI-capable" heuristic) as the
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

    fun setColumnWidth() {
        width = System.getenv("COLUMNS")?.trim()?.toIntOrNull()?.takeIf { it > 0 }
            ?: sttyColumns()
            ?: 80
    }

    /** Fallback for when `COLUMNS` isn't exported (many shells only set it as a
     * shell variable; `ssh host cmd` and cron never have it): ask the
     * controlling terminal directly. Null on Windows, with no tty (output
     * redirected, IDE/Gradle runs), or on any failure. */
    private fun sttyColumns(): Int? {
        if ((System.getProperty("os.name") ?: "").startsWith("Windows", ignoreCase = true)) return null
        return try {
            val process = ProcessBuilder("sh", "-c", "stty size < /dev/tty").redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText().trim()
            if (process.waitFor() != 0) null
            else output.split(" ").getOrNull(1)?.toIntOrNull()?.takeIf { it > 0 }
        } catch (_: Exception) {
            null
        }
    }

    var width = 80

    private const val ESC = "\u001B"

    /** A plain line feed, deliberately not `System.lineSeparator()` (which
     * is `\r\n` on Windows) -- [CliRunner] already emits its own explicit
     * `\r` via [cr]/[clearLine] where a carriage return is actually wanted,
     * so letting `println()` additionally append a platform line separator
     * would risk a doubled `\r` on Windows. Every line this module writes
     * ends with exactly this, nothing implicit. */
    const val NL = "\n"

    fun reset(): String = if (isAnsi) "$ESC[0m" else ""
    fun bold(): String = if (isAnsi) "$ESC[1m" else ""
    fun clearToEol(): String = if (isAnsi) "$ESC[K" else ""

    fun cr(): String = if (isAnsi) "\r" else ""

    /** Move the cursor to the given 1-based column on the current line, so a
     * status line can be redrawn in place instead of scrolling the terminal. */
    fun gotoColumn(col: Int): String = if (isAnsi) "$ESC[${col}G" else ""

    enum class Color(private val code: Int) {
        RED(31), GREEN(32), YELLOW(33), BLUE(34), PURPLE(35), CYAN(36), GREY(37);

        /** Plain foreground color. */
        fun fg(): String = if (isAnsi) "$ESC[${code}m" else ""

        /** Foreground color + bold, for things that should stand out
         * (headers, the final return code) without being a full highlight. */
        fun bold(): String = if (isAnsi) "$ESC[${code};1m" else ""

        /** Reverse video (swaps fore/background) in this color -- used for
         * the live progress track's filled cells, where what's wanted is a
         * solid block of color rather than readable boxed text. */
        fun reverse(): String = if (isAnsi) "$ESC[${code};7m" else ""

        /** A true SGR background-color code, bold, no implied foreground --
         * [AnsiCodes.ANSI_COLOR.getReverseHighlightCode] in BandWidthTester,
         * confusingly named there too (it's a real background code, not a
         * reverse-video trick). Used for the progress track's solid filled
         * cells, where a uniform color block is wanted regardless of the
         * terminal's own default foreground/background pairing -- unlike
         * [reverse], legibility of text on top isn't a concern here, there
         * is no text, just a space character. */
        fun fillCell(): String = if (isAnsi) "$ESC[${code + 10};1m" else ""
    }

    /** The two Nerd Font Powerline "round" glyphs BandWidthTester's own
     * `TerminalType.FANCY_RIGHT_ARROW`/`FANCY_LEFT_ARROW` use as the leading,
     * rounded cap of its progress track -- safe to rely on here since this
     * project's own terminal font (`CLAUDE.md`'s font section) is already a
     * real Nerd Font. Right-pointing for a left-to-right (upload) track,
     * left-pointing for a right-to-left (reverse/download) one. */
    const val ARROW_RIGHT = ""
    const val ARROW_LEFT = ""

    /** Clear the current line and return the cursor to column 1 -- used before
     * printing a completed line over an in-progress status line. */
    fun clearLine(): String = cr() + clearToEol()

    /** Wraps [text] in a boxed [color] label, e.g. `box(BLUE, "sender")` --
     * reverse video ([Color.reverse]), not a true background code: swapping
     * relative to whatever the terminal's own default foreground/background
     * happen to be is what keeps the text inside readable on any theme
     * (BandWidthTester's own `[Avg]`/`[Min]`/`[Max]`/`sender`/`receiver`
     * labels use exactly this -- `getReverseBoldCode`, not a background
     * code). A true background code here would leave the label text in
     * whatever the terminal's default foreground already is, which for a
     * light color like grey-on-dark-terminal is unreadably low-contrast. */
    fun box(color: Color, text: String): String = "${color.reverse()}$text${reset()}"

    /** A full-width horizontal rule, the same `─` character BandWidthTester's
     * own `TerminalType.ANSI_LINE` uses (falls back to `-` when [isAnsi] is
     * false, since the box-drawing character itself isn't an escape code and
     * would otherwise still print on a genuinely dumb/non-UTF terminal). */
    fun horizontalRule(width: Int): String {
        val ch = if (isAnsi) "─" else "-"
        return ch.repeat(width)
    }

    fun horizontalRule(): String {
        return horizontalRule(width)
    }
}