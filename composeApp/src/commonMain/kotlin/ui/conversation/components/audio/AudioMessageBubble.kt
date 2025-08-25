package ui.conversation.components.audio

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import augmy.composeapp.generated.resources.Res
import augmy.composeapp.generated.resources.accessibility_pause
import augmy.composeapp.generated.resources.accessibility_play
import augmy.interactive.shared.ui.components.MinimalisticIcon
import augmy.interactive.shared.ui.theme.LocalTheme
import augmy.interactive.shared.utils.DateUtils
import base.utils.audio.rememberAudioPlayer
import base.utils.orZero
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import ui.conversation.components.experimental.robotalk.RobotalkVisualization
import kotlin.math.ceil

enum class AudioMessageState {
    Loading,
    Playing,
    Paused
}

/**
 * Message bubble that has embedded player and is automatically downloaded and cached via [url].
 */
@Composable
fun AudioMessageBubble(
    modifier: Modifier = Modifier,
    url: String,
    tintColor: Color
) {
    val processorModel: MediaProcessorModel = koinViewModel(key = url)
    val cancellableCoroutineScope = rememberCoroutineScope()

    val resultByteArray = processorModel.resultByteArray.collectAsState()

    val waveformHeight = remember { 50.dp }
    val totalLengthMs = rememberUpdatedState(
        calculateAudioLength(
            byteArraySize = resultByteArray.value?.size.orZero(),
            sampleRate = 44100, // get info from .wav once we support multi channel etc.
            bytesPerSample = 2,
            channels = 1
        )
    )
    val startTime = rememberSaveable { mutableLongStateOf(0L) }
    val millisecondsElapsed = remember {
        mutableLongStateOf(0L)
    }
    val isPlaying = remember {
        mutableStateOf(false)
    }
    val player = rememberAudioPlayer(
        byteArray = resultByteArray.value ?: ByteArray(0),
        secondsPerBar = 0.1,
        barsCount = 5,
        onFinish = {
            startTime.longValue = 0L
            millisecondsElapsed.longValue = 0L
            isPlaying.value = false
        }
    )
    val startPlaying = {
        startTime.value = Clock.System.now().toEpochMilliseconds() - millisecondsElapsed.longValue
        isPlaying.value = true
        player.play()
    }
    val stopPlaying = {
        startTime.longValue = 0L
        millisecondsElapsed.longValue = 0L
        isPlaying.value = false
        player.pause()
    }

    LaunchedEffect(url) {
        processorModel.downloadAudioByteArray(url)
    }
    LaunchedEffect(isPlaying.value) {
        if (isPlaying.value) {
            cancellableCoroutineScope.coroutineContext.cancelChildren()
            cancellableCoroutineScope.launch {
                while (isPlaying.value) {
                    millisecondsElapsed.longValue += Clock.System.now().toEpochMilliseconds() - startTime.longValue - millisecondsElapsed.longValue
                    delay(100L)
                }
            }
        }
    }

    Row(
        modifier = modifier
            .padding(top = 10.dp, bottom = 0.dp, start = 12.dp, end = 16.dp)
            .height(waveformHeight)
            .wrapContentWidth()
            .animateContentSize(),
        horizontalArrangement = Arrangement.spacedBy(
            space = 4.dp,
            alignment = Alignment.CenterHorizontally
        ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            modifier = Modifier.animateContentSize(),
            text = "${DateUtils.formatMillis(millisecondsElapsed.longValue).takeIf {
                it != "0s"
            }?.plus("/") ?: ""}${DateUtils.formatMillis(totalLengthMs.value)}",
            style = LocalTheme.current.styles.regular.copy(color = tintColor)
        )
        RobotalkVisualization(
            modifier = Modifier
                .align(Alignment.Bottom)
                .padding(horizontal = 16.dp)
                .size(waveformHeight - 16.dp),
            amplitudes = player.barPeakAmplitudes.value.takeIf { isPlaying.value }.orEmpty(),
            median = player.peakMedian.value.takeIf { isPlaying.value }.orZero()
        )
        player.barPeakAmplitudes.value.takeLast(player.barsCount).forEach { bar ->
            Box(
                modifier = Modifier
                    .heightIn(min = 6.dp, max = waveformHeight)
                    .width(4.dp)
                    .background(
                        color = tintColor,
                        shape = RoundedCornerShape(4.dp)
                    )
                    .height((bar.first / player.peakMedian.value * waveformHeight.value).dp)
                    .animateContentSize()
            )
        }
        Crossfade(
            targetState = when {
                isPlaying.value -> AudioMessageState.Playing
                resultByteArray.value == null -> AudioMessageState.Loading
                else -> AudioMessageState.Paused
            }
        ) { state ->
            when(state) {
                AudioMessageState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.requiredSize(24.dp),
                        color = LocalTheme.current.colors.brandMainDark,
                        trackColor = LocalTheme.current.colors.tetrial
                    )
                }
                else -> {
                    MinimalisticIcon(
                        modifier = Modifier.padding(start = 4.dp),
                        imageVector = if(state == AudioMessageState.Playing) Icons.Outlined.Stop else Icons.Outlined.PlayArrow,
                        tint = tintColor,
                        contentDescription = stringResource(
                            if(state == AudioMessageState.Playing) Res.string.accessibility_pause else Res.string.accessibility_play
                        ),
                        onTap = {
                            if(isPlaying.value) {
                                stopPlaying()
                            }else startPlaying()
                        }
                    )
                }
            }
        }
    }
}

private fun calculateAudioLength(byteArraySize: Int, sampleRate: Int, bytesPerSample: Int, channels: Int): Long {
    val lengthInSeconds = byteArraySize.toDouble() / (sampleRate * bytesPerSample * channels) * 1000L
    return ceil(lengthInSeconds).toLong()
}
