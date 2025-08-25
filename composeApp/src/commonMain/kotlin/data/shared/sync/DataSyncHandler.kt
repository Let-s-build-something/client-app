package data.shared.sync

import data.io.base.AppPing
import data.io.base.AppPingType
import data.io.matrix.room.ConversationRoomIO
import data.io.matrix.room.RoomSummary
import data.io.matrix.room.RoomType
import data.io.matrix.room.event.ConversationTypingIndicator
import data.io.social.network.conversation.message.MediaIO
import database.dao.ConversationRoomDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import net.folivo.trixnity.clientserverapi.model.sync.Sync
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent
import net.folivo.trixnity.core.model.events.m.DirectEventContent
import net.folivo.trixnity.core.model.events.m.TypingEventContent
import net.folivo.trixnity.core.model.events.m.room.AvatarEventContent
import net.folivo.trixnity.core.model.events.m.room.CanonicalAliasEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptionEventContent
import net.folivo.trixnity.core.model.events.m.room.HistoryVisibilityEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.NameEventContent
import net.folivo.trixnity.core.model.events.m.room.PowerLevelsEventContent
import net.folivo.trixnity.core.model.events.originTimestampOrNull
import org.koin.mp.KoinPlatform

class DataSyncHandler: MessageProcessor() {

    private val conversationRoomDao: ConversationRoomDao by KoinPlatform.getKoin().inject()

    fun stop() {
        decryptionScope.coroutineContext.cancelChildren()
    }

