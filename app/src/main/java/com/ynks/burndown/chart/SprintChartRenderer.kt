package com.ynks.burndown.chart

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.createBitmap
import com.ynks.burndown.domain.ChartType
import com.ynks.burndown.domain.Resolution
import com.ynks.burndown.domain.SeriesPoint
import com.ynks.burndown.domain.SprintSeries
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

object SprintChartRenderer {

    internal enum class Line { REMAINING, TOTAL, COMPLETED }

    fun render(
        series: SprintSeries,
        type: ChartType,
        colors: ChartColors,
        widthPx: Int,
        heightPx: Int,
        density: Float,
        drawPlate: Boolean = true,
        simple: Boolean = false,
    ): Bitmap {
        val bitmap = createBitmap(widthPx.coerceAtLeast(1), heightPx.coerceAtLeast(1))
        draw(Canvas(bitmap), series, type, colors, widthPx.toFloat(), heightPx.toFloat(), density, drawPlate, 1f, simple)
        return bitmap
    }

    fun draw(
        canvas: Canvas,
        series: SprintSeries,
        type: ChartType,
        colors: ChartColors,
        width: Float,
        height: Float,
        density: Float,
        drawPlate: Boolean = true,
        reveal: Float = 1f,
        simple: Boolean = false,
    ) {
        fun dp(v: Float) = v * density
        val hourly = series.resolution == Resolution.HOURLY
        val plot = plotFor(series, type, width, height, density, simple)
        if (simple) {
            drawSimple(canvas, plot, series, type, colors, density, width, height)
            return
        }

        if (drawPlate) {
            val radius = min(dp(24f), height / 4f)
            canvas.drawRoundRect(0f, 0f, width, height, radius, radius, fill(colors.plate))
        }

        drawFrame(canvas, plot, series, colors, density)
        drawTrend(canvas, plot, series, type, colors, density)

        canvas.save()
        canvas.clipRect(0f, 0f, plot.left + (width - plot.left) * reveal.coerceIn(0f, 1f), height)
        val strokeWidth = dp(if (plot.compact) 4f else if (hourly) 5f else 6f)
        val dotRadius = dp(if (plot.compact) 2.5f else 3.5f)
        when (type) {
            ChartType.BURNDOWN -> {
                val v = vertices(plot, series, Line.REMAINING)
                drawFill(canvas, plot, v, colors.primary)
                drawLine(canvas, v, colors.primary, strokeWidth)
                if (!hourly) drawDots(canvas, v, colors.primary, dotRadius)
            }
            ChartType.BURNUP -> {
                val total = vertices(plot, series, Line.TOTAL)
                val done = vertices(plot, series, Line.COMPLETED)
                drawFill(canvas, plot, done, colors.primary)
                drawLine(canvas, total, colors.secondary, strokeWidth)
                drawLine(canvas, done, colors.primary, strokeWidth)
                if (!hourly) {
                    drawDots(canvas, done, colors.primary, dotRadius)
                }
            }
        }
        canvas.restore()

        val last = series.last ?: return
        val calloutAlpha = ((reveal - 0.7f) / 0.3f).coerceIn(0f, 1f)
        val bounds = RectF(dp(6f), dp(4f), width - dp(6f), plot.bottom + dp(4f))
        val placed = ArrayList<RectF>()
        when (type) {
            ChartType.BURNDOWN -> {
                val p = vertices(plot, series, Line.REMAINING).last()
                drawNow(canvas, p, colors.primary, colors.onPrimary, colors.plate, formatValue(last.remaining), true, bounds, placed, calloutAlpha, density, plot.compact)
            }
            ChartType.BURNUP -> {
                val t = vertices(plot, series, Line.TOTAL).last()
                val c = vertices(plot, series, Line.COMPLETED).last()
                drawNow(canvas, t, colors.secondary, colors.onSecondary, colors.plate, formatValue(last.total), true, bounds, placed, calloutAlpha, density, plot.compact, preferRight = true)
                drawNow(canvas, c, colors.primary, colors.onPrimary, colors.plate, formatValue(last.completed), false, bounds, placed, calloutAlpha, density, plot.compact)
            }
        }
        drawXLabels(canvas, plot, series, colors, width, height, density)
    }

