package ui.conversation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.FaceRetouchingOff
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Moving
import androidx.compose.material.icons.outlined.TextFields
import augmy.composeapp.generated.resources.Res
import augmy.composeapp.generated.resources.conversation_power_avatar
import augmy.composeapp.generated.resources.conversation_power_ban
import augmy.composeapp.generated.resources.conversation_power_invite
import augmy.composeapp.generated.resources.conversation_power_kick
import augmy.composeapp.generated.resources.conversation_power_manage
import augmy.composeapp.generated.resources.conversation_power_name
import augmy.composeapp.generated.resources.conversation_power_redact
import augmy.composeapp.generated.resources.conversation_role_admin
import augmy.composeapp.generated.resources.conversation_role_member
import augmy.composeapp.generated.resources.conversation_role_moderator
import augmy.composeapp.generated.resources.conversation_role_user
import data.io.social.network.conversation.ConversationRole
import org.jetbrains.compose.resources.getString
import ui.conversation.settings.roles.ConversationPermission

object ConversationUtils {

    suspend fun getDefaultRoles() = listOf(
        ConversationRole(power = 100, label = getString(Res.string.conversation_role_admin), roomId = ""),
        ConversationRole(power = 75, label = getString(Res.string.conversation_role_moderator), roomId = ""),
        ConversationRole(power = 50, label = getString(Res.string.conversation_role_member), roomId = ""),
        ConversationRole(power = 10, label = getString(Res.string.conversation_role_user), roomId = ""),
    )

    enum class PermissionType {
        Kick,
        Ban,
        Invite,
        Redact,
        Manage,
        Avatar,
        Name
    }

    fun getConversationPermissions(
        kick: Long?,
        ban: Long?,
        invite: Long?,
        redact: Long?,
        powerLevels: Long?,
        avatar: Long?,
        name: Long?,
    ) = listOf(
        ConversationPermission(
            kick,
            Icons.Outlined.FaceRetouchingOff,
            Res.string.conversation_power_kick,
            type = PermissionType.Kick
        ),
        ConversationPermission(
            ban,
            Icons.Outlined.Block,
            Res.string.conversation_power_ban,
            type = PermissionType.Ban
        ),
        ConversationPermission(
            invite,
            Icons.AutoMirrored.Outlined.Send,
            Res.string.conversation_power_invite,
            type = PermissionType.Invite
        ),
        ConversationPermission(
            redact,
            Icons.Outlined.Build,
            Res.string.conversation_power_redact,
            type = PermissionType.Redact
        ),
        ConversationPermission(
            powerLevels,
            Icons.Outlined.Moving,
            Res.string.conversation_power_manage,
            type = PermissionType.Manage
        ),
        ConversationPermission(
            avatar,
            Icons.Outlined.Image,
            Res.string.conversation_power_avatar,
            type = PermissionType.Avatar
        ),
        ConversationPermission(
            name,
            Icons.Outlined.TextFields,
            Res.string.conversation_power_name,
            type = PermissionType.Name
        )
    )
}