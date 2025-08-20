package ui.conversation.settings

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import base.global.verification.ComparisonByUserData
import base.utils.orZero
import base.utils.tagToColor
import data.NetworkProximityCategory
import data.io.base.BaseResponse
import data.io.matrix.room.FullConversationRoom
import data.io.matrix.room.event.ConversationRoomMember
import data.io.social.network.conversation.message.MediaIO
import data.io.user.NetworkItemIO
import data.shared.SharedModel
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.extension
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readBytes
import korlibs.io.net.MimeType
import korlibs.io.util.getOrNullLoggingError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Contextual
import net.folivo.trixnity.client.verification
import net.folivo.trixnity.client.verification.ActiveUserVerification
import net.folivo.trixnity.client.verification.ActiveVerificationState
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.EventType
import net.folivo.trixnity.core.model.events.m.room.AvatarEventContent
import net.folivo.trixnity.core.model.events.m.room.ImageInfo
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.events.m.room.NameEventContent
import net.folivo.trixnity.core.model.events.m.room.PowerLevelsEventContent
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import ui.conversation.ConversationDataManager

val conversationSettingsModule = module {
    factory { ConversationDataManager() }
    single { ConversationDataManager() }
    factory {
        ConversationSettingsRepository(get(), get(), get(), get(), get(), get(), get(), get(), get())
    }
    viewModelOf(::ConversationSettingsModel)
}

