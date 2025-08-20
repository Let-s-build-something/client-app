package data.shared

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import base.utils.MatrixUtils.AUGMY_HOMESERVER_IDENTIFIER
import base.utils.MatrixUtils.AUGMY_HOME_SERVER_ADDRESS
import base.utils.NetworkConnectivity
import base.utils.NetworkSpeed
import base.utils.asSimpleString
import data.NetworkProximityCategory
import data.io.app.ClientStatus
import data.io.app.LocalSettings
import data.io.app.SettingsKeys
import data.io.app.ThemeChoice
import data.io.base.AppPingType
import data.shared.auth.AuthService
import data.shared.sync.DataSyncService
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.messaging.messaging
import koin.AppSettings
import koin.commonModule
import koin.secureSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.folivo.trixnity.client.MatrixClient
import org.koin.core.context.loadKoinModules
import org.koin.core.context.unloadKoinModules
import org.koin.mp.KoinPlatform
import ui.home.homeModule
import ui.login.homeserver_picker.HomeserverPickerModel
import utils.SharedLogger

/** Viewmodel with shared behavior and injections for general purposes */
open class SharedModel: ViewModel() {
    protected val syncService = KoinPlatform.getKoin().get<DataSyncService>()
    protected val authService = KoinPlatform.getKoin().get<AuthService>()

    /** Singleton data manager to keep session-only data alive */
    protected val sharedDataManager: SharedDataManager by KoinPlatform.getKoin().inject()

    /** persistent settings saved locally to a device */
    protected val settings: AppSettings by KoinPlatform.getKoin().inject()

    //======================================== public variables ==========================================

    val matrixUserId: String?
        get() = currentUser.value?.userId ?: authService.userId

    val matrixClient: MatrixClient?
        get() = sharedDataManager.matrixClient.value

    val homeserverAddress: String
        get() = homeserver.address

    val homeserver: HomeserverPickerModel.HomeserverAddress
        get() = currentUser.value?.matrixHomeserver?.let {
            HomeserverPickerModel.HomeserverAddress(it, it)
        } ?: HomeserverPickerModel.HomeserverAddress(
            AUGMY_HOMESERVER_IDENTIFIER,
            AUGMY_HOME_SERVER_ADDRESS
        )

    val awaitingAutologin: Boolean
        get() = authService.awaitingAutologin


    /** Current configuration specific to this app */
    val localSettings = sharedDataManager.localSettings.asStateFlow()

    /** currently signed in user */
    val currentUser = sharedDataManager.currentUser.asStateFlow()

    /** Acts as a sort of a in-app push notification, notifying of changes */
    val pingStream = sharedDataManager.pingStream.asStateFlow()

    /** whether toolbar is currently expanded */
    val isToolbarExpanded = sharedDataManager.isToolbarExpanded.asStateFlow()

    /** Most recent measure of speed and network connectivity */
    val networkConnectivity = sharedDataManager.networkConnectivity.asStateFlow()

    //======================================== functions ==========================================

    /** Initializes the user and returns whether successful */
    suspend fun initUser(): Boolean {
        authService.setupAutoLogin(forceRefresh = false)
        updateClientSettings()

        SharedLogger.logger.debug { "initUser, user: ${currentUser.value}" }
        return currentUser.value?.accessToken != null && currentUser.value?.matrixHomeserver != null
    }

    fun updateNetworkConnectivity(
        isNetworkAvailable: Boolean? = null,
        networkSpeed: NetworkSpeed? = null
    ) {
        sharedDataManager.networkConnectivity.value = sharedDataManager.networkConnectivity.value?.copy(
            isNetworkAvailable = isNetworkAvailable ?: sharedDataManager.networkConnectivity.value?.isNetworkAvailable,
            speed = networkSpeed ?: sharedDataManager.networkConnectivity.value?.speed
        ) ?: NetworkConnectivity(
            isNetworkAvailable = isNetworkAvailable,
            speed = networkSpeed
        )
    }

    suspend fun consumePing(identifier: String) = withContext(Dispatchers.Default) {
        sharedDataManager.pingStream.update { prev ->
            prev.toMutableSet().apply {
                removeAll { it.identifier == identifier }
            }
        }
    }

    suspend fun consumePing(type: AppPingType) = withContext(Dispatchers.Default) {
        sharedDataManager.pingStream.update { prev ->
            prev.toMutableSet().apply {
                removeAll { it.type == type }
            }
        }
    }

    /** Changes the state of the toolbar */
    fun changeToolbarState(expand: Boolean) {
        sharedDataManager.isToolbarExpanded.value = expand
        viewModelScope.launch {
            settings.putBoolean(SettingsKeys.KEY_TOOLBAR_EXPANDED, expand)
        }
    }

    suspend fun updateClientSettings() {
        val fcmToken = settings.getStringOrNull(SettingsKeys.KEY_FCM) ?: try {
            Firebase.messaging.getToken()
        } catch (_: NotImplementedError) {
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }?.apply {
            settings.putString(SettingsKeys.KEY_FCM, this)
        }
        val theme = ThemeChoice.entries.find {
            it.name == settings.getStringOrNull(SettingsKeys.KEY_THEME)
        } ?: ThemeChoice.DARK
        val clientStatus = ClientStatus.entries.find {
            it.name == settings.getStringOrNull(SettingsKeys.KEY_CLIENT_STATUS)
        } ?: ClientStatus.NEW
        val networkColors = settings.getStringOrNull(
            "${SettingsKeys.KEY_NETWORK_COLORS}_$matrixUserId"
        )?.split(",")
            ?: NetworkProximityCategory.entries.map { it.color.asSimpleString() }

        val update = LocalSettings(
            theme = theme,
            fcmToken = fcmToken,
            clientStatus = clientStatus,
            networkColors = networkColors,
            deviceId = authService.getDeviceId()
        )
        sharedDataManager.localSettings.value = sharedDataManager.localSettings.value?.update(update) ?: update
    }

    /** Logs out the currently signed in user */
    open suspend fun logoutCurrentUser() {
        SharedLogger.logger.warn { "Logging current user out" }
        syncService.stop()
        authService.clear()
        secureSettings.clear()
        sharedDataManager.matrixClient.value?.logout()
        sharedDataManager.matrixClient.value?.close()
        sharedDataManager.currentUser.value = null
        sharedDataManager.localSettings.value = null
        sharedDataManager.matrixClient.value = null
        sharedDataManager.pingStream.value = setOf()
        unloadKoinModules(commonModule)
        unloadKoinModules(homeModule)
        loadKoinModules(commonModule)
        loadKoinModules(homeModule)
        updateClientSettings()
    }
}