    internal fun isCompact(width: Float, height: Float, density: Float) =
        height / density < 125f || width / density < 210f

    internal class Plot(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val yMax: Double,
        val series: SprintSeries,
        val gutterLabels: Pair<String, String>?,
        val compact: Boolean,
    ) {
        fun x(time: Instant): Float = left + (right - left) * series.fraction(time).toFloat()
        fun y(value: Double): Float = bottom - (bottom - top) * (value / yMax).toFloat().coerceIn(0f, 1.05f)
    }

    private fun labelPaint(colors: ChartColors?, density: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colors?.label ?: 0
        textSize = 11f * density
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    }

    internal fun plotFor(series: SprintSeries, type: ChartType, width: Float, height: Float, density: Float, simple: Boolean = false): Plot {
        val yMax = yMax(series, type)
        if (simple) {
            return Plot(
                left = 6f * density,
                top = 11f * density,
                right = width - 11f * density,
                bottom = height - 11f * density,
                yMax = yMax,
                series = series,
                gutterLabels = null,
                compact = true,
            )
        }
        val compact = isCompact(width, height, density)
        if (compact) {
            return Plot(
                left = 14f * density,
                top = 12f * density,
                right = width - 14f * density,
                bottom = height - 26f * density,
                yMax = yMax,
                series = series,
                gutterLabels = null,
                compact = true,
            )
        }
        return Plot(
            left = 18f * density,
            top = 34f * density,
            right = width - 18f * density,
            bottom = height - 40f * density,
            yMax = yMax,
            series = series,
            gutterLabels = formatValue(yMax) to "0",
            compact = false,
        )
    }

    // hourly only keeps the points where the value changes
    internal fun vertices(plot: Plot, series: SprintSeries, line: Line): List<PointF> {
        val points = series.points
        val kept = if (series.resolution == Resolution.HOURLY) {
            points.filterIndexed { i, p ->
                i == 0 || i == points.lastIndex || valueOf(p, line) != valueOf(points[i - 1], line)
            }
        } else {
            points
        }
        return kept.map { PointF(plot.x(it.time), plot.y(valueOf(it, line))) }
    }

    internal fun vertices(series: SprintSeries, type: ChartType, line: Line, width: Float, height: Float, density: Float): List<PointF> =
        vertices(plotFor(series, type, width, height, density), series, line)

    private fun valueOf(p: SeriesPoint, line: Line) = when (line) {
        Line.REMAINING -> p.remaining
        Line.TOTAL -> p.total
        Line.COMPLETED -> p.completed
    }

    private fun yMax(series: SprintSeries, type: ChartType): Double {
        var peak = 1.0
        series.points.forEach { peak = max(peak, if (type == ChartType.BURNDOWN) it.remaining else it.total) }
        peak = max(peak, if (type == ChartType.BURNDOWN) series.startRemaining else series.endTotal)
        return niceCeil(peak)
    }

    internal fun niceCeil(value: Double): Double {
        if (value <= 1.0) return 1.0
        val magnitude = 10.0.pow(floor(log10(value)))
        val normalized = value / magnitude
        val step = listOf(1.0, 1.2, 1.5, 2.0, 2.5, 3.0, 4.0, 5.0, 6.0, 8.0, 10.0).first { normalized <= it + 1e-9 }
        return ceil(step * magnitude)
    }

    fun formatSigned(v: Double): String {
        val sign = if (v < 0) "-" else "+"
        val a = kotlin.math.abs(v)
        return sign + if (a in 0.0001..0.0999) String.format(Locale.US, "%.2f", a) else formatValue(Math.round(a * 10) / 10.0)
    }

    internal fun formatValue(v: Double): String =
        if (v == floor(v)) v.toLong().toString() else String.format(Locale.US, "%.1f", v)

