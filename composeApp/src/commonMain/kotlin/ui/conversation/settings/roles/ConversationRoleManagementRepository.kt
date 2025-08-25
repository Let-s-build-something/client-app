package ui.conversation.settings.roles

import data.io.matrix.room.ConversationRoomIO
import data.io.social.network.conversation.ConversationRole
import database.dao.ConversationRoleDao
import database.dao.ConversationRoomDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

class ConversationRoleManagementRepository(
    private val conversationRoleDao: ConversationRoleDao,
    private val conversationRoomDao: ConversationRoomDao
) {
    suspend fun getAllRoles(roomId: String) = withContext(Dispatchers.IO) {
        conversationRoleDao.getAll(roomId)
    }

    suspend fun updateRoom(room: ConversationRoomIO) = withContext(Dispatchers.IO) {
        conversationRoomDao.insert(room)
    }

    suspend fun insertRoles(data: List<ConversationRole>) = withContext(Dispatchers.IO) {
        conversationRoleDao.insert(data)
    }
}