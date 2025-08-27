package data.io.experiment

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import augmy.interactive.shared.ui.theme.LocalTheme
import augmy.interactive.shared.utils.DateUtils
import data.sensor.HZ_SPEED_NORMAL
import database.AppRoomDatabase
import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Entity(tableName = AppRoomDatabase.TABLE_EXPERIMENT)
data class ExperimentIO(

    /** User-friendly identifying name of the experiment */
    val name: String,

    /** Matrix user id of the relevant user. If null, it is globally applicable */
    val owner: String? = null,

    @PrimaryKey
    val uid: String = Uuid.random().toString(),

    @ColumnInfo("created_at")
    val createdAt: Long = DateUtils.now.toEpochMilliseconds(),

    /** Time of expiration in milliseconds. If null, it never expires until manually turned off. */
    @ColumnInfo("activate_until")
    val activateUntil: Long? = 0,

    val setUids: List<String> = listOf(),

    @ColumnInfo("observe_chats")
    val observeChats: Boolean = false,

    @ColumnInfo("active_sensors")
    val activeSensors: List<ActiveSensor> = listOf(),

    val displayFrequency: DisplayFrequency = DisplayFrequency.BeginEnd,
    val choiceBehavior: ChoiceBehavior = ChoiceBehavior.SingleChoice
) {

    @Serializable
    data class ActiveSensor(
        val uid: String,
        val hz: Int = HZ_SPEED_NORMAL
    ) {
        override fun toString(): String {
            return "{ uid: $uid, hz: $hz }"
        }
    }

    @Serializable
    sealed class DisplayFrequency {
        @Serializable
        data object BeginEnd: DisplayFrequency()
        @Serializable
        data object Permanent: DisplayFrequency()
        @Serializable
        data class Constant(val delaySeconds: Long): DisplayFrequency()
    }

    enum class ChoiceBehavior {
        SingleChoice,
        MultiChoice,
        OrderedChoice
    }

    @get:Ignore
    val fullName: AnnotatedString
        @Composable get() = buildAnnotatedString {
            append(name)
            withStyle(SpanStyle(color = LocalTheme.current.colors.disabled)) {
                append(" (${uid})")
            }
        }

    override fun toString(): String {
        return "{" +
                "name: $name" +
                ", owner: $owner" +
                ", uid: $uid" +
                ", createdAt: $createdAt" +
                ", observeChats: $observeChats" +
                ", activateUntil: $activateUntil" +
                ", setUids: $setUids" +
                ", activeSensors: $activeSensors" +
                ", displayFrequency: $displayFrequency" +
                ", choiceBehavior: $choiceBehavior" +
                "}"
    }
}
