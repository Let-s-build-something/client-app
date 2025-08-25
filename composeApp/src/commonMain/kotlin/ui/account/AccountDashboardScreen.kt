package ui.account

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import augmy.composeapp.generated.resources.Res
import augmy.composeapp.generated.resources.accessibility_change_avatar
import augmy.composeapp.generated.resources.accessibility_change_username
import augmy.composeapp.generated.resources.accessibility_share
import augmy.composeapp.generated.resources.account_dashboard_sign_out
import augmy.composeapp.generated.resources.account_dashboard_theme
import augmy.composeapp.generated.resources.account_dashboard_theme_dark
import augmy.composeapp.generated.resources.account_dashboard_theme_device
import augmy.composeapp.generated.resources.account_dashboard_theme_light
import augmy.composeapp.generated.resources.account_settings_content_invisible
import augmy.composeapp.generated.resources.account_settings_content_offline
import augmy.composeapp.generated.resources.account_settings_content_online
import augmy.composeapp.generated.resources.account_settings_private_content
import augmy.composeapp.generated.resources.account_settings_private_title
import augmy.composeapp.generated.resources.account_settings_public_content
import augmy.composeapp.generated.resources.account_settings_public_title
import augmy.composeapp.generated.resources.account_settings_title_invisible
import augmy.composeapp.generated.resources.account_settings_title_offline
import augmy.composeapp.generated.resources.account_settings_title_online
import augmy.composeapp.generated.resources.account_sign_out_message
import augmy.composeapp.generated.resources.account_username_empty
import augmy.composeapp.generated.resources.action_link_copied
import augmy.composeapp.generated.resources.button_dismiss
import augmy.composeapp.generated.resources.button_yes
import augmy.composeapp.generated.resources.network_action_share
import augmy.composeapp.generated.resources.screen_account_title
import augmy.interactive.shared.ext.scalingClickable
import augmy.interactive.shared.ui.base.LocalIsMouseUser
import augmy.interactive.shared.ui.base.LocalNavController
import augmy.interactive.shared.ui.base.LocalSnackbarHost
import augmy.interactive.shared.ui.base.ModalScreenContent
import augmy.interactive.shared.ui.components.ErrorHeaderButton
import augmy.interactive.shared.ui.components.MinimalisticComponentIcon
import augmy.interactive.shared.ui.components.MinimalisticFilledIcon
import augmy.interactive.shared.ui.components.MultiChoiceSwitch
import augmy.interactive.shared.ui.components.dialog.AlertDialog
import augmy.interactive.shared.ui.components.dialog.ButtonState
import augmy.interactive.shared.ui.components.rememberMultiChoiceState
import augmy.interactive.shared.ui.theme.LocalTheme
import base.BrandBaseScreen
import base.navigation.NavigationNode
import base.utils.orZero
import base.utils.shareLink
import base.utils.withPlainText
import components.AvatarImage
import components.RowSetting
import data.io.app.ThemeChoice
import data.io.social.UserPrivacy
import data.io.social.UserVisibility
import data.io.social.network.conversation.message.MediaIO
import koin.HttpDomain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.folivo.trixnity.core.model.UserId
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import ui.account.profile.DialogPictureChange
import ui.account.profile.DisplayNameChangeLauncher

/**
 * Screen for the home page
 */
@Composable
fun AccountDashboardScreen(model: AccountDashboardModel = koinViewModel()) {
    val navController = LocalNavController.current

    val currentUser = model.currentUser.collectAsState(null)
    val signOutResponse = model.signOutResponse.collectAsState()

    val showSignOutDialog = remember {
        mutableStateOf(false)
    }

    LaunchedEffect(signOutResponse.value, currentUser.value) {
        if(signOutResponse.value && currentUser.value == null) {
            navController?.navigate(NavigationNode.Home) {
                popUpTo(NavigationNode.Home) {
                    inclusive = true
                }
                launchSingleTop = true
            }
        }
    }

    if(showSignOutDialog.value) {
        AlertDialog(
            title = stringResource(Res.string.account_dashboard_sign_out),
            message = AnnotatedString(stringResource(Res.string.account_sign_out_message)),
            confirmButtonState = ButtonState(
                text = stringResource(Res.string.button_yes),
                onClick = {
                    model.logout()
                }
            ),
            dismissButtonState = ButtonState(text = stringResource(Res.string.button_dismiss)),
            icon = Icons.AutoMirrored.Outlined.Logout,
            onDismissRequest = {
                showSignOutDialog.value = false
            }
        )
    }

    BrandBaseScreen(
        title = stringResource(Res.string.screen_account_title),
        /*actionIcons = { isExpanded ->
            ActionBarIcon(
                text = if(isExpanded) stringResource(Res.string.screen_network_management) else null,
                imageVector = Icons.Outlined.Handshake,
                onClick = {
                    navController?.navigate(NavigationNode.NetworkManagement())
                }
            )
        }*/
    ) {
        ModalScreenContent(
            modifier = Modifier
                .padding(start = 16.dp, end = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(
                LocalTheme.current.shapes.betweenItemsSpace * 2
            )
        ) {
            ProfileSection(model)

            SettingsSection(model)

            val isLoading = model.isLoading.collectAsState()
            ErrorHeaderButton(
                modifier = Modifier
                    .padding(top = 32.dp)
                    .fillMaxWidth(),
                text = stringResource(Res.string.account_dashboard_sign_out),
                isLoading = isLoading.value,
                endImageVector = Icons.AutoMirrored.Outlined.Logout,
                onClick = {
                    showSignOutDialog.value = true
                }
            )
        }
    }
}

@Composable
private fun SettingsSection(viewModel: AccountDashboardModel) {
    val localSettings = viewModel.localSettings.collectAsState()
    val currentUser = viewModel.currentUser.collectAsState()
    val privacyResponse = viewModel.privacyResponse.collectAsState()
    val visibilityResponse = viewModel.visibilityResponse.collectAsState()

    val privacy = currentUser.value?.configuration?.privacy ?: UserPrivacy.Public
    val visibility = currentUser.value?.configuration?.visibility ?: UserVisibility.Online

    val switchThemeState = rememberMultiChoiceState(
        items = mutableListOf(
            stringResource(Res.string.account_dashboard_theme_light),
            stringResource(Res.string.account_dashboard_theme_dark),
            stringResource(Res.string.account_dashboard_theme_device)
        ),
        selectedTabIndex = mutableStateOf(
            localSettings.value?.theme?.ordinal ?: ThemeChoice.SYSTEM.ordinal
        ),
        onSelectionChange = {
            viewModel.updateTheme(it)
        }
    )

    LaunchedEffect(localSettings.value?.theme) {
        switchThemeState.selectedTabIndex.value = localSettings.value?.theme?.ordinal ?: ThemeChoice.SYSTEM.ordinal
    }

    RowSetting(
        response = privacyResponse.value,
        title = stringResource(if(privacy == UserPrivacy.Private) {
            Res.string.account_settings_private_title
        }else Res.string.account_settings_public_title),
        content = stringResource(if(privacy == UserPrivacy.Private) {
            Res.string.account_settings_private_content
        }else Res.string.account_settings_public_content),
        lottieFileName = "private_public",
        scale = 2f,
        progressValue = if(privacy == UserPrivacy.Private) .5f else 0f,
        onTap = {
            if(privacyResponse.value == null) {
                viewModel.requestPrivacyChange(
                    if(currentUser.value?.configuration?.privacy == UserPrivacy.Public) {
                        UserPrivacy.Private
                    }else UserPrivacy.Public
                )
            }
        }
    )

    RowSetting(
        response = visibilityResponse.value,
        title = stringResource(when(visibility) {
            UserVisibility.Offline -> Res.string.account_settings_title_offline
            UserVisibility.Invisible -> Res.string.account_settings_title_invisible
            else -> Res.string.account_settings_title_online
        }),
        content = stringResource(when(visibility) {
            UserVisibility.Offline -> Res.string.account_settings_content_offline
            UserVisibility.Invisible -> Res.string.account_settings_content_invisible
            else -> Res.string.account_settings_content_online
        }),
        lottieFileName = "online_offline",
        progressValue = if(visibility == UserVisibility.Offline) 0f else .3f,
        tint = if(visibility == UserVisibility.Invisible) LocalTheme.current.colors.disabled else null,
        onTap = {
            if(visibilityResponse.value == null) {
                val v = currentUser.value?.configuration?.visibility
                viewModel.requestVisibilityChange(
                    if(v?.ordinal == 2) {
                        UserVisibility.entries.first()
                    }else UserVisibility.entries[v?.ordinal.orZero() + 1]
                )
            }
        }
    )

    Column(Modifier.fillMaxWidth()) {
        Text(
            modifier = Modifier.align(Alignment.Start),
            text = stringResource(Res.string.account_dashboard_theme),
            style = LocalTheme.current.styles.category
        )
        MultiChoiceSwitch(
            modifier = Modifier.fillMaxWidth(),
            shape = LocalTheme.current.shapes.rectangularActionShape,
            state = switchThemeState
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColumnScope.ProfileSection(model: AccountDashboardModel) {
    val currentUser = model.currentUser.collectAsState(null)

    val showNameChangeLauncher = rememberSaveable {
        mutableStateOf(false)
    }
    val showPictureChangeDialog = rememberSaveable {
        mutableStateOf(false)
    }

    val clipboard = LocalClipboard.current
    val snackbarHostState = LocalSnackbarHost.current
    val coroutineScope = rememberCoroutineScope()

    if (showNameChangeLauncher.value) {
        DisplayNameChangeLauncher {
            showNameChangeLauncher.value = false
        }
    }
    if (showPictureChangeDialog.value) {
        DialogPictureChange(
            onDismissRequest = {
                showPictureChangeDialog.value = false
            }
        )
    }

    Box {
        AvatarImage(
            modifier = Modifier
                .zIndex(5f)
                .fillMaxWidth(.4f)
                .aspectRatio(1f),
            media = MediaIO(url = currentUser.value?.avatarUrl),
            tag = currentUser.value?.tag,
            name = (currentUser.value?.displayName ?: "").ifBlank {
                UserId(currentUser.value?.userId ?: "").localpart
            }
        )
        MinimalisticFilledIcon(
            modifier = Modifier
                .padding(bottom = 8.dp, end = 8.dp)
                .align(Alignment.BottomEnd),
            onTap = {
                showPictureChangeDialog.value = true
            },
            imageVector = Icons.Outlined.Brush,
            contentDescription = stringResource(Res.string.accessibility_change_avatar)
        )
    }

    Row(
        modifier = Modifier.align(Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val isMouseUser = LocalIsMouseUser.current
        val isHovered = remember(model) {
            mutableStateOf(!isMouseUser)
        }

        Column {
            Row(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .scalingClickable(
                        onHover = { isHovered.value = it },
                        enabled = currentUser.value?.userId != null
                    ) {
                        showNameChangeLauncher.value = true
                    }
                    .animateContentSize(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = currentUser.value?.displayName ?: stringResource(Res.string.account_username_empty),
                    style = LocalTheme.current.styles.subheading
                )
                if (isHovered.value) {
                    Icon(
                        modifier = Modifier.size(24.dp),
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = stringResource(Res.string.accessibility_change_username),
                        tint = LocalTheme.current.colors.secondary
                    )
                }
            }
            currentUser.value?.userId?.let { userId ->
                Text(
                    text = userId,
                    style = LocalTheme.current.styles.regular.copy(
                        color = LocalTheme.current.colors.disabled
                    )
                )
            }
        }
        MinimalisticComponentIcon(
            modifier = Modifier.padding(start = 4.dp),
            imageVector = Icons.Outlined.IosShare,
            contentDescription = stringResource(Res.string.accessibility_share),
            onTap = {
                shareProfile(
                    coroutineScope = coroutineScope,
                    publicId = currentUser.value?.userId,
                    clipboard = clipboard,
                    snackbarHostState = snackbarHostState
                )
            }
        )
    }
}

/** Shares current user's profile */
fun shareProfile(
    coroutineScope: CoroutineScope,
    publicId: String?,
    clipboard: Clipboard,
    snackbarHostState: SnackbarHostState?
) {
    val url = "$HttpDomain/users/$publicId"
    coroutineScope.launch {
        if(!shareLink(
                title = getString(Res.string.network_action_share),
                link = url
            )
        ) {
            clipboard.withPlainText(url)
            snackbarHostState?.showSnackbar(
                message = getString(Res.string.action_link_copied)
            )
        }
    }
}
