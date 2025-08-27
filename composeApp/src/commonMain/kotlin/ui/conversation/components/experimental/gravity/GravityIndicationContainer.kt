package ui.conversation.components.experimental.gravity

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import augmy.interactive.shared.ui.theme.LocalTheme
import base.utils.orZero
import data.sensor.HZ_SPEED_SLOW
import data.sensor.getGravityListener
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ui.conversation.components.experimental.gravity.GravityUseCase.Companion.FULL_GRAVITY
import ui.conversation.components.experimental.gravity.GravityUseCase.Companion.MAX_FRACTION_OFFSET
import kotlin.math.absoluteValue

@Composable
fun GravityIndicationContainer(
    modifier: Modifier = Modifier,
    indicationColor: Color = LocalTheme.current.colors.tetrial,
    backgroundColor: Color,
    enabled: Boolean,
    shape: Shape,
    gravityData: GravityData?,
    content: @Composable ColumnScope.() -> Unit= {}
) {
    val index = if(gravityData != null) {
        rememberSaveable(gravityData) {
            mutableStateOf(0)
        }
    }else null

    if(enabled && !gravityData?.values.isNullOrEmpty() && index?.value != gravityData.values.size) {
        val scope = rememberCoroutineScope()
        val offset = remember(gravityData) {
            Animatable(gravityData.values.firstOrNull()?.offset.orZero())
        }
        val fraction = remember(gravityData) {
            Animatable(gravityData.values.firstOrNull()?.fraction.orZero())
        }
        val gravityValues = remember {
            mutableStateOf<Triple<Float, Float, Float>?>(null)
        }

        val listener = remember {
            getGravityListener(
                onSensorChanged = { event ->
                    event?.values?.let {
                        gravityValues.value = Triple(it[0], it[1], it[2])
                    }
                }
            )
        }

        LifecycleResumeEffect(gravityData) {
            scope.coroutineContext.cancelChildren()
            scope.launch {
                while(index?.value != null && index.value < gravityData.values.size) {
                    delay(gravityData.tickMs)
                    gravityData.values.getOrNull(index.value)?.let {
                        fraction.animateTo(it.fraction)
                        offset.animateTo(it.offset)
                    }
                    index.value += 1
                }
                index?.value = gravityData.values.size
            }
            listener?.register(HZ_SPEED_SLOW)

            onPauseOrDispose {
                listener?.unregister()
            }
        }

        val customBrush = rememberUpdatedState(
            gravityValues.value.let { ownValues ->
                fraction.value.let { counterValue ->
                    object: ShaderBrush() {
                        override fun createShader(size: Size): Shader {
                            val indication = calculateIndicationStops(
                                size = size,
                                gx = ownValues?.first ?: 0f,
                                gy = ownValues?.second ?: 0f,
                                counterValue = counterValue,
                                counterOffset = offset.value
                            )

                            return LinearGradientShader(
                                colors = listOf(
                                    backgroundColor,
                                    indicationColor,
                                    backgroundColor,
                                ),
                                colorStops = indication.stops,
                                tileMode = TileMode.Decal,
                                from = Offset.Zero,
                                to = Offset(size.width, size.height)
                            )
                        }
                    }
                }
            }
        )

        Column(
            modifier = Modifier
                .border(
                    width = 2.dp,
                    shape = shape,
                    brush = customBrush.value
                ).then(modifier),
            content = content
        )
    }else {
        Column(modifier = modifier, content = content)
    }
}

private data class GravityIndicationStops(
    val stops: List<Float>,
    val from: Offset,
    val to: Offset,
)

private fun calculateIndicationStops(
    size: Size,
    gx: Float,
    gy: Float,
    counterValue: Float? = null,
    counterOffset: Float? = null
): GravityIndicationStops {
    val verticalFraction = ((gy.absoluteValue / FULL_GRAVITY).minus(1f).absoluteValue * MAX_FRACTION_OFFSET).let {
        if(counterOffset != null) {
            counterOffset - (if(it < 0) it else -it).div(2)
        }else it / 2
    }

    val calculatedValue = (gx / FULL_GRAVITY / 2 + 0.5f).minus(1).absoluteValue.coerceIn(0f, 1f)
    val value = if (counterValue != null) {
        0.5f + (counterValue - calculatedValue)
    } else {
        calculatedValue
    }.minus(1).absoluteValue.coerceIn(0f, 1f)

    val stops = listOf(
        value - verticalFraction,
        value,
        value + verticalFraction,
    )

    val from = Offset.Zero
    val to = Offset(size.width, size.height)

    return GravityIndicationStops(
        stops = stops,
        from = from,
        to = to
    )
}
