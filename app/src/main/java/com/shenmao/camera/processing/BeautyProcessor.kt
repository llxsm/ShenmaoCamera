package com.shenmao.camera.processing

import com.shenmao.camera.facedetect.Face
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.max

/**
 * 坐标网格缓存（对应 `_base_grid`）：避免每帧重新 mgrid 分配大数组。
 * 返回 (ys, xs) 两张 CV_32FC1，ys[i,j]=i（行/纵坐标），xs[i,j]=j（列/横坐标）。
 */
object GridCache {
    private val cache = HashMap<String, Pair<Mat, Mat>>()

    fun grid(h: Int, w: Int): Pair<Mat, Mat> {
        val key = "$h-$w"
        return cache.getOrPut(key) {
            val ys = Mat(h, w, CvType.CV_32FC1)
            val xs = Mat(h, w, CvType.CV_32FC1)
            val yd = FloatArray(h * w)
            val xd = FloatArray(h * w)
            for (y in 0 until h) {
                val row = y * w
                for (x in 0 until w) {
                    val i = row + x
                    yd[i] = y.toFloat()
                    xd[i] = x.toFloat()
                }
            }
            ys.put(0, 0, yd)
            xs.put(0, 0, xd)
            ys to xs
        }
    }
}

/**
 * 美颜处理：美白、瘦脸 + 大眼（形变场）。
 * 与原桌面版 `whiten` / `build_beauty_warp` 一一对应，全程 BGR。
 * 注：磨皮（smooth_skin）功能已按要求彻底移除。
 */
object BeautyProcessor {

    /** 美白：轻微降饱和 + 提亮。返回新 Mat。 */
    fun whiten(frame: Mat, strength: Int): Mat {
        if (strength <= 0) return frame.clone()
        val hsv = Mat()
        Imgproc.cvtColor(frame, hsv, Imgproc.COLOR_BGR2HSV)
        val ch = ArrayList<Mat>(3)
        Core.split(hsv, ch)

        val sf = Mat()
        ch[1].convertTo(sf, CvType.CV_32FC1)
        Core.multiply(sf, Scalar(1.0 - 0.25 * strength / 100.0), sf)
        sf.convertTo(ch[1], CvType.CV_8UC1)
        sf.release()

        val vf = Mat()
        ch[2].convertTo(vf, CvType.CV_32FC1)
        Core.add(vf, Scalar(12.0 * strength / 100.0), vf)
        vf.convertTo(ch[2], CvType.CV_8UC1)
        vf.release()

        Core.merge(ch, hsv)
        val out = Mat()
        Imgproc.cvtColor(hsv, out, Imgproc.COLOR_HSV2BGR)
        hsv.release()
        releaseAll(ch)
        return out
    }

