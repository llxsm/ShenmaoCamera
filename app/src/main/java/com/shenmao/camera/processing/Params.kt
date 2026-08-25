package com.shenmao.camera.processing

/**
 * 全部可调参数（默认值与原桌面版一致）。
 *
 * 相机相关参数（前置/后置、相片比例、分辨率）也放在这里作为单一状态源，
 * 由 CameraController / FrameAnalyzer 读取。
 */
data class Params(
    val whiten: Int = 25,
    val slim: Int = 35,
    val eye: Int = 25,
    val clarity: Int = 0,
    val sharpen: Int = 0,
    val brightness: Int = 0,
    val contrast: Int = 0,
    val saturation: Int = 0,
    val filter: Int = 0,
    val isNight: Boolean = false,
    val isManual: Boolean = false,
    val exposure: Int = 0,
    val iso: Int = 0,
    val wb: Int = 0,
    val focus: Int = 0,

    // ---- 相机配置 ----
    val frontFacing: Boolean = true,   // 前置/后置
    val aspectRatio: Float = 9f / 16f, // 相片比例（宽/高，竖屏）
    val resolutionW: Int = 0,          // 0 = 自动（横屏尺寸）
    val resolutionH: Int = 0,
)