    suspend fun handle(
        response: Sync.Response,
        owner: String
    ) {
        withContext(Dispatchers.Default) {
            val matrixRooms = response.room?.let { matrixRooms ->
                mutableListOf<ConversationRoomIO>().apply {
                    addAll(matrixRooms.join?.map { it.value.asConversation(id = it.key) }.orEmpty())
                    addAll(matrixRooms.invite?.map { it.value.asConversation(id = it.key) }.orEmpty())
                    addAll(matrixRooms.knock?.map { it.value.asConversation(id = it.key) }.orEmpty())
                    addAll(matrixRooms.leave?.map { it.value.asConversation(id = it.key) }.orEmpty())
                }
            }
            val rooms = mutableListOf<ConversationRoomIO>()
            val directRoomIds = mutableSetOf<RoomId>()

            response.accountData?.events?.forEach { event ->
                when (val content = event.content) {
                    is DirectEventContent -> {
                        directRoomIds.addAll(content.mappings.flatMap { it.value.orEmpty() })
                    }
                }
            }

            matrixRooms?.forEach { room ->
                val isDirect = directRoomIds.contains(RoomId(room.id))
                var alias: String? = null
                var name: String? = null
                var avatar: AvatarEventContent? = null
                var historyVisibility: HistoryVisibilityEventContent.HistoryVisibility? = null
                var algorithm: EncryptionEventContent? = null
                val members = mutableListOf<Pair<Boolean?, String?>>()
                var powerLevels: Pair<LocalDateTime, PowerLevelsEventContent>? = null

                val events = mutableListOf<ClientEvent<*>>()
                    .apply {
                        addAll(room.accountData?.events.orEmpty())
                        addAll(room.ephemeral?.events.orEmpty())
                        addAll(room.state?.events.orEmpty())
                        addAll(room.timeline?.events.orEmpty())
                        addAll(room.inviteState?.events.orEmpty())
                        addAll(room.knockState?.events.orEmpty())
                    }
                    .sortedBy { it.originTimestampOrNull }
                    .map { event ->
                        // preprocessing of the room and adding info for further processing
                        when(val content = event.content) {
                            is HistoryVisibilityEventContent -> historyVisibility = content.historyVisibility
                            is CanonicalAliasEventContent -> {
                                alias = (content.alias ?: content.aliases?.firstOrNull())?.full
                            }
                            is MemberEventContent -> {
                                members.add(content.isDirect to content.displayName)
                            }
                            is NameEventContent -> name = content.name
                            is AvatarEventContent -> avatar = content
                            is EncryptionEventContent -> algorithm = content
                            is TypingEventContent -> {
                                sharedDataManager.typingIndicators.update { prev ->
                                    prev.second.apply {
                                        this[room.id] = ConversationTypingIndicator(userIds = content.users)
                                    }.let {
                                        it.hashCode() to it
                                    }
                                }
                            }
                            is PowerLevelsEventContent -> {
                                event.originTimestampOrNull?.let { millis ->
                                    val sentAt = Instant.fromEpochMilliseconds(millis)
                                        .toLocalDateTime(TimeZone.currentSystemDefault())
                                    powerLevels = sentAt to content
                                }
                            }
                            else -> {}
                        }

                        with(event) {
                            when (this) {
                                is RoomEvent.MessageEvent -> {
                                    copy(roomId = roomId.takeIf { it.full.isNotBlank() } ?: RoomId(room.id))
                                }
                                is RoomEvent.StateEvent -> {
                                    copy(roomId = roomId.takeIf { it.full.isNotBlank() } ?: RoomId(room.id))
                                }
                                is ClientEvent.RoomAccountDataEvent -> {
                                    copy(roomId = roomId.takeIf { it.full.isNotBlank() } ?: RoomId(room.id))
                                }
                                is ClientEvent.StrippedStateEvent -> {
                                    copy(roomId = roomId.takeIf { !it?.full.isNullOrBlank() } ?: RoomId(room.id))
                                }
                                is ClientEvent.EphemeralEvent -> {
                                    copy(roomId = roomId.takeIf { !it?.full.isNullOrBlank() } ?: RoomId(room.id))
                                }
                                else -> this
                            }
                        }
                    }

                val hasNewPowerLevels = powerLevels?.let {
                    it.first >= (room.summary?.powerLevelsTime ?: it.first)
                } ?: false
                val newItem = room.copy(
                    summary = (room.summary ?: RoomSummary()).copy(
                        avatar = avatar?.url?.takeIf { it.isNotBlank() }?.let {
                            MediaIO(
                                url = it,
                                mimetype = avatar.info?.mimeType,
                                size = avatar.info?.size,
                                messageId = room.id
                            )
                        },
                        canonicalAlias = alias
                            ?: name
                            ?: room.summary?.canonicalAlias
                            ?: (if (isDirect) room.summary?.heroes?.firstOrNull()?.full ?: members.first { it.first != false }.second else null),
                        isDirect = isDirect,
                        powerLevels = powerLevels?.takeIf { hasNewPowerLevels }?.second ?: room.summary?.powerLevels,
                        powerLevelsTime = if (hasNewPowerLevels) powerLevels.first else room.summary?.powerLevelsTime
                    ),
                    prevBatch = room.timeline?.previousBatch,
                    ownerPublicId = owner,
                    historyVisibility = historyVisibility,
                    primaryKey = "${room.id}_${owner}",
                    algorithm = algorithm?.algorithm
                )

                saveEvents(
                    events = events,
                    prevBatch = newItem.prevBatch?.takeIf { room.timeline?.limited == true },
                    roomId = newItem.id
                ).also { res ->
                    if (res.changeInMessages) {
                        dataService.appendPing(
                            AppPing(
                                type = AppPingType.Conversation,
                                identifier = room.id
                            )
                        )
                    }

                    // either update existing one, or insert new one
                    newItem.copy(summary = newItem.summary ?: RoomSummary()).let { roomUpdate ->
                        (conversationRoomDao.get(
                            id = room.id,
                            ownerPublicId = owner
                        )?.data?.update(roomUpdate) ?: roomUpdate).also { data ->
                            conversationRoomDao.insert(data)
                            rooms.add(data)
                        }
                    }
                }
            }

            dataService.appendPing(AppPing(type = AppPingType.ConversationDashboard))
        }
    }

    private fun Sync.Response.Rooms.JoinedRoom.asConversation(id: RoomId) = ConversationRoomIO(
        id = id.full,
        unreadNotifications = unreadNotifications,
        summary = RoomSummary(
            heroes = summary?.heroes,
            joinedMemberCount = summary?.joinedMemberCount?.toInt(),
            invitedMemberCount = summary?.invitedMemberCount?.toInt()
        ),
        type = RoomType.Joined
    ).also {
        it.state = state
        it.timeline = timeline
        it.accountData = accountData
        it.ephemeral = ephemeral
    }

    private fun Sync.Response.Rooms.KnockedRoom.asConversation(id: RoomId) = ConversationRoomIO(
        id = id.full,
        knockState = strippedState,
        type = RoomType.Knocked
    )

    private fun Sync.Response.Rooms.InvitedRoom.asConversation(id: RoomId) =  ConversationRoomIO(
        id = id.full,
        inviteState = strippedState,
        type = RoomType.Invited
    )

    private fun Sync.Response.Rooms.LeftRoom.asConversation(id: RoomId) = ConversationRoomIO(
        id = id.full,
        type = RoomType.Left
    ).also {
        it.state = state
        it.timeline = timeline
        it.accountData = accountData
    }
}