package base.utils.audio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import base.utils.orZero
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.allocPointerTo
import kotlinx.cinterop.get
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioMixerNode
import platform.AVFAudio.AVAudioNode
import platform.AVFAudio.AVAudioPCMFormatInt16
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryOptionAllowBluetooth
import platform.AVFAudio.AVAudioSessionCategoryOptionMixWithOthers
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVAudioSessionModeSpokenAudio
import platform.AVFAudio.availableInputs
import platform.AVFAudio.setActive
import platform.AVFAudio.setPreferredSampleRate
import platform.Foundation.NSError
import platform.Foundation.NSMutableData
import platform.Foundation.appendBytes

/** Automatically disposed audio recorder */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberAudioRecorder(
    barsCount: Int,
    sampleRate: Int,
    secondsPerBar: Double,
    bufferSize: Int
): AudioRecorder {
    val coroutineScope = rememberCoroutineScope()

    val audioRecorder = remember {
        object: AudioRecorder(
            sampleRate = sampleRate,
            secondsPerBar = secondsPerBar,
            barsCount = barsCount
        ) {
            private var engine: AVAudioEngine? = null
            private var byteOutputStream: NSMutableData? = null
            private var barByteOutputStream: NSMutableData? = null
            private var mixerNode: AVAudioMixerNode? = null
            private val format = AVAudioFormat(
                AVAudioPCMFormatInt16,
                sampleRate.toDouble(),
                1u,
                false
            )

            override fun startRecording() {
                if(engine == null) {
                    stopRecording()
                }
                val errorPointer = nativeHeap.allocPointerTo<ObjCObjectVar<NSError?>>()

                if(engine == null) {
                    engine = AVAudioEngine()
                    mixerNode = AVAudioMixerNode()

                    engine?.inputNode?.let { inputNode ->
                        AVAudioSession.sharedInstance().apply {
                            if((this.availableInputs()?.size ?: 0) == 0) {
                                stopRecording()
                                return@let
                            }
                            setPreferredSampleRate(sampleRate.toDouble(), errorPointer.value)
                            setMode(AVAudioSessionModeSpokenAudio, errorPointer.value)
                            setCategory(
                                AVAudioSessionCategoryPlayAndRecord,
                                AVAudioSessionCategoryOptionMixWithOthers or AVAudioSessionCategoryOptionAllowBluetooth,
                                errorPointer.value
                            )
                            setActive(true, errorPointer.value)
                        }

                        byteOutputStream = NSMutableData()
                        barByteOutputStream = NSMutableData()

                        inputNode.installTapOnBus(
                            bus = 0u,
                            bufferSize = bufferSize.toUInt(),
                            format = format
                        ) { tapBuffer, _ ->
                            val audioBuffer = tapBuffer?.audioBufferList?.get(0)?.mBuffers?.get(0)
                            val data = audioBuffer?.mData
                            val size = audioBuffer?.mDataByteSize?.toInt().orZero()

                            byteOutputStream?.appendBytes(data, size.toULong())
                            barByteOutputStream?.appendBytes(data, size.toULong())

                            if(barByteOutputStream?.length?.toInt().orZero() >= bytesPerBar) {
                                val byteArray = barByteOutputStream?.byteArray
                                coroutineScope.launch {
                                    processChunk(byteArray = byteArray)
                                }
                                barByteOutputStream?.setLength(0u)
                            }
                        }
                    }
                }

                engine?.inputNode?.let { inputNode ->
                    val inputFormat = inputNode.outputFormatForBus(0u)
                    val mainMixerNode = engine?.mainMixerNode as? AVAudioNode
                    val mixerFormat = AVAudioFormat(
                        AVAudioPCMFormatInt16,
                        sampleRate.toDouble(),
                        1u,
                        false
                    )

                    // remove interrupting audio
                    mixerNode?.apply {
                        setVolume(0f)
                        engine?.attachNode(this)
                        engine?.connect(inputNode, this, inputFormat)
                        mainMixerNode?.let { engine?.connect(this, it, mixerFormat) }
                    }
                }

                engine?.prepare()
                engine?.startAndReturnError(nativeHeap.allocPointerTo<ObjCObjectVar<NSError?>>().value)
            }

            override suspend fun saveRecording(): ByteArray? {
                return withContext(Dispatchers.Default) {
                    pcmToWav(
                        pcmData = byteOutputStream?.byteArray,
                        channels = 1,
                        bitsPerSample = 16
                    )
                }
            }

            override fun pauseRecording() {
                engine?.pause()
            }

            override fun stopRecording() {
                super.stopRecording()
                if (engine?.isRunning() == true && mixerNode?.engine != null) {
                    mixerNode?.removeTapOnBus(0u)
                }
                engine?.inputNode?.removeTapOnBus(0u)
                engine?.stop()
                engine = null
                byteOutputStream?.setLength(0u)
                barByteOutputStream?.setLength(0u)
                AVAudioSession.sharedInstance().apply {
                    setActive(
                        false,
                        nativeHeap.allocPointerTo<ObjCObjectVar<NSError?>>().value
                    )
                }
                byteOutputStream = null
                barByteOutputStream = null
            }
        }
    }

    DisposableEffect(null) {
        onDispose {
            audioRecorder.stopRecording()
        }
    }

    return audioRecorder
}

/** Returns [ByteArray] from [NSMutableData] */
@OptIn(ExperimentalForeignApi::class)
val NSMutableData?.byteArray: ByteArray?
    get() {
        return this?.let { outputStream ->
            outputStream.bytes?.usePinned {
                it.get().readBytes(outputStream.length.toInt())
            }
        }
    }
