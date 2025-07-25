package ui.account.profile

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import augmy.composeapp.generated.resources.Res
import augmy.composeapp.generated.resources.accessibility_upload_files
import augmy.composeapp.generated.resources.accessibility_upload_url
import augmy.composeapp.generated.resources.account_picture_change_success
import augmy.composeapp.generated.resources.account_picture_custom
import augmy.composeapp.generated.resources.account_picture_peeps
import augmy.composeapp.generated.resources.account_picture_pick_title
import augmy.composeapp.generated.resources.button_dismiss
import augmy.composeapp.generated.resources.image_field_url_error_formats
import augmy.composeapp.generated.resources.image_field_url_hint
import augmy.composeapp.generated.resources.username_change_launcher_confirm
import augmy.interactive.shared.ext.scalingClickable
import augmy.interactive.shared.ui.base.LocalSnackbarHost
import augmy.interactive.shared.ui.components.BrandHeaderButton
import augmy.interactive.shared.ui.components.ErrorHeaderButton
import augmy.interactive.shared.ui.components.dialog.DialogShell
import augmy.interactive.shared.ui.components.input.CustomTextField
import augmy.interactive.shared.ui.theme.LocalTheme
import components.AsyncSvgImage
import data.Asset
import data.io.base.BaseResponse
import data.io.social.network.conversation.message.MediaIO
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.ktor.http.headers
import koin.profileChangeModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.context.loadKoinModules
import ui.conversation.components.MediaElement

