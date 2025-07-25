package data.io.social.network.conversation.message

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import database.AppRoomDatabase
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.serializers.LocalDateTimeIso8601Serializer
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.events.m.RelatesTo
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationMethod
import ui.conversation.components.experimental.gravity.GravityData
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Conversation entity representing a singular message within a conversation */
@Entity(
    tableName = AppRoomDatabase.TABLE_CONVERSATION_MESSAGE,
    indices = [
        Index(value = ["conversation_id"]),
        Index(value = ["sent_at"]),
        Index(value = ["anchor_message_id"]),
        Index(value = ["parent_anchor_message_id"])
    ]
)
@Serializable
data class ConversationMessageIO @OptIn(ExperimentalUuidApi::class) constructor(

    /** Message identifier */
    @PrimaryKey
    val id: String = Uuid.random().toString(), // default value due to Room ksp requirement

    /** message content */
    val content: String? = null,

    /** Public id of the author of this message */
    @ColumnInfo("author_public_id")
    val authorPublicId: String? = null,

    /** Whether preview should be shown for this message */
    @ColumnInfo("show_preview")
    val showPreview: Boolean? = true,

    val gravityData: GravityData? = null,

    @ColumnInfo("anchor_message_id")
    val anchorMessageId: String? = null,

    @ColumnInfo("parent_anchor_message_id")
    val parentAnchorMessageId: String? = null,

    /** Time of message being sent in ISO format */
    @ColumnInfo(name = "sent_at")
    @Serializable(with = LocalDateTimeIso8601Serializer::class)
    val sentAt: LocalDateTime? = null,

    /**
     * State of this message. Generally, this information is sent only for the last item,
     * as it represents all of the messages above it
     */
    val state: MessageState? = null,

    /** List of timings of each keystroke in this message */
    val timings: List<Long>? = null,
    var transcribed: Boolean? = null,

    @ColumnInfo(name = "conversation_id")
    var conversationId: String? = null,

    val verification: VerificationRequestInfo? = null,

    val edited: Boolean = false,

    val prevBatch: String? = null,
    val nextBatch: String? = null
) {

    @Serializable
    data class VerificationRequestInfo(
        val fromDeviceId: String,
        val methods: Set<VerificationMethod>,
        val to: String
    )

    @Ignore
    fun update(other: ConversationMessageIO): ConversationMessageIO {
        return this.copy(
            content = other.content ?: content,
            authorPublicId = other.authorPublicId ?: authorPublicId,
            showPreview = other.showPreview ?: showPreview,
            anchorMessageId = other.anchorMessageId ?: anchorMessageId,
            parentAnchorMessageId = other.parentAnchorMessageId ?: parentAnchorMessageId,
            sentAt = other.sentAt ?: sentAt,
            state = other.state ?: state,
            timings = other.timings ?: timings,
            conversationId = other.conversationId ?: conversationId,
            transcribed = other.transcribed ?: transcribed,
            verification = other.verification ?: verification,
        )
    }

    fun relatesTo(): RelatesTo? {
        return when {
            parentAnchorMessageId != null -> RelatesTo.Thread(
                replyTo = if(anchorMessageId != null) RelatesTo.ReplyTo(EventId(anchorMessageId)) else null,
                eventId = EventId(parentAnchorMessageId)
            )
            anchorMessageId != null -> RelatesTo.Reply(
                replyTo = RelatesTo.ReplyTo(EventId(anchorMessageId))
            )
            else -> null
        }
    }
}
