package data.io.matrix.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import database.AppRoomDatabase
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.folivo.trixnity.clientserverapi.model.sync.Sync.Response.Rooms.JoinedRoom.Ephemeral
import net.folivo.trixnity.clientserverapi.model.sync.Sync.Response.Rooms.JoinedRoom.UnreadNotificationCounts
import net.folivo.trixnity.clientserverapi.model.sync.Sync.Response.Rooms.RoomAccountData
import net.folivo.trixnity.clientserverapi.model.sync.Sync.Response.Rooms.State
import net.folivo.trixnity.clientserverapi.model.sync.Sync.Response.Rooms.StrippedState
import net.folivo.trixnity.clientserverapi.model.sync.Sync.Response.Rooms.Timeline
import net.folivo.trixnity.core.model.events.m.room.HistoryVisibilityEventContent
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Matrix conversation room object */
@Entity(
    tableName = AppRoomDatabase.TABLE_CONVERSATION_ROOM,
    indices = [
        Index(value = ["owner_public_id"]),
        Index(value = ["type"]),
        Index(value = ["proximity"]),
        Index(value = ["owner_public_id", "type"]),
        Index(value = ["owner_public_id", "id"])
    ]
)
@Serializable
data class ConversationRoomIO @OptIn(ExperimentalUuidApi::class) constructor(
    /** Unique identifier of this room, in the format of "!opaque_id:domain" */
    val id: String = Uuid.random().toString(),

    /** Information about the room */
    val summary: RoomSummary? = null,

    /**
     * A decimal range between -1 and 10. -1 means blocked, 1 is muted,
     *  or just a far social circle, and 10 is the closest
     */
    val proximity: Float? = null,

    /** Counts of unread notifications for this room. */
    @ColumnInfo("unread_notifications")
    val unreadNotifications: UnreadNotificationCounts? = null,

    /** The stripped state of a room that the user has been invited to. */
    @ColumnInfo("invite_state")
    val inviteState: StrippedState? = null,

    /** The stripped state of a room that the user has knocked upon. */
    @ColumnInfo("knock_state")
    val knockState: StrippedState? = null,

    /** Database flag: an identifier of the owner of this item */
    @ColumnInfo("owner_public_id")
    val ownerPublicId: String? = null,

    @PrimaryKey
    @ColumnInfo("primary_key")
    val primaryKey: String = "${id}_$ownerPublicId",

    /**
     * Previous batch of the initially received room. This should never change and, thus, marks the beginning of /messages pagination
     */
    @ColumnInfo("prev_batch")
    var prevBatch: String? = null,

    @ColumnInfo("history_visibility")
    val historyVisibility: HistoryVisibilityEventContent.HistoryVisibility? = null,

    val algorithm: EncryptionAlgorithm? = null,

    /** Type of the room */
    val type: RoomType,

    @ColumnInfo("is_direct")
    val isDirect: Boolean? = summary?.isDirect
) {
    @Ignore
    @Transient
    var state: State? = null

    @Ignore
    @Transient
    var timeline: Timeline? = null

    @Ignore
    @Transient
    var accountData: RoomAccountData? = null

    @Ignore
    @Transient
    var ephemeral: Ephemeral? = null


    fun update(other: ConversationRoomIO?): ConversationRoomIO {
        return if(other == null) this
        else this.copy(
            id = other.id,
            summary = summary?.update(other.summary) ?: other.summary,
            proximity = other.proximity ?: proximity,
            unreadNotifications = other.unreadNotifications ?: unreadNotifications,
            inviteState = other.inviteState ?: inviteState,
            knockState = other.knockState ?: knockState,
            ownerPublicId = other.ownerPublicId ?: ownerPublicId,
            primaryKey = other.primaryKey,
            prevBatch = other.prevBatch ?: prevBatch,
            historyVisibility = other.historyVisibility ?: historyVisibility,
            algorithm = other.algorithm ?: algorithm,
            type = other.type
        )
    }

    override fun toString(): String {
        return "{" +
                "id: $id, " +
                "summary: $summary, " +
                "proximity: $proximity, " +
                "unreadNotifications: $unreadNotifications, " +
                "inviteState: $inviteState, " +
                "knockState: $knockState, " +
                "ownerPublicId: $ownerPublicId, " +
                "primaryKey: $primaryKey, " +
                "prevBatch: $prevBatch, " +
                "historyVisibility: $historyVisibility, " +
                "algorithm: $algorithm, " +
                "type: $type" +
                "}"
    }
}
