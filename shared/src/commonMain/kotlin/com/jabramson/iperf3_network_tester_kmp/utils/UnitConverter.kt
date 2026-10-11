package com.jabramson.iperf3_network_tester_kmp.utils

import androidx.compose.runtime.retain.retain
import androidx.compose.ui.text.intl.Locale
import com.jabramson.iperf3_network_tester_kmp.utils.Units.UNIT_LENTGH
import com.jabramson.iperf3_network_tester_kmp.utils.Units.BITS_UNIT
import com.jabramson.iperf3_network_tester_kmp.utils.Units.GBITS
import com.jabramson.iperf3_network_tester_kmp.utils.Units.GB_UNIT
import com.jabramson.iperf3_network_tester_kmp.utils.Units.KBITS
import com.jabramson.iperf3_network_tester_kmp.utils.Units.KB_UNIT
import com.jabramson.iperf3_network_tester_kmp.utils.Units.MBITS
import com.jabramson.iperf3_network_tester_kmp.utils.Units.MB_UNIT
import com.jabramson.iperf3_network_tester_kmp.utils.Units.TBITS
import com.jabramson.iperf3_network_tester_kmp.utils.Units.TB_UNIT
import com.jabramson.iperf3_network_tester_kmp.utils.Units.ZERO_STRING
import com.jabramson.iperf3_network_tester_kmp.utils.Units.ZERO_VALUE_STRING
//import java.util.Locale


/**
 * Represents a numeric value together with its associated unit.
 *
 * The `toString()` implementation returns the formatted value.
 */
data class UnitConvertedData(val value: Double = 0.0, val unit: String = "") {
    override fun toString(): String = if (value <= 0.0) "" else  "$value $unit"
}

object Units {
    const val KBITS = 1024.0
    const val MBITS = 1024.0 * KBITS
    const val GBITS = 1024.0 * MBITS
    const val TBITS = 1024.0 * GBITS
    const val BITS = 1.0

    const val UNIT_LENTGH = "Gbits/sec".length
    const val KB_UNIT = "Kbits/sec"
    const val MB_UNIT = "Mbits/sec"
    const val GB_UNIT = "Gbits/sec"
    const val TB_UNIT = "Tbits/sec"
    const val BITS_UNIT = "bits/sec"
    const val ZERO_STRING = "----"
    const val ZERO_VALUE_STRING  ="----------"
}


/**
 * Returns a string representation of the given unit‑converted data.
 *
 * @param unitConvertedData The data containing a numeric value and its associated unit.
 * @return A formatted string consisting of the value followed by a space and the unit.
 * */
fun toString(unitConvertedData: UnitConvertedData): String =
    "${unitConvertedData.value} ${unitConvertedData.unit}"

/**
 * Converts a {@code UnitConvertedData} into its integer string representation.
 *
 * If the value is greater than zero, the method returns the integer part of the value as a string; otherwise it returns the constant {@code ZERO_STRING}.
 *
 * @param unitConvertedData the data class containing the numeric value and associated unit to be converted
 * @return the integer string representation of the value, or {@code ZERO_STRING} if the value is not positive
 * */
fun toIntString(unitConvertedData: UnitConvertedData): String =
    if (unitConvertedData.value >  0) "${unitConvertedData.value.toInt()})" else ZERO_STRING


/**
 * Converts the provided [UnitConvertedData] into a formatted string representation.
 *
 * When preview is false, positive values are formatted to a fixed width with two decimal places followed by the unit.
 * Non-positive values are replaced with a predefined zero value string.
 * When preview is true, returns the raw numeric value and unit concatenated without formatting or sign checks.
 *
 * @param unitConvertedData The data object containing the numeric value and its associated unit.
 * @param preview Flag indicating whether to return unformatted raw values or apply standard formatting rules. Defaults to false.
 * @return A formatted string representation of the converted data, or a zero-value placeholder for non-positive inputs when not in preview mode.
 */
fun toWholeNumber(unitConvertedData: UnitConvertedData, preview: Boolean = false): String {
    val ret: String
    if (!preview) {
        val unitLengthFormat = "%${UNIT_LENTGH}.${UNIT_LENTGH}s"
        ret =
            if (unitConvertedData.value == 0.toDouble())
                formatString("%-13.13s  $unitLengthFormat", "00.00", unitConvertedData.unit)
            else if (unitConvertedData.value > 0)
                formatString("%10.2f  $unitLengthFormat", unitConvertedData.value, unitConvertedData.unit)
            else
                ZERO_VALUE_STRING
    } else {
        ret = "${unitConvertedData.value} ${unitConvertedData.unit}"
    }
    return ret
}

fun formatTime(time: Double, preview: Boolean = false): String = formatGeneric(time, "Seconds", preview)
fun formatTime(time: Int, preview: Boolean = false): String =formatGeneric(time, "Seconds", preview)
fun formatIterations(iterations: Long, preview: Boolean = false): String = formatGeneric(iterations, "Reported", preview)
fun formatGeneric(value: Int, unit: String, preview: Boolean = false): String = formatGeneric(value.toLong(), unit, preview)

