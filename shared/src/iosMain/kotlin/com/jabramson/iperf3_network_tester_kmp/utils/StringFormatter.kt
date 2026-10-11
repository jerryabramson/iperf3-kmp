package com.jabramson.iperf3_network_tester_kmp.utils

import com.jabramson.iperf3_network_tester_kmp.nativeformat.FormatArg
import com.jabramson.iperf3_network_tester_kmp.nativeformat.FormatArgType
import com.jabramson.iperf3_network_tester_kmp.nativeformat.format_string
import kotlinx.cinterop.*

private const val OUT_BUFFER_SIZE = 65536

/**
 * iOS implementation of formatString(), calling into the native
 * `format_string()` in native/native_format.h directly via cinterop (see
 * native_format.def) — no library to link, since the function is `static
 * inline` and fully defined in the header. This is the same C function
 * Android and desktop reach via their own JNI builds.
 */
@OptIn(ExperimentalForeignApi::class)
actual fun formatString(format: String, vararg args: Any): String {
    val result = try {
        memScoped {
            val cArgs = if (args.isEmpty()) null else allocArray<FormatArg>(args.size)
            args.forEachIndexed { index, arg ->
                val entry = cArgs!![index]
                when (arg) {
                    is String -> {
                        entry.type = FormatArgType.FORMAT_ARG_STRING
                        entry.s = arg.cstr.getPointer(this)
                    }
                    is Double -> {
                        entry.type = FormatArgType.FORMAT_ARG_DOUBLE
                        entry.d = arg
                    }
                    is Float -> {
                        entry.type = FormatArgType.FORMAT_ARG_DOUBLE
                        entry.d = arg.toDouble()
                    }
                    is Long -> {
                        entry.type = FormatArgType.FORMAT_ARG_INT64
                        entry.i = arg
                    }
                    is Int -> {
                        entry.type = FormatArgType.FORMAT_ARG_INT64
                        entry.i = arg.toLong()
                    }
                    is Short -> {
                        entry.type = FormatArgType.FORMAT_ARG_INT64
                        entry.i = arg.toLong()
                    }
                    is Byte -> {
                        entry.type = FormatArgType.FORMAT_ARG_INT64
                        entry.i = arg.toLong()
                    }
                    else -> throw IllegalArgumentException(
                        "Unsupported argument type for formatString: ${arg::class}"
                    )
                }
            }

            val outBuffer = allocArray<kotlinx.cinterop.ByteVar>(OUT_BUFFER_SIZE)
            val n = format_string(outBuffer, OUT_BUFFER_SIZE, format, cArgs, args.size)
            if (n < 0) {
                throw IllegalArgumentException("Format string error: $format")
            }
            outBuffer.toKString()
        }
    } catch (e: IllegalArgumentException) {
        throw e
    } catch (e: Exception) {
        throw IllegalArgumentException("Format string error: $format - ${e.message}", e)
    }

    return if (result.length > 4096) result.substring(0, 4096) else result
}
