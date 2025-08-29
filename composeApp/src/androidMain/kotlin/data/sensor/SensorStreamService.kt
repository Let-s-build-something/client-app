package data.sensor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import augmy.interactive.com.R
import augmy.interactive.shared.utils.DateUtils
import base.utils.openSinkFromUri
import data.io.app.SettingsKeys.KEY_STREAMING_DIRECTORY
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.path
import koin.settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okio.buffer
import org.koin.mp.KoinPlatform
import ui.dev.DeveloperConsoleDataManager
import ui.dev.DeveloperConsoleModel
import utils.SharedLogger

class SensorStreamService : LifecycleService() {
    companion object {
        private const val SENSOR_STREAM_CHANNEL_ID = "sensor_stream"
    }

    private val dataManager: DeveloperConsoleDataManager by KoinPlatform.getKoin().inject()
    private var job: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        startForeground(
            789,
            buildNotification()
        )
        dataManager.sinkPlatformFile?.let { file ->
            setUpLocalStream(file)
        }

        return START_STICKY
    }

    private fun setUpLocalStream(file: PlatformFile) {
        job?.cancel()
        job = CoroutineScope(Dispatchers.IO).launch {
            val buffer = mutableListOf<String>()
            var lastFlushTime = DateUtils.now.toEpochMilliseconds()

            try {
                if (dataManager.sink == null) {
                    dataManager.sink = file.openSinkFromUri().buffer()
                    dataManager.streamingDirectory = file.path
                    settings.putString(KEY_STREAMING_DIRECTORY, dataManager.streamingDirectory)
                }
                dataManager.isLocalStreamRunning.value = true

                for (line in dataManager.streamChannel) {
                    try {
                        buffer.add(line)

                        if (DateUtils.now.toEpochMilliseconds() - lastFlushTime >= DeveloperConsoleModel.Companion.WRITE_SENSORS_INTERVAL_MS) {
                            buffer.forEach { line ->
                                dataManager.sink?.writeUtf8(line)
                                dataManager.sink?.writeUtf8("\n")
                            }
                            dataManager.sink?.flush()
                            buffer.clear()
                            lastFlushTime = DateUtils.now.toEpochMilliseconds()
                        }
                    } catch (e: Exception) {
                        if (e.message?.contains("EBADF") == true) {
                            SharedLogger.logger.debug { "EBADF detected, retrying sink setup" }
                            dataManager.sink = file.openSinkFromUri().buffer()
                            continue
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                delay(5000)
                if (dataManager.isLocalStreamRunning.value) {
                    setUpLocalStream(file)
                }
            } finally {
                dataManager.sink?.let {
                    try {
                        it.close()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        dataManager.localStreamJob?.cancel()
                        dataManager.sink = null
                        dataManager.isLocalStreamRunning.value = false
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        job?.cancel()
        dataManager.sinkPlatformFile = null
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, SENSOR_STREAM_CHANNEL_ID)
            .setContentTitle("Sensor Streaming")
            .setContentText("Streaming sensors...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                SENSOR_STREAM_CHANNEL_ID,
                "Sensor Streaming",
                NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(NOTIFICATION_SERVICE) as? NotificationManager)
                ?.createNotificationChannel(channel)
        }
    }
}