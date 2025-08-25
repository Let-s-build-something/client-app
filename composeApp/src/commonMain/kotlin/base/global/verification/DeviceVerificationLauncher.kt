package base.global.verification

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.byValue
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.KeyboardCommandKey
import androidx.compose.material.icons.outlined.LaptopWindows
import androidx.compose.material.icons.outlined.MarkChatUnread
import androidx.compose.material.icons.outlined.Pin
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import augmy.composeapp.generated.resources.Res
import augmy.composeapp.generated.resources.accessibility_cancel
import augmy.composeapp.generated.resources.accessibility_device_android
import augmy.composeapp.generated.resources.accessibility_device_apple
import augmy.composeapp.generated.resources.accessibility_device_jvm
import augmy.composeapp.generated.resources.accessibility_hide_password
import augmy.composeapp.generated.resources.accessibility_show_password
import augmy.composeapp.generated.resources.button_confirm
import augmy.composeapp.generated.resources.button_dismiss
import augmy.composeapp.generated.resources.button_reject
import augmy.composeapp.generated.resources.device_verification_bootstrap_title
import augmy.composeapp.generated.resources.device_verification_canceled
import augmy.composeapp.generated.resources.device_verification_confirm
import augmy.composeapp.generated.resources.device_verification_loading
import augmy.composeapp.generated.resources.device_verification_match
import augmy.composeapp.generated.resources.device_verification_no_match
import augmy.composeapp.generated.resources.device_verification_other_device
import augmy.composeapp.generated.resources.device_verification_other_request
import augmy.composeapp.generated.resources.device_verification_passphrase
import augmy.composeapp.generated.resources.device_verification_passphrase_error
import augmy.composeapp.generated.resources.device_verification_passphrase_hint
import augmy.composeapp.generated.resources.device_verification_send
import augmy.composeapp.generated.resources.device_verification_send_info
import augmy.composeapp.generated.resources.device_verification_send_info_2
import augmy.composeapp.generated.resources.device_verification_success
import augmy.composeapp.generated.resources.device_verification_title
import augmy.composeapp.generated.resources.device_verification_title_decimal
import augmy.composeapp.generated.resources.device_verification_title_emojis
import augmy.interactive.shared.ext.ifNull
import augmy.interactive.shared.ui.base.PlatformType
import augmy.interactive.shared.ui.components.BrandHeaderButton
import augmy.interactive.shared.ui.components.MinimalisticIcon
import augmy.interactive.shared.ui.components.OutlinedButton
import augmy.interactive.shared.ui.components.input.CustomTextField
import augmy.interactive.shared.ui.theme.LocalTheme
import augmy.interactive.shared.ui.theme.SharedColors
import base.utils.MatrixUtils.AUGMY_HOMESERVER_IDENTIFIER
import korlibs.io.util.substringBeforeOrNull
import kotlinx.coroutines.launch
import net.folivo.trixnity.client.verification.SelfVerificationMethod
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.context.loadKoinModules

