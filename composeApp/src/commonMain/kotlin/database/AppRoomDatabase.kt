package database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import data.io.base.paging.MatrixPagingMetaIO
import data.io.base.paging.PagingMetaIO
import data.io.experiment.ExperimentIO
import data.io.experiment.ExperimentSet
import data.io.matrix.room.ConversationRoomIO
import data.io.matrix.room.event.ConversationRoomMember
import data.io.social.network.conversation.ConversationRole
import data.io.social.network.conversation.EmojiSelection
import data.io.social.network.conversation.message.ConversationMessageIO
import data.io.social.network.conversation.message.MediaIO
import data.io.social.network.conversation.message.MessageReactionIO
import data.io.user.NetworkItemIO
import data.io.user.PresenceData
import database.dao.ConversationMessageDao
import database.dao.ConversationRoleDao
import database.dao.ConversationRoomDao
import database.dao.EmojiSelectionDao
import database.dao.GravityDao
import database.dao.MatrixPagingMetaDao
import database.dao.MediaDao
import database.dao.MessageReactionDao
import database.dao.NetworkItemDao
import database.dao.PagingMetaDao
import database.dao.PresenceEventDao
import database.dao.RoomMemberDao
import database.dao.experiment.ExperimentDao
import database.dao.experiment.ExperimentSetDao
import net.folivo.trixnity.core.model.events.m.PresenceEventContent
import ui.conversation.components.experimental.gravity.GravityValue

@Database(
    entities = [
        NetworkItemIO::class,
        EmojiSelection::class,
        PagingMetaIO::class,
        MatrixPagingMetaIO::class,
        ConversationMessageIO::class,
        PresenceData::class,
        ConversationRoomMember::class,
        GravityValue::class,
        MessageReactionIO::class,
        MediaIO::class,
        ExperimentIO::class,
        ConversationRole::class,
        ExperimentSet::class,
        ConversationRoomIO::class
    ],
    version = 85,
    exportSchema = true
)
@ConstructedBy(AppDatabaseConstructor::class)
@TypeConverters(AppDatabaseConverter::class)
abstract class AppRoomDatabase: RoomDatabase() {

    /** An interface for interacting with local database for collections */
    abstract fun networkItemDao(): NetworkItemDao
    abstract fun conversationMessageDao(): ConversationMessageDao
    abstract fun emojiSelectionDao(): EmojiSelectionDao
    abstract fun pagingMetaDao(): PagingMetaDao
    abstract fun conversationRoomDao(): ConversationRoomDao
    abstract fun presenceEventDao(): PresenceEventDao
    abstract fun matrixPagingMetaDao(): MatrixPagingMetaDao
    abstract fun roomMemberDao(): RoomMemberDao
    abstract fun messageReactionDao(): MessageReactionDao
    abstract fun mediaDao(): MediaDao
    abstract fun gravityDao(): GravityDao
    abstract fun experimentDao(): ExperimentDao
    abstract fun experimentSetDao(): ExperimentSetDao
    abstract fun conversationRoleDao(): ConversationRoleDao

    companion object {
        /** File name of the main database */
        const val DATABASE_NAME = "app_database.db"

        /** Identification of table for [NetworkItemIO] */
        const val TABLE_NETWORK_ITEM = "room_network_item_table"

        /** Identification of table for [ConversationRoomIO] */
        const val TABLE_CONVERSATION_ROOM = "room_conversation_room_table"
        const val TABLE_MEDIA = "table_media"
        const val TABLE_CONVERSATION_ROLE = "table_conversation_role"

        /** Identification of table for [ConversationMessageIO] */
        const val TABLE_CONVERSATION_MESSAGE = "room_conversation_message_table"
        const val TABLE_MESSAGE_REACTION = "room_message_reaction_table"

        /** Identification of table for [EmojiSelection] */
        const val TABLE_EMOJI_SELECTION = "room_emoji_selection"

        /** Identification of table for [PagingMetaIO] */
        const val TABLE_PAGING_META = "room_paging_meta_table"

        /** Identification of table for [MatrixPagingMetaIO] */
        const val TABLE_MATRIX_PAGING_META = "room_matrix_paging_meta_table"

        /** Identification of table for [PresenceEventContent] */
        const val TABLE_PRESENCE_EVENT = "presence_event_table"

        /** Identification of table for [ConversationRoomMember] */
        const val TABLE_ROOM_MEMBER = "room_member_table"

        /** Identification of table for [GravityValue] */
        const val TABLE_GRAVITY = "room_gravity_table"

        /** Table for list of ongoing experiments [ExperimentIO] */
        const val TABLE_EXPERIMENT = "experiment_table"

        /** Sets with values that can be nested under experiments */
        const val TABLE_EXPERIMENT_SET = "experiment_set_table"
    }
}

/** returns database builder specific to each platform */
expect fun getDatabaseBuilder(): RoomDatabase.Builder<AppRoomDatabase>

/** Master database of this application */
@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppRoomDatabase> {
    override fun initialize(): AppRoomDatabase
}
