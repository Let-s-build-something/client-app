package utils

import augmy.interactive.shared.utils.DateUtils
import base.utils.MatrixUtils
import data.io.app.SecureSettingsKeys
import data.io.app.SettingsKeys.KEY_REFEREE_USER_ID
import data.io.matrix.room.ConversationRoomIO
import data.io.matrix.room.RoomSummary
import data.io.matrix.room.RoomType
import data.io.matrix.room.event.ConversationRoomMember
import data.io.user.NetworkItemIO
import database.dao.ConversationRoomDao
import database.dao.RoomMemberDao
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import koin.secureSettings
import koin.settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import net.folivo.trixnity.client.MatrixClient
import net.folivo.trixnity.clientserverapi.model.rooms.CreateRoom
import net.folivo.trixnity.clientserverapi.model.rooms.DirectoryVisibility
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.InitialStateEvent
import net.folivo.trixnity.core.model.events.m.room.EncryptionEventContent
import net.folivo.trixnity.core.model.events.m.room.GuestAccessEventContent
import net.folivo.trixnity.core.model.events.m.room.HistoryVisibilityEventContent
import net.folivo.trixnity.core.model.events.m.room.HistoryVisibilityEventContent.HistoryVisibility
import net.folivo.trixnity.core.model.events.m.room.Membership
import net.folivo.trixnity.core.model.keys.EncryptionAlgorithm
import org.koin.mp.KoinPlatform
import ui.login.safeRequest

object ReferralUtils {

    suspend fun findAndUseReferee(client: MatrixClient, homeserver: String) {
        settings.getString(KEY_REFEREE_USER_ID, "").takeIf { it.isNotBlank() }?.let { userId ->
            client.api.room.createRoom(
                visibility = DirectoryVisibility.PRIVATE,
                initialState = listOf(
                    InitialStateEvent(GuestAccessEventContent(GuestAccessEventContent.GuestAccessType.FORBIDDEN), ""),
                    InitialStateEvent(HistoryVisibilityEventContent(HistoryVisibility.INVITED), ""),
                    InitialStateEvent(EncryptionEventContent(algorithm = EncryptionAlgorithm.Megolm), ""),
                    MatrixUtils.defaultRoomPowerLevels
                ),
                roomVersion = "11",
                isDirect = true,
                preset = CreateRoom.Request.Preset.TRUSTED_PRIVATE,
                invite = setOf(UserId(userId)),
            ).getOrNull()?.full?.let { conversationId ->
                insertMemberByUserId(
                    conversationId = conversationId,
                    userId = userId,
                    homeserver = homeserver
                )
                insertConversation(
                    ConversationRoomIO(
                        id = conversationId,
                        summary = RoomSummary(
                            heroes = listOf(UserId(userId)),
                            isDirect = true
                        ),
                        ownerPublicId = secureSettings.getStringOrNull(key = SecureSettingsKeys.KEY_USER_ID),
                        historyVisibility = HistoryVisibility.INVITED,
                        prevBatch = null,
                        type = RoomType.Joined
                    )
                )
                settings.remove(KEY_REFEREE_USER_ID)
            }
        }
    }

    suspend fun insertConversation(conversation: ConversationRoomIO) {
        return withContext(Dispatchers.IO) {
            KoinPlatform.getKoin().get<ConversationRoomDao>().insert(conversation)
        }
    }

    private suspend fun insertMemberByUserId(
        conversationId: String,
        userId: String,
        homeserver: String
    ) {
        return withContext(Dispatchers.IO) {
            with(KoinPlatform.getKoin().get<RoomMemberDao>()) {
                if (get(userId) == null) {
                    val remoteInfo = getRemoteUser(userId, homeserver)

                    insertReplace(
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
    }

    private suspend fun getRemoteUser(
        userId: String,
        homeserver: String
    ) = with(KoinPlatform.getKoin().get<HttpClient>()) {
        safeRequest<NetworkItemIO> {
            get(urlString = "https://${homeserver}/_matrix/client/v3/profile/${userId}")
        }.success?.data
    }
}