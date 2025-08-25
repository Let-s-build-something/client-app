package base.utils.audio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import base.utils.orZero
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.ShortVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocPointerTo
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okio.Buffer
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioPCMBuffer
import platform.AVFAudio.AVAudioPCMFormatFloat32
import platform.AVFAudio.AVAudioPCMFormatInt16
import platform.AVFAudio.AVAudioPlayerNode
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryOptionAllowBluetooth
import platform.AVFAudio.AVAudioSessionCategoryOptionMixWithOthers
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioSessionModeSpokenAudio
import platform.AVFAudio.availableInputs
import platform.AVFAudio.setActive
import platform.AVFAudio.setPreferredSampleRate
import platform.Foundation.NSError
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_sync
import platform.posix.memcpy

/**
 * Automatically disposed audio player
 * @param secondsPerBar how many seconds should be represented by a single bar
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberAudioPlayer(
    byteArray: ByteArray,
    onFinish: () -> Unit,
    barsCount: Int,
    sampleRate: Int,
    secondsPerBar: Double,
    bufferSize: Int
): AudioPlayer {
    return remember(byteArray.size) {
        val scope = CoroutineScope(Job() + Dispatchers.IO)

        object : AudioPlayer(
            byteArray = byteArray,
            barsCount = barsCount,
            secondsPerBar = secondsPerBar,
            bufferSize = bufferSize,
            scope = scope
        ) {
            private var engine: AVAudioEngine? = null
            private var playerNode: AVAudioPlayerNode? = null
            private var barBuffer: Buffer? = null
            private var playedBytes = 0
            private var format = AVAudioFormat(
                AVAudioPCMFormatInt16,
                sampleRate.toDouble(),
                1u,
                false
            )

            override fun play() {
                scope.launch {
                    if (engine == null) {
                        playedBytes = 0
                        engine = AVAudioEngine()
                        barBuffer = Buffer()
                        playerNode = AVAudioPlayerNode().also {
                            engine?.attachNode(it)
                            engine?.mainMixerNode?.let { mixer ->
                                format = mixer.outputFormatForBus(0u)
                                engine?.connect(it, mixer, format)
                            }
                        }
                        AVAudioSession.sharedInstance().apply {
                            if(this.availableInputs()?.size.orZero() == 0) {
                                discard()
                                return@launch
                            }
                            setPreferredSampleRate(sampleRate.toDouble(), null)
                            setMode(AVAudioSessionModeSpokenAudio, null)
                            setCategory(
                                AVAudioSessionCategoryPlayback,
                                AVAudioSessionCategoryOptionMixWithOthers or AVAudioSessionCategoryOptionAllowBluetooth,
                                null
                            )
                            setActive(true, null)
                        }
                    }

                    val audioBuffer = AVAudioPCMBuffer(format, byteArray.size.toUInt())
                    audioBuffer.frameLength = (byteArray.size / 2).toUInt()

                    engine?.mainMixerNode?.installTapOnBus(
                        bus = 0u,
                        bufferSize = bufferSize.toUInt(),
                        format = format
                    ) { buffer, _ ->
                        handleAudioBuffer(buffer)
                    }

                    if (format.commonFormat == AVAudioPCMFormatFloat32) {
                        byteArray.usePinned { pinnedBytes ->
                            val int16Data = pinnedBytes.addressOf(0).reinterpret<ShortVar>()
                            val floatData = audioBuffer.floatChannelData?.get(0) ?: return@usePinned

                            // Convert Int16 to Float32 (normalize to the range -1.0 to 1.0)
                            for (i in 0 until byteArray.size / 2) {
                                val intValue = int16Data[i].toInt()
                                floatData[i] = (intValue.toFloat() / Short.MAX_VALUE.toFloat())
                            }
                        }
                    } else {
                        byteArray.usePinned { pinnedBytes ->
                            val destination = audioBuffer.int16ChannelData?.get(0) ?: return@usePinned
                            memcpy(destination, pinnedBytes.addressOf(0), byteArray.size.convert())
                        }
                    }

                    playerNode?.scheduleBuffer(audioBuffer) {
                        dispatch_sync(dispatch_get_main_queue()) {
                            discard()
                        }
                    }

                    engine?.prepare()
                    engine?.startAndReturnError(null)
                    playerNode?.play()
                }
            }

            override fun pause() {
                scope.coroutineContext.cancelChildren()
                playerNode?.pause()
            }

            override fun discard() {
                scope.coroutineContext.cancelChildren()
                runBlocking {
                    onFinish()
                    engine?.mainMixerNode?.removeTapOnBus(0u)
                    playerNode?.stop()
                    engine?.stop()
                    playedBytes = 0
                    playerNode = null
                    engine = null
                    barBuffer = null
                    AVAudioSession.sharedInstance().setActive(
                        active = false,
                        nativeHeap.allocPointerTo<ObjCObjectVar<NSError?>>().value
                    )
                }
            }

            private fun handleAudioBuffer(buffer: AVAudioPCMBuffer?) {
                if (buffer == null) return

                val frameLength = buffer.frameLength.toInt()
                val channelCount = buffer.format.channelCount.toInt()
                val isFloat32 = buffer.format.commonFormat == AVAudioPCMFormatFloat32

                scope.launch {
                    val int16Data: ShortArray

                    if (isFloat32) {
                        // Handle Float32 data
                        val channelData = buffer.floatChannelData ?: return@launch
                        val monoData = FloatArray(frameLength) // Pre-allocate for performance

                        for (channel in 0 until channelCount) {
                            val channelSamples = channelData[channel] ?: continue
                            for (frame in 0 until frameLength) {
                                monoData[frame] += channelSamples[frame]
                            }
                        }

                        // Average stereo channels if applicable
                        if (channelCount > 1) {
                            for (frame in monoData.indices) {
                                monoData[frame] = monoData.getOrNull(frame).orZero() / channelCount
                            }
                        }

                        // Convert to Int16
                        int16Data = ShortArray(frameLength)
                        for (i in monoData.indices) {
                            int16Data[i] = (monoData[i].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
                        }
                    } else {
                        // Handle Int16 data
                        val channelData = buffer.int16ChannelData ?: return@launch
                        val monoData = ShortArray(frameLength) // Pre-allocate for performance

                        for (channel in 0 until channelCount) {
                            val channelSamples = channelData[channel] ?: continue
                            for (frame in 0 until frameLength) {
                                monoData[frame] = (monoData[frame] + channelSamples[frame]).toShort()
                            }
                        }

                        // Average stereo channels if applicable
                        if (channelCount > 1) {
                            for (frame in monoData.indices) {
                                monoData[frame] = (monoData[frame] / channelCount).toShort()
                            }
                        }

                        int16Data = monoData
                    }

                    // Convert Int16 data to ByteArray for processing
                    val byteData = shortArrayToByteArray(int16Data)

                    // Write to bar buffer
                    barBuffer?.write(byteData)
                    playedBytes += byteData.size

                    // Process chunks if barBuffer has enough data
                    if (playedBytes >= bytesPerBar) {
                        processChunk(barBuffer?.readByteArray())
                        barBuffer?.clear()
                        playedBytes -= bytesPerBar
                    }
                }
            }

            private fun shortArrayToByteArray(shortArray: ShortArray): ByteArray {
                val res = ByteArray(shortArray.size * 2) // 2 bytes per short
                for (i in shortArray.indices) {
                    val value = shortArray[i].toInt()
                    res[i * 2] = (value and 0xFF).toByte() // Low byte
                    res[i * 2 + 1] = ((value shr 8) and 0xFF).toByte() // High byte
                }
                return res
            }

        }
    }
}

actual fun getMinBufferSize(sampleRate: Int, channels: Int, encoding: Int): Int {
    val bytesPerSample = when (encoding) {
        16 -> 2
        8 -> 1
        else -> throw IllegalArgumentException("Unsupported encoding: $encoding")
    }
    val estimatedFramesPerBuffer = (sampleRate * 0.02).toInt() // 20ms buffer
    return estimatedFramesPerBuffer * channels * bytesPerSample
}
