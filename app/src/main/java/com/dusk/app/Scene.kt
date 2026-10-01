package com.dusk.app

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.sin

/**
 * The island at sunset.
 * [sunLevel] 1 = sun high in the sky, 0 = touching the horizon.
 * [gulls] one gull per craving ridden out (up to 12 drawn).
 * [island] 0 bare, 1 palm, 2 hut, 3 boat, 4 lighthouse.
 */
@Composable
fun SunsetScene(
    modifier: Modifier = Modifier,
    sunLevel: Float,
    gulls: Int,
    island: Int,
    animate: Boolean
) {
    val s = skyColors()
    val t = if (animate) {
        rememberInfiniteTransition(label = "sky").animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(40_000, easing = LinearEasing)),
            label = "drift"
        ).value
    } else 0f

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val horizon = h * 0.68f

        drawRect(Brush.verticalGradient(s.sky, startY = 0f, endY = horizon), size = Size(w, horizon))

        val r = h * 0.17f
        val lowest = horizon
        val highest = r * 1.4f
        val sunY = lowest - sunLevel.coerceIn(0f, 1f) * (lowest - highest)
        drawCircle(s.sun.copy(alpha = 0.35f), r * 1.5f, Offset(w * 0.5f, sunY))
        drawCircle(s.sun, r, Offset(w * 0.5f, sunY))

        drawRect(s.sea, topLeft = Offset(0f, horizon), size = Size(w, h - horizon))
        val shimmer = if (animate) sin(t * 6.283f * 4) * 6.dp.toPx() else 0f
        listOf(Triple(0.16f, 0.38f, 0.22f), Triple(0.52f, 0.86f, 0.45f), Triple(0.30f, 0.50f, 0.70f)).forEach { (x1, x2, yf) ->
            val y = horizon + (h - horizon) * yf
            drawLine(
                s.wave, Offset(w * x1 + shimmer, y), Offset(w * x2 + shimmer, y),
                strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round
            )
        }

        // Island
        val ix = w * 0.80f
        val iw = w * 0.17f
        val hump = Path().apply {
            moveTo(ix - iw, horizon + 1f)
            quadraticBezierTo(ix, horizon - h * 0.11f, ix + iw, horizon + 1f)
            close()
        }
        drawPath(hump, s.sand)
        val ground = horizon - h * 0.045f

        if (island >= 1) drawPalm(ix + iw * 0.15f, ground, s.trunk, s.palm)
        if (island >= 2) drawHut(ix - iw * 0.45f, ground + 2.dp.toPx(), s.trunk)
        if (island >= 3) drawBoat(w * 0.22f, horizon + (h - horizon) * 0.32f, s.trunk, s.light)
        if (island >= 4) drawLighthouse(ix + iw * 0.62f, horizon - 1.dp.toPx(), s.light, s.sun)

        // Gulls, one per craving let go
        val n = gulls.coerceIn(0, 12)
        for (i in 0 until n) {
            val base = (i * 0.37f + 0.11f) % 1f
            val speed = 0.6f + (i % 3) * 0.2f
            val x = ((base + t * speed) % 1f) * (w + 40f) - 20f
            val y = horizon * (0.10f + ((i * 0.53f) % 0.45f)) + sin(t * 6.283f * 3 + i) * 4.dp.toPx()
            drawGull(x, y, (5 + (i % 3) * 2).dp.toPx(), s.ink)
        }
    }
}

