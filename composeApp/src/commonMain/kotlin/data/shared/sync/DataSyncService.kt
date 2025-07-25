package data.shared.sync

import augmy.interactive.shared.ext.ifNull
import data.io.social.UserVisibility
import data.shared.SharedDataManager
import korlibs.io.async.onCancel
import korlibs.logger.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.folivo.trixnity.client.MatrixClient
import net.folivo.trixnity.core.ClientEventEmitter.Priority.FIRST
import net.folivo.trixnity.core.model.events.m.Presence
import org.koin.dsl.module
import org.koin.mp.KoinPlatform
import kotlin.time.Duration.Companion.milliseconds

internal val dataSyncModule = module {
    factory { DataSyncHandler() }
    factory { DataSyncService() }
    single { DataSyncService() }
    single { DataService() }
}

class DataSyncService {
    private val logger = Logger("DataSyncService")

    companion object {
        const val SYNC_INTERVAL = 60_000L
    }

    private val sharedDataManager: SharedDataManager by KoinPlatform.getKoin().inject()

    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var homeserver: String? = null
    private var isRunning = false
    private val handler = DataSyncHandler()
    private val synMutex = Mutex()

    /** Begins the synchronization process and runs it over and over as long as the app is running or stopped via [stop] */
    fun sync(homeserver: String, delay: Long? = null) {
        if(!isRunning && homeserver.isNotBlank()) {
            this@DataSyncService.homeserver = homeserver
            isRunning = true

            syncScope.launch {
                this.coroutineContext.onCancel {
                    CoroutineScope(Job()).launch {
                        sharedDataManager.matrixClient.value?.api?.sync?.stop()
                    }
                    isRunning = false
                }

                synMutex.withLock {
                    sharedDataManager.matrixClient.value?.let { client ->
                        delay?.let { delay(it) }
                        if(sharedDataManager.currentUser.value?.isFullyValid == true) {
                            enqueue(client = client)
                        }else {
                            logger.debug { "User not fully valid, stopping." }
                            stop()
                        }
                    }.ifNull {
                        logger.debug { "Client is null, stopping." }
                        stop()
                    }
                }
            }
        }
    }

    fun restart() {
        CoroutineScope(Job()).launch {
            stop()
            sync(homeserver ?: return@launch)
        }
    }

    suspend fun stop() {
        sharedDataManager.matrixClient.value?.api?.sync?.stop()
        if(isRunning) {
            handler.stop()
            isRunning = false
            if(syncScope.isActive) syncScope.coroutineContext.cancelChildren()
        }
    }

    private suspend fun enqueue(
        client: MatrixClient,
        homeserver: String? = this@DataSyncService.homeserver
    ) {
        val owner = sharedDataManager.currentUser.value?.matrixUserId
        if(homeserver == null || owner == null) {
            stop()
            return
        }

        client.api.sync.subscribe(priority = FIRST) {
            handler.handle(
                response = it.syncResponse,
                owner = owner
            )
        }

        logger.debug { "enqueue, entityId: ${homeserver}_$owner" }
        client.api.sync.start(
            /*filter = json.encodeToString(
                Filters.RoomFilter.RoomEventFilter(lazyLoadMembers = true)
            ),*/
            timeout = SYNC_INTERVAL.milliseconds,
            setPresence = when(sharedDataManager.currentUser.value?.configuration?.visibility) {
                UserVisibility.Online -> Presence.ONLINE
                UserVisibility.Invisible, UserVisibility.Offline -> Presence.OFFLINE
                else -> Presence.UNAVAILABLE
            }
        )
    }
}
