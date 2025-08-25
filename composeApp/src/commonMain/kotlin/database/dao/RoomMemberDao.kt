package database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import data.io.matrix.room.event.ConversationRoomMember
import database.AppRoomDatabase

/** Interface for communication with local Room database */
@Dao
interface RoomMemberDao {
    @Query("SELECT * FROM ${AppRoomDatabase.TABLE_ROOM_MEMBER}")
    suspend fun getAll(): List<ConversationRoomMember>

    @Query("""
        SELECT * FROM ${AppRoomDatabase.TABLE_ROOM_MEMBER}
        WHERE room_id = :roomId
        """)
    suspend fun getAll(roomId: String): List<ConversationRoomMember>

    @Query("SELECT * FROM ${AppRoomDatabase.TABLE_ROOM_MEMBER} " +
            "WHERE userId = :userId ")
    suspend fun get(userId: String): ConversationRoomMember?

    @Query("SELECT * FROM ${AppRoomDatabase.TABLE_ROOM_MEMBER} " +
            "WHERE room_id = :roomId ")
    suspend fun getOfRoom(roomId: String): List<ConversationRoomMember>

    @Query("""
        SELECT * FROM ${AppRoomDatabase.TABLE_ROOM_MEMBER}
            WHERE (display_name like '%' || :query || '%'
            OR userId  like '%' || :query || '%')
            AND id NOT IN (:excludeIds)
            ORDER BY proximity DESC 
            LIMIT :limit
    """)
    suspend fun query(
        query: String,
        excludeIds: List<String> = emptyList(),
        limit: Int = 100
    ): List<ConversationRoomMember>

    @Query("""
        SELECT * FROM ${AppRoomDatabase.TABLE_ROOM_MEMBER}
            WHERE id NOT IN (:excludeIds)
            ORDER BY proximity DESC 
            LIMIT :limit
    """)
    suspend fun getSorted(
        excludeIds: List<String> = emptyList(),
        limit: Int
    ): List<ConversationRoomMember>

    @Query("""
        SELECT * FROM ${AppRoomDatabase.TABLE_ROOM_MEMBER}
            WHERE room_id = :roomId
            AND userId != :ignoreUserId
            AND membership = 'JOIN'
            ORDER BY proximity DESC, timestamp ASC
            LIMIT :limit
            OFFSET :offset
            """)
    suspend fun getPaginated(
        roomId: String?,
        offset: Int,
        ignoreUserId: String?,
        limit: Int,
    ): List<ConversationRoomMember>

    @Query(
        """
         SELECT COUNT(*) FROM ${AppRoomDatabase.TABLE_ROOM_MEMBER}   
         WHERE room_id = :roomId
         AND membership = 'JOIN'
        """
    )
    suspend fun getCount(roomId: String?): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ConversationRoomMember>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(item: ConversationRoomMember): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReplace(item: ConversationRoomMember)

    @Query("DELETE FROM ${AppRoomDatabase.TABLE_ROOM_MEMBER}")
    suspend fun removeAll()

    @Query("""
        DELETE FROM ${AppRoomDatabase.TABLE_ROOM_MEMBER}
        WHERE userId = :userId
        AND room_id = :roomId
    """)
    suspend fun remove(
        userId: String,
        roomId: String
    )

    @Query("DELETE FROM ${AppRoomDatabase.TABLE_ROOM_MEMBER} WHERE room_id = :roomId")
    suspend fun removeAll(roomId: String)
}
