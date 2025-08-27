package data.io.experiment

import androidx.room.Entity
import androidx.room.PrimaryKey
import database.AppRoomDatabase
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Entity(tableName = AppRoomDatabase.TABLE_EXPERIMENT_SET)
data class ExperimentQuestionnaire(
    @PrimaryKey
    val uid: String = Uuid.random().toString(),
    val name: String,
    val randomize: Boolean = true,
    val values: List<ExperimentSetValue> = listOf()
) {
    override fun toString(): String {
        return "{" +
                "uid: $uid, " +
                "name: $name, " +
                "randomize: $randomize, " +
                "values: $values, " +
                "}"
    }
}
