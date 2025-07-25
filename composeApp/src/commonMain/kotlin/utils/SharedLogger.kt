package utils

import augmy.interactive.com.BuildKonfig
import augmy.interactive.shared.utils.DateUtils
import korlibs.logger.DefaultLogOutput
import korlibs.logger.Logger
import korlibs.logger.Logger.Output
import kotlinx.coroutines.flow.update
import org.koin.mp.KoinPlatform
import ui.dev.DeveloperConsoleDataManager
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

object SharedLogger {
    val logger = Logger("Augmy")

    private val developerConsoleDataManager by lazy {
        KoinPlatform.getKoinOrNull()?.injectOrNull<DeveloperConsoleDataManager>()
    }

    private val output = object: Output {
        override fun output(
            logger: Logger,
            level: Logger.Level,
            msg: Any?
        ) {
            if(BuildKonfig.isDevelopment) {
                developerConsoleDataManager?.value?.logs?.update {
                    it.toMutableList().apply {
                        add(0, LoggerMessage(level = level, message = "${logger.name}: $msg"))
                    }
                }
                DefaultLogOutput.output(logger, level, msg)
            }
        }
    }

    fun init() {
        val level = if (BuildKonfig.isDevelopment) Logger.Level.TRACE else Logger.Level.ERROR
        Logger.defaultOutput = output
        Logger.defaultLevel = level
        logger.debug { "Logger initialized at level $level" }
    }

    internal data class LoggerMessage @OptIn(ExperimentalUuidApi::class) constructor(
        val level: Logger.Level,
        val message: Any?,
        val timestamp: Long = DateUtils.now.toEpochMilliseconds(),
        val uid: String = Uuid.random().toString()
    )
}
