/*
 * Copyright (c) 2026, Jerold Abramson
 *
 * This source code is licensed under the BSD 3-Clause License found in
 * the LICENSE file in the root directory of this source tree.
 */

/*
   * Copyright (c) 2026, Jerold Abramson
   *
   * This source code is licensed under the BSD 3-Clause License found in
   * the LICENSE file in the root directory of this source tree.
   */

package com.jabramson.iperf3_network_tester_kmp.view

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * True when the *active color scheme* is dark. Uses the scheme itself rather than
 * isSystemInDarkTheme(), so it stays correct if the theme is ever forced light/dark.
 */
@Composable
fun isDarkScheme(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

/**
 * Top-bar container: keeps the lavender primaryContainer in light mode, but uses a
 * calm tonal surface in dark mode instead of the saturated primaryContainer purple.
 */
@Composable
fun topBarContainerColor(): Color =
    if (isDarkScheme()) MaterialTheme.colorScheme.surfaceContainerHigh
    else MaterialTheme.colorScheme.primaryContainer