private fun DrawScope.drawGull(x: Float, y: Float, s: Float, color: Color) {
    val p = Path().apply {
        moveTo(x - s, y)
        quadraticBezierTo(x - s / 2, y - s * 0.7f, x, y)
        quadraticBezierTo(x + s / 2, y - s * 0.7f, x + s, y)
    }
    drawPath(p, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
}

private fun DrawScope.drawPalm(x: Float, ground: Float, trunk: Color, leaf: Color) {
    val top = Offset(x + 4.dp.toPx(), ground - 26.dp.toPx())
    drawLine(trunk, Offset(x, ground), top, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
    val f = 13.dp.toPx()
    listOf(-1f to 0.35f, 1f to 0.35f, -0.8f to -0.45f, 0.8f to -0.45f).forEach { (dx, dy) ->
        val p = Path().apply {
            moveTo(top.x, top.y)
            quadraticBezierTo(top.x + dx * f * 0.6f, top.y - f * 0.6f, top.x + dx * f, top.y + dy * f)
        }
        drawPath(p, leaf, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
    }
}

private fun DrawScope.drawHut(x: Float, ground: Float, wood: Color) {
    val bw = 16.dp.toPx()
    val bh = 10.dp.toPx()
    drawRect(Color(0xFFC98A5A), topLeft = Offset(x - bw / 2, ground - bh), size = Size(bw, bh))
    val roof = Path().apply {
        moveTo(x - bw * 0.7f, ground - bh)
        lineTo(x, ground - bh - 9.dp.toPx())
        lineTo(x + bw * 0.7f, ground - bh)
        close()
    }
    drawPath(roof, wood)
}

private fun DrawScope.drawBoat(x: Float, y: Float, wood: Color, sail: Color) {
    val hw = 15.dp.toPx()
    val hull = Path().apply {
        moveTo(x - hw, y)
        lineTo(x + hw, y)
        lineTo(x + hw * 0.7f, y + 6.dp.toPx())
        lineTo(x - hw * 0.7f, y + 6.dp.toPx())
        close()
    }
    drawPath(hull, wood)
    drawLine(wood, Offset(x, y), Offset(x, y - 20.dp.toPx()), strokeWidth = 2.dp.toPx())
    val s = Path().apply {
        moveTo(x + 1.dp.toPx(), y - 20.dp.toPx())
        lineTo(x + 12.dp.toPx(), y - 3.dp.toPx())
        lineTo(x + 1.dp.toPx(), y - 3.dp.toPx())
        close()
    }
    drawPath(s, sail)
}

private fun DrawScope.drawLighthouse(x: Float, ground: Float, body: Color, glow: Color) {
    val h = 30.dp.toPx()
    val bw = 10.dp.toPx()
    val tw = 6.dp.toPx()
    val tower = Path().apply {
        moveTo(x - bw / 2, ground)
        lineTo(x + bw / 2, ground)
        lineTo(x + tw / 2, ground - h)
        lineTo(x - tw / 2, ground - h)
        close()
    }
    drawPath(tower, body)
    drawRect(Color(0xFFF0765A), topLeft = Offset(x - bw * 0.42f, ground - h * 0.45f), size = Size(bw * 0.84f, 4.dp.toPx()))
    drawCircle(glow, 4.dp.toPx(), Offset(x, ground - h - 3.dp.toPx()))
}

private val TileSkies = listOf(
    listOf(Color(0xFFFFD2B0), Color(0xFFFF9F80)),
    listOf(Color(0xFFFFC6A8), Color(0xFFF07C8C)),
    listOf(Color(0xFFFFE0A3), Color(0xFFF59A6B)),
    listOf(Color(0xFFF9C2C8), Color(0xFFF28B6B)),
    listOf(Color(0xFFFFD8A8), Color(0xFFE8875F)),
    listOf(Color(0xFFFCC9B5), Color(0xFFD9776E)),
)

/** One collected sunset. Each index gets its own colors, so the collection looks different every day. */
@Composable
fun SunsetTile(index: Int, modifier: Modifier = Modifier) {
    val sky = TileSkies[index % TileSkies.size]
    val sea = if (index % 2 == 0) Color(0xFF4F9AA3) else Color(0xFF3E7F8C)
    val sunHeight = 0.2f + (index * 0.29f % 0.5f)
    Canvas(modifier.size(52.dp).clip(RoundedCornerShape(14.dp))) {
        val w = size.width
        val h = size.height
        val horizon = h * 0.64f
        drawRect(Brush.verticalGradient(sky, endY = horizon), size = Size(w, horizon))
        drawCircle(Color(0xFFFFE29A), h * 0.18f, Offset(w * (0.35f + (index * 0.17f % 0.3f)), horizon - h * sunHeight * 0.5f))
        drawRect(sea, topLeft = Offset(0f, horizon), size = Size(w, h - horizon))
        drawLine(Color(0xFF9FD0D4), Offset(w * 0.2f, horizon + h * 0.14f), Offset(w * 0.6f, horizon + h * 0.14f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
    }
}
