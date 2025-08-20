package ui.conversation

import androidx.lifecycle.viewModelScope
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import augmy.interactive.shared.ext.ifNull
import augmy.interactive.shared.ui.components.input.DELAY_BETWEEN_TYPING_SHORT
import augmy.interactive.shared.utils.DateUtils.localNow
import augmy.interactive.shared.utils.PersistentListData
import base.utils.MatrixUtils
import base.utils.getUrlExtension
import base.utils.toSha256
import components.pull_refresh.RefreshableViewModel
import data.io.app.SettingsKeys
import data.io.base.BaseResponse
import data.io.matrix.room.ConversationRoomIO
import data.io.matrix.room.RoomSummary
import data.io.matrix.room.RoomType
import data.io.matrix.room.event.ConversationRoomMember
import data.io.matrix.room.event.ConversationTypingIndicator
import data.io.social.network.conversation.MessageReactionRequest
import data.io.social.network.conversation.giphy.GifAsset
import data.io.social.network.conversation.message.ConversationMessageIO
import data.io.social.network.conversation.message.FullConversationMessage
import data.io.social.network.conversation.message.MediaIO
import data.io.social.network.conversation.message.MessageReactionIO
import data.io.social.network.conversation.message.MessageState
import data.shared.GeneralObserver
import database.file.FileAccess
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.extension
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readBytes
import korlibs.io.net.MimeType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.folivo.trixnity.clientserverapi.model.rooms.CreateRoom
import net.folivo.trixnity.clientserverapi.model.rooms.DirectoryVisibility
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.InitialStateEvent
import net.folivo.trixnity.core.model.events.m.room.EncryptionEventContent
import net.folivo.trixnity.core.model.events.m.room.GuestAccessEventContent
import net.folivo.trixnity.core.model.events.m.room.HistoryVisibilityEventContent
import net.folivo.trixnity.core.model.events.m.room.HistoryVisibilityEventContent.HistoryVisibility
import net.folivo.trixnity.core.model.events.m.room.JoinRulesEventContent
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import ui.conversation.components.KeyboardModel
import ui.conversation.components.audio.MediaHttpProgress
import ui.conversation.components.emoji.EmojiUseCase
import ui.conversation.components.experimental.gravity.GravityData
import ui.conversation.components.experimental.gravity.GravityUseCase
import ui.conversation.components.experimental.gravity.GravityUseCase.Companion.TICK_MILLIS
import ui.conversation.components.experimental.gravity.GravityValue
import ui.conversation.components.experimental.pacing.PacingUseCase
import ui.conversation.components.experimental.pacing.PacingUseCase.Companion.WAVES_PER_PIXEL
import ui.conversation.components.gif.GifUseCase
import ui.conversation.components.keyboardModule
import utils.SharedLogger
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

internal val conversationModule = module {
    includes(keyboardModule)

    factory { ConversationDataManager() }
    single { ConversationDataManager() }
    factory {
        ConversationRepository(
            get(),
            get(),
            get(),
            get(),
            get<FileAccess>()
        )
    }

    factory { (conversationId: String?, userId: String?, enableMessages: Boolean, scrollTo: String?, joinRule: String?) ->
        ConversationModel(
            conversationId = MutableStateFlow(conversationId ?: ""),
            userId = userId,
            enableMessages = enableMessages,
            scrollTo = scrollTo,
            joinRule = joinRule,
            get<ConversationRepository>(),
            get<ConversationDataManager>(),
            get<EmojiUseCase>(),
            get<GifUseCase>(),
            get<FileAccess>(),
            get<PacingUseCase>(),
            get<GravityUseCase>(),
        )
    }
    viewModel { (conversationId: String?, userId: String?, enableMessages: Boolean, scrollTo: String?, joinRule: String?) ->
        ConversationModel(
            conversationId = MutableStateFlow(conversationId ?: ""),
            userId = userId,
            enableMessages = enableMessages,
            scrollTo = scrollTo,
            joinRule = joinRule,
            get<ConversationRepository>(),
            get<ConversationDataManager>(),
            get<EmojiUseCase>(),
            get<GifUseCase>(),
            get<FileAccess>(),
            get<PacingUseCase>(),
            get<GravityUseCase>(),
        )
    }
}

