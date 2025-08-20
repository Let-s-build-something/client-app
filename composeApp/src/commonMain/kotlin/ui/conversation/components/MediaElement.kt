package ui.conversation.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FilePresent
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import augmy.composeapp.generated.resources.Res
import augmy.composeapp.generated.resources.accessibility_message_file
import augmy.composeapp.generated.resources.accessibility_message_pdf
import augmy.composeapp.generated.resources.accessibility_message_presentation
import augmy.composeapp.generated.resources.accessibility_message_text
import augmy.composeapp.generated.resources.accessibility_play
import augmy.composeapp.generated.resources.logo_pdf
import augmy.composeapp.generated.resources.logo_powerpoint
import augmy.interactive.shared.ext.scalingClickable
import augmy.interactive.shared.ui.theme.LocalTheme
import base.theme.Colors
import base.utils.MatrixUtils.Media.MATRIX_REPOSITORY_PREFIX
import base.utils.MediaType
import base.utils.PlatformFileShell
import base.utils.getExtensionFromMimeType
import base.utils.getUrlExtension
import chaintech.videoplayer.host.MediaPlayerHost
import chaintech.videoplayer.model.VideoPlayerConfig
import chaintech.videoplayer.ui.preview.VideoPreviewComposable
import chaintech.videoplayer.ui.video.VideoPlayerComposable
import coil3.compose.AsyncImagePainter
import components.AsyncSvgImage
import components.PlatformFileImage
import data.io.base.BaseResponse
import data.io.social.network.conversation.message.MediaIO
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.extension
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.path
import korlibs.io.net.MimeType
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import ui.conversation.components.audio.AudioMessageBubble
import ui.conversation.components.audio.MediaProcessorModel
import ui.conversation.components.gif.GifImage

/**
 * Media element, which is anything from an image, video, gif, to any type of file
 * @param media reference to a remote file containing the media
 * @param localMedia reference to a local file containing the media
 * @param contentDescription textual description of the content
 */
