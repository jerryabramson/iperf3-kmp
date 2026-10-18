/*
 * Copyright (c) 2026, Jerold Abramson
 *
 * This source code is licensed under the BSD 3-Clause License found in
 * the LICENSE file in the root directory of this source tree.
 */

package com.jabramson.iperf3_network_tester_kmp.utils

/**
 * Platform-independent printf-style string formatting.
 *
 *   Written using the below AI tooling:
 *      * qwen_qwen3.6-35b-a3b running locally inside LM Studio
 *        on Dell Pro Max with GB10 - 128 GB
 *      * opencode version 1.18.3 on Macbook Pro 14-inch, M5 Max 128 GB
 *
 *   A full discussion of the tradeoffs and benefits of this approach can be found here:
 *      * NATIVE_FORMATTING_JUSTIFICATION.md
 *
 * This function provides a Kotlin-friendly wrapper around platform-specific
 * formatting implementations:
 *
 * - iOS/native targets: Foundation's NSString stringWithFormat: (printf-compatible)
 * - JVM/Android targets: java.lang.String.format with Locale.US
 * 
 * Supported format specifiers match the C printf family:
 *       %s - String
 *   %d, %i - Integer (Int, Long, Short, Byte)
 *       %f - Floating point (Double, Float)
 *   %e, %g - Scientific/decimal notation
 *   %x, %o - Hexadecimal/octal
 * 
 * Format flags are supported:
 *   - Left-align within field width
 *   0 Zero-pad numeric fields
 *   + Always show sign for numbers
 *   (space) Space for positive numbers
 *   # Alternate form (e.g., 0x prefix for %x)
 * 
 * Width and precision are supported:
 *   %-12s - Left-align string to 12 characters
 *     %5d - Right-align integer in 5-character field
 *    %.2f - Float with 2 decimal places
 *   %8.3f - Total width 8, 3 decimal places
 * 
 * Example usage:
 *   val result = formatString("%-12s %5d", "hello", 42)
 *   // Returns: "hello       42    "
 * 
 * Buffer size is automatically managed (default 4096 characters).
 * For very long format strings, use the overload with explicit bufferSize.
 *
 * @param format The format string, using printf-style format specifiers.
 * @param args The arguments to format into the string.
 * @return The formatted string.
 */
expect fun formatString(format: String, vararg args: Any): String


