package com.ynks.burndown.widget

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.glance.unit.ColorProvider
import com.ynks.burndown.chart.ChartColors
import com.ynks.burndown.domain.ChartType
import com.ynks.burndown.ui.BaselineDark
import com.ynks.burndown.ui.BaselineLight

class WidgetTheme(
    val container: ColorProvider,
    val onContainer: ColorProvider,
    val error: ColorProvider,
    val chart: ChartColors,
) {
    companion object {
        fun of(context: Context, type: ChartType, schemes: Pair<ColorScheme, ColorScheme>? = null): WidgetTheme {
            val dynamic = schemes == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            val light = schemes?.first ?: if (dynamic) dynamicLightColorScheme(context) else BaselineLight
            val dark = schemes?.second ?: if (dynamic) dynamicDarkColorScheme(context) else BaselineDark
            val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
            val now = if (night) dark else light

            fun pick(select: (ColorScheme) -> Color) = androidx.glance.color.ColorProvider(day = select(light), night = select(dark))

            val burndown = type == ChartType.BURNDOWN
            val container = if (burndown) now.primaryContainer else now.tertiaryContainer
            val onContainer = if (burndown) now.onPrimaryContainer else now.onTertiaryContainer
            val main = if (burndown) now.primary else now.tertiary

            return WidgetTheme(
                container = pick { if (burndown) it.primaryContainer else it.tertiaryContainer },
                onContainer = pick { if (burndown) it.onPrimaryContainer else it.onTertiaryContainer },
                error = pick { it.error },
                chart = ChartColors(
                    primary = main.toArgb(),
                    onPrimary = container.toArgb(),
                    secondary = ColorUtils.blendARGB(container.toArgb(), onContainer.toArgb(), 0.55f),
                    onSecondary = container.toArgb(),
                    trend = ColorUtils.setAlphaComponent(onContainer.toArgb(), 150),
                    grid = onContainer.toArgb(),
                    label = onContainer.toArgb(),
                    plate = container.toArgb(),
                ),
            )
        }
    }
}
