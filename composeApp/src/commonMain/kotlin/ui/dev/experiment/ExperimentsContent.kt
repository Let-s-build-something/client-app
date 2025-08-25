package ui.dev.experiment

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.byValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import augmy.interactive.shared.ext.scalingClickable
import augmy.interactive.shared.ui.components.ComponentHeaderButton
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
import kotlin.uuid.ExperimentalUuidApi

@Composable
fun ExperimentContent(model: ExperimentModel = koinViewModel()) {
    val experiments = model.experiments.collectAsState()
    val activeExperiments = model.activeExperiments.collectAsState()

    val selectedExperiment = remember(model) {
        mutableStateOf<FullExperiment?>(null)
    }

    selectedExperiment.value?.let { experiment ->
        ExperimentBottomSheet(
            model = model,
            experiment = experiment,
            onDismissRequest = { selectedExperiment.value = null }
        )
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item(key = "title") {
            Text(
                modifier = Modifier
                    .padding(top = 16.dp)
                    .fillMaxWidth()
                    .background(color = LocalTheme.current.colors.appbarBackground)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                text = "Experiments",
                style = LocalTheme.current.styles.subheading.copy(
                    color = LocalTheme.current.colors.appbarContent
                )
            )
        }
        stickyHeader(key = "addNewButton") {
            ComponentHeaderButton(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 2.dp)
                    .fillMaxWidth(),
                text = "New experiment",
                endImageVector = Icons.Outlined.Add
            ) {
                val newExperiment = ExperimentIO(name = "Experiment ${experiments.value.size}")
                model.createExperiment(newExperiment)
                selectedExperiment.value = FullExperiment(newExperiment)
            }
        }
        items(
            items = experiments.value,
            key = { it.data.uid }
        ) { experiment ->
            Column(
                modifier = Modifier
                    .animateItem()
                    .padding(horizontal = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .scalingClickable(key = experiment.data.uid, scaleInto = .95f) {
                            selectedExperiment.value = experiment
                        }
                        .background(
                            color = LocalTheme.current.colors.backgroundLight,
                            shape = LocalTheme.current.shapes.rectangularActionShape
                        )
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = experiment.data.name,
                            style = LocalTheme.current.styles.category,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = experiment.sets.joinToString(", ") { it.name },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = LocalTheme.current.styles.regular
                        )
                        Text(
                            text = "Displayed " + when (experiment.data.displayFrequency) {
                                ExperimentIO.DisplayFrequency.BeginEnd -> "at the start + end"
                                ExperimentIO.DisplayFrequency.Permanent -> "all the time"
                                is ExperimentIO.DisplayFrequency.Constant -> "every ${experiment.data.displayFrequency.delaySeconds}s"
                            },
                            style = LocalTheme.current.styles.regular
                        )
                        Text(
                            text = when (experiment.data.choiceBehavior) {
                                ExperimentIO.ChoiceBehavior.SingleChoice -> "single-choice"
                                ExperimentIO.ChoiceBehavior.MultiChoice -> "multi-choice"
                                ExperimentIO.ChoiceBehavior.OrderedChoice -> "ordered-choice"
                            },
                            style = LocalTheme.current.styles.regular
                        )
                    }

                    val isActive = activeExperiments.value.contains(experiment.data.uid)
                    Text(
                        text = if (isActive) "Active" else "Inactive",
                        style = LocalTheme.current.styles.regular.copy(
                            color = if (isActive) {
                                LocalTheme.current.colors.brandMain
                            } else SharedColors.RED_ERROR_50
                        )
                    )
                    AnimatedVisibility(isActive) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .requiredSize(24.dp),
                            color = LocalTheme.current.colors.disabled,
                            trackColor = LocalTheme.current.colors.disabledComponent
                        )
                    }
                }
            }
        }
        item(key = "bottomPadding") {
            Spacer(Modifier.height(50.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalUuidApi::class)
@Composable
private fun ExperimentBottomSheet(
    model: ExperimentModel,
    experiment: FullExperiment,
    onDismissRequest: () -> Unit
) {
    val cancellableScope = rememberCoroutineScope()
    val activeExperiments = model.activeExperiments.collectAsState()

    val showDeleteConfirmation = remember(experiment.data.uid) {
        mutableStateOf(false)
    }
    val showSetSelection = remember(experiment.data.uid) {
        mutableStateOf(false)
    }
    val showFrequencySelection = remember(experiment.data.uid) {
        mutableStateOf(false)
    }
    val showBehaviorSelection = remember(experiment.data.uid) {
        mutableStateOf(false)
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

    LaunchedEffect(nameState.text) {
        cancellableScope.coroutineContext.cancelChildren()
        cancellableScope.launch {
            delay(DELAY_BETWEEN_TYPING_SHORT)
            model.changeNameOf(experiment.data.uid, nameState.text)
        }
    }

    when {
        showDeleteConfirmation.value -> {
            AlertDialog(
                title = "Delete experiment",
                message = AnnotatedString("Are you sure you want to delete this experiment?"),
                dismissButtonState = ButtonState("Dismiss"),
                confirmButtonState = ButtonState(
                    text = "Confirm",
                    onClick = {
                        model.deleteExperiment(experiment.data.uid)
                        showDeleteConfirmation.value = false
                        onDismissRequest()
                    }
                ),
                onDismissRequest = { showDeleteConfirmation.value = false }
            )
        }
        showSetSelection.value -> {
            SetListDialog(
                model = model,
                experiment = experiment,
                selectedSet = selectedSet.value,
                onConfirm = {
                    selectedSet.value = it
                    model.changeSetOf(experiment.data.uid, it.uid)
                    showSetSelection.value = false
                },
                onDismissRequest = { showSetSelection.value = false }
            )
        }
        showFrequencySelection.value -> {
            DisplayFrequencyDialog(
                initialFrequency = selectedFrequency.value,
                onConfirm = {
                    model.changeFrequencyOf(experiment.data.uid, it)
                    selectedFrequency.value = it
                },
                onDismissRequest = { showFrequencySelection.value = false }
            )
        }
        showBehaviorSelection.value -> ChoiceBehaviorDialog(
            initialBehavior = selectedBehavior.value,
            onConfirm = {
                model.changeBehaviorOf(experiment.data.uid, it)
                selectedBehavior.value = it
            },
            onDismissRequest = { showBehaviorSelection.value = false }
        )
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
                        showDeleteConfirmation.value = true
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
                        showSetSelection.value = true
                    }
                    .background(
                        color = LocalTheme.current.colors.backgroundLight,
                        shape = LocalTheme.current.shapes.rectangularActionShape
                    )
                    .padding(horizontal = 6.dp, vertical = 4.dp),
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
                        showFrequencySelection.value = true
                    }
                    .background(
                        color = LocalTheme.current.colors.backgroundLight,
                        shape = LocalTheme.current.shapes.rectangularActionShape
                    )
                    .padding(horizontal = 6.dp, vertical = 4.dp),
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
                        showBehaviorSelection.value = true
                    }
                    .background(
                        color = LocalTheme.current.colors.backgroundLight,
                        shape = LocalTheme.current.shapes.rectangularActionShape
                    )
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                text = when (selectedBehavior.value) {
                    ExperimentIO.ChoiceBehavior.SingleChoice -> "single-choice"
                    ExperimentIO.ChoiceBehavior.MultiChoice -> "multi-choice"
                    ExperimentIO.ChoiceBehavior.OrderedChoice -> "ordered-choice"
                },
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
    onConfirm: (ExperimentIO.ChoiceBehavior,) -> Unit,
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
