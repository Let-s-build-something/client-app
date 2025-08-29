package data.sensor

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

expect fun getGravityListener(onSensorChanged: (event: SensorEvent?) -> Unit): SensorEventListener?
expect suspend fun getAllSensors(): List<SensorEventListener>?

expect fun streamDevConsoleInBackground(): Boolean
expect fun stopBackgroundStreamDevConsole(): Boolean

const val HZ_SPEED_SLOW = 5
const val HZ_SPEED_NORMAL = 16
const val HZ_SPEED_FAST = 50

interface SensorEventListener {
    var data: MutableStateFlow<List<SensorEvent>>
    var listener: ((event: SensorEvent) -> Unit)?

    fun onSensorChanged(event: SensorEvent?) {
        if (event != null) {
            listener?.invoke(event)
            data.update {
                it.toMutableList().apply {
                    add(0, event)
                }
            }
        }
    }

    val id: Int
    val name: String
    val description: String?
    val maximumRange: Float?
    val resolution: Float?
    var hzSpeed: Int
    var instance: String?
    val uid: String
        get() = if (instance == null) "$id-${name.hashCode()}" else "$instance-${name.hashCode()}"

    fun register(hzSpeed: Int = HZ_SPEED_NORMAL)
    fun unregister()
}
