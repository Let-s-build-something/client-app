package ui.login.homeserver_picker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import base.utils.MatrixUtils.AUGMY_HOMESERVER_IDENTIFIER
import base.utils.MatrixUtils.AUGMY_HOME_SERVER_ADDRESS
import base.utils.MatrixUtils.MATRIX_HOME_SERVER
import data.io.DELAY_BETWEEN_REQUESTS_SHORT
import data.io.app.SettingsKeys.KEY_HOMESERVERS
import data.io.base.BaseResponse
import koin.settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import ui.login.homeserver_picker.HomeserverPickerRepository.WellKnownServer

internal val homeserverPickerModule = module {
    factory { HomeserverPickerRepository(get(), get()) }
    viewModelOf(::HomeserverPickerModel)
}

class HomeserverPickerModel(
    private val repository: HomeserverPickerRepository
): ViewModel() {
    companion object {
        private const val HOMESERVER_REGEX = """^([a-zA-Z0-9.-]+|\[[0-9a-fA-F:.]+\])(:\d+)?$"""
    }

    data class HomeserverAddress(
        val identifier: String,
        val address: String
    )

    private val _state = MutableStateFlow<BaseResponse<WellKnownServer>>(BaseResponse.Idle)
    private val _homeservers = MutableStateFlow<List<HomeserverAddress>>(listOf())
    val state = _state.asStateFlow()
    val homeservers = _homeservers.asStateFlow()

    init {
        viewModelScope.launch {
            _homeservers.value = settings.getStringOrNull(KEY_HOMESERVERS)?.split(',')?.mapNotNull { item ->
                val values = item.split('_')
                values.firstOrNull()?.let { identifier ->
                    values.lastOrNull()?.let { address ->
                        HomeserverAddress(identifier, address)
                    }
                }
            } ?: listOf(
                HomeserverAddress(AUGMY_HOMESERVER_IDENTIFIER, AUGMY_HOME_SERVER_ADDRESS),
                HomeserverAddress(MATRIX_HOME_SERVER, MATRIX_HOME_SERVER)
            )
        }
    }

    private val validateScope = CoroutineScope(Job())
    fun validateHomeserver(homeserver: CharSequence) {
        if (!homeserver.matches(Regex(HOMESERVER_REGEX))) {
            _state.value = BaseResponse.Error()
            return
        }

        _state.value = BaseResponse.Loading
        validateScope.coroutineContext.cancelChildren()
        validateScope.launch {
            delay(DELAY_BETWEEN_REQUESTS_SHORT)
            repository.getWellKnown(homeserver.toString()).let { server ->
                if (!server?.server.isNullOrBlank()) {
                    repository.validateHomeserver(server.server).also { response ->
                        _state.value = if (response.isSuccess) {
                            BaseResponse.Success(server)
                        } else BaseResponse.Error()
                    }
                } else _state.value = BaseResponse.Error() 
            }
        }
    }

    fun saveHomeserverAddress(identifier: String) {
        _state.value.data?.server?.let { address ->
            CoroutineScope(Job()).launch {
                settings.putString(
                    KEY_HOMESERVERS,
                    _homeservers.value.plus(
                        HomeserverAddress(identifier, address)
                    ).distinctBy { it.address }.joinToString(",") { item ->
                        "${item.identifier}_${item.address}"
                    }
                )
            }
        }
    }
}
