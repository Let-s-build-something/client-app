package ui.search.user

import androidx.lifecycle.viewModelScope
import data.io.user.NetworkItemIO
import data.shared.SharedModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

internal val searchUserModule = module {
    factory { SearchUserRepository(get(), get(), get()) }
    viewModelOf(::SearchUserModel)
}

/** Communication between the UI, the control layers, and control and data layers */
class SearchUserModel(
    private val repository: SearchUserRepository
): SharedModel() {

    companion object {
        const val ITEMS_COUNT = 10
    }

    private val _users = MutableStateFlow<List<NetworkItemIO>?>(null)

    val users = _users.asStateFlow()

    /** Queries both for local and remote users that match the searched term */
    fun queryUsers(prompt: CharSequence, excludeUsers: List<String>) {
        if(prompt.isBlank() && _users.value.isNullOrEmpty()) return

        viewModelScope.launch(Dispatchers.Default) {
            _users.value = repository.queryForUsers(
                limit = ITEMS_COUNT,
                homeserver = homeserverAddress,
                prompt = prompt.toString()
            )?.distinctBy { it.userId }.let { list ->
                if(excludeUsers.isNotEmpty()) {
                    list?.filter { excludeUsers.contains(it.userId).not() }
                }else list
            }
        }
    }

    fun saveUser(
        user: NetworkItemIO,
        onResult: () -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.saveUser(user.also {
                it.ownerUserId = matrixUserId
            })
            onResult()
        }
    }
}
