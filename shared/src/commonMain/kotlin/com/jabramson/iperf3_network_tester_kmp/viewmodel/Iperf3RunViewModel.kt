/*
 * Copyright (c) 2026, Jerold Abramson
 *
 * This source code is licensed under the BSD 3-Clause License found in
 * the LICENSE file in the root directory of this source tree.
 */

package com.jabramson.iperf3_network_tester_kmp.viewmodel


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jabramson.iperf3_network_tester_kmp.database.ResultDataEntity
import com.jabramson.iperf3_network_tester_kmp.database.ResultDataRepository
import com.jabramson.iperf3_network_tester_kmp.database.getResultDatabase
import com.jabramson.iperf3_network_tester_kmp.database.toEntity
import com.jabramson.iperf3_network_tester_kmp.model.Iperf3RunningState


import com.jabramson.iperf3_network_tester_kmp.getAppCurrentTimeMillis
import com.jabramson.iperf3_network_tester_kmp.utils.getAverage
import com.jabramson.iperf3_network_tester_kmp.utils.getMaximum
import com.jabramson.iperf3_network_tester_kmp.utils.getMedian
import com.jabramson.iperf3_network_tester_kmp.utils.getMinimum
import com.jabramson.iperf3_network_tester_kmp.utils.getSampleSize
import com.jabramson.iperf3_network_tester_kmp.utils.getStandardDeviation
import com.jabramson.iperf3_network_tester_kmp.runner.Iperf3Executor
import com.jabramson.iperf3_network_tester_kmp.utils.Log
import com.jabramson.iperf3_network_tester_kmp.utils.LogConfig
import com.jabramson.iperf3_network_tester_kmp.utils.formatTime
import com.jabramson.iperf3_network_tester_kmp.utils.resultsToCsv
import com.jabramson.iperf3_network_tester_kmp.utils.shareCsv
import com.jabramson.iperf3_network_tester_kmp.viewmodel.UploadDownload.isDownload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch


/**
 * Utility object for identifying and validating upload or download operation types.
 * Provides helper functions to check if a given string represents an upload, download, or either.
 */
object UploadDownload {
    const val UPLOAD = "Upload"
    const val DOWNLOAD = "Download"
    fun isDownload(s: String): Boolean = s.equals(DOWNLOAD, ignoreCase = true)
    fun isUpload(s: String): Boolean = s.equals(UPLOAD, ignoreCase = true)
    fun isUploadOrDownload(s: String): Boolean = isUpload(s) || isDownload(s)
}

/**
 * Contains default configuration parameters for iperf3 network testing execution.
 * These values act as fallback settings when user input is absent or invalid,
 * ensuring consistent initialization of test runs throughout the application.
 */
object DefaultInputData {
    // Default iperf3 server the UI pre-fills. Point this at your own iperf3 server
    // (`iperf server -s`) — `iperf.example.com` is a reserved, non-routable placeholder.
    const val HOST_NAME = "iperf.example.com"
    const val PORT_NUMBER = 5201
    const val PARALLEL_STREAMS = 8
    const val DURATION = 10
    const val SKIP = 2
    const val FORCE_FLUSH = true
    const val IS_REVERSE = true
    const val TIMEOUT = 3000L
    const val IS_DEBUGGING = false
}

/**
 * Encapsulates user-provided configuration parameters for executing an iperf3 network performance test.
 * This data class serves as the primary input model for the UI layer, aggregating target host details,
 * execution timing, concurrency settings, and behavioral flags required to initialize and control the testing workflow.
 */
data class UiInputData(
    val hostName: String = "",
    val portNumber: Int = -1,
    val durationSecs: Int = -1,
    val parallelStreams: Int = -1,
    val skip: Int = -1,
    val timeout: Long = -1,
    val isForceFlush: Boolean = DefaultInputData.FORCE_FLUSH,
    val isReverse: Boolean = DefaultInputData.IS_REVERSE,
    val isDebugging: Boolean = DefaultInputData.IS_DEBUGGING
)




