package data.shared.sync

import augmy.composeapp.generated.resources.Res
import augmy.composeapp.generated.resources.message_alias_change
import augmy.composeapp.generated.resources.message_avatar_change
import augmy.composeapp.generated.resources.message_redacted
import augmy.composeapp.generated.resources.message_room_created
import augmy.composeapp.generated.resources.message_room_kick
import augmy.interactive.shared.utils.DateUtils
import base.utils.orZero
import data.io.base.AppPing
import data.io.base.AppPingType
import data.io.matrix.room.event.ConversationRoomMember
import data.io.social.network.conversation.message.ConversationMessageIO
import data.io.social.network.conversation.message.FullConversationMessage
import data.io.social.network.conversation.message.MediaIO
import data.io.social.network.conversation.message.MessageReactionIO
import data.io.social.network.conversation.message.MessageState
import data.io.user.PresenceData
import data.shared.GeneralObserver
import data.shared.SharedDataManager
import data.shared.sync.EventUtils.asMessage
import database.dao.ConversationMessageDao
import database.dao.MediaDao
import database.dao.MessageReactionDao
import database.dao.PresenceEventDao
import database.dao.RoomMemberDao
import korlibs.io.util.getOrNullLoggingError
import korlibs.logger.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import net.folivo.trixnity.client.room.decrypt
import net.folivo.trixnity.client.roomEventEncryptionServices
import net.folivo.trixnity.core.model.events.ClientEvent
import net.folivo.trixnity.core.model.events.ClientEvent.RoomEvent
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.idOrNull
import net.folivo.trixnity.core.model.events.m.PresenceEventContent
import net.folivo.trixnity.core.model.events.m.ReactionEventContent
import net.folivo.trixnity.core.model.events.m.ReceiptEventContent
import net.folivo.trixnity.core.model.events.m.ReceiptType
import net.folivo.trixnity.core.model.events.m.RelatesTo
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationCancelEventContent
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationDoneEventContent
import net.folivo.trixnity.core.model.events.m.key.verification.VerificationStep
import net.folivo.trixnity.core.model.events.m.room.AvatarEventContent
import net.folivo.trixnity.core.model.events.m.room.CanonicalAliasEventContent
import net.folivo.trixnity.core.model.events.m.room.CreateEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptedMessageEventContent.MegolmEncryptedMessageEventContent
import net.folivo.trixnity.core.model.events.m.room.EncryptedToDeviceEventContent.OlmEncryptedToDeviceEventContent
import net.folivo.trixnity.core.model.events.m.room.MemberEventContent
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.events.m.room.RedactionEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent
import net.folivo.trixnity.core.model.events.m.room.RoomMessageEventContent.FileBased
import net.folivo.trixnity.core.model.events.originTimestampOrNull
import net.folivo.trixnity.core.model.events.senderOrNull
import net.folivo.trixnity.core.model.events.stateKeyOrNull
import org.jetbrains.compose.resources.getString
import org.koin.mp.KoinPlatform
import ui.conversation.message.AUTHOR_SYSTEM
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

abstract class MessageProcessor {

    companion object {
        const val DECRYPTION_TIMEOUT_MS = 50L
    }

    protected val dataService: DataService by KoinPlatform.getKoin().inject()
    protected val sharedDataManager: SharedDataManager by KoinPlatform.getKoin().inject()
    private val conversationMessageDao: ConversationMessageDao by KoinPlatform.getKoin().inject()
    private val mediaDao: MediaDao by KoinPlatform.getKoin().inject()
    private val messageReactionDao: MessageReactionDao by KoinPlatform.getKoin().inject()
    private val roomMemberDao: RoomMemberDao by KoinPlatform.getKoin().inject()
    private val presenceEventDao: PresenceEventDao by KoinPlatform.getKoin().inject()

    protected val decryptionScope = CoroutineScope(Dispatchers.Default)
    private val logger = Logger(name = "MessageProcessor")

