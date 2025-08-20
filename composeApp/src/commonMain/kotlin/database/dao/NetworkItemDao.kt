package database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import data.io.user.NetworkItemIO
import database.AppRoomDatabase

/** Interface for communication with local Room database */
@Dao
interface NetworkItemDao {

    /** Returns all network items */
    @Query("""
    SELECT ni.*, p.content AS presence
    FROM ${AppRoomDatabase.TABLE_NETWORK_ITEM} AS ni
    LEFT JOIN ${AppRoomDatabase.TABLE_PRESENCE_EVENT} AS p
    ON ni.user_id = p.user_id_full
    WHERE owner_user_id = :ownerPublicId
    ORDER BY proximity DESC
    LIMIT :limit
    OFFSET :offset
""")
    suspend fun getPaginated(
        ownerPublicId: String?,
        limit: Int,
        offset: Int = 0
    ): List<NetworkItemIO>

    /** Returns all network items related to an owner as defined by [ownerPublicId] */
    @Query("SELECT * FROM ${AppRoomDatabase.TABLE_NETWORK_ITEM} " +
            "WHERE owner_user_id = :ownerPublicId ")
    suspend fun getNonFiltered(
        ownerPublicId: String?
    ): List<NetworkItemIO>

    @Query("""
        SELECT * FROM ${AppRoomDatabase.TABLE_NETWORK_ITEM}
            WHERE display_name like '%' || :prompt || '%'
            OR user_id  like '%' || :prompt || '%'
            """)
    suspend fun searchByPrompt(prompt: String): List<NetworkItemIO>

    /** Returns all network items within the list [userPublicIds] */
    @Query("SELECT * FROM ${AppRoomDatabase.TABLE_NETWORK_ITEM} " +
            "WHERE owner_user_id = :ownerPublicId " +
            "AND user_public_id IN (:userPublicIds)")
    suspend fun getItems(
        userPublicIds: List<String>?,
        ownerPublicId: String?
    ): List<NetworkItemIO>

    /** Retrieves a single item */
    @Query("SELECT * FROM ${AppRoomDatabase.TABLE_NETWORK_ITEM} " +
            "WHERE user_id = :userId " +
            "AND owner_user_id = :ownerPublicId " +
            "LIMIT 1")
    suspend fun get(
        userId: String?,
        ownerPublicId: String?
    ): NetworkItemIO?

    @Query("DELETE FROM ${AppRoomDatabase.TABLE_NETWORK_ITEM} " +
            "WHERE user_id = :userId " +
            "AND owner_user_id = :ownerPublicId ")
    suspend fun remove(
        userId: String?,
        ownerPublicId: String?
    )

    /** Returns all network items specific to proximity bounds as defined by [proximityMin] and [proximityMax] */
    @Query("SELECT * FROM ${AppRoomDatabase.TABLE_NETWORK_ITEM} " +
            "WHERE owner_user_id = :ownerPublicId " +
            "AND user_public_id != :excludeId " +
            "AND proximity BETWEEN :proximityMin AND :proximityMax " +
            "LIMIT :count")
    suspend fun getByProximity(
        count: Int,
        ownerPublicId: String?,
        proximityMin: Float,
        proximityMax: Float,
        excludeId: String?
    ): List<NetworkItemIO>

    /** Counts the number of items */
    @Query("SELECT COUNT(*) FROM ${AppRoomDatabase.TABLE_NETWORK_ITEM} " +
            "WHERE owner_user_id = :ownerPublicId")
    suspend fun getCount(ownerPublicId: String?): Int

    /** Counts the number of items */
    @Query("UPDATE ${AppRoomDatabase.TABLE_NETWORK_ITEM} " +
            "SET proximity = :proximity " +
            "WHERE owner_user_id = :ownerPublicId " +
            "AND public_id = :publicId ")
    suspend fun updateProximity(
        ownerPublicId: String?,
        proximity: Float,
        publicId: String?
    )

    /** Inserts or updates a set of item objects */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<NetworkItemIO>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: NetworkItemIO)

    /** Removes all items from the database */
    @Query("DELETE FROM ${AppRoomDatabase.TABLE_NETWORK_ITEM}")
    suspend fun removeAll()
}