package com.shenmao.camera.processing

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc

/**
 * 滤镜画廊缩略图：对一张合成样图（一只可爱卡通小猫）逐一应用 20 个滤镜，缓存为 Bitmap。
 * 与原桌面版「用背景图逐滤镜生成缩略图」同理，但改用离线合成样图，避免打包外部图片。
 */
object Thumbnails {

    private var cache: List<Bitmap>? = null

    fun generate(): List<Bitmap> {
        cache?.let { return it }
        val w = 84
        val h = 63
        val sample = buildSample(w, h)
        val list = Filters.ALL.map { f ->
            val out = FilterProcessor.applyFilter(sample, f)
            val rgba = Mat()
            Imgproc.cvtColor(out, rgba, Imgproc.COLOR_BGR2RGBA)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(rgba, bmp)
            rgba.release()
            if (out !== sample) out.release()
            bmp
        }
        sample.release()
        cache = list
        return list
    }

    /** 合成样图：奶油底 + 一只可爱卡通小猫（与标题栏小猫风格一致），便于直观对比滤镜效果。 */
    private fun buildSample(w: Int, h: Int): Mat {
        val m = Mat(h, w, CvType.CV_8UC3)
        // 奶油底 #f3e5c3（BGR）
        m.setTo(Scalar(195.0, 229.0, 243.0))

        // 配色（BGR）
        val ink = Scalar(24.0, 33.0, 42.0)       // #2a2118
        val head = Scalar(107.0, 197.0, 245.0)   // #f5c56b
        val ear = Scalar(61.0, 163.0, 232.0)     // #e8a33d
        val inner = Scalar(192.0, 184.0, 242.0)  // #f2b8c0
        val blush = Scalar(192.0, 182.0, 244.0)  // #f4b6c0
        val nose = Scalar(127.0, 132.0, 232.0)   // #e8847f
        val white = Scalar(255.0, 255.0, 255.0)

        val cx = w / 2.0
        val cy = h / 2.0 + 1
        val R = minOf(w, h) * 0.30

        fun pt(dx: Double, dy: Double) = Point(cx + dx * R, cy + dy * R)

        // 耳朵（三角 + 内耳）
        Imgproc.fillPoly(m, listOf(MatOfPoint(pt(-0.88, -0.55), pt(-0.45, -1.30), pt(-0.08, -0.70))), ear)
        Imgproc.fillPoly(m, listOf(MatOfPoint(pt(0.88, -0.55), pt(0.45, -1.30), pt(0.08, -0.70))), ear)
        Imgproc.fillPoly(m, listOf(MatOfPoint(pt(-0.62, -0.55), pt(-0.45, -1.02), pt(-0.22, -0.62))), inner)
        Imgproc.fillPoly(m, listOf(MatOfPoint(pt(0.62, -0.55), pt(0.45, -1.02), pt(0.22, -0.62))), inner)

        // 头（椭圆 + 描边）
        Imgproc.ellipse(m, pt(0.0, 0.0), org.opencv.core.Size(R * 1.05, R * 0.95), 0.0, 0.0, 360.0, head, -1)
        Imgproc.ellipse(m, pt(0.0, 0.0), org.opencv.core.Size(R * 1.05, R * 0.95), 0.0, 0.0, 360.0, ink, 1)

        // 眼睛 + 高光
        Imgproc.ellipse(m, pt(-0.38, -0.06), org.opencv.core.Size(R * 0.20, R * 0.24), 0.0, 0.0, 360.0, ink, -1)
        Imgproc.ellipse(m, pt(0.38, -0.06), org.opencv.core.Size(R * 0.20, R * 0.24), 0.0, 0.0, 360.0, ink, -1)
        Imgproc.circle(m, pt(-0.32, -0.14), maxOf(1, (R * 0.06).toInt()), white, -1)
        Imgproc.circle(m, pt(0.44, -0.14), maxOf(1, (R * 0.06).toInt()), white, -1)

        // 鼻子 + 腮红
        Imgproc.fillPoly(m, listOf(MatOfPoint(pt(-0.07, 0.16), pt(0.07, 0.16), pt(0.0, 0.28))), nose)
        Imgproc.ellipse(m, pt(-0.62, 0.26), org.opencv.core.Size(R * 0.26, R * 0.15), 0.0, 0.0, 360.0, blush, -1)
        Imgproc.ellipse(m, pt(0.62, 0.26), org.opencv.core.Size(R * 0.26, R * 0.15), 0.0, 0.0, 360.0, blush, -1)

        // 胡须
        for (dy in listOf(0.12, 0.28, 0.44)) {
            Imgproc.line(m, pt(-1.10, dy), pt(-0.78, dy), ink, 1)
            Imgproc.line(m, pt(1.10, dy), pt(0.78, dy), ink, 1)
        }
        return m
    }
}
