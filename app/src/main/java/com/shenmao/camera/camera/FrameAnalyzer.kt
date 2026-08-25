package com.shenmao.camera.camera

import android.graphics.Bitmap
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.shenmao.camera.facedetect.Face
import com.shenmao.camera.facedetect.FaceDetector
import com.shenmao.camera.processing.ImageProcessingEngine
import com.shenmao.camera.processing.Params
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

/**
 * 帧分析器：ImageProxy(RGBA) → 转正 + 镜像 → 人脸检测 → BGR 处理管线 → Bitmap。
 * 与桌面版 `_loop` 的处理顺序一致。
 */
class FrameAnalyzer(
    private val engine: ImageProcessingEngine,
    private val faceDetector: FaceDetector?,
    private val paramsProvider: () -> Params,
    private val onFrame: (ProcessedFrame) -> Unit,
) : ImageAnalysis.Analyzer {

    private var frameNo = 0
    private var lastFaces: List<Face> = emptyList()
    private val displayBitmaps = arrayOfNulls<Bitmap>(2)
    private var bufIndex = 0
    private var fps = 0.0
    private var lastT = System.nanoTime()

    override fun analyze(image: ImageProxy) {
        val rotation = image.imageInfo?.rotationDegrees ?: 0
        val rgba = imageProxyToRgba(image)
        image.close()

        val p = paramsProvider()

        val upright = Mat()
        rotateInto(rgba, upright, rotation)
        rgba.release()
        if (p.frontFacing) Core.flip(upright, upright, 1)   // 前置镜像，如照镜子；后置不镜像

        // 人脸检测（每 2 帧一次，复用结果）
        frameNo++
        if (faceDetector != null && frameNo % 2 == 1) {
            lastFaces = try {
                faceDetector.detect(upright)
            } catch (e: Exception) {
                emptyList()
            }
        }

        val bgr = Mat()
        Imgproc.cvtColor(upright, bgr, Imgproc.COLOR_RGBA2BGR)
        val outBgr = engine.process(bgr, lastFaces, p)   // 拥有 bgr 所有权

        val outRgba = Mat()
        Imgproc.cvtColor(outBgr, outRgba, Imgproc.COLOR_BGR2RGBA)
        outBgr.release()
        upright.release()

        // 双缓冲 Bitmap，避免下一帧覆盖正在显示的图
        var bmp = displayBitmaps[bufIndex]
        if (bmp == null || bmp.width != outRgba.cols() || bmp.height != outRgba.rows()) {
            bmp = Bitmap.createBitmap(outRgba.cols(), outRgba.rows(), Bitmap.Config.ARGB_8888)
            displayBitmaps[bufIndex] = bmp
        }
        Utils.matToBitmap(outRgba, bmp)
        outRgba.release()
        bufIndex = (bufIndex + 1) % 2

        val now = System.nanoTime()
        val dt = (now - lastT) / 1e9
        if (dt > 0) fps = 0.9 * fps + 0.1 * (1.0 / dt)
        lastT = now

        onFrame(ProcessedFrame(bmp, fps, lastFaces.size, bmp.width, bmp.height))
    }

    private fun rotateInto(src: Mat, dst: Mat, degrees: Int) {
        when (degrees) {
            90 -> Core.rotate(src, dst, Core.ROTATE_90_CLOCKWISE)
            180 -> Core.rotate(src, dst, Core.ROTATE_180)
            270 -> Core.rotate(src, dst, Core.ROTATE_90_COUNTERCLOCKWISE)
            else -> src.copyTo(dst)
        }
    }

    /** 将 RGBA_8888 的 ImageProxy 第一个 plane 拷贝为紧密排列的 CV_8UC4 Mat（处理 rowStride 填充）。 */
    private fun imageProxyToRgba(image: ImageProxy): Mat {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val w = image.width
        val h = image.height
        val mat = Mat(h, w, CvType.CV_8UC4)
        val data = ByteArray(w * h * 4)
        val rowBuf = ByteArray(rowStride)
        for (row in 0 until h) {
            buffer.position(row * rowStride)
            buffer.get(rowBuf, 0, rowStride)
            var dst = row * w * 4
            var src = 0
            for (col in 0 until w) {
                data[dst++] = rowBuf[src]
                data[dst++] = rowBuf[src + 1]
                data[dst++] = rowBuf[src + 2]
                data[dst++] = rowBuf[src + 3]
                src += pixelStride
            }
        }
        mat.put(0, 0, data)
        return mat
    }
}
