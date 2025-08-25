
import android.app.Application
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowDecoration
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import augmy.composeapp.generated.resources.Res
import augmy.composeapp.generated.resources.app_name
import augmy.interactive.com.BuildKonfig
import augmy.interactive.shared.ui.base.BackPressDispatcher
import augmy.interactive.shared.ui.base.LocalBackPressDispatcher
import augmy.interactive.shared.ui.base.LocalScreenSize
import base.utils.orZero
import com.google.firebase.Firebase
import com.google.firebase.FirebaseOptions
import com.google.firebase.FirebasePlatform
import com.google.firebase.initialize
import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.coroutines.SuspendSettings
import com.russhwolf.settings.coroutines.toBlockingSettings
import data.io.app.ThemeChoice
import data.shared.AppServiceModel
import dev.datlag.kcef.KCEF
import io.kamel.core.config.KamelConfig
import io.kamel.core.config.takeFrom
import io.kamel.image.config.Default
import io.kamel.image.config.LocalKamelConfig
import koin.AppSettings
import koin.commonModule
import koin.settingsModule
import korlibs.logger.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.cef.CefSettings
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.context.unloadKoinModules
import org.koin.mp.KoinPlatform
import ui.conversation.components.gif.PlatformFileFetcher
import utils.SharedLogger
import java.awt.Button
import java.awt.Dialog
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Frame
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import javax.swing.JScrollPane
import javax.swing.JTextArea

// paranoid check
private var isAppInitialized = false

