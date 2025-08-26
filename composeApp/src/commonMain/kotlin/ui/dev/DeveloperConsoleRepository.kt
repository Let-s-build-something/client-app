package ui.dev

import data.io.base.BaseResponse
import database.dao.ConversationMessageDao
import database.dao.ConversationRoleDao
import database.dao.ConversationRoomDao
import database.dao.EmojiSelectionDao
import database.dao.GravityDao
import database.dao.MatrixPagingMetaDao
import database.dao.MediaDao
import database.dao.MessageReactionDao
import database.dao.NetworkItemDao
import database.dao.PagingMetaDao
import database.dao.PresenceEventDao
import database.dao.RoomMemberDao
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.Url
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import org.koin.mp.KoinPlatform
import ui.login.safeRequest

open class DeveloperConsoleRepository {

    private val httpClient by lazy {
        KoinPlatform.getKoin().get<HttpClient>()
    }

    suspend fun postStreamData(
        url: String,
        body: String
    ): BaseResponse<Any> = withContext(Dispatchers.IO) {
        httpClient.safeRequest<Any> {
            httpClient.post(url = Url(url)) {
                setBody(body)
            }
        }
    }

    suspend fun clearAllDaos() {
        with(KoinPlatform.getKoin()) {
            get<NetworkItemDao>().removeAll()
            get<ConversationRoleDao>().removeAll()
            get<ConversationMessageDao>().removeAll()
            get<EmojiSelectionDao>().removeAll()
            get<PagingMetaDao>().removeAll()
            get<MediaDao>().removeAll()
            get<ConversationRoomDao>().removeAll()
            get<PresenceEventDao>().removeAll()
            get<MatrixPagingMetaDao>().removeAll()
            get<GravityDao>().removeAll()
            get<RoomMemberDao>().removeAll()
            get<MessageReactionDao>().removeAll()
        }
        //secureSettings.clear(force = true)
    }
}