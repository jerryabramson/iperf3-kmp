/*
 * Copyright (c) 2026, Jerold Abramson
 *
 * This source code is licensed under the BSD 3-Clause License found in
 * the LICENSE file in the root directory of this source tree.
 */

package com.jabramson.iperf3_network_tester_kmp.view




import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuDefaults.textFieldColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface



import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.tooling.preview.AndroidUiModes.UI_MODE_NIGHT_YES
import androidx.compose.ui.tooling.preview.AndroidUiModes.UI_MODE_TYPE_NORMAL
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.jabramson.iperf3_network_tester_kmp.ui.theme.Iperf3NetworkTesterTheme
import com.jabramson.iperf3_network_tester_kmp.utils.getDest
import com.jabramson.iperf3_network_tester_kmp.utils.getSource
import com.jabramson.iperf3_network_tester_kmp.viewmodel.AppScreen
import com.jabramson.iperf3_network_tester_kmp.viewmodel.Iperf3RunViewModel
import com.jabramson.iperf3_network_tester_kmp.viewmodel.UiExecutionData
import com.jabramson.iperf3_network_tester_kmp.viewmodel.UiInputData
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jabramson.iperf3_network_tester_kmp.getNotch



/**
 * Composable function for displaying the Iperf3 screen. This screen includes
 * UI elements like a top bar, bottom bar, and a body to manage and visualize
 * Iperf3 execution and input states.
 *
 * @param viewModel The ViewModel instance of type [Iperf3RunViewModel] used
 * to manage UI state and interactions. By default, it retrieves an instance
 * using Hilt and the current ViewModelStoreOwner. Throws an error if no
 * ViewModelStoreOwner is provided.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunIperf3Screen(viewModel: Iperf3RunViewModel = viewModel { Iperf3RunViewModel() })
{
    val uiExecutionState by viewModel.uiExecutionDataStateFlow.collectAsState()
    val uiInputState by viewModel.uiInputDataStateFlow.collectAsState()
    val uiMicroProgressData by viewModel.uiMicroProgressData.collectAsState()
    val currentScreen by viewModel.currentScreenStateFlow.collectAsState()
    val hasHistory by viewModel.hasHistoryStateFlow.collectAsState()
    val historyResults by viewModel.historyResultsStateFlow.collectAsState()
    val monoStyle = mesloMonoTextStyle()
    val fieldColors = textFieldColors()

    Iperf3NetworkTesterTheme(dynamicColor = true) {
        if (currentScreen == AppScreen.History) {
            HistoryScreen(
                results = historyResults,
                isCompact = isCompactHeight(),
                onBack = viewModel::showRunScreen,
                onDelete = viewModel::deleteHistoryItem,
                onExport = viewModel::exportHistoryCsv,
                preview =  false
            )
            return@Iperf3NetworkTesterTheme
        }
        // we are manually handling the notch, since it is wrong on iPhone's
        Scaffold(
            contentWindowInsets = ScaffoldDefaults.contentWindowInsets.exclude(insets = WindowInsets.displayCutout),
            modifier = Modifier
                .fillMaxWidth(),
            topBar = {
                IperfTopBar(
                    buttonAction = viewModel::launchOrCancel,
                    onHistoryClick = viewModel::showHistory,
                    onTitleTapped = viewModel::onAppTitleTapped,
                    currentRunning = uiExecutionState.isRunning,
                    hasHistory = hasHistory,
                    isCompact = isCompactHeight()
                )
            },
            bottomBar = {
                IperfBottomBar(
                    isDebugging = uiInputState.isDebugging,
                    isWide = isWideWindow(),
                    elapsedTime =  uiMicroProgressData.elapsedTime,
                    preview = false,
                    buttonAction =  viewModel::toggleDebug)
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(
                        start = deviceLargeNotchPadding(isWide = isWideWindow()),
                        end = deviceLargeNotchPadding(isWide = isWideWindow()),
                        top = innerPadding.calculateTopPadding(),
                        bottom = innerPadding.calculateBottomPadding(),

                    )
            )
            {
                InputFields(
                    currentHostName = uiInputState.hostName,
                    currentPort = uiInputState.portNumber,
                    currentDuration = uiInputState.durationSecs,
                    currentParallelStreams = uiInputState.parallelStreams,
                    currentSkip = uiInputState.skip,
                    currentReverse = uiInputState.isReverse,
                    isRunning = uiExecutionState.isRunning,
                    isWide = isWideWindow(),
                    updateHostName = viewModel::setHostName,
                    uploadDownload = viewModel::setUploadDownload,
                    launch = viewModel::launch,
                    setDuration = viewModel::setDuration,
                    setPortNumber = viewModel::setPortNumber,
                    setParallelStreams = viewModel::setParallelStreams,
                    setSkip = viewModel::setSkip,
                    colors = fieldColors,
                    style = MaterialTheme.typography.bodySmall,
                )
                //Spacer(modifier = Modifier.height(10.dp))

                /* Output rows */
                RunningColumnSection(
                    progress = uiMicroProgressData.progress,
                    percentageComplete = uiMicroProgressData.percentComplete,
                    isRunning = uiExecutionState.isRunning,
                    latestLine = uiExecutionState.latestLine,
                    bandWidth = uiExecutionState.bandWidth,
                    isReverse =  uiInputState.isReverse,
                    parallelStreams = uiInputState.parallelStreams,
                    durationSecs = uiInputState.durationSecs,
                    isOmitted = uiExecutionState.iperf3RunningState.omitted,
                    interval = uiExecutionState.iperf3RunningState.intervalNumber,
                    max = uiExecutionState.iperf3RunningState.currentMax,
                    min = uiExecutionState.iperf3RunningState.currentMin,
                    avg = uiExecutionState.iperf3RunningState.currentAvg,
                )
                ResultsRow(
                    results = uiExecutionState.results,
                    src = getSource(uiExecutionState.iperf3RunningState),
                    dest = getDest(uiExecutionState.iperf3RunningState),
                    returnCode = uiExecutionState.returnCode,
                    isRunning = uiExecutionState.isRunning,
                    isFinished = uiExecutionState.isFinished,
                    monoStyle = monoStyle,
                    isWide = isWideWindow(),
                    isTall = isTallWindow(),
                    isTiny = isTinyWindow()
                )
                ErrorSection(
                    errorLines = uiExecutionState.errorLines,
                    isWide = isWideWindow(),
                    isTall = isTallWindow(),
                    isTiny = isTinyWindow()
                )

                IperfMessagesSection(
                    isDebugMode = uiInputState.isDebugging,
                    isRunning = uiExecutionState.isRunning,
                    isFinished = uiExecutionState.isFinished,
                    latestLine = uiExecutionState.latestLine,
                    errorLines = uiExecutionState.errorLines,
                    iperfMessages = uiExecutionState.iperf3Messages,
                    returnCode = uiExecutionState.returnCode,
                    isTall = isTallWindow(),
                    isWide = isWideWindow(),
                    isTiny = isTinyWindow()
                )
                DebugOutputSection(
                    isDebugging = uiInputState.isDebugging,
                    isOmitted = uiExecutionState.iperf3RunningState.omitted,
                    outputLines =  uiExecutionState.outputLines,
                    monoStyle = monoStyle,
                    preview = false,
                    isWide = isWideWindow(),
                    isTall = isTallWindow(),
                    isTiny = isTinyWindow()
                )
            }
        }
    }
}

