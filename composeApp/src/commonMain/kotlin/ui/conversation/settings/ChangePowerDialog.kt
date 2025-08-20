package ui.conversation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.FaceRetouchingOff
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Moving
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import augmy.composeapp.generated.resources.Res
import augmy.composeapp.generated.resources.button_confirm
import augmy.composeapp.generated.resources.button_dismiss
import augmy.composeapp.generated.resources.conversation_power_avatar
import augmy.composeapp.generated.resources.conversation_power_ban
import augmy.composeapp.generated.resources.conversation_power_invite
import augmy.composeapp.generated.resources.conversation_power_kick
import augmy.composeapp.generated.resources.conversation_power_manage
import augmy.composeapp.generated.resources.conversation_power_message
import augmy.composeapp.generated.resources.conversation_power_name
import augmy.composeapp.generated.resources.conversation_power_none
import augmy.composeapp.generated.resources.conversation_power_redact
import augmy.composeapp.generated.resources.conversation_power_title
import augmy.composeapp.generated.resources.conversation_role_admin
import augmy.composeapp.generated.resources.conversation_role_member
import augmy.composeapp.generated.resources.conversation_role_moderator
import augmy.composeapp.generated.resources.conversation_role_user
import augmy.interactive.shared.ext.scalingClickable
import augmy.interactive.shared.ui.components.dialog.AlertDialog
import augmy.interactive.shared.ui.components.dialog.ButtonState
import augmy.interactive.shared.ui.theme.LocalTheme
import base.utils.orDefault
import base.utils.orZero
import data.io.matrix.room.event.ConversationRoomMember
import net.folivo.trixnity.core.model.UserId
import net.folivo.trixnity.core.model.events.m.room.AvatarEventContent
import net.folivo.trixnity.core.model.events.m.room.NameEventContent
import net.folivo.trixnity.core.model.events.m.room.PowerLevelsEventContent
import net.folivo.trixnity.core.model.events.m.room.get
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource


sealed class ConversationRoomRole(
    val power: Long,
    val labelRes: StringResource
) {
    data object Admin: ConversationRoomRole(100, Res.string.conversation_role_admin)
    data object Moderator: ConversationRoomRole(50, Res.string.conversation_role_moderator)
    data object Member: ConversationRoomRole(10, Res.string.conversation_role_member)
    data object User: ConversationRoomRole(0, Res.string.conversation_role_user)
}

@Composable
internal fun ChangePowerDialog(
    member: ConversationRoomMember,
    model: ConversationSettingsModel,
    onDismissRequest: () -> Unit
) {
    val defaultRoles = listOf(
        ConversationRoomRole.Admin,
        ConversationRoomRole.Moderator,
        ConversationRoomRole.Member,
        ConversationRoomRole.User
    )
    val detail = model.conversation.collectAsState()
    val defaultPower = detail.value?.data?.summary?.powerLevels?.users?.get(
        UserId(member.userId)
    ) ?: detail.value?.data?.summary?.powerLevels?.usersDefault.orZero()

    val selectedPower = remember { mutableStateOf(defaultPower) }

    AlertDialog(
        title = stringResource(Res.string.conversation_power_title),
        message = AnnotatedString(
            stringResource(
                Res.string.conversation_power_message,
                member.displayName ?: member.userId
            )
        ),
        additionalContent = {
            val items = listOf(
                Triple(
                    detail.value?.data?.summary?.powerLevels?.kick,
                    Icons.Outlined.FaceRetouchingOff,
                    Res.string.conversation_power_kick
                ),
                Triple(
                    detail.value?.data?.summary?.powerLevels?.ban,
                    Icons.Outlined.Block,
                    Res.string.conversation_power_ban
                ),
                Triple(
                    detail.value?.data?.summary?.powerLevels?.events?.get<PowerLevelsEventContent>().orZero(),
                    Icons.Outlined.Moving,
                    Res.string.conversation_power_manage
                ),
                Triple(
                    detail.value?.data?.summary?.powerLevels?.invite,
                    Icons.AutoMirrored.Outlined.Send,
                    Res.string.conversation_power_invite
                ),
                Triple(
                    detail.value?.data?.summary?.powerLevels?.redact,
                    Icons.Outlined.Build,
                    Res.string.conversation_power_redact
                ),
                Triple(
                    detail.value?.data?.summary?.powerLevels?.events?.get<AvatarEventContent>()
                        .orDefault(detail.value?.data?.summary?.powerLevels?.stateDefault.orZero()),
                    Icons.Outlined.Image,
                    Res.string.conversation_power_avatar
                ),
                Triple(
                    detail.value?.data?.summary?.powerLevels?.events?.get<NameEventContent>()
                        .orDefault(detail.value?.data?.summary?.powerLevels?.stateDefault.orZero()),
                    Icons.Outlined.TextFields,
                    Res.string.conversation_power_name
                )
            )
            val sortedGroups = items.groupBy { it.first.orZero() }.let {
                if (!it.contains(0)) {
                    it.plus(
                        0L to listOf(
                            Triple(
                                0L,
                                Icons.Outlined.Circle,
                                Res.string.conversation_power_none
                            )
                        )
                    )
                }else it
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                val density = LocalDensity.current
                val maxHeight = remember { mutableStateOf(0) }

                sortedGroups.keys.sortedByDescending { it }.forEach { powerLevel ->
                    val values = sortedGroups[powerLevel]

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .scalingClickable(scaleInto = .95f, hoverEnabled = false) {
                                selectedPower.value = powerLevel
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            RadioButton(
                                selected = selectedPower.value == powerLevel,
                                onClick = { selectedPower.value = powerLevel },
                                colors = LocalTheme.current.styles.radioButtonColors
                            )
                            Text(
                                modifier = Modifier
                                    .onSizeChanged {
                                        if (it.width > maxHeight.value) maxHeight.value = it.width
                                    }
                                    .widthIn(min = with(density) { maxHeight.value.toDp() }),
                                text = defaultRoles.findLast { it.power >= powerLevel }?.labelRes?.let {
                                    stringResource(it)
                                } ?: powerLevel.toString(),
                                style = LocalTheme.current.styles.subheading
                            )
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                        ) {
                            values?.forEach { (_, icon, labelRes) ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        modifier = Modifier.size(18.dp),
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = LocalTheme.current.colors.secondary
                                    )
                                    Text(
                                        modifier = Modifier.padding(start = 6.dp),
                                        style = LocalTheme.current.styles.regular,
                                        text = stringResource(labelRes)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        icon = Icons.Outlined.Moving,
        confirmButtonState = ButtonState(
            text = stringResource(Res.string.button_confirm),
            enabled = selectedPower.value != defaultPower
        ) {
            model.changePowerOf(member, selectedPower.value)
        },
        dismissButtonState = ButtonState(text = stringResource(Res.string.button_dismiss)),
        onDismissRequest = onDismissRequest
    )
}