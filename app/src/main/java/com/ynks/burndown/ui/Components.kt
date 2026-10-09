package com.ynks.burndown.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import com.ynks.burndown.chart.ChartColors
import com.ynks.burndown.chart.SprintChartRenderer
import com.ynks.burndown.domain.ChartType
import com.ynks.burndown.domain.SprintSeries

@Composable
fun chartColors(type: ChartType, plate: Color = MaterialTheme.colorScheme.surfaceContainer): ChartColors {
    val s = MaterialTheme.colorScheme
    val burndown = type == ChartType.BURNDOWN
    return ChartColors(
        primary = (if (burndown) s.primary else s.tertiary).toArgb(),
        onPrimary = (if (burndown) s.onPrimary else s.onTertiary).toArgb(),
        secondary = (if (burndown) s.tertiary else s.primary).toArgb(),
        onSecondary = (if (burndown) s.onTertiary else s.onPrimary).toArgb(),
        trend = s.onSurfaceVariant.toArgb(),
        grid = ColorUtils.setAlphaComponent(s.outline.toArgb(), 150),
        label = s.onSurfaceVariant.toArgb(),
        plate = plate.toArgb(),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChartImage(series: SprintSeries, type: ChartType, modifier: Modifier = Modifier) {
    val colors = chartColors(type)
    val reveal = remember { Animatable(0f) }
    val spec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(series.resolution, type) {
        reveal.snapTo(0f)
        reveal.animateTo(1f, spec)
    }
    Canvas(modifier.fillMaxWidth().height(240.dp)) {
        drawIntoCanvas {
            SprintChartRenderer.draw(
                canvas = it.nativeCanvas,
                series = series,
                type = type,
                colors = colors,
                width = size.width,
                height = size.height,
                density = density,
                drawPlate = true,
                reveal = reveal.value,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> ChoiceGroup(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    fill: Boolean = true,
) {
    Row(
        modifier = if (fill) modifier.fillMaxWidth() else modifier,
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, (value, label) ->
            val shapes = when (index) {
                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
            }
            ToggleButton(
                checked = value == selected,
                onCheckedChange = { if (it) onSelect(value) },
                shapes = shapes,
                modifier = if (fill) Modifier.weight(1f) else Modifier,
            ) {
                Text(label, style = MaterialTheme.typography.labelLargeEmphasized, modifier = Modifier.padding(horizontal = 4.dp))
            }
        }
    }
}
