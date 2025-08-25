package base.navigation

import augmy.composeapp.generated.resources.Res
import augmy.composeapp.generated.resources.screen_account_title
import augmy.composeapp.generated.resources.screen_conversation
import augmy.composeapp.generated.resources.screen_conversation_detail
import augmy.composeapp.generated.resources.screen_conversation_info
import augmy.composeapp.generated.resources.screen_conversation_search
import augmy.composeapp.generated.resources.screen_conversation_settings
import augmy.composeapp.generated.resources.screen_home
import augmy.composeapp.generated.resources.screen_login
import augmy.composeapp.generated.resources.screen_manage_roles
import augmy.composeapp.generated.resources.screen_media_detail
import augmy.composeapp.generated.resources.screen_network_management
import augmy.composeapp.generated.resources.screen_search_network
import augmy.composeapp.generated.resources.screen_search_room
import augmy.composeapp.generated.resources.screen_search_user
import augmy.composeapp.generated.resources.screen_water_please
import data.io.social.network.conversation.message.MediaIO
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.compose.resources.StringResource

/** Main holder of all navigation nodes */
sealed class NavigationNode {

    /**
     * Equivalent of route within the navigation.
     * Can be used to check whether current destination matches a specific Node.
     */
    val route
        get() = this::class.qualifiedName

    abstract val titleRes: StringResource?

    /**
     * Deeplink is the appendix of a brand link, which is shared between all client apps.
     * Null if deeplink isn't supported
     */
    abstract val deepLink: String?

    /** screen for both login and signup */
    @Serializable
    data class Login(
        val nonce: String? = null,
        val loginToken: String? = null
    ): NavigationNode() {
        @Transient override val titleRes: StringResource = Res.string.screen_login
        override val deepLink: String
            get() {
                val base = "login"
                return when {
                    nonce != null && loginToken != null -> "$base?nonce=$nonce&loginToken=$loginToken"
                    loginToken != null -> "$base?loginToken=$loginToken"
                    nonce != null -> "$base?nonce=$nonce"
                    else -> base
                }
            }
    }

    /** Easter-egg screen - just because */
    @Serializable
    data object Water: NavigationNode() {
        @Transient override val titleRes: StringResource = Res.string.screen_water_please
        override val deepLink: String? = null
    }

    @Serializable
    data class SearchUser(
        /** If true, there is no user detail and the user is just selected on tap */
        val awaitingResult: Boolean = false,
        val isInvitation: Boolean = false,
        val excludeUsers: String = ""
    ): NavigationNode() {
        @Transient override val titleRes: StringResource = Res.string.screen_search_user
        override val deepLink: String = "users/search?awaitingResult=$awaitingResult&excludeUsers=$excludeUsers&isInvitation=$isInvitation"
    }

    @Serializable
    data class ManageRoles(
        val roomId: String? = null
    ): NavigationNode() {
        @Transient override val titleRes: StringResource = Res.string.screen_manage_roles
        override val deepLink: String = "room/roles?roomId=$roomId"
    }

    @Serializable
    object SearchRoom: NavigationNode() {
        @Transient override val titleRes: StringResource = Res.string.screen_search_room
        override val deepLink: String = "room/search"
    }

    /** Conversation detail screen */
    @Serializable
    data class Conversation(
        /** unique identifier of the conversation */
        val conversationId: String? = null,

        /** identifier of the user with which the conversation can be started */
        val userId: String? = null,

        /** Name of the conversation */
        val name: String? = null,

        /** Item to which the scroll should be set to when the screen is opened */
        val scrollTo: String? = null,

        /** Specific to larger screens as the search query will be open in split screen under this conversation */
        val searchQuery: String? = null,

        val joinRule: String? = null
    ): NavigationNode() {
        @Transient override val titleRes: StringResource = Res.string.screen_conversation
        override val deepLink: String = "messages?conversation=$conversationId&name=$name&userId=$userId&scrollTo=$scrollTo&searchQuery=$searchQuery"
    }

    /** Conversation detail screen */
    @Serializable
    data class ConversationSearch(
        /** unique identifier of the conversation */
        val conversationId: String? = null,
        val searchQuery: String? = null
    ): NavigationNode() {
        @Transient override val titleRes: StringResource = Res.string.screen_conversation_search
        override val deepLink: String = "messages/search?conversation=$conversationId&searchQuery=$searchQuery"
    }

    @Serializable
    data class ConversationSettings(
        /** unique identifier of the conversation */
        val conversationId: String? = null
    ): NavigationNode() {
        @Transient override val titleRes: StringResource = Res.string.screen_conversation_settings
        override val deepLink: String = "settings?conversation=$conversationId"
    }

    /** Conversation detail screen */
    @Serializable
    data class ConversationInformation(
        /** unique identifier of the conversation */
        val conversationId: String? = null,

        /** Name of the conversation */
        val name: String? = null
    ): NavigationNode() {
        @Transient override val titleRes: StringResource = Res.string.screen_conversation_info
        override val deepLink: String = "conversation?conversation=$conversationId&name=$name"
    }

    /** dashboard screen for user information and general user-related actions */
    @Serializable
    data object AccountDashboard: NavigationNode() {
        @Transient override val titleRes: StringResource = Res.string.screen_account_title
        override val deepLink: String = "account/dashboard"
    }

    /** Screen for searching within one's network */
    @Serializable
    data object SearchNetwork: NavigationNode() {
        @Transient override val titleRes: StringResource = Res.string.screen_search_network
        override val deepLink: String = "network/search"
    }

    /** screen for managing social circle of this app, specific to the current user */
    @Serializable
    data class NetworkManagement(
        val displayName: String? = null,
        val tag: String? = null
    ): NavigationNode() {
        @Transient override val titleRes: StringResource = Res.string.screen_network_management
        override val deepLink: String = "network"
    }

    /** home screen of the whole app */
    @Serializable
    data object Home: NavigationNode() {
        @Transient override val titleRes: StringResource = Res.string.screen_home
        override val deepLink: String? = null
    }

    /** Detail screen of a single message, sort of a focus mode with replies,
     *  reactions and longer and scrollable content */
    @Serializable
    data class MessageDetail(
        val messageId: String? = null,
        val conversationId: String? = null,
        val title: String? = null
    ): NavigationNode() {
        @Transient override val titleRes: StringResource = Res.string.screen_conversation_detail
        override val deepLink: String? = null
    }

    /** Full screen media detail */
    @Serializable
    data class MediaDetail(
        @Transient
        val media: List<MediaIO> = listOf(),
        val title: String? = null,
        val subtitle: String? = null,
        val selectedIndex: Int = 0,
        private val idList: List<String> = media.map { it.id }
    ): NavigationNode() {
        @Transient override val titleRes: StringResource = Res.string.screen_media_detail
        override val deepLink: String? = null
    }

    companion object {
        val allNodes = listOf(
            Login(),
            Water,
            SearchRoom,
            Conversation(),
            ConversationSearch(),
            AccountDashboard,
            NetworkManagement(),
            ConversationSettings(),
            SearchUser(),
            ManageRoles(),
            ConversationInformation(),
            MediaDetail(),
            MessageDetail()
        )
    }
}