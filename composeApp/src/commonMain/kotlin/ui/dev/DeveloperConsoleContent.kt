package ui.dev

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.WavingHand
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.BottomSheetScaffoldState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import augmy.interactive.shared.ext.scalingClickable
import augmy.interactive.shared.ui.base.LocalDeviceType
import augmy.interactive.shared.ui.base.LocalScreenSize
import augmy.interactive.shared.ui.components.BrandHeaderButton
import augmy.interactive.shared.ui.theme.LocalTheme
import data.io.experiment.ExperimentIO
import data.io.experiment.ExperimentSetValue
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.context.loadKoinModules
import ui.dev.experiment.ExperimentContent

private enum class ConsoleSection(val imageVector: ImageVector) {
    Logger(Icons.AutoMirrored.Outlined.ReceiptLong),
    Experiment(Icons.Outlined.Science),
    Sensors(Icons.Outlined.WavingHand);

    override fun toString(): String {
        return when(this) {
            Logger -> "Logs"
            Experiment -> "Experiments"
            Sensors -> "Depth"
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperHolderLayout(
    modifier: Modifier = Modifier,
    appContent: @Composable () -> Unit = {}
) {
    loadKoinModules(developerConsoleModule)
    val model: DeveloperConsoleModel = koinViewModel(key = "generic-shared")

    val isCompact = LocalDeviceType.current == WindowWidthSizeClass.Compact
    val experimentsToShow = model.experimentsToShow.collectAsState()
    val sheetState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            initialValue = SheetValue.Expanded,
            skipHiddenState = experimentsToShow.value.isNotEmpty()
        )
    )

    LaunchedEffect(experimentsToShow.value) {
        if (experimentsToShow.value.isNotEmpty()) {
            sheetState.bottomSheetState.expand()
        } else sheetState.bottomSheetState.hide()
    }

    OverlayBottomSheet(
        modifier = modifier,
        sheetState = sheetState,
        sheetContent = {
            ExperimentSheetContent(model)
        },
        content = {
            if (isCompact) {
                Column(modifier = Modifier.background(LocalTheme.current.colors.backgroundDark)) {
                    DeveloperConsoleContent(
                        modifier = modifier,
                        model = model,
                        isCompact = isCompact
                    )
                    appContent()
                }
            } else {
                Row(modifier = Modifier.background(LocalTheme.current.colors.backgroundDark)) {
                    DeveloperConsoleContent(
                        modifier = modifier,
                        model = model,
                        isCompact = isCompact
                    )
                    appContent()
                }
            }
        }
    )
}

@Composable
private fun DeveloperConsoleContent(
    modifier: Modifier = Modifier,
    model: DeveloperConsoleModel,
    isCompact: Boolean
) {
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current
    val screenSize = LocalScreenSize.current

    val consoleSize = model.developerConsoleSize.collectAsState()
    val selectedSectionIndex = rememberSaveable {
        mutableStateOf(0)
    }

    val sectionPagerState = rememberPagerState(
        initialPage = selectedSectionIndex.value,
        pageCount = {
            ConsoleSection.entries.size
        }
    )

    LaunchedEffect(selectedSectionIndex.value) {
        sectionPagerState.animateScrollToPage(selectedSectionIndex.value)
    }

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    focusManager.clearFocus()
                })
            }
            .then(
                if(isCompact) {
                    Modifier
                        .fillMaxWidth()
                        .height(consoleSize.value.coerceIn(
                            minimumValue = 32f,
                            maximumValue = screenSize.height * 0.8f
                        ).dp)
                } else Modifier
                    .fillMaxHeight()
                    .width(consoleSize.value.coerceIn(
                        minimumValue = 32f,
                        maximumValue = screenSize.width * 0.8f
                    ).dp)
            )
            .background(color = LocalTheme.current.colors.backgroundDark),
        contentAlignment = Alignment.TopStart
    ) {
        Box(
            modifier = (if(isCompact) {
                Modifier.fillMaxWidth(.3f)
            } else Modifier.fillMaxHeight(.15f))
                .draggable(
                    orientation = if(isCompact) Orientation.Vertical else Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        coroutineScope.launch {
                            model.changeDeveloperConsole(
                                size = model.developerConsoleSize.value + with(density) { delta.toDp().value }
                            )
                        }
                    }
                )
                .padding(12.dp)
                .background(
                    color = LocalTheme.current.colors.secondary,
                    shape = RoundedCornerShape(12.dp)
                )
                .align(if(isCompact) Alignment.BottomCenter else Alignment.CenterEnd)
                .padding(4.dp)
                .zIndex(100f)
        )

        if(consoleSize.value > ((if(isCompact) screenSize.height else screenSize.width) * 0.2f)) {
            Row(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .padding(vertical = 16.dp, horizontal = 2.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ConsoleSection.entries.forEach { section ->
                        SectionIndicator(
                            isSelected = section.ordinal == selectedSectionIndex.value,
                            imageVector = section.imageVector,
                            onClick = {
                                selectedSectionIndex.value = section.ordinal
                            }
                        )
                    }
                }

                VerticalPager(
                    modifier = Modifier.padding(start = 4.dp),
                    state = sectionPagerState,
                    userScrollEnabled = false
                ) { index ->
                    val section = ConsoleSection.entries[index]

                    Column(modifier = Modifier.fillMaxSize()) {
                        Text(
                            modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
                            text = section.name,
                            style = LocalTheme.current.styles.heading
                        )
                        when(section) {
                            ConsoleSection.Logger -> LoggerContent(model = model, isCompact = isCompact)
                            ConsoleSection.Experiment -> ExperimentContent(developerModel = model)
                            ConsoleSection.Sensors -> BiometricContent(model = model)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionIndicator(
    modifier: Modifier = Modifier,
    imageVector: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val tint = animateColorAsState(
        targetValue = if(isSelected) {
            LocalTheme.current.colors.brandMain
        }else LocalTheme.current.colors.disabled
    )

    Icon(
        modifier = modifier
            .size(52.dp)
            .scalingClickable {
                onClick()
            }
            .padding(6.dp)
            .padding(4.dp),
        imageVector = imageVector,
        tint = tint.value,
        contentDescription = null
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColumnScope.ExperimentSheetContent(model: DeveloperConsoleModel) {
    val experimentToShow = model.experimentsToShow.collectAsState()

    experimentToShow.value.firstOrNull().let { experiment ->
        if (experiment != null) {
            val set = experiment.sets.firstOrNull()
            val values = if (set?.randomize == true) set.values.shuffled() else set?.values
            if (values.isNullOrEmpty()) {
                return@let
            }

            val selectedValues = remember {
                mutableStateSetOf<ExperimentSetValue>()
            }
            val customValueState = remember {
                TextFieldState()
            }


            Text(
                modifier = Modifier.padding(horizontal = 8.dp),
                text = experiment.data.fullName,
                style = LocalTheme.current.styles.subheading
            )

            LazyColumn(
                modifier = Modifier
                    .padding(start = 12.dp, top = 12.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(
                    items = values,
                    key = { it.uid }
                ) { value ->
                    val onClick = {
                        when (experiment.data.choiceBehavior) {
                            ExperimentIO.ChoiceBehavior.SingleChoice -> {
                                selectedValues.clear()
                                selectedValues.add(value)
                            }
                            ExperimentIO.ChoiceBehavior.MultiChoice,
                            ExperimentIO.ChoiceBehavior.OrderedChoice -> {
                                if (selectedValues.contains(value)) {
                                    selectedValues.remove(value)
                                } else selectedValues.add(value)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .padding(horizontal = 6.dp)
                            .scalingClickable(key = value.uid, scaleInto = .95f) {
                                onClick()
                            }
                            .fillMaxWidth()
                            .background(
                                color = LocalTheme.current.colors.backgroundLight,
                                shape = LocalTheme.current.shapes.rectangularActionShape
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val isChecked = selectedValues.contains(value)

                        when (experiment.data.choiceBehavior) {
                            ExperimentIO.ChoiceBehavior.SingleChoice -> {
                                RadioButton(
                                    selected = isChecked,
                                    onClick = { onClick() },
                                    colors = LocalTheme.current.styles.radioButtonColors
                                )
                            }
                            ExperimentIO.ChoiceBehavior.MultiChoice -> {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { onClick() },
                                    colors = LocalTheme.current.styles.checkBoxColorsDefault
                                )
                            }
                            ExperimentIO.ChoiceBehavior.OrderedChoice -> {
                                Box(modifier = Modifier.width(48.dp)) {
                                    this@Row.AnimatedVisibility(isChecked) {
                                        Text(
                                            text = (selectedValues.indexOf(value) + 1).toString(),
                                            style = LocalTheme.current.styles.category.copy(
                                                fontSize = 26.sp
                                            )
                                        )
                                    }
                                }
                            }
                        }
                        Text(
                            text = value.value,
                            style = LocalTheme.current.styles.regular.copy(
                                fontSize = 32.sp
                            )
                        )
                    }
                }
            }

            BrandHeaderButton(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .align(Alignment.End),
                text = "Submit"
            ) {
                model.reportExperimentValues(
                    values = selectedValues.toList(),
                    customValue = customValueState.text.takeIf { it.isNotBlank() }?.toString(),
                    experiment = experiment.data
                )
                selectedValues.clear()
            }
            Spacer(Modifier.height(48.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverlayBottomSheet(
    modifier: Modifier = Modifier,
    sheetState: BottomSheetScaffoldState,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    sheetContent: @Composable ColumnScope.() -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    BottomSheetScaffold(
        sheetContent = {
            Column(
                modifier = modifier.navigationBarsPadding(),
                verticalArrangement = verticalArrangement,
                horizontalAlignment = horizontalAlignment
            ) {
                sheetContent()
            }
        },
        containerColor = LocalTheme.current.colors.backgroundLight,
        scaffoldState = sheetState,
        sheetDragHandle = {
            BottomSheetDefaults.DragHandle(color = LocalTheme.current.colors.disabled)
        },
        sheetShape = RoundedCornerShape(
            topStart = LocalTheme.current.shapes.componentCornerRadius,
            topEnd = LocalTheme.current.shapes.componentCornerRadius
        ),
        sheetTonalElevation = LocalTheme.current.styles.actionElevation,
        content = content
    )
}
