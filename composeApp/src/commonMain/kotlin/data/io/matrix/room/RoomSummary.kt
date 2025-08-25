package data.io.matrix.room

import augmy.interactive.shared.utils.DateUtils
import data.io.social.network.conversation.message.MediaIO
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.m.room.PowerLevelsEventContent

/**
 * Information about the room which clients may need to correctly render it to users.
 *
 * https://spec.matrix.org/v1.13/client-server-api/#get_matrixclientv3sync_response-200_roomsummary
 */
@Serializable
data class RoomSummary(
    /** This should be the first 5 members of the room, ordered by stream ordering, which are joined or invited.
     *  The list must never include the client’s own user ID. */
    @SerialName("m.heroes")
    val heroes: List<UserId>? = null,

    /** Name of the room. */
    val canonicalAlias: String? = null,

    /** Avatar photo url. */
    val avatar: MediaIO? = null,

    /** Whether this room is just one on one. */
    val isDirect: Boolean? = null,

    val powerLevels: PowerLevelsEventContent? = null,
    val powerLevelsTime: LocalDateTime? = if (powerLevels != null) DateUtils.localNow else null,

    /** Message sent out to invited people by default if not changed. */
    val invitationMessage: String? = null,

    /** The number of users with membership of invite. */
    @SerialName("m.invited_member_count")
    val invitedMemberCount: Int? = null,

    /** The number of users with membership of join, including the client’s own user ID. */
    @SerialName("m.joined_member_count")
    val joinedMemberCount: Int? = null
) {

    fun update(other: RoomSummary?): RoomSummary {
        return if(other == null) this
        else this.copy(
            heroes = other.heroes ?: heroes,
            canonicalAlias = other.canonicalAlias ?: canonicalAlias,
            avatar = other.avatar ?: avatar,
            isDirect = other.isDirect ?: isDirect,
            invitationMessage = other.invitationMessage ?: invitationMessage,
            invitedMemberCount = other.invitedMemberCount ?: invitedMemberCount,
            joinedMemberCount = other.joinedMemberCount ?: joinedMemberCount
        )
    }

    override fun toString(): String {
        return "{" +
                "heroes: $heroes, " +
                "canonicalAlias: $canonicalAlias, " +
                "avatar: $avatar, " +
                "isDirect: $isDirect, " +
                "invitationMessage: $invitationMessage, " +
                "invitedMemberCount: $invitedMemberCount, " +
                "joinedMemberCount: $joinedMemberCount, " +
                "}"
    }
}
