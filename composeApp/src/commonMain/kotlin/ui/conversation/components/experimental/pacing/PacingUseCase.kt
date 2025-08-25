package ui.conversation.components.experimental.pacing

import base.utils.orZero
import data.io.app.SettingsKeys
import data.io.app.SettingsKeys.KEY_PACING_NARROW_AVG
import data.io.app.SettingsKeys.KEY_PACING_WIDE_AVG
import koin.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.koin.dsl.module
import org.koin.mp.KoinPlatform

internal val pacingModule = module {
    factory { PacingUseCase() }
    single { PacingDataManager() }
}

class PacingDataManager {
    val keyboardRows = listOf(
        "1234567890",
        "qwertyuiop",
        "asdfghjkl",
        "zxcvbnm"
    )
}

/** Bundled functionality of Gifs */
class PacingUseCase {
    companion object {
        private const val WIDE_AVG_WEIGHT = 0.33f
        private const val NARROW_AVG_WEIGHT = 0.33f
        private const val CLOSE_AVG_WEIGHT = 0.33f

        private const val MIN_WAVES = 8
        const val WAVES_PER_PIXEL = 0.015f
        private const val NARROW_AVG_CHARS = 1400
        private const val CLOSE_AVG_CHARS = 50
        private const val AVG_SEPARATOR = "_"
        private val ignoredCharacters = listOf(
            ' '
        )
    }

    private val settings: AppSettings by KoinPlatform.getKoin().inject()
    private val dataManager: PacingDataManager by KoinPlatform.getKoin().inject()

    val timingSensor = MutableStateFlow(TimingSensor(isLocked = true))


    var isInitialized = false

    private val keyPressMutex = Mutex()
    private val appendAvgMutex = Mutex()
    private val _keyWidths = MutableStateFlow(MutableList(MIN_WAVES) { 0.1f })
    private var backStack = MutableList(MIN_WAVES) { it to 0.1f }
    val keyWidths = _keyWidths.asStateFlow()

    private val settingsScope = CoroutineScope(Job())
    private val decreaseScope = CoroutineScope(Job())

    private var wideAvg = 0 to 0f
    private var narrowAvg = 0 to 0f
    private var closeAvg = 0 to 0f

    // =============== public functions ===============

    suspend fun init(
        maxWaves: Int,
        defaultAvg: Float = 300f,
        conversationId: String,
        savedMessage: String
    ) {
        isInitialized = true
        wideAvg = retrieveAvg(KEY_PACING_WIDE_AVG) ?: (0 to defaultAvg)
        narrowAvg = retrieveAvg(KEY_PACING_NARROW_AVG) ?: (0 to defaultAvg) // will come from the constructor in the future
        closeAvg = 0 to 0f // will come from the constructor in the future

        timingSensor.value = TimingSensor(
            initialText = savedMessage,
            timings = settings
                .getStringOrNull("${SettingsKeys.KEY_LAST_MESSAGE_TIMINGS}_$conversationId")
                ?.split(",")
                ?.mapNotNull { it.toLongOrNull() }
                .orEmpty()
                .toMutableList()
        )
        _keyWidths.update {
            MutableList(maxWaves.coerceAtLeast(MIN_WAVES)) { 0.1f }
        }
        backStack = MutableList(maxWaves.coerceAtLeast(MIN_WAVES)) { it to 0.1f }
    }

    suspend fun cache(conversationId: String) = withContext(Dispatchers.Default) {
        val key = "${SettingsKeys.KEY_LAST_MESSAGE_TIMINGS}_$conversationId"
        val timings = timingSensor.value.timings

        if(timings.isNotEmpty()) {
            settings.putString(key, timings.joinToString(","))
        }else settings.remove(key)
    }

    private fun runDecreaseRepeat() {
        decreaseScope.coroutineContext.cancelChildren()
        decreaseScope.launch {
            while (true) {
                delay(200)
                _keyWidths.update { prev ->
                    prev.map { it.times(0.95f).coerceAtLeast(0.1f) }.toMutableList().also {
                        backStack = it.mapIndexed { index, value -> index to value }.toMutableList()
                    }
                }
            }
        }
    }

    suspend fun clearCache(conversationId: String) {
        settings.remove("${SettingsKeys.KEY_LAST_MESSAGE_TIMINGS}_$conversationId")
    }

    // 1. define number of waves for the message
    suspend fun onKeyPressed(char: Char, timingMs: Long) = withContext(Dispatchers.Default) {
        if(ignoredCharacters.contains(char)) return@withContext

        keyPressMutex.withLock {
            while(backStack.size > keyWidths.value.size) {
                backStack.removeFirst()
            }
            val positionScale = getKeyScalePosition(char)
            val position = (keyWidths.value.size * positionScale).toInt().let {
                // we don't want to override previously selected index right away
                if(backStack.lastOrNull()?.first == it) {
                    it + (if(positionScale >= 0.5) -1 else 1)
                }else it
            }.coerceAtMost(keyWidths.value.lastIndex)

            val wideValance = (if(timingMs < wideAvg.second) {
                1 - timingMs / wideAvg.second
            }else 0f).times(WIDE_AVG_WEIGHT)

            val narrowValance = (if(timingMs < narrowAvg.second) {
                1 - timingMs / narrowAvg.second
            }else 0f).times(NARROW_AVG_WEIGHT)

            val closeValance = (if(timingMs < closeAvg.second) {
                1 - timingMs / closeAvg.second
            }else 0f).times(CLOSE_AVG_WEIGHT)


            val valance = (wideValance + narrowValance + closeValance)
                .times(1.25f)
                .coerceIn(minimumValue = 0f, maximumValue = 1f)

            _keyWidths.update {
                it.apply {
                    this[position] = valance
                }
            }
            backStack.add(position to valance)
            runDecreaseRepeat()
            settingsScope.launch {
                wideAvg = wideAvg.appendAvg(KEY_PACING_WIDE_AVG, timingMs)
                narrowAvg = narrowAvg.appendAvg(KEY_PACING_NARROW_AVG, timingMs, limit = NARROW_AVG_CHARS)
                closeAvg = closeAvg.appendAvg(key = null, timingMs, limit = CLOSE_AVG_CHARS)
            }
        }
    }


    // ================ util functions ================

    private suspend fun retrieveAvg(key: String): Pair<Int, Float>? = withContext(Dispatchers.IO) {
        settings.getStringOrNull(key)?.split(AVG_SEPARATOR)?.let {
            it.firstOrNull()?.toIntOrNull().orZero() to (it.lastOrNull()?.toFloatOrNull() ?: 0f)
        }
    }

    private suspend fun Pair<Int, Float>.appendAvg(
        key: String?,
        newValue: Long,
        limit: Int? = null
    ): Pair<Int, Float> = withContext(Dispatchers.IO) {
        appendAvgMutex.withLock {
            val sum = (second * first)
                .minus(if(limit != null && first >= limit) (first.plus(1) - limit).times(second) else 0f)
                .plus(newValue)

            val count = if(limit != null) (first + 1).coerceAtMost(limit) else first + 1
            val average = sum / count
            if(key != null) settings.putString(key = key, "$sum$AVG_SEPARATOR$average")

            count to average
        }
    }

    private suspend fun getKeyScalePosition(key: Char): Double = withContext(Dispatchers.Default) {
        for (row in dataManager.keyboardRows) {
            val index = row.lastIndexOf(key.lowercaseChar())
            if (index != -1) {
                return@withContext index.toDouble() / (row.length - 1).toDouble()
            }
        }
        0.5
    }
}
