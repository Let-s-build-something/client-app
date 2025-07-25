package base.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.CheckboxColors
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.RadioButtonColors
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import augmy.composeapp.generated.resources.Res
import augmy.composeapp.generated.resources.quicksand_medium
import augmy.composeapp.generated.resources.quicksand_regular
import augmy.composeapp.generated.resources.quicksand_semi_bold
import augmy.composeapp.generated.resources.quicksand_variable
import augmy.interactive.shared.ui.theme.LocalTheme
import augmy.interactive.shared.ui.theme.SharedColors
import augmy.interactive.shared.ui.theme.ThemeStyle
import org.jetbrains.compose.resources.Font
import kotlin.jvm.JvmStatic

/** Styles specific to main app theme */
open class DefaultThemeStyles: ThemeStyle {

    companion object {
        protected val fontQuicksandVariable
            @Composable get() = Font(Res.font.quicksand_variable)

        @JvmStatic
        protected val fontQuicksandRegular
            @Composable get() = Font(Res.font.quicksand_regular)

        @JvmStatic
        val fontQuicksandMedium
            @Composable get() = Font(Res.font.quicksand_medium)

        @JvmStatic
        val fontQuicksandSemiBold
            @Composable get() = Font(Res.font.quicksand_semi_bold)
    }

    override val textFieldColors: TextFieldColors
        @Composable get() {
            return TextFieldDefaults.colors(
                focusedContainerColor = LocalTheme.current.colors.backgroundLight,
                unfocusedContainerColor = LocalTheme.current.colors.backgroundLight,
                disabledContainerColor = LocalTheme.current.colors.backgroundLight,
                disabledTextColor = LocalTheme.current.colors.disabled,
                focusedTextColor = LocalTheme.current.colors.primary,
                unfocusedTextColor = LocalTheme.current.colors.secondary,
                cursorColor = LocalTheme.current.colors.secondary,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                errorIndicatorColor = Color.Transparent,
                errorContainerColor = LocalTheme.current.colors.backgroundLight,
                errorTextColor = SharedColors.RED_ERROR,
                errorTrailingIconColor = SharedColors.RED_ERROR
            )
        }

    override val textFieldColorsOnFocus: TextFieldColors
        @Composable get() {
            return TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedLabelColor = Color.Transparent,
                disabledTextColor = LocalTheme.current.colors.secondary,
                focusedTextColor = LocalTheme.current.colors.primary,
                unfocusedTextColor = LocalTheme.current.colors.secondary,
                cursorColor = LocalTheme.current.colors.secondary,
                disabledIndicatorColor = Color.Transparent,
                errorContainerColor = Color.Transparent,
                errorIndicatorColor = Color.Transparent,
                errorTextColor = SharedColors.RED_ERROR,
                errorTrailingIconColor = SharedColors.RED_ERROR,
                disabledLabelColor = Color.Transparent,
                disabledPrefixColor = LocalTheme.current.colors.secondary,
                disabledSuffixColor = LocalTheme.current.colors.secondary
            )
        }

    override val checkBoxColorsDefault: CheckboxColors
        @Composable get() = CheckboxColors(
            checkedCheckmarkColor = LocalTheme.current.colors.primary,
            uncheckedCheckmarkColor = LocalTheme.current.colors.secondary,
            checkedBoxColor = Color.Transparent,
            uncheckedBoxColor = Color.Transparent,
            checkedBorderColor = LocalTheme.current.colors.brandMain,
            uncheckedBorderColor = LocalTheme.current.colors.brandMain,
            disabledUncheckedBoxColor = Color.Transparent,
            disabledCheckedBoxColor = Color.Transparent,
            disabledBorderColor = LocalTheme.current.colors.disabled,
            disabledIndeterminateBorderColor = LocalTheme.current.colors.disabled,
            disabledIndeterminateBoxColor = Color.Transparent,
            disabledUncheckedBorderColor = LocalTheme.current.colors.disabled
        )

    override val switchColorsDefault: SwitchColors
        @Composable get() = SwitchDefaults.colors(
            checkedTrackColor = LocalTheme.current.colors.brandMain,
            checkedThumbColor = LocalTheme.current.colors.tetrial,
            uncheckedBorderColor = LocalTheme.current.colors.secondary,
            uncheckedThumbColor = LocalTheme.current.colors.secondary,
            uncheckedTrackColor = LocalTheme.current.colors.backgroundLight
        )

    override val radioButtonColors: RadioButtonColors
        @Composable get() = RadioButtonDefaults.colors(
            selectedColor = LocalTheme.current.colors.brandMain,
            unselectedColor = LocalTheme.current.colors.secondary,
            disabledSelectedColor = LocalTheme.current.colors.disabled,
            disabledUnselectedColor = LocalTheme.current.colors.disabled
        )

    override val componentElevation: Dp = 6.dp
    override val actionElevation: Dp = 8.dp
    override val minimumElevation: Dp = 2.dp

    override val link: TextLinkStyles
        @Composable
        get() {
            val style = SpanStyle(
                color = LocalTheme.current.colors.brandMain,
                textDecoration = TextDecoration.None,
                fontFamily = FontFamily(fontQuicksandMedium)
            )
            val styleDark = SpanStyle(
                color = LocalTheme.current.colors.brandMain,
                textDecoration = TextDecoration.Underline,
                fontFamily = FontFamily(fontQuicksandMedium),
                fontWeight = FontWeight.W900
            )
            return TextLinkStyles(
                style = style,
                focusedStyle = style,
                hoveredStyle = styleDark,
                pressedStyle = styleDark
            )
        }

    override val chipBorderDefault: BorderStroke
        @Composable get() = FilterChipDefaults.filterChipBorder(
            borderColor = Color.Transparent,
            selectedBorderColor = Color.Transparent,
            disabledBorderColor = Color.Transparent,
            disabledSelectedBorderColor = Color.Transparent,
            borderWidth = 0.dp,
            selectedBorderWidth = 0.dp,
            enabled = true,
            selected = false
        )

    override val chipColorsDefault: SelectableChipColors
        @Composable get() = FilterChipDefaults.filterChipColors(
            containerColor = LocalTheme.current.colors.tetrial,
            labelColor = LocalTheme.current.colors.brandMain,
            selectedContainerColor = LocalTheme.current.colors.brandMain,
            selectedLabelColor = LocalTheme.current.colors.tetrial
        )

    override val heading: TextStyle
        @Composable
        get() = TextStyle(
            color = LocalTheme.current.colors.primary,
            fontSize = 28.sp,
            fontFamily = FontFamily(fontQuicksandSemiBold)
        )

    override val subheading: TextStyle
        @Composable
        get() = TextStyle(
            color = LocalTheme.current.colors.primary,
            fontSize = 22.sp,
            fontFamily = FontFamily(fontQuicksandSemiBold)
        )

    override val category: TextStyle
        @Composable
        get() = TextStyle(
            color = LocalTheme.current.colors.secondary,
            fontSize = 16.sp,
            fontFamily = FontFamily(fontQuicksandSemiBold)
        )

    override val title: TextStyle
        @Composable
        get() = TextStyle(
            color = LocalTheme.current.colors.primary,
            fontSize = 16.sp,
            fontFamily = FontFamily(fontQuicksandMedium)
        )

    override val regular: TextStyle
        @Composable
        get() = TextStyle(
            color = LocalTheme.current.colors.secondary,
            fontSize = 12.sp,
            fontFamily = FontFamily(fontQuicksandMedium)
        )
}