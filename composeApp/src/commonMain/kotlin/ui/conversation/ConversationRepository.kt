package ui.conversation

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import augmy.interactive.shared.utils.DateUtils
import base.utils.MediaType
import base.utils.orZero
import base.utils.toSha256
import data.io.base.BaseResponse
import data.io.matrix.media.FileList
import data.io.matrix.media.MediaRepositoryConfig
import data.io.matrix.media.MediaUploadResponse
import data.io.matrix.room.ConversationRoomIO
import data.io.matrix.room.FullConversationRoom
import data.io.matrix.room.event.ConversationRoomMember
import data.io.matrix.room.event.ConversationTypingIndicator
import data.io.social.network.conversation.MessageReactionRequest
import data.io.social.network.conversation.message.ConversationMessageIO
import data.io.social.network.conversation.message.ConversationMessagesResponse
import data.io.social.network.conversation.message.FullConversationMessage
import data.io.social.network.conversation.message.MediaIO
import data.io.social.network.conversation.message.MessageReactionIO
import data.io.user.NetworkItemIO
import data.shared.SharedDataManager
import data.shared.sync.DataSyncHandler
import data.shared.sync.MessageProcessor
import database.dao.ConversationMessageDao
import database.dao.ConversationRoomDao
import database.dao.MediaDao
import database.dao.MessageReactionDao
import database.dao.RoomMemberDao
import database.file.FileAccess
import io.github.vinceglb.filekit.PlatformFile
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import net.folivo.trixnity.client.MatrixClient
import net.folivo.trixnity.client.room.encrypt
import net.folivo.trixnity.client.roomEventEncryptionServices
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.m.Mentions
import net.folivo.trixnity.core.model.events.m.ReactionEventContent
import net.folivo.trixnity.core.model.events.m.RelatesTo
import net.folivo.trixnity.core.model.events.m.room.AudioInfo
import net.folivo.trixnity.core.model.events.m.room.FileInfo
import net.folivo.trixnity.core.model.events.m.room.ImageInfo
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import org.koin.mp.KoinPlatform
import ui.conversation.components.audio.MediaProcessorDataManager
import ui.login.safeRequest