/**
 * Data class to hold the UI state.
 * Notice that all variables are unchangeable `val` that are also mutable.
 * I also decided to store all numeric values as strings to avoid NumberFormatException
 * issues at odd times in the UI.
 */
data class UiExecutionData(
    val outputLines: MutableList<String> = emptyList<String>().toMutableList(),
    val errorLines: MutableList<String> = emptyList<String>().toMutableList(),
    val iperf3Messages: MutableList<String> = emptyList<String>().toMutableList(),
    val results: MutableList<String> = emptyList<String>().toMutableList(),
    val latestLine: String = "",
    val bandWidth: String = "",
    var isRunning: Boolean = false,
    val isVerbose: Boolean = false,
    var isFinished: Boolean = false,
    val isSaved: Boolean = false,
    val isSaving: Boolean = false,
    val returnCode: Int = 0,
    val lastLine: String = "",
    val resultNumber: Long = -1,
    val numberOfMessages: Int = 0,
    var iperf3RunningState: Iperf3RunningState =
        Iperf3RunningState(
            isReverse = DefaultInputData.IS_REVERSE,
            duration = DefaultInputData.DURATION,
            parallelStreams = DefaultInputData.PARALLEL_STREAMS,
            skip = DefaultInputData.SKIP,
        ),
    val testTime: Double = 0.toDouble(),
    val context: String = "", //Context? = null,
    val isDatabaseCreated: Boolean = false,
)

data class UiMicroProgressData(
    val progress: Float = 0f,
    val percentComplete: Long = 0,
    val elapsedTime: Double = 0.toDouble()
)

/**
 * Which top-level screen is currently visible. There's no navigation
 * library in this project (see CLAUDE.md) -- a single ViewModel-owned flow
 * is enough for the two screens this app has.
 */
enum class AppScreen {
    RunTest,
    History
}

/**
 * View model to Runs the native iperf3 binary extracted from
 * app assets to the app's private files' directory.
 *
 */
class Iperf3RunViewModel() : ViewModel() {

    val tag = "Iperf3RunViewModel"

    // State during execution of the iperf3 test
    private val _uiExecutionDataStateFlow = MutableStateFlow(UiExecutionData())
    val uiExecutionDataStateFlow: StateFlow<UiExecutionData> = _uiExecutionDataStateFlow.asStateFlow()

    // "microstate" lightweight container for ongoing progress
    private val _uiMicroProgressStateFlow = MutableStateFlow(UiMicroProgressData())
    val uiMicroProgressData: StateFlow<UiMicroProgressData> = _uiMicroProgressStateFlow.asStateFlow()


    // input state provided by user
    private val _uiInputDataStateFlow = MutableStateFlow(UiInputData())
    val uiInputDataStateFlow: StateFlow<UiInputData> = _uiInputDataStateFlow.asStateFlow()

    // Which top-level screen (Run Test vs. History) is currently shown.
    private val _currentScreenStateFlow = MutableStateFlow(AppScreen.RunTest)
    val currentScreenStateFlow: StateFlow<AppScreen> = _currentScreenStateFlow.asStateFlow()

    // True once the RoomDB has at least one saved result -- drives whether the
    // top bar's History button is shown at all (nothing to browse otherwise).
    private val _hasHistoryStateFlow = MutableStateFlow(false)
    val hasHistoryStateFlow: StateFlow<Boolean> = _hasHistoryStateFlow.asStateFlow()

    // Rows loaded for the History screen. Populated by showHistory(); left
    // stale (not cleared) when navigating away so re-opening History is instant.
    private val _historyResultsStateFlow = MutableStateFlow<List<ResultDataEntity>>(emptyList())
    val historyResultsStateFlow: StateFlow<List<ResultDataEntity>> = _historyResultsStateFlow.asStateFlow()

