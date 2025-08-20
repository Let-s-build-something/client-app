package ui.search.user

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import augmy.composeapp.generated.resources.Res
import augmy.composeapp.generated.resources.action_search_users
import augmy.composeapp.generated.resources.button_invite
import augmy.composeapp.generated.resources.screen_search_user
import augmy.composeapp.generated.resources.search_user_information
import augmy.interactive.shared.ext.scalingClickable
import augmy.interactive.shared.ui.base.LocalNavController
import augmy.interactive.shared.ui.components.BrandHeaderButton
import augmy.interactive.shared.ui.components.input.CustomTextField
import augmy.interactive.shared.ui.components.input.DELAY_BETWEEN_TYPING_SHORT
import augmy.interactive.shared.ui.theme.LocalTheme
import base.BrandBaseScreen
import base.navigation.NavIconType
import base.navigation.NavigationArguments
import components.network.NetworkItemRow
import data.io.user.NetworkItemIO
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.context.loadKoinModules
import ui.network.components.user_detail.UserDetailDialog
import ui.search.user.SearchUserModel.Companion.ITEMS_COUNT
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalFoundationApi::class, ExperimentalUuidApi::class,
    ExperimentalMaterial3Api::class
)
@Composable
fun SearchUserScreen(
    excludeUsers: List<String>?,
    awaitingResult: Boolean?,
    isInvitation: Boolean
) {
    loadKoinModules(searchUserModule)
    val model = koinViewModel<SearchUserModel>()

    val navController = LocalNavController.current
    val cancellableScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val searchState = remember { TextFieldState() }

    val users = model.users.collectAsState()

    val selectedUser = remember { mutableStateOf<NetworkItemIO?>(null) }

    LaunchedEffect(searchState.text) {
        cancellableScope.coroutineContext.cancelChildren()
        cancellableScope.launch {
            delay(DELAY_BETWEEN_TYPING_SHORT)
            model.queryUsers(
                prompt = searchState.text,
                excludeUsers = excludeUsers.orEmpty()
            )
        }
    }

    LaunchedEffect(focusRequester) {
        focusRequester.requestFocus()
    }

    selectedUser.value?.let { user ->
        UserDetailDialog(
            networkItem = user,
            userId = user.userId,
            onDismissRequest = {
                selectedUser.value = null
            }
        )
    }

    val selectUser: (NetworkItemIO) -> Unit = { user ->
        if(awaitingResult == true) {
            model.saveUser(user) {
                navController?.previousBackStackEntry?.savedStateHandle?.set(
                    key = NavigationArguments.SEARCH_USER_ID,
                    value = user.userId
                )
                navController?.navigateUp()
            }
        }else {
            model.saveUser(user)
            selectedUser.value = user
        }
    }

    BrandBaseScreen(
        title = stringResource(Res.string.screen_search_user),
        navIconType = NavIconType.CLOSE
    ) {
        LazyColumn {
            stickyHeader {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CustomTextField(
                        modifier = Modifier
                            .zIndex(1f)
                            .padding(vertical = 12.dp)
                            .weight(1f),
                        focusRequester = focusRequester,
                        hint = stringResource(Res.string.action_search_users),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Search
                        ),
                        onKeyboardAction = {
                            if (isInvitation) {
                                selectUser(NetworkItemIO(userId = searchState.text.toString()))
                            }
                        },
                        prefixIcon = Icons.Outlined.Search,
                        state = searchState,
                        isClearable = true
                    )

                    AnimatedVisibility(isInvitation) {
                        BrandHeaderButton(
                            text = stringResource(Res.string.button_invite),
                            shape = LocalTheme.current.shapes.rectangularActionShape
                        ) {
                            selectUser(NetworkItemIO(userId = searchState.text.toString()))
                        }
                    }
                }
            }
            item {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(Res.string.search_user_information),
                        style = LocalTheme.current.styles.regular.copy(
                            color = LocalTheme.current.colors.disabled
                        )
                    )
                }
            }
            items(
                items = users.value ?: arrayOfNulls<NetworkItemIO>(ITEMS_COUNT).toList().takeIf {
                    searchState.text.isNotBlank()
                }.orEmpty(),
                key = { it?.primaryKey ?: Uuid.random().toString() }
            ) { user ->
                NetworkItemRow(
                    modifier = Modifier
                        .animateItem()
                        .padding(horizontal = 16.dp)
                        .scalingClickable(scaleInto = .95f) {
                            if(user != null) selectUser(user)
                        }
                        .fillMaxWidth(),
                    highlight = searchState.text.toString().lowercase(),
                    data = user,
                    onAvatarClick = {
                        selectedUser.value = user
                    }
                )
            }
        }
    }
}
