package ui.network.add_new

import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import augmy.composeapp.generated.resources.Res
import augmy.composeapp.generated.resources.account_username_error_format
import augmy.composeapp.generated.resources.account_username_hint
import augmy.composeapp.generated.resources.button_confirm
import augmy.composeapp.generated.resources.error_general
import augmy.composeapp.generated.resources.network_inclusion_description
import augmy.composeapp.generated.resources.network_inclusion_error_blocked
import augmy.composeapp.generated.resources.network_inclusion_error_duplicate
import augmy.composeapp.generated.resources.network_inclusion_error_non_existent
import augmy.composeapp.generated.resources.network_inclusion_error_non_format
import augmy.composeapp.generated.resources.network_inclusion_format_tag
import augmy.composeapp.generated.resources.network_inclusion_hint_tag
import augmy.composeapp.generated.resources.network_inclusion_success
import augmy.composeapp.generated.resources.network_inclusion_success_action
import augmy.composeapp.generated.resources.screen_network_new
import augmy.interactive.shared.ui.base.LocalNavController
import augmy.interactive.shared.ui.base.LocalSnackbarHost
import augmy.interactive.shared.ui.components.BrandHeaderButton
import augmy.interactive.shared.ui.components.SimpleModalBottomSheet
import augmy.interactive.shared.ui.components.input.CustomTextField
import augmy.interactive.shared.ui.theme.LocalTheme
import base.navigation.NavigationArguments
import base.navigation.NavigationNode
import data.NetworkProximityCategory
import data.io.ApiErrorCode
import data.io.social.network.conversation.message.MediaIO
import data.io.user.NetworkItemIO
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.context.loadKoinModules
import ui.account.profile.USERNAME_MAX_LENGTH
import ui.account.profile.USERNAME_MIN_LENGTH
import ui.network.components.ProximityPicker

private const val REGEX_USER_TAG = """^[a-fA-F0-9]+${'$'}"""

/** Screen for including new users to one's social network */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkAddNewLauncher(
    modifier: Modifier = Modifier,
    displayName: String? = null,
    tag: String? = null,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    onDismissRequest: () -> Unit
) {
    loadKoinModules(networkAddNewModule)
    val viewModel: NetworkAddNewModel = koinViewModel()

    val snackbarHostState = LocalSnackbarHost.current
    val navController = LocalNavController.current
    val isLoading = viewModel.isLoading.collectAsState()

    val displayNameState = remember { TextFieldState(displayName ?: "") }
    val tagState = rememberSaveable { TextFieldState(tag ?: "") }
    val errorMessage = remember {
        mutableStateOf<String?>(null)
    }
    val selectedCategory = remember {
        mutableStateOf(NetworkProximityCategory.Public)
    }
    val showProximityChoice = remember {
        mutableStateOf(false)
    }
    val focusRequester = remember { FocusRequester() }

    val isDisplayNameValid = with(displayNameState.text) {
        length in USERNAME_MIN_LENGTH..USERNAME_MAX_LENGTH
                && !startsWith(' ') && !endsWith(' ')
    }
    val isTagValid = with(tagState.text) {
        length == 6 && REGEX_USER_TAG.toRegex().matches(this)
    }

    LaunchedEffect(isDisplayNameValid, isTagValid) {
        if(isDisplayNameValid && isTagValid && errorMessage.value == null) {
            showProximityChoice.value = true
        }
    }

    LaunchedEffect(displayNameState.text) {
        errorMessage.value = null
    }

    LaunchedEffect(Unit) {
        delay(400)
        focusRequester.requestFocus()
    }

    LaunchedEffect(Unit) {
        viewModel.response.collectLatest {
            it?.error?.let { error ->
                errorMessage.value = getString(
                    when(error.errors.firstOrNull()) {
                        ApiErrorCode.DUPLICATE.name -> Res.string.network_inclusion_error_duplicate
                        ApiErrorCode.NON_EXISTENT.name -> Res.string.network_inclusion_error_non_existent
                        ApiErrorCode.INVALID_FORMAT.name -> Res.string.network_inclusion_error_non_format
                        ApiErrorCode.USER_BLOCKED.name -> Res.string.network_inclusion_error_blocked
                        else -> Res.string.error_general
                    }
                )
            }
            it?.success?.data?.let { data ->
                val name = displayNameState.text.toString() + ""
                CoroutineScope(Dispatchers.Main).launch {
                    if(snackbarHostState?.showSnackbar(
                            message = getString(Res.string.network_inclusion_success),
                            actionLabel = if(data.isInclusionImmediate == true && data.targetPublicId != null) {
                                getString(Res.string.network_inclusion_success_action)
                            }else null,
                            duration = SnackbarDuration.Short
                        ) == SnackbarResult.ActionPerformed
                    ) {
                        onDismissRequest()
                        navController?.navigate(
                            NavigationNode.Conversation(
                                conversationId = data.targetPublicId,
                                name = name
                            )
                        )
                    }
                }
                navController?.previousBackStackEntry
                    ?.savedStateHandle
                    ?.apply {
                        set(NavigationArguments.NETWORK_NEW_SUCCESS, true)
                    }
                onDismissRequest()
            }
        }
    }

    SimpleModalBottomSheet(
        modifier = modifier,
        sheetState = sheetState,
        onDismissRequest = onDismissRequest,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = stringResource(Res.string.screen_network_new),
            style = LocalTheme.current.styles.subheading
        )
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = stringResource(Res.string.network_inclusion_description),
            style = LocalTheme.current.styles.regular
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            CustomTextField(
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .weight(1f, fill = true),
                hint = stringResource(Res.string.account_username_hint),
                state = displayNameState,
                suggestText = if(!isDisplayNameValid) {
                    stringResource(Res.string.account_username_error_format)
                } else null,
                maxCharacters = USERNAME_MAX_LENGTH,
                errorText = errorMessage.value,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                )
            )
            CustomTextField(
                modifier = Modifier.weight(.75f),
                hint = stringResource(Res.string.network_inclusion_hint_tag),
                state = tagState,
                maxCharacters = 6,
                errorText = if(!isTagValid && tagState.text.isNotBlank()) {
                    stringResource(Res.string.network_inclusion_format_tag)
                } else null,
                suggestText = if(tagState.text.isBlank()) {
                    stringResource(Res.string.network_inclusion_format_tag)
                } else null,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done
                ),
                onKeyboardAction = {
                    if(isDisplayNameValid && isTagValid) {
                        viewModel.includeNewUser(
                            displayName = displayNameState.text,
                            proximity = selectedCategory.value
                        )
                    }
                },
                prefixIcon = Icons.Outlined.Tag
            )
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = showProximityChoice.value,
            enter = expandVertically() + fadeIn()
        ) {
            ProximityPicker(
                model = viewModel,
                selectedCategory = selectedCategory,
                newItem = NetworkItemIO(
                    displayName = displayNameState.text.toString(),
                    avatar = MediaIO(url = "https://augmy.org/storage/img/imjustafish.jpg")
                )
            )
        }

        BrandHeaderButton(
            modifier = Modifier.padding(top = 16.dp),
            isEnabled = isDisplayNameValid && isTagValid && errorMessage.value == null,
            isLoading = isLoading.value,
            text = stringResource(Res.string.button_confirm),
            onClick = {
                viewModel.includeNewUser(
                    displayName = displayNameState.text,
                    proximity = selectedCategory.value
                )
            }
        )
    }
}