@Composable
fun isWideWindow(): Boolean {
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current

    val widthDp = with(density) {
        windowInfo.containerSize.width.toDp()
    }
    return widthDp >= 840.dp
}

@Composable
fun isTallWindow(): Boolean {
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current

    val heightDp = with(density) {
        windowInfo.containerSize.height.toDp()
    }
    return heightDp >= 650.dp
}
@Composable
fun isTinyWindow(): Boolean {
    return !isTallWindow() && !isWideWindow()
}


/**
 * Calculates the padding value required for devices with a large notch.
 * The function determines if the device has a wide window with a notch
 * and then provides a padding value to appropriately accommodate the notch size.
 * This is mainly for iPhone's with a huge 'dynamic island'.
 *
 * For normal Android devices with a very small camera notch, a small padding is used.
 *
 * @return The padding value in Dp. Returns 50.dp if the device has a wide window and a notch, otherwise 0.dp.
 */
@Composable
fun deviceLargeNotchPadding(isWide: Boolean = false): Dp {
    if (isWide) {
        return if (getNotch().hasNotch)  45.dp else 30.dp
    }
    return 0.dp
}


/**
 * True when there isn't much vertical room to work with -- e.g. a phone turned
 * landscape, where a full-height top/bottom app bar can crowd out everything else.
 * Mirrors Material's own compact-height window size class breakpoint (480dp).
 */
