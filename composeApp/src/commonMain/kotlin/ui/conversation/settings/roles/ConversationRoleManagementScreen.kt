package ui.conversation.settings.roles

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import augmy.composeapp.generated.resources.Res
import augmy.composeapp.generated.resources.accessibility_permission_down
import augmy.composeapp.generated.resources.accessibility_permission_up
import augmy.interactive.shared.ext.scalingClickable
import augmy.interactive.shared.ui.theme.LocalTheme
import base.BrandBaseScreen
import base.theme.Colors.ProximityPublic
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.context.loadKoinModules
import org.koin.core.parameter.parametersOf
import ui.conversation.ConversationUtils
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
data class ConversationPermission(
    val power: Long? = 0,
    val icon: ImageVector,
    val labelRes: StringResource,
    val uid: String = Uuid.random().toString(),
    val type: ConversationUtils.PermissionType
)

@Composable
fun ConversationRoleManagementScreen(
    roomId: String?
) {
    loadKoinModules(conversationRoleManagementModule)
    val model = koinViewModel<ConversationRoleManagementModel>(
        key = roomId,
        parameters = {
            parametersOf(roomId ?: "")
        }
    )
    val roles = model.roles.collectAsState(null)
    val circleColors = model.socialCircleColors.collectAsState(null)
    val isLoading = model.isLoading.collectAsState()
    val colors = circleColors.value?.values?.toList()
    val density = LocalDensity.current
    val maxLabelHeight = remember { mutableStateOf(0) }

    BrandBaseScreen(
        /*floatingActionButton = {
            AnimatedVisibility(roles.value != null) {
                BrandHeaderButton(
                    endImageVector = Icons.Outlined.Add,
                    contentPadding = PaddingValues(16.dp),
                    shape = LocalTheme.current.shapes.rectangularActionShape
                ) {
                    model.addNewRole()
                }
            }
        }*/
    ) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            itemsIndexed(
                items = roles.value.orEmpty(),
                key = { _, data -> data.role.uid }
            ) { index, data ->
                val tagColor = colors?.getOrNull(index) ?: colors?.lastOrNull() ?: ProximityPublic

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        modifier = Modifier
                            .padding(end = 12.dp, top = 4.dp)
                            .onSizeChanged { if (it.width > maxLabelHeight.value) maxLabelHeight.value = it.width }
                            .widthIn(min = with(density) { maxLabelHeight.value.toDp() }),
                        text = data.role.label,
                        style = LocalTheme.current.styles.category.copy(color = tagColor)
                    )

                    Column(modifier = Modifier.fillMaxWidth()) {
                        data.permissions.forEach { permission ->
                            Row(
                                modifier = Modifier.height(24.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    modifier = Modifier.size(18.dp),
                                    imageVector = permission.icon,
                                    contentDescription = null,
                                    tint = LocalTheme.current.colors.secondary
                                )
                                Text(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 6.dp),
                                    style = LocalTheme.current.styles.regular,
                                    text = stringResource(permission.labelRes)
                                )

                                AnimatedVisibility(
                                    visible = index != roles.value?.lastIndex && !isLoading.value
                                ) {
                                    Icon(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .scalingClickable {
                                                model.movePermission(
                                                    fromIndex = index,
                                                    toIndex = index + 1,
                                                    type = permission.type
                                                )
                                            },
                                        imageVector = Icons.Outlined.ArrowDownward,
                                        tint = LocalTheme.current.colors.secondary,
                                        contentDescription = stringResource(Res.string.accessibility_permission_down)
                                    )
                                }
                                AnimatedVisibility(
                                    visible = index != 0 && !isLoading.value
                                ) {
                                    Icon(
                                        modifier = Modifier
                                            .padding(start = 6.dp)
                                            .size(24.dp)
                                            .scalingClickable {
                                                model.movePermission(
                                                    fromIndex = index,
                                                    toIndex = index - 1,
                                                    type = permission.type
                                                )
                                            },
                                        imageVector = Icons.Outlined.ArrowUpward,
                                        tint = LocalTheme.current.colors.secondary,
                                        contentDescription = stringResource(Res.string.accessibility_permission_up)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
