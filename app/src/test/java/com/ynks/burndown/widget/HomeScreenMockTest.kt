package com.ynks.burndown.widget

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.ynks.burndown.domain.ChartType
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "xxhdpi")
class HomeScreenMockTest {
    private val d = 3f // xxhdpi
    private fun dp(v: Float) = v * d

    private class Palette(val sky: IntArray, val text: Int, val iconLabel: Int, val dock: Int, val blob: Int)

    private val light = Palette(
        intArrayOf(Color.parseColor("#A9C4F2"), Color.parseColor("#D3C5F0"), Color.parseColor("#F4D5E4")),
        Color.parseColor("#1B1B1F"), Color.parseColor("#1B1B1F"), Color.parseColor("#55FFFFFF"), Color.parseColor("#66FFFFFF"),
    )
    private val dark = Palette(
        intArrayOf(Color.parseColor("#0A1030"), Color.parseColor("#241A52"), Color.parseColor("#47224F")),
        Color.WHITE, Color.WHITE, Color.parseColor("#33FFFFFF"), Color.parseColor("#22FFFFFF"),
    )

    private fun text(color: Int, sizeDp: Float, bold: Boolean = false, align: Paint.Align = Paint.Align.LEFT) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = dp(sizeDp)
            textAlign = align
            typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
        }

    private fun home(night: Boolean, widgets: List<Pair<ChartType, Int>>, iconRows: Int, showNumber: Boolean = true): Bitmap {
        val p = if (night) dark else light
        val w = 390
        val h = 844
        val bmp = Bitmap.createBitmap((w * d).toInt(), (h * d).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)

        // wallpaper
        c.drawRect(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, bmp.width * 0.3f, bmp.height.toFloat(), p.sky, null, Shader.TileMode.CLAMP)
        })
        for ((cx, cy, r) in listOf(Triple(0.15f, 0.2f, 0.45f), Triple(0.9f, 0.65f, 0.5f))) {
            c.drawCircle(bmp.width * cx, bmp.height * cy, bmp.width * r, Paint().apply {
                shader = RadialGradient(bmp.width * cx, bmp.height * cy, bmp.width * r, p.blob, Color.TRANSPARENT, Shader.TileMode.CLAMP)
            })
        }

        // status bar
        c.drawText("9:41", dp(24f), dp(34f), text(p.text, 14f, true))
        c.drawCircle(dp(w - 28f), dp(28f), dp(5f), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.text })
        c.drawRoundRect(RectF(dp(w - 62f), dp(24f), dp(w - 40f), dp(32f)), dp(4f), dp(4f), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.text })
        c.drawText("Thursday, Oct 8", dp(24f), dp(86f), text(p.text, 20f))

        // widgets
        var y = 112f
        val clip = Path()
        for ((type, heightDp) in widgets) {
            val bitmap = WidgetTestSupport.renderWidget(type, WidgetTestSupport.ready(), 358, heightDp, night, showNumber)
            c.save()
            clip.reset()
            clip.addRoundRect(RectF(dp(16f), dp(y), dp(374f), dp(y + heightDp)), dp(28f), dp(28f), Path.Direction.CW)
            c.clipPath(clip)
            c.drawBitmap(bitmap, dp(16f), dp(y), null)
            c.restore()
            y += heightDp + 14f
        }

        // app icons
        val apps = listOf("Phone", "Messages", "Camera", "Photos", "Maps", "Mail", "Notes", "Music")
        val colors = listOf("#6FBF73", "#5B9BF0", "#9AA0A6", "#F2B84B", "#E76F6F", "#4F8EF7", "#F5D547", "#B57BEE")
        val label = text(p.iconLabel, 11f, align = Paint.Align.CENTER)
        for (i in 0 until iconRows * 4) {
            val col = i % 4
            val row = i / 4
            val cx = dp(16f + 89.5f * col + 44.75f)
            val cy = dp(y + 34f + row * 92f)
            c.drawRoundRect(RectF(cx - dp(27f), cy - dp(27f), cx + dp(27f), cy + dp(27f)), dp(18f), dp(18f),
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor(colors[i % colors.size]) })
            c.drawText(apps[i % apps.size], cx, cy + dp(46f), label)
        }

        // dock
        c.drawRoundRect(RectF(dp(16f), dp(h - 118f), dp(374f), dp(h - 34f)), dp(36f), dp(36f), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.dock })
        for (i in 0 until 4) {
            val cx = dp(16f + 89.5f * i + 44.75f)
            c.drawRoundRect(RectF(cx - dp(26f), dp(h - 105f), cx + dp(26f), dp(h - 53f)), dp(17f), dp(17f),
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor(colors[(i * 2 + 1) % colors.size]) })
        }
        // gesture pill
        c.drawRoundRect(RectF(dp(w / 2f - 66f), dp(h - 20f), dp(w / 2f + 66f), dp(h - 15f)), dp(3f), dp(3f), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.text })
        return bmp
    }

    @Test
    fun rendersMockHomeScreens() {
        val both42 = listOf(ChartType.BURNDOWN to 140, ChartType.BURNUP to 140)
        WidgetTestSupport.save(home(false, both42, iconRows = 2), "mock-home-light-number")
        WidgetTestSupport.save(home(false, both42, iconRows = 2, showNumber = false), "mock-home-light-chart")
        WidgetTestSupport.save(home(true, both42, iconRows = 2), "mock-home-dark-number")
        WidgetTestSupport.save(home(true, both42, iconRows = 2, showNumber = false), "mock-home-dark-chart")
        WidgetTestSupport.save(home(false, listOf(ChartType.BURNDOWN to 215, ChartType.BURNUP to 215), iconRows = 1), "mock-home-light-4x3-number")
    }
}
