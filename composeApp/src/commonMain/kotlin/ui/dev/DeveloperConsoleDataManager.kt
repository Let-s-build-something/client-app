package ui.dev

import data.io.base.BaseResponse
import data.io.experiment.FullExperiment
import io.github.vinceglb.filekit.PlatformFile
import korlibs.logger.Logger
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import okio.BufferedSink
import utils.DeveloperUtils
import utils.SharedLogger.LoggerMessage

class DeveloperConsoleDataManager {

    var localStreamJob: Job? = null
    var remoteStreamJob: Job? = null
    val streamChannel = Channel<String>(capacity = Channel.UNLIMITED)

    /** developer console size */
    val developerConsoleSize = MutableStateFlow(0f)

    internal val logs = MutableStateFlow(listOf<LoggerMessage>())

    /** Log information for past or ongoing http calls */
    val httpLogData = MutableStateFlow(DeveloperUtils.HttpLogData())

    /** Current host override if there is any */
    val hostOverride = MutableStateFlow<String?>(null)

    /** filter input + whether it's ASC */
    val httpLogFilter = MutableStateFlow("" to false)

    /** filter input + whether it's ASC */
    val logFilter = MutableStateFlow<Triple<String, Boolean, Logger.Level?>>(Triple("", false, null))

    val experiments = MutableStateFlow(listOf<FullExperiment>())
    val activeExperiments = MutableStateFlow(setOf<String>())
    val experimentsToShow = MutableStateFlow(listOf<FullExperiment>())
    val observedEntities = MutableStateFlow(listOf<String>())
    val listensToChats = MutableStateFlow(true)
    val activeSensors = MutableStateFlow(listOf<String>())

    val streamLines = MutableStateFlow(listOf<Pair<String, Any>>())
    val streamingUrlResponse = MutableStateFlow<BaseResponse<*>>(BaseResponse.Idle)
    val activeExperimentScopes = hashMapOf<String, Job>()
    val isLocalStreamRunning = MutableStateFlow(false)
    var remoteStreamStep = 20
    var streamingUrl = ""
    var streamingDirectory = ""
    var sink: BufferedSink? = null
    var sinkPlatformFile: PlatformFile? = null
}