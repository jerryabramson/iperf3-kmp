package com.jabramson.iperf3_network_tester_kmp.view

//import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.WavyProgressIndicatorDefaults.trackColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.jabramson.iperf3_network_tester_kmp.model.Iperf3RunningState
import com.jabramson.iperf3_network_tester_kmp.utils.UnitConvertedData
import com.jabramson.iperf3_network_tester_kmp.utils.toWholeNumber
import com.jabramson.iperf3_network_tester_kmp.viewmodel.UiExecutionData
import kotlinx.coroutines.NonCancellable.isActive


@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "Running Column Section", showBackground = true,
    showSystemUi = true,
    device = "spec:width=411dp,height=891dp"
)
@Composable
fun RunningColumnSection(
    progress: Float = 0.5.toFloat(),
    percentageComplete: Long = 50,

    isReverse: Boolean = getSampleInputData().isReverse,
    parallelStreams: Int = getSampleInputData().parallelStreams,
    durationSecs: Int = getSampleInputData().durationSecs,

    interval: Long = getSampleUiState(true).iperf3RunningState.intervalNumber,
    isRunning: Boolean = getSampleUiState(true).isRunning,
    latestLine: String = getSampleUiState(true).latestLine,
    bandWidth: String = getSampleUiState(true).bandWidth,
    isOmitted: Boolean = getSampleUiState(true).iperf3RunningState.omitted,
    max: UnitConvertedData= getSampleUiState(true).iperf3RunningState.currentMax,
    min: UnitConvertedData = getSampleUiState(true).iperf3RunningState.currentMin,
    avg: UnitConvertedData = getSampleUiState(true).iperf3RunningState.currentAvg,
    preview: Boolean = true)
{
    if (!isRunning) return

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
        LaunchingMessage(latestLine.isEmpty())
        ProgressPercent(
            isOmitted = isOmitted, isReverse = isReverse,
            newLine =  latestLine.isNotEmpty(),
            parallelStreams = parallelStreams, durationSecs = durationSecs,
            percentageComplete = percentageComplete,
            progress = progress, intervalNumber = interval,
            bandWidth = bandWidth,
            max = max, min = min, avg = avg, preview = preview
        )
    }
}


@Composable
private fun LaunchingMessage(show: Boolean) {
    if (show) {
        Text("Launching iperf3 ...",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            fontSize = 18.sp,
        )
    }
}


@Composable
fun progressColors(isReverse: Boolean): Pair<androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color> {
    return if (!isReverse) {
        MaterialTheme.colorScheme.onErrorContainer to MaterialTheme.colorScheme.surfaceDim
    } else {
        MaterialTheme.colorScheme.surfaceDim to MaterialTheme.colorScheme.primary
    }
}

@Composable
fun ProgressMicroBar(progress: Float = 0.5.toFloat(),
                     isReverse: Boolean = getSampleInputData().isReverse)
{
    val (barColor, trackColor) = progressColors(isReverse)

    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier.fillMaxWidth().height(4.dp),
        color = barColor,
        trackColor = trackColor,
        strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
        gapSize = 5.dp

    )

}

@Composable
fun ProgressPercent(
    progress: Float = 0.5.toFloat(),
    newLine: Boolean = false,
    percentageComplete: Long = 50,

    isReverse: Boolean = getSampleInputData().isReverse,
    parallelStreams: Int = getSampleInputData().parallelStreams,
    durationSecs: Int = getSampleInputData().durationSecs,

    intervalNumber: Long = getSampleUiState(true).iperf3RunningState.intervalNumber,
    isOmitted: Boolean = getSampleUiState(true).iperf3RunningState.omitted,
    bandWidth: String = getSampleUiState(true).bandWidth,
    max: UnitConvertedData= getSampleUiState(true).iperf3RunningState.currentMax,
    min: UnitConvertedData = getSampleUiState(true).iperf3RunningState.currentMin,
    avg: UnitConvertedData = getSampleUiState(true).iperf3RunningState.currentAvg,
    preview: Boolean = true
) {
    if (!newLine) return
    if (!isOmitted) {
        if (intervalNumber >= 0) {
            val iter = "iteration $intervalNumber of $durationSecs"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "${percentageComplete}% complete  ", modifier = Modifier.padding(end = 2.dp))
                Text(
                    text = iter,
                    modifier = Modifier.padding(end = 3.dp)
                )
                Text(
                    text = " $parallelStreams streams",
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(end = 3.dp)
                )
            }
        }
        // Keep the linear progress bar updated 'in-between' full steps
        ProgressMicroBar(progress, isReverse)
        BandwidthDisplay(omittedResult = isOmitted, min = min, max = max, avg = avg, preview = preview, basicBandWidthString = bandWidth)
    } else {
        Column(modifier = Modifier.fillMaxWidth().padding(start=10.dp, end =  10.dp)) {
            if (bandWidth.isNotEmpty()) {
                Text(
                    text = bandWidth,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    color = MaterialTheme.colorScheme.error
                )
            }
            CircularProgressIndicator(
                strokeWidth = 4.dp,
                modifier = Modifier.align(Alignment.CenterHorizontally).size(20.dp),
            )
        }
    }
}

@Composable
fun BandwidthDisplay(omittedResult: Boolean = false,
                     stalledResult: Boolean = false,
                     basicBandWidthString: String = "",
                     max: UnitConvertedData,
                     min: UnitConvertedData,
                     avg: UnitConvertedData,
                     preview: Boolean = false)
{

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val color =
            if (!omittedResult && !stalledResult) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
        Text(
            text = basicBandWidthString,
            color = color,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.titleLargeEmphasized
        )

        HorizontalDivider(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 2.dp, end = 2.dp),
            thickness = 2.dp,
            color = MaterialTheme.colorScheme.primary
        )


        //val med = uiState.lineResult.currentMedian
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 2.dp, end = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Min",
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                text = "Avg",
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                text = "Max",
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.labelMedium
            )

        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 2.dp, end = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = toWholeNumber(min, preview).trim(),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = toWholeNumber(avg, preview),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = toWholeNumber(max, preview),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyLarge
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
    }
}



