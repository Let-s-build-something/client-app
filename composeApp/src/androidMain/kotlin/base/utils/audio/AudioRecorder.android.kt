package base.utils.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import base.utils.orZero
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

/** Automatically disposed audio recorder */
@SuppressLint("MissingPermission")
@Composable
actual fun rememberAudioRecorder(
    barsCount: Int,
    sampleRate: Int,
    secondsPerBar: Double,
    bufferSize: Int
): AudioRecorder {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val audioRecorder = remember {
        object: AudioRecorder(
            sampleRate = sampleRate,
            secondsPerBar = secondsPerBar,
            barsCount = barsCount
        ) {
            // audio recording
            private var audioRecord: AudioRecord? = null
            private var buffer: ByteArrayOutputStream? = null
            private var recordingThread: Thread? = null

            // bar creation
            private var barBuffer: ByteArrayOutputStream? = null
            private val channelConfig = AudioFormat.CHANNEL_IN_MONO
            private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

            override fun startRecording() {
                val effectiveBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
                    .coerceAtLeast(bufferSize)

                if(audioRecord == null) {
                    stopRecording()

                    audioRecord = AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        sampleRate,
                        channelConfig,
                        audioFormat,
                        effectiveBufferSize
                    )
                    buffer = ByteArrayOutputStream()
                    barBuffer = ByteArrayOutputStream()
                }
                recordingThread = Thread {
                    val threadBuffer = ByteArray(effectiveBufferSize)

                    while (recordingThread != null) {
                        val readResult = audioRecord?.read(threadBuffer, 0, threadBuffer.size).orZero()

                        if (readResult > 0) {
                            buffer?.write(threadBuffer, 0, readResult)
                            barBuffer?.write(threadBuffer, 0, readResult)
                            if(barBuffer?.size().orZero() >= bytesPerBar) {
                                val byteArray = barBuffer?.toByteArray()
                                coroutineScope.launch {
                                    processChunk(byteArray = byteArray)
                                }
                                barBuffer?.reset()
                            }
                        }
                    }
                }
                recordingThread?.start()

                audioRecord?.startRecording()
            }

            override suspend fun saveRecording(): ByteArray? {
                return pcmToWav(
                    pcmData = buffer?.toByteArray(),
                    channels = 1,
                    bitsPerSample = 16
                )
            }

            override fun pauseRecording() {
                audioRecord?.stop()
                recordingThread?.interrupt()
                recordingThread = null
            }

            override fun stopRecording() {
                super.stopRecording()
                buffer?.flush()
                audioRecord?.stop()
                audioRecord?.release()
                recordingThread?.interrupt()
                recordingThread = null
                audioRecord = null
                buffer = null
                barBuffer = null
            }
        }
    }

    DisposableEffect(context) {
        onDispose {
            audioRecorder.stopRecording()
        }
    }

    return audioRecorder
}
