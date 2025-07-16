package database.dao.experiment

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import data.io.experiment.ExperimentSet
import database.AppRoomDatabase

/** Interface for communication with local Room database */
@Dao
interface ExperimentSetDao {
    @Query("SELECT * FROM ${AppRoomDatabase.TABLE_EXPERIMENT_SET}")
    suspend fun getAll(): List<ExperimentSet>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ExperimentSet)

    @Query("DELETE FROM ${AppRoomDatabase.TABLE_EXPERIMENT_SET}")
    suspend fun removeAll()

    @Query("""
        DELETE FROM ${AppRoomDatabase.TABLE_EXPERIMENT_SET}
        WHERE uid = :uid
        """)
    suspend fun remove(uid: String)
}