package com.shenmao.camera.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shenmao.camera.CameraViewModel
import com.shenmao.camera.processing.Params
import com.shenmao.camera.ui.theme.Palette

/** 单行滑块（标签 + 滑条 + 数值）。 */
@Composable
fun SliderRow(
    label: String,
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
    palette: Palette,
    enabled: Boolean = true,
) {
    Row(
        Modifier.fillMaxWidth().height(40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = palette.fg, fontSize = 13.sp, modifier = Modifier.width(62.dp))
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            enabled = enabled,
            modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
            colors = SliderDefaults.colors(
                thumbColor = palette.accent,
                activeTrackColor = palette.accent,
                inactiveTrackColor = palette.trough,
            ),
        )
        Box(
            Modifier.width(40.dp).clip(RoundedCornerShape(4.dp)).background(palette.trough),
            contentAlignment = Alignment.Center,
        ) {
            Text(value.toString(), color = palette.fg, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** 自动 / 手动模式切换。 */
@Composable
fun ModeToggle(isManual: Boolean, onSelect: (Boolean) -> Unit, palette: Palette) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("模式", color = palette.fg, fontSize = 13.sp, modifier = Modifier.width(62.dp))
        Row {
            listOf(false to "自动", true to "手动").forEach { (m, name) ->
                val sel = isManual == m
                Box(
                    Modifier.padding(horizontal = 4.dp).width(60.dp).height(26.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (sel) palette.accent else palette.trough)
                        .clickable { onSelect(m) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(name, color = if (sel) palette.panel else palette.fg, fontSize = 12.sp)
                }
            }
        }
    }
}

/**
 * 参数面板：三个标签（美颜/画质/相机）+ 对应滑杆，滑动区域可垂直滚动。
 */
@Composable
fun ControlPanel(vm: CameraViewModel, params: Params, palette: Palette, modifier: Modifier = Modifier) {
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("美颜", "画质", "相机")
    Column(modifier.fillMaxWidth().background(palette.panel).padding(horizontal = 10.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            tabs.forEachIndexed { i, name ->
                val sel = i == tab
                Box(
                    Modifier.weight(1f).height(30.dp).padding(horizontal = 2.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (sel) palette.tabSel else palette.tab)
                        .clickable { tab = i },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(name, color = palette.fg, fontSize = 13.sp,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
        Column(
            Modifier.fillMaxWidth().height(132.dp).verticalScroll(rememberScrollState())
        ) {
            when (tab) {
                0 -> {
                    SliderRow("美白", params.whiten, 0..100, vm::setWhiten, palette)
                    SliderRow("瘦脸", params.slim, 0..100, vm::setSlim, palette)
                    SliderRow("大眼", params.eye, 0..100, vm::setEye, palette)
                }
                1 -> {
                    SliderRow("清晰度", params.clarity, 0..100, vm::setClarity, palette)
                    SliderRow("锐化", params.sharpen, 0..100, vm::setSharpen, palette)
                    SliderRow("亮度", params.brightness, -100..100, vm::setBrightness, palette)
                    SliderRow("对比度", params.contrast, -100..100, vm::setContrast, palette)
                    SliderRow("饱和度", params.saturation, -100..100, vm::setSaturation, palette)
                }
                else -> {
                    ModeToggle(params.isManual, vm::setManual, palette)
                    SliderRow("快门", params.exposure, 0..100, vm::setExposure, palette, enabled = params.isManual)
                    SliderRow("ISO", params.iso, 0..100, vm::setIso, palette, enabled = params.isManual)
                    SliderRow("白平衡", params.wb, 0..100, vm::setWb, palette, enabled = params.isManual)
                    SliderRow("聚焦", params.focus, 0..100, vm::setFocus, palette, enabled = params.isManual)
                }
            }
        }
    }
}
