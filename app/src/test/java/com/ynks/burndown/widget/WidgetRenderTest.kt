package com.ynks.burndown.widget

import android.app.Application
import android.graphics.Color
import com.ynks.burndown.domain.ChartType
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "xxhdpi")
class WidgetRenderTest {
    @Test
    fun rendersWidgets() {
        for (type in ChartType.entries) for (night in listOf(false, true)) for (showNumber in listOf(true, false)) {
            for ((label, size) in listOf("4x2" to (358 to 140), "4x3" to (358 to 215), "3x2" to (268 to 140), "2x2" to (176 to 140))) {
                val bitmap = WidgetTestSupport.renderWidget(type, WidgetTestSupport.ready(), size.first, size.second, night, showNumber)
                // corners get rounded by the system so check the middle
                assertTrue(Color.alpha(bitmap.getPixel(bitmap.width / 2, 4)) > 0)
                WidgetTestSupport.save(bitmap, "widget-${type.name.lowercase()}-${if (showNumber) "number" else "chart"}-$label-${if (night) "dark" else "light"}")
            }
        }
    }
}
