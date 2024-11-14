package base.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBackIosNew
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import augmy.composeapp.generated.resources.Res
import augmy.composeapp.generated.resources.button_tune
import augmy.composeapp.generated.resources.navigate_back
import augmy.composeapp.generated.resources.navigate_close
import augmy.composeapp.generated.resources.navigate_hamburger_menu
import augmy.composeapp.generated.resources.navigate_home
import org.jetbrains.compose.resources.stringResource

/**
 * Type of a navigation icon
 */
enum class NavIconType {
    /** simple cross for closing a screen */
    CLOSE,

    /** arrow back to navigate back from the screen */
    BACK,

    /** icon of a home to navigate to home page */
    HOME,

    /** icon of the app */
    BRAND,

    /** icon of tuning */
    TUNE,

    /** icon of a hamburger menu */
    HAMBURGER,

    /** no navigation icon */
    NONE;

    /** ImageVector resource with content description for accessibility */
    val imageVector: Pair<ImageVector, String>?
        @Composable
        get() = when(this) {
            TUNE -> Icons.Outlined.Tune to stringResource(Res.string.button_tune)
            HOME -> Icons.Outlined.Home to stringResource(Res.string.navigate_home)
            CLOSE -> Icons.Outlined.Close to stringResource(Res.string.navigate_close)
            HAMBURGER -> Icons.Outlined.Menu to stringResource(Res.string.navigate_hamburger_menu)
            BACK -> Icons.Outlined.ArrowBackIosNew to stringResource(Res.string.navigate_back)
            else -> null
        }
}