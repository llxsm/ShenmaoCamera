package com.shenmao.camera.ui.theme

import androidx.compose.ui.graphics.Color

/** 解析 "#RRGGBB" 为 Compose Color。 */
fun color(hex: String): Color {
    val c = hex.removePrefix("#")
    val v = c.toLong(16)
    return Color(
        red = ((v shr 16) and 0xFF) / 255f,
        green = ((v shr 8) and 0xFF) / 255f,
        blue = (v and 0xFF) / 255f,
        alpha = 1f,
    )
}

// ---- 固定装饰色（白天复古木板）----
val WoodDark = color("#5a3a22")     // 深木板
val WoodMid = color("#8a5a2f")      // 中木板
val WoodLight = color("#b98a5e")    // 浅木板
val Cream = color("#f3e5c3")        // 奶油底
val Paper = color("#efe0bb")        // 羊皮纸
val Ink = color("#4a2f1a")          // 深棕文字
val Gold = color("#e0b052")         // 复古金
val FilmDark = color("#2a2118")     // 胶片深色

/**
 * 16 个主题配色角色，与原桌面版 `PALETTE` 一一对应。
 * 白天 = 复古木板羊皮纸；深夜 = 深蓝夜空。
 */
data class Palette(
    val bg: Color, val panel: Color, val frame: Color, val fg: Color,
    val accent: Color, val trough: Color, val btn: Color, val btn2: Color,
    val tab: Color, val tabSel: Color, val tabActive: Color,
    val film: Color, val sub: Color, val desc: Color,
    val spark: Color, val sprocket: Color, val video: Color,
)

val DayPalette = Palette(
    bg = WoodDark, panel = Paper, frame = Cream, fg = Ink,
    accent = Gold, trough = WoodLight, btn = Gold, btn2 = WoodLight,
    tab = WoodLight, tabSel = Gold, tabActive = WoodMid,
    film = FilmDark, sub = WoodLight, desc = color("#6b4a2f"),
    spark = Gold, sprocket = Cream, video = color("#000000"),
)

val NightPalette = Palette(
    bg = color("#0e1420"), panel = color("#161e30"), frame = color("#121a28"), fg = color("#c9d4e8"),
    accent = color("#8faee0"), trough = color("#2a3550"), btn = color("#3a4a6a"), btn2 = color("#2a3550"),
    tab = color("#22304a"), tabSel = color("#5a7ab0"), tabActive = color("#33507a"),
    film = color("#0a0e18"), sub = color("#6b7da8"), desc = color("#8ba0c8"),
    spark = color("#dbe6ff"), sprocket = color("#3d4a68"), video = color("#000000"),
)

fun paletteFor(night: Boolean) = if (night) NightPalette else DayPalette
