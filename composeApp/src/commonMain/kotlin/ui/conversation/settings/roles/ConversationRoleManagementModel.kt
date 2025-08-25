package ui.conversation.settings.roles

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewModelScope
import base.utils.orDefault
import base.utils.orZero
import base.utils.tagToColor
import data.NetworkProximityCategory
import data.io.matrix.room.FullConversationRoom
import data.io.social.network.conversation.ConversationRole
import data.shared.SharedModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.folivo.trixnity.core.model.RoomId
import net.folivo.trixnity.core.model.events.EventType
import net.folivo.trixnity.core.model.events.m.room.AvatarEventContent
import net.folivo.trixnity.core.model.events.m.room.NameEventContent
import net.folivo.trixnity.core.model.events.m.room.PowerLevelsEventContent
import net.folivo.trixnity.core.model.events.m.room.get
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import ui.conversation.ConversationDataManager
import ui.conversation.ConversationUtils
import ui.conversation.ConversationUtils.getConversationPermissions
import ui.conversation.ConversationUtils.getDefaultRoles

val conversationRoleManagementModule = module {
    factory {
        ConversationRoleManagementRepository(get(), get())
    }
    factory {
        ConversationRoleManagementModel(get(), get(), get())
    }
    viewModelOf(::ConversationRoleManagementModel)
}

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationRoleManagementModel(
    val roomId: String,
    val dataManager: ConversationDataManager,
    val repository: ConversationRoleManagementRepository
): SharedModel() {
    data class RoleWithPermissions(
        val role: ConversationRole,
        val permissions: List<ConversationPermission>
    )

    private val _roles = MutableStateFlow<List<ConversationRole>?>(null)
    private val _isLoading = MutableStateFlow(false)
    private val conversation = dataManager.conversations
        .mapLatest { it.second[roomId] }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )
    private val permissions = conversation
        .mapLatest { detail ->
            getConversationPermissions(
                detail?.data?.summary?.powerLevels?.kick,
                detail?.data?.summary?.powerLevels?.ban,
                detail?.data?.summary?.powerLevels?.invite,
                detail?.data?.summary?.powerLevels?.redact,
                detail?.data?.summary?.powerLevels?.events?.get<PowerLevelsEventContent>().orZero(),
                detail?.data?.summary?.powerLevels?.events?.get<AvatarEventContent>()
                    .orDefault(detail?.data?.summary?.powerLevels?.stateDefault.orZero()),
                detail?.data?.summary?.powerLevels?.events?.get<NameEventContent>()
                    .orDefault(detail?.data?.summary?.powerLevels?.stateDefault.orZero())
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )

    val isLoading = _isLoading.asStateFlow()

    val roles = _roles.combine(permissions) { roles, permissions ->
        withContext(Dispatchers.Default) {
            var limitedPermissions = permissions.orEmpty()
            roles?.sortedBy { it.power }?.map { role ->
                RoleWithPermissions(
                    role = role,
                    permissions = limitedPermissions.filter { it.power.orZero() <= role.power }.also {
                        limitedPermissions = limitedPermissions.minus(it)
                    }
                )
            }?.sortedByDescending { it.role.power }.orEmpty()
        }
    }
    val socialCircleColors: Flow<Map<NetworkProximityCategory, Color>> = localSettings.map { settings ->
        withContext(Dispatchers.Default) {
            settings?.networkColors?.mapIndexedNotNull { index, s ->
                tagToColor(s)?.let { color ->
                    NetworkProximityCategory.entries[index] to color
                }
            }.orEmpty().toMap()
        }
    }

    init {
        viewModelScope.launch {
            _roles.value = repository.getAllRoles(roomId).let {
                it.ifEmpty {
                    val default = getDefaultRoles()
                    repository.insertRoles(default)
                    default
                }
            }
        }
    }

    /*
    fun addNewRole() {
        viewModelScope.launch {
            _roles.update {
                it?.plus(ConversationRole(label = "New Role", roomId = roomId, power = 0L))
            }
        }
    }

    fun moveRole(fromIndex: Int, toIndex: Int) {
        if (fromIndex == -1 || toIndex == 1) return

        viewModelScope.launch {
            _roles.update { prev ->
                prev?.toMutableList()?.apply {
                    add(toIndex, removeAt(fromIndex))
                    // TD reassign permissions. We have previously had them by role, now roles' changed, so just swap the two

                    forEachIndexed { index, role ->
                        when (index) {
                            0 -> role.power = 100
                            lastIndex -> role.power = 10
                            else -> (size - index) * 100 / size
                        }
                    }

                    // TD change permissions power levels due to role changes
                    repository.insertRoles(this)
                }
            }
        }
    }
    */

    fun movePermission(fromIndex: Int, toIndex: Int, type: ConversationUtils.PermissionType) {
        viewModelScope.launch {
            _isLoading.value = true
            val fromRole = _roles.value?.getOrNull(fromIndex)
            val toRole = _roles.value?.getOrNull(toIndex)
            if (toRole?.power == null || fromRole == null) return@launch

            val update = ((matrixClient?.api?.room?.getStateEvent(
                "m.room.power_levels",
                RoomId(roomId)
            )?.getOrNull() as? PowerLevelsEventContent) ?: conversation.value?.data?.summary?.powerLevels)?.let { prev ->
                when(type) {
                    ConversationUtils.PermissionType.Kick -> prev.copy(kick = toRole.power)
                    ConversationUtils.PermissionType.Ban -> prev.copy(ban = toRole.power)
                    ConversationUtils.PermissionType.Invite -> prev.copy(invite = toRole.power)
                    ConversationUtils.PermissionType.Redact -> prev.copy(redact = toRole.power)
                    ConversationUtils.PermissionType.Manage -> prev.copy(
                        events = prev.events.plus(
                            (EventType(
                                PowerLevelsEventContent::class,
                                "m.room.power_levels"
                            ) to toRole.power)
                        )
                    )
                    ConversationUtils.PermissionType.Avatar -> prev.copy(
                        events = prev.events.plus(
                            (EventType(
                                AvatarEventContent::class,
                                "m.room.avatar"
                            ) to toRole.power)
                        )
                    )
                    ConversationUtils.PermissionType.Name -> prev.copy(
                        events = prev.events.plus(
                            (EventType(
                                NameEventContent::class,
                                "m.room.name"
                            ) to toRole.power)
                        )
                    )
                }
            }

            if (update != null) {
                if (matrixClient?.api?.room?.sendStateEvent(
                        roomId = RoomId(roomId),
                        eventContent = update
                    )?.getOrNull() != null) {
                    conversation.value?.data?.copy(
                        summary = conversation.value?.data?.summary?.copy(powerLevels = update)
                    )?.let { newRoom ->
                        repository.updateRoom(newRoom)
                        dataManager.updateConversations { prev ->
                            prev.apply {
                                this[roomId] = this[roomId]?.copy(data = newRoom) ?: FullConversationRoom(newRoom)
                            }
                        }
                    }
                }
            }
            _isLoading.value = false
        }
    }
}