fun formatGeneric(value: Double, unit: String, preview: Boolean = false): String {
    val ret: String
    if (!preview) {
        val unitLengthFormat = "%${UNIT_LENTGH}.${UNIT_LENTGH}s"
        ret =
            if (value != 0.toDouble())
                formatString("%9.3f $unitLengthFormat", value,  unit)
            else
                ""
    } else {
        ret = "  $value $unit"
    }
    return ret
}


fun formatGeneric(value: Long, unit: String, preview: Boolean = false): String {
    val ret: String
    if (!preview) {
        val unitLengthFormat = "%${UNIT_LENTGH}.${UNIT_LENTGH}s"
        val d = value.toDouble()
        ret =
            if (value != 0.toLong())
                formatString("%-6.0f $unitLengthFormat", d,  unit)
            else
                ""
    } else {
        ret = "  $value $unit"
    }
    return ret
}


/**
 * Converts a human-readable network speed value with a specified unit into raw bits per second.
 *
 * @param value The numeric value to convert, expressed in the given unit.
 * @param unit The unit of measurement for the input value (e.g., KB, MB, GB, TB).
 * @return The equivalent value in raw bits per second. If the unit is not recognized, returns the original value unchanged.
 */
fun fromHumanUnit(value: Double, unit: String): Double {
        val rawBitsPerSec = when (unit) {
            KB_UNIT -> value * KBITS
            MB_UNIT -> value * MBITS
            GB_UNIT -> value * GBITS
            TB_UNIT -> value * TBITS
            else -> value
        }
        return rawBitsPerSec
}

/**
 * Converts a human-readable network speed value with its associated unit into raw bits per second.
 *
 * @param value The numeric value and its associated unit to convert.
 * @return The equivalent value in raw bits per second. If the unit is not recognized, returns the original value unchanged.
 */
fun fromHumanUnit(value: UnitConvertedData): Double {
    return fromHumanUnit(value.value, value.unit)
}

fun fromHumanString(value: String, unit: String): UnitConvertedData {
    val rawBitsPerSec = fromHumanUnit(value.toDouble(), unit)
    return toHumanUnit(rawBitsPerSec)
}


/**
 * Converts a raw bits-per-second value into a human-readable format with an appropriate unit.
 * The numeric value is truncated to two decimal places, and the unit is selected based on magnitude thresholds.
 *
 * @param rawBitsPerSec The raw data transfer rate in bits per second.
 * @return A [UnitConvertedData] instance containing the truncated value and its corresponding unit string.
 */
fun toHumanUnit(rawBitsPerSec: Double): UnitConvertedData {
    var perSec = rawBitsPerSec // bits/sec
    var convertedUnit = BITS_UNIT
    if (perSec >= TBITS) {
        perSec = rawBitsPerSec / TBITS
        convertedUnit = TB_UNIT
    } else if (perSec >= GBITS) {
        perSec = rawBitsPerSec / GBITS
        convertedUnit = GB_UNIT
    } else if (perSec >= MBITS) {
        perSec = rawBitsPerSec / MBITS
        convertedUnit = MB_UNIT
    } else if (perSec >= KBITS) {
        perSec = rawBitsPerSec / KBITS
        convertedUnit = KB_UNIT
    }
    return UnitConvertedData(truncateDouble(perSec), convertedUnit)
}

/**
 * Formats a double value to two decimal places if it is strictly between Double.MIN_VALUE and Double.MAX_VALUE.
 * Values outside this range, including negative numbers, zero, infinities, and NaNs, result in 0.0 being returned.
 *
 * @param d The double value to be formatted.
 * @return The double value rounded or truncated to two decimal places if within the valid range, otherwise 0.0.
 */
fun truncateDouble(d: Double): Double {
        if (d > Double.MIN_VALUE && d < Double.MAX_VALUE) {
            return formatString("%10.2f", d).toDouble()
        } else {
            return 0.0
        }
    }

/**
 * Converts the given current value to bits per second (bps).
 *
 * Checks whether the input falls within valid bounds before performing the conversion.
 * If the value is outside the expected range, zero is returned instead of a calculated result.
 * The conversion divides the long-cast input by KBITS to derive the final bps value.
 *
 * @param current The numeric measurement to be converted to bits per second.
 * @return The resulting value in bits per second, or 0 if the input is out of bounds.
 */
fun tobps(current: Double): Long {
    if (current < Double.MAX_VALUE && current > Double.MIN_VALUE) {
        return (current.toLong() / KBITS).toLong()
    }
    return 0
}

/**
 * Converts a numeric data transfer rate to a human-readable megabits per second (mbs) string.
 *
 * @param current The input value representing the data rate, typically in bits per second.
 * @return A formatted string showing the rate in mbs, or "  ***" if the value is outside acceptable bounds.
 */
fun toMbs(current: Double): String {
    if (current < Double.MAX_VALUE && current > Double.MIN_VALUE) {
        val perSec = current / MBITS
        return "$perSec.toInt() mbs"
    } else {
        return "  ***"
    }
}
