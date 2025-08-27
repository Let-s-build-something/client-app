package ui.dev.experiment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import augmy.interactive.shared.ext.scalingClickable
import augmy.interactive.shared.ui.theme.LocalTheme
import data.io.experiment.ExperimentQuestionnaire

@Composable
fun ExperimentSet(
    modifier: Modifier = Modifier,
    set: ExperimentQuestionnaire,
    size: Int,
    setPick: MutableState<ExperimentQuestionnaire?>?
) {
    Row(
        modifier = modifier
            .padding(top = 6.dp)
            .scalingClickable(key = set.uid, hoverEnabled = false, scaleInto = .95f) {
                setPick?.value = set
            }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = buildAnnotatedString {
                    append(set.name)
                    withStyle(SpanStyle(color = LocalTheme.current.colors.disabled)) {
                        append(" (${size} values)")
                    }
                },
                style = LocalTheme.current.styles.category
            )
            Text(
                text = set.values.joinToString(", ") { it.value },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = LocalTheme.current.styles.regular
            )
        }

        if (setPick != null) {
            val isSelected = setPick.value?.uid == set.uid
            Icon(
                modifier = Modifier
                    .scalingClickable(key = set.uid.plus(isSelected)) {
                        setPick.value = if (isSelected) null else set
                    }
                    .size(32.dp)
                    .padding(6.dp),
                imageVector = if (isSelected) Icons.Outlined.Close else Icons.Outlined.Edit,
                contentDescription = null,
                tint = if (isSelected) {
                    LocalTheme.current.colors.secondary
                } else LocalTheme.current.colors.disabled
            )
        }
    }
}