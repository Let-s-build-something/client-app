package ui.home

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import data.io.base.BaseResponse
import data.io.base.paging.PaginationInfo
import data.io.matrix.room.FullConversationRoom
import data.io.matrix.room.RoomType
import data.io.social.network.conversation.ConversationListResponse
import database.dao.ConversationMessageDao
import database.dao.ConversationRoomDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import net.folivo.trixnity.client.MatrixClient
import net.folivo.trixnity.core.model.RoomId
import utils.SharedLogger

class HomeRepository(
    private val conversationRoomDao: ConversationRoomDao,
    private val conversationMessageDao: ConversationMessageDao
) {

    private var currentPagingSource: PagingSource<*, *>? = null

    private fun invalidateSource() {
        currentPagingSource?.invalidate()
    }

    /** Returns a flow of network list */
    fun getConversationRoomPager(
        config: PagingConfig,
        ownerPublic: () -> String?
    ): Pager<Int, FullConversationRoom> {
        return Pager(
            config = config,
            pagingSourceFactory = {
                ConversationRoomSource(
                    size = config.pageSize,
                    getItems = { page ->
                        val res = conversationRoomDao.getPaginated(
                            ownerPublicId = ownerPublic(),
                            limit = config.pageSize,
                            offset = page * config.pageSize
                        )

                        BaseResponse.Success(
                            ConversationListResponse(
                                content = res,
                                pagination = PaginationInfo(
                                    page = page,
                                    size = res.size,
                                    totalItems = conversationRoomDao.getCount(ownerPublic())
                                )
                            )
                        )
                    }
                ).also {
                    currentPagingSource = it
                }
            }
        )
    }

    suspend fun respondToInvitation(
        client: MatrixClient?,
        matrixUserId: String?,
        roomId: String,
        accept: Boolean
    ): Result<Any>? {
        return withContext(Dispatchers.IO) {
            if(accept) {
                client?.api?.room?.joinRoom(roomId = RoomId(roomId))?.onSuccess {
                    conversationRoomDao.setType(
                        id = roomId,
                        ownerPublicId = matrixUserId,
                        newType = RoomType.Joined
                    )
                    invalidateSource()
                }?.onFailure {
                    SharedLogger.logger.debug { "Invitation accept failed: $it" }
                }
            }else {
                client?.api?.room?.leaveRoom(roomId = RoomId(roomId))?.onSuccess {
                    client.api.room.forgetRoom(roomId = RoomId(roomId)).onSuccess {
                        conversationRoomDao.remove(id = roomId, ownerPublicId = matrixUserId)
                        invalidateSource()
                    }
                }
            }
        }
    }

    suspend fun queryLocalMessagesOfRoom(
        roomId: String,
        query: String,
        limit: Int
    ) = withContext(Dispatchers.IO) {
        conversationMessageDao.queryPaginated(
            conversationId = roomId,
            query = query,
            limit = limit,
            offset = 0
        )
    }

    /*suspend fun queryAndInsertMessages(
        matrixClient: MatrixClient?,
        query: String,
        roomId: String,
        limit: Int,
    ) = withContext(Dispatchers.IO) {
        matrixClient?.api?.server?.search()
    }*/
}
