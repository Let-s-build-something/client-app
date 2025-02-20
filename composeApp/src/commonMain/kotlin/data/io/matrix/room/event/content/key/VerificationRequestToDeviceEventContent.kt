package data.io.matrix.room.event.content.key

import data.io.matrix.room.event.content.ToDeviceEventContent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * @see <a href="https://spec.matrix.org/unstable/client-server-api/#mkeyverificationrequest">matrix spec</a>
 */
@Serializable
data class VerificationRequestToDeviceEventContent(
    @SerialName("from_device")
    override val fromDevice: String,
    @SerialName("methods")
    override val methods: Set<VerificationMethod>,
    @SerialName("timestamp")
    val timestamp: Long,
    @SerialName("transaction_id")
    val transactionId: String
) : ToDeviceEventContent, VerificationRequest