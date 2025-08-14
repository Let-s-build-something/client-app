package database

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import database.AppRoomDatabase.Companion.TABLE_CONVERSATION_ROOM

object DatabaseMigrations {
    val MIGRATION_82_83 = object : Migration(82, 83) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                """
                    ALTER TABLE ${AppRoomDatabase.TABLE_MESSAGE_REACTION}
                    ADD COLUMN sent_at TEXT
                """.trimIndent()
            )
        }
    }

    val MIGRATION_83_84 = object : Migration(83, 84) {
        override fun migrate(connection: SQLiteConnection) {
            // Create a temporary table with the updated schema (excluding last_message_timestamp)
            connection.execSQL("""
            CREATE TABLE room_conversation_room_table_temp (
                id TEXT NOT NULL,
                summary TEXT,
                proximity REAL,
                unread_notifications TEXT,
                invite_state TEXT,
                knock_state TEXT,
                owner_public_id TEXT,
                primary_key TEXT NOT NULL,
                prev_batch TEXT,
                history_visibility TEXT,
                algorithm TEXT,
                type TEXT NOT NULL,
                is_direct INTEGER,
                PRIMARY KEY (primary_key)
            )
        """.trimIndent())

            // Copy data from the old table to the temporary table, excluding last_message_timestamp
            connection.execSQL("""
            INSERT INTO room_conversation_room_table_temp (
                id, summary, proximity, unread_notifications, invite_state, knock_state,
                owner_public_id, primary_key, prev_batch, history_visibility, algorithm, type, is_direct
            )
            SELECT 
                id, summary, proximity, unread_notifications, invite_state, knock_state,
                owner_public_id, primary_key, prev_batch, history_visibility, algorithm, type, is_direct
            FROM $TABLE_CONVERSATION_ROOM
        """.trimIndent())

            // Drop the old table (this also drops any associated indices)
            connection.execSQL("DROP TABLE $TABLE_CONVERSATION_ROOM")

            // Rename the temporary table to the original table name
            connection.execSQL("ALTER TABLE room_conversation_room_table_temp RENAME TO $TABLE_CONVERSATION_ROOM")

            // Recreate the required indices (excluding the one on last_message_timestamp)
            connection.execSQL("CREATE INDEX index_room_conversation_room_table_owner_public_id ON room_conversation_room_table(owner_public_id)")
            connection.execSQL("CREATE INDEX index_room_conversation_room_table_type ON room_conversation_room_table(type)")
            connection.execSQL("CREATE INDEX index_room_conversation_room_table_proximity ON room_conversation_room_table(proximity)")
            connection.execSQL("CREATE INDEX index_room_conversation_room_table_owner_public_id_type ON room_conversation_room_table(owner_public_id, type)")
            connection.execSQL("CREATE INDEX index_room_conversation_room_table_owner_public_id_id ON room_conversation_room_table(owner_public_id, id)")
        }
    }

}