package database

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import database.DatabaseMigrations.MIGRATION_82_83
import database.DatabaseMigrations.MIGRATION_83_84
import database.DatabaseMigrations.MIGRATION_84_85
import database.DatabaseMigrations.MIGRATION_85_86
import database.DatabaseMigrations.MIGRATION_86_87
import database.DatabaseMigrations.MIGRATION_87_88
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import org.koin.dsl.module

internal val databaseModule = module {
    single<AppRoomDatabase> {
        getDatabaseBuilder()
            //.fallbackToDestructiveMigration(dropAllTables = true)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .addMigrations(
                MIGRATION_82_83,
                MIGRATION_83_84,
                MIGRATION_84_85,
                MIGRATION_85_86,
                MIGRATION_86_87,
                MIGRATION_87_88,
            )
            .addTypeConverter(AppDatabaseConverter())
            .build()
    }

    // DAO providers
    factory { get<AppRoomDatabase>().networkItemDao() }
    factory { get<AppRoomDatabase>().emojiSelectionDao() }
    factory { get<AppRoomDatabase>().conversationMessageDao() }
    factory { get<AppRoomDatabase>().pagingMetaDao() }
    factory { get<AppRoomDatabase>().conversationRoomDao() }
    factory { get<AppRoomDatabase>().presenceEventDao() }
    factory { get<AppRoomDatabase>().matrixPagingMetaDao() }
    factory { get<AppRoomDatabase>().roomMemberDao() }
    factory { get<AppRoomDatabase>().messageReactionDao() }
    factory { get<AppRoomDatabase>().mediaDao() }
    factory { get<AppRoomDatabase>().gravityDao() }
    factory { get<AppRoomDatabase>().experimentDao() }
    factory { get<AppRoomDatabase>().experimentSetDao() }
    factory { get<AppRoomDatabase>().conversationRoleDao() }
}