/** Class for calling APIs and remote work in general */
open class ConversationRepository(
    private val httpClient: HttpClient,
    private val conversationMessageDao: ConversationMessageDao,
    internal val conversationRoomDao: ConversationRoomDao,
    mediaDataManager: MediaProcessorDataManager,
    fileAccess: FileAccess
): MediaRepository(httpClient, mediaDataManager, fileAccess) {
    private val sharedDataManager by lazy { KoinPlatform.getKoin().get<SharedDataManager>() }
    private val dataSyncHandler by lazy { KoinPlatform.getKoin().get<DataSyncHandler>() }
    private val mediaDao by lazy { KoinPlatform.getKoin().get<MediaDao>() }
    private val roomMemberDao by lazy { KoinPlatform.getKoin().get<RoomMemberDao>() }
    private val messageReactionDao by lazy { KoinPlatform.getKoin().get<MessageReactionDao>() }

    protected var currentPagingSource: PagingSource<*, *>? = null
    val cachedFiles = MutableStateFlow(hashMapOf<String, PlatformFile?>())
    protected var certainMessageCount: Int? = null

    companion object {
        const val MENTION_REGEX_USER_ID = """.*\/#\/(@[^"]+)"""
        private const val MENTION_REGEX = """<a href="$MENTION_REGEX_USER_ID">([^<]+)<\/a>"""
        const val REGEX_HTML_MENTION = """<a href=".*(@[^"]+)">([^<]+)<\/a>"""
    }

    /** Attempts to invalidate local PagingSource with conversation messages */
    fun invalidateLocalSource() {
        certainMessageCount = null
        currentPagingSource?.invalidate()
    }

    /**
     * returns a list of network list
     * @param dir - direction of the list
     * @param fromBatch - The token to start returning events from, usually the "prev_batch" of the timeline.
     */
    private suspend fun getMessages(
        dir: String = "b",
        homeserver: String,
        fromBatch: String? = null,
        limit: Int,
        conversationId: String?
    ): BaseResponse<ConversationMessagesResponse> {
        return withContext(Dispatchers.IO) {
            if(conversationId == null) {
                return@withContext BaseResponse.Error()
            }

            httpClient.safeRequest<ConversationMessagesResponse> {
                get(
                    urlString = "https://${homeserver}/_matrix/client/v3/rooms/$conversationId/messages",
                    block =  {
                        parameter("limit", limit)
                        parameter("dir", dir)
                        parameter("from", fromBatch)
                    }
                )
            }
        }
    }

    private suspend fun getAndStoreNewMessages(
        homeserver: String,
        fromBatch: String?,
        limit: Int,
        conversationId: String?
    ): MessageProcessor.SaveEventsResult? {
        return if(conversationId != null) {
            getMessages(
                limit = limit,
                conversationId = conversationId,
                fromBatch = fromBatch,
                homeserver = homeserver
            ).success?.data?.let { data ->
                dataSyncHandler.saveEvents(
                    events = data.chunk.orEmpty() + data.state.orEmpty(),
                    roomId = conversationId,
                    prevBatch = data.end
                )
            }
        }else null
    }

    suspend fun insertMemberByUserId(
        conversationId: String,
        userId: String,
        homeserver: String
    ) {
        return withContext(Dispatchers.IO) {
            if (roomMemberDao.get(userId) == null) {
                val remoteInfo = getRemoteUser(userId, homeserver)

                roomMemberDao.insertReplace(
                    ConversationRoomMember(
                        userId = userId,
                        roomId = conversationId,
                        sender = UserId(userId),
                        timestamp = DateUtils.now.toEpochMilliseconds(),
                        isDirect = true,
                        membership = Membership.INVITE,
                        avatarUrl = remoteInfo?.avatarUrl,
                        displayName = remoteInfo?.displayName
                    )
                )
            }
        }
    }

    suspend fun insertConversation(conversation: ConversationRoomIO) {
        return withContext(Dispatchers.IO) {
            conversationRoomDao.insert(conversation)
        }
    }

    suspend fun getRemoteUser(
        userId: String,
        homeserver: String
    ) = httpClient.safeRequest<NetworkItemIO> {
        httpClient.get(urlString = "https://${homeserver}/_matrix/client/v3/profile/${userId}")
    }.success?.data

    suspend fun getRoomIdByUser(userId: String): String? {
        return withContext(Dispatchers.IO) {
            conversationRoomDao.getAll().let { rooms ->
                withContext(Dispatchers.Default) {
                    rooms.find {
                        it.data.summary?.isDirect == true && roomMemberDao.getOfRoom(it.data.id).firstOrNull()?.userId == userId
                    }?.data?.id ?: rooms.find {
                        roomMemberDao.getOfRoom(it.data.id).size == 1 && roomMemberDao.getOfRoom(it.data.id).firstOrNull()?.userId == userId
                    }?.data?.id

                }
            }
        }
    }

    suspend fun recommendUsersToInvite(
        limit: Int,
        excludeMembers: List<String>
    ): List<ConversationRoomMember> {
        return withContext(Dispatchers.IO) {
            roomMemberDao.getSorted(excludeIds = excludeMembers, limit = limit)
        }
    }

    suspend fun queryUsersToInvite(
        query: String,
        excludeMembers: List<String>,
        limit: Int
    ): List<ConversationRoomMember> {
        return withContext(Dispatchers.IO) {
            roomMemberDao.query(query, excludeIds = excludeMembers, limit = limit)
        }
    }

    /** Returns a flow of conversation messages */
    fun getMessagesListFlow(
        homeserver: () -> String,
        config: PagingConfig,
        conversationId: String? = null
    ): Pager<Int, FullConversationMessage> {
        return Pager(
            config = config,
            pagingSourceFactory = {
                ConversationRoomSource(
                    getMessages = { page ->
                        if(conversationId.isNullOrBlank()) {
                            return@ConversationRoomSource GetMessagesResponse(
                                data = listOf(), hasNext = false
                            )
                        }

                        withContext(Dispatchers.IO) {
                            val prevBatch = conversationRoomDao.get(conversationId)?.data?.prevBatch

                            conversationMessageDao.getPaginated(
                                conversationId = conversationId,
                                limit = config.pageSize,
                                offset = page * config.pageSize
                            ).let { res ->
                                if(res.isNotEmpty()) {
                                    GetMessagesResponse(
                                        data = res,
                                        hasNext = res.size == config.pageSize || prevBatch != null
                                    )
                                }else null
                            } ?: if(prevBatch != null) {
                                getAndStoreNewMessages(
                                    limit = config.pageSize,
                                    conversationId = conversationId,
                                    fromBatch = prevBatch,
                                    homeserver = homeserver()
                                )?.let { res ->
                                    certainMessageCount = certainMessageCount?.plus(res.messages.size)
                                    GetMessagesResponse(
                                        data = res.messages,
                                        hasNext = res.prevBatch != null && res.changeInMessages
                                    ).also {
                                        val newPrevBatch = if(res.changeInMessages) res.prevBatch else null

                                        conversationRoomDao.setPrevBatch(
                                            id = conversationId,
                                            prevBatch = newPrevBatch
                                        )
                                        if(newPrevBatch == null) {
                                            // we just downloaded empty page, let's refresh UI to end paging
                                            invalidateLocalSource()
                                        }
                                    }
                                }
                            }else GetMessagesResponse(data = emptyList(), hasNext = false)
                        }
                    },
                    getCount = {
                        certainMessageCount ?: conversationMessageDao.getCount(conversationId = conversationId).also {
                            certainMessageCount = it
                        }
                    },
                    size = config.pageSize
                ).also { pagingSource ->
                    currentPagingSource = pagingSource
                }
            }
        )
    }

    /** Returns a detailed information about a conversation */
    suspend fun getConversationDetail(
        conversationId: String,
        owner: String?
    ): FullConversationRoom? = withContext(Dispatchers.IO) {
        conversationRoomDao.get(conversationId, ownerPublicId = owner)
    }

    /** Informs Matrix about typing progress */
    suspend fun updateTypingIndicator(
        conversationId: String,
        indicator: ConversationTypingIndicator
    ): BaseResponse<Any> = withContext(Dispatchers.IO) {
        val userId = sharedDataManager.currentUser.value?.userId
        val homeserver = sharedDataManager.currentUser.value?.matrixHomeserver

        httpClient.safeRequest<Any> {
            put("https://${homeserver}/_matrix/client/v3/rooms/${conversationId}/typing/${userId}") {
                setBody(indicator)
            }
        }
    }

    suspend fun sendMessage(
        client: MatrixClient?,
        conversationId: String,
        message: ConversationMessageIO,
        media: List<MediaIO>? = null
    ): Result<EventId>? = withContext(Dispatchers.IO) {
        val roomId = RoomId(conversationId)
        val mentions = Mentions(
            users = MENTION_REGEX.toRegex().findAll(message.content ?: "").map { UserId(it.groupValues[1]) }.toSet(),
            room = false // TODO ability to mention rooms 86c46m1bq
        )
        val content = message.content?.replace(MENTION_REGEX.toRegex()) { it.groupValues[2] } ?: ""

        val eventContent = when {
            media?.size == 1 -> {
                val media = media.firstOrNull()
                when(MediaType.fromMimeType(media?.mimetype ?: "")) {
                    MediaType.AUDIO -> RoomMessageEventContent.FileBased.Audio(
                        body = content,
                        url = media?.url,
                        fileName = media?.name,
                        info = AudioInfo(
                            mimeType = media?.mimetype,
                            size = media?.size
                        ),
                        mentions = mentions,
                        relatesTo = message.relatesTo()
                    )
                    MediaType.GIF, MediaType.IMAGE -> RoomMessageEventContent.FileBased.Image(
                        body = content,
                        url = media?.url,
                        fileName = media?.name,
                        info = ImageInfo(
                            mimeType = media?.mimetype,
                            size = media?.size
                        ),
                        mentions = mentions,
                        relatesTo = message.relatesTo()
                    )
                    else -> RoomMessageEventContent.FileBased.File(
                        body = content,
                        url = media?.url,
                        fileName = media?.name,
                        info = FileInfo(
                            mimeType = media?.mimetype,
                            size = media?.size
                        ),
                        mentions = mentions,
                        relatesTo = message.relatesTo()
                    )
                }
            }
            media?.size.orZero() > 1 -> FileList(
                body = content,
                urls = media?.mapNotNull { it.url },
                fileName = media?.firstOrNull()?.name,
                infos = media?.map { media ->
                    FileInfo(
                        mimeType = media.mimetype,
                        size = media.size
                    )
                },
                mentions = mentions,
                relatesTo = message.relatesTo()
            )
            else -> RoomMessageEventContent.TextBased.Text(
                body = content,
                formattedBody = message.content ?: "",
                format = "org.matrix.custom.html",
                mentions = mentions,
                relatesTo = message.relatesTo()
            )
        }

        client?.api?.room?.sendMessageEvent(
            roomId = roomId,
            eventContent = client.roomEventEncryptionServices.encrypt(
                content = eventContent,
                roomId = roomId,
            )?.getOrNull() ?: eventContent
        )
    }

    suspend fun saveMedia(media: MediaIO) = withContext(Dispatchers.IO) {
        mediaDao.insertReplace(media)
    }

    suspend fun removeAllMediaOf(messageId: String) = withContext(Dispatchers.IO) {
        mediaDao.removeAllOf(messageId)
    }

    suspend fun indexOfMessage(
        messageId: String,
        conversationId: String
    ) = withContext(Dispatchers.IO) {
        if (conversationMessageDao.get(messageId) != null) {
            conversationMessageDao.getMessagesAfterCount(messageId, conversationId)
        } else null
    }

    suspend fun cacheMessage(message: ConversationMessageIO) = withContext(Dispatchers.IO) {
        conversationMessageDao.insertReplace(message)
    }

    suspend fun removeMessage(id: String) = withContext(Dispatchers.IO) {
        conversationMessageDao.remove(id)
    }

    /** Marks a message as transcribed */
    suspend fun markMessageAsTranscribed(id: String) {
        withContext(Dispatchers.IO) {
            conversationMessageDao.transcribe(messageId = id, transcribed = true)
        }
    }

    /** Adds or changes reaction on a message */
    suspend fun reactToMessage(
        conversationId: String,
        reaction: MessageReactionRequest
    ): BaseResponse<Pair<MessageReactionIO, Boolean>> {
        return withContext(Dispatchers.Default) {
            val self = sharedDataManager.currentUser.value?.userId
            val data = MessageReactionIO(
                content = reaction.content,
                authorPublicId = self,
                messageId = reaction.messageId,
                sentAt = DateUtils.localNow
            )
            conversationMessageDao.get(reaction.messageId)?.let { message ->
                val existingReaction = message.reactions.find {
                    it.authorPublicId == self && it.content == reaction.content
                }

                // add new reaction
                if (existingReaction == null) {
                    messageReactionDao.insertReplace(data) // placeholder local reaction
                    invalidateLocalSource()

                    sharedDataManager.matrixClient.value?.api?.room?.sendMessageEvent(
                        roomId = RoomId(conversationId),
                        eventContent = ReactionEventContent(
                            relatesTo = RelatesTo.Annotation(
                                eventId = EventId(reaction.messageId),
                                key = reaction.content
                            )
                        )
                    )?.getOrNull().let { eventId ->
                        messageReactionDao.remove(data.eventId)

                        (if (eventId != null) { // replace placeholder with real event
                            messageReactionDao.insertReplace(data.copy(eventId = eventId.full))
                            BaseResponse.Success(data.copy(eventId = eventId.full) to true)
                        }else BaseResponse.Error()).also {
                            invalidateLocalSource()
                        }
                    }
                } else {    // remove existing reaction
                    messageReactionDao.remove(existingReaction.eventId)
                    invalidateLocalSource()

                    sharedDataManager.matrixClient.value?.api?.room?.redactEvent(
                        roomId = RoomId(conversationId),
                        eventId = EventId(existingReaction.eventId)
                    )?.getOrNull().let { eventId ->
                        if (eventId == null) {
                            messageReactionDao.insertReplace(existingReaction)
                            invalidateLocalSource()
                            BaseResponse.Error()
                        } else BaseResponse.Success(data.copy(eventId = eventId.full) to false)
                    }
                }
            } ?: BaseResponse.Error()
        }
    }

    /** Returns configuration of the homeserver */
    suspend fun getMediaConfig(homeserver: String): BaseResponse<MediaRepositoryConfig> {
        return httpClient.safeRequest<MediaRepositoryConfig> {
            get(url = Url("https://$homeserver/_matrix/client/v1/media/config"))
        }
    }
}

