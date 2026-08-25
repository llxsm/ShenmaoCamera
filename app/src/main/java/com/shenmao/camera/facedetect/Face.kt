package com.shenmao.camera.facedetect

/**
 * 人脸检测结果（对应原桌面版 YuNet 的 15 列输出，取其中 box + 5 个关键点）。
 *
 * landmarks 长度 10，顺序：
 * [右眼x, 右眼y, 左眼x, 左眼y, 鼻尖x, 鼻尖y, 右嘴角x, 右嘴角y, 左嘴角x, 左嘴角y]
 */
data class Face(
    val x: Int,
    val y: Int,
    val w: Int,
    val h: Int,
    val landmarks: FloatArray,
) {
    /** 右眼坐标（用于大眼）。 */
    val rightEye: FloatArray get() = floatArrayOf(landmarks[0], landmarks[1])

    /** 左眼坐标（用于大眼）。 */
    val leftEye: FloatArray get() = floatArrayOf(landmarks[2], landmarks[3])
}