@Composable
fun isCompactHeight(): Boolean {
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current

    val heightDp = with(density) {
        windowInfo.containerSize.height.toDp()
    }

    return heightDp < 480.dp
}

/**
 * Custom Compose preview annotations that automatically generates previews for both light and dark themes.
 *
 * Each preview displays the UI with the system bars visible, uses the Pixel 8 Pro device configuration,
 * and renders the background to accurately reflect theme-specific colors and contrast.
 * This annotation simplifies the development workflow by combining common preview settings into
 * a single reusable declaration.
 *
 * Previews for a selection of popular devices are utilized:
 *   - Pixel 8
 *   - iPhone 16 (approximation)
 *   - iPhone 16 Landscape  (approximation)
 *   - Generic Tablet
 */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.FUNCTION)
@Preview(name = "Pixel 8 Light", showBackground = true,
    device =  Devices.PIXEL_8, showSystemUi = true, apiLevel = 36)
@Preview(name = "Pixel 8 Dark", showBackground = true,
    uiMode = UI_MODE_NIGHT_YES or UI_MODE_TYPE_NORMAL,
    device =  Devices.PIXEL_8, showSystemUi = true, apiLevel = 36)
annotation class PreviewLightDarkWithBackground

@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.FUNCTION)
@Preview(name = "Pixel 8 Landscape Light", showBackground = true,
    device = "spec:parent=pixel_8,orientation=landscape",
    showSystemUi = true, apiLevel = 36)
@Preview(name = "Pixel 8 Landscape Dark", showBackground = true,
    uiMode = UI_MODE_NIGHT_YES or UI_MODE_TYPE_NORMAL,
    device = "spec:parent=pixel_8,orientation=landscape",
    showSystemUi = true, apiLevel = 36)
annotation class PreviewLandscapeLightDarkWithBackground


@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.FUNCTION)
@Preview(name = "iPhone 16 Light", showBackground = true,
    device = "spec:width=393dp,height=852dp,dpi=460",
    showSystemUi = true, apiLevel = 36)
@Preview(name = "iPhone 16 Dark", showBackground = true,
    uiMode = UI_MODE_NIGHT_YES or UI_MODE_TYPE_NORMAL,
    device = "spec:width=393dp,height=852dp,dpi=460",
    showSystemUi = true, apiLevel = 36)
annotation class PreviewIPhoneLightDarkWithBackground

@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.FUNCTION)
@Preview(name = "iPhone 16 Landscape Light", showBackground = true,
    device = "spec:width=393dp,height=852dp,dpi=460,orientation=landscape",
    showSystemUi = true, apiLevel = 36)
@Preview(name = "iPhone 16 Landscape Dark", showBackground = true,
    uiMode = UI_MODE_NIGHT_YES or UI_MODE_TYPE_NORMAL,
    device = "spec:width=393dp,height=852dp,dpi=460,orientation=landscape",
    showSystemUi = true, apiLevel = 36)
annotation class PreviewIPhoneLandscapeLightDarkWithBackground


@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.FUNCTION)
@Preview(name = "Tablet Light", showBackground = true,
    showSystemUi = true,
    device =  Devices.TABLET, apiLevel = 36)
@Preview(name = "Tablet Dark", showBackground = true,
    uiMode = UI_MODE_NIGHT_YES or UI_MODE_TYPE_NORMAL,
    showSystemUi = true,
    device =  Devices.TABLET, apiLevel = 36)
annotation class PreviewTabletLightDarkWithBackground

/****************************************************
 * DEVICE Specifications                            *
 ****************************************************/


/** Composable function representing the "Test Finished" screen of the application.
  * Pixel 8 finished*
 * - `@PreviewLightDarkWithBackground` is used to generate previews for both light and dark modes.
 */
@PreviewLightDarkWithBackground
@Composable
fun ScreenTestRunning() {
    val sampleUiState: UiExecutionData = getSampleUiState(false)
    ScreenTestScaffold(uiState = sampleUiState, inputData = getSampleInputData(), isWide = false)
}

/**
 * Pixel 8 running
 * Composable function representing the "Test Finished" screen of the application.
 * - `@PreviewLightDarkWithBackground` is used to generate previews for both light and dark modes.
 */
@PreviewLightDarkWithBackground
@Composable
fun ScreenTestFinished() {
    val sampleUiState: UiExecutionData = getSampleUiState(true)
    ScreenTestScaffold(uiState = sampleUiState, inputData = getSampleInputData(), isWide = false)
}

/**
 *  iPhone 16 finished
 *  - `@PreviewIPhoneLightDarkWithBackground` previews for both light and dark modes.
 */
@PreviewIPhoneLightDarkWithBackground
@Composable
fun IPhone16() {
    val sampleUiState: UiExecutionData = getSampleUiState(false)
    ScreenTestScaffold(uiState = sampleUiState, inputData = getSampleInputData(), isWide = false)
}

/**
 * pixel 8 Landscape finished
 *  - `@PreviewLandscapeLightDarkWithBackground` previews for both light and dark modes.
 */
@PreviewLandscapeLightDarkWithBackground
@Composable
fun Pixel8Landscape() {
    val sampleUiState: UiExecutionData = getSampleUiState(false)
    ScreenTestScaffold(uiState = sampleUiState, inputData = getSampleInputData(), isWide = true)
}

/**
 * iPhone 16 Landscape Finished
 *  - `@PreviewIPhoneLandscapeLightDarkWithBackground` previews for both light and dark modes.
 */
@PreviewIPhoneLandscapeLightDarkWithBackground
@Composable
fun IPhone16Landscape() {
    val sampleUiState: UiExecutionData = getSampleUiState(false)
    ScreenTestScaffold(uiState = sampleUiState, inputData = getSampleInputData(), isWide = true)
}

/**
 *  - `@PreviewTabletLightDarkWithBackground` previews for both light and dark modes.
 */
@PreviewTabletLightDarkWithBackground
@Composable
fun ScreenTestTablet() {
    ScreenTestScaffold(getSampleUiState(true), inputData = getSampleInputData(), isWide = true)
}

/**
 * Composable function that initializes and displays the main screen layout for the Iperf3 Network Tester.
 * It applies the application theme, renders a full-screen Surface, and arranges the top bar,
 * bottom bar, and main content area using a Scaffold, passing the provided states to their respective components.
 *
 * @param uiState The current execution state holding test progress, logs, bandwidth data, and status flags.
 * @param inputData The configuration data for the network test, including host, port, duration, streams, and other parameters.
 */
@Composable
fun ScreenTestScaffold(uiState: UiExecutionData,
                       inputData: UiInputData,
                       isWide: Boolean = false)
{
    val elapsedTime = if (uiState.isFinished) 0.toDouble() else 4.40
    val notchPadding = deviceLargeNotchPadding(isWide = isWide)
    Iperf3NetworkTesterTheme(dynamicColor = true) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            /* Input rows */
            Scaffold(
                topBar = {
                    IperfTopBar(
                        currentRunning = uiState.isRunning,
                        hasHistory = uiState.isSaved,
                        isCompact = isWide,
                        buttonAction = {},
                        onHistoryClick = {})
                },
                bottomBar = { IperfBottomBar(inputData.isDebugging, elapsedTime = elapsedTime, preview = true, isWide = isWide) {} },
                modifier = Modifier.fillMaxWidth().padding(start = notchPadding, end = notchPadding)
                ) { padding ->
                ScreenBodyPreview(
                    padding = padding,
                    uiInputState = inputData,
                    uiExecutionState = uiState,
                    isWide = isWide,
                )
            }
        }
    }
}


/**
 * Renders the main content area of the screen, including input fields,
 * execution status, results, and debug outputs.
 *
 * @param padding Layout padding applied to the root column.
 * @param uiExecutionState Current state of the UI execution, including progress,
 *                         results, and running status. Defaults to a sample state.
 * @param uiInputState Current configuration of input parameters such as host,
 *                     port, duration, and stream settings.
 * @param updateHostName Callback invoked when the host name is updated.
 * @param uploadDownload Callback invoked for upload or download operations.
 * @param launch Callback invoked to initiate or launch the execution.
 * @param setDuration Callback invoked when the duration parameter is updated.
 * @param setPortNumber Callback invoked when the port number is updated.
 * @param setParallelStreams Callback invoked when the number of parallel streams is updated.
 * @param setSkip Callback invoked when the skip parameter is updated.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ScreenBodyPreview(padding: PaddingValues,
                      uiExecutionState: UiExecutionData, // = getSampleUiState(false),
                      uiInputState: UiInputData, // = getSampleInputData(),
                      updateHostName: (String) -> Unit = {},
                      uploadDownload: (String) -> Unit = {},
                      launch: () -> Unit = {},
                      setDuration: (String) -> Unit = {},
                      setPortNumber: (String) -> Unit = {},
                      setParallelStreams: (String) -> Unit = {},
                      setSkip: (String) -> Unit = {},
                      isWide: Boolean = false
)
{
    val monoStyle = mesloMonoTextStyle()
    val fieldColors = textFieldColors()
    Column(modifier = Modifier
        .padding(padding))
    {
        InputFields(
            currentHostName = uiInputState.hostName,
            currentPort = uiInputState.portNumber,
            currentDuration = uiInputState.durationSecs,
            currentParallelStreams = uiInputState.parallelStreams,
            currentSkip = uiInputState.skip,
            currentReverse = uiInputState.isReverse,
            isRunning = uiExecutionState.isRunning,
            updateHostName = updateHostName,
            uploadDownload = uploadDownload,
            isWide = isWide,
            launch = launch,
            setDuration = setDuration,
            setPortNumber = setPortNumber,
            setParallelStreams = setParallelStreams,
            setSkip = setSkip,
            colors = fieldColors,
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(10.dp))

        /* Output rows */
        RunningColumnSection(
            progress = 0.5.toFloat(),
            percentageComplete = 50,
            isRunning = uiExecutionState.isRunning,
            latestLine = uiExecutionState.latestLine,
            bandWidth = uiExecutionState.bandWidth,
            isReverse =  !uiInputState.isReverse,
            parallelStreams = uiInputState.parallelStreams,
            durationSecs = uiInputState.durationSecs,
            preview = true
        )

        ResultsRow(
            results = uiExecutionState.results,
            src = getSource(uiExecutionState.iperf3RunningState),
            dest = getDest(uiExecutionState.iperf3RunningState),
            returnCode = uiExecutionState.returnCode,
            isRunning = uiExecutionState.isRunning,
            isFinished = uiExecutionState.isFinished,
            monoStyle = monoStyle,
            isWide = isWideWindow(),
            isTall = isTallWindow()
        )
        ErrorSection(errorLines = uiExecutionState.errorLines,
            monoStyle = monoStyle,
            isWide = isWideWindow(),
            isTall = isTallWindow()
        )
        IperfMessagesSection(
            isDebugMode = uiInputState.isDebugging,
            isRunning = uiExecutionState.isRunning,
            isFinished = uiExecutionState.isFinished,
            latestLine = uiExecutionState.latestLine,
            errorLines = uiExecutionState.errorLines,
            iperfMessages = uiExecutionState.iperf3Messages,
            returnCode = uiExecutionState.returnCode,
            isTall = isTallWindow(),
            isWide = isWideWindow())
        DebugOutputSection(
            isDebugging = uiInputState.isDebugging,
            outputLines = uiExecutionState.outputLines,
            isOmitted = uiExecutionState.iperf3RunningState.omitted,
            isWide = isWideWindow(),
            isTall = isTallWindow()
        )
    }
}






