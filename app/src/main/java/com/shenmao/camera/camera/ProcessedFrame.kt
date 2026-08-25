package com.shenmao.camera.camera

import android.graphics.Bitmap

/** 处理后的帧结果（供 Compose 显示 + 状态栏展示）。 */
data class ProcessedFrame(
    val bitmap: Bitmap,
    val fps: Double,
    val faceCount: Int,
    val width: Int,
    val height: Int,
)
