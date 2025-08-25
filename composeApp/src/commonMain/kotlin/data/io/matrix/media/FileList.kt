package data.io.matrix.media

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.m.Mentions
import net.folivo.trixnity.core.model.events.m.RelatesTo
import net.folivo.trixnity.core.model.events.m.room.EncryptedFile
import net.folivo.trixnity.core.model.events.m.room.FileInfo

@Serializable
data class FileList(
    @SerialName("body") val body: String,
    @SerialName("format") val format: String? = null,
    @SerialName("formatted_body") val formattedBody: String? = null,
    @SerialName("filename") val fileName: String? = null,
    @SerialName("infos") val infos: List<FileInfo>? = null,
    @SerialName("info") val info: FileInfo? = infos?.firstOrNull(),
    @SerialName("urls") val urls: List<String>? = null,
    @SerialName("url") val url: String? = urls?.firstOrNull(),
    @SerialName("files") val files: List<EncryptedFile>? = null,
    @SerialName("file") val file: EncryptedFile? = files?.firstOrNull(),
    @SerialName("m.relates_to") override val relatesTo: RelatesTo? = null,
    @SerialName("m.mentions") override val mentions: Mentions? = null,
    @SerialName("external_url") override val externalUrl: String? = null,
): MessageEventContent {
    @SerialName("msgtype")
    val type = TYPE

    override fun copyWith(relatesTo: RelatesTo?): MessageEventContent = copy(relatesTo = relatesTo)

    companion object {
        const val TYPE = "m.file"
    }
}