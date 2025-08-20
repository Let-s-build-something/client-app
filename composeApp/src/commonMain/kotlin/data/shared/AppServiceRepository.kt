package data.shared

import base.utils.MatrixUtils.AUGMY_INTERNAL_ROOM_ID
import data.io.base.BaseResponse
import data.io.user.RequestUpdateFcmToken
import database.dao.ConversationRoomDao
import io.ktor.client.HttpClient
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import org.koin.mp.KoinPlatform
import ui.login.safeRequest

/** Class for calling APIs and remote work in general */
class AppServiceRepository(private val httpClient: HttpClient) {

    private val roomDao by lazy { KoinPlatform.getKoin().get<ConversationRoomDao>() }

    /** Makes a request to update an FCM token */
    suspend fun updateFCMToken(
        prevFcmToken: String?,
        userId: String?,
        newToken: String
    ): BaseResponse<Any> {
        return withContext(Dispatchers.IO) {
            httpClient.safeRequest {
                put(urlString = "/api/v1/users/${userId}/fcm-tokens") {
                    setBody(
                        RequestUpdateFcmToken(
                            fcmToken = newToken,
                            oldFcmToken = prevFcmToken
                        )
                    )
                }
            }
        }
    }

    suspend fun checkIsDeveloper(ownerUserId: String?): Boolean = withContext(Dispatchers.IO) {
        roomDao.get(id = AUGMY_INTERNAL_ROOM_ID, ownerPublicId = ownerUserId) != null
    }
}
