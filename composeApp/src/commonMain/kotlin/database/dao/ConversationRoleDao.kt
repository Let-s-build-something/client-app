package database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import data.io.social.network.conversation.ConversationRole
import database.AppRoomDatabase

@Dao
interface ConversationRoleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReplace(item: ConversationRole)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(items: List<ConversationRole>)

    @Query("""
        SELECT * FROM ${AppRoomDatabase.TABLE_CONVERSATION_ROLE}
        WHERE room_id = :roomId
        """)
    suspend fun getAll(roomId: String): List<ConversationRole>

    @Query("""
        UPDATE ${AppRoomDatabase.TABLE_CONVERSATION_ROLE}
        SET power = :power
        WHERE uid = :uid
    """)
    suspend fun updatePower(
        uid: String,
        power: Long
    )

    @Query("DELETE FROM ${AppRoomDatabase.TABLE_CONVERSATION_ROLE}")
    suspend fun removeAll()
}