    private fun drawSimple(
        canvas: Canvas,
        plot: Plot,
        series: SprintSeries,
        type: ChartType,
        colors: ChartColors,
        density: Float,
        width: Float,
        height: Float,
    ) {
        drawTrend(canvas, plot, series, type, colors, density)
        val stroke = 5f * density
        val bounds = RectF(4f * density, 4f * density, width - 4f * density, height - 4f * density)
        val placed = ArrayList<RectF>()
        val last = series.last
        when (type) {
            ChartType.BURNDOWN -> {
                val v = vertices(plot, series, Line.REMAINING)
                drawFill(canvas, plot, v, colors.primary, fade = false)
                drawLine(canvas, v, colors.primary, stroke)
                if (last != null && v.isNotEmpty()) {
                    drawNow(canvas, v.last(), colors.primary, colors.onPrimary, colors.plate, formatValue(last.remaining), true, bounds, placed, 1f, density, true)
                }
            }
            ChartType.BURNUP -> {
                val total = vertices(plot, series, Line.TOTAL)
                val done = vertices(plot, series, Line.COMPLETED)
                drawLine(canvas, total, colors.secondary, 3.5f * density)
                drawFill(canvas, plot, done, colors.primary, fade = false)
                drawLine(canvas, done, colors.primary, stroke)
                if (last != null && total.isNotEmpty() && done.isNotEmpty()) {
                    drawNow(canvas, total.last(), colors.secondary, colors.onSecondary, colors.plate, formatValue(last.total), true, bounds, placed, 1f, density, true, preferRight = true)
                    drawNow(canvas, done.last(), colors.primary, colors.onPrimary, colors.plate, formatValue(last.completed), false, bounds, placed, 1f, density, true)
                }
            }
        }
    }

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

    private fun dotted(color: Int, widthPx: Float, gapPx: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = widthPx
        strokeCap = Paint.Cap.ROUND
        pathEffect = DashPathEffect(floatArrayOf(0.01f, gapPx), 0f)
    }

    private fun drawFrame(canvas: Canvas, plot: Plot, series: SprintSeries, colors: ChartColors, density: Float) {
        val grid = dotted(ColorUtils.setAlphaComponent(colors.grid, 110), 2f * density, 7f * density)
        canvas.drawLine(plot.left, plot.bottom, plot.right, plot.bottom, grid)
        plot.gutterLabels?.let { (top, _) ->
            canvas.drawLine(plot.left, plot.top, plot.right, plot.top, grid)
            val label = labelPaint(colors, density).apply { textAlign = Paint.Align.LEFT }
            canvas.drawText(top, plot.left, plot.top - 10f * density, label)
        }
    }

    private fun drawTrend(canvas: Canvas, plot: Plot, series: SprintSeries, type: ChartType, colors: ChartColors, density: Float) {
        val (from, to) = when (type) {
            ChartType.BURNDOWN -> series.startRemaining to 0.0
            ChartType.BURNUP -> series.startCompleted to series.endTotal
        }
        canvas.drawLine(
            plot.x(series.start), plot.y(from), plot.x(series.end), plot.y(to),
            dotted(ColorUtils.setAlphaComponent(colors.trend, 170), 2.5f * density, 9f * density),
        )
    }

    private fun linePath(v: List<PointF>) = Path().apply {
        moveTo(v[0].x, v[0].y)
        for (i in 1 until v.size) lineTo(v[i].x, v[i].y)
    }

    private fun drawLine(canvas: Canvas, v: List<PointF>, color: Int, strokeWidth: Float) {
        if (v.isEmpty()) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        if (v.size == 1) canvas.drawPoint(v[0].x, v[0].y, paint) else canvas.drawPath(linePath(v), paint)
    }