/** Initialization of the Jvm application. */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalSettingsApi::class)
fun main(args: Array<String>) = application {
    var arguments: Array<String>? = args
    val coroutineScope = rememberCoroutineScope()
    val systemDarkTheme = isSystemInDarkTheme()
    val isDarkTheme = remember { mutableStateOf(systemDarkTheme) }

    if(isAppInitialized.not()) {
        startKoin {
            modules(settingsModule)
            initializeFirebase(
                settings = KoinPlatform.getKoin().get<AppSettings>(),
                scope = coroutineScope
            )
            modules(commonModule)
            coroutineScope.launch {
                KoinPlatform.getKoin().get<AppServiceModel>().localSettings.collectLatest {
                    isDarkTheme.value = when(it?.theme) {
                        ThemeChoice.DARK -> true
                        ThemeChoice.LIGHT -> false
                        ThemeChoice.SYSTEM -> systemDarkTheme
                        null -> true
                    }
                }
            }
        }
        isAppInitialized = true
    }

    val density = LocalDensity.current
    val toolkit = Toolkit.getDefaultToolkit()

    Thread.setDefaultUncaughtExceptionHandler { _, e ->
        Dialog(Frame(), e.message ?: "Error").apply {
            layout = FlowLayout()

            val stringWriter = StringWriter()
            e.printStackTrace(PrintWriter(stringWriter))
            val stackTraceText = "We apologize, an unexpected error occurred:\n\n$stringWriter"

            val errorMessageTextArea = JTextArea(stackTraceText, 10, 80).apply {
                lineWrap = true
                wrapStyleWord = true
                isEditable = false
            }

            val scrollPane = JScrollPane(errorMessageTextArea).apply {
                preferredSize = Dimension(1100, 200)
                verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
                horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            }

            add(scrollPane)
            add(
                Button("I'll report this to info@augmy.org").apply {
                    addActionListener {
                        KCEF.disposeBlocking()
                        dispose()
                    }
                }
            )
            setSize(1000, 500)
            isAutoRequestFocus = true
            isResizable = false
            isVisible = true
        }
        e.printStackTrace()
    }

    val backPressDispatcher = remember {
        object: BackPressDispatcher {
            val listeners = mutableListOf<() -> Unit>()
            override val progress = mutableFloatStateOf(0f)

            override fun addOnBackPressedListener(listener: () -> Unit) {
                this.listeners.add(0, listener)
            }
            override fun removeOnBackPressedListener(listener: () -> Unit) {
                this.listeners.remove(listener)
            }
            override fun executeBackPress() {
                listeners.firstOrNull()?.invoke()
            }
            override fun executeSystemBackPress() {
                KCEF.disposeBlocking()
                unloadKoinModules(commonModule)
                stopKoin()
                exitApplication()
            }
        }
    }

    LaunchedEffect(isDarkTheme.value) {
        withContext(Dispatchers.IO) {
            try {
                KCEF.init(builder = {
                    settings {
                        backgroundColor = if(isDarkTheme.value) {
                            CefSettings().ColorType(255, 34, 31, 28)
                        }else CefSettings().ColorType(255, 236, 241, 231)
                        cachePath = File("cache").absolutePath
                    }
                    installDir(File("kcef-bundle"))
                }, onError = {
                    it?.printStackTrace()
                }, onRestartRequired = {
                    // should we restart the app?
                })
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val kamelConfig = remember {
        KamelConfig {
            takeFrom(KamelConfig.Default)
            fetcher(PlatformFileFetcher)
        }
    }

    LaunchedEffect(Unit) {
        associateWithDomain()
    }

    Window(
        onCloseRequest = {
            backPressDispatcher.executeBackPress()
        },
        state = rememberWindowState(
            placement = WindowPlacement.Floating,
            width = with(density) {
                toolkit?.screenSize?.width?.div(2)?.toDp() ?: 600.dp
            },
            height = with(density) {
                (toolkit?.screenSize?.height?.minus(
                    toolkit.getScreenInsets(
                        GraphicsEnvironment.getLocalGraphicsEnvironment()
                            .defaultScreenDevice
                            .defaultConfiguration
                    )?.bottom.orZero()
                ))?.toDp() ?: 600.dp
            },
            position = WindowPosition.Aligned(Alignment.TopStart)
        ),
        title = stringResource(Res.string.app_name),
        visible = true,
        decoration = WindowDecoration.SystemDefault,
        resizable = true,
        alwaysOnTop = false
    ) {
        val containerSize = LocalWindowInfo.current.containerSize

        CompositionLocalProvider(
            LocalKamelConfig provides kamelConfig,
            LocalBackPressDispatcher provides backPressDispatcher,
            LocalScreenSize provides IntSize(
                height = with(density) { containerSize.height.toDp() }.value.toInt(),
                width = with(density) { containerSize.width.toDp() }.value.toInt()
            )
        ) {
            val viewModel: AppServiceModel = koinViewModel()

            App(viewModel)

            LaunchedEffect(arguments) {
                delay(500)
                arguments?.firstOrNull()?.let { arg ->
                    viewModel.emitDeepLink(arg)
                    arguments = null
                }
            }
        }
    }
}


private fun associateWithDomain() {
    val os = System.getProperty("os.name").lowercase()

    when {
        os.contains("windows") -> {
            initWindowsRegistry()
        }
        os.contains("linux") -> {
            registerUriSchemeLinux()
        }
    }
}

private val logger = Logger("JvmRegistry")

private fun initWindowsRegistry() {
    try {
        val exePath = System.getProperty("user.dir") + File.separator + "Augmy.exe"

        val commands = listOf(
            """reg add "HKCU\Software\Classes\augmy" /ve /d "Description here" /f""",
            """reg add "HKCU\Software\Classes\augmy" /v "URL Protocol" /f""",
            """reg add "HKCU\Software\Classes\augmy\shell" /f""",
            """reg add "HKCU\Software\Classes\augmy\shell\open" /f""",
            """reg add "HKCU\Software\Classes\augmy\shell\open\command" /ve /d "\"$exePath\" \"%1\"" /f"""
        )

        for (cmd in commands) {
            val process = Runtime.getRuntime().exec(arrayOf(cmd))
            process.waitFor()
            if (process.exitValue() != 0) {
                logger.error { "Command failed: $cmd" }
                process.errorStream.bufferedReader()
                    .use { it.lines().forEach { line -> logger.error { line } } }
            } else {
                logger.debug { "Command succeeded: $cmd" }
            }
        }
    }catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun registerUriSchemeLinux() {
    val home = System.getProperty("user.home")
    val desktopFilePath = "$home/.local/share/applications/augmy.desktop"

    // Determine the executable path based on build type
    val execPath = if (BuildKonfig.isDevelopment) {
        // Debug: Use the launcher script
        "/home/jacob/StudioProjects/enrichment-app/composeApp/augmy-launcher.sh"
    } else System.getProperty("user.dir") + File.separator + "Augmy"
    logger.debug { "execPath: $execPath" }

    val desktopFileContent = """
        [Desktop Entry]
        Name=Augmy
        Exec=gnome-terminal -- $execPath %u
        Type=Application
        MimeType=x-scheme-handler/augmy;
        Terminal=false
        Categories=Utility;
    """.trimIndent()

    try {
        val desktopFile = File(desktopFilePath)
        desktopFile.parentFile.mkdirs()
        desktopFile.writeText(desktopFileContent)
        desktopFile.setExecutable(true)

        val commands = listOf(
            "xdg-mime default augmy.desktop x-scheme-handler/augmy",
            "update-desktop-database ~/.local/share/applications"
        )

        for (cmd in commands) {
            val process = ProcessBuilder("sh", "-c", cmd)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            logger.debug {
                "Command: $cmd" +
                        "\nExit Code: $exitCode" +
                        "\nOutput:\n$output"
            }
        }

        // Optional: Confirm it's registered
        val verify = ProcessBuilder("sh", "-c", "xdg-mime query default x-scheme-handler/augmy")
            .redirectErrorStream(true)
            .start()

        verify.waitFor()
        val result = verify.inputStream.bufferedReader().readText().trim()
        logger.debug { "Verified x-scheme-handler/augmy: $result" }

    } catch (e: Exception) {
        logger.debug { "Failed to register URI scheme:" }
        e.printStackTrace()
    }
}

/**
 * initializes Firebase, which is specific to JVM,
 * see https://github.com/GitLiveApp/firebase-java-sdk?tab=readme-ov-file#initializing-the-sdk
 */
@OptIn(ExperimentalSettingsApi::class)
private fun initializeFirebase(
    settings: SuspendSettings,
    scope: CoroutineScope
) {
    FirebasePlatform.initializeFirebasePlatform(
        object : FirebasePlatform() {
            override fun store(key: String, value: String) {
                scope.launch {
                    settings.putString(key, value)
                }
            }
            override fun retrieve(key: String) = settings.toBlockingSettings().getString(key, "").ifEmpty { null }
            override fun clear(key: String) {
                scope.launch {
                    settings.remove(key)
                }
            }
            override fun log(msg: String) = SharedLogger.logger.debug { msg }
        }
    )

    val options: FirebaseOptions = FirebaseOptions.Builder()
        .setProjectId(BuildKonfig.FirebaseProjectId)
        .setApplicationId(BuildKonfig.AndroidAppId)
        .setApiKey(BuildKonfig.CloudWebApiKey)
        .setStorageBucket(BuildKonfig.StorageBucketName)
        .build()

    Firebase.initialize(Application(), options)
}
