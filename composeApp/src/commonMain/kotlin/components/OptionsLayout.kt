package components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Deselect
import androidx.compose.material.icons.outlined.FaceRetouchingOff
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.VoiceOverOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.zIndex
import augmy.composeapp.generated.resources.Res
import augmy.composeapp.generated.resources.button_deselect
import augmy.composeapp.generated.resources.button_invite
import augmy.composeapp.generated.resources.button_mute
import augmy.composeapp.generated.resources.button_remove
import augmy.composeapp.generated.resources.button_select_all
import augmy.composeapp.generated.resources.network_dialog_message_block
import augmy.composeapp.generated.resources.network_dialog_message_mute
import augmy.composeapp.generated.resources.network_dialog_title_block
import augmy.composeapp.generated.resources.network_dialog_title_mute
import augmy.interactive.shared.ui.components.DEFAULT_ANIMATION_LENGTH_SHORT
import augmy.interactive.shared.ui.theme.LocalTheme
import augmy.interactive.shared.ui.theme.SharedColors
import base.theme.Colors
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** default option for items selection */
val checkedOptionsItems = listOf(
    OptionsLayoutAction.SelectAll,
    OptionsLayoutAction.DeselectAll,
    OptionsLayoutAction.Invite
)

/**
 * Standardised way of giving user options for editing or handling list of items
 */
@Composable
fun OptionsLayout(
    modifier: Modifier = Modifier,
    actions: List<OptionsLayoutAction> = checkedOptionsItems,
    show: Boolean = false,
    zIndex: Float = 1f,
    onClick: (OptionsLayoutAction) -> Unit = {},
    content: LazyListScope.() -> Unit = {}
) {
    AnimatedVisibility(
        modifier = Modifier.zIndex(zIndex),
        visible = actions.isNotEmpty() && show,
        enter = slideInVertically (
            initialOffsetY = { -it },
            animationSpec = tween(DEFAULT_ANIMATION_LENGTH_SHORT)
        ),
        exit = slideOutVertically (
            targetOffsetY = { -it },
            animationSpec = tween(DEFAULT_ANIMATION_LENGTH_SHORT)
        )
    ) {
        LazyRow(
            modifier = modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .zIndex(zIndex),
            horizontalArrangement = Arrangement.spacedBy(LocalTheme.current.shapes.betweenItemsSpace)
        ) {
            item {
                Spacer(Modifier)
            }
            items(
                items = actions,
                key = { action -> action.toString() }
            ) { action ->
                ImageAction(
                    modifier = Modifier.animateItem(),
                    leadingImageVector = action.leadingImageVector,
                    text = stringResource(action.textRes),
                    onClick = {
                        onClick(action)
                    },
                    contentColor = Colors.DutchWhite,
                    containerColor = action.containerColor ?: LocalTheme.current.colors.brandMain
                )
            }
            content()
            item {
                Spacer(modifier = Modifier.width(LocalTheme.current.shapes.betweenItemsSpace))
            }
        }
    }
}

sealed class OptionsLayoutAction(
    open val textRes: StringResource,
    open val leadingImageVector: ImageVector,
    open val containerColor: Color? = null,
    open val title: StringResource? = null,
    open val message: StringResource? = null
) {
    data object SelectAll: OptionsLayoutAction(
        textRes = Res.string.button_select_all,
        leadingImageVector = Icons.Outlined.SelectAll
    )
    data object DeselectAll: OptionsLayoutAction(
        textRes = Res.string.button_deselect,
        leadingImageVector = Icons.Outlined.Deselect
    )
    data object Invite: OptionsLayoutAction(
        textRes = Res.string.button_invite,
        leadingImageVector = Icons.Outlined.Add
    )
    data object Mute: OptionsLayoutAction(
        textRes = Res.string.button_mute,
        title = Res.string.network_dialog_title_mute,
        message = Res.string.network_dialog_message_mute,
        leadingImageVector = Icons.Outlined.VoiceOverOff,
        containerColor = SharedColors.RED_ERROR
    )
    data object Remove: OptionsLayoutAction(
        textRes = Res.string.button_remove,
        title = Res.string.network_dialog_title_block,
        message = Res.string.network_dialog_message_block,
        leadingImageVector = Icons.Outlined.FaceRetouchingOff,
        containerColor = SharedColors.RED_ERROR.copy(alpha = 0.6f)
    )
}
