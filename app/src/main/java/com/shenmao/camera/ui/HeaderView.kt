package com.shenmao.camera.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shenmao.camera.ui.theme.Cream
import com.shenmao.camera.ui.theme.FilmDark
import com.shenmao.camera.ui.theme.Gold
import com.shenmao.camera.ui.theme.Ink
import com.shenmao.camera.ui.theme.Palette
import com.shenmao.camera.ui.theme.WoodDark
import com.shenmao.camera.ui.theme.WoodLight
import com.shenmao.camera.ui.theme.WoodMid
import com.shenmao.camera.ui.theme.color
import kotlinx.coroutines.delay

/**
 * 标题栏：白天复古木板 + 眨眼猫 + 相机/胶片/粒子；深夜夜空 + 月亮/路灯/睡猫/星星。
 * 全部为程序化绘制，对应桌面版 `_build_header` 及其装饰函数。
 */
@Composable
fun HeaderView(isNight: Boolean, palette: Palette, modifier: Modifier = Modifier) {
    // 白天小猫眨眼（每 ~2.75s 眨一次，每次 200ms）
    var blink by remember { mutableStateOf(false) }
    LaunchedEffect(isNight) {
        while (true) {
            if (!isNight) {
                delay(2750); blink = true; delay(200); blink = false
            } else {
                delay(3000)
            }
        }
    }

    // 粒子/星星上下浮动相位
    val phase by rememberInfiniteTransition(label = "spark").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing)), label = "phase"
    )

    Box(modifier = modifier.fillMaxWidth().height(72.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val s = size.height / 132f   // 相对桌面 132px 高度的缩放
            if (isNight) {
                drawNightSky(s)
                drawMoon(s)
                drawLamp(s)
                drawSleepingCat(s, palette)
                drawStars(s, phase)
            } else {
                drawWood(s)
                drawCat(s, blink)
                drawCamera(s)
                drawFilm(s)
                drawSparks(s, phase)
            }
        }
        Column(
            Modifier.align(Alignment.CenterStart).padding(start = 92.dp)
        ) {
            Text(
                "神猫相机",
                color = if (isNight) palette.fg else Gold,
                fontSize = 20.sp, fontWeight = FontWeight.Bold,
            )
            Text(
                if (isNight) "SHENMAO · NIGHT MODE" else "SHENMAO · RETRO CAMERA",
                color = if (isNight) palette.sub else WoodLight,
                fontSize = 9.sp, fontStyle = FontStyle.Italic,
            )
        }
    }
}

private fun DrawScope.strokeW(w: Float, s: Float) = (w * s).coerceAtLeast(1f)

private fun DrawScope.poly(pts: List<Offset>, fill: Color, stroke: Color? = null, w: Float = 0f) {
    val p = Path().apply {
        moveTo(pts[0].x, pts[0].y)
        for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
        close()
    }
    drawPath(p, fill)
    if (stroke != null && w > 0f) drawPath(p, stroke, style = Stroke(w))
}

// ---- 白天：木板 ----
private fun DrawScope.drawWood(s: Float) {
    val plank = 22f * s
    var i = 0f
    var row = 0
    while (i < size.height) {
        val shade = if (row % 2 == 0) WoodMid else color("#7c4f29")
        drawRect(shade, Offset(0f, i), Size(size.width, plank))
        drawLine(WoodDark, Offset(0f, i), Offset(size.width, i), strokeW(2f, s))
        for (g in 0 until 4) {
            val gy = i + (5 + g * 4) * s
            drawLine(color("#6b4423"), Offset(0f, gy), Offset(size.width, gy + 2f * s), strokeW(1f, s))
        }
        i += plank
        row++
    }
}

