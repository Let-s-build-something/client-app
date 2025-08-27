package ui.dev

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Deselect
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import augmy.composeapp.generated.resources.Res
import augmy.composeapp.generated.resources.button_confirm
import augmy.composeapp.generated.resources.button_dismiss
import augmy.interactive.shared.ext.scalingClickable
import augmy.interactive.shared.ui.components.BrandHeaderButton
import augmy.interactive.shared.ui.components.ContrastHeaderButton
import augmy.interactive.shared.ui.components.ErrorHeaderButton
import augmy.interactive.shared.ui.components.MultiChoiceSwitchMinimalistic
import augmy.interactive.shared.ui.components.ProgressPressableContainer
import augmy.interactive.shared.ui.components.dialog.AlertDialog
import augmy.interactive.shared.ui.components.dialog.ButtonState
import augmy.interactive.shared.ui.components.rememberMultiChoiceState
import augmy.interactive.shared.ui.theme.LocalTheme
import augmy.interactive.shared.ui.theme.SharedColors
import augmy.interactive.shared.utils.DateUtils.formatAs
import components.ScrollBarProgressIndicator
import data.sensor.HZ_SPEED_FAST
import data.sensor.HZ_SPEED_NORMAL
import data.sensor.HZ_SPEED_SLOW
import data.sensor.SensorEventListener
import io.github.vinceglb.filekit.dialogs.compose.rememberFileSaverLauncher
import kotlinx.datetime.LocalDateTime
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun BiometricContent(model: DeveloperConsoleModel) {
    DashboardSection(model)
}

@Composable
private fun DashboardSection(model: DeveloperConsoleModel) {
    val sensorListState = rememberLazyListState()
    val availableSensors = model.availableSensors.collectAsState()
    val activeSensors = model.activeSensors.collectAsState()

    LaunchedEffect(Unit) {
        model.requestAvailableSensors(null)
    }

    LazyColumn(
        modifier = Modifier.animateContentSize(),
        state = sensorListState,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text(
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .fillMaxWidth()
                        .background(color = LocalTheme.current.colors.appbarBackground)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    text = "Dashboard",
                    style = LocalTheme.current.styles.subheading.copy(
                        color = LocalTheme.current.colors.appbarContent
                    )
                )

                Text(
                    text = "${activeSensors.value.size}/${availableSensors.value.size} sensors registered",
                    style = LocalTheme.current.styles.regular
                )

                ScrollBarProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    state = sensorListState
                )
            }
        }
        stickyHeader {
            val showDialog = remember {
                mutableStateOf(false)
            }

            if(showDialog.value) {
                AlertDialog(
                    title = "Reset all",
                    dismissButtonState = ButtonState(text = stringResource(Res.string.button_dismiss)),
                    confirmButtonState = ButtonState(
                        text = stringResource(Res.string.button_confirm),
                        onClick = {
                            model.resetAllSensors()
                        }
                    ),
                    onDismissRequest = {
                        showDialog.value = false
                    }
                )
            }

            val filePicker = rememberFileSaverLauncher(
                onResult = { file ->
                    model.exportData(file)
                }
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ErrorHeaderButton(
                    text = "Reset all",
                    contentPadding = PaddingValues(vertical = 6.dp, horizontal = 10.dp),
                    endImageVector = Icons.Outlined.CleaningServices,
                    onClick = {
                        showDialog.value = true
                    }
                )
                ErrorHeaderButton(
                    text = "Deselect",
                    contentPadding = PaddingValues(vertical = 6.dp, horizontal = 10.dp),
                    endImageVector = Icons.Outlined.Deselect,
                    onClick = {
                        model.unregisterAllSensors()
                    }
                )
                ContrastHeaderButton(
                    text = "Select all",
                    contentPadding = PaddingValues(vertical = 6.dp, horizontal = 10.dp),
                    contentColor = LocalTheme.current.colors.brandMainDark,
                    containerColor = LocalTheme.current.colors.brandMain,
                    endImageVector = Icons.Outlined.SelectAll,
                    onClick = {
                        model.registerAllSensors()
                    }
                )
                BrandHeaderButton(
                    text = "Export",
                    endImageVector = Icons.Outlined.Download,
                    onClick = {
                        filePicker.launch(extension = "txt", suggestedName = "log-sensory-augmy")
                    }
                )
            }
        }
        sensorList(
            availableSensors = availableSensors.value,
            activeSensors = activeSensors.value,
            model = model
        )
        item {
            Spacer(Modifier.height(120.dp))
        }
    }
}

