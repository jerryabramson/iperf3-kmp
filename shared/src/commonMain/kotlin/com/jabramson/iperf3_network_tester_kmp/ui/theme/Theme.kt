/*
 * Copyright (c) 2026, Jerold Abramson
 *
 * This source code is licensed under the BSD 3-Clause License found in
 * the LICENSE file in the root directory of this source tree.
 */

package com.jabramson.iperf3_network_tester_kmp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
//import androidx.compose.material3.dynamicDarkColorScheme
//import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

//import androidx.compose.ui.platform.LocalContext
//import androidx.compose.ui.platform.LocalView
//import androidx.core.view.WindowCompat
//import androidx.room.ForeignKey


val DarkColorScheme = darkColorScheme(
    // Was Purple40/PurpleGrey40/Pink40 -- those are *light-mode* tones and read
    // as dim mid-purple/brown on black. Dark schemes need the lighter 80 tones.
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80Dark,
    // onError keeps the M3 default (dark text on the light error color)
    background = Black,          // true OLED black; use Color(0xFF141218) for M3 surface tone
    onErrorContainer = SoftRed,  // was pure 0xFFF00000 -- harsh on black
    surfaceDim =  Grey40
)

 val LightColorScheme = lightColorScheme(
     primary = Black,
     secondary = PurpleGrey40,
     tertiary = Pink80,
     onError = Red40,
     background = White,
     onErrorContainer = BrightRed,
     surfaceDim = Grey80

     /* Other default colors to override */
//   surface = Color(0xFFFFFBFE),
//   onPrimary = Color.White,
//   onSecondary = Color.White,
//   onTertiary = Color.White,
//   onBackground = Color(0xFF1C1B1F),
//   onSurface = Color(0xFF1C1B1F),

)

@Composable
fun Iperf3NetworkTesterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {

    val colorScheme = when {
//        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
//            val context = LocalContext.current
//            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
//        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    //val view = LocalView.current
    //SideEffect {
        //val window = (view.context as Activity).window
        //window.statusBarColor = colorScheme.primary
        //WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = darkTheme
    //}
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}