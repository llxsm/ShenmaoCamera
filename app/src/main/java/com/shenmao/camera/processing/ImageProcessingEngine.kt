package com.shenmao.camera.processing

import com.shenmao.camera.facedetect.Face
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * 每帧处理管线编排 + 自动曝光状态（对应原 `Camera` + `_loop` 的处理部分）。
 *
 * 约定：process() 拥有传入 Mat 的所有权（会就地修改 / 替换并释放），返回最终结果（调用方负责释放）。
 * 内部全程 BGR，仅在边界（进出）与 RGBA 互转。
 */
class ImageProcessingEngine {

    var autoExposure = true
    var autoGain = 1.0
    var manualGain = 0.0      // 手动 ISO 的软件增益兜底
    var night = false

    private var lumaWgt: Mat? = null
    private var lumaWsum = 1.0

    fun process(frameBgr: Mat, faces: List<Face>, p: Params): Mat {
        if (autoExposure) {
            autoExpose(frameBgr, faces.firstOrNull())
        }

        var cur = applyGain(frameBgr)
        if (cur !== frameBgr) frameBgr.release()

        val whitened = BeautyProcessor.whiten(cur, p.whiten)
        cur.release()
        cur = whitened

        if (p.slim > 0 || p.eye > 0) {
            val (mx, my) = BeautyProcessor.buildBeautyWarp(cur.cols(), cur.rows(), faces, p.slim, p.eye)
            val warped = Mat()
            Imgproc.remap(cur, warped, mx, my, Imgproc.INTER_LINEAR, Core.BORDER_REPLICATE)
            mx.release(); my.release(); cur.release()
            cur = warped
        }

        val filtered = FilterProcessor.applyFilter(cur, Filters.ALL[p.filter])
        if (filtered !== cur) { cur.release(); cur = filtered }

        val enhanced = EnhanceProcessor.enhance(
            cur, p.clarity, p.sharpen, p.brightness, p.contrast, p.saturation
        )
        if (enhanced !== cur) { cur.release(); cur = enhanced }
        return cur
    }

    // ---- 自动曝光（中心加权 luma + 人脸背光补偿 / 夜间低照度增强）----
    fun autoExpose(frame: Mat, face: Face?) {
        if (night) {
            val base = centerLuma(frame)
            val target = 0.55
            val boost = clamp(1.0 + (target - base) * 2.0, 1.0, 2.2)
            autoGain = clamp(autoGain + (boost - autoGain) * 0.35, 1.0, 2.2)
            return
        }
        val base = centerLuma(frame)
        var boost = 1.0
        if (face != null) {
            val x = max(0, face.x)
            val y = max(0, face.y)
            val x2 = min(frame.cols(), face.x + face.w)
            val y2 = min(frame.rows(), face.y + face.h)
            if (x2 - x > 10 && y2 - y > 10) {
                val sub = frame.submat(y, y2, x, x2)
                val g = Mat()
                Imgproc.cvtColor(sub, g, Imgproc.COLOR_BGR2GRAY)
                val faceLuma = Core.mean(g).`val`[0] / 255.0
                g.release(); sub.release()
                if (faceLuma < base - 0.10) {
                    boost = clamp(1.0 + (base - faceLuma - 0.10) * 1.0, 1.0, 1.25)
                }
            }
        }
        autoGain = clamp(autoGain + (boost - autoGain) * 0.25, 1.0, 1.25)
    }

    /** 软件增益：自动模式用 autoGain，手动模式用 manualGain（不叠加）。返回新 Mat（增益≈1 时返回原 Mat）。 */
    fun applyGain(frame: Mat): Mat {
        val g = if (autoExposure) autoGain else (1.0 + manualGain)
        val gg = max(0.3, min(3.0, g))
        if (abs(gg - 1.0) < 0.01) return frame
        val out = Mat()
        Core.convertScaleAbs(frame, out, gg, 0.0)
        return out
    }

    private fun centerLuma(frame: Mat): Double {
        val gray = Mat()
        Imgproc.cvtColor(frame, gray, Imgproc.COLOR_BGR2GRAY)
        val g = Mat()
        Imgproc.resize(gray, g, Size(64.0, 48.0), 0.0, 0.0, Imgproc.INTER_AREA)
        gray.release()

        if (lumaWgt == null) {
            val h = g.rows()
            val w = g.cols()
            val wgt = Mat(h, w, CvType.CV_32FC1)
            val data = FloatArray(h * w)
            val sigma = min(w, h) * 0.4
            val denom = 2.0 * sigma * sigma
            var sum = 0.0
            for (y in 0 until h) {
                val row = y * w
                for (x in 0 until w) {
                    val v = exp(-((x - w / 2.0) * (x - w / 2.0) + (y - h / 2.0) * (y - h / 2.0)) / denom)
                    data[row + x] = v.toFloat()
                    sum += v
                }
            }
            wgt.put(0, 0, data)
            lumaWgt = wgt
            lumaWsum = sum
        }

        val gf = Mat()
        g.convertTo(gf, CvType.CV_32FC1)
        Core.multiply(gf, lumaWgt, gf)
        val total = Core.sumElems(gf).`val`[0]
        gf.release(); g.release()
        return (total / lumaWsum) / 255.0
    }

    private fun clamp(v: Double, lo: Double, hi: Double) = max(lo, min(hi, v))
}
