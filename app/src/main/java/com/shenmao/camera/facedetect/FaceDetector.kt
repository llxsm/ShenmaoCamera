package com.shenmao.camera.facedetect

import android.graphics.Bitmap
import android.graphics.PointF
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face as MLFace
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import org.opencv.android.Utils
import org.opencv.core.Mat

/**
 * 人脸检测（ML Kit 离线 bundled，无 Play Services 依赖），封装为同步 detect。
 * 输入为已转正并镜像的 RGBA Mat，输出 box + 5 个关键点（右眼/左眼/鼻尖/右嘴角/左嘴角）。
 */
class FaceDetector {

    private val detector: com.google.mlkit.vision.face.FaceDetector
    private var bitmap: Bitmap? = null

    init {
        val opts = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setMinFaceSize(0.15f)
            .build()
        detector = FaceDetection.getClient(opts)
    }

    /** 同步检测（调用方须在后台线程执行）。 */
    fun detect(rgba: Mat): List<Face> {
        val w = rgba.cols()
        val h = rgba.rows()
        if (w <= 0 || h <= 0) return emptyList()
        var bmp = bitmap
        if (bmp == null || bmp.width != w || bmp.height != h) {
            bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bitmap = bmp
        }
        Utils.matToBitmap(rgba, bmp)
        val image = InputImage.fromBitmap(bmp, 0)
        val faces = try {
            Tasks.await(detector.process(image))
        } catch (e: Exception) {
            emptyList()
        }
        return faces.map { ml ->
            val b = ml.boundingBox
            Face(b.left, b.top, b.width(), b.height(), landmarks(ml))
        }
    }

    private fun landmarks(face: MLFace): FloatArray {
        fun p(id: Int): PointF = face.getLandmark(id)?.position ?: PointF(0f, 0f)
        val rightEye = p(FaceLandmark.RIGHT_EYE)
        val leftEye = p(FaceLandmark.LEFT_EYE)
        val nose = p(FaceLandmark.NOSE_BASE)
        val mouthR = p(FaceLandmark.MOUTH_RIGHT)
        val mouthL = p(FaceLandmark.MOUTH_LEFT)
        return floatArrayOf(
            rightEye.x, rightEye.y,
            leftEye.x, leftEye.y,
            nose.x, nose.y,
            mouthR.x, mouthR.y,
            mouthL.x, mouthL.y,
        )
    }

    fun close() {
        detector.close()
    }
}
