package ui.dev.experiment

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import augmy.interactive.shared.ui.base.LocalScreenSize
import augmy.interactive.shared.ui.components.ComponentHeaderButton
import augmy.interactive.shared.ui.components.MinimalisticIcon
import augmy.interactive.shared.ui.components.ProgressPressableContainer
import augmy.interactive.shared.ui.components.dialog.AlertDialog
import augmy.interactive.shared.ui.components.dialog.ButtonState
import augmy.interactive.shared.ui.components.input.CustomTextField
import augmy.interactive.shared.ui.components.input.DELAY_BETWEEN_TYPING_SHORT
import augmy.interactive.shared.ui.theme.LocalTheme
import augmy.interactive.shared.ui.theme.SharedColors
import data.io.experiment.ExperimentQuestionnaire
import data.io.experiment.ExperimentSetValue
import data.io.experiment.FullExperiment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Composable
fun QuestionnaireListDialog(
    model: ExperimentModel,
    experiment: FullExperiment,
    selectedSet: ExperimentQuestionnaire?,
    onConfirm: (ExperimentQuestionnaire) -> Unit,
    onDismissRequest: () -> Unit
) {
    val cancellableScope = rememberCoroutineScope()
    val setPick = remember(experiment.data.uid) {
        mutableStateOf(selectedSet)
    }
    val sets = model.sets.collectAsState()

    LaunchedEffect(sets.value) {
        if (setPick.value != null) setPick.value = sets.value.find {
            it.uid == setPick.value?.uid
        }
    }

    AlertDialog(
        title = "Select a questionnaire",
        intrinsicContent = false,
        additionalContent = {
            AnimatedVisibility(setPick.value != null) {
                setPick.value?.let { set ->
                    val values = remember(set.uid) {
                        mutableStateListOf(*set.values.toTypedArray())
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = LocalTheme.current.colors.backgroundLight,
                                shape = LocalTheme.current.shapes.rectangularActionShape
                            )
                            .border(
                                width = .5.dp,
                                color = LocalTheme.current.colors.disabled,
                                shape = LocalTheme.current.shapes.rectangularActionShape
                            )
                            .animateContentSize()
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateContentSize(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val isInEdit = remember(set.uid) { mutableStateOf(false) }
                            val nameState = remember(set.uid) {
                                TextFieldState(set.name)
                            }

                            LaunchedEffect(nameState.text) {
                                cancellableScope.launch {
                                    delay(DELAY_BETWEEN_TYPING_SHORT)
                                    model.updateSet(set.copy(name = nameState.text.toString()))
                                }
                            }

                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Crossfade(
                                    modifier = Modifier.weight(1f),
                                    targetState = isInEdit.value
                                ) { editing ->
                                    if (editing) {
                                        CustomTextField(
                                            state = nameState,
                                            onKeyboardAction = {
                                                isInEdit.value = false
                                            }
                                        )
                                    } else {
                                        ExperimentSet(
                                            set = set.copy(values = listOf()),
                                            size = values.size,
                                            setPick = null
                                        )
                                    }
                                }
                                Crossfade(isInEdit.value) { editing ->
                                    MinimalisticIcon(
                                        imageVector = if (editing) Icons.Outlined.Check else Icons.Outlined.Edit,
                                        contentDescription = null,
                                        tint = LocalTheme.current.colors.secondary,
                                        onTap = {
                                            isInEdit.value = !editing
                                        }
                                    )
                                }

                                Spacer(Modifier.width(8.dp))
                                ProgressPressableContainer(
                                    modifier = Modifier.requiredSize(28.dp),
                                    onFinish = {
                                        model.removeSet(set.uid)
                                        setPick.value = null
                                    },
                                    trackColor = LocalTheme.current.colors.disabled,
                                    progressColor = SharedColors.RED_ERROR
                                ) {
                                    Icon(
                                        modifier = Modifier.size(24.dp),
                                        imageVector = Icons.Outlined.Delete,
                                        contentDescription = null,
                                        tint = LocalTheme.current.colors.secondary
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = set.randomize,
                                    onCheckedChange = { value ->
                                        model.updateSet(set.copy(randomize = value))
                                    },
                                    colors = LocalTheme.current.styles.checkBoxColorsDefault
                                )
                                Text(
                                    text = "Randomize",
                                    style = LocalTheme.current.styles.regular
                                )
                            }
                        }

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp, alignment = Alignment.CenterVertically)
                        ) {
                            item(key = "addNew") {
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    ComponentHeaderButton(
                                        text = "New value",
                                        modifier = Modifier.fillMaxWidth(.6f),
                                        endImageVector = Icons.Outlined.Add
                                    ) {
                                        values.add(0, ExperimentSetValue(""))
                                        model.updateSet(set.copy(values = values))
                                    }
                                }
                            }
                            items(
                                count = values.size,
                                key = { values.getOrNull(it)?.uid ?: Uuid.random().toString() }
                            ) { index ->
                                val value = values.getOrNull(index)
                                val valueState = remember(value?.uid) {
                                    TextFieldState(initialText = value?.value ?: "")
                                }

                                LaunchedEffect(valueState.text) {
                                    if (valueState.text != value?.value) {
                                        value?.copy(value = valueState.text.toString())?.let { newValue ->
                                            values[index] = newValue
                                            model.updateSet(set.copy(values = values))
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .animateItem(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    CustomTextField(
                                        modifier = Modifier.weight(1f),
                                        backgroundColor = LocalTheme.current.colors.backgroundDark,
                                        paddingValues = PaddingValues(6.dp),
                                        state = valueState,
                                        textStyle = LocalTheme.current.styles.title.copy(
                                            fontSize = 32.sp
                                        )
                                    )

                                    ProgressPressableContainer(
                                        modifier = Modifier.requiredSize(28.dp),
                                        onFinish = {
                                            values.removeAt(index)
                                            model.updateSet(set.copy(values = values))
                                        },
                                        trackColor = LocalTheme.current.colors.disabled,
                                        progressColor = SharedColors.RED_ERROR
                                    ) {
                                        Icon(
                                            modifier = Modifier.size(24.dp),
                                            imageVector = Icons.Outlined.Delete,
                                            contentDescription = null,
                                            tint = LocalTheme.current.colors.secondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            val sets = model.sets.collectAsState()

            LaunchedEffect(sets.value) {
                if (setPick.value != null) {
                    withContext(Dispatchers.Default) {
                        setPick.value = sets.value.find { it.uid == setPick.value?.uid }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .heightIn(max = (LocalScreenSize.current.height * .5f).dp)
                    .animateContentSize()
            ) {
                stickyHeader(key = "addNewButton") {
                    ComponentHeaderButton(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 6.dp)
                            .fillMaxWidth(),
                        text = "New questionnaire",
                        endImageVector = Icons.Outlined.Add
                    ) {
                        val newSet = ExperimentQuestionnaire(name = "Questionnaire ${sets.value.size}")
                        model.createSet(newSet)
                        setPick.value = newSet
                    }
                }
                items(
                    items = setPick.value?.let { picked ->
                        sets.value.filter { it.uid != picked.uid }
                    } ?: sets.value,
                    key = { it.uid }
                ) { set ->
                    ExperimentSet(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem(),
                        set = set,
                        size = set.values.size,
                        setPick = setPick
                    )
                }
            }
        },
        confirmButtonState = ButtonState(
            text = "Confirm",
            onClick = {
                setPick.value?.let { onConfirm(it) }
            }
        ),
        onDismissRequest = onDismissRequest
    )
}