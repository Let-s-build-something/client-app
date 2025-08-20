package ui.conversation.media

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import augmy.composeapp.generated.resources.Res
import augmy.composeapp.generated.resources.conversation_detail_you
import augmy.interactive.shared.ext.detectMessageInteraction
import augmy.interactive.shared.ext.draggable
import augmy.interactive.shared.ui.base.LocalIsMouseUser
import augmy.interactive.shared.ui.base.LocalNavController
import augmy.interactive.shared.ui.base.LocalScreenSize
import augmy.interactive.shared.ui.theme.LocalTheme
import augmy.interactive.shared.utils.DateUtils.formatAsRelative
import base.navigation.NavigationNode
import base.theme.Colors
import base.utils.MatrixUtils.Media.MATRIX_REPOSITORY_PREFIX
import base.utils.MediaType
import data.io.social.network.conversation.message.FullConversationMessage
import data.io.social.network.conversation.message.MediaIO
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import org.koin.compose.viewmodel.koinViewModel
import ui.conversation.components.MEDIA_MAX_HEIGHT_DP
import ui.conversation.components.MediaElement
import ui.conversation.components.audio.MediaProcessorModel

@Composable
fun MediaRow(
    modifier: Modifier = Modifier,
    mediaProcessorModel: MediaProcessorModel = koinViewModel(),
    scrollState: ScrollState,
    data: FullConversationMessage?,
    media: List<MediaIO>,
    temporaryFiles: Map<String, PlatformFile?>,
    isCurrentUser: Boolean,
    onDragChange: (change: PointerInputChange, dragAmount: Offset) -> Unit,
    onDrag: (dragged: Boolean) -> Unit,
    onLongPress: (Offset) -> Unit,
) {
    if(media.isNotEmpty() && data != null) {
        val navController = LocalNavController.current
        val isMouseUser = LocalIsMouseUser.current
        val density = LocalDensity.current
        val screenSize = LocalScreenSize.current
        val coroutineScope = rememberCoroutineScope()

        val showSpacers = remember(data.id) {
            mutableStateOf(false)
        }
        val date = data.data.sentAt?.formatAsRelative() ?: ""
        val hoverInteractionSource = remember { MutableInteractionSource() }
        val cachedMedia = mediaProcessorModel.cachedFiles.collectAsState()
        val isHovered = hoverInteractionSource.collectIsHoveredAsState()

        LaunchedEffect(media) {
            withContext(Dispatchers.Default) {
                mediaProcessorModel.cacheFiles(
                    *media.filter { it.url?.startsWith(MATRIX_REPOSITORY_PREFIX) == true }.toTypedArray()
                )
            }
        }

        Row(
            modifier = modifier
                .wrapContentWidth()
                .horizontalScroll(state = scrollState)
                .draggable(state = scrollState)
                .hoverable(
                    enabled = isMouseUser,
                    interactionSource = hoverInteractionSource
                )
                .onSizeChanged {
                    showSpacers.value = with(density) { it.width.toDp().value } >= screenSize.width * .8f
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            if(isCurrentUser && media.size > 1) {
                AnimatedVisibility(!isHovered.value && showSpacers.value) {
                    Spacer(Modifier.width((LocalScreenSize.current.width * .3f).dp))
                }
            }
            data.media.forEachIndexed { index, media ->
                val temporaryMedia = temporaryFiles[media.url]
                val canBeVisualized = MediaType.fromMimeType(media.mimetype ?: "").isVisual

                val onTap: (Offset) -> Unit = {
                    coroutineScope.launch {
                        navController?.navigate(
                            NavigationNode.MediaDetail(
                                media = data.media.map { cachedMedia.value[it.url] ?.success?.data?: it },
                                selectedIndex = index,
                                title = if(isCurrentUser) {
                                    getString(Res.string.conversation_detail_you)
                                } else data.author?.displayName,
                                subtitle = date
                            )
                        )
                    }
                }

                MediaElement(
                    modifier = (if((data.data.state?.ordinal ?: 0) > 0 && canBeVisualized) {
                        modifier.pointerInput(data.id) {
                            if(isMouseUser) {
                                detectTapGestures(
                                    onTap = onTap,
                                    onLongPress = onLongPress
                                )
                            }else {
                                detectMessageInteraction(
                                    onTap = onTap,
                                    onLongPress = onLongPress,
                                    onDragChange = onDragChange,
                                    onDrag = onDrag
                                )
                            }
                        }
                    }else modifier)
                        .then(if((media.size ?: 0) > 1) {
                            Modifier.padding(horizontal = LocalTheme.current.shapes.betweenItemsSpace)
                        } else Modifier)
                        .heightIn(min = MEDIA_MAX_HEIGHT_DP.div(2).dp)
                        .clip(
                            RoundedCornerShape(
                                topStart = LocalTheme.current.shapes.componentCornerRadius,
                                topEnd = LocalTheme.current.shapes.componentCornerRadius,
                                bottomStart = if(data.data.content.isNullOrBlank()) {
                                    LocalTheme.current.shapes.componentCornerRadius
                                }else 0.dp,
                                bottomEnd = if(data.data.content.isNullOrBlank()) {
                                    LocalTheme.current.shapes.componentCornerRadius
                                }else 0.dp
                            )
                        ),
                    visualHeight = MEDIA_MAX_HEIGHT_DP.dp,
                    tintColor = if(isCurrentUser) Colors.GrayLight else LocalTheme.current.colors.secondary,
                    media = cachedMedia.value[media.url]?.success?.data ?: media,
                    localMedia = temporaryMedia,
                    enabled = false,
                    contentScale = ContentScale.FillHeight
                )
            }
            if(!isCurrentUser && media.size > 1) {
                AnimatedVisibility(!isHovered.value && showSpacers.value) {
                    Spacer(Modifier.width((LocalScreenSize.current.width * .3f).dp))
                }
            }
        }
    }
}