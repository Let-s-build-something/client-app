package data.io.matrix.room.event.content

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#mroomhistory_visibility">matrix spec</a>
 */
@Serializable
data class HistoryVisibilityEventContent(
    @SerialName("history_visibility")
    val historyVisibility: HistoryVisibility,
    @SerialName("external_url")
    override val externalUrl: String? = null,
) : StateEventContent {
    @Serializable
    enum class HistoryVisibility {
        @SerialName("invited")
        INVITED,

        @SerialName("joined")
        JOINED,

        @SerialName("shared")
        SHARED,

        @SerialName("world_readable")
        WORLD_READABLE
    }
}