/** Communication between the UI, the control layers, and control and data layers */
open class ConversationModel(
    protected var conversationId: MutableStateFlow<String>,
    private val userId: String? = null,
    enableMessages: Boolean = true,
    private val scrollTo: String? = null,
    private val joinRule: String? = null,
    private val repository: ConversationRepository,
    private val dataManager: ConversationDataManager,
    emojiUseCase: EmojiUseCase,
    gifUseCase: GifUseCase,
    private val fileAccess: FileAccess,

    // experimental
    private val pacingUseCase: PacingUseCase,
    private val gravityUseCase: GravityUseCase
): KeyboardModel(
    emojiUseCase = emojiUseCase,
    gifUseCase = gifUseCase,
    conversationId = conversationId.value
), RefreshableViewModel {

    enum class UiMode {
        Idle,
        IdleNoRoom,
        InternalError,
        CreatingRoom,
        CreateRoomNoMembers,
        Preview,
        Restricted,
        Knock
    }

    override val isRefreshing = MutableStateFlow(false)
    override var lastRefreshTimeMillis = 0L

    private val _uploadProgress = MutableStateFlow<List<MediaHttpProgress>>(emptyList())
    private val _uiMode = MutableStateFlow<UiMode>(UiMode.Idle)
    private val _mentionRecommendations = MutableStateFlow<List<ConversationRoomMember>?>(null)
    private val _recommendedUsersToInvite = MutableStateFlow(listOf<ConversationRoomMember>())
    private val _membersToInvite = MutableStateFlow(mutableSetOf<ConversationRoomMember>())
    private val _joinResponse = MutableStateFlow<BaseResponse<RoomId>>(BaseResponse.Idle)
    private val _knockResponse = MutableStateFlow<BaseResponse<RoomId>>(BaseResponse.Idle)

    // firstVisibleItemIndex to firstVisibleItemScrollOffset
    var persistentPositionData: PersistentListData? = null

    /** Detailed information about this conversation */
    val conversation = dataManager.conversations.combine(conversationId) { conversation, id ->
        conversation.second[id]
    }

    /** Current typing indicators, indicating typing statuses of other users */
    val typingIndicators = sharedDataManager.typingIndicators.combine(conversationId) { indicators, conversationId ->
        withContext(Dispatchers.Default) {
            indicators.second[conversationId]?.userIds?.mapNotNull { userId ->
                if(userId.full != matrixUserId) {
                    ConversationTypingIndicator().apply {
                        user = dataManager.conversations.value.second[conversationId]?.members?.find { user ->
                            user.userId == userId.full
                        }
                    }
                }else null
            }.let {
                // hashcode to enforce recomposition
                it.hashCode() to it?.takeLast(MAX_TYPING_INDICATORS).orEmpty()
            }
        }
    }

    /** flow of current messages */
    @OptIn(ExperimentalCoroutinesApi::class)
    val conversationMessages: Flow<PagingData<FullConversationMessage>> = if (enableMessages) {
        conversationId.flatMapLatest { conversationId ->
            repository.getMessagesListFlow(
                config = PagingConfig(
                    pageSize = 30,
                    enablePlaceholders = true,
                    initialLoadSize = 30
                ),
                homeserver = { homeserverAddress },
                conversationId = conversationId
            ).flow.cachedIn(viewModelScope)
        }
    }else flow { PagingData.empty<ConversationMessageIO>() }

    /** Current configuration of media repository */
    val repositoryConfig = dataManager.repositoryConfig.asStateFlow()

    /** List of mentions based on the typed text */
    val mentionRecommendations = _mentionRecommendations.asStateFlow()

    val recommendedUsersToInvite = _recommendedUsersToInvite.asStateFlow()
    val membersToInvite = _membersToInvite.asStateFlow()

    val joinResponse = _joinResponse.asStateFlow()
    val knockResponse = _knockResponse.asStateFlow()

    val uiMode = _uiMode.asStateFlow()

    /** Progress of the current upload */
    val uploadProgress = _uploadProgress.asStateFlow()

    /** currently locally cached byte arrays */
    val temporaryFiles
        get() = repository.cachedFiles

    /** Last saved message relevant to this conversation */
    val savedMessage = MutableStateFlow<String?>(null)
    val scrollToIndex = MutableSharedFlow<Int>()
    val timingSensor = pacingUseCase.timingSensor
    val gravityValues = gravityUseCase.gravityValues.asStateFlow()

    private val messageMaxLength = 5000

    init {
        viewModelScope.launch {
            _uiMode.value = when {
                joinRule != null -> when (joinRule) {
                    JoinRulesEventContent.JoinRule.Public.name -> UiMode.Preview
                    JoinRulesEventContent.JoinRule.Knock.name,
                    JoinRulesEventContent.JoinRule.KnockRestricted.name -> UiMode.Knock
                    else -> UiMode.Restricted
                }
                !conversationId.value.isBlank() -> UiMode.Idle
                // no conversationId, attempt to retrieve it from userId
                userId != null -> repository.getRoomIdByUser(userId)?.let { newConversationId ->
                    conversationId.value = newConversationId
                    UiMode.Idle
                } ?: UiMode.IdleNoRoom
                else -> UiMode.CreateRoomNoMembers
            }

            onDataRequest(isSpecial = false, isPullRefresh = false)
        }
        scrollTo?.let { messageId ->
            scrollTo(messageId)
        }
    }

    // ==================== functions ===========================

    override suspend fun onDataRequest(isSpecial: Boolean, isPullRefresh: Boolean) {
        if ((conversationId.value.isNotBlank()
                    && dataManager.conversations.value.second[conversationId.value] == null)
            || isSpecial
        ) {
            withContext(Dispatchers.IO) {
                repository.getConversationDetail(
                    conversationId = conversationId.value,
                    owner = matrixUserId
                )?.let { data ->
                    dataManager.updateConversations { it.apply { this[conversationId.value] = data } }
                    dataManager.conversations.value.second[conversationId.value] = data
                }.ifNull {
                    // only if there is no detail existing
                    if (joinRule == JoinRulesEventContent.JoinRule.Public.name) {
                        _uiMode.value = UiMode.Preview
                    }
                }
            }
        }
        withContext(Dispatchers.IO) {
            savedMessage.value = settings.getStringOrNull(
                "${SettingsKeys.KEY_LAST_MESSAGE}_$conversationId"
            )?.take(messageMaxLength) ?: ""

            if(dataManager.repositoryConfig.value == null || isSpecial) {
                currentUser.value?.matrixHomeserver?.let { homeserver ->
                    dataManager.repositoryConfig.value = repository.getMediaConfig(homeserver = homeserver).success?.data
                }
            }
        }

        if (isSpecial) {
            repository.invalidateLocalSource()
        }
    }

    override fun onCleared() {
        gravityUseCase.kill()
        super.onCleared()
    }

    /** Experimental typing services */
    fun startTypingServices() {
        if(!timingSensor.value.isRunning && !timingSensor.value.isLocked) {
            viewModelScope.launch {
                timingSensor.value.start()
                gravityUseCase.start()
            }
        }
    }

    fun stopTypingServices() {
        if(timingSensor.value.isRunning) {
            timingSensor.value.pause()
            gravityUseCase.kill()
        }
    }

    fun recommendMentions(input: CharSequence?) {
        viewModelScope.launch(Dispatchers.Default) {
            if (input == null) {
                _mentionRecommendations.value = null
                return@launch
            } else {
                val strippedInput = input.toString().lowercase().removePrefix("@")

                _mentionRecommendations.value = dataManager.conversations.value.second[conversationId.value]?.members?.filter { member ->
                    (strippedInput.isEmpty() // no filter yet
                            || member.displayName?.lowercase()?.startsWith(strippedInput) == true)
                            || member.userId.lowercase().startsWith(strippedInput)
                }?.take(4).orEmpty()
            }
        }
    }

    /** Either calls for most relevant recommendations or queries all users */
    private val recommendScope = CoroutineScope(Job())
    private val _recommendUsersState = MutableStateFlow<BaseResponse<Any>>(BaseResponse.Idle)
    val recommendUsersState = _recommendUsersState.asStateFlow()

    fun recommendUsersToInvite(query: CharSequence? = null) {
        _recommendUsersState.value = BaseResponse.Loading
        recommendScope.coroutineContext.cancelChildren()
        recommendScope.launch(Dispatchers.Default) {
            delay(DELAY_BETWEEN_TYPING_SHORT)
            val limit = 7

            _recommendedUsersToInvite.value = (if (query.isNullOrBlank()) {
                repository.recommendUsersToInvite(
                    limit = limit,
                    excludeMembers = _membersToInvite.value.map { it.id }
                )
            } else {
                repository.queryUsersToInvite(
                    query = query.toString(),
                    excludeMembers = _membersToInvite.value.map { it.id },
                    limit = limit
                )
            }).distinctBy { it.userId }.filter {
                it.userId != matrixUserId
                        && membersToInvite.value.none { member -> member.userId == it.userId  }
            }
            _recommendUsersState.value = BaseResponse.Idle
        }
    }

    fun selectInvitedMember(
        member: ConversationRoomMember,
        add: Boolean = true
    ) {
        _membersToInvite.update { prev ->
            prev.apply {
                removeAll { it.userId == member.userId }

                if(add) add(member) else remove(member)
            }
        }
    }

    fun updateTypingStatus(content: CharSequence) {
        viewModelScope.launch {
            if (content.isNotBlank()) {
                repository.updateTypingIndicator(
                    conversationId = conversationId.value,
                    indicator = ConversationTypingIndicator(content = content.toString())
                )
            }
        }
    }

    fun onKeyPressed(char: Char, timingMs: Long) {
        viewModelScope.launch {
            pacingUseCase.onKeyPressed(char, timingMs)
        }
    }

    fun initPacing(widthPx: Float) {
        if(pacingUseCase.isInitialized) return
        viewModelScope.launch {
            gravityUseCase.init(conversationId.value)
            pacingUseCase.init(
                maxWaves = (WAVES_PER_PIXEL * widthPx).toInt(),
                conversationId = conversationId.value,
                savedMessage = savedMessage.value ?: ""
            )
            pacingUseCase.timingSensor.value.onTick(ms = TICK_MILLIS) {
                viewModelScope.launch { gravityUseCase.onTick() }
            }
            if(!savedMessage.value.isNullOrBlank()) startTypingServices()
        }
    }

    /** Saves the content of a message */
    fun cache(content: String?) {
        CoroutineScope(Job() + Dispatchers.IO).launch {
            val key = "${SettingsKeys.KEY_LAST_MESSAGE}_$conversationId"
            if(content != null) {
                settings.putString(key, content)
            }else settings.remove(key)

            if(content != null) {
                pacingUseCase.cache(conversationId.value)
            }else {
                pacingUseCase.clearCache(conversationId.value)
                gravityUseCase.clearCache(conversationId.value)
            }
            savedMessage.value = content
        }
    }

    /** Marks a single message as transcribed */
    fun markMessageAsTranscribed(id: String?) {
        if(id == null) return
        viewModelScope.launch {
            repository.markMessageAsTranscribed(id = id)
        }
    }

    private suspend fun joinRoom(reason: String): Boolean {
        if (_uiMode.value != UiMode.Preview) return false

        _joinResponse.value = BaseResponse.Loading
        return matrixClient?.api?.room?.joinRoom(
            roomId = RoomId(conversationId.value),
            reason = reason.takeIf { it.isNotBlank() }
        )?.getOrElse { error ->
            _joinResponse.value = BaseResponse.Error(code = error.message)
            null
        }?.let {
            _uiMode.value = UiMode.Idle
            _joinResponse.value = BaseResponse.Success(it)
            true
        }.ifNull { false }
    }

    private fun knockOnRoom(reason: String) {
        if (_uiMode.value != UiMode.Knock) return

        _knockResponse.value = BaseResponse.Loading
        viewModelScope.launch {
            matrixClient?.api?.room?.knockRoom(
                roomId = RoomId(conversationId.value),
                reason = reason.takeIf { it.isNotBlank() }
            )?.getOrElse { error ->
                _knockResponse.value = BaseResponse.Error(code = error.message)
                null
            }?.let {
                _knockResponse.value = BaseResponse.Success(it)
            }
        }
    }

    private suspend fun createMissingRoom(): Boolean {
        if (_membersToInvite.value.size == 1) {
            _membersToInvite.value.firstOrNull()?.userId?.let {
                repository.getRoomIdByUser(it)?.let { userId ->
                    conversationId.value = userId
                    _uiMode.value = UiMode.Idle
                    onDataRequest(isSpecial = false, isPullRefresh = false)
                    if (conversationId.value.isNotBlank()) return false
                }
            }
        }

        _uiMode.value = UiMode.CreatingRoom
        val users = _membersToInvite.value.map { UserId(it.userId) }.toMutableSet().apply {
            userId?.let { add(UserId(it)) }
        }

        return matrixClient?.api?.room?.createRoom(
            visibility = DirectoryVisibility.PRIVATE,
            initialState = listOf<InitialStateEvent<*>>(
                InitialStateEvent(GuestAccessEventContent(GuestAccessEventContent.GuestAccessType.FORBIDDEN), ""),
                InitialStateEvent(HistoryVisibilityEventContent(HistoryVisibility.INVITED), ""),
                InitialStateEvent(EncryptionEventContent(algorithm = EncryptionAlgorithm.Megolm), ""),
                MatrixUtils.defaultRoomPowerLevels
            ),
            roomVersion = "11",
            isDirect = userId != null,
            preset = CreateRoom.Request.Preset.TRUSTED_PRIVATE,
            invite = users,
        )?.getOrNull()?.full?.let { newConversationId ->
            _uiMode.value = UiMode.Idle
            repository.insertConversation(
                ConversationRoomIO(
                    id = newConversationId,
                    summary = RoomSummary(
                        heroes = users.toList(),
                        isDirect = userId != null
                    ),
                    ownerPublicId = matrixUserId,
                    historyVisibility = HistoryVisibility.INVITED,
                    prevBatch = null,
                    type = RoomType.Joined
                )
            )
            userId?.let {
                repository.insertMemberByUserId(
                    conversationId = newConversationId,
                    userId = it,
                    homeserver = homeserverAddress
                )
            }
            conversationId.value = newConversationId
            true
        }.ifNull { false }
    }

    /**
     * Makes a request to send a conversation message
     * @param content textual content of the message
     * @param mediaFiles list of urls in the format of: [Pair.first]: file name, [Pair.second]: [ByteArray] content of the image
     */
    fun sendMessage(
        content: String,
        anchorMessage: ConversationMessageIO?,
        mediaFiles: List<PlatformFile>,
        mediaUrls: List<String>,
        timings: List<Long>,
        gravityValues: List<GravityValue>,
        gifAsset: GifAsset?,
        showPreview: Boolean
    ) {
        CoroutineScope(Job()).launch {
            val repeat = {
                sendMessage(
                    content = content,
                    anchorMessage = anchorMessage,
                    mediaFiles = mediaFiles,
                    mediaUrls = mediaUrls,
                    timings = timings,
                    gravityValues = gravityValues,
                    gifAsset = gifAsset,
                    showPreview = showPreview
                )
            }
            when (_uiMode.value) {
                UiMode.CreateRoomNoMembers, UiMode.IdleNoRoom -> {  // no conversation room yet, let's create it
                    if (createMissingRoom()) repeat()
                }
                UiMode.Preview -> {
                    if (joinRoom(content)) repeat()
                }
                UiMode.Knock -> {
                    knockOnRoom(content)
                }
                else -> {
                    var progressId = ""
                    val media = mediaUrls.mapNotNull { url ->
                        MediaIO(
                            url = url,
                            mimetype = MimeType.getByExtension(getUrlExtension(url)).mime,
                        ).takeIf { url.isNotBlank() }
                    }
                    sendMessage(
                        conversationId = conversationId.value,
                        anchorMessage = anchorMessage,
                        homeserver = homeserverAddress,
                        mediaFiles = mediaFiles,
                        onProgressChange = { progress ->
                            _uploadProgress.update {
                                it.toMutableList().apply {
                                    add(progress)
                                    progressId = progress.id
                                }
                            }
                        },
                        gifAsset = gifAsset,
                        message = ConversationMessageIO(
                            content = content,
                            gravityData = GravityData(
                                values = gravityValues.map { it.copy(conversationId = null) },
                                tickMs = TICK_MILLIS
                            ),
                            anchorMessageId = anchorMessage?.id,
                            parentAnchorMessageId = anchorMessage?.anchorMessageId,
                            showPreview = showPreview,
                            timings = timings
                        ),
                        mediaIn = media
                    )
                    _uploadProgress.update { previous ->
                        previous.toMutableList().apply {
                            removeAll { it.id == progressId }
                        }
                    }
                }
            }
        }
    }

    private val scrollMutex = Mutex()
    fun scrollTo(messageId: String?) {
        if (messageId == null) return
        SharedLogger.logger.debug { "scrollTo, messageId: $messageId, conversation: ${conversationId.value}" }

        CoroutineScope(Job()).launch {
            scrollMutex.withLock {
                repository.indexOfMessage(messageId, conversationId.value).also {
                    SharedLogger.logger.debug { "scrollTo, messageId: $messageId, index: $it" }
                    // messageId: $VbX838MXJO_4aLApBpmwRnVa1W1u_NOmPyBhc_r2brs, conversation: !KVYcKAMBaUKZEebhSQ:matrix.org
                }?.let { index ->
                    scrollToIndex.emit((index - 5).coerceAtLeast(0))
                }
            }
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private suspend fun sendMessage(
        conversationId: String,
        homeserver: String,
        message: ConversationMessageIO,
        mediaIn: List<MediaIO> = listOf(),
        anchorMessage: ConversationMessageIO? = null,
        audioByteArray: ByteArray? = null,
        gifAsset: GifAsset? = null,
        onProgressChange: ((MediaHttpProgress) -> Unit)? = null,
        mediaFiles: List<PlatformFile> = listOf()
    ) {
        val uuids = mutableListOf<String>()
        val msgId = "temporary_${Uuid.random()}"
        var media = withContext(Dispatchers.Default) {
            mediaFiles.map { media ->
                MediaIO(
                    url = (Uuid.random().toString() + ".${media.extension.lowercase()}").also { uuid ->
                        repository.cachedFiles.update { prev ->
                            prev.apply { this[uuid] = media }
                        }
                        uuids.add(uuid)
                    },
                    messageId = msgId
                )
            }.toMutableList().apply {
                addAll(mediaIn)
                if(audioByteArray != null) {
                    add(MediaIO(url = MESSAGE_AUDIO_URL_PLACEHOLDER, messageId = msgId))
                }
                add(
                    MediaIO(
                        url = gifAsset?.original,
                        mimetype = MimeType.IMAGE_GIF.mime,
                        name = gifAsset?.description,
                        messageId = msgId
                    )
                )
            }.filter { !it.isEmpty }
        }

        // placeholder/loading message preview
        var msg = message.copy(
            id = msgId,
            conversationId = conversationId,
            sentAt = localNow,
            authorPublicId = currentUser.value?.userId,
            state = MessageState.Pending
        )
        repository.cacheMessage(msg)
        media.forEach { repository.saveMedia(it) }
        repository.invalidateLocalSource()

        // real message
        media = withContext(Dispatchers.Default) {
            mediaFiles.mapNotNull { file ->
                val bytes = file.readBytes()
                if(bytes.isNotEmpty()) {
                    repository.uploadMedia(
                        mediaByteArray = bytes,
                        fileName = file.name,
                        homeserver = homeserver,
                        mimetype = MimeType.getByExtension(file.extension).mime
                    )?.success?.data?.contentUri.takeIf { !it.isNullOrBlank() }?.let { url ->
                        MediaIO(
                            url = url,
                            messageId = msgId,
                            size = bytes.size.toLong(),
                            name = file.name,
                            mimetype = MimeType.getByExtension(file.extension).mime
                        )
                    }
                }else null
            }.toMutableList().apply {
                // existing media
                addAll(mediaIn.toMutableList().filter { !it.isEmpty })

                // audio file
                if(audioByteArray != null) {
                    val size = audioByteArray.size.toLong()
                    val fileName = "${Uuid.random()}.wav"
                    val mimetype = MimeType.getByExtension("wav").mime

                    repository.uploadMedia(
                        mediaByteArray = audioByteArray,
                        fileName = fileName,
                        homeserver = homeserver,
                        mimetype = mimetype
                    )?.success?.data?.contentUri.let { audioUrl ->
                        if(!audioUrl.isNullOrBlank()) {
                            fileAccess.saveFileToCache(
                                data = audioByteArray,
                                fileName = audioUrl.toSha256()
                            )?.let { res ->
                                removeAll { it.url == MESSAGE_AUDIO_URL_PLACEHOLDER }
                                add(
                                    MediaIO(
                                        url = audioUrl,
                                        size = size,
                                        name = fileName,
                                        mimetype = mimetype,
                                        path = res.toString(),
                                        messageId = msgId
                                    )
                                )
                            }
                        }
                    }
                }

                // GIPHY asset
                if(!gifAsset?.original.isNullOrBlank()) {
                    MediaIO(
                        url = gifAsset.original,
                        mimetype = MimeType.IMAGE_GIF.mime,
                        name = gifAsset.description,
                        messageId = msgId
                    )
                }
            }
        }
        msg = msg.copy(
            state = if(sharedDataManager.matrixClient.value != null) MessageState.Pending else MessageState.Failed
        )

        if(sharedDataManager.matrixClient.value != null) {
            repository.sendMessage(
                client = sharedDataManager.matrixClient.value,
                conversationId = conversationId,
                message = msg,
                media = media
            )?.let { result ->
                // we don't need the local data anymore
                repository.removeMessage(msg.id)
                repository.removeAllMediaOf(msgId)
                if(result.isSuccess) {
                    repository.cachedFiles.update { prev ->
                        prev.apply {
                            uuids.forEachIndexed { index, s ->
                                media.getOrNull(index)?.url?.let { remove(it) }
                                remove(s)
                            }
                        }
                    }

                    sharedDataManager.observers.forEach { observer ->
                        if (observer is GeneralObserver.MessageObserver) {
                            observer.invoke(
                                FullConversationMessage(
                                    data = msg,
                                    anchorMessage = anchorMessage,
                                    author = ConversationRoomMember(
                                        userId = matrixUserId ?: "",
                                        roomId = conversationId
                                    )
                                )
                            )
                        }
                    }
                }

                // save the actual version
                result.getOrNull()?.full?.let { messageId ->
                    repository.cacheMessage(
                        msg.copy(
                            id = messageId,
                            state = if(result.isSuccess) MessageState.Sent else MessageState.Failed
                        )
                    )
                    media.distinctBy { it.url }.forEach {
                        repository.saveMedia(it.copy(messageId = messageId, conversationId = conversationId))
                    }
                }
            }
        }else repository.cacheMessage(msg)

        repository.invalidateLocalSource()
    }

    /** Makes a request to add or change reaction to a message */
    fun reactToMessage(messageId: String?, content: String) {
        if(messageId == null) return
        viewModelScope.launch {
            repository.reactToMessage(
                conversationId = conversationId.value,
                reaction = MessageReactionRequest(
                    content = forceEmojiPresentation(content),
                    messageId = messageId
                )
            ).data?.let { reaction ->
                sharedDataManager.observers.forEach { observer ->
                    if (observer is GeneralObserver.ReactionsObserver) {
                        observer.invoke(reaction.first.apply {
                            type = if (reaction.second) MessageReactionIO.ReactionType.Add else MessageReactionIO.ReactionType.Remove
                        })
                    }
                }
            }
        }
    }

    fun onState(type: ConversationStateType) {
        sharedDataManager.observers.forEach { observer ->
            if (observer is GeneralObserver.ConversationStateObserver) {
                observer.invoke(
                    ConversationState(
                        type = type,
                        conversationId = conversationId.value
                    )
                )
            }
        }
    }

    private fun forceEmojiPresentation(
        emoji: String
    ) = if (!emoji.contains('\uFE0F')) emoji + '\uFE0F' else emoji

    /** Makes a request to send a conversation audio message */
    fun sendAudioMessage(byteArray: ByteArray) {
        viewModelScope.launch {
            sendMessage(
                audioByteArray = byteArray,
                conversationId = conversationId.value,
                homeserver = homeserverAddress,
                message = ConversationMessageIO()
            )
        }
    }
}

private const val MAX_TYPING_INDICATORS = 3
