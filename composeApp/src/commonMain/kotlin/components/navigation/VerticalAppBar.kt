package components.navigation

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import augmy.composeapp.generated.resources.Res
import augmy.composeapp.generated.resources.accessibility_refresh
import augmy.interactive.shared.ui.base.PlatformType
import augmy.interactive.shared.ui.base.currentPlatform
import augmy.interactive.shared.ui.components.navigation.ActionBarIcon
import augmy.interactive.shared.ui.components.navigation.HorizontalAppBar
import augmy.interactive.shared.ui.components.navigation.NavigationIcon
import augmy.interactive.shared.ui.theme.LocalTheme
import base.navigation.NavIconType
import components.pull_refresh.LocalRefreshCallback
import data.shared.SharedViewModel
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel

/**
 * Custom app bar with options of customization
 *
 * @param actions other actions on the right side of the action bar
 */
@Composable
fun VerticalAppBar(
    modifier: Modifier = Modifier,
    actions: @Composable (Boolean) -> Unit
) {
    val sharedViewModel = koinViewModel<SharedViewModel>()

    val isBarExpanded = sharedViewModel.isToolbarExpanded.collectAsState()
    val actionsWidth = remember { mutableFloatStateOf(0f) }

    Column(
        modifier = modifier
            .wrapContentWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            Modifier
                .onGloballyPositioned {
                    actionsWidth.value = it.size.width.toFloat()
                }
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            actions(isBarExpanded.value)
            CustomDivider(actionsWidth.value)
            // TODO list of sections
        }

        Column {
            if(currentPlatform == PlatformType.Jvm) {
                LocalRefreshCallback.current?.let { refresher ->
                    NavigationIcon(
                        onClick = {
                            refresher()
                        },
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = stringResource(Res.string.accessibility_refresh)
                    )
                }
            }

            CustomDivider(actionsWidth.value)

            NavIconType.HAMBURGER.imageVector?.let {
                NavigationIcon(
                    onClick = {
                        sharedViewModel.changeToolbarState(!isBarExpanded.value)
                    },
                    imageVector = it.first,
                    contentDescription = it.second
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.CustomDivider(actionsWidth: Float) {
    val density = LocalDensity.current

    Box(
        modifier = Modifier
            .padding(vertical = 6.dp)
            .align(Alignment.CenterHorizontally)
            .height(2.dp)
            .widthIn(
                min = 32.dp,
                max = with(density) { actionsWidth.toDp() / 2 }
            )
            .fillMaxWidth()
            .background(
                color = LocalTheme.current.colors.appbarContent,
                shape = CircleShape
            )
    )
}

@Preview
@Composable
private fun Preview() {
    HorizontalAppBar(
        modifier = Modifier.fillMaxWidth(),
        actions = {
            ActionBarIcon(
                text = "play",
                imageVector = Icons.Outlined.PlayArrow
            )
        },
        subtitle = "subtitle subtitle subtitle subtitle subtitle subtitle subtitle subtitle subtitle subtitle subtitle subtitle subtitle subtitle subtitle subtitle ",
        title = "title title title title title title title title title title title title title title title "
    )
}