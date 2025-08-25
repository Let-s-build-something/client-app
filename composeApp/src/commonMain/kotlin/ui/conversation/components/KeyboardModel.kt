package ui.conversation.components

import androidx.compose.animation.core.Animatable
import androidx.lifecycle.viewModelScope
import base.utils.orZero
import data.io.app.SettingsKeys
import data.io.social.network.conversation.EmojiData
import data.shared.SharedModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.dsl.module
import ui.conversation.components.emoji.EmojiUseCase
import ui.conversation.components.emoji.emojiModule
import ui.conversation.components.experimental.gravity.gravityModule
import ui.conversation.components.experimental.pacing.pacingModule
import ui.conversation.components.gif.GifRepository.Companion.SEARCH_SECTION
import ui.conversation.components.gif.GifRepository.Companion.TRENDING_SECTION
import ui.conversation.components.gif.GifUseCase
import ui.conversation.components.gif.gifModule

internal val keyboardModule = module {
    includes(emojiModule)
    includes(gifModule)

    // experimental
    includes(pacingModule)
    includes(gravityModule)
}

/** Controller and provider of data specific to the conversation keyboard */
open class KeyboardModel(
    private val emojiUseCase: EmojiUseCase,
    private val gifUseCase: GifUseCase,
    private val conversationId: String?,
): SharedModel() {

    val additionalBottomPadding = Animatable(0f)

    /** Last height of soft keyboard */
    val keyboardHeight = MutableStateFlow(0)

    /** Whether hint about emoji preference should be displayed */
    var showEmojiPreferenceHint = true
        set(value) {
            field = value
            viewModelScope.launch(Dispatchers.IO) {
                settings.putBoolean(SettingsKeys.KEY_SHOW_EMOJI_PREFERENCE_HINT, value)
            }
        }

    /** Preferred emojis of an individual conversation */
    val preferredEmojis = emojiUseCase.preferredEmojis.asStateFlow()

    /** List of all available emojis */
    val emojis = emojiUseCase.emojis

    /** Whether there is emoji filter */
    val areEmojisFiltered = emojiUseCase.areEmojisFiltered.asStateFlow()

    /** Paginated list of currently trending gifs */
    val trendingGifs = gifUseCase.trendingGifs

    /** Paginated list of searched gifs */
    val queriedGifs = gifUseCase.queriedGifs


    init {
        viewModelScope.launch {
            emojiUseCase.initialize(conversationId)
        }
        viewModelScope.launch(Dispatchers.IO) {
            showEmojiPreferenceHint = settings.getBooleanOrNull(SettingsKeys.KEY_SHOW_EMOJI_PREFERENCE_HINT) ?: true
            keyboardHeight.value = settings.getIntOrNull(SettingsKeys.KEY_KEYBOARD_HEIGHT).orZero()
        }
    }

    /** Takes a note about an emoji selection */
    fun noteEmojiSelection(emoji: EmojiData) {
        viewModelScope.launch {
            emojiUseCase.noteEmojiSelection(
                emoji = emoji,
                conversationId = conversationId
            )
        }
    }

    /** Saves current keyboard height */
    fun setKeyboardHeight(value: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            settings.putInt(SettingsKeys.KEY_KEYBOARD_HEIGHT, value)
            keyboardHeight.value = value
        }
    }

    /** Makes a request to retrieve trending GIFs */
    fun requestTrendingGifs() {
        viewModelScope.launch {
            gifUseCase.requestGifs(
                coroutineScope = viewModelScope,
                section = TRENDING_SECTION
            )
        }
    }

    /** Makes a request to retrieve trending GIFs */
    fun requestGifSearch(query: CharSequence) {
        viewModelScope.launch {
            if(query.isBlank()) requestTrendingGifs()
            gifUseCase.requestGifs(
                coroutineScope = viewModelScope,
                section = SEARCH_SECTION,
                query = query.toString()
            )
        }
    }

    /** Filters emojis */
    fun filterEmojis(query: CharSequence) {
        viewModelScope.launch {
            emojiUseCase.filterEmojis(query.toString())
        }
    }

    /** Updates preferred emojis */
    fun updatePreferredEmojiSet(list: List<EmojiData>) {
        viewModelScope.launch {
            emojiUseCase.updatePreferredEmojiSet(list)
        }
    }
}