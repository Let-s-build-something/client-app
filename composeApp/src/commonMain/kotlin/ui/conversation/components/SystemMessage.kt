package ui.conversation.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import augmy.composeapp.generated.resources.Res
import augmy.composeapp.generated.resources.message_room_join
import components.AvatarImage
import data.io.social.network.conversation.message.FullConversationMessage
import data.io.user.UserIO.Companion.generateUserTag
import net.folivo.trixnity.core.model.UserId
import org.jetbrains.compose.resources.stringResource

@Composable
fun SystemMessage(
    modifier: Modifier = Modifier,
    data: FullConversationMessage?
) {
    InfoBox(
        modifier = modifier,
        message = data?.data?.content ?: "",
        paddingValues = PaddingValues(
            vertical = if (data?.media.isNullOrEmpty()) 18.dp else 8.dp,
            horizontal = 12.dp
        )
    ) {
        data?.media?.forEach { media ->
            if (data.data.content?.contains(stringResource(Res.string.message_room_join)) == true) {
                AvatarImage(
                    modifier = Modifier
                        .size(42.dp)
                        .padding(horizontal = 4.dp),
                    media = media,
                    name = media.name,
                    tag = media.name?.let { UserId(it).generateUserTag() }
                )
            } else {
                MediaElement(
                    modifier = Modifier
                        .size(42.dp)
                        .padding(horizontal = 4.dp),
                    media = media
                )
            }
        }
    }
}