// ---- 白天：小猫 ----
private fun DrawScope.drawCat(s: Float, blink: Boolean) {
    val ink = Ink
    poly(listOf(Offset(40 * s, 48 * s), Offset(50 * s, 12 * s), Offset(78 * s, 34 * s)), color("#e8a33d"), ink, strokeW(2f, s))
    poly(listOf(Offset(130 * s, 48 * s), Offset(120 * s, 12 * s), Offset(92 * s, 34 * s)), color("#e8a33d"), ink, strokeW(2f, s))
    poly(listOf(Offset(48 * s, 42 * s), Offset(55 * s, 22 * s), Offset(68 * s, 34 * s)), color("#f2b8c0"))
    poly(listOf(Offset(122 * s, 42 * s), Offset(115 * s, 22 * s), Offset(102 * s, 34 * s)), color("#f2b8c0"))

    drawOval(color("#f5c56b"), Offset(32 * s, 30 * s), Size(106 * s, 88 * s), style = Stroke(strokeW(3f, s)))

    val dark = color("#2a2118")
    if (blink) {
        drawLine(dark, Offset(58 * s, 76 * s), Offset(82 * s, 76 * s), strokeW(3f, s), StrokeCap.Round)
        drawLine(dark, Offset(98 * s, 76 * s), Offset(122 * s, 76 * s), strokeW(3f, s), StrokeCap.Round)
    } else {
        drawOval(dark, Offset(58 * s, 64 * s), Size(24 * s, 24 * s))
        drawOval(dark, Offset(98 * s, 64 * s), Size(24 * s, 24 * s))
        drawOval(Color.White, Offset(70 * s, 68 * s), Size(8 * s, 8 * s))
        drawOval(Color.White, Offset(110 * s, 68 * s), Size(8 * s, 8 * s))
    }

    poly(listOf(Offset(82 * s, 90 * s), Offset(88 * s, 90 * s), Offset(85 * s, 96 * s)), color("#e8847f"))
    drawArc(ink, 200f, 140f, false, Offset(74 * s, 90 * s), Size(10 * s, 10 * s), style = Stroke(strokeW(2f, s)))
    drawArc(ink, 200f, 140f, false, Offset(86 * s, 90 * s), Size(10 * s, 10 * s), style = Stroke(strokeW(2f, s)))
    drawOval(color("#f4b6c0"), Offset(50 * s, 88 * s), Size(16 * s, 10 * s))
    drawOval(color("#f4b6c0"), Offset(104 * s, 88 * s), Size(16 * s, 10 * s))

    for (y in listOf(74f, 84f, 94f)) {
        drawLine(ink, Offset(26 * s, y * s), Offset(50 * s, y * s), strokeW(2f, s))
        drawLine(ink, Offset(120 * s, y * s), Offset(144 * s, y * s), strokeW(2f, s))
    }
}

// ---- 白天：相机 ----
private fun DrawScope.drawCamera(s: Float) {
    val ink = Ink
    val x0 = (size.width - 175f * s).coerceAtLeast(150f * s)
    drawRect(WoodLight, Offset(x0, 20 * s), Size(150 * s, 80 * s), style = Stroke(strokeW(2f, s)))
    drawRect(WoodMid, Offset(x0 + 10 * s, 12 * s), Size(48 * s, 24 * s), style = Stroke(strokeW(2f, s)))
    drawOval(FilmDark, Offset(x0 + 45 * s, 46 * s), Size(60 * s, 60 * s), style = Stroke(strokeW(3f, s)))
    drawOval(color("#6b4423"), Offset(x0 + 58 * s, 59 * s), Size(34 * s, 34 * s), style = Stroke(strokeW(2f, s)))
    drawOval(Gold, Offset(x0 + 68 * s, 69 * s), Size(14 * s, 14 * s))
    drawOval(Gold, Offset(x0 + 120 * s, 76 * s), Size(26 * s, 24 * s), style = Stroke(strokeW(2f, s)))
}

// ---- 白天：胶片 ----
private fun DrawScope.drawFilm(s: Float) {
    val y0 = 106f * s
    val x0 = (size.width - 205f * s).coerceAtLeast(150f * s)
    drawRect(FilmDark, Offset(x0, y0), Size(200 * s, 16 * s))
    for (i in 0 until 10) {
        val px = x0 + (4 + i * 20) * s
        drawRect(Cream, Offset(px, y0 + 1 * s), Size(8 * s, 6 * s))
        drawRect(Cream, Offset(px, y0 + 9 * s), Size(8 * s, 6 * s))
    }
}

// ---- 白天：粒子 ----
private fun DrawScope.drawSparks(s: Float, phase: Float) {
    for (i in 0 until 6) {
        val baseX = 210f + i * 95f
        val speed = (0.5f + (i % 3) * 0.3f)
        var y = (30f + (i * 37) % 85) - phase * 120f * speed
        if (y < 6f) y = 120f + y
        val bright = ((y.toInt() / 8) % 2) == 0
        drawCircle(if (bright) Gold else color("#f7d98b"), radius = 3f * s, center = Offset(baseX * s, y * s))
    }
}

// ---- 深夜：天空 ----
private fun DrawScope.drawNightSky(s: Float) {
    var i = 0
    while (i < size.height.toInt()) {
        val t = i / 132f
        val col = Color(
            (14 + t * 7) / 255f, (20 + t * 9) / 255f, (32 + t * 13) / 255f, 1f
        )
        drawLine(col, Offset(0f, i.toFloat()), Offset(size.width, i.toFloat()), 1f)
        i++
    }
}