@Composable
fun MediaElement(
    modifier: Modifier = Modifier,
    videoPlayerEnabled: Boolean = false,
    media: MediaIO? = null,
    tintColor: Color = LocalTheme.current.colors.secondary,
    visualHeight: Dp? = null,
    localMedia: PlatformFile? = null,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Inside,
    onTap: ((MediaType) -> Unit)? = null,
    enabled: Boolean = onTap != null,
    onLongPress: () -> Unit = {},
    onState: (BaseResponse<Any>) -> Unit = {}
) {
    val newMedia = media.takeIf { it?.url?.startsWith(MATRIX_REPOSITORY_PREFIX) != true }
    var finalMedia by remember(newMedia?.url) {
        mutableStateOf(newMedia)
    }
    val mediaType = MediaType.fromMimeType(
        finalMedia?.mimetype
            ?: localMedia?.extension?.let { MimeType.getByExtension(it).mime }
            ?: finalMedia?.url?.let { MimeType.getByExtension(getUrlExtension(it)).mime }
            ?: finalMedia?.path?.let { MimeType.getByExtension(getUrlExtension(it)).mime }
            ?: "image"
    )
    val itemModifier = (if(visualHeight != null && mediaType.isVisual) {
        modifier.height(visualHeight)
    } else modifier).scalingClickable(
        enabled = enabled,
        scaleInto = .95f,
        hoverEnabled = false,
        onLongPress = {
            onLongPress()
        },
        onTap = {
            onTap?.invoke(mediaType)
        }
    )

    if(media?.url?.startsWith(MATRIX_REPOSITORY_PREFIX) == true) {
        val model: MediaProcessorModel = koinViewModel()

        LaunchedEffect(media.url) {
            onState(BaseResponse.Loading)
            model.cacheFiles(media)
        }

        LaunchedEffect(media.url) {
            model.cachedFiles.collectLatest {
                it[media.url]?.let { response ->
                    onState(response)
                    response.success?.data?.let { newMedia ->
                        finalMedia = newMedia
                    }
                }
            }
        }
    }

    if(!finalMedia?.url.isNullOrBlank() || localMedia != null) {
        when(mediaType) {
            MediaType.IMAGE -> {
                if (localMedia != null) {
                    PlatformFileImage(
                        modifier = itemModifier.wrapContentWidth(),
                        contentScale = contentScale,
                        media = localMedia,
                        onState = {
                            onState(it)
                        }
                    )
                } else if(finalMedia?.url != null) {
                    AsyncSvgImage(
                        modifier = itemModifier.wrapContentWidth(),
                        model = finalMedia?.path ?: finalMedia?.url,
                        contentScale = contentScale,
                        contentDescription = contentDescription,
                        onState = { asyncState ->
                            onState(
                                when (asyncState) {
                                    is AsyncImagePainter.State.Empty -> BaseResponse.Idle
                                    is AsyncImagePainter.State.Error -> BaseResponse.Error()
                                    is AsyncImagePainter.State.Loading -> BaseResponse.Loading
                                    is AsyncImagePainter.State.Success -> BaseResponse.Success("")
                                }
                            )
                        }
                    )
                }
            }
            MediaType.GIF -> {
                @Suppress("IMPLICIT_CAST_TO_ANY")
                (if(localMedia != null) {
                    PlatformFileShell(localMedia)
                } else finalMedia?.path ?: finalMedia?.url)?.let { data ->
                    GifImage(
                        modifier = itemModifier,
                        data = data,
                        contentDescription = contentDescription,
                        contentScale = contentScale,
                        onState = {
                            onState(it)
                        }
                    )
                }
            }
            MediaType.VIDEO -> {
                if(videoPlayerEnabled) {
                    val theme = LocalTheme.current
                    val config = VideoPlayerConfig(
                        isFullScreenEnabled = true,
                        isSpeedControlEnabled = false,
                        isMuteControlEnabled = false,
                        isSeekBarVisible = true,
                        seekBarThumbColor = theme.colors.brandMainDark,
                        seekBarActiveTrackColor = theme.colors.brandMain,
                        seekBarInactiveTrackColor = theme.colors.tetrial,
                        durationTextStyle = theme.styles.regular,
                        iconsTintColor = theme.colors.secondary,
                        loadingIndicatorColor = theme.colors.secondary,
                        isDurationVisible = true,
                        isFastForwardBackwardEnabled = false,
                        loaderView = {
                            Box(
                                modifier = itemModifier,
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .zIndex(1f)
                                        .requiredSize(32.dp),
                                    color = LocalTheme.current.colors.brandMainDark,
                                    trackColor = LocalTheme.current.colors.tetrial
                                )
                            }
                        }
                    )

                    val playerHost = remember(finalMedia?.url) {
                        MediaPlayerHost(
                            mediaUrl = localMedia?.path ?: finalMedia?.path ?: finalMedia?.url ?: "",
                        )
                    }
                    VideoPlayerComposable(
                        modifier = itemModifier.animateContentSize(
                            alignment = Alignment.Center
                        ),
                        playerHost = playerHost,
                        playerConfig = config
                    )
                }else {
                    Box(
                        modifier = itemModifier,
                        contentAlignment = Alignment.Center
                    ) {
                        if(enabled) {
                            Icon(
                                modifier = Modifier
                                    .zIndex(1f)
                                    .size(36.dp),
                                imageVector = Icons.Outlined.PlayArrow,
                                tint = Colors.GrayLight,
                                contentDescription = stringResource(Res.string.accessibility_play)
                            )
                        }

                        VideoPreviewComposable(
                            url = localMedia?.path ?: finalMedia?.path ?: finalMedia?.url ?: "",
                            loadingIndicatorColor = LocalTheme.current.colors.secondary,
                            frameCount = 1,
                            contentScale = contentScale
                        )
                    }
                }
            }
            MediaType.AUDIO -> {
                AudioMessageBubble(
                    modifier = Modifier.zIndex(1f),
                    url = localMedia?.path ?: finalMedia?.path ?: finalMedia?.url ?: "",
                    tintColor = tintColor
                )
            }
            else -> {
                Column(
                    modifier = itemModifier.width(IntrinsicSize.Min),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    val iconModifier = modifier.fillMaxWidth(.3f).aspectRatio(1f)

                    when(mediaType) {
                        MediaType.PDF -> {
                            Image(
                                modifier = iconModifier,
                                painter = painterResource(Res.drawable.logo_pdf),
                                contentDescription = stringResource(Res.string.accessibility_message_pdf)
                            )
                        }
                        MediaType.TEXT -> {
                            Icon(
                                modifier = iconModifier,
                                imageVector = Icons.Outlined.Description,
                                tint = tintColor,
                                contentDescription = stringResource(Res.string.accessibility_message_text)
                            )
                        }
                        MediaType.PRESENTATION -> {
                            Image(
                                modifier = iconModifier,
                                colorFilter = ColorFilter.tint(tintColor),
                                painter = painterResource(Res.drawable.logo_powerpoint),
                                contentDescription = stringResource(Res.string.accessibility_message_presentation)
                            )
                        }
                        else -> {
                            Icon(
                                modifier = iconModifier,
                                imageVector = Icons.Outlined.FilePresent,
                                tint = tintColor,
                                contentDescription = stringResource(Res.string.accessibility_message_file)
                            )
                        }
                    }
                    Text(
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        text = "${localMedia?.name ?: finalMedia?.name ?: ""}.${localMedia?.extension ?: getExtensionFromMimeType(finalMedia?.mimetype)}",
                        style = LocalTheme.current.styles.regular.copy(
                            color = tintColor
                        )
                    )
                }
            }
        }
    }
}