/**
 * Bottom sheet for verifying current device
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceVerificationLauncher(modifier: Modifier = Modifier) {
    loadKoinModules(verificationModule)
    val model: DeviceVerificationModel = koinViewModel()
    val launcherState = model.launcherState.collectAsState()

    if (launcherState.value !is LauncherState.Hidden) {
        Column(
            modifier = modifier
                .animateContentSize()
                .fillMaxWidth()
                .background(color = LocalTheme.current.colors.backgroundDark)
        ) {
            Crossfade(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                targetState = launcherState.value
            ) { state ->
                when(state) {
                    is LauncherState.SelfVerification -> SelfVerification(
                        model = model,
                        state = state
                    )
                    is LauncherState.ComparisonByUser -> ComparisonByUser(
                        model = model,
                        launcherState = state
                    )
                    is LauncherState.TheirRequest -> TheirRequestContent(
                        model = model,
                        launcherState = state
                    )
                    is LauncherState.Success -> SuccessContent(model)
                    is LauncherState.Canceled -> CanceledContent(model)
                    is LauncherState.Bootstrap -> BootstrapContent(model)
                    is LauncherState.Loading -> LoadingContent(model)
                    else -> {}
                }
            }
        }
    }
}

@Composable
private fun BootstrapContent(model: DeviceVerificationModel) {
    val isLoading = model.isLoading.collectAsState()

    val passphraseState = remember { TextFieldState() }
    val showPassphrase = remember { mutableStateOf(false) }

    val verify = {
        model.bootstrap(passphraseState.text.toString())
    }

    Column {
        Text(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 24.dp),
            text = stringResource(Res.string.device_verification_bootstrap_title),
            style = LocalTheme.current.styles.heading
        )

        CustomTextField(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(vertical = LocalTheme.current.shapes.betweenItemsSpace)
                .fillMaxWidth(.7f),
            hint = stringResource(Res.string.device_verification_passphrase_hint),
            prefixIcon = Icons.Outlined.Pin,
            state = passphraseState,
            textObfuscationMode = if(showPassphrase.value) {
                TextObfuscationMode.Visible
            }else TextObfuscationMode.RevealLastTyped,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Send
            ),
            onKeyboardAction = {
                if (isLoading.value) model.cancel() else verify()
            },
            trailingIcon = {
                Crossfade(
                    targetState = showPassphrase.value
                ) { isPasswordVisible ->
                    MinimalisticIcon(
                        contentDescription = stringResource(
                            if(isPasswordVisible) {
                                Res.string.accessibility_hide_password
                            }else Res.string.accessibility_show_password
                        ),
                        imageVector = if(isPasswordVisible) {
                            Icons.Outlined.Visibility
                        }else Icons.Outlined.VisibilityOff,
                        tint = LocalTheme.current.colors.secondary
                    ) {
                        showPassphrase.value = !showPassphrase.value
                    }
                }
            },
            // Augmy has numeric PINs only
            inputTransformation = if(model.currentUser.value?.matrixHomeserver == AUGMY_HOMESERVER_IDENTIFIER) {
                InputTransformation.maxLength(PASSPHRASE_MAX_LENGTH).byValue { _, proposed ->
                    proposed.replace(Regex("[^0-9]"), "")
                }
            }else null,
            paddingValues = PaddingValues(start = 16.dp)
        )

        BrandHeaderButton(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            text = stringResource(
                when {
                    isLoading.value -> Res.string.accessibility_cancel
                    else -> Res.string.button_confirm
                }
            ),
            isLoading = isLoading.value,
            onClick = {
                if (isLoading.value) model.cancel() else verify()
            }
        )
    }
}

@Composable
private fun SuccessContent(model: DeviceVerificationModel) {
    Row (
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp, start = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            modifier = Modifier.size(60.dp),
            imageVector = Icons.Outlined.VerifiedUser,
            tint = LocalTheme.current.colors.brandMain,
            contentDescription = null
        )
        Text(
            modifier = Modifier.weight(1f),
            text = stringResource(Res.string.device_verification_success),
            style = LocalTheme.current.styles.heading
        )
        OutlinedButton(
            text = stringResource(Res.string.button_confirm),
            onClick = { model.hide() },
            activeColor = LocalTheme.current.colors.secondary
        )
    }
}

@Composable
private fun CanceledContent(model: DeviceVerificationModel) {
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = stringResource(Res.string.device_verification_canceled),
            style = LocalTheme.current.styles.category
        )
        OutlinedButton(
            text = stringResource(Res.string.button_dismiss),
            onClick = { model.hide() },
            activeColor = LocalTheme.current.colors.disabled
        )
    }
}

@Composable
private fun LoadingContent(model: DeviceVerificationModel) {
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = stringResource(Res.string.device_verification_loading),
            style = LocalTheme.current.styles.category
        )
        BrandHeaderButton(
            text = stringResource(Res.string.accessibility_cancel),
            onClick = { model.cancel(manual = true) },
            isLoading = true
        )
    }
}

@Composable
private fun SelfVerification(
    model: DeviceVerificationModel,
    state: LauncherState.SelfVerification
) {
    val isLoading = model.isLoading.collectAsState()
    val verificationResult = model.verificationResult.collectAsState()

    val selectedMethod = remember(model) {
        mutableStateOf(state.methods.firstOrNull())
    }
    val passphraseState = remember { TextFieldState() }
    val showPassphrase = remember { mutableStateOf(false) }

    val verify = {
        selectedMethod.value?.let { method ->
            model.verifySelf(
                method = method,
                passphrase = passphraseState.text.toString()
            )
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 24.dp),
            text = stringResource(Res.string.device_verification_title),
            style = LocalTheme.current.styles.heading
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.methods.forEach { method ->
                ClickableTile(
                    text = stringResource(
                        when(method) {
                            is SelfVerificationMethod.CrossSignedDeviceVerification -> Res.string.device_verification_other_device
                            else -> Res.string.device_verification_passphrase
                        }
                    ),
                    icon = when(method) {
                        is SelfVerificationMethod.CrossSignedDeviceVerification -> Icons.Outlined.MarkChatUnread
                        else -> Icons.Outlined.Pin
                    },
                    isSelected = selectedMethod.value == method,
                    onClick = {
                        selectedMethod.value = method
                    }
                )
            }
        }

        AnimatedVisibility(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            visible = selectedMethod.value?.isRecoveryMethod() == true
        ) {
            CustomTextField(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = LocalTheme.current.shapes.betweenItemsSpace)
                    .fillMaxWidth(.7f),
                hint = stringResource(Res.string.device_verification_passphrase_hint),
                prefixIcon = Icons.Outlined.Pin,
                state = passphraseState,
                errorText = if(verificationResult.value?.isFailure == true) {
                    stringResource(Res.string.device_verification_passphrase_error)
                }else null,
                textObfuscationMode = if(showPassphrase.value) {
                    TextObfuscationMode.Visible
                }else TextObfuscationMode.RevealLastTyped,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Send
                ),
                onKeyboardAction = { verify() },
                trailingIcon = {
                    Crossfade(
                        targetState = showPassphrase.value
                    ) { isPasswordVisible ->
                        MinimalisticIcon(
                            contentDescription = stringResource(
                                if(isPasswordVisible) {
                                    Res.string.accessibility_hide_password
                                }else Res.string.accessibility_show_password
                            ),
                            imageVector = if(isPasswordVisible) {
                                Icons.Outlined.Visibility
                            }else Icons.Outlined.VisibilityOff,
                            tint = LocalTheme.current.colors.secondary
                        ) {
                            showPassphrase.value = !showPassphrase.value
                        }
                    }
                },
                // Augmy has numeric PINs only
                inputTransformation = if(model.currentUser.value?.matrixHomeserver == AUGMY_HOMESERVER_IDENTIFIER) {
                    InputTransformation.maxLength(PASSPHRASE_MAX_LENGTH).byValue { _, proposed ->
                        proposed.replace(Regex("[^0-9]"), "")
                    }
                }else null,
                paddingValues = PaddingValues(start = 16.dp)
            )
        }

        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BrandHeaderButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                text = stringResource(
                    when {
                        isLoading.value -> Res.string.accessibility_cancel
                        selectedMethod.value?.isRecoveryMethod() == true -> Res.string.device_verification_confirm
                        else -> Res.string.device_verification_send
                    }
                ),
                isLoading = isLoading.value,
                onClick = {
                    if(isLoading.value) {
                        model.cancel()
                    }else verify()
                }
            )
            AnimatedVisibility(
                visible = selectedMethod.value is SelfVerificationMethod.CrossSignedDeviceVerification
            ) {
                Text(
                    modifier = Modifier
                        .padding(start = 16.dp, end = 16.dp, top = 2.dp)
                        .animateContentSize(),
                    text = stringResource(
                        if(isLoading.value) {
                            Res.string.device_verification_send_info_2
                        }else Res.string.device_verification_send_info
                    ),
                    style = LocalTheme.current.styles.regular
                )
            }
        }
    }
}

@Composable
fun TheirRequestContent(
    model: DeviceVerificationModel,
    launcherState: LauncherState.TheirRequest
) {
    val isLoading = model.isLoading.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val platformType = PlatformType.entries.find {
        it.name == launcherState.fromDevice.substringBeforeOrNull("_")
    }

    Row(
        modifier = Modifier
            .padding(
                bottom = 24.dp,
                start = 16.dp
            )
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                platformType?.let { platform ->
                    Icon(
                        modifier = Modifier.size(32.dp),
                        imageVector = when(platform) {
                            PlatformType.Jvm -> Icons.Outlined.LaptopWindows
                            PlatformType.Native -> Icons.Outlined.KeyboardCommandKey
                            PlatformType.Android -> Icons.Outlined.Android
                        },
                        contentDescription = stringResource(
                            when(platform) {
                                PlatformType.Jvm -> Res.string.accessibility_device_jvm
                                PlatformType.Native -> Res.string.accessibility_device_apple
                                PlatformType.Android -> Res.string.accessibility_device_android
                            }
                        ),
                        tint = LocalTheme.current.colors.disabled
                    )
                }
                Text(
                    text = launcherState.fromDevice.let {
                        if(platformType != null) it.substringAfter("_") else it
                    },
                    style = LocalTheme.current.styles.regular.copy(
                        color = LocalTheme.current.colors.disabled
                    )
                )
            }
            Text(
                text = stringResource(Res.string.device_verification_other_request),
                style = LocalTheme.current.styles.subheading
            )
        }
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AnimatedVisibility(!isLoading.value) {
                OutlinedButton(
                    text = stringResource(Res.string.button_reject),
                    onClick = { model.cancel(manual = true) },
                    activeColor = LocalTheme.current.colors.disabled
                )
            }
            BrandHeaderButton(
                text = stringResource(
                    when {
                        isLoading.value -> Res.string.accessibility_cancel
                        else -> Res.string.button_confirm
                    }
                ),
                isLoading = isLoading.value,
                onClick = {
                    if(isLoading.value) {
                        model.cancel()
                    }else coroutineScope.launch {
                        launcherState.onReady()
                    }
                }
            )
        }
    }
}

@Composable
private fun ComparisonByUser(
    model: DeviceVerificationModel,
    launcherState: LauncherState.ComparisonByUser
) {
    val isLoading = model.isLoading.collectAsState()

    Column(
        modifier = Modifier
            .padding(bottom = 24.dp, top = 4.dp)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .align(Alignment.CenterHorizontally),
            text = stringResource(
                if(launcherState.data.emojis.isNotEmpty()) {
                    Res.string.device_verification_title_emojis
                }else Res.string.device_verification_title_decimal
            ),
            style = LocalTheme.current.styles.subheading
        )

        with(Modifier
            .padding(4.dp)
            .border(
                width = 1.dp,
                color = LocalTheme.current.colors.backgroundLight,
                shape = LocalTheme.current.shapes.rectangularActionShape
            )
            .padding(vertical = 8.dp, horizontal = 10.dp)
        ) {
            launcherState.data.emojis.takeIf { it.isNotEmpty() }?.chunked(4)?.forEach { chunks ->
                Row(horizontalArrangement = Arrangement.SpaceBetween) {
                    chunks.forEach { emoji ->
                        EmojiEntity(
                            modifier = this@with,
                            emoji = emoji
                        )
                    }
                }
            }?.ifNull {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    launcherState.data.decimals.forEach { decimal ->
                        Text(
                            modifier = this@with,
                            text = decimal.toString(),
                            style = LocalTheme.current.styles.subheading
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .padding(vertical = 8.dp, horizontal = 16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedVisibility(!isLoading.value) {
                OutlinedButton(
                    modifier = Modifier.padding(end = 12.dp),
                    text = stringResource(Res.string.device_verification_no_match),
                    onClick = {
                        model.matchChallenge(false)
                    },
                    activeColor = SharedColors.RED_ERROR_50
                )
            }
            BrandHeaderButton(
                isLoading = isLoading.value,
                text = stringResource(
                    if(isLoading.value) Res.string.accessibility_cancel
                    else Res.string.device_verification_match
                ),
                onClick = {
                    if(isLoading.value) model.cancel()
                    else model.matchChallenge(true)
                }
            )
        }
    }
}

fun SelfVerificationMethod.isRecoveryMethod() = this is SelfVerificationMethod.AesHmacSha2RecoveryKeyWithPbkdf2Passphrase
        || this is SelfVerificationMethod.AesHmacSha2RecoveryKey

private const val PASSPHRASE_MAX_LENGTH = 8
