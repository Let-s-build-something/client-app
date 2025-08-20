package ui.conversation.components.audio

import androidx.lifecycle.viewModelScope
import base.utils.MatrixUtils.Media.MATRIX_REPOSITORY_PREFIX
import base.utils.getExtensionFromMimeType
import base.utils.orZero
import com.fleeksoft.ksoup.Ksoup
import data.io.base.BaseResponse
import data.io.social.network.conversation.message.MediaIO
import data.shared.SharedModel
import database.file.FileAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import ui.conversation.components.link.GraphProtocol

internal val mediaProcessorModule = module {
    factory { MediaProcessorDataManager() }
    single { MediaProcessorDataManager() }
    factory { MediaProcessorModel(get(), get()) }
    factory { MediaProcessorRepository(get<FileAccess>(), get(), get(), get()) }
    viewModelOf(::MediaProcessorModel)
}

data class MediaHttpProgress(
    val id: String = "",
    val items: Int,
    val item: Int,
    val progress: LongRange?
)

/**
 * Media processor model for downloading and caching media files
 * @see [MediaIO]
 */
class MediaProcessorModel(
    private val repository: MediaProcessorRepository,
    private val dataManager: MediaProcessorDataManager
): SharedModel() {
    private val _resultByteArray = MutableStateFlow<ByteArray?>(null)
    private val _resultData = MutableStateFlow<Map<MediaIO, ByteArray>>(mapOf())
    private val _media = MutableStateFlow(listOf<MediaIO>())
    private val _downloadProgress = MutableStateFlow<MediaHttpProgress?>(null)
    private val _graphProtocol = MutableStateFlow<GraphProtocol?>(null)

    /** Result of the downloaded byte array from an url */
    val resultByteArray = _resultByteArray.asStateFlow()

    /** Result of the downloaded data from an url */
    val resultData = _resultData.asStateFlow()

    /** Progress of the current download */
    val downloadProgress = _downloadProgress.asStateFlow()

    /** Locally cached files mapped to local paths */
    val cachedFiles = dataManager.cachedFiles.asStateFlow()

    /** Resulting graph protocol from a website fetcher */
    val graphProtocol = _graphProtocol.asStateFlow()
    val media = _media.asStateFlow()

    private val awaitingDownloads = MutableStateFlow(listOf<MediaIO>())
    private val schedulerMutex = Mutex()

    init {
        viewModelScope.launch {
            currentUser.combine(awaitingDownloads) { user, scheduled ->
                user to scheduled
            }.collectLatest { (user, scheduled) ->
                if(scheduled.isNotEmpty() && user?.matrixHomeserver != null) {
                    schedulerMutex.withLock {
                        awaitingDownloads.value = listOf()
                        cacheFiles(*scheduled.toTypedArray())
                    }
                }
            }
        }
    }

    /** Download the remote [ByteArray] by [url] */
    fun downloadAudioByteArray(url: String) {
        viewModelScope.launch {
            val downloadUrl = if(currentUser.value?.matrixHomeserver != null) {
                url.takeIf { !it.startsWith(MATRIX_REPOSITORY_PREFIX) }
                    ?: "https://${currentUser.value?.matrixHomeserver}/_matrix/client/v1/media/download/${url.removePrefix(MATRIX_REPOSITORY_PREFIX)}"
            }else ""

            repository.getFileByteArray(
                url = url,
                downloadUrl = downloadUrl,
                onProgressChange = { bytesSentTotal, contentLength ->
                    _downloadProgress.value = MediaHttpProgress(
                        items = 1,
                        item = 1,
                        progress = if(contentLength == null) null else (bytesSentTotal..contentLength)
                    )
                }
            )?.let { file ->
                // wav to PCM
                _resultByteArray.value = file.byteArray.copyOfRange(44, file.byteArray.size)
            }
        }
    }

    /** Flushes the cached downloaded data */
    fun flush() {
        _resultByteArray.value = null
        _resultData.value = mapOf()
        _downloadProgress.value = null
    }

    fun retrieveMedia(idList: List<String?>) {
        viewModelScope.launch(Dispatchers.Default) {
            _media.value = repository.retrieveMedia(idList.filterNotNull())
        }
    }

    /** Attempts to retrieve bytearrays out of urls */
    fun downloadFiles(vararg media: MediaIO?) {
        viewModelScope.launch {
            _downloadProgress.value = MediaHttpProgress(
                items = media.size,
                item = 0,
                progress = null
            )
            var bytesSentTotal = 0L
            val totalContentSize = media.sumOf { it?.size.orZero() }
            _resultData.value = media.mapIndexedNotNull { index, unit ->
                (if(unit == null) null else repository.getFileByteArray(
                    url = unit.url ?: "",
                    onProgressChange = { bytesSent, _ ->
                        _downloadProgress.value = MediaHttpProgress(
                            items = media.size,
                            item = index + 1,
                            progress = (bytesSentTotal + bytesSent)..totalContentSize
                        )
                    }
                )).let {
                    bytesSentTotal += unit?.size.orZero()
                    if(it == null || unit == null) null else unit to it.byteArray
                }
            }.toMap()
            _downloadProgress.value = null
        }
    }

    private val cacheMutex = Mutex()

    /** Attempts to retrieve bytearrays out of urls and cache them */
    fun cacheFiles(vararg media: MediaIO?) {
        viewModelScope.launch {
            cacheMutex.withLock {
                _downloadProgress.value = MediaHttpProgress(
                    items = media.size,
                    item = 0,
                    progress = null
                )
                var bytesSentTotal = 0L
                val totalContentSize = media.sumOf { it?.size.orZero() }
                val toBeScheduled = mutableListOf<MediaIO>()
                media.forEachIndexed { index, unit ->
                    if(unit != null && dataManager.cachedFiles.value[unit.url] == null) {
                        val unknownHomeserver = currentUser.value?.matrixHomeserver == null
                        val downloadUrl = if(unit.url != null && !unknownHomeserver) {
                            unit.url.takeIf { !it.startsWith(MATRIX_REPOSITORY_PREFIX) }
                                ?: "https://${currentUser.value?.matrixHomeserver}/_matrix/client/v1/media/download/${unit.url.replace(MATRIX_REPOSITORY_PREFIX, "")}"
                        }else ""

                        dataManager.cachedFiles.update {
                            it.toMutableMap().apply {
                                put(unit.url ?: "", BaseResponse.Loading)
                            }
                        }

                        repository.getFileByteArray(
                            url = unit.url ?: "",
                            downloadUrl = downloadUrl,
                            extension = getExtensionFromMimeType(unit.mimetype),
                            onProgressChange = { bytesSent, _ ->
                                _downloadProgress.value = MediaHttpProgress(
                                    items = media.size,
                                    item = index + 1,
                                    progress = (bytesSentTotal + bytesSent)..totalContentSize
                                )
                            }
                        ).let { result ->
                            if(result == null && unknownHomeserver) {
                                toBeScheduled.add(unit)
                            }
                            dataManager.cachedFiles.update {
                                it.toMutableMap().apply {
                                    put(
                                        unit.url ?: "",
                                        if(result != null) {
                                            bytesSentTotal += unit.size.orZero()
                                            BaseResponse.Success(
                                                unit.copy(
                                                    path = result.path?.toString(),
                                                    mimetype = result.mimetype
                                                )
                                            )
                                        }else BaseResponse.Error()
                                    )
                                }
                            }
                        }
                    }
                }
                if(toBeScheduled.isNotEmpty()) {
                    schedulerMutex.withLock {
                        awaitingDownloads.update { it + toBeScheduled }
                    }
                }
                _downloadProgress.value = null
            }
        }
    }

    /** Requests open graph protocol data out of an [url] */
    fun requestGraphProtocol(url: String) {
        viewModelScope.launch {
            Ksoup.parseMetaData(html = repository.getUrlContent(url) ?: "").let { metadata ->
                _graphProtocol.value = GraphProtocol(
                    title = metadata.ogTitle,
                    description = metadata.ogDescription,
                    imageUrl = metadata.ogImage,
                    iconUrl = metadata.favicon
                )
            }
        }
    }
}