// ---- 深夜：月亮 ----
private fun DrawScope.drawMoon(s: Float) {
    val cx = (size.width - 95f * s).coerceAtLeast(150f * s)
    val cy = 38f * s
    val r = 22f * s
    val glow = listOf(r + 16f * s to color("#161f30"), r + 10f * s to color("#1a2438"), r + 5f * s to color("#232f46"))
    for ((rr, col) in glow) drawCircle(col, radius = rr, center = Offset(cx, cy))
    drawCircle(color("#9fb4cc"), radius = r, center = Offset(cx, cy))
    drawCircle(color("#c3d4e6"), radius = r - 5f * s, center = Offset(cx + 6f * s, cy + 5f * s))
}

// ---- 深夜：路灯 ----
private fun DrawScope.drawLamp(s: Float) {
    val x = (size.width - 125f * s).coerceAtLeast(150f * s)
    val pole = color("#2c3a58")
    drawLine(pole, Offset(x, 132f * s), Offset(x, 58f * s), strokeW(5f, s))
    drawLine(pole, Offset(x - 24f * s, 58f * s), Offset(x + 6f * s, 58f * s), strokeW(5f, s))
    val halo = listOf(34f * s to color("#141c2c"), 26f * s to color("#182133"), 18f * s to color("#1d2940"))
    for ((rr, col) in halo) drawCircle(col, radius = rr, center = Offset(x + 4f * s, 58f * s))
    poly(
        listOf(Offset(x - 6f * s, 58f * s), Offset(x + 14f * s, 58f * s), Offset(x + 10f * s, 40f * s), Offset(x - 2f * s, 40f * s)),
        pole
    )
    drawOval(color("#e6c27a"), Offset(x - 1f * s, 44f * s), Size(10f * s, 10f * s))
}

// ---- 深夜：睡猫 ----
private fun DrawScope.drawSleepingCat(s: Float, palette: Palette) {
    val ink = palette.sub
    drawOval(color("#c99a56"), Offset(30f * s, 58f * s), Size(122f * s, 64f * s), style = Stroke(strokeW(2f, s)))
    drawOval(color("#e8b878"), Offset(36f * s, 40f * s), Size(84f * s, 56f * s), style = Stroke(strokeW(2f, s)))
    poly(listOf(Offset(44 * s, 46 * s), Offset(54 * s, 20 * s), Offset(70 * s, 40 * s)), color("#c99a56"), ink, strokeW(2f, s))
    poly(listOf(Offset(112 * s, 46 * s), Offset(102 * s, 20 * s), Offset(86 * s, 40 * s)), color("#c99a56"), ink, strokeW(2f, s))

    drawArc(ink, 200f, 140f, false, Offset(52 * s, 62 * s), Size(22 * s, 12 * s), style = Stroke(strokeW(2f, s)))
    drawArc(ink, 200f, 140f, false, Offset(86 * s, 62 * s), Size(22 * s, 12 * s), style = Stroke(strokeW(2f, s)))
    poly(listOf(Offset(78 * s, 78 * s), Offset(84 * s, 78 * s), Offset(81 * s, 84 * s)), color("#d8907f"))
    drawOval(color("#d99a94"), Offset(48 * s, 74 * s), Size(12 * s, 8 * s))
    drawOval(color("#d99a94"), Offset(100 * s, 74 * s), Size(12 * s, 8 * s))

    for (y in listOf(70f, 78f, 86f)) {
        drawLine(ink, Offset(32 * s, y * s), Offset(48 * s, y * s), strokeW(1f, s))
        drawLine(ink, Offset(112 * s, y * s), Offset(128 * s, y * s), strokeW(1f, s))
    }
    drawArc(ink, 180f, 180f, false, Offset(122 * s, 88 * s), Size(36 * s, 40 * s), style = Stroke(strokeW(3f, s)))
}

// ---- 深夜：星星 ----
private fun DrawScope.drawStars(s: Float, phase: Float) {
    for (i in 0 until 7) {
        val x = 210f + i * 90f
        val speed = (0.4f + (i % 3) * 0.2f)
        var y = (18f + (i * 53) % 90) - phase * 100f * speed
        if (y < 6f) y = 100f + y
        val bright = ((y.toInt() / 8) % 2) == 0
        drawCircle(if (bright) color("#dbe6ff") else color("#6b7da8"), radius = 2f * s, center = Offset(x * s, y * s))
    }
}
