package com.totsiaw.nr5g.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** One line on a [LiveChart]. Values are oldest → newest, one per sample; null = no reading. */
data class ChartSeries(val name: String, val color: Color, val values: List<Float?>)

/** Series colors follow the technology, never its rank: LTE is always blue, NR always orange. */
object SeriesColors {
    @Composable fun lte(): Color = if (isSystemInDarkTheme()) Color(0xFF3987E5) else Color(0xFF2A78D6)
    @Composable fun nr(): Color = if (isSystemInDarkTheme()) Color(0xFFD95926) else Color(0xFFEB6834)
}

private val AxisWidth = 34.dp
private val PlotHeight = 112.dp

/**
 * Rolling line chart: newest sample on the right edge. Press or drag across it to
 * read any earlier point. One unit per chart (no second axis).
 */
@Composable
fun LiveChart(
    title: String,
    unit: String,
    series: List<ChartSeries>,
    capacity: Int,
    secondsPerSample: Int,
    modifier: Modifier = Modifier,
) {
    val shown = series.filter { s -> s.values.any { it != null } }
    val count = series.maxOfOrNull { it.values.size } ?: 0
    var scrub by remember { mutableStateOf<Int?>(null) } // slot 0..capacity-1, left → right

    val ink = MiuixTheme.colorScheme.onSurface
    val muted = MiuixTheme.colorScheme.onSurfaceVariantActions
    val grid = muted.copy(alpha = 0.16f)
    val surface = MiuixTheme.colorScheme.surfaceContainer
    val measurer = rememberTextMeasurer()
    val tickStyle = TextStyle(color = muted, fontSize = 10.sp)

    // Index into the value lists for a slot (lists are right-aligned to the newest slot).
    fun indexFor(slot: Int): Int? = (slot - (capacity - count)).takeIf { it in 0 until count }
    val readIndex: Int? = scrub?.let { indexFor(it) } ?: (count - 1).takeIf { it >= 0 }

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        // Header: title on the left, readout (current, or scrubbed) on the right.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(title, color = ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            if (scrub != null && readIndex != null) {
                val ago = (count - 1 - readIndex) * secondsPerSample
                Text(if (ago == 0) "now" else "${ago}s ago", color = muted, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(6.dp))
        // Legend with live values. Two or more series: swatch + name + value.
        // A single series needs no swatch; its name + value is enough.
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (shown.isEmpty()) {
                Text("Waiting for signal…", color = muted, fontSize = 12.sp)
            }
            shown.forEach { s ->
                val v = readIndex?.let { s.values.getOrNull(it) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (shown.size > 1) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(s.color))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text = "${s.name}  ${v?.let { "${it.roundToInt()} $unit" } ?: "no reading"}",
                        color = ink,
                        fontSize = 12.sp,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(PlotHeight)
                .pointerInput(capacity) {
                    detectTapGestures(onPress = { pos ->
                        scrub = slotAt(pos.x, AxisWidth.toPx(), size.width.toFloat(), capacity)
                        tryAwaitRelease()
                        scrub = null
                    })
                }
                .pointerInput(capacity) {
                    detectHorizontalDragGestures(
                        onDragStart = { pos -> scrub = slotAt(pos.x, AxisWidth.toPx(), size.width.toFloat(), capacity) },
                        onDragEnd = { scrub = null },
                        onDragCancel = { scrub = null },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            scrub = slotAt(change.position.x, AxisWidth.toPx(), size.width.toFloat(), capacity)
                        },
                    )
                },
        ) {
            val left = AxisWidth.toPx()
            val top = 6.dp.toPx()
            val bottom = size.height - 6.dp.toPx()
            val plotW = size.width - left
            val all = shown.flatMap { it.values.filterNotNull() }
            if (all.isEmpty() || plotW <= 0f) return@Canvas

            val (lo, hi, step) = niceRange(all.min(), all.max())
            fun y(v: Float) = bottom - (v - lo) / (hi - lo) * (bottom - top)
            fun x(i: Int) = left + plotW * (capacity - count + i) / (capacity - 1).toFloat()

            // Hairline gridlines + tick labels (recessive).
            var t = lo
            while (t <= hi + 0.001f) {
                val gy = y(t)
                drawLine(grid, Offset(left, gy), Offset(size.width, gy), strokeWidth = 1f)
                val label = measurer.measure(t.roundToInt().toString(), tickStyle)
                drawText(label, topLeft = Offset(left - label.size.width - 6.dp.toPx(), gy - label.size.height / 2f))
                t += step
            }

            val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            shown.forEach { s ->
                val path = Path()
                var pen = false
                s.values.forEachIndexed { i, v ->
                    if (v == null) { pen = false; return@forEachIndexed }
                    val p = Offset(x(i), y(v))
                    if (pen) path.lineTo(p.x, p.y) else path.moveTo(p.x, p.y)
                    // A lone reading between gaps would be invisible as a path; dot it.
                    val prevNull = i == 0 || s.values[i - 1] == null
                    val nextNull = i == s.values.lastIndex || s.values[i + 1] == null
                    if (prevNull && nextNull) drawCircle(s.color, radius = 2.dp.toPx(), center = p)
                    pen = true
                }
                drawPath(path, s.color, style = stroke)
            }

            // Crosshair while scrubbing; otherwise mark the newest point of each line.
            val markIndex = if (scrub != null) readIndex else count - 1
            if (scrub != null && markIndex != null) {
                val cx = x(markIndex)
                drawLine(muted.copy(alpha = 0.5f), Offset(cx, top), Offset(cx, bottom), strokeWidth = 1f)
            }
            if (markIndex != null) {
                shown.forEach { s ->
                    val v = s.values.getOrNull(markIndex) ?: return@forEach
                    val c = Offset(x(markIndex), y(v))
                    drawCircle(surface, radius = 6.dp.toPx(), center = c)
                    drawCircle(s.color, radius = 4.dp.toPx(), center = c)
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(start = AxisWidth, top = 4.dp)) {
            Text("${capacity * secondsPerSample / 60} min ago", color = muted, fontSize = 10.sp)
            Spacer(Modifier.weight(1f))
            Text("now", color = muted, fontSize = 10.sp)
        }
    }
}

private fun slotAt(x: Float, left: Float, width: Float, capacity: Int): Int {
    val plotW = (width - left).coerceAtLeast(1f)
    return (((x - left) / plotW) * (capacity - 1)).roundToInt().coerceIn(0, capacity - 1)
}

/** Clean axis bounds: rounds out to a step from 1/2/5/10/20 giving ~3–4 gridlines. */
private fun niceRange(min: Float, max: Float): Triple<Float, Float, Float> {
    var lo = min
    var hi = max
    if (hi - lo < 4f) { lo -= 2f; hi += 2f }
    val step = listOf(1f, 2f, 5f, 10f, 20f, 50f).first { (hi - lo) / it <= 4f }
    lo = floor(lo / step) * step
    hi = ceil(hi / step) * step
    if (hi == lo) hi += step
    return Triple(lo, hi, step)
}
