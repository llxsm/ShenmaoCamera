package com.shenmao.camera.processing

import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc

/**
 * 滤镜处理（对应 `apply_filter`）。内部全程 BGR，缓存 gamma LUT 与暗角 mask。
 */
object FilterProcessor {

    // gamma LUT 缓存（每 gamma 一条 256 查表）
    private val gammaCache = HashMap<Double, Mat>()

    // 暗角 mask 缓存，key 为 (h, w, strength)
    private val vignetteCache = HashMap<String, Mat>()

    fun applyFilter(bgr: Mat, f: Filter): Mat {
        if (f.identity) return bgr

        // ---- 白平衡（色温 / 色调）-> 通道增益（BGR 顺序）----
        val rGain = 1.0 + f.temp / 220.0 - f.tint / 320.0
        val gGain = 1.0 + f.tint / 420.0
        val bGain = 1.0 - f.temp / 220.0 - f.tint / 320.0

        val imgF = Mat()
        bgr.convertTo(imgF, CvType.CV_32FC3)
        val ch = ArrayList<Mat>(3)
        Core.split(imgF, ch)
        Core.multiply(ch[0], Scalar(bGain), ch[0])
        Core.multiply(ch[1], Scalar(gGain), ch[1])
        Core.multiply(ch[2], Scalar(rGain), ch[2])
        Core.merge(ch, imgF)
        releaseAll(ch)

        val u8 = Mat()
        imgF.convertTo(u8, CvType.CV_8UC3)   // 饱和转换即 clip(0..255)
        imgF.release()

        // ---- 色相偏移 + 饱和度 ----
        val hsv = Mat()
        Imgproc.cvtColor(u8, hsv, Imgproc.COLOR_BGR2HSV)
        val hs = ArrayList<Mat>(3)
        Core.split(hsv, hs)
        if (f.hue != 0.0) {
            val shift = (f.hue / 2.0).toInt()
            val hueLut = Mat(1, 256, CvType.CV_8UC1)
            val lut = ByteArray(256) { ((it + shift) % 180).toByte() }
            hueLut.put(0, 0, lut)
            Core.LUT(hs[0], hueLut, hs[0])
            hueLut.release()
        }
        if (f.sat != 1.0) {
            val sf = Mat()
            hs[1].convertTo(sf, CvType.CV_32FC1)
            Core.multiply(sf, Scalar(f.sat), sf)
            sf.convertTo(hs[1], CvType.CV_8UC1)
            sf.release()
        }
        Core.merge(hs, hsv)
        releaseAll(hs)
        val out = Mat()
        Imgproc.cvtColor(hsv, out, Imgproc.COLOR_HSV2BGR)
        hsv.release()
        u8.release()

        // ---- gamma（查表）----
        if (f.gamma != 1.0) {
            val lut = cachedGamma(f.gamma)
            val g = Mat()
            Core.LUT(out, lut, g)
            out.release()
            // 继续用 g 作为 out
            Core.convertScaleAbs(g, out, f.contrast, f.bright)
            g.release()
        } else {
            Core.convertScaleAbs(out, out, f.contrast, f.bright)
        }

        // ---- 暗角 ----
        if (f.vignette > 0.0) {
            val mask = cachedVignette(out.rows(), out.cols(), f.vignette)
            val outF = Mat()
            out.convertTo(outF, CvType.CV_32FC3)
            val c3 = ArrayList<Mat>(3)
            Core.split(outF, c3)
            Core.multiply(c3[0], mask, c3[0])
            Core.multiply(c3[1], mask, c3[1])
            Core.multiply(c3[2], mask, c3[2])
            Core.merge(c3, outF)
            releaseAll(c3)
            outF.convertTo(out, CvType.CV_8UC3)
            outF.release()
        }
        return out
    }

    private fun cachedGamma(gamma: Double): Mat {
        val g = Math.round(gamma * 100.0) / 100.0
        return gammaCache.getOrPut(g) {
            val lut = Mat(1, 256, CvType.CV_8UC1)
            val data = ByteArray(256) { i ->
                Math.min(255.0, Math.pow(i / 255.0, 1.0 / g) * 255.0).toInt().toByte()
            }
            lut.put(0, 0, data)
            lut
        }
    }

    private fun cachedVignette(h: Int, w: Int, strength: Double): Mat {
        val s = Math.round(strength * 100.0) / 100.0
        val key = "$h-$w-$s"
        return vignetteCache.getOrPut(key) {
            val mask = Mat(h, w, CvType.CV_32FC1)
            val cx = w / 2.0
            val cy = h / 2.0
            val rMax = Math.sqrt(cx * cx + cy * cy)
            val data = FloatArray(h * w)
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val d = Math.sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy)) / rMax
                    val m = 1.0 - s * Math.max(0.0, Math.min(1.0, d - 0.45))
                    data[y * w + x] = Math.max(0.0, Math.min(1.0, m)).toFloat()
                }
            }
            mask.put(0, 0, data)
            mask
        }
    }

    private fun releaseAll(list: List<Mat>) {
        for (m in list) m.release()
    }
}