class ConversationSettingsModel(
    private val conversationId: String,
    private val repository: ConversationSettingsRepository,
    private val dataManager: ConversationDataManager
): SharedModel() {
    companion object {
        const val SHIMMER_ITEM_COUNT = 4
        const val MAX_MEMBERS_COUNT = 6
        const val PAGE_ITEM_COUNT = 20

        fun ActiveVerificationState.isFinished() = this is ActiveVerificationState.Cancel
                || this is ActiveVerificationState.Done
                || this is ActiveVerificationState.WaitForDone
    }

    sealed class ChangeType(open val state: BaseResponse<*>) {
        data class Avatar(override val state: BaseResponse<*>): ChangeType(state)
        data class Name(override val state: BaseResponse<*>): ChangeType(state)
        data class Leave(override val state: BaseResponse<*>): ChangeType(state)
        data class InviteMember(override val state: BaseResponse<*>): ChangeType(state)
        data class VerifyMember(
            override val state: BaseResponse<*>,
            val data: ComparisonByUserData? = null
        ): ChangeType(state)
    }

    private val _ongoingChange = MutableStateFlow<ChangeType?>(null)
    private val _selectedInvitedUser = MutableStateFlow<NetworkItemIO?>(null)
    private val _verifications = MutableStateFlow<HashMap<String, ActiveUserVerification?>>(hashMapOf())

    /** Detailed information about this conversation */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val _conversation = MutableStateFlow(dataManager.conversations.value.second[conversationId])

    val ongoingChange = _ongoingChange.asStateFlow()
    val conversation = _conversation.asStateFlow()
    val selectedInvitedUser = _selectedInvitedUser.asStateFlow()
    val verifications = _verifications.asStateFlow()

    val members: Flow<PagingData<ConversationRoomMember>> = repository.getMembersListFlow(
        config = PagingConfig(
            pageSize = PAGE_ITEM_COUNT,
            enablePlaceholders = true
        ),
        homeserver = { homeserverAddress },
        ignoreUserId = matrixUserId,
        conversationId = conversationId
    ).flow.cachedIn(viewModelScope)

    @OptIn(ExperimentalCoroutinesApi::class)
    val myPowerLevel = _conversation.mapLatest {
        it?.data?.summary?.powerLevels?.users?.get(UserId(matrixUserId ?: "")) ?: it?.data?.summary?.powerLevels?.usersDefault.orZero()
    }

    /** Customized social circle colors */
    val socialCircleColors: Flow<Map<NetworkProximityCategory, Color>> = localSettings.map { settings ->
        withContext(Dispatchers.Default) {
            settings?.networkColors?.mapIndexedNotNull { index, s ->
                tagToColor(s)?.let { color ->
                    NetworkProximityCategory.entries[index] to color
                }
            }.orEmpty().toMap()
        }
    }

    init {
        viewModelScope.launch {
            if((conversationId.isNotBlank() && dataManager.conversations.value.second[conversationId] == null)) {
                withContext(Dispatchers.IO) {
                    repository.getConversationDetail(
                        conversationId = conversationId,
                        owner = matrixUserId
                    )?.let { data ->
                        dataManager.updateConversations { prev ->
                            prev.apply {
                                this[conversationId] = if (data.data.summary?.powerLevels == null) {
                                    data.copy(
                                        data = data.data.copy(
                                            summary = data.data.summary?.copy(
                                                powerLevels = matrixClient?.api?.room?.getStateEvent(
                                                    "m.room.power_levels",
                                                    RoomId(conversationId)
                                                )?.getOrNull() as? PowerLevelsEventContent
                                            )
                                        ).also {
                                            repository.updateRoom(it)
                                        }
                                    )
                                } else data
                            }
                        }
                        _conversation.value = data
                    }
                }
            }
        }

        viewModelScope.launch {
            dataManager.conversations.collect { stream ->
                stream.second[conversationId]?.let {
                    _conversation.value = it
                }
            }
        }
    }


    /** Removes a member out of a conversation */
    fun kickMember(member: ConversationRoomMember, onFinish: () -> Unit) {
        viewModelScope.launch {
            sharedDataManager.matrixClient.value?.api?.room?.kickUser(
                roomId = RoomId(conversationId),
                userId = UserId(member.userId)
            )
            repository.updateRoomMember(member.copy(membership = Membership.LEAVE))
            onFinish()
        }
    }

    fun banMember(member: ConversationRoomMember, onFinish: () -> Unit) {
        viewModelScope.launch {
            sharedDataManager.matrixClient.value?.api?.room?.banUser(
                roomId = RoomId(conversationId),
                userId = UserId(member.userId)
            )
            repository.updateRoomMember(member.copy(membership = Membership.BAN))
            onFinish()
        }
    }

    fun changePowerOf(member: ConversationRoomMember, power: Long) {
        viewModelScope.launch {
            val update = ((matrixClient?.api?.room?.getStateEvent(
                "m.room.power_levels",
                RoomId(conversationId)
            )?.getOrNull() as? PowerLevelsEventContent) ?: conversation.value?.data?.summary?.powerLevels)?.let {
                it.copy(
                    users = it.users.plus(UserId(member.userId) to power)
                )
            }

            if (update != null) {
                if (matrixClient?.api?.room?.sendStateEvent(
                    roomId = RoomId(conversationId),
                    eventContent = update
                )?.getOrNull() != null) {
                    conversation.value?.data?.copy(
                        summary = conversation.value?.data?.summary?.copy(powerLevels = update)
                    )?.let { newRoom ->
                        repository.updateRoom(newRoom)
                        dataManager.updateConversations { prev ->
                            prev.apply {
                                this[conversationId] = this[conversationId]?.copy(data = newRoom) ?: FullConversationRoom(newRoom)
                            }
                        }
                    }
                }
            }
        }
    }

    fun updateRoomPowers(
        invite: Long,
        ban: Long,
        kick: Long,
        redact: Long,
        events: Map<@Contextual EventType, Long>
    ) {
        viewModelScope.launch {
            val update = ((matrixClient?.api?.room?.getStateEvent(
                "m.room.power_levels",
                RoomId(conversationId)
            )?.getOrNull() as? PowerLevelsEventContent) ?: conversation.value?.data?.summary?.powerLevels)?.let {
                it.copy(
                    invite = invite,
                    ban = ban,
                    kick = kick,
                    redact = redact,
                    events = it.events + events
                )
            }

            if (update != null) {
                if (matrixClient?.api?.room?.sendStateEvent(
                    roomId = RoomId(conversationId),
                    eventContent = update
                )?.getOrNull() != null) {
                    conversation.value?.data?.copy(
                        summary = conversation.value?.data?.summary?.copy(powerLevels = update)
                    )?.let { newRoom ->
                        repository.updateRoom(newRoom)
                        dataManager.updateConversations { prev ->
                            prev.apply {
                                this[conversationId] = this[conversationId]?.copy(data = newRoom) ?: FullConversationRoom(newRoom)
                            }
                        }
                    }
                }
            }
        }
    }

    fun requestRoomNameChange(roomName: CharSequence) {
        _ongoingChange.value = ChangeType.Name(BaseResponse.Loading)
        viewModelScope.launch {
            sharedDataManager.matrixClient.value?.api?.room?.sendStateEvent(
                roomId = RoomId(conversationId),
                eventContent = NameEventContent(name = roomName.toString())
            ).also { res ->
                _ongoingChange.value = ChangeType.Name(
                    if(res?.getOrNull() != null) {
                        dataManager.updateConversations { prev ->
                            prev.apply {
                                val conversation = this[conversationId]
                                conversation?.copy(
                                    data = conversation.data.copy(
                                        summary = conversation.data.summary?.copy(canonicalAlias = roomName.toString())
                                    )
                                )?.let {
                                    set(conversationId, it)
                                    repository.updateRoom(it.data)
                                }
                            }
                        }
                        BaseResponse.Success(null)
                    }else BaseResponse.Error()
                )
            }
        }
    }

    fun leaveRoom(reason: CharSequence) {
        _ongoingChange.value = ChangeType.Leave(BaseResponse.Loading)
        viewModelScope.launch {
            sharedDataManager.matrixClient.value?.api?.room?.leaveRoom(
                roomId = RoomId(conversationId),
                reason = reason.takeIf { it.isNotBlank() }?.toString()
            ).also { res ->
                _ongoingChange.value = ChangeType.Leave(
                    if(res?.getOrNull() != null) {
                        dataManager.updateConversations { prev ->
                            prev.apply {
                                remove(conversationId)
                                repository.removeRoom(
                                    conversationId = conversationId,
                                    ownerPublicId = matrixUserId
                                )
                            }
                        }
                        BaseResponse.Success(null)
                    }else BaseResponse.Error()
                )
            }
        }
    }

    fun selectInvitedUser(userId: String?) {
        viewModelScope.launch {
            _selectedInvitedUser.value = if(userId == null) null else repository.getUser(userId, matrixUserId)
        }
    }

    suspend fun getActiveVerification(
        userId: String,
        unfinishedOnly: Boolean = false
    ) = repository.getPendingVerifications(
        senderUserId = matrixUserId
    ).mapNotNull { message ->
        if(message.verification?.to == userId) {
            matrixClient?.verification?.getActiveUserVerification(
                roomId = RoomId(conversationId),
                eventId = EventId(message.id)
            )?.takeIf { !unfinishedOnly || !it.state.value.isFinished() }
        } else null
    }

    /** Check for a given user's verification state */
    fun checkVerificationState(userId: String?) {
        if(userId == null) return
        viewModelScope.launch {
            _verifications.update {
                it.apply {
                    set(userId, getActiveVerification(userId).firstOrNull())
                }
            }
        }
    }

    fun verifyUser(userId: String?) {
        if(userId == null) return
        _ongoingChange.value = ChangeType.VerifyMember(BaseResponse.Loading)
        viewModelScope.launch {
            if(getActiveVerification(userId = userId, unfinishedOnly = true).isEmpty()) {
                matrixClient?.verification?.createUserVerificationRequest(UserId(userId))
                    ?.getOrNullLoggingError()
                    ?.roomId
                    .let { roomId ->
                        checkVerificationState(userId)
                        _ongoingChange.value = ChangeType.VerifyMember(
                            if(roomId != null) BaseResponse.Success(roomId.full) else BaseResponse.Error()
                        )
                    }
            }
        }
    }

    fun inviteMembers(user: NetworkItemIO) {
        _ongoingChange.value = ChangeType.InviteMember(BaseResponse.Loading)
        viewModelScope.launch {
            val newMember = ConversationRoomMember(
                userId = user.userId ?: "",
                displayName = user.displayName,
                avatarUrl = user.avatar?.url,
                roomId = conversationId,
                membership = Membership.INVITE
            )
            sharedDataManager.matrixClient.value?.api?.room?.inviteUser(
                roomId = RoomId(conversationId),
                userId = UserId(user.userId ?: "")
            ).also { res ->
                _ongoingChange.value = ChangeType.InviteMember(
                    if(res?.getOrNull() != null) {
                        dataManager.updateConversations { prev ->
                            prev.apply {
                                this[conversationId]?.let {
                                    this[conversationId] = it.copy(
                                        data = it.data,
                                        members = this[conversationId]?.members?.toMutableList()?.apply {
                                            add(newMember)
                                        }?.toList() ?: emptyList()
                                    )
                                }
                            }
                        }
                        repository.updateRoomMember(newMember)
                        BaseResponse.Success(null)
                    }else {
                        if (user.displayName == null && user.avatarUrl == null) {
                            repository.removeUser(user.userId ?: "", matrixUserId)
                        }
                        BaseResponse.Error()
                    }
                )
            }
        }
    }

    /**
     * If [file] is not null, it is firstly uploaded to the server and then attempted to be used as a room avatar.
     */
    fun requestAvatarChange(
        file: PlatformFile?,
        url: String?
    ) {
        if(sharedDataManager.matrixClient.value == null) return

        _ongoingChange.value = ChangeType.Avatar(BaseResponse.Loading)
        viewModelScope.launch {
            var media = MediaIO(url = url)
            sharedDataManager.currentUser.value?.matrixHomeserver?.let { homeserver ->
                if(file != null) {
                    repository.uploadMedia(
                        mediaByteArray = file.readBytes(),
                        fileName = file.name,
                        mimetype = MimeType.getByExtension(file.extension).mime,
                        homeserver = homeserver
                    )?.success?.data?.contentUri?.let {
                        media = MediaIO(
                            mimetype = media.mimetype,
                            size = media.size,
                            url = it
                        )
                    }
                }
            }

            sharedDataManager.matrixClient.value?.api?.room?.sendStateEvent(
                roomId = RoomId(conversationId),
                eventContent = AvatarEventContent(
                    url = media.url,
                    info = ImageInfo(
                        mimeType = media.mimetype,
                        size = media.size
                    )
                )
            ).also { res ->
                _ongoingChange.value = ChangeType.Avatar(
                    if(res?.getOrNull() != null) {
                        dataManager.updateConversations { prev ->
                            prev.apply {
                                val conversation = this[conversationId]
                                conversation?.copy(
                                    data = conversation.data.copy(
                                        summary = conversation.data.summary?.copy(avatar = media)
                                    )
                                )?.let {
                                    set(conversationId, it)
                                    repository.updateRoom(it.data)
                                }
                            }
                        }
                        BaseResponse.Success(null)
                    }else BaseResponse.Error()
                )
            }
        }
    }
}