    // Constant values for the iperf3 execution flow
    private var iperf3Executor: Iperf3Executor = Iperf3Executor(
        updateProgress = ::updateProgress,       // floating point track of progress
        updateElapsedTime = ::updateElapsedTime, // test execution time
        stdout = ::saveOutputLine,               // output from iperf3 callback
        stderr = ::saveErrorLine,                // errors from iperf3 callback
        onTestComplete = { completeTest() }      // test complete callback
    )

    // The Job for the currently in-flight launch() -> runIperf3() coroutine, so
    // cancel() can actually stop it (see cancel() below) instead of just
    // flipping isRunning and leaving the coroutine to keep running orphaned.
    private var runJob: Job? = null

    // Lazy: the platform database (see database/Database.<platform>.kt) is
    // itself cached behind its own `by lazy` singleton, so first touching
    // this property builds it once; every call after that reuses the same
    // instance instead of recreating it. First access happens inside
    // init{}'s Dispatchers.Default launch below (the startup hasHistory
    // check), keeping the (blocking) Room setup off the main thread.
    //
    // NOTE: For Windows on Arm this might actually result in a null return, which is used
    // subsequently to disable DB operations
    private val resultDataRepository: ResultDataRepository? by lazy {
        val resultDataRepository = getResultDatabase()
        if (resultDataRepository != null) ResultDataRepository(resultDataRepository.resultDataEntityDao())
        else return@lazy null
    }

    /**
     * Initialize the UI state.
     */
    init {

        _uiMicroProgressStateFlow.update {
            it.copy(
                progress = 0.0.toFloat(),
                percentComplete = 0,
                elapsedTime = 0.toDouble()
            )
        }

        _uiInputDataStateFlow.update {
            it.copy(
                hostName = "",
                portNumber = -1,
                skip = -1,
                durationSecs = -1,
                parallelStreams = -1,
                timeout = -1,
                isForceFlush = DefaultInputData.FORCE_FLUSH,
                isReverse = DefaultInputData.IS_REVERSE,
                isDebugging = DefaultInputData.IS_DEBUGGING,
            )
        }
        _uiExecutionDataStateFlow.update {
            it.copy(
                outputLines = emptyList<String>().toMutableList(),
                errorLines = emptyList<String>().toMutableList(),
                iperf3Messages = emptyList<String>().toMutableList(),
                results = emptyList<String>().toMutableList(),
                latestLine = "",
                isRunning = false,
                isFinished = false,
                isVerbose = false,
                isSaved = false,
                isSaving = false,
                returnCode = 0,
                testTime = 0.toDouble(),
                lastLine = "",
                bandWidth = "",
                resultNumber = -1,
                numberOfMessages = 0,
                iperf3RunningState = Iperf3RunningState(), //.getCurrentLineResult(),
                isDatabaseCreated = false

            )
        }

        // Check for pre-existing rows (from a prior app session) so the
        // History button shows up immediately, not just after a fresh save.
        viewModelScope.launch(Dispatchers.Default) {
            refreshHasHistory()
        }
    }

    /**
     * Refresh [hasHistoryStateFlow] from the RoomDB row count. Safe to call
     * repeatedly -- e.g. after every save/delete -- since it's just a count().
     */
    private suspend fun refreshHasHistory() {
        try {
            _hasHistoryStateFlow.update {
                resultDataRepository!!.getCount() > 0
            }
        } catch (e: Exception) {
            // Leave hasHistory as-is; nothing sensible to show the user for a
            // background count failure, and saveResult()/showHistory() will
            // surface a real error if the DB is genuinely unreachable.
        }
    }

    /**
     * Navigate to the History screen and (re)load its contents. No-ops while
     * a test is running -- the top bar's History button is disabled for the
     * same reason (see IperfTopBar/HistoryButton), but this guard makes that
     * true regardless of how showHistory() gets called.
     */
    fun showHistory() {
        if (_uiExecutionDataStateFlow.value.isRunning) return
        _currentScreenStateFlow.update { AppScreen.History }
        viewModelScope.launch(Dispatchers.Default) {
            try {
                _historyResultsStateFlow.update { resultDataRepository!!.getAllResults() }
            } catch (e: Exception) {
                _uiExecutionDataStateFlow.update {
                    it.copy(errorLines = (it.errorLines + "Failed to load history: ${e.message}").toMutableList())
                }
            }
        }
    }

