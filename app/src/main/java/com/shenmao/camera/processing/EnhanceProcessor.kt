package com.shenmao.camera.processing

import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * 画质增强（对应 `enhance`）：清晰度 / 锐化（反锐化掩模）、亮度、对比度、饱和度。
 */
object EnhanceProcessor {

    fun enhance(frame: Mat, clarity: Int, sharpen: Int, brightness: Int, contrast: Int, saturation: Int): Mat {
        var cur = frame
        if (clarity > 0 || sharpen > 0) {
            if (clarity > 0) {
                val blur = Mat()
                Imgproc.GaussianBlur(cur, blur, Size(), 3.0)
                val out = Mat()
                Core.addWeighted(cur, 1.0 + clarity / 100.0 * 0.9, blur, -clarity / 100.0 * 0.9, 0.0, out)
                blur.release()
                releaseOld(cur, frame)
                cur = out
            }
            if (sharpen > 0) {
                val blur2 = Mat()
                Imgproc.GaussianBlur(cur, blur2, Size(), 1.2)
                val out = Mat()
                Core.addWeighted(cur, 1.0 + sharpen / 100.0 * 0.6, blur2, -sharpen / 100.0 * 0.6, 0.0, out)
                blur2.release()
                releaseOld(cur, frame)
                cur = out
            }
        }
        if (brightness != 0 || contrast != 0) {
            val alpha = 1.0 + contrast / 100.0
            val out = Mat()
            Core.convertScaleAbs(cur, out, alpha, brightness.toDouble())
            releaseOld(cur, frame)
            cur = out
        }
        if (saturation != 0) {
            val hsv = Mat()
            Imgproc.cvtColor(cur, hsv, Imgproc.COLOR_BGR2HSV)
            val ch = ArrayList<Mat>(3)
            Core.split(hsv, ch)
            val sf = Mat()
            ch[1].convertTo(sf, CvType.CV_32FC1)
            Core.multiply(sf, Scalar(1.0 + saturation / 100.0), sf)
            sf.convertTo(ch[1], CvType.CV_8UC1)
            sf.release()
            Core.merge(ch, hsv)
            for (c in ch) c.release()
            val out = Mat()
            Imgproc.cvtColor(hsv, out, Imgproc.COLOR_HSV2BGR)
            hsv.release()
            releaseOld(cur, frame)
            cur = out
        }
        return cur
    }

    /** 释放中间结果（但绝不释放传入的原始 frame）。 */
    private fun releaseOld(cur: Mat, original: Mat) {
        if (cur !== original) cur.release()
    }
}
