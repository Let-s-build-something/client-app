package ui.dev.experiment

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.byValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.WavingHand
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import augmy.interactive.shared.ext.scalingClickable
import augmy.interactive.shared.ui.components.ProgressPressableContainer
import augmy.interactive.shared.ui.components.SimpleModalBottomSheet
import augmy.interactive.shared.ui.components.dialog.AlertDialog
import augmy.interactive.shared.ui.components.dialog.ButtonState
import augmy.interactive.shared.ui.components.input.CustomTextField
import augmy.interactive.shared.ui.components.input.DELAY_BETWEEN_TYPING_SHORT
import augmy.interactive.shared.ui.theme.LocalTheme
import augmy.interactive.shared.ui.theme.SharedColors
import data.io.experiment.ExperimentIO
import data.io.experiment.FullExperiment
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import ui.dev.sensorList
import kotlin.uuid.ExperimentalUuidApi

private enum class DialogType {
    DeleteConfirmation,
    Set,
    Frequency,
    Behavior,
    Sensor
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalUuidApi::class)
@Composable
fun ExperimentBottomSheet(
    experiment: FullExperiment,
    model: ExperimentModel = koinViewModel(key = experiment.data.uid),
    onDismissRequest: () -> Unit
) {
    val cancellableScope = rememberCoroutineScope()
    val activeExperiments = model.activeExperiments.collectAsState()

    val showDialog = remember(experiment.data.uid) {
        mutableStateOf<DialogType?>(null)
    }
    val selectedSet = remember(experiment.data.uid) {
        mutableStateOf(experiment.sets.firstOrNull())
    }
    val selectedFrequency = remember(experiment.data.uid) {
        mutableStateOf(experiment.data.displayFrequency)
    }
    val selectedBehavior = remember(experiment.data.uid) {
        mutableStateOf(experiment.data.choiceBehavior)
    }
    val nameState = remember(experiment.data.uid) {
        TextFieldState(initialText = experiment.data.name)
    }

    LaunchedEffect(model) {
        model.requestAvailableSensors(key = experiment.data.uid)
    }

    LaunchedEffect(nameState.text) {
        cancellableScope.coroutineContext.cancelChildren()
        cancellableScope.launch {
            delay(DELAY_BETWEEN_TYPING_SHORT)
            model.changeNameOf(experiment.data.uid, nameState.text)
        }
    }

    when(showDialog.value) {
        DialogType.DeleteConfirmation -> AlertDialog(
            title = "Delete experiment",
            message = AnnotatedString("Are you sure you want to delete this experiment?"),
            dismissButtonState = ButtonState("Dismiss"),
            confirmButtonState = ButtonState(
                text = "Confirm",
                onClick = {
                    model.deleteExperiment(experiment.data.uid)
                    showDialog.value = null
                    onDismissRequest()
                }
            ),
            onDismissRequest = { showDialog.value = null }
        )
        DialogType.Set -> SetListDialog(
            model = model,
            experiment = experiment,
            selectedSet = selectedSet.value,
            onConfirm = {
                selectedSet.value = it
                model.changeSetOf(experiment.data.uid, it.uid)
                showDialog.value = null
            },
            onDismissRequest = { showDialog.value = null }
        )
        DialogType.Frequency -> DisplayFrequencyDialog(
            initialFrequency = selectedFrequency.value,
            onConfirm = {
                model.changeFrequencyOf(experiment.data.uid, it)
                selectedFrequency.value = it
            },
            onDismissRequest = { showDialog.value = null }
        )
        DialogType.Behavior -> ChoiceBehaviorDialog(
            initialBehavior = selectedBehavior.value,
            onConfirm = {
                model.changeBehaviorOf(experiment.data.uid, it)
                selectedBehavior.value = it
            },
            onDismissRequest = { showDialog.value = null }
        )
        DialogType.Sensor -> SensorSelectionDialog(
            model = model,
            experiment = experiment.data,
            onDismissRequest = { showDialog.value = null }
        )
        null -> {}
    }

    SimpleModalBottomSheet(
        onDismissRequest = onDismissRequest
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CustomTextField(
                modifier = Modifier.fillMaxWidth(.7f),
                state = nameState,
                prefixIcon = Icons.Outlined.Tag
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ProgressPressableContainer(
                    modifier = Modifier.requiredSize(36.dp),
                    onFinish = {
                        showDialog.value = DialogType.DeleteConfirmation
                    },
                    trackColor = LocalTheme.current.colors.disabled,
                    progressColor = SharedColors.RED_ERROR
                ) {
                    Icon(
                        modifier = Modifier.size(32.dp),
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = null,
                        tint = SharedColors.RED_ERROR
                    )
                }

                Crossfade(activeExperiments.value.contains(experiment.data.uid)) { isActive ->
                    Icon(
                        modifier = Modifier
                            .scalingClickable {
                                model.toggleExperiment(experiment.data.uid, !isActive)
                                onDismissRequest()
                            }
                            .size(42.dp)
                            .padding(5.dp),
                        imageVector = if (isActive) Icons.Outlined.Stop else Icons.Outlined.PlayArrow,
                        contentDescription = null,
                        tint = if (isActive) SharedColors.RED_ERROR else LocalTheme.current.colors.brandMain
                    )
                }
            }
        }

        // value set
        Row(
            modifier = Modifier.padding(top = 12.dp, start = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                modifier = Modifier.size(24.dp),
                imageVector = Icons.Outlined.Quiz,
                tint = LocalTheme.current.colors.disabled,
                contentDescription = null
            )
            Text(
                modifier = Modifier.padding(start = 2.dp),
                text = "Values set: ",
                style = LocalTheme.current.styles.regular
            )
            Text(
                modifier = Modifier
                    .padding(start = 6.dp)
                    .scalingClickable {
                        showDialog.value = DialogType.Set
                    }
                    .background(
                        color = LocalTheme.current.colors.backgroundDark,
                        shape = LocalTheme.current.shapes.rectangularActionShape
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                text = selectedSet.value?.name ?: "no set selected",
                style = LocalTheme.current.styles.category
            )
        }

        // frequency
        Row(
            modifier = Modifier.padding(top = 6.dp, start = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                modifier = Modifier.size(24.dp),
                imageVector = Icons.Outlined.EventRepeat,
                tint = LocalTheme.current.colors.disabled,
                contentDescription = null
            )
            Text(
                modifier = Modifier.padding(start = 4.dp),
                text = "Display frequency: ",
                style = LocalTheme.current.styles.regular
            )
            Text(
                modifier = Modifier
                    .padding(start = 6.dp)
                    .scalingClickable {
                        showDialog.value = DialogType.Frequency
                    }
                    .background(
                        color = LocalTheme.current.colors.backgroundDark,
                        shape = LocalTheme.current.shapes.rectangularActionShape
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                text = when (val frequency = selectedFrequency.value) {
                    is ExperimentIO.DisplayFrequency.Constant -> "every ${frequency.delaySeconds}s"
                    ExperimentIO.DisplayFrequency.BeginEnd -> "start + end"
                    ExperimentIO.DisplayFrequency.Permanent -> "all the time"
                },
                style = LocalTheme.current.styles.category
            )
        }

        // choice behavior
        Row(
            modifier = Modifier.padding(top = 6.dp, start = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                modifier = Modifier.size(24.dp),
                imageVector = Icons.Outlined.CheckBox,
                tint = LocalTheme.current.colors.disabled,
                contentDescription = null
            )
            Text(
                modifier = Modifier.padding(start = 4.dp),
                text = "Choice behavior: ",
                style = LocalTheme.current.styles.regular
            )
            Text(
                modifier = Modifier
                    .padding(start = 6.dp)
                    .scalingClickable {
                        showDialog.value = DialogType.Behavior
                    }
                    .background(
                        color = LocalTheme.current.colors.backgroundDark,
                        shape = LocalTheme.current.shapes.rectangularActionShape
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                text = when (selectedBehavior.value) {
                    ExperimentIO.ChoiceBehavior.SingleChoice -> "single-choice"
                    ExperimentIO.ChoiceBehavior.MultiChoice -> "multi-choice"
                    ExperimentIO.ChoiceBehavior.OrderedChoice -> "ordered-choice"
                },
                style = LocalTheme.current.styles.category
            )
        }

        Row(
            modifier = Modifier.padding(top = 4.dp, start = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                modifier = Modifier.size(24.dp),
                imageVector = Icons.AutoMirrored.Outlined.Chat,
                tint = LocalTheme.current.colors.disabled,
                contentDescription = null
            )
            Text(
                modifier = Modifier.padding(start = 4.dp),
                text = "Stream messages, reactions: ",
                style = LocalTheme.current.styles.regular
            )
            Switch(
                modifier = Modifier
                    .requiredHeight(20.dp)
                    .padding(start = 10.dp),
                colors = LocalTheme.current.styles.switchColorsDefault,
                onCheckedChange = { observe ->
                    model.updateChatObservation(uid = experiment.data.uid, observe = observe)
                    if (activeExperiments.value.contains(experiment.data.uid)) {
                        model.observeChats(observe, key = experiment.data.uid)
                    }
                },
                checked = experiment.data.observeChats
            )
        }

        Row(
            modifier = Modifier.padding(top = 6.dp, start = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                modifier = Modifier.size(24.dp),
                imageVector = Icons.Outlined.WavingHand,
                tint = LocalTheme.current.colors.disabled,
                contentDescription = null
            )
            Text(
                modifier = Modifier.padding(start = 4.dp),
                text = "Sensors: ",
                style = LocalTheme.current.styles.regular
            )

            val availableSensors = model.availableSensors.collectAsState()
            Text(
                modifier = Modifier
                    .padding(start = 6.dp)
                    .scalingClickable {
                        showDialog.value = DialogType.Sensor
                    }
                    .background(
                        color = LocalTheme.current.colors.backgroundDark,
                        shape = LocalTheme.current.shapes.rectangularActionShape
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                text = "${experiment.data.activeSensors.size}/${availableSensors.value.size} registered",
                style = LocalTheme.current.styles.category
            )
        }


        Spacer(Modifier.height(48.dp))
    }
}

@Composable
private fun DisplayFrequencyDialog(
    initialFrequency: ExperimentIO.DisplayFrequency,
    onConfirm: (ExperimentIO.DisplayFrequency) -> Unit,
    onDismissRequest: () -> Unit
) {
    val missFocusRequester = remember { FocusRequester() }
    val selectedFrequency = remember(initialFrequency) {
        mutableStateOf(initialFrequency)
    }

    AlertDialog(
        modifier = Modifier
            .focusRequester(missFocusRequester)
            .focusable()
            .animateContentSize(),
        title = "Select display frequency",
        intrinsicContent = false,
        additionalContent = {
            Row(
                modifier = Modifier.scalingClickable(hoverEnabled = false, scaleInto = .95f) {
                    selectedFrequency.value = ExperimentIO.DisplayFrequency.BeginEnd
                    missFocusRequester.requestFocus()
                },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RadioButton(
                    colors = LocalTheme.current.styles.radioButtonColors,
                    selected = selectedFrequency.value == ExperimentIO.DisplayFrequency.BeginEnd,
                    onClick = {
                        selectedFrequency.value = ExperimentIO.DisplayFrequency.BeginEnd
                        missFocusRequester.requestFocus()
                    }
                )
                Text(
                    text = "Start + end",
                    style = LocalTheme.current.styles.regular
                )
            }
            Row(
                modifier = Modifier.scalingClickable(hoverEnabled = false, scaleInto = .95f) {
                    selectedFrequency.value = ExperimentIO.DisplayFrequency.Permanent
                    missFocusRequester.requestFocus()
                },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RadioButton(
                    colors = LocalTheme.current.styles.radioButtonColors,
                    selected = selectedFrequency.value == ExperimentIO.DisplayFrequency.Permanent,
                    onClick = {
                        selectedFrequency.value = ExperimentIO.DisplayFrequency.Permanent
                        missFocusRequester.requestFocus()
                    }
                )
                Text(
                    text = "All the time",
                    style = LocalTheme.current.styles.regular
                )
            }
            Row(
                modifier = Modifier.scalingClickable(hoverEnabled = false, scaleInto = .95f) {
                    selectedFrequency.value = ExperimentIO.DisplayFrequency.Constant(5)
                },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val focusRequester = remember { FocusRequester() }
                val isFocused = rememberSaveable(initialFrequency) {
                    mutableStateOf(false)
                }
                val secondsState = remember(initialFrequency) {
                    TextFieldState(
                        initialText = (initialFrequency as? ExperimentIO.DisplayFrequency.Constant)?.delaySeconds?.toString() ?: "5"
                    )
                }

                LaunchedEffect(isFocused.value) {
                    if (isFocused.value) {
                        selectedFrequency.value = ExperimentIO.DisplayFrequency.Constant(
                            secondsState.text.toString().toLongOrNull() ?: 5L
                        )
                    }
                }
                LaunchedEffect(secondsState.text) {
                    if (selectedFrequency.value is ExperimentIO.DisplayFrequency.Constant) {
                        selectedFrequency.value = ExperimentIO.DisplayFrequency.Constant(
                            secondsState.text.toString().toLongOrNull() ?: 5L
                        )
                    }
                }

                RadioButton(
                    colors = LocalTheme.current.styles.radioButtonColors,
                    selected = selectedFrequency.value is ExperimentIO.DisplayFrequency.Constant,
                    onClick = {
                        focusRequester.requestFocus()
                    }
                )
                CustomTextField(
                    backgroundColor = LocalTheme.current.colors.backgroundLight,
                    state = secondsState,
                    lineLimits = TextFieldLineLimits.SingleLine,
                    hint = "Every X",
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    focusRequester = focusRequester,
                    inputTransformation = InputTransformation.byValue { _, proposed ->
                        proposed.replace(Regex("[^0-9]"), "")
                    },
                    isFocused = isFocused,
                    trailingIcon = {
                        Text(
                            text = "seconds",
                            style = LocalTheme.current.styles.regular.copy(
                                color = LocalTheme.current.colors.disabled
                            )
                        )
                    }
                )
            }
        },
        confirmButtonState = ButtonState(
            text = "Confirm",
            onClick = {
                onConfirm(selectedFrequency.value)
            }
        ),
        onDismissRequest = onDismissRequest
    )
}

@Composable
private fun ChoiceBehaviorDialog(
    initialBehavior: ExperimentIO.ChoiceBehavior,
    onConfirm: (ExperimentIO.ChoiceBehavior) -> Unit,
    onDismissRequest: () -> Unit
) {
    val selectedBehavior = remember(initialBehavior) {
        mutableStateOf(initialBehavior)
    }

    AlertDialog(
        title = "Select choice behavior",
        intrinsicContent = false,
        additionalContent = {
            ExperimentIO.ChoiceBehavior.entries.forEach { behavior ->
                Row(
                    modifier = Modifier.scalingClickable(hoverEnabled = false, scaleInto = .95f) {
                        selectedBehavior.value = behavior
                    },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RadioButton(
                        colors = LocalTheme.current.styles.radioButtonColors,
                        selected = selectedBehavior.value == behavior,
                        onClick = {
                            selectedBehavior.value = behavior
                        }
                    )
                    Text(
                        text = when (behavior) {
                            ExperimentIO.ChoiceBehavior.SingleChoice -> "single-choice"
                            ExperimentIO.ChoiceBehavior.MultiChoice -> "multi-choice"
                            ExperimentIO.ChoiceBehavior.OrderedChoice -> "ordered-choice"
                        },
                        style = LocalTheme.current.styles.regular
                    )
                }
            }
        },
        confirmButtonState = ButtonState(
            text = "Confirm",
            onClick = {
                onConfirm(selectedBehavior.value)
            }
        ),
        onDismissRequest = onDismissRequest
    )
}

@Composable
private fun SensorSelectionDialog(
    model: ExperimentModel,
    experiment: ExperimentIO,
    onDismissRequest: () -> Unit
) {
    val availableSensors = model.availableSensors.collectAsState()
    val activeSensors = remember(experiment.uid) {
        mutableStateListOf(*experiment.activeSensors.toTypedArray())
    }

    AlertDialog(
        title = "Select sensors",
        intrinsicContent = false,
        additionalContent = {
            LazyColumn {
                sensorList(
                    availableSensors = availableSensors.value,
                    activeSensors = activeSensors.map { it.uid },
                    model = model,
                    onActivation = { sensor, delay ->
                        if (!activeSensors.removeAll { it.uid == sensor.uid }) {
                            activeSensors.add(ExperimentIO.ActiveSensor(sensor.uid, delay))
                            model.addActiveSensors(
                                uid = experiment.uid,
                                sensor = sensor,
                                delay = delay
                            )
                        } else model.removeActiveSensors(experiment.uid, sensor)
                    }
                )
            }
        },
        onDismissRequest = onDismissRequest
    )
}
