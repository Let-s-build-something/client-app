package components.network

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import augmy.composeapp.generated.resources.Res
import augmy.composeapp.generated.resources.accessibility_cancel
import augmy.composeapp.generated.resources.button_dismiss
import augmy.composeapp.generated.resources.button_yes
import augmy.interactive.shared.ext.brandShimmerEffect
import augmy.interactive.shared.ext.scalingClickable
import augmy.interactive.shared.ui.components.MinimalisticIcon
import augmy.interactive.shared.ui.theme.LocalTheme
import augmy.interactive.shared.ui.theme.SharedColors
import components.LoadingIndicator
import components.AvatarImage
import data.io.base.BaseResponse
import data.io.social.network.request.CirclingRequest
import org.jetbrains.compose.resources.stringResource

@Composable
fun CircleRequestRow(
    modifier: Modifier = Modifier,
    data: CirclingRequest?,
    onResponse: (accept: Boolean) -> Unit,
    response: BaseResponse<*>?
) {
    Crossfade(targetState = data != null) { isData ->
        if(isData && data != null) {
            ContentLayout(
                modifier = modifier,
                data = data,
                onResponse = onResponse,
                response = response
            )
        }else {
            ShimmerLayout(modifier = modifier)
        }
    }
}

@Composable
private fun ContentLayout(
    modifier: Modifier = Modifier,
    data: CirclingRequest,
    onResponse: (accept: Boolean) -> Unit,
    response: BaseResponse<*>?
) {
    Row(
        modifier = modifier.padding(vertical = 8.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AvatarImage(
                modifier = Modifier.size(48.dp),
                media = data.avatar,
                tag = data.tag,
                name = data.displayName
            )
            Text(
                text = data.displayName ?: "",
                style = LocalTheme.current.styles.category,
                overflow = TextOverflow.Ellipsis
            )
        }
        NetworkRequestActions(
            modifier = Modifier.weight(1f),
            response = response,
            key = data.publicId,
            onResponse = onResponse
        )
    }
}

@Composable
fun NetworkRequestActions(
    modifier: Modifier = Modifier,
    key: Any?,
    response: BaseResponse<*>?,
    onResponse: (accept: Boolean) -> Unit
) {
    val confirmReject = rememberSaveable(key) {
        mutableStateOf(false)
    }
    val confirmAccept = rememberSaveable(key) {
        mutableStateOf(false)
    }

    Crossfade(
        modifier = modifier,
        targetState = response != null
    ) { isLoading ->
        Box(
            modifier = Modifier
                .animateContentSize()
                .fillMaxWidth()
        ) {
            if(isLoading) {
                LoadingIndicator(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    response = response
                )
            }else {
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .height(IntrinsicSize.Min)
                        .background(
                            color = when {
                                confirmAccept.value -> LocalTheme.current.colors.brandMain.copy(.5f)
                                confirmReject.value -> SharedColors.RED_ERROR_50
                                else -> Color.Transparent
                            },
                            shape = LocalTheme.current.shapes.componentShape
                        )
                        .border(
                            width = 1.dp,
                            color = when {
                                confirmAccept.value -> LocalTheme.current.colors.brandMain
                                confirmReject.value -> SharedColors.RED_ERROR
                                else -> Color.Transparent
                            },
                            shape = LocalTheme.current.shapes.componentShape
                        )
                        .padding(4.dp)
                        .animateContentSize(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Crossfade(
                        targetState = when {
                            confirmReject.value -> ActionMode.REJECT
                            confirmAccept.value -> ActionMode.ACCEPT
                            else -> ActionMode.DEFAULT
                        }
                    ) { actionMode ->
                        Row(
                            modifier = Modifier.fillMaxHeight(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            when(actionMode) {
                                ActionMode.DEFAULT -> {
                                    MinimalisticIcon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = stringResource(Res.string.button_dismiss),
                                        tint = SharedColors.RED_ERROR,
                                        onTap = {
                                            if(confirmReject.value) {
                                                confirmReject.value = false
                                                onResponse(false)
                                            }else confirmReject.value = true
                                        }
                                    )
                                    MinimalisticIcon(
                                        imageVector = Icons.Outlined.Check,
                                        contentDescription = stringResource(Res.string.button_yes),
                                        tint = LocalTheme.current.colors.brandMain,
                                        onTap = {
                                            if(confirmAccept.value) {
                                                confirmAccept.value = false
                                                onResponse(true)
                                            }else confirmAccept.value = true
                                        }
                                    )
                                }
                                ActionMode.REJECT -> {
                                    Text(
                                        modifier = Modifier
                                            .scalingClickable {
                                                confirmReject.value = false
                                            }
                                            .padding(4.dp),
                                        text = stringResource(Res.string.accessibility_cancel),
                                        style = LocalTheme.current.styles.regular
                                    )
                                    MinimalisticIcon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = stringResource(Res.string.button_dismiss),
                                        tint = SharedColors.RED_ERROR,
                                        onTap = {
                                            if(confirmReject.value) {
                                                confirmReject.value = false
                                                onResponse(false)
                                            }else confirmReject.value = true
                                        }
                                    )
                                }
                                ActionMode.ACCEPT -> {
                                    MinimalisticIcon(
                                        imageVector = Icons.Outlined.Check,
                                        contentDescription = stringResource(Res.string.button_yes),
                                        tint = LocalTheme.current.colors.brandMain,
                                        onTap = {
                                            if(confirmAccept.value) {
                                                confirmAccept.value = false
                                                onResponse(true)
                                            }else confirmAccept.value = true
                                        }
                                    )
                                    Text(
                                        modifier = Modifier
                                            .scalingClickable {
                                                confirmAccept.value = false
                                            }
                                            .padding(4.dp),
                                        text = stringResource(Res.string.accessibility_cancel),
                                        style = LocalTheme.current.styles.regular
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

private enum class ActionMode {
    DEFAULT,
    REJECT,
    ACCEPT
}

@Composable
private fun ShimmerLayout(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(vertical = 8.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .brandShimmerEffect(shape = CircleShape)
            )
            Text(
                modifier = Modifier
                    .fillMaxWidth((20..55).random().toFloat()/100f)
                    .brandShimmerEffect(),
                text = "",
                style = LocalTheme.current.styles.category,
            )
        }
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .brandShimmerEffect(shape = CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .brandShimmerEffect(shape = CircleShape)
            )
        }
    }
}