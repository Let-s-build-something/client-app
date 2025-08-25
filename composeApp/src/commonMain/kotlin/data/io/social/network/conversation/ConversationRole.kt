package data.io.social.network.conversation

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import database.AppRoomDatabase
import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Entity(
    tableName = AppRoomDatabase.TABLE_CONVERSATION_ROLE,
    indices = [Index(value = ["room_id"])]
)
@Serializable
data class ConversationRole @OptIn(ExperimentalUuidApi::class) constructor(
    var power: Long,
    val label: String,
    @ColumnInfo("room_id")
    val roomId: String,
    @PrimaryKey
    val uid: String = Uuid.random().toString()
) {
    override fun toString(): String {
        return "{" +
                "power: $power, " +
                "label: $label, " +
                "roomId: $roomId, " +
                "uid: $uid" +
                "}"
    }
}