    data class SaveEventsResult(
        val messages: List<FullConversationMessage>,
        val changeInMessages: Boolean,
        val events: Int,
        val members: List<ConversationRoomMember>,
        val prevBatch: String?,
        val nextBatch: String? = null,
    )

    suspend fun saveEvents(
        events: List<ClientEvent<*>>,
        roomId: String,
        prevBatch: String?,
        nextBatch: String? = null,
    ): SaveEventsResult {
        return withContext(Dispatchers.IO) {
            val result = processEvents(
                events = events,
                roomId = roomId
            )

            val decryptedMessages = mutableListOf<ConversationMessageIO>()
            result.encryptedEvents.forEach { encrypted ->
                returnOrSaveEncryptedEvent(
                    messageId = encrypted.first,
                    event = encrypted.second,
                    roomId = roomId,
                    receipts = result.receipts
                )?.let { res ->
                    res.first?.let { mediaDao.insertReplace(it) }
                    decryptedMessages.add(res.second)
                }
            }

            val reactionObserver = sharedDataManager.observers.find { it is GeneralObserver.ReactionsObserver }
            mediaDao.insertAll(result.media)
            result.reactions.forEach { reaction ->
                if (messageReactionDao.insertIgnore(reaction) == -1L) {
                    messageReactionDao.insertReplace(reaction)
                } else (reactionObserver as? GeneralObserver.ReactionsObserver)?.invoke(reaction)
            }

            val messageObserver = sharedDataManager.observers.find { it is GeneralObserver.MessageObserver }
            val messages = result.messages.plus(decryptedMessages).mapNotNull { m ->
                val message = m.copy(nextBatch = nextBatch, prevBatch = prevBatch)

                (if (conversationMessageDao.insertIgnore(message) == -1L) {
                    conversationMessageDao.insertReplace(message)
                    null
                }else FullConversationMessage(
                    data = message,
                    reactions = result.reactions.filter { it.messageId == message.id },
                    media = result.media.filter { it.messageId == message.id },
                ))?.also {
                    (messageObserver as? GeneralObserver.MessageObserver)?.run {
                        conversationMessageDao.get(message.id)?.let {
                            invoke(it)
                        }
                    }
                }
            }

            result.receipts.forEach { receipt ->
                receipt.content.events.forEach { event ->
                    if (!event.value[ReceiptType.Read].isNullOrEmpty() || !event.value[ReceiptType.FullyRead].isNullOrEmpty()) {
                        conversationMessageDao.updateState(
                            id = event.key.full,
                            state = MessageState.Read
                        )
                    }
                }
            }

            if(result.presenceData.isNotEmpty()) {
                presenceEventDao.insertAll(result.presenceData)
            }

            val members = result.members.mapNotNull { member ->
                val existingMember = roomMemberDao.get(member.userId)

                val isNew = existingMember == null
                val isNewer = existingMember?.timestamp.orZero() < member.timestamp.orZero()

                if (isNew || isNewer) {
                    roomMemberDao.insertReplace(member)
                    member
                } else null
            }

            result.redactions.forEach { redaction ->
                conversationMessageDao.updateMessage(
                    id = redaction.redacts.full,
                    message = getString(
                        Res.string.message_redacted,
                        redaction.reason ?: ""
                    )
                )
                messageReactionDao.get(redaction.redacts.full)?.let {
                    (reactionObserver as GeneralObserver.ReactionsObserver).invoke(it.apply {
                        type = MessageReactionIO.ReactionType.Remove
                    })
                    messageReactionDao.remove(redaction.redacts.full)
                }
            }

            result.replacements.forEach { replacement ->
                if (conversationMessageDao.get(replacement.key) != null) {
                    conversationMessageDao.updateMessage(
                        id = replacement.key,
                        message = replacement.value?.content ?: ""
                    )
                }else replacement.value?.let {
                    conversationMessageDao.insertReplace(it)
                }
            }

            SaveEventsResult(
                messages = messages,
                members = members.filter { it.membership == Membership.JOIN },
                events = events.size,
                prevBatch = prevBatch,
                changeInMessages = !result.isEmpty,
                nextBatch = nextBatch
            )
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    @Suppress("UNCHECKED_CAST")
    suspend fun processEvents(
        events: List<ClientEvent<*>>,
        roomId: String
    ): ProcessedEvents = withContext(Dispatchers.Default) {
        val messages = mutableListOf<ConversationMessageIO>()
        val media = mutableListOf<MediaIO>()
        val members = mutableListOf<ConversationRoomMember>()
        val receipts = mutableListOf<ClientEvent<ReceiptEventContent>>()
        val encryptedEvents = mutableListOf<Pair<String, RoomEvent.MessageEvent<*>>>()
        val redactions = mutableListOf<RedactionEventContent>()
        val replacements = hashMapOf<String, ConversationMessageIO?>()
        val reactions = mutableSetOf<MessageReactionIO>()
        val presenceData = mutableListOf<PresenceData>()

        events.forEach { event ->
            when (val content = event.content) {
                is MemberEventContent -> {
                    (event.stateKeyOrNull ?: event.senderOrNull?.full
                        ?: content.thirdPartyInvite?.signed?.signed?.userId?.full)?.let { userId ->
                        members.add(
                            ConversationRoomMember(
                                roomId = roomId,
                                timestamp = event.originTimestampOrNull,
                                sender = event.senderOrNull,
                                userId = userId,
                                displayName = content.displayName,
                                avatarUrl = content.avatarUrl,
                                isDirect = content.isDirect,
                                joinAuthorisedViaUsersServer = content.joinAuthorisedViaUsersServer,
                                thirdPartyInvite = content.thirdPartyInvite,
                                reason = content.reason,
                                externalUrl = content.externalUrl,
                                membership = content.membership
                            )
                        )
                    }
                    val isKick = content.membership == Membership.LEAVE
                            && event.stateKeyOrNull != null
                            && event.senderOrNull?.full != event.stateKeyOrNull

                    event.senderOrNull?.full?.let { senderUid ->
                        val sender = if (content.avatarUrl == null) roomMemberDao.get(senderUid) else null

                        (content.avatarUrl ?: sender?.avatarUrl)?.let {
                            event.idOrNull?.full?.let { messageId ->
                                media.add(
                                    MediaIO(
                                        url = it,
                                        name = event.senderOrNull?.localpart,
                                        messageId = messageId,
                                        conversationId = null
                                    )
                                )
                            }
                        }
                        ConversationMessageIO(
                            content = if (isKick) {
                                val target = event.stateKeyOrNull?.let { roomMemberDao.get(it) }

                                getString(
                                    Res.string.message_room_kick,
                                    sender?.displayName ?: roomMemberDao.get(senderUid)?.displayName ?: senderUid,
                                    target?.displayName ?: event.stateKeyOrNull ?: "a member"
                                )
                            } else {
                                content.membership.asMessage(
                                    isSelf = event.senderOrNull?.localpart == sharedDataManager.currentUser.value?.userId,
                                    displayName = content.displayName ?: sender?.displayName ?: roomMemberDao.get(senderUid)?.displayName ?: senderUid
                                )
                            },
                            authorPublicId = AUTHOR_SYSTEM
                        )
                    }
                }
                is ReceiptEventContent -> {
                    (event as? ClientEvent<ReceiptEventContent>)?.let {
                        receipts.add(it)
                    }
                    null
                }
                is PresenceEventContent -> {
                    presenceData.add(
                        PresenceData(
                            userIdFull = event.senderOrNull?.full ?: "",
                            content = content
                        )
                    )
                    null
                }
                is OlmEncryptedToDeviceEventContent, is MegolmEncryptedMessageEventContent -> {
                    if (event is RoomEvent.MessageEvent) {
                        val id = event.idOrNull?.full ?: Uuid.random().toString()
                        encryptedEvents.add(id to event)
                        //ConversationMessageIO(state = MessageState.Decrypting)
                    }
                    null
                }
                is AvatarEventContent -> {
                    content.url?.let {
                        event.idOrNull?.full?.let { messageId ->
                            media.add(
                                MediaIO(
                                    url = it,
                                    name = event.senderOrNull?.localpart,
                                    messageId = messageId,
                                    conversationId = roomId
                                )
                            )
                        }
                    }
                    ConversationMessageIO(
                        content = getString(
                            Res.string.message_avatar_change,
                            event.senderOrNull?.localpart ?: ""
                        ),
                        authorPublicId = AUTHOR_SYSTEM
                    )
                }
                is CanonicalAliasEventContent -> {
                    ConversationMessageIO(
                        content = getString(
                            Res.string.message_alias_change,
                            (content.alias ?: content.aliases?.firstOrNull())?.localpart ?: "",
                            event.senderOrNull?.localpart ?: ""
                        ),
                        authorPublicId = AUTHOR_SYSTEM
                    )
                }
                is ReactionEventContent -> {
                    content.relatesTo?.eventId?.full?.let { eventId ->
                        reactions.add(
                            MessageReactionIO(
                                eventId = event.idOrNull?.full ?: Uuid.random().toString(),
                                messageId = eventId,
                                content = content.relatesTo?.key,
                                authorPublicId = event.senderOrNull?.full,
                                sentAt = event.originTimestampOrNull?.let { millis ->
                                    Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault())
                                }
                            )
                        )
                    }
                    null
                }
                is CreateEventContent -> {
                    ConversationMessageIO(
                        content = getString(
                            Res.string.message_room_created,
                            event.senderOrNull?.localpart ?: content.creator?.localpart ?: "",
                        ),
                        authorPublicId = AUTHOR_SYSTEM
                    )
                }
                is RedactionEventContent -> {
                    redactions.add(content)
                    null
                }
                is MessageEventContent -> {
                    val replacement: RelatesTo.Replace? = if (content.relatesTo is RelatesTo.Replace) {
                        content.relatesTo as RelatesTo.Replace
                    } else null
                    (this as? FileBased)?.takeIf { it.url?.isBlank() == false }?.let {
                        media.add(
                            MediaIO(
                                url = it.url,
                                mimetype = it.info?.mimeType,
                                name = it.fileName,
                                size = it.info?.size,
                                messageId = event.idOrNull?.full ?: "",
                                conversationId = roomId
                            )
                        )
                    }

                    if (replacement != null) {
                        replacements[replacement.eventId.full] = replacement.newContent?.process()
                        null
                    }else content.process()
                }
                else -> null
                /*EmptyEventContent -> TODO()
                is EphemeralDataUnitContent -> TODO()
                is EphemeralEventContent -> TODO()
                is GlobalAccountDataEventContent -> TODO()
                is RoomAccountDataEventContent -> TODO()
                is RedactedEventContent -> TODO()
                is StateEventContent -> TODO()
                is UnknownEventContent -> TODO()
                is ToDeviceEventContent -> TODO()*/
            }?.also { message ->
                messages.add(
                    message.addGeneralInfo(
                        roomId = roomId,
                        receipts = receipts,
                        event = event,
                        messageId = message.id
                    )
                )
            }
        }

        ProcessedEvents(
            messages = messages,
            members = members,
            receipts = receipts,
            redactions = redactions,
            presenceData = presenceData,
            reactions = reactions,
            replacements = replacements,
            encryptedEvents = encryptedEvents,
            media = media
        )
    }

    private suspend fun returnOrSaveEncryptedEvent(
        messageId: String,
        roomId: String,
        receipts: List<ClientEvent<ReceiptEventContent>>,
        event: RoomEvent.MessageEvent<*>,
        save: Boolean = false
    ): Pair<MediaIO?, ConversationMessageIO>? = withContext(Dispatchers.IO) {
        val decryptionStart = DateUtils.now.toEpochMilliseconds()
        withTimeoutOrNull(DECRYPTION_TIMEOUT_MS) {
            decryptEvent(event = event)?.let { content ->
                when (content) {
                    is VerificationCancelEventContent, is VerificationDoneEventContent -> {
                        conversationMessageDao.remove(event.idOrNull?.full ?: messageId)
                        null
                    }
                    else -> content.process()?.addGeneralInfo(
                        roomId = roomId,
                        receipts = receipts,
                        event = event,
                        messageId = messageId
                    )?.let { update ->
                        val media = (content as? FileBased)?.takeIf { it.url?.isBlank() == false }?.let {
                            MediaIO(
                                url = it.url,
                                mimetype = it.info?.mimeType,
                                name = it.fileName,
                                size = it.info?.size,
                                messageId = messageId,
                                conversationId = roomId
                            )
                        }
                        val message = conversationMessageDao.get(messageId)?.data?.update(update) ?: update

                        media to message.copy(
                            state = if (message.authorPublicId == sharedDataManager.currentUser.value?.userId) {
                                MessageState.Sent
                            } else MessageState.Read
                        ).also { decryptedMessage ->
                            if(save) {
                                media?.let { mediaDao.insertReplace(it) }
                                conversationMessageDao.insertReplace(decryptedMessage)

                                dataService.appendPing(
                                    AppPing(
                                        type = AppPingType.Conversation,
                                        identifier = roomId
                                    )
                                )
                            }
                            logger.debug {
                                "decrypted message within ${DateUtils.now.toEpochMilliseconds() - decryptionStart}ms"
                            }
                        }
                    }
                }
            }
        }.also {
            // the decryption timed-out -> put it to the background
            if(it == null && !save) {
                logger.warn { "failed to decrypt within given timeout, putting decryption to background" }
                decryptionScope.launch {
                    returnOrSaveEncryptedEvent(
                        messageId = messageId,
                        roomId = roomId,
                        receipts = receipts,
                        event = event,
                        save = true
                    )
                }
            }
        }
    }

    private fun ConversationMessageIO.addGeneralInfo(
        event: ClientEvent<*>,
        messageId: String,
        receipts: List<ClientEvent<ReceiptEventContent>>,
        roomId: String
    ): ConversationMessageIO = this.copy(
        id = event.idOrNull?.full ?: messageId,
        authorPublicId = this.authorPublicId ?: event.senderOrNull?.full,
        sentAt = event.originTimestampOrNull?.let { millis ->
            Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault())
        },
        state = if(receipts.find { it.idOrNull?.full == event.idOrNull?.full } != null) {
            MessageState.Read
        }else MessageState.Sent,
        conversationId = roomId
    )

    private suspend fun decryptEvent(event: RoomEvent.MessageEvent<*>): MessageEventContent? {
        return sharedDataManager.matrixClient.value?.roomEventEncryptionServices?.decrypt(event)?.getOrNullLoggingError()
    }

    private fun MessageEventContent.process(): ConversationMessageIO? {
        return when(this) {
            is VerificationStep -> null
            is RoomMessageEventContent.VerificationRequest -> {
                ConversationMessageIO(
                    verification = ConversationMessageIO.VerificationRequestInfo(
                        fromDeviceId = fromDevice,
                        methods = methods,
                        to = to.full
                    ),
                    authorPublicId = AUTHOR_SYSTEM
                )
            }
            else -> {
                val file = (this as? FileBased)?.takeIf { it.url?.isBlank() == false }
                val formattedBody = (this as? RoomMessageEventContent)?.formattedBody?.takeIf {
                    body != file?.body
                }
                ConversationMessageIO(
                    content = formattedBody,
                    anchorMessageId = this.relatesTo?.replyTo?.eventId?.full,
                    parentAnchorMessageId = (relatesTo as? RelatesTo.Thread)?.eventId?.full
                )
            }
        }
    }
}