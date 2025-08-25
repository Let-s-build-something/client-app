package ui.conversation.components.audio

import base.utils.LinkUtils.urlRegex
import base.utils.MatrixUtils.Media.MATRIX_REPOSITORY_PREFIX
import base.utils.getExtensionFromMimeType
import base.utils.toSha256
import data.io.social.network.conversation.message.MediaIO
import data.shared.SharedDataManager
import database.dao.MediaDao
import database.file.FileAccess
import database.file.FileAccess.Companion.EXPIRATION_MILLIS
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.onDownload
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import okio.Path

/** Class for calling APIs and remote work in general */
class MediaProcessorRepository(
    private val fileAccess: FileAccess,
    private val httpClient: HttpClient,
    private val sharedDataManager: SharedDataManager,
    private val mediaDao: MediaDao
) {

    class FileResult(
        val byteArray: ByteArray,
        val path: Path?,
        val mimetype: String?
    )

    /** downloads a a file and caches or retrieves the cache */
    suspend fun getFileByteArray(
        url: String,
        downloadUrl: String = url,
        extension: String? = null,
        onProgressChange: (bytesSentTotal: Long, contentLength: Long?) -> Unit
    ): FileResult? {
        return withContext(Dispatchers.IO) {
            val fileName = if(url.startsWith(MATRIX_REPOSITORY_PREFIX) || urlRegex.matches(url)) {
                url.toSha256().plus(if(extension != null) ".$extension" else "")
            }else url.substringAfterLast("/")

            if(url.isBlank()) return@withContext null

            fileAccess.loadFileFromCache(
                fileName = fileName,
                extension = extension,
                expirationMillis = if(sharedDataManager.networkConnectivity.value?.isStable != true) {
                    Long.MAX_VALUE
                }else EXPIRATION_MILLIS
            ) ?: try {
                if(downloadUrl.isBlank() || !urlRegex.matches(downloadUrl)) return@withContext null

                val response = httpClient.get(urlString = downloadUrl) {
                    onDownload { bytesSentTotal, contentLength ->
                        onProgressChange(bytesSentTotal, contentLength)
                    }
                }
                val mimetype = response.contentType()?.toString()
                val responseMimetype = getExtensionFromMimeType(mimetype)?.let { ".$it" }

                response.bodyAsBytes().takeIf { it.isNotEmpty() }.let { bytes ->
                    if(bytes == null) null
                    else FileResult(
                        byteArray = bytes,
                        path = fileAccess.saveFileToCache(
                            data = bytes,
                            fileName = fileName.plus(if(extension == null) responseMimetype else "")
                        ),
                        mimetype = mimetype
                    )
                }
            }catch (_: Exception) { null }
        }
    }

    /** Returns the content of a url as a text */
    suspend fun getUrlContent(url: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                HttpClient().config {
                    install(ContentNegotiation)
                }.get(urlString = url).bodyAsText()
            }catch (_: Exception) {
                null
            }
        }
    }

    suspend fun retrieveMedia(idList: List<String>): List<MediaIO> {
        return withContext(Dispatchers.IO) {
            mediaDao.getAllById(idList)
        }
    }
}
