package ui.conversation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Mood
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.LifecycleResumeEffect
import augmy.composeapp.generated.resources.Res
import augmy.composeapp.generated.resources.accessibility_action_emojis
import augmy.composeapp.generated.resources.accessibility_action_keyboard
import augmy.composeapp.generated.resources.accessibility_cancel
import augmy.composeapp.generated.resources.accessibility_message_action_audio
import augmy.composeapp.generated.resources.accessibility_message_action_file
import augmy.composeapp.generated.resources.accessibility_message_action_image
import augmy.composeapp.generated.resources.accessibility_message_more_options
import augmy.composeapp.generated.resources.account_picture_pick_title
import augmy.composeapp.generated.resources.conversation_attached
import augmy.composeapp.generated.resources.file_too_large
import augmy.interactive.shared.ext.contentReceiver
import augmy.interactive.shared.ext.draggable
import augmy.interactive.shared.ext.scalingClickable
import augmy.interactive.shared.ui.base.CustomSnackbarVisuals
import augmy.interactive.shared.ui.base.LocalDeviceType
import augmy.interactive.shared.ui.base.LocalNavController
import augmy.interactive.shared.ui.base.LocalScreenSize
import augmy.interactive.shared.ui.base.LocalSnackbarHost
import augmy.interactive.shared.ui.base.MaxModalWidthDp
import augmy.interactive.shared.ui.base.OnBackHandler
import augmy.interactive.shared.ui.base.PlatformType
import augmy.interactive.shared.ui.base.currentPlatform
import augmy.interactive.shared.ui.components.MinimalisticIcon
import augmy.interactive.shared.ui.components.input.CustomTextField
import augmy.interactive.shared.ui.components.input.DELAY_BETWEEN_TYPING_SHORT
import augmy.interactive.shared.ui.theme.LocalTheme
import base.navigation.NavigationArguments
import base.navigation.NavigationNode
import base.utils.LinkUtils
import base.utils.MediaType
import base.utils.getUrlExtension
import base.utils.maxMultiLineHeight
import base.utils.orZero
import components.AvatarImage
import data.io.base.BaseResponse
import data.io.social.network.conversation.giphy.GifAsset
import data.io.social.network.conversation.message.FullConversationMessage
import data.io.social.network.conversation.message.MediaIO
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.size
import korlibs.io.net.MimeType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import ui.conversation.ConversationModel
import ui.conversation.ConversationRepository.Companion.REGEX_HTML_MENTION
import ui.conversation.components.audio.PanelMicrophone
import ui.conversation.components.gif.GifImage
import ui.conversation.components.link.LinkPreview
import ui.conversation.components.message.MessageMediaPanel
import ui.conversation.components.message.ReplyIndication

private const val MENTION_REGEX = """@[^.]*$"""

/** Horizontal panel for sending and managing a message, and attaching media to it */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun BoxScope.SendMessagePanel(
    modifier: Modifier = Modifier,
    keyboardMode: MutableState<Int>,
    overrideAnchorMessage: FullConversationMessage? = null,
    replyToMessage: MutableState<FullConversationMessage?>,
    scrollToMessage: (FullConversationMessage) -> Unit,
    model: ConversationModel
) {
    val screenSize = LocalScreenSize.current
    val density = LocalDensity.current
    val navController = LocalNavController.current
    val snackbarHost = LocalSnackbarHost.current
    val isDesktop = LocalDeviceType.current == WindowWidthSizeClass.Expanded || currentPlatform == PlatformType.Jvm
    val spacing = LocalTheme.current.shapes.betweenItemsSpace / 2
    val imeHeightPadding = WindowInsets.ime.getBottom(density)
    val keyboardController  = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()
    val cancellableScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val isDefaultMode = keyboardMode.value == ConversationKeyboardMode.Default.ordinal

    val conversation = model.conversation.collectAsState(initial = null)
    val keyboardHeight = model.keyboardHeight.collectAsState()
    val savedMessage = model.savedMessage.collectAsState()
    val repositoryConfig = model.repositoryConfig.collectAsState()
    val messageState = remember(savedMessage.value) {
        TextFieldState(
            initialText = savedMessage.value ?: "",
            initialSelection = TextRange(savedMessage.value?.length.orZero())
        )
    }
    val timingSensor = model.timingSensor.collectAsState()
    val missingKeyboardHeight = remember { keyboardHeight.value < 2 }
    val mediaAttached = remember {
        mutableStateListOf<PlatformFile>()
    }
    val urlsAttached = remember {
        mutableStateListOf<String>()
    }
    val showPreview = remember {
        mutableStateOf(true)
    }
    val gifAttached = remember {
        mutableStateOf<GifAsset?>(null)
    }
    val showMoreOptions = rememberSaveable {
        mutableStateOf(messageState.text.isBlank())
    }
    val actionYCoordinate = rememberSaveable {
        mutableStateOf(-1f)
    }
    val typedUrl = remember {
        mutableStateOf<String?>(null)
    }

    val isContentEmpty = messageState.text.isBlank()
            && mediaAttached.isEmpty()
            && urlsAttached.isEmpty()
            && gifAttached.value == null

    val bottomPadding = animateFloatAsState(
        targetValue = if (imeHeightPadding >= keyboardHeight.value.div(2) || !isDefaultMode) {
            LocalTheme.current.shapes.betweenItemsSpace.value
        } else {
            with(density) { WindowInsets.navigationBars.getBottom(density).toDp().value }.coerceAtLeast(
                LocalTheme.current.shapes.betweenItemsSpace.value
            )
        },
        label = "bottomPadding",
        animationSpec = tween(durationMillis = 20, easing = LinearEasing)
    )
    val launcherImageVideo = rememberFilePickerLauncher(
        type = FileKitType.ImageAndVideo,
        mode = FileKitMode.Multiple(maxItems = MAX_ITEMS_SELECTED),
        title = stringResource(Res.string.account_picture_pick_title)
    ) { files ->
        if(!files.isNullOrEmpty()) {
            coroutineScope.launch(Dispatchers.Default) {
                mediaAttached.addAll(
                    index = 0,
                    files.filter { newFile ->
                        mediaAttached.none { it.name == newFile.name }
                                && newFile.size() < (repositoryConfig.value?.maxUploadSize.orZero())
                    }
                )
                if(files.any { it.size() > (repositoryConfig.value?.maxUploadSize.orZero()) }) {
                    snackbarHost?.showSnackbar(
                        CustomSnackbarVisuals(
                            message = getString(
                                Res.string.file_too_large,
                                repositoryConfig.value?.maxUploadSize?.div(1_000_000).toString()
                            ),
                            isError = true
                        )
                    )
                }
            }
        }
    }
    val launcherFile = rememberFilePickerLauncher(
        type = FileKitType.File(),
        mode = FileKitMode.Multiple(maxItems = MAX_ITEMS_SELECTED),
        title = stringResource(Res.string.account_picture_pick_title)
    ) { files ->
        if(!files.isNullOrEmpty()) {
            coroutineScope.launch(Dispatchers.Default) {
                mediaAttached.addAll(
                    index = 0,
                    files.filter { newFile ->
                        mediaAttached.none { it.name == newFile.name }
                                && newFile.size() < (repositoryConfig.value?.maxUploadSize.orZero())
                    }
                )
                if(files.any { it.size() < (repositoryConfig.value?.maxUploadSize.orZero()) }) {
                    snackbarHost?.showSnackbar(
                        getString(
                            Res.string.file_too_large,
                            repositoryConfig.value?.maxUploadSize?.div(1_000_000).toString()
                        )
                    )
                }
            }
        }
    }

    val sendMessage = {
        if ((messageState.text.toString().isNotBlank()
            || mediaAttached.isNotEmpty()
            || gifAttached.value != null
            || urlsAttached.isNotEmpty())
                && model.joinResponse.value !is BaseResponse.Loading
        ) {
            model.sendMessage(
                content = messageState.text.toString(),
                mediaFiles = mediaAttached.toList(),
                anchorMessage = replyToMessage.value?.data ?: overrideAnchorMessage?.data,
                gifAsset = gifAttached.value,
                mediaUrls = urlsAttached,
                showPreview = showPreview.value,
                timings = timingSensor.value.timings.toList(),
                gravityValues = model.gravityValues.value
            )
            model.cache(null)
            timingSensor.value.flush()
            typedUrl.value = null
            model.stopTypingServices()
            mediaAttached.clear()
            keyboardMode.value = ConversationKeyboardMode.Default.ordinal
            messageState.clearText()
            replyToMessage.value = null
            gifAttached.value = null
            showPreview.value = true

            navController?.previousBackStackEntry
                ?.savedStateHandle
                ?.apply {
                    set(NavigationArguments.CONVERSATION_NEW_MESSAGE, true)
                }
        }
    }


    LifecycleResumeEffect(model) {
        // temporary init to showcase the behaviour, remove for implementation
        model.initPacing(widthPx = with(density) { screenSize.width.dp.toPx() })

        onPauseOrDispose {
            model.stopTypingServices()
            model.cache(content = messageState.text.toString())
        }
    }
    LaunchedEffect(savedMessage.value) {
        focusRequester.requestFocus()

        if (missingKeyboardHeight || !savedMessage.value.isNullOrBlank()) keyboardController?.show()
        else keyboardController?.hide()
    }

    if(missingKeyboardHeight) {
        LaunchedEffect(imeHeightPadding) {
            if(missingKeyboardHeight) {
                imeHeightPadding.let { imeHeight ->
                    if(imeHeight > keyboardHeight.value) {
                        model.setKeyboardHeight(imeHeight)
                    }
                }
            }
        }
    }

    LaunchedEffect(keyboardMode.value) {
        when(keyboardMode.value) {
            ConversationKeyboardMode.Default.ordinal -> model.additionalBottomPadding.animateTo(0f)
            else -> keyboardController?.hide()
        }
    }

    LaunchedEffect(messageState.text) {
        if(messageState.text.isNotBlank()) {
            model.startTypingServices()
            showMoreOptions.value = false
        }else model.stopTypingServices()

        timingSensor.value.onNewText(messageState.text)?.let { result ->
            model.onKeyPressed(
                char = result.newChar,
                timingMs = result.timing
            )
        }
        if(showPreview.value) {
            cancellableScope.coroutineContext.cancelChildren()
            cancellableScope.launch(Dispatchers.Default) {
                delay(DELAY_BETWEEN_TYPING_SHORT)
                model.updateTypingStatus(content = messageState.text)
                typedUrl.value = LinkUtils.urlRegex.findAll(messageState.text).firstOrNull()?.value
            }
        }
    }

    LaunchedEffect(messageState.selection, messageState.text) {
        withContext(Dispatchers.Default) {
            val untilSelection = messageState.text.subSequence(0, messageState.selection.end)
            val matches = MENTION_REGEX.toRegex().findAll(untilSelection)

            model.recommendMentions(matches.firstOrNull()?.value)
        }
    }

    OnBackHandler(enabled = imeHeightPadding > 0 || !isDefaultMode) {
        when {
            imeHeightPadding > 0 -> keyboardController?.hide()
            !isDefaultMode -> keyboardMode.value = ConversationKeyboardMode.Default.ordinal
        }
    }


    Column(
        modifier = modifier.animateContentSize(
            alignment = Alignment.BottomCenter
        ),
        verticalArrangement = Arrangement.Top
    ) {
        gifAttached.value?.let { gifAsset ->
            Box {
                GifImage(
                    modifier = Modifier
                        .zIndex(1f)
                        .scalingClickable(scaleInto = .95f) {
                            navController?.navigate(
                                NavigationNode.MediaDetail(
                                    media = listOf(MediaIO(
                                        url = gifAsset.original ?: "",
                                        mimetype = "image/gif"
                                    ))
                                )
                            )
                        }
                        .height(MEDIA_MAX_HEIGHT_DP.dp)
                        .wrapContentWidth()
                        .clip(RoundedCornerShape(6.dp)),
                    data = gifAsset.fixedWidthSmall ?: "",
                    contentDescription = gifAsset.description,
                    contentScale = ContentScale.FillHeight
                )
                MinimalisticIcon(
                    modifier = Modifier
                        .align(Alignment.TopEnd),
                    imageVector = Icons.Outlined.Close,
                    tint = LocalTheme.current.colors.secondary,
                    onTap = {
                        gifAttached.value = null
                    }
                )
            }
        }

        Spacer(Modifier.height(LocalTheme.current.shapes.betweenItemsSpace))

        replyToMessage.value?.takeIf { it.id != overrideAnchorMessage?.id } ?.let { originalMessage ->
            ReplyIndication(
                modifier = Modifier
                    .padding(start = 12.dp)
                    .widthIn(max = MaxModalWidthDp.dp)
                    .fillMaxWidth(),
                data = originalMessage,
                onClick = {
                    scrollToMessage(originalMessage)
                },
                onRemoveRequest = { replyToMessage.value = null },
                isCurrentUser = originalMessage.data.authorPublicId == model.currentUser.value?.userId,
                removable = true
            )
        }

        // media preview
        if(mediaAttached.isNotEmpty() || urlsAttached.isNotEmpty()) {
            val mediaListState = rememberScrollState(
                initial = mediaAttached.lastIndex + urlsAttached.lastIndex
            )

            Column(modifier = Modifier.padding(bottom = bottomPadding.value.dp)) {
                Text(
                    modifier = Modifier.padding(
                        bottom = 8.dp,
                        start = LocalTheme.current.shapes.betweenItemsSpace * 2
                    ),
                    text = stringResource(Res.string.conversation_attached),
                    style = LocalTheme.current.styles.regular
                )

                Row(
                    modifier = Modifier
                        .padding(bottom = 4.dp)
                        .fillMaxWidth()
                        .heightIn(max = MEDIA_MAX_HEIGHT_DP.dp)
                        .horizontalScroll(state = mediaListState)
                        .draggable(state = mediaListState, orientation = Orientation.Horizontal),
                    horizontalArrangement = Arrangement.spacedBy(spacing)
                ) {
                    Spacer(Modifier)
                    (mediaAttached + arrayOfNulls(urlsAttached.size)).forEachIndexed { index, media ->
                        Box(
                            modifier = Modifier.fillMaxHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            MinimalisticIcon(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .zIndex(4f)
                                    .background(
                                        color = LocalTheme.current.colors.backgroundDark,
                                        shape = LocalTheme.current.shapes.componentShape
                                    ),
                                tint = LocalTheme.current.colors.secondary,
                                imageVector = Icons.Outlined.Close,
                                onTap = {
                                    if(index < mediaAttached.size) {
                                        mediaAttached.removeAt(index)
                                    }
                                }
                            )

                            val remoteMedia = urlsAttached.getOrNull(index - mediaAttached.lastIndex)
                            MediaElement(
                                modifier = Modifier
                                    .requiredHeight(MEDIA_MAX_HEIGHT_DP.dp)
                                    .wrapContentWidth()
                                    .clip(LocalTheme.current.shapes.rectangularActionShape),
                                localMedia = media,
                                media = if(remoteMedia != null) {
                                    MediaIO(
                                        url = remoteMedia,
                                        mimetype = MimeType.getByExtension(getUrlExtension(remoteMedia)).mime
                                    )
                                }else null,
                                contentScale = ContentScale.FillHeight
                            )
                        }
                    }
                    Spacer(Modifier)
                }
            }
        }

        typedUrl.value?.let { url ->
            val shape = RoundedCornerShape(
                topStart = LocalTheme.current.shapes.componentCornerRadius,
                topEnd = LocalTheme.current.shapes.componentCornerRadius
            )

            Box(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .background(
                        color = LocalTheme.current.colors.backgroundLight,
                        shape = shape
                    )
                    .padding(6.dp)
                    .animateContentSize(
                        alignment = Alignment.BottomCenter
                    )
            ) {
                MinimalisticIcon(
                    modifier = Modifier
                        .zIndex(1f)
                        .align(Alignment.TopEnd),
                    imageVector = Icons.Outlined.Close,
                    tint = LocalTheme.current.colors.secondary,
                    onTap = {
                        showPreview.value = false
                        typedUrl.value = null
                    }
                )
                Column {
                    LinkPreview(
                        shape = shape,
                        url = url,
                        textBackground = Color.Transparent,
                        imageSize = IntSize(height = 140, width = 0),
                        alignment = Alignment.Start
                    )
                }
            }
        }

        // message input and emojis if on desktop
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 10.dp)
                .padding(bottom = bottomPadding.value.dp)
                .then(if(isDefaultMode) Modifier.imePadding() else Modifier),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CustomTextField(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp, end = spacing)
                    .onGloballyPositioned {
                        actionYCoordinate.value = it.positionOnScreen().y
                    },
                fieldModifier = Modifier
                    .onKeyEvent { keyEvent ->
                        if(keyEvent.key == Key.Enter) {
                            sendMessage()
                            true
                        }else false
                    }
                    .contentReceiver { uri ->
                        // TODO untested
                        when(MediaType.fromMimeType(MimeType.getByExtension(getUrlExtension(uri)).mime)) {
                            MediaType.GIF -> gifAttached.value = GifAsset(singleUrl = uri)
                            else -> urlsAttached.add(uri)
                        }
                    }
                    .onFocusChanged {
                        if(it.isFocused && messageState.text.isNotBlank()) {
                            model.updateTypingStatus(content = messageState.text)
                            model.startTypingServices()
                        }else model.stopTypingServices()
                    }
                    .fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Send,
                    showKeyboardOnFocus = false // can't be closed without clearing focus otherwise
                ),
                additionalContent = if (conversation.value?.data?.summary?.isDirect == false) {
                    {
                        MentionRecommendationsBox(model, messageState)
                    }
                } else null,
                outputTransformation = object: OutputTransformation {
                    override fun TextFieldBuffer.transformOutput() {
                        val mentionRegex = REGEX_HTML_MENTION.toRegex()

                        // Start from the end to preserve correct indexing while replacing
                        mentionRegex.findAll(originalText).toList().asReversed().forEach { match ->
                            val start = match.range.first
                            val end = match.range.last + 1
                            val label = match.groupValues[2]

                            // Replace the matched tag with a readable label
                            replace(start, end, "@[$label]")
                        }
                    }
                },
                focusRequester = focusRequester,
                state = messageState,
                onKeyboardAction = {
                    sendMessage()
                },
                trailingIcon = {
                    Crossfade(targetState = !isDefaultMode) { isMedia ->
                        Image(
                            modifier = Modifier.scalingClickable {
                                if(isMedia) {
                                    coroutineScope.launch {
                                        model.additionalBottomPadding.animateTo(0f)
                                    }
                                    focusRequester.requestFocus()
                                    keyboardController?.show()
                                    if(isDesktop) keyboardMode.value = ConversationKeyboardMode.Default.ordinal
                                }else {
                                    // hack to not close the emoji picker right away
                                    if(keyboardHeight.value > 99) {
                                        model.setKeyboardHeight(keyboardHeight.value + 1)
                                        coroutineScope.launch {
                                            delay(200)
                                            model.setKeyboardHeight(keyboardHeight.value - 1)
                                        }
                                    }else model.setKeyboardHeight(screenSize.height / 3)

                                    keyboardController?.hide()
                                    keyboardMode.value = ConversationKeyboardMode.Emoji.ordinal
                                }
                            },
                            imageVector = if(isMedia) Icons.Outlined.Keyboard else Icons.Outlined.Mood,
                            contentDescription = stringResource(
                                if(isMedia) {
                                    Res.string.accessibility_action_keyboard
                                }else Res.string.accessibility_action_emojis
                            ),
                            colorFilter = ColorFilter.tint(LocalTheme.current.colors.secondary)
                        )
                    }
                },
                lineLimits = if(messageState.text.length < 100) {
                    TextFieldLineLimits.SingleLine
                }else TextFieldLineLimits.MultiLine(maxHeightInLines = maxMultiLineHeight),
                shape = LocalTheme.current.shapes.componentShape
            )

            val showMoreIconRotation = animateFloatAsState(
                label = "showMoreIconRotation",
                targetValue = if (showMoreOptions.value) 0f else 135f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )

            Row(
                modifier = Modifier
                    .padding(start = 6.dp)
                    .background(
                        color = LocalTheme.current.colors.backgroundDark,
                        shape = LocalTheme.current.shapes.rectangularActionShape
                    )
                    .padding(end = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    modifier = Modifier
                        .scalingClickable {
                            showMoreOptions.value = !showMoreOptions.value
                        }
                        .size(38.dp)
                        .padding(6.dp)
                        .rotate(showMoreIconRotation.value),
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(
                        if(showMoreOptions.value) Res.string.accessibility_cancel
                        else Res.string.accessibility_message_more_options
                    ),
                    tint = LocalTheme.current.colors.secondary
                )

                AnimatedVisibility(showMoreOptions.value) {
                    Row(
                        modifier = Modifier.padding(start = spacing),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing)
                    ) {
                        Icon(
                            modifier = Modifier
                                .scalingClickable {
                                    launcherFile.launch()
                                }
                                .size(38.dp)
                                .background(
                                    color = LocalTheme.current.colors.brandMainDark,
                                    shape = LocalTheme.current.shapes.componentShape
                                )
                                .padding(6.dp),
                            imageVector = Icons.Outlined.AttachFile,
                            contentDescription = stringResource(Res.string.accessibility_message_action_file),
                            tint = Color.White
                        )
                        Icon(
                            modifier = Modifier
                                .scalingClickable {
                                    launcherImageVideo.launch()
                                }
                                .size(38.dp)
                                .background(
                                    color = LocalTheme.current.colors.brandMainDark,
                                    shape = LocalTheme.current.shapes.componentShape
                                )
                                .padding(6.dp),
                            imageVector = Icons.Outlined.Image,
                            contentDescription = stringResource(Res.string.accessibility_message_action_image),
                            tint = Color.White
                        )
                    }
                }

                // space for the microphone action
                Crossfade(targetState = isContentEmpty) { isBlank ->
                    Icon(
                        modifier = Modifier
                            .scalingClickable(enabled = !isBlank) {
                                if(!isBlank) sendMessage()
                            }
                            .padding(start = spacing)
                            .size(38.dp)
                            .background(
                                color = LocalTheme.current.colors.brandMainDark,
                                shape = LocalTheme.current.shapes.componentShape
                            )
                            .padding(6.dp),
                        imageVector = if(isBlank) Icons.Outlined.Mic else Icons.AutoMirrored.Outlined.Send,
                        contentDescription = stringResource(
                            if(isBlank) Res.string.accessibility_message_action_audio
                            else Res.string.accessibility_message_action_image
                        ),
                        tint = Color.White
                    )
                }
            }
        }

        // Emojis + GIFs + Stickers
        if(keyboardMode.value != ConversationKeyboardMode.Default.ordinal) {
            MessageMediaPanel(
                mode = keyboardMode,
                viewModel = model,
                showBackSpace = messageState.text.isNotBlank(),
                onGifSelected = { gif ->
                    gifAttached.value = gif
                },
                onEmojiSelected = { emoji ->
                    showMoreOptions.value = false

                    messageState.edit {
                        replace(
                            text = emoji,
                            start = messageState.selection.start,
                            end = messageState.selection.end
                        )
                        selection = TextRange(
                            messageState.selection.start + emoji.length
                        )
                    }
                },
                onBackSpace = {
                    showMoreOptions.value = false

                    val isRangeRemoval = messageState.selection.start != messageState.selection.end

                    if(isRangeRemoval) {
                        messageState.setTextAndPlaceCursorAtEnd(
                            buildString {
                                // before selection
                                append(
                                    messageState.text.subSequence(
                                        startIndex = 0,
                                        endIndex = messageState.selection.start
                                    ).toString()
                                )
                                // after selection
                                append(
                                    messageState.text.subSequence(
                                        startIndex = messageState.selection.end,
                                        endIndex = messageState.text.length
                                    )
                                )
                            }
                        )
                    }else {
                        val modifiedPrefix = removeUnicodeCharacter(
                            text = messageState.text.subSequence(
                                0, messageState.selection.start
                            ).toString(),
                            index = messageState.selection.start
                        )

                        val newContent = buildString {
                            // before selection
                            append(modifiedPrefix)
                            // after selection
                            append(
                                messageState.text.subSequence(
                                    messageState.selection.end,
                                    messageState.text.length
                                )
                            )
                        }
                        messageState.setTextAndPlaceCursorAtEnd(newContent)
                    }
                },
                onDismissRequest = {
                    keyboardMode.value = ConversationKeyboardMode.Default.ordinal
                }
            )
        }
    }

    // Has to be outside of message panel in order to lay over everything else
    if(isContentEmpty) {
        PanelMicrophone(
            modifier = Modifier
                .offset(y = - (screenSize.height.dp - with(density) { actionYCoordinate.value.toDp() } - 44.dp))
                .align(Alignment.BottomEnd),
            onSaveRequest = { byteArray ->
                model.sendAudioMessage(byteArray)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun MentionRecommendationsBox(
    model: ConversationModel,
    messageState: TextFieldState
) {
    val recommendations = model.mentionRecommendations.collectAsState()

    LaunchedEffect(recommendations.value) {
        if (recommendations.value?.isEmpty() == true) {
            model.recommendMentions(null)
        }
    }

    if (recommendations.value?.isEmpty() == false) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp, horizontal = 8.dp)
        ) {
            items(
                items = recommendations.value.orEmpty(),
                key = { it.id }
            ) { recommendation ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .scalingClickable(scaleInto = .95f) {
                            val untilSelection = messageState.text.subSequence(0, messageState.selection.end)

                            val start = untilSelection.indexOfLast { it == '@' }
                            messageState.edit {
                                val toReplace = "<a href=\"https://matrix.to/#/" +
                                        "${recommendation.userId}\">${recommendation.displayName ?: recommendation.userId}</a>"

                                replace(
                                    start,
                                    untilSelection.length,
                                    toReplace
                                )
                                placeCursorAfterCharAt(start + toReplace.length - 1)
                            }
                            model.recommendMentions(null)
                        }
                        .padding(vertical = 4.dp, horizontal = 8.dp)
                        .animateItem(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val name = recommendation.displayName ?: recommendation.userId

                    recommendation.avatarUrl?.let { avatar ->
                        AvatarImage(
                            modifier = Modifier.size(32.dp),
                            name = name,
                            tag = recommendation.tag,
                            media = MediaIO(url = avatar)
                        )
                    }
                    Text(
                        text = name,
                        style = LocalTheme.current.styles.category.copy(
                            color = LocalTheme.current.colors.secondary
                        )
                    )
                }
            }
        }
    }
}

private fun removeUnicodeCharacter(text: String, index: Int): String {
    val prefix = text.substring(0, index)
    val suffix = REGEX_GRAPHEME.toRegex().findAll(prefix).lastOrNull()?.value ?: ""


    return prefix.removeSuffix(suffix) + text.substring(index)
}

/** Maximum amount of files, images, and videos to be selected and sent within a singular message */
private const val MAX_ITEMS_SELECTED = 20

/** Maximum height of a user attached media */
const val MEDIA_MAX_HEIGHT_DP = 250

// IOS does not support unicode special classes, must be done manually
private val REGEX_GRAPHEME_IOS = """
    (?:\p{Extended_Pictographic}(?:\u200D\p{Extended_Pictographic})*|\p{L}\p{M}*)
""".trimIndent()

/** regular expression for a singular grapheme */
val REGEX_GRAPHEME = if(currentPlatform == PlatformType.Native) REGEX_GRAPHEME_IOS else """\X"""