fun LazyListScope.sensorList(
    availableSensors: List<SensorEventListener>,
    activeSensors: List<String>,
    model: DeveloperConsoleModel,
    onActivation: (SensorEventListener, Int) -> Unit = { sensor, hz ->
        if (activeSensors.contains(sensor.uid)) {
            model.unregisterSensor(sensor)
        }else model.registerSensor(
            sensor = sensor,
            hz = hz
        )
    }
) {
    items(
        items = availableSensors,
        key = { it.uid }
    ) { sensor ->
        val delayItems = mutableListOf(
            HZ_SPEED_SLOW.toString(),
            HZ_SPEED_NORMAL.toString(),
            HZ_SPEED_FAST.toString()
        )
        val selectedDelayIndex = rememberSaveable(sensor.uid) {
            mutableStateOf(
                delayItems.indexOf(sensor.hzSpeed.toString()).takeIf { it != -1 } ?: 1
            )
        }
        val showSensorDialog = remember {
            mutableStateOf<SensorEventListener?>(null)
        }

        showSensorDialog.value?.let { sensor ->
            val data = sensor.data.collectAsState()

            AlertDialog(
                title = sensor.name + if (!sensor.description.isNullOrBlank()) " (${sensor.description})" else "",
                dismissButtonState = ButtonState(text = stringResource(Res.string.button_dismiss)),
                icon = Icons.Outlined.History,
                onDismissRequest = {
                    showSensorDialog.value = null
                },
                intrinsicContent = false,
                additionalContent = {
                    LazyColumn(modifier = Modifier.animateContentSize()) {
                        items(items = data.value) { record ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateItem(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                record.values?.let {
                                    SelectionContainer {
                                        Text(
                                            text = it.joinToString(separator = ", "),
                                            style = LocalTheme.current.styles.category
                                        )
                                    }
                                }
                                record.uiValues?.let {
                                    val text = buildAnnotatedString {
                                        it.forEach { window ->
                                            withStyle(LocalTheme.current.styles.category.toSpanStyle()) {
                                                append("\n${window.key}")
                                            }
                                            append(": ${window.value}")
                                        }
                                    }

                                    SelectionContainer {
                                        Text(
                                            text = text,
                                            style = LocalTheme.current.styles.regular
                                        )
                                    }
                                }
                                SelectionContainer {
                                    Text(
                                        text = "Timestamp: ${LocalDateTime.parse(record.timestamp).formatAs("HH:mm:ss")}",
                                        style = LocalTheme.current.styles.regular
                                    )
                                }

                                HorizontalDivider(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = LocalTheme.current.colors.disabled
                                )
                            }
                        }
                    }
                }
            )
        }

        val data = sensor.data.collectAsState()

        Column {
            Row(
                modifier = Modifier.animateItem(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ProgressPressableContainer(
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .requiredSize(36.dp),
                    onFinish = {
                        sensor.data.value = listOf()
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

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .scalingClickable(scaleInto = .95f) {
                            showSensorDialog.value = sensor
                        },
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        modifier = Modifier.padding(start = 8.dp),
                        text = sensor.name + if (!sensor.description.isNullOrBlank()) " (${sensor.description})" else "",
                        style = LocalTheme.current.styles.category
                    )
                    sensor.maximumRange?.let {
                        Text(
                            modifier = Modifier.padding(start = 12.dp),
                            text = "Maximum range: $it",
                            style = LocalTheme.current.styles.regular
                        )
                    }
                    sensor.resolution?.let {
                        Text(
                            modifier = Modifier.padding(start = 12.dp),
                            text = "Resolution: $it",
                            style = LocalTheme.current.styles.regular
                        )
                    }
                    Text(
                        modifier = Modifier.padding(start = 12.dp),
                        text = "Collected: ${data.value.size}",
                        style = LocalTheme.current.styles.regular
                    )
                    Text(
                        modifier = Modifier.padding(start = 12.dp),
                        text = "Last record: ${data.value.firstOrNull()?.let { value ->
                            value.values?.toList() ?: value.uiValues
                        }}",
                        style = LocalTheme.current.styles.regular
                    )

                    Row(
                        modifier = Modifier
                            .padding(bottom = 6.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Speed: ",
                            style = LocalTheme.current.styles.regular
                        )
                        MultiChoiceSwitchMinimalistic(
                            state = rememberMultiChoiceState(
                                selectedTabIndex = selectedDelayIndex,
                                items = delayItems
                            ),
                            onClick = { index ->
                                selectedDelayIndex.value = index
                                model.changeSensorDelay(sensor, delayItems[index].toIntOrNull() ?: HZ_SPEED_NORMAL)
                            },
                            onItemCreation = { _, index, _ ->
                                Text(
                                    modifier = Modifier.fillMaxWidth(),
                                    text = delayItems[index],
                                    style = LocalTheme.current.styles.category.copy(
                                        textAlign = TextAlign.Center
                                    )
                                )
                            }
                        )
                    }
                }

                Switch(
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .padding(end = 16.dp),
                    colors = LocalTheme.current.styles.switchColorsDefault,
                    onCheckedChange = {
                        onActivation(sensor, delayItems[selectedDelayIndex.value].toIntOrNull() ?: HZ_SPEED_NORMAL)
                    },
                    checked = activeSensors.contains(sensor.uid)
                )
            }

            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                color = LocalTheme.current.colors.disabled
            )
        }
    }
}
