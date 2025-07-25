package ui.conversation.message

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import augmy.interactive.shared.ext.detectMessageInteraction
import augmy.interactive.shared.ui.base.LocalScreenSize
import augmy.interactive.shared.ui.theme.LocalTheme
import components.AvatarImage
import data.io.social.network.conversation.EmojiData
import data.io.social.network.conversation.message.FullConversationMessage
import data.io.social.network.conversation.message.MediaIO
import io.github.vinceglb.filekit.PlatformFile
import ui.conversation.components.link.LinkPreview
import ui.conversation.components.message.MessageBubble
import ui.conversation.components.message.MessageBubbleModel
import ui.conversation.components.message.ReplyIndication
import ui.conversation.media.MediaRow

const val AUTHOR_SYSTEM = "SYSTEM"

enum class MessageType {
    CurrentUser,
    OtherUser,
    System
}

/**
 * All content relevant to a single message within a conversation
 * This means media, audio, attachments, reactions, etc.
 */
@Composable
fun LazyItemScope.ConversationMessageContent(
    data: FullConversationMessage?,
    temporaryFiles: Map<String, PlatformFile?>,
    currentUserPublicId: String?,
    highlight: String? = null,
    isPreviousMessageSameAuthor: Boolean,
    isNextMessageSameAuthor: Boolean,
    messageType: MessageType,
    isMyLastMessage: Boolean,
    model: MessageBubbleModel,
    reactingToMessageId: MutableState<String?>,
    replyToMessage: MutableState<FullConversationMessage?>,
    preferredEmojis: List<EmojiData>,
    scrollToMessage: (String?) -> Unit
) {
    val density = LocalDensity.current
    val screenSize = LocalScreenSize.current

    val mediaRowState = rememberScrollState(initial = 0)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                top = if(isPreviousMessageSameAuthor) 1.dp else LocalTheme.current.shapes.betweenItemsSpace.div(2),
                bottom = if(isNextMessageSameAuthor) 1.dp else LocalTheme.current.shapes.betweenItemsSpace.div(2)
            )
            .animateItem(),
        horizontalArrangement = when (messageType) {
            MessageType.CurrentUser -> Arrangement.End
            else -> Arrangement.Start
        },
        verticalAlignment = Alignment.Bottom
    ) {
        val profileImageSize = with(density) { 38.sp.toDp() }
        val hasAttachment = rememberSaveable(data?.id) {
            mutableStateOf(false)
        }

        if(messageType == MessageType.OtherUser) {
            if(!isNextMessageSameAuthor) {
                AvatarImage(
                    modifier = Modifier
                        .padding(
                            start = 12.dp,
                            end = 10.dp,
                            // offset if there are reactions (because those offset the message content)
                            bottom = if(!data?.reactions.isNullOrEmpty()) {
                                with(density) { LocalTheme.current.styles.category.fontSize.toDp() + 6.dp }
                            }else 0.dp
                        )
                        .zIndex(4f)
                        .size(profileImageSize),
                    media = MediaIO(url = data?.author?.avatarUrl),
                    tag = data?.author?.tag,
                    name = data?.author?.displayName
                )
            }else if(isPreviousMessageSameAuthor || isNextMessageSameAuthor) {
                Spacer(Modifier.width(profileImageSize + 22.dp))
            }
        }

        MessageBubble(
            data = data,
            isReacting = reactingToMessageId.value == data?.id,
            currentUserPublicId = currentUserPublicId ?: "",
            hasPrevious = isPreviousMessageSameAuthor,
            hasNext = isNextMessageSameAuthor,
            hasAttachment = hasAttachment.value,
            isReplying = replyToMessage.value?.id == data?.id,
            isMyLastMessage = isMyLastMessage,
            preferredEmojis = preferredEmojis,
            model = model,
            highlight = highlight,
            additionalContent = { onDragChange, onDrag, messageContent ->
                val rememberedHeight = rememberSaveable(data?.id) {
                    mutableStateOf(0f)
                }
                val shape = if(data?.data?.content.isNullOrBlank()) {
                    LocalTheme.current.shapes.rectangularActionShape
                }else RoundedCornerShape(
                    topStart = LocalTheme.current.shapes.rectangularActionRadius,
                    topEnd = LocalTheme.current.shapes.rectangularActionRadius
                )
                val heightModifier = Modifier
                    .heightIn(max = (screenSize.height.coerceAtMost(screenSize.width) * .7f).dp)
                    .clip(shape)

                val horizontalAlignment = when(messageType) {
                    MessageType.CurrentUser -> Alignment.End
                    MessageType.OtherUser -> Alignment.Start
                    else -> Alignment.CenterHorizontally
                }
                Column(
                    modifier = (if(rememberedHeight.value > 0f) Modifier.heightIn(min = rememberedHeight.value.dp) else Modifier)
                        .wrapContentHeight()
                        .onSizeChanged {
                            if(it.height != 0) {
                                with(density) {
                                    rememberedHeight.value = it.height.toDp().value
                                }
                            }
                        }
                        .align(horizontalAlignment),
                    horizontalAlignment = horizontalAlignment
                ) {
                    data?.anchorMessage?.let { anchorData ->
                        ReplyIndication(
                            modifier = Modifier
                                .wrapContentWidth()
                                .padding(start = 12.dp),
                            data = FullConversationMessage(anchorData),
                            onClick = { scrollToMessage(anchorData.id) },
                            isCurrentUser = anchorData.authorPublicId == currentUserPublicId
                        )
                    }

                    MediaRow(
                        modifier = heightModifier,
                        data = data,
                        media = data?.media.orEmpty(),
                        scrollState = mediaRowState,
                        temporaryFiles = temporaryFiles,
                        isCurrentUser = messageType == MessageType.CurrentUser,
                        onDragChange = onDragChange,
                        onDrag = onDrag,
                        onLongPress = {
                            reactingToMessageId.value = data?.id
                        }
                    )

                    if (data?.data?.showPreview == true && data.data.content?.isNotBlank() == true) {
                        messageContent.getLinkAnnotations(0, messageContent.length)
                            .firstOrNull()?.let { link ->
                                LinkPreview(
                                    modifier = Modifier
                                        .pointerInput(data.id) {
                                            detectMessageInteraction(
                                                onTap = {
                                                    link.item.linkInteractionListener?.onClick(link.item)
                                                },
                                                onDragChange = onDragChange,
                                                onDrag = onDrag
                                            )
                                        },
                                    shape = shape,
                                    url = messageContent.subSequence(link.start, link.end)
                                        .toString(),
                                    alignment = Alignment.CenterHorizontally,
                                    onLayout = { isVisible ->
                                        hasAttachment.value = isVisible
                                    }
                                )
                            }
                    }
                }
            }
        )

    }
}
