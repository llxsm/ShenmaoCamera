package com.shenmao.camera.camera

import android.util.Size

/** 可选的相片比例（标签 → 宽/高，竖屏）。 */
object Ratios {
    val OPTIONS = listOf(
        "9:16" to (9f / 16f),
        "3:4" to (3f / 4f),
        "1:1" to 1f,
    )
}

/**
 * 相机绑定配置：前置/后置、相片比例、目标分辨率。
 * `resolution` 为横屏（传感器方向）尺寸；null 表示自动。
 */
data class CameraConfig(
    val frontFacing: Boolean = true,
    val ratio: Float = 9f / 16f,
    val resolution: Size? = null,
)