@Composable
fun DialogPictureChange(onDismissRequest: () -> Unit) {
    loadKoinModules(profileChangeModule)
    val viewModel: ProfileChangeModel = koinViewModel()
    val snackbarHostState = LocalSnackbarHost.current
    val focusManager = LocalFocusManager.current

    val currentUser = viewModel.currentUser.collectAsState()
    val isLoading = viewModel.isLoading.collectAsState()

    val urlState = remember { TextFieldState() }
    val selectedImageUrl = rememberSaveable {
        mutableStateOf(
            urlState.text.ifEmpty { currentUser.value?.avatarUrl }
        )
    }
    val localFile = remember {
        mutableStateOf<PlatformFile?>(null)
    }
    val isUrlInEdit = rememberSaveable { mutableStateOf(false) }
    val urlLoadState = remember {
        mutableStateOf<BaseResponse<Any>?>(null)
    }

    LaunchedEffect(urlState.text) {
        if(urlState.text.isEmpty()) urlLoadState.value = null
    }

    val launcher = rememberFilePickerLauncher(
        type = FileKitType.Image,
        mode = FileKitMode.Single,
        title = stringResource(Res.string.account_picture_pick_title)
    ) { file ->
        if (file != null) {
            selectedImageUrl.value = null
            localFile.value = file
        }
    }

    LaunchedEffect(selectedImageUrl.value) {
        localFile.value = null
    }

    LaunchedEffect(Unit) {
        viewModel.isPictureChangeSuccess.collectLatest { res ->
            if(res == true) {
                CoroutineScope(Dispatchers.Main).launch {
                    snackbarHostState?.showSnackbar(
                        message = getString(Res.string.account_picture_change_success)
                    )
                }
                onDismissRequest()
            }
        }
    }

    DialogShell(
        modifier = Modifier.pointerInput(Unit) {
            detectTapGestures(onTap = {
                focusManager.clearFocus()
                if(urlState.text.isBlank()) isUrlInEdit.value = false
            })
        },
        content = {
            Column(
                verticalArrangement = Arrangement.spacedBy(LocalTheme.current.shapes.betweenItemsSpace),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                MediaElement(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth(.4f)
                        .widthIn(max = 250.dp)
                        .aspectRatio(1f)
                        .clip(CircleShape)
                        .background(
                            color = LocalTheme.current.colors.brandMain,
                            shape = CircleShape
                        ),
                    media = MediaIO(
                        url = urlState.text.ifEmpty { selectedImageUrl.value }?.toString()
                    ),
                    localMedia = localFile.value,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    onState = { loadState ->
                        if(isUrlInEdit.value) {
                            if(loadState is BaseResponse.Success) {
                                selectedImageUrl.value = urlState.text
                                isUrlInEdit.value = false
                            }
                            urlLoadState.value = loadState
                        }
                    }
                )
                Row(
                    modifier = Modifier
                        .padding(
                            bottom = 16.dp,
                            top = 8.dp,
                            start = 16.dp,
                            end = 16.dp
                        )
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ErrorHeaderButton(
                        modifier = Modifier.weight(1f),
                        text = stringResource(Res.string.button_dismiss),
                        onClick = onDismissRequest
                    )
                    BrandHeaderButton(
                        modifier = Modifier.weight(1f),
                        isLoading = isLoading.value,
                        text = stringResource(Res.string.username_change_launcher_confirm),
                        isEnabled = localFile.value != null
                                || (urlState.text.ifEmpty { selectedImageUrl.value }) != currentUser.value?.avatarUrl, // only if the url to avatar is different
                        onClick = {
                            viewModel.requestPictureChange(
                                fileUrl = selectedImageUrl.value.toString(),
                                localFile = localFile.value
                            )
                        }
                    )
                }
                LazyVerticalGrid(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(.5f),
                    columns = GridCells.Adaptive(minSize = 100.dp)
                ) {
                    headers {
                        item(span = {
                            GridItemSpan(maxCurrentLineSpan)
                        }) {
                            Column(
                                Modifier
                                    .padding(bottom = 16.dp)
                                    .animateContentSize()
                            ) {
                                Text(
                                    modifier = Modifier
                                        .padding(top = 16.dp)
                                        .fillMaxWidth(),
                                    text = stringResource(Res.string.account_picture_custom),
                                    style = LocalTheme.current.styles.category.copy(
                                        textAlign = TextAlign.Center,
                                        color = LocalTheme.current.colors.secondary
                                    )
                                )
                                Crossfade(targetState = isUrlInEdit.value) { isUrlInput ->
                                    if(isUrlInput) {
                                        CustomTextField(
                                            modifier = Modifier
                                                .padding(horizontal = 8.dp)
                                                .fillMaxWidth(),
                                            hint = stringResource(Res.string.image_field_url_hint),
                                            prefixIcon = Icons.Outlined.Link,
                                            state = urlState,
                                            errorText = if(urlLoadState.value is BaseResponse.Error) {
                                                stringResource(Res.string.image_field_url_error_formats)
                                            }else null,
                                            keyboardOptions = KeyboardOptions(
                                                keyboardType = KeyboardType.Text,
                                                imeAction = ImeAction.Done
                                            ),
                                            trailingIcon = if(urlLoadState.value is BaseResponse.Loading) {
                                                {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.requiredSize(24.dp),
                                                        color = LocalTheme.current.colors.brandMainDark,
                                                        trackColor = LocalTheme.current.colors.tetrial
                                                    )
                                                }
                                            }else null,
                                            isClearable = true
                                        )
                                    }else {
                                        Row(
                                            modifier = Modifier
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                                .fillMaxWidth()
                                                .border(
                                                    width = 1.dp,
                                                    color = LocalTheme.current.colors.secondary,
                                                    shape = LocalTheme.current.shapes.componentShape
                                                )
                                                .padding(vertical = 12.dp),
                                            horizontalArrangement = Arrangement.SpaceAround
                                        ) {
                                            Icon(
                                                modifier = Modifier
                                                    .size(42.dp)
                                                    .scalingClickable {
                                                        launcher.launch()
                                                    },
                                                imageVector = Icons.Outlined.Upload,
                                                contentDescription = stringResource(Res.string.accessibility_upload_files),
                                                tint = LocalTheme.current.colors.primary
                                            )
                                            Icon(
                                                modifier = Modifier
                                                    .size(42.dp)
                                                    .scalingClickable(
                                                        onTap = {
                                                            isUrlInEdit.value = true
                                                        }
                                                    ),
                                                imageVector = Icons.Outlined.Link,
                                                contentDescription = stringResource(Res.string.accessibility_upload_url),
                                                tint = LocalTheme.current.colors.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item(span = {
                        GridItemSpan(maxCurrentLineSpan)
                    }) {
                        Text(
                            modifier = Modifier
                                .padding(bottom = 4.dp)
                                .fillMaxWidth(),
                            text = stringResource(Res.string.account_picture_peeps),
                            style = LocalTheme.current.styles.category.copy(
                                textAlign = TextAlign.Center,
                                color = LocalTheme.current.colors.secondary
                            )
                        )
                    }
                    items(count = 105) { index ->
                        val peep = Asset.Peep(index)

                        AsyncSvgImage(
                            modifier = Modifier
                                .padding(1.5.dp)
                                .background(
                                    color = LocalTheme.current.colors.brandMain,
                                    shape = LocalTheme.current.shapes.rectangularActionShape
                                )
                                .scalingClickable(
                                    onTap = {
                                        selectedImageUrl.value = peep.url
                                    }
                                )
                                .aspectRatio(1f)
                                .fillMaxSize(),
                            model = peep.url,
                            contentDescription = null
                        )
                    }
                }
            }
        },
        onDismissRequest = onDismissRequest
    )
}