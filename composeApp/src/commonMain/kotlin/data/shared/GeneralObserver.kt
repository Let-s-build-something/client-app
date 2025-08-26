package data.shared

import data.io.social.network.conversation.message.FullConversationMessage
import data.io.social.network.conversation.message.MessageReactionIO
import ui.conversation.ConversationState

sealed class GeneralObserver<D> {
    abstract fun invoke(data: D)
    abstract val key: String?

    class MessageObserver(
        override val key: String?,
        private val invocation: (FullConversationMessage) -> Unit
    ): GeneralObserver<FullConversationMessage>() {
        override fun invoke(data: FullConversationMessage) = invocation(data)
    }

    class ReactionsObserver(
        override val key: String?,
        private val invocation: (MessageReactionIO) -> Unit
    ): GeneralObserver<MessageReactionIO>() {
        override fun invoke(data: MessageReactionIO) = invocation(data)
    }

    class ConversationStateObserver(
        override val key: String?,
        private val invocation: (ConversationState) -> Unit
    ): GeneralObserver<ConversationState>() {
        override fun invoke(data: ConversationState) = invocation(data)
    }
}