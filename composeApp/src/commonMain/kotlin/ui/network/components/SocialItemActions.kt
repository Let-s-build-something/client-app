package ui.network.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FaceRetouchingOff
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material.icons.outlined.VoiceOverOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import augmy.composeapp.generated.resources.Res
import augmy.composeapp.generated.resources.accessibility_cancel
import augmy.composeapp.generated.resources.accessibility_save
import augmy.composeapp.generated.resources.button_confirm
import augmy.composeapp.generated.resources.button_dismiss
import augmy.composeapp.generated.resources.button_mute
import augmy.composeapp.generated.resources.button_remove
import augmy.composeapp.generated.resources.network_action_circle_move
import augmy.composeapp.generated.resources.network_dialog_message_block
import augmy.composeapp.generated.resources.network_dialog_message_mute
import augmy.composeapp.generated.resources.network_dialog_title_block
import augmy.composeapp.generated.resources.network_dialog_title_mute
import augmy.interactive.shared.ext.draggable
import augmy.interactive.shared.ext.scalingClickable
import augmy.interactive.shared.ui.components.OutlinedButton
import augmy.interactive.shared.ui.components.SimpleModalBottomSheet
import augmy.interactive.shared.ui.components.dialog.AlertDialog
import augmy.interactive.shared.ui.components.dialog.ButtonState
import augmy.interactive.shared.ui.theme.LocalTheme
import augmy.interactive.shared.ui.theme.SharedColors
import base.theme.Colors
import components.OptionsLayoutAction
import data.BlockedProximityValue
import data.NetworkProximityCategory
import data.io.user.NetworkItemIO
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.context.loadKoinModules
import ui.network.add_new.networkAddNewModule

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocialItemActions(
    key: Any?,
    newItem: NetworkItemIO,
    requestProximityChange: (proximity: Float) -> Unit
) {
    val showActionDialog = remember(key) {
        mutableStateOf<OptionsLayoutAction?>(null)
    }
    val showMoveCircleDialog = remember(key) {
        mutableStateOf(false)
    }
    val selectedCategory = remember {
        mutableStateOf(NetworkProximityCategory.entries.find {
            it.range.contains(newItem.proximity ?: -1f)
        } ?: NetworkProximityCategory.Public)
    }
    val actionsState = rememberScrollState()

    showActionDialog.value?.let { action ->
        AlertDialog(
            title = stringResource(
                if(action == OptionsLayoutAction.Mute) {
                    Res.string.network_dialog_title_mute
                }else Res.string.network_dialog_title_block
            ),
            message = AnnotatedString(stringResource(
                if(action == OptionsLayoutAction.Mute) {
                    Res.string.network_dialog_message_mute
                }else Res.string.network_dialog_message_block
            )),
            icon = action.leadingImageVector,
            confirmButtonState = ButtonState(
                text = stringResource(Res.string.button_confirm)
            ) {
                requestProximityChange(
                    if(action == OptionsLayoutAction.Mute) {
                        NetworkProximityCategory.Public.range.start
                    }else BlockedProximityValue
                )
            },
            dismissButtonState = ButtonState(
                text = stringResource(Res.string.button_dismiss)
            ),
            onDismissRequest = {
                showActionDialog.value = null
            }
        )
    }

    if(showMoveCircleDialog.value) {
        loadKoinModules(networkAddNewModule)

        SimpleModalBottomSheet(
            onDismissRequest = {
                showMoveCircleDialog.value = false
            },
            scrollEnabled = false,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ProximityPicker(
                model = koinViewModel(),
                selectedCategory = selectedCategory,
                newItem = newItem
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp, alignment = Alignment.End)
            ) {
                OutlinedButton(
                    text = stringResource(Res.string.accessibility_cancel),
                    onClick = {
                        showMoveCircleDialog.value = false
                    },
                    activeColor = SharedColors.RED_ERROR_50
                )
                OutlinedButton(
                    text = stringResource(Res.string.accessibility_save),
                    onClick = {
                        requestProximityChange(
                            selectedCategory.value.range.start
                        )
                    },
                    activeColor = LocalTheme.current.colors.brandMain
                )
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = LocalTheme.current.colors.backgroundDark,
                shape = RoundedCornerShape(
                    bottomEnd = LocalTheme.current.shapes.rectangularActionRadius,
                    bottomStart = LocalTheme.current.shapes.rectangularActionRadius,
                )
            )
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .horizontalScroll(actionsState)
                .draggable(state = actionsState, orientation = Orientation.Horizontal)
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
        ) {
            Spacer(Modifier.width(LocalTheme.current.shapes.betweenItemsSpace))
            ScalingIcon(
                color = SharedColors.RED_ERROR.copy(.6f),
                imageVector = Icons.Outlined.FaceRetouchingOff,
                contentDescription = stringResource(Res.string.button_remove),
                onClick = {
                    showActionDialog.value = OptionsLayoutAction.Remove
                }
            )
            ScalingIcon(
                color = Colors.Coffee,
                imageVector = Icons.Outlined.VoiceOverOff,
                contentDescription = stringResource(Res.string.button_mute),
                onClick = {
                    showActionDialog.value = OptionsLayoutAction.Mute
                }
            )
            ScalingIcon(
                color = NetworkProximityCategory.Family.color,
                imageVector = Icons.Outlined.TrackChanges,
                contentDescription = stringResource(Res.string.network_action_circle_move),
                onClick = {
                    showMoveCircleDialog.value = true
                }
            )
            Spacer(Modifier.width(LocalTheme.current.shapes.betweenItemsSpace))
        }
    }
}

@Composable
fun ScalingIcon(
    color: Color,
    imageVector: ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        Modifier.background(
            color = color,
            shape = LocalTheme.current.shapes.componentShape
        )
    ) {
        Row(
            modifier = Modifier
                .scalingClickable(enabled) {
                    onClick()
                }
                .background(
                    color = LocalTheme.current.colors.backgroundDark,
                    shape = LocalTheme.current.shapes.rectangularActionShape
                )
                .border(
                    width = 1.dp,
                    color = color,
                    shape = LocalTheme.current.shapes.rectangularActionShape
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                modifier = Modifier.padding(start = 8.dp, end = 2.dp),
                text = contentDescription,
                style = LocalTheme.current.styles.regular
            )
            Icon(
                modifier = Modifier
                    .size(24.dp)
                    .padding(4.dp),
                imageVector = imageVector,
                contentDescription = null,
                tint = LocalTheme.current.colors.secondary
            )
        }
    }
}