    private fun drawFill(canvas: Canvas, plot: Plot, v: List<PointF>, color: Int, fade: Boolean = true) {
        if (v.size < 2) return
        val area = Path(linePath(v)).apply {
            lineTo(v.last().x, plot.bottom)
            lineTo(v.first().x, plot.bottom)
            close()
        }
        val top = v.minOf { it.y }
        val first = v.first().x
        val last = v.last().x
        if (!fade) {
            canvas.drawPath(area, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, top, 0f, plot.bottom,
                    ColorUtils.setAlphaComponent(color, 90),
                    ColorUtils.setAlphaComponent(color, 0),
                    Shader.TileMode.CLAMP,
                )
            })
            return
        }
        val layer = canvas.saveLayer(plot.left - 40f, 0f, plot.right + 40f, plot.bottom + 4f, null)
        canvas.drawPath(area, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, top, 0f, plot.bottom,
                ColorUtils.setAlphaComponent(color, 110),
                ColorUtils.setAlphaComponent(color, 0),
                Shader.TileMode.CLAMP,
            )
        })
        // fade the fill out before the last dot
        val fadeFrom = last - (last - first) * 0.2f
        canvas.drawRect(plot.left - 40f, 0f, plot.right + 40f, plot.bottom + 4f, Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            shader = LinearGradient(fadeFrom, 0f, last, 0f, Color.BLACK, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        })
        canvas.restoreToCount(layer)
    }

    private fun drawDots(canvas: Canvas, v: List<PointF>, color: Int, radius: Float) {
        val paint = fill(color)
        for (i in 0 until v.size - 1) canvas.drawCircle(v[i].x, v[i].y, radius, paint)
    }

    private fun drawNow(
        canvas: Canvas,
        p: PointF,
        color: Int,
        onColor: Int,
        ring: Int,
        text: String,
        preferAbove: Boolean,
        bounds: RectF,
        placed: MutableList<RectF>,
        alpha: Float,
        density: Float,
        compact: Boolean,
        preferRight: Boolean = false,
    ) {
        fun dp(v: Float) = v * density
        val ringR = dp(if (compact) 8.5f else 10.5f)
        canvas.drawCircle(p.x, p.y, ringR, fill(ring))
        canvas.drawCircle(p.x, p.y, ringR - dp(3f), fill(color))

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = onColor
            textSize = dp(if (compact) 11.5f else 12.5f)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val w = textPaint.measureText(text) + dp(if (compact) 18f else 22f)
        val h = dp(if (compact) 21f else 24f)
        val gap = dp(if (compact) 4f else 6f)
        val reach = ringR + gap
        fun around(cx: Float, cy: Float) = RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
        val above = around(p.x, p.y - reach - h / 2)
        val below = around(p.x, p.y + reach + h / 2)
        val right = around(p.x + reach + w / 2, p.y)
        val left = around(p.x - reach - w / 2, p.y)
        val order = when {
            preferRight -> listOf(right, above, left, below)
            preferAbove -> listOf(above, right, left, below)
            else -> listOf(below, right, left, above)
        }
        val pick = order.firstOrNull { r ->
            r.left >= bounds.left && r.right <= bounds.right && r.top >= bounds.top && r.bottom <= bounds.bottom &&
                placed.none { RectF.intersects(it, r) }
        } ?: order.first().also {
            // nothing fits so push it back inside
            it.offset(
                (bounds.left - it.left).coerceAtLeast(0f) + (bounds.right - it.right).coerceAtMost(0f),
                (bounds.top - it.top).coerceAtLeast(0f) + (bounds.bottom - it.bottom).coerceAtMost(0f),
            )
        }
        placed += pick
        val a = (alpha * 255).toInt()
        val halo = fill(ring).apply { this.alpha = a }
        val grow = dp(3f)
        canvas.drawRoundRect(RectF(pick.left - grow, pick.top - grow, pick.right + grow, pick.bottom + grow), h / 2 + grow, h / 2 + grow, halo)
        val pill = fill(color).apply { this.alpha = a }
        canvas.drawRoundRect(pick, h / 2, h / 2, pill)
        textPaint.alpha = a
        canvas.drawText(text, pick.centerX(), pick.centerY() + textPaint.textSize * 0.35f, textPaint)
    }

    private fun drawXLabels(canvas: Canvas, plot: Plot, series: SprintSeries, colors: ChartColors, width: Float, height: Float, density: Float) {
        val format = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
        val paint = labelPaint(colors, density).apply { if (plot.compact) textSize = 10f * density }
        val baseline = if (plot.compact) height - 9f * density else plot.bottom + 24f * density
        val first = series.start.atZone(series.zone).toLocalDate()
        val lastDay = series.end.atZone(series.zone).toLocalDate().minusDays(1)
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText(format.format(first), plot.left, baseline, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(format.format(lastDay), plot.right, baseline, paint)
    }
}
