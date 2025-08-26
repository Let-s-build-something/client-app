package ui.dev.experiment

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import augmy.interactive.shared.ext.scalingClickable
import augmy.interactive.shared.ui.components.ComponentHeaderButton
import augmy.interactive.shared.ui.components.ContrastHeaderButton
import augmy.interactive.shared.ui.components.LoadingHeaderButton
import augmy.interactive.shared.ui.components.MultiChoiceSwitchMinimalistic
import augmy.interactive.shared.ui.components.input.CustomTextField
import augmy.interactive.shared.ui.components.rememberMultiChoiceState
import augmy.interactive.shared.ui.theme.LocalTheme
import augmy.interactive.shared.ui.theme.SharedColors
import data.io.base.BaseResponse
import data.io.experiment.ExperimentIO
import data.io.experiment.FullExperiment
import data.sensor.SensorDelay
import io.github.vinceglb.filekit.dialogs.compose.rememberFileSaverLauncher
import org.koin.compose.viewmodel.koinViewModel
import ui.dev.DeveloperConsoleModel

@Composable
fun ExperimentContent(
    developerModel: DeveloperConsoleModel,
    model: ExperimentModel = koinViewModel()
) {
    val experiments = model.experiments.collectAsState()
    val activeExperiments = model.activeExperiments.collectAsState()

    val selectedExperiment = remember(model) {
        mutableStateOf<FullExperiment?>(null)
    }

    selectedExperiment.value?.let { experiment ->
        ExperimentBottomSheet(
            experiment = experiment,
            onDismissRequest = { selectedExperiment.value = null }
        )
    }

    LaunchedEffect(experiments.value) {
        if (selectedExperiment.value != null) {
            selectedExperiment.value = experiments.value.find {
                it.data.uid == selectedExperiment.value?.data?.uid
            }
        }
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item(key = "streamingSection") {
            StreamingSection(model = developerModel)
        }
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
                        .scalingClickable(
                            key = experiment.data.toString(),
                            scaleInto = .95f
                        ) {
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

@Composable
private fun StreamingSection(model: DeveloperConsoleModel) {
    val streamingUrlResponse = model.streamingUrlResponse.collectAsState()

    val streamingUrlState = remember(model) {
        TextFieldState(initialText = model.streamingUrl)
    }

    val filePicker = rememberFileSaverLauncher(
        onResult = { filePicker ->
            model.setUpLocalStream(filePicker)
        }
    )

    Text(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = LocalTheme.current.colors.appbarBackground)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        text = "Data streaming",
        style = LocalTheme.current.styles.subheading.copy(
            color = LocalTheme.current.colors.appbarContent
        )
    )

    Row(
        modifier = Modifier.padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedVisibility(
            modifier = Modifier.weight(1f),
            visible = streamingUrlResponse.value !is BaseResponse.Success
        ) {
            CustomTextField(
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done
                ),
                onKeyboardAction = {
                    model.setupRemoteStream(streamingUrlState.text)
                },
                backgroundColor = LocalTheme.current.colors.backgroundLight,
                hint = "Server url",
                state = streamingUrlState,
                shape = LocalTheme.current.shapes.componentShape,
                errorText = (streamingUrlResponse.value as? BaseResponse.Error)?.message
            )
        }
        Crossfade(targetState = streamingUrlResponse.value) { response ->
            when(response) {
                is BaseResponse.Success -> {
                    ContrastHeaderButton(
                        text = "Stop remote stream",
                        endImageVector = Icons.Outlined.Stop,
                        contentColor = Color.White,
                        containerColor = SharedColors.RED_ERROR,
                        onClick = {
                            model.stopRemoteStream()
                        }
                    )
                }
                else -> {
                    LoadingHeaderButton(
                        text = "Stream",
                        isLoading = streamingUrlResponse.value is BaseResponse.Loading,
                        isEnabled = streamingUrlResponse.value !is BaseResponse.Loading,
                        endImageVector = Icons.Outlined.Check,
                        onClick = {
                            model.setupRemoteStream(streamingUrlState.text)
                        }
                    )
                }
            }
        }
        AnimatedVisibility(streamingUrlResponse.value is BaseResponse.Success) {
            val selectedDelayIndex = rememberSaveable {
                mutableStateOf(model.remoteStreamDelay.ordinal)
            }

            Row(
                modifier = Modifier.padding(start = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Upload by: ",
                    style = LocalTheme.current.styles.regular
                )
                MultiChoiceSwitchMinimalistic(
                    modifier = Modifier.padding(start = 6.dp),
                    state = rememberMultiChoiceState(
                        selectedTabIndex = selectedDelayIndex,
                        items = SensorDelay.entries.map { it.name }.toMutableList()
                    ),
                    onClick = { index ->
                        selectedDelayIndex.value = index
                        model.remoteStreamDelay =  SensorDelay.entries[index]
                    },
                    onItemCreation = { _, index, _ ->
                        Text(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp),
                            text = when(SensorDelay.entries[index]) {
                                SensorDelay.Slow -> 50
                                SensorDelay.Normal -> 20
                                SensorDelay.Fast -> 1
                            }.toString(),
                            style = LocalTheme.current.styles.category.copy(
                                textAlign = TextAlign.Center
                            )
                        )
                    }
                )
            }
        }
    }

    val localRunning = model.isLocalStreamRunning.collectAsState()
    Crossfade(
        modifier = Modifier.padding(top = 8.dp),
        targetState = localRunning.value
    ) {
        if (it) {
            ContrastHeaderButton(
                text = "Stop local stream",
                endImageVector = Icons.Outlined.Stop,
                contentColor = Color.White,
                containerColor = SharedColors.RED_ERROR,
                onClick = {
                    model.stopLocalStream()
                }
            )
        }else {
            ContrastHeaderButton(
                text = "Stream locally",
                endImageVector = Icons.Outlined.Folder,
                contentColor = LocalTheme.current.colors.tetrial,
                containerColor = LocalTheme.current.colors.brandMainDark,
                onClick = {
                    filePicker.launch(extension = "txt", suggestedName = "stream-sensory-augmy")
                }
            )
        }
    }

    val streamLines = model.streamLines.collectAsState()
    AnimatedVisibility(
        modifier = Modifier
            .padding(top = 8.dp)
            .fillMaxWidth(),
        visible = streamLines.value.isNotEmpty()
    ) {
        val state = rememberLazyListState()

        LaunchedEffect(streamLines.value.size) {
            state.animateScrollToItem(0)
        }

        LazyColumn(
            modifier = Modifier
                .requiredHeight(50.dp)
                .padding(horizontal = 8.dp),
            state = state,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            reverseLayout = true
        ) {
            items(items = streamLines.value) { line ->
                Text(
                    modifier = Modifier.animateItem(),
                    text = line,
                    style = LocalTheme.current.styles.regular.copy(
                        color = LocalTheme.current.colors.disabled
                    )
                )
            }
        }
    }
}
