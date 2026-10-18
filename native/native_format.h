// Copyright (c) 2026, Jerold Abramson
//
// This source code is licensed under the BSD 3-Clause License found in
// the LICENSE file in the root directory of this source tree.

/*
 * native_format.h — single source of truth for printf-style formatting,
 * shared across Android (JNI), desktop JVM (JNI), and iOS (cinterop).
 *
 * See the following documents for a more thorough analysis and justification
 * for this approach:
 *    <git_root>/
 *       IPERF3_NATIVE_INTEGRATION.md
 *       NATIVE_FORMATTING_JUSTIFICATION.md
 *       PRINTF_FORMATTING_CASE_STUDY.md
 *
 * A generic C variadic function (`int printf(const char*, ...)`) cannot be
 * called generically across a JNI or cinterop boundary — the caller and
 * callee must agree on argument types and count at compile time, which is
 * exactly what "generic vararg args from Kotlin" breaks. So instead of a
 * variadic function, format_string() takes an explicit array of tagged
 * FormatArg values, one per conversion specifier.
 *
 * format_string() itself only tokenizes the format string into literal runs
 * and single conversion specifiers, matching each specifier with the next
 * FormatArg. For each specifier it rebuilds a minimal one-conversion format
 * string and calls the platform's own snprintf() on exactly one correctly
 * typed argument — so width/precision/flags/rounding/zero-padding all come
 * from the real libc printf implementation, not a reimplementation of it.
 *
 * Defined `static inline` and fully in this header (no separate .c/.o) so
 * every consumer — a JNI wrapper's translation unit, or Kotlin/Native's
 * cinterop, which compiles headers directly — gets an identical copy of the
 * same logic with no cross-target linking step.
 */
#ifndef NATIVE_FORMAT_H
#define NATIVE_FORMAT_H

#include <stdint.h>
#include <stdio.h>
#include <string.h>

typedef enum {
    FORMAT_ARG_INT64,
    FORMAT_ARG_DOUBLE,
    FORMAT_ARG_STRING
} FormatArgType;

typedef struct {
    FormatArgType type;
    int64_t i;
    double d;
    const char* s;
} FormatArg;

/*
 * Formats `fmt` using `args` (one entry per non-"%%" conversion specifier,
 * in order) into `out` (a buffer of `out_size` bytes, always null
 * terminated on success). Returns the number of characters written
 * (excluding the null terminator), or -1 on error: a null/zero-size
 * buffer, a malformed or unsupported specifier, an argument count
 * mismatch, or output that would exceed out_size.
 */
static inline int format_string(char* out, int out_size, const char* fmt,
                                 const FormatArg* args, int arg_count) {
    if (out == NULL || out_size <= 0 || fmt == NULL || (arg_count > 0 && args == NULL)) {
        return -1;
    }

    int out_pos = 0;
    int arg_index = 0;
    const char* p = fmt;

    while (*p != '\0') {
        if (*p != '%') {
            if (out_pos + 1 >= out_size) return -1;
            out[out_pos++] = *p++;
            continue;
        }

        p++; /* consume '%' */

        if (*p == '%') {
            if (out_pos + 1 >= out_size) return -1;
            out[out_pos++] = '%';
            p++;
            continue;
        }

        const char* mid_start = p; /* flags + width + precision (no length modifiers) */

        while (*p == '-' || *p == '+' || *p == ' ' || *p == '#' || *p == '0') p++;
        while (*p >= '0' && *p <= '9') p++;
        if (*p == '.') {
            p++;
            while (*p >= '0' && *p <= '9') p++;
        }
        if (*p == '*') return -1; /* dynamic width/precision unsupported */

        int mid_len = (int)(p - mid_start);

        /* skip (and discard) any length modifiers: h, hh, l, ll, L, z, j, t */
        while (*p == 'h' || *p == 'l' || *p == 'L' || *p == 'z' || *p == 'j' || *p == 't') p++;

        char conv = *p;
        if (conv == '\0') return -1; /* dangling '%' */
        p++; /* consume conversion char */

        if (mid_len < 0 || mid_len > 24) return -1;

        int remaining = out_size - out_pos;
        if (remaining <= 0) return -1;

        if (arg_index >= arg_count) return -1;
        const FormatArg* arg = &args[arg_index++];

        int written;
        char spec[32];

        switch (conv) {
            case 'd': case 'i': case 'u': case 'x': case 'X': case 'o': {
                if (arg->type == FORMAT_ARG_STRING) return -1; /* numeric conversion needs a number */
                long long val = (arg->type == FORMAT_ARG_DOUBLE) ? (long long)arg->d : arg->i;
                spec[0] = '%';
                memcpy(spec + 1, mid_start, mid_len);
                spec[1 + mid_len] = 'l';
                spec[2 + mid_len] = 'l';
                spec[3 + mid_len] = conv;
                spec[4 + mid_len] = '\0';
                written = snprintf(out + out_pos, (size_t)remaining, spec, val);
                break;
            }
            case 'c': {
                if (arg->type == FORMAT_ARG_STRING) return -1; /* numeric conversion needs a number */
                int val = (int)((arg->type == FORMAT_ARG_DOUBLE) ? (long long)arg->d : arg->i);
                spec[0] = '%';
                memcpy(spec + 1, mid_start, mid_len);
                spec[1 + mid_len] = conv;
                spec[2 + mid_len] = '\0';
                written = snprintf(out + out_pos, (size_t)remaining, spec, val);
                break;
            }
            case 'f': case 'F': case 'e': case 'E': case 'g': case 'G': {
                if (arg->type == FORMAT_ARG_STRING) return -1; /* numeric conversion needs a number */
                double val = (arg->type == FORMAT_ARG_DOUBLE) ? arg->d : (double)arg->i;
                spec[0] = '%';
                memcpy(spec + 1, mid_start, mid_len);
                spec[1 + mid_len] = conv;
                spec[2 + mid_len] = '\0';
                written = snprintf(out + out_pos, (size_t)remaining, spec, val);
                break;
            }
            case 's': {
                const char* val = (arg->type == FORMAT_ARG_STRING && arg->s != NULL) ? arg->s : "";
                spec[0] = '%';
                memcpy(spec + 1, mid_start, mid_len);
                spec[1 + mid_len] = conv;
                spec[2 + mid_len] = '\0';
                written = snprintf(out + out_pos, (size_t)remaining, spec, val);
                break;
            }
            default:
                return -1; /* unsupported conversion */
        }

        if (written < 0 || written >= remaining) return -1;
        out_pos += written;
    }

    if (out_pos >= out_size) return -1;
    out[out_pos] = '\0';
    return out_pos;
}

#endif /* NATIVE_FORMAT_H */
