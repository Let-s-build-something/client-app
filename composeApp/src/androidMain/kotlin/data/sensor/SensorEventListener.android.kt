package data.sensor

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorManager
import android.hardware.SensorManager.SENSOR_DELAY_GAME
import android.hardware.SensorManager.SENSOR_DELAY_NORMAL
import android.hardware.SensorManager.SENSOR_DELAY_UI
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.koin.mp.KoinPlatform.getKoin

actual suspend fun getAllSensors(): List<SensorEventListener>? {
    return withContext(Dispatchers.Default) {
        getSensorManager()?.getSensorList(Sensor.TYPE_ALL)?.map { it.toSensorEventListener() }
    }
}

actual fun getGravityListener(onSensorChanged: (event: data.sensor.SensorEvent?) -> Unit): SensorEventListener? {
    return getSensorManager()?.getDefaultSensor(Sensor.TYPE_GRAVITY)?.toSensorEventListener()
}

private fun Sensor.toSensorEventListener(): SensorEventListener {
    return object: SensorEventListener {
        private val eventListener = object: android.hardware.SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                onSensorChanged(event = event?.toSensorEvent())
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        override var data: MutableStateFlow<List<data.sensor.SensorEvent>> = MutableStateFlow(emptyList())
        override var listener: ((event: data.sensor.SensorEvent) -> Unit)? = null
        override var instance: String? = null

        override val id: Int = this@toSensorEventListener.id
        override val name: String = this@toSensorEventListener.name
        override val description: String? = null
        override val maximumRange: Float = this@toSensorEventListener.maximumRange
        override val resolution: Float = this@toSensorEventListener.resolution
        override var hzSpeed: Int = HZ_SPEED_NORMAL

        override fun register(hzSpeed: Int) {
            this.hzSpeed = hzSpeed
            getSensorManager()?.registerListener(
                eventListener,
                this@toSensorEventListener,
                when(hzSpeed) {
                    HZ_SPEED_SLOW -> SENSOR_DELAY_NORMAL
                    HZ_SPEED_FAST -> SENSOR_DELAY_GAME
                    else -> SENSOR_DELAY_UI
                }
            )
        }

        override fun unregister() {
            eventListener.let { instance ->
                with(getKoin().get<Context>()) {
                    (getSystemService(Context.SENSOR_SERVICE) as? SensorManager)?.unregisterListener(instance)
                }
            }
        }
    }
}

private fun getSensorManager() = getKoin()
    .get<Context>()
    .getSystemService(Context.SENSOR_SERVICE) as? SensorManager

private fun SensorEvent.toSensorEvent() = SensorEvent(
    values = values
)

actual fun stopBackgroundStreamDevConsole(): Boolean {
    val context = getKoin().get<Context>()
    return context.stopService(Intent(context, SensorStreamService::class.java))
}
actual fun streamDevConsoleInBackground(): Boolean {
    val context = getKoin().get<Context>()
    val intent = Intent(context, SensorStreamService::class.java)
    ContextCompat.startForegroundService(context, intent)
    return true
}
