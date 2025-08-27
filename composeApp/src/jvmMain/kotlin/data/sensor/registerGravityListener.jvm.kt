package data.sensor

import base.utils.orZero
import korlibs.math.toInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import oshi.SystemInfo
import utils.SharedLogger

actual suspend fun getAllSensors(): List<SensorEventListener>? {
    return with(SystemInfo()) {
        listOf(
            createRepeatedEventListener(name = "System uptime") {
                SensorEvent(
                    values = floatArrayOf(operatingSystem.systemUptime.toFloat())
                )
            },
            createRepeatedEventListener(name = "Battery capacity percentage") {
                SensorEvent(
                    values = floatArrayOf(
                        hardware.powerSources.firstOrNull()?.remainingCapacityPercent?.toFloat() ?: 0f)
                )
            },
            createRepeatedEventListener(name = "Battery charging bool state") {
                SensorEvent(
                    values = floatArrayOf(
                        hardware.powerSources.firstOrNull()?.isCharging?.toInt()?.toFloat().orZero()
                    )
                )
            },
            createRepeatedEventListener(name = "Visible GUI windows") {
                SensorEvent(
                    values = null,
                    uiValues = operatingSystem.getDesktopWindows(true).associate {
                        it.title to it.command
                    }
                )
            }
        )
    }
}

private fun createRepeatedEventListener(
    name: String,
    factory: () -> SensorEvent?
): SensorEventListener {
    SharedLogger.logger.debug { "createRepeatedEventListener" }
    return object : SensorEventListener {
        override var data: MutableStateFlow<List<SensorEvent>> = MutableStateFlow(emptyList())
        override var listener: ((event: SensorEvent) -> Unit)? = null
        override val id: Int = this.hashCode()
        override val name: String = name
        override val description: String? = null
        override val maximumRange: Float? = null
        override val resolution: Float? = null
        override var hzSpeed: Int = HZ_SPEED_NORMAL
        override var instance: String? = null
        private var runningScope = CoroutineScope(Job())
        private var isRunning = false

        //register data.sensor.RegisterGravityListener_jvmKt$createRepeatedEventListener$2@14d4c871 (c0767bc0-9f2b-4f4c-be49-cb57a53e6931)
        //unregister data.sensor.RegisterGravityListener_jvmKt$createRepeatedEventListener$2@5428fb1 (c0767bc0-9f2b-4f4c-be49-cb57a53e6931)
        override fun register(hzSpeed: Int) {
            SharedLogger.logger.debug { "register $this ($instance)" }
            if (isRunning) return
            isRunning = true
            this.hzSpeed = hzSpeed
            runningScope.coroutineContext.cancelChildren()
            runningScope.launch {
                while (runningScope.isActive && isRunning) {
                    onSensorChanged(factory())
                    delay(1000L / hzSpeed)
                }
            }
        }
        override fun unregister() {
            SharedLogger.logger.debug { "unregister $this ($instance)" }
            runningScope.coroutineContext.cancelChildren()
            isRunning = false
        }
    }
}

actual fun getGravityListener(onSensorChanged: (event: SensorEvent?) -> Unit): SensorEventListener? {
    return null
}