open class MediaRepository(
    private val httpClient: HttpClient,
    private val mediaDataManager: MediaProcessorDataManager,
    private val fileAccess: FileAccess
) {

    /**
     * Uploads media to the server
     * @return the server location URL
     */
    suspend fun uploadMedia(
        mediaByteArray: ByteArray?,
        mimetype: String,
        homeserver: String,
        fileName: String
    ): BaseResponse<MediaUploadResponse>? = withContext(Dispatchers.IO) {
        try {
            if(mediaByteArray == null) null
            else {
                httpClient.safeRequest<MediaUploadResponse> {
                    post(url = Url("https://$homeserver/_matrix/media/v3/upload")) {
                        header(HttpHeaders.ContentType, mimetype)
                        parameter("filename", fileName)
                        setBody(mediaByteArray)
                    }
                }.also {
                    it.success?.success?.data?.contentUri?.let { uri ->
                        fileAccess.saveFileToCache(
                            data = mediaByteArray,
                            fileName = uri.toSha256()
                        )?.let { path ->
                            mediaDataManager.cachedFiles.value = mediaDataManager.cachedFiles.value.toMutableMap().apply {
                                put(
                                    path.toString(),
                                    BaseResponse.Success(
                                        MediaIO(
                                            url = uri,
                                            mimetype = mimetype,
                                            size = mediaByteArray.size.toLong(),
                                            name = fileName
                                        )
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

const val MESSAGE_AUDIO_URL_PLACEHOLDER = "audio_placeholder"
