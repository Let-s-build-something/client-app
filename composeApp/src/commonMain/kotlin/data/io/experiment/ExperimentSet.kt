package data.io.experiment

import androidx.room.Entity
import androidx.room.PrimaryKey
import database.AppRoomDatabase
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Entity(tableName = AppRoomDatabase.TABLE_EXPERIMENT_SET)
data class ExperimentSet(
    @PrimaryKey
    val uid: String = Uuid.random().toString(),
    val name: String,
    val values: List<ExperimentSetValue> = listOf()
)