    /**
     * Navigate back from History to the main Run Test screen.
     */
    fun showRunScreen() {
        _currentScreenStateFlow.update { AppScreen.RunTest }
    }

    /**
     * Delete a single saved result (identified by its guid) and refresh the
     * History screen's contents, plus the top bar hasHistory flag.
     */
    fun deleteHistoryItem(guid: String) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                resultDataRepository!!.deleteById(guid)
                _historyResultsStateFlow.update { resultDataRepository!!.getAllResults() }
                refreshHasHistory()
            } catch (e: Exception) {
                _uiExecutionDataStateFlow.update {
                    it.copy(errorLines = (it.errorLines + "Failed to delete result: ${e.message}").toMutableList())
                }
            }
        }
    }

    /**
     * Export everything currently shown on the History screen as a CSV,
     * handed off to the platform's native Share sheet (Desktop: a Save File
     * dialog instead -- see shareCsv()'s doc). No coroutine needed: the data
     * is already in memory (historyResultsStateFlow), and presenting a
     * share sheet/file dialog must happen on the calling (UI) thread anyway.
     */
    fun exportHistoryCsv() {
        val results = _historyResultsStateFlow.value
        if (results.isEmpty()) return
        try {
            val filename = "iperf3-history-${getAppCurrentTimeMillis().value}.csv"
            shareCsv(filename, resultsToCsv(results))
        } catch (e: Exception) {
            _uiExecutionDataStateFlow.update {
                it.copy(errorLines = (it.errorLines + "Failed to export CSV: ${e.message}").toMutableList())
            }
        }
    }

    fun updateElapsedTime(time: String) {
        _uiMicroProgressStateFlow.update {
            it.copy(
                elapsedTime = time.toDouble()
            )
        }
    }

    /**
     * Callback to save an output line from the iperf3 binary.
     * @param iperf3RunningState The output line from the process execution.
     */
    fun saveOutputLine(iperf3RunningState: Iperf3RunningState, newMessage: Boolean = false) {
        val lastMessages = iperf3RunningState.messages.toMutableList()

        if (newMessage) {
            _uiExecutionDataStateFlow.update {
                it.copy(
                    iperf3Messages = if (iperf3RunningState.messages.isNotEmpty())
                        (iperf3RunningState.messages).toMutableList()
                    else it.iperf3Messages,
                    numberOfMessages = lastMessages.size,
                    iperf3RunningState = iperf3RunningState
                )
            }
        } else {
            _uiExecutionDataStateFlow.update { it ->
                it.copy(
                    lastLine = it.latestLine,
                    bandWidth = iperf3RunningState.basicBandWidthString,
                    latestLine = iperf3RunningState.formattedOutputLine,
                    outputLines = if (iperf3RunningState.formattedOutputLine.isNotEmpty())
                        (it.outputLines + iperf3RunningState.formattedOutputLine).toMutableList()
                    else it.outputLines,
                    resultNumber = iperf3RunningState.intervalNumber,
                    iperf3Messages = it.iperf3Messages.toMutableList(),
                    iperf3RunningState = iperf3RunningState
                )
            }
        }
    }

    /**
     * Callback to save an error line from the iperf3 binary.
     * @param aLine The error line from the process execution.
     */
    fun saveErrorLine(iperf3RunningState: Iperf3RunningState, aLine: String = "") {
        Log.d(tag, "stderr: $aLine")
        _uiExecutionDataStateFlow.update { data ->
            data.copy(
                errorLines =
                    if (data.errorLines.isNotEmpty()) (data.errorLines + aLine).toMutableList()
                    else (emptyList<String>().toMutableList() + aLine).toMutableList(),
                iperf3RunningState = iperf3RunningState,
                latestLine = iperf3RunningState.rawOutputLine
            )
        }
    }

    /**
     * Launch or cancel the iperf3 test, dependending on the current execution
     * state.
     */
    fun launchOrCancel() {
        if (!_uiExecutionDataStateFlow.value.isRunning) launch() else cancel()
    }

    /**
     * Save/persist the result of the test to a RoomDB database (backed by sqlite).
     * Called automatically once a test finishes successfully (see runIperf3())
     * -- there is no user-facing Save button anymore. Safe to call more than
     * once per run: no-ops if already saved, or if there's nothing meaningful
     * to save yet (e.g. a run with zero samples).
     * See CLAUDE.md's "Room + KMP SQLite persistence" section for the
     * complex requirements to support roomDB on KMP for all required platforms.
     */
    fun saveResult(time: Double) {
        if (_uiExecutionDataStateFlow.value.isSaved) return
        viewModelScope.launch(Dispatchers.Default) {
            if (resultDataRepository != null) {
                if (_uiExecutionDataStateFlow.value.iperf3RunningState.totalSamples > 0) {
                    val current =
                        toEntity(_uiExecutionDataStateFlow.value.iperf3RunningState, time)
                    if (current.reportedIterations > 0) {
                        _uiExecutionDataStateFlow.update {
                            it.copy(isSaving = true)
                        }
                        try {
                            resultDataRepository!!.insert(current)
                            _uiExecutionDataStateFlow.update {
                                it.copy(isSaved = true, isSaving = false, isDatabaseCreated = true)
                            }
                            refreshHasHistory()
                        } catch (e: Exception) {
                            _uiExecutionDataStateFlow.update {
                                it.copy(
                                    isSaving = false,
                                    errorLines = (it.errorLines + "Failed to save result: ${e.message}").toMutableList()
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Launch the iperf3 binary (notice that this is an asynchronous operation).
     */
    fun launch() {
        // Belt-and-suspenders: launchOrCancel()'s isRunning check (above) is
        // what normally prevents a second launch() while one is in flight,
        // but that's an indirect invariant relying on state staying in sync.
        // Cancelling any stale job here too means launch() can never leave
        // two runJob coroutines alive, even if that guard is ever bypassed.
        runJob?.cancel()

        _uiInputDataStateFlow.update {
            it.copy(
                // Ensure the user can see selections during the run
                hostName = if (it.hostName.trim().isEmpty()) DefaultInputData.HOST_NAME else it.hostName,
                portNumber = if (it.portNumber == -1) DefaultInputData.PORT_NUMBER else it.portNumber,
                durationSecs = if (it.durationSecs == -1) DefaultInputData.DURATION else it.durationSecs,
                parallelStreams = if (it.parallelStreams == -1) DefaultInputData.PARALLEL_STREAMS else it.parallelStreams,
                skip = if (it.skip == -1) DefaultInputData.SKIP else it.skip,
                timeout = if (it.timeout == -1L) DefaultInputData.TIMEOUT else it.timeout,
                isForceFlush = it.isForceFlush,
                isReverse = it.isReverse,
                isDebugging = it.isDebugging
            )
        }

        // Update the UI active running state with empty values for the start of the test.
        _uiExecutionDataStateFlow.update { it ->
            it.copy(
                isRunning = true,
                isFinished = false,
                outputLines = emptyList<String>().toMutableList(),
                errorLines = emptyList<String>().toMutableList(),
                iperf3Messages = emptyList<String>().toMutableList(),
                results = emptyList<String>().toMutableList(),
                bandWidth = "",
                testTime = 0.toDouble(),
                latestLine = "",
                lastLine = "",
                resultNumber = -1,
                returnCode = 0,
                isSaved = false,
                isSaving = false
            )
        }

        // Update the microstate at start of test
        _uiMicroProgressStateFlow.update {
            it.copy(
                progress = 0.0.toFloat(),
                percentComplete = 0,
                elapsedTime = 0.toDouble()
            )
        }
        /*
       * Launch the iperf3 binary asynchronously.
       * I decided to wrap the async launch in a separate function
       * to make the code more explicit.
       */
        Log.d(tag, "Async Launch Started")

        runJob = viewModelScope.launch { runIperf3() }

        Log.d(tag, "Async Launch Completed")
    }




    /**
     * Return a valid number or reset to base  state if it is
     * either empty or an invalid number.
     * @param s The string to convert to an integer.
     * @return The integer value of the string, or -1 if invalid or empty.
     */
    fun myInt(s: String, o: Int): Int = if (s.isEmpty() || s.equals("0")) { -1 } else { try { s.toInt() } catch (e: Exception) { o } }

    /**
     * Cancel the running test. It is important to give the running iperf3
     * threads the mechanism to exit cleanly, but telling the remote iperf3
     * server that the connection should be closed.
     * Otherwise, the server stays in the busy state for an indeterminate duration.
     */
    fun cancel() {
        Log.d(tag, "Async cancel Started")
        // Actually stop the in-flight launch() coroutine -- without this, a
        // fast Run->Cancel->Run could leave the first run's coroutine alive
        // in the background, racing a second run on the shared executor.
        runJob?.cancel()
        _uiExecutionDataStateFlow.update {
            it.copy(
                returnCode = -1,
                isRunning = false,
                isFinished = true
            )
        }
        completeTest()
        Log.d(tag, "Async Cancel Started")
        viewModelScope.launch {cancelTest() }
        Log.d(tag, "Async Cancel Completed")
    }

    /**
     * Cancel a running test using an asynchronous request.
     * @return the return code from the cancel request (not the iperf3 test itself)
     */
    fun cancelTest(): Int {
        var rc: Int
        try {
            rc = iperf3Executor.cancelTest()
        } catch (e: Exception) {
            /* Shouldn't ever get here, since guards are already in place */
            Log.e(tag, "Failed to cancel iperf3: ${e.message}", e)
            saveErrorLine(_uiExecutionDataStateFlow.value.iperf3RunningState, "Failed to cancel iperf3: ${e.message}")
            rc = -1
        }
        return rc
    }

    /**
     * Run the iperf3 binary.
     * This must be a suspend function called from a coroutine.
     * @return The return code from the iperf3 library execution.
     */
    suspend fun runIperf3(): Int {

        val rc = iperf3Executor.startTest(params = _uiInputDataStateFlow.value)


        val testTime = _uiExecutionDataStateFlow.value.testTime
        //Update the UI state to show that the test is finished and
        // Provide the return code to the UI.
        val outputCount = _uiExecutionDataStateFlow.value.iperf3RunningState.intervalNumber
        if (outputCount >= 0) {
            val time = "   Time:   ${formatTime(testTime)}"
            val exe = getSampleSize(_uiExecutionDataStateFlow.value.iperf3RunningState)
            val max = getMaximum(_uiExecutionDataStateFlow.value.iperf3RunningState)
            val min = getMinimum(_uiExecutionDataStateFlow.value.iperf3RunningState)
            val avg = getAverage(_uiExecutionDataStateFlow.value.iperf3RunningState)
            val med = getMedian(_uiExecutionDataStateFlow.value.iperf3RunningState)
            val std = getStandardDeviation(_uiExecutionDataStateFlow.value.iperf3RunningState)
            val newResults = listOf(time, exe, max, min, avg, med, /*std*/).filter { it.isNotEmpty() }
            if (newResults.isNotEmpty()) {
                _uiExecutionDataStateFlow.update { it.copy(results = (it.results + newResults).toMutableList()) }
            }
        } else if (rc == 0) {
            _uiExecutionDataStateFlow.update { it.copy(results = (it.results + "No Results").toMutableList()) }
        }


        // Provide the return code to the UI.
        _uiExecutionDataStateFlow.update {
            it.copy(
                returnCode = rc,
                isRunning = false,
                isFinished = true,
            )
        }
        // Persist automatically on a successful run -- no Save button anymore.
        // saveResult() itself no-ops if there were no real samples to save.
        if (rc == 0) {
            saveResult(testTime)
        }
        completeTest()
        return rc
    }

    fun completeTest() {
        _uiInputDataStateFlow.update {
            it.copy(
                hostName = if (it.hostName != DefaultInputData.HOST_NAME) it.hostName else "",
                skip = if (it.skip != DefaultInputData.SKIP) it.skip else -1,
                durationSecs = if (it.durationSecs != DefaultInputData.DURATION) it.durationSecs else -1,
                parallelStreams = if (it.parallelStreams != DefaultInputData.PARALLEL_STREAMS) it.parallelStreams else -1,
                portNumber = if (it.portNumber != DefaultInputData.PORT_NUMBER) it.portNumber else -1,
                isForceFlush = it.isForceFlush,
            )
        }


        // The elapsed-time row (ProjectBottomBar) is only shown while
        // elapsedTime != 0 -- clear it here so it disappears once the test
        // is done, whether it finished normally or was canceled (cancel()
        // also calls completeTest()).
        _uiExecutionDataStateFlow.update {
            it.copy(testTime = _uiMicroProgressStateFlow.value.elapsedTime)
        }

        _uiMicroProgressStateFlow.update {
            it.copy(elapsedTime = 0.toDouble())
        }
    }

    //
    /**
     * Callback to update the progress bar.
     * @param newProgress The new progress value.
     *
     * We implement the progress bar as a floating point value between 0.0 and 1.0.
     * If uploading, the progress bar goes from 0.0 to 1.0 [left to right]
     * If downloading, the progress bar goes from 1.0 to 0.0 [right to left]
     */
    fun updateProgress(newProgress: Float, percentComplete: Long) {
        val normalizedProgress = if (!_uiInputDataStateFlow.value.isReverse) newProgress else 1.0f - newProgress
        _uiMicroProgressStateFlow.update {
            it.copy(
                progress = normalizedProgress,
                percentComplete = percentComplete
            )
        }
    }

    /**
     * User entered a new host name.
     * @param hostName The new host name.
     */
    fun setHostName(hostName: String) {
        if (hostName.trim().isNotEmpty()) {
            _uiInputDataStateFlow.update {
                it.copy(
                    hostName = hostName.trim(),
                )
            }
        } else {
            // Reset to base state if user clears field
            _uiInputDataStateFlow.update {
                it.copy(hostName = "")
            }
        }
    }

    /**
     * User entered a new duration.
     * Notice that we do not allow changes to the user interface
     * if the resulting number is invalid.
     * @param portNumber new port number
     */
    fun setPortNumber(portNumber: String) {
        _uiInputDataStateFlow.update {
            it.copy(portNumber = myInt(portNumber, _uiInputDataStateFlow.value.portNumber))
        }
    }

    /**
     * User entered a new duration.
     * Notice that we do not allow changes to the user interface
     * if the resulting number is invalid.
     * @param duration The new value for duration.
     */
    fun setDuration(duration: String) {
        _uiInputDataStateFlow.update {
            it.copy(durationSecs = myInt(duration, _uiInputDataStateFlow.value.durationSecs))
        }
    }

    /**
     * The user entered a new value for the omitted field.
     * Notice that we do not allow changes to the user interface
     * if the resulting number is invalid.
     * @param skip The new value for omitted.
     */
    fun setSkip(skip: String) {
        // 0 is a valid skip choice
        if (skip == "0") {
            _uiInputDataStateFlow.update {
                it.copy(skip = 0)
            }
        } else {
            _uiInputDataStateFlow.update {
                it.copy(skip = myInt(skip, _uiInputDataStateFlow.value.skip))
            }
        }
    }


    /**
     * User entered a new value for parallel streams.
     * Notice that we do not allow changes to the user interface
     * if the resulting number is invalid.
     * @param str The new value for parallel streams.
     */
    fun setParallelStreams(str: String) {
        _uiInputDataStateFlow.update {
            it.copy(parallelStreams = myInt(str, _uiInputDataStateFlow.value.parallelStreams))
        }
    }

    /**
     * User chose the radio button for upload/download.
     * @param str The new value for upload/download.
     */
    fun setUploadDownload(str: String) {
        _uiInputDataStateFlow.update {
            it.copy(isReverse = isDownload(str))
        }
    }


    /**
     * User toggled forceflush. (not currently implemented)
     * @param forceFlush The new value for forceflush.
     */
    fun setForceFlush(forceFlush: Boolean) {
        _uiInputDataStateFlow.update {
            it.copy(isForceFlush = forceFlush)
        }
    }

    /**
     * Called at the start of the composable view.
     * We No longer use this technique with KMP+roomDB+sqlite.
     *
     * Room's Android driver gets its Context from AndroidAppContextHolder
     * instead (set once in MainActivity.onCreate()) -- this app has no DI
     * container to lean on. See CLAUDE.md's "Room + KMP SQLite persistence".
     */
    fun setContext(context: String) {  //Context) {
        _uiExecutionDataStateFlow.update {
            it.copy(context = context)
        }
    }



    /**
     * User pressed the 'show iperf3 output' button. Purely a UI concern --
     * shows/hides DebugOutputSection's raw-output panel. NOT the same thing
     * as developer-only system logging (see onAppTitleTapped() below) --
     * those used to be wired to this same button, but that conflated a
     * legitimate end-user feature (seeing raw output) with a diagnostic one
     * (logcat/NSLog/stderr output nobody but a developer would ever look
     * at), so they're separate controls now.
     */
    fun toggleDebug() {
        val newState = !_uiInputDataStateFlow.value.isDebugging
        _uiInputDataStateFlow.update {
            it.copy(isDebugging = newState)
        }
    }

    // Hidden developer-mode unlock: tap the app title (IperfTopBar) this
    // many times, within lastTitleTapTime's window, to flip developer
    // logging on/off -- mirrors Android's own "tap the build number 7
    // times" Developer Options convention. Deliberately not part of
    // UiInputData/UiExecutionData: this has no business being observed by
    // the UI beyond the one-shot confirmation message below, and doesn't
    // need to survive process death the way real test state does.
    private var titleTapCount = 0
    private var lastTitleTapTime = 0L

    /**
     * Call on every tap of the app title. No-ops until [TITLE_TAPS_TO_UNLOCK]
     * taps land within [TITLE_TAP_WINDOW_MS] of each other, at which point it
     * flips developer logging (native IPERF_LOG + the Kotlin Log utility) on
     * or off and surfaces a one-line confirmation the same way other
     * background-operation failures already do (errorLines) -- there's no
     * dedicated toast/snackbar mechanism in this app, and this is rare/minor
     * enough not to warrant adding one just for this.
     */
    fun onAppTitleTapped() {
        val now = getAppCurrentTimeMillis().value
        if (now - lastTitleTapTime > TITLE_TAP_WINDOW_MS) {
            titleTapCount = 0
        }
        lastTitleTapTime = now
        titleTapCount++

        if (titleTapCount >= TITLE_TAPS_TO_UNLOCK) {
            titleTapCount = 0
            val newState = !LogConfig.developerLoggingEnabled
            LogConfig.developerLoggingEnabled = newState
            iperf3Executor.setDebug(newState)
            val message = "🛠 Developer logging ${if (newState) "enabled" else "disabled"}"
            Log.i(tag, message)
            _uiExecutionDataStateFlow.update {
                it.copy(errorLines = (it.errorLines + message).toMutableList())
            }
        }
    }

    private companion object {
        const val TITLE_TAPS_TO_UNLOCK = 7
        const val TITLE_TAP_WINDOW_MS = 1500L
    }
}








