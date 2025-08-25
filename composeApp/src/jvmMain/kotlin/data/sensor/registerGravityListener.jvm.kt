package data.sensor

import base.utils.orZero
import korlibs.math.toInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import oshi.SystemInfo

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
    return object : SensorEventListener {
        override var data: MutableStateFlow<List<SensorEvent>> = MutableStateFlow(emptyList())
        override var listener: ((event: SensorEvent) -> Unit)? = null
        override val id: Int = this.hashCode()
        override val name: String = name
        override val description: String? = null
        override val maximumRange: Float? = null
        override val resolution: Float? = null
        override var delay: SensorDelay = SensorDelay.Slow
        private var runningScope = CoroutineScope(Job())

        override fun register(sensorDelay: SensorDelay) {
            delay = sensorDelay
            unregister()
            runningScope.launch {
                while (true) {
                    onSensorChanged(factory())
                    delay(sensorDelay.milliseconds)
                }
            }
        }
        override fun unregister() {
            runningScope.coroutineContext.cancelChildren()
        }
    }
}

actual fun getGravityListener(onSensorChanged: (event: SensorEvent?) -> Unit): SensorEventListener? {
    return null
}
