package data.io.matrix.room.event.content.space

import data.io.matrix.room.event.content.StateEventContent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * @see <a href="https://spec.matrix.org/v1.10/client-server-api/#mspaceparent">matrix spec</a>
 */
@Serializable
data class ParentEventContent(
    @SerialName("canonical")
    val canonical: Boolean = false,
    @SerialName("via")
    val via: Set<String>,
    @SerialName("external_url")
    override val externalUrl: String? = null
) : StateEventContent