    /** 构造瘦脸 + 大眼形变场，返回 (map_x, map_y)，供 `Imgproc.remap` 使用。 */
    fun buildBeautyWarp(frameW: Int, frameH: Int, faces: List<Face>, slim: Int, eye: Int): Pair<Mat, Mat> {
        val h = frameH
        val w = frameW
        val sh = h / 2
        val sw = w / 2

        val (ys, xs) = GridCache.grid(sh, sw)
        val dx = Mat.zeros(sh, sw, CvType.CV_32FC1)
        val dy = Mat.zeros(sh, sw, CvType.CV_32FC1)

        for (face in faces) {
            val fx = face.x * 0.5
            val fy = face.y * 0.5
            val fw = face.w * 0.5
            val fh = face.h * 0.5
            val cx = fx + fw / 2.0
            val jawCy = fy + fh * 0.72

            if (slim > 0) {
                val m = Mat.zeros(sh, sw, CvType.CV_32FC1)
                Imgproc.ellipse(m, Point(cx, fy + fh * 0.5),
                    Size(max(3.0, fw * 0.5), max(3.0, fh * 0.55)),
                    0.0, 0.0, 360.0, Scalar(1.0), -1)
                Imgproc.GaussianBlur(m, m, Size(), max(3.0, fw * 0.08))

                val X = Mat(); Core.subtract(xs, Scalar(cx), X)
                val Y = Mat(); Core.subtract(ys, Scalar(jawCy), Y)
                val X2 = Mat(); Core.multiply(X, X, X2)
                val Y2 = Mat(); Core.multiply(Y, Y, Y2)
                val sx = 2.0 * (fw * 0.45) * (fw * 0.45)
                val sy = 2.0 * (fh * 0.40) * (fh * 0.40)
                val Xn = Mat(); Core.multiply(X2, Scalar(1.0 / sx), Xn)
                val Yn = Mat(); Core.multiply(Y2, Scalar(1.0 / sy), Yn)
                val sum = Mat(); Core.add(Xn, Yn, sum)
                val neg = Mat(); Core.multiply(sum, Scalar(-1.0), neg)
                val g = Mat(); Core.exp(neg, g)

                val term = Mat(); Core.multiply(X, g, term)
                Core.multiply(term, m, term)
                Core.multiply(term, Scalar(slim / 100.0 * 0.28), term)
                Core.add(dx, term, dx)

                releaseAll(listOf(m, X, Y, X2, Y2, Xn, Yn, sum, neg, g, term))
            }

            if (eye > 0) {
                val k = eye / 100.0 * 0.30
                for (ei in 0..1) {
                    val ex = face.landmarks[ei * 2].toDouble()
                    val ey = face.landmarks[ei * 2 + 1].toDouble()
                    if (ex <= 0 || ey <= 0) continue
                    val R = max(fw * 0.16, 5.0)

                    val dxe = Mat(); Core.subtract(xs, Scalar(ex), dxe)
                    val dye = Mat(); Core.subtract(ys, Scalar(ey), dye)
                    val dx2 = Mat(); Core.multiply(dxe, dxe, dx2)
                    val dy2 = Mat(); Core.multiply(dye, dye, dy2)
                    val dsum = Mat(); Core.add(dx2, dy2, dsum)
                    val D = Mat(); Core.sqrt(dsum, D)

                    val wt = Mat(); Core.divide(D, Scalar(R), wt)          // D/R
                    Core.multiply(wt, Scalar(-1.0), wt)                     // -D/R
                    Core.add(wt, Scalar(1.0), wt)                           // 1 - D/R
                    Core.min(wt, Scalar(1.0), wt); Core.max(wt, Scalar(0.0), wt) // clip
                    Core.multiply(wt, wt, wt)                               // square

                    val tx = Mat(); Core.multiply(dxe, wt, tx); Core.multiply(tx, Scalar(-k), tx)
                    val ty = Mat(); Core.multiply(dye, wt, ty); Core.multiply(ty, Scalar(-k), ty)
                    Core.add(dx, tx, dx)
                    Core.add(dy, ty, dy)

                    releaseAll(listOf(dxe, dye, dx2, dy2, dsum, D, wt, tx, ty))
                }
            }
        }

        // 位移场放大回原尺寸（×2），并与全尺寸基础网格合成
        val dxFull = Mat()
        Imgproc.resize(dx, dxFull, Size(w.toDouble(), h.toDouble()), 0.0, 0.0, Imgproc.INTER_LINEAR)
        val dyFull = Mat()
        Imgproc.resize(dy, dyFull, Size(w.toDouble(), h.toDouble()), 0.0, 0.0, Imgproc.INTER_LINEAR)
        Core.multiply(dxFull, Scalar(2.0), dxFull)
        Core.multiply(dyFull, Scalar(2.0), dyFull)

        val (fys, fxs) = GridCache.grid(h, w)
        val mapX = Mat(); Core.add(fxs, dxFull, mapX)
        val mapY = Mat(); Core.add(fys, dyFull, mapY)

        dx.release(); dy.release(); dxFull.release(); dyFull.release()
        return mapX to mapY
    }

    private fun releaseAll(list: List<Mat>) {
        for (m in list) m.release()
    }
}
