package com.jabramson.iperf3_network_tester_kmp.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Comprehensive tests for formatString across all platforms.
 * 
 * These tests verify that the printf-style formatting works correctly for:
 * - String padding and alignment (%s)
 * - Integer formatting with width/precision (%d, %i)
 * - Floating-point formatting with precision (%f)
 * - Mixed format specifiers
 * - Edge cases (empty strings, large numbers, negative values)
 * - Error handling (invalid format strings)
 */
class StringFormatterTest {

    // =========================================================================
    // String Formatting Tests (%s)
    // =========================================================================

    @Test
    fun stringSimpleRightAlign() {
        val result = formatString("%5s", "hi")
        assertEquals("   hi", result)
    }

    @Test
    fun stringSimpleLeftAlign() {
        val result = formatString("%-5s", "hello")
        assertEquals("hello", result)
    }

    @Test
    fun stringExactWidthNoPadding() {
        val result = formatString("%5s", "hello")
        assertEquals("hello", result)
    }

    @Test
    fun stringNoTruncationWhenLonger() {
        // Java/Kotlin String.format does NOT truncate strings longer than width
        val result = formatString("%-4s", "hello")
        assertEquals("hello", result)
    }

    @Test
    fun stringEmptyString() {
        val result = formatString("%-5s", "")
        assertEquals("     ", result)
    }

    // =========================================================================
    // Integer Formatting Tests (%d, %i)
    // =========================================================================

    @Test
    fun intSimpleRightAlign() {
        val result = formatString("%5d", 42)
        assertEquals("   42", result)
    }

    @Test
    fun intLeftAlign() {
        val result = formatString("%-5d", 99)
        assertEquals("99   ", result)
    }

    @Test
    fun intZeroPad() {
        val result = formatString("%05d", 7)
        assertEquals("00007", result)
    }

    @Test
    fun intNegativeWithZeroPad() {
        val result = formatString("%05d", -42)
        assertEquals("-0042", result)
    }

    @Test
    fun intExactWidthNoPadding() {
        val result = formatString("%3d", 123)
        assertEquals("123", result)
    }

    // =========================================================================
    // Floating-Point Formatting Tests (%f)
    // =========================================================================

    @Test
    fun doubleSimpleRightAlign() {
        val result = formatString("%8.2f", 3.14159)
        // Width 8: "    3.14" (4 spaces + "3.14")
        assertEquals("    3.14", result)
    }

    @Test
    fun doubleLeftAlign() {
        val result = formatString("%-8.2f", 3.14159)
        assertEquals("3.14    ", result)
    }

    @Test
    fun doubleNegativeValue() {
        val result = formatString("%8.1f", -2.718)
        // Width 8: "    -2.7" (5 spaces + "-2.7")
        assertEquals("    -2.7", result)
    }

    @Test
    fun doubleZeroPrecision() {
        val result = formatString("%.0f", 3.7)
        assertEquals("4", result)
    }

    @Test
    fun doubleHighPrecision() {
        val result = formatString("%.6f", 1.0)
        assertEquals("1.000000", result)
    }

    @Test
    fun doubleLargeValue() {
        val result = formatString("%12.2f", 9876543.21)
        // Width 12: "  9876543.21" (2 spaces + "9876543.21")
        assertEquals("  9876543.21", result)
    }

    // =========================================================================
    // Mixed Format Specifier Tests
    // =========================================================================

    @Test
    fun mixedStringAndInt() {
        val result = formatString("%-10s %5d", "test", 42)
        assertEquals("test          42", result)
    }

    @Test
    fun mixedAllTypes() {
        val result = formatString("%-12s %5d %-9.2f", "hello", 42, 3.14159)
        // %-12s="hello       ", %5d="   42", %-9.2f="3.14     "
        assertEquals("hello           42 3.14     ", result)
    }

    @Test
    fun multipleSameSpecifier() {
        val result = formatString("%5d %5d %5d", 1, 2, 3)
        assertEquals("    1     2     3", result)
    }

    // =========================================================================
    // iperf3-Specific Format String Tests
    // =========================================================================

    @Test
    fun iperfConnectionInfoFormat() {
        // Simulates: [ ID ] Interval... connection line
        val result = formatString("%-12s %4s %-9s %10s", "ID", "Int", "Receiver", "Transmitter")
        assertEquals("ID            Int Receiver  Transmitter", result)
    }

    @Test
    fun iperfIntervalFormat() {
        // Simulates interval output: 0.0-1.0 sec, 1.23 Mbits/sec, sender/receiver
        val result = formatString("%-12s %4s %-9s %10s", "0.0-1.0", "1.23", "Mbits", "sender")
        assertEquals("0.0-1.0      1.23 Mbits         sender", result)
    }

    @Test
    fun iperfSummaryFormat() {
        // Simulates summary line with statistics
        val result = formatString("%-12s %5d %-9.2f", "SUM", 10, 5.678)
        assertEquals("SUM             10 5.68     ", result)
    }

    // =========================================================================
    // Edge Case Tests
    // =========================================================================

    @Test
    fun noFormatSpecifiers() {
        val result = formatString("hello world")
        assertEquals("hello world", result)
    }

    @Test
    fun singleSpecifierNoArgs() {
        // Java throws MissingFormatArgumentException when format specifiers exceed arguments
        assertFailsWith<IllegalArgumentException> {
            formatString("%s")
        }
    }

    @Test
    fun tooManySpecifiers() {
        // Java throws MissingFormatArgumentException when there are more specifiers than args
        assertFailsWith<IllegalArgumentException> {
            formatString("%d %d", 42)
        }
    }

    @Test
    fun veryLongOutputTruncation() {
        // Test that output is limited to 4096 characters (internal buffer limit)
        val longString = "a".repeat(5000)
        val result = formatString("%s", longString)
        assertEquals(4096, result.length)
        assertEquals("a".repeat(4096), result)
    }

    @Test
    fun moderateOutputNoTruncation() {
        // Test that normal-length output is not truncated
        val mediumString = "hello world".repeat(100)
        val result = formatString("%s", mediumString)
        assertEquals(mediumString.length, result.length)
        assertEquals(mediumString, result)
    }

    // =========================================================================
    // Type Handling Tests
    // =========================================================================

    @Test
    fun longInteger() {
        val result = formatString("%15d", 987654321L)
        // Width 15: "      987654321" (6 spaces + 9 digits)
        assertEquals("      987654321", result)
    }

    @Test
    fun shortInteger() {
        val result = formatString("%5d", 99.toShort())
        assertEquals("   99", result)
    }

    @Test
    fun byteInteger() {
        val result = formatString("%3d", 42.toByte())
        assertEquals(" 42", result)
    }

    @Test
    fun floatValue() {
        val result = formatString("%.2f", 3.14f)
        assertEquals("3.14", result)
    }

    // =========================================================================
    // Error Handling Tests
    // =========================================================================

    @Test
    fun invalidFormatSpecifier() {
        assertFailsWith<IllegalArgumentException> {
            formatString("%x", "not a number")
        }
    }

    @Test
    fun malformedFormatString() {
        assertFailsWith<IllegalArgumentException> {
            formatString("%", 42)
        }
    }
}
