package ui.login.homeserver_picker

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import augmy.composeapp.generated.resources.Res
import augmy.composeapp.generated.resources.button_confirm
import augmy.composeapp.generated.resources.homeserver_picker_address_error
import augmy.composeapp.generated.resources.homeserver_picker_address_hint
import augmy.composeapp.generated.resources.homeserver_picker_your_address
import augmy.composeapp.generated.resources.login_homeserver_description
import augmy.composeapp.generated.resources.login_homeserver_heading
import augmy.interactive.shared.ext.scalingClickable
import augmy.interactive.shared.ui.components.BrandHeaderButton
import augmy.interactive.shared.ui.components.SimpleModalBottomSheet
import augmy.interactive.shared.ui.components.input.CustomTextField
import augmy.interactive.shared.ui.theme.LocalTheme
import base.utils.MatrixUtils.AUGMY_HOMESERVER_IDENTIFIER
import base.utils.MatrixUtils.MATRIX_HOME_SERVER
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.context.loadKoinModules
import ui.login.homeserver_picker.HomeserverPickerModel.HomeserverAddress

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatrixHomeserverPicker(
    userHomeserver: String,
    homeserver: HomeserverAddress? = null,
    onDismissRequest: () -> Unit,
    onSelect: (HomeserverAddress) -> Unit
) {
    loadKoinModules(homeserverPickerModule)
    val model = koinViewModel<HomeserverPickerModel>()
    val state = model.state.collectAsState()
    val homeservers = model.homeservers.collectAsState()

    val focusRequester = remember { FocusRequester() }
    val homeServerState = remember { TextFieldState() }
    val selectedHomeserver = remember(model) {
        mutableStateOf(homeserver)
    }

    LaunchedEffect(homeServerState.text) {
        val text = homeServerState.text
        if (text.isNotEmpty() && text != AUGMY_HOMESERVER_IDENTIFIER && text != MATRIX_HOME_SERVER) {
            model.validateHomeserver(homeserver = homeServerState.text)
            selectedHomeserver.value = HomeserverAddress(identifier = text.toString(), address = "")
        }
    }

    SimpleModalBottomSheet(
        modifier = Modifier.padding(horizontal = 12.dp),
        onDismissRequest = onDismissRequest
    ) {
        Text(
            text = stringResource(Res.string.login_homeserver_heading),
            style = LocalTheme.current.styles.subheading
        )
        Text(
            text = stringResource(Res.string.login_homeserver_description),
            style = LocalTheme.current.styles.regular
        )

        Spacer(Modifier.height(12.dp))

        homeservers.value.forEach { homeserver ->
            val missFocusRequester = remember(homeserver.address) { FocusRequester() }
            Row(
                modifier = Modifier
                    .focusRequester(missFocusRequester)
                    .focusable()
                    .scalingClickable(
                        key = homeserver,
                        hoverEnabled = false,
                        scaleInto = .95f
                    ) {
                        selectedHomeserver.value = homeserver
                        missFocusRequester.requestFocus()
                    }
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RadioButton(
                    selected = selectedHomeserver.value?.address == homeserver.address,
                    colors = LocalTheme.current.styles.radioButtonColors,
                    onClick = {
                        selectedHomeserver.value = homeserver
                        missFocusRequester.requestFocus()
                    }
                )
                Text(
                    text = homeserver.identifier,
                    style = LocalTheme.current.styles.title
                )
                if (userHomeserver == homeserver.address) {
                    Text(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .background(
                                color = LocalTheme.current.colors.backgroundDark,
                                shape = LocalTheme.current.shapes.circularActionShape
                            )
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        text = stringResource(Res.string.homeserver_picker_your_address)
                    )
                }
            }
        }

        val isCustomFocused = remember { mutableStateOf(false) }
        val isCustomSelected = (homeServerState.text == selectedHomeserver.value
                && homeServerState.text.isNotBlank()) || isCustomFocused.value

        LaunchedEffect(isCustomFocused.value) {
            if (isCustomFocused.value) {
                selectedHomeserver.value = HomeserverAddress(identifier = homeServerState.text.toString(), address = "")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(.7f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isCustomSelected,
                colors = LocalTheme.current.styles.radioButtonColors,
                onClick = { focusRequester.requestFocus() }
            )

            CustomTextField(
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Send
                ),
                isFocused = isCustomFocused,
                focusRequester = focusRequester,
                enabled = !state.value.isLoading,
                hint = stringResource(Res.string.homeserver_picker_address_hint),
                errorText = if(state.value.isError && homeServerState.text.isNotEmpty()) {
                    stringResource(Res.string.homeserver_picker_address_error)
                } else null,
                state = homeServerState,
                shape = LocalTheme.current.shapes.rectangularActionShape
            )

            AnimatedVisibility(state.value.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.requiredSize(24.dp),
                    color = LocalTheme.current.colors.disabled,
                    trackColor = LocalTheme.current.colors.disabledComponent
                )
            }
        }

        BrandHeaderButton(
            modifier = Modifier
                .align(Alignment.End)
                .padding(top = 8.dp),
            text = stringResource(Res.string.button_confirm),
            isEnabled = state.value.isSuccess || selectedHomeserver.value != homeServerState.text,
            isLoading = state.value.isLoading
        ) {
            (selectedHomeserver.value.takeIf { !isCustomSelected }?.address ?: state.value.data?.server)?.let { address ->
                if (isCustomSelected) {
                    model.saveHomeserverAddress(identifier = homeServerState.text.toString())
                }
                onSelect(
                    HomeserverAddress(
                        address = address,
                        identifier = selectedHomeserver.value?.identifier ?: homeServerState.text.toString()
                    )
                )
                onDismissRequest()
            }
        }
    }
}
