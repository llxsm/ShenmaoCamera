package com.shenmao.camera

import android.app.Application
import android.graphics.Bitmap
import android.util.Size
import androidx.lifecycle.AndroidViewModel
import com.shenmao.camera.camera.CameraConfig
import com.shenmao.camera.camera.CameraController
import com.shenmao.camera.camera.FrameAnalyzer
import com.shenmao.camera.camera.ProcessedFrame
import com.shenmao.camera.facedetect.FaceDetector
import com.shenmao.camera.processing.Filters
import com.shenmao.camera.processing.ImageProcessingEngine
import com.shenmao.camera.processing.Params
import com.shenmao.camera.storage.PhotoSaver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import kotlin.math.abs

/**
 * 相机/参数/帧结果的单一状态源。UI 只读写 StateFlow，处理管线与相机硬件由这里编排。
 */
class CameraViewModel(application: Application) : AndroidViewModel(application) {

    val engine = ImageProcessingEngine()
    val faceDetector: FaceDetector? = try { FaceDetector() } catch (_: Exception) { null }

    private val _params = MutableStateFlow(Params())
    val params: StateFlow<Params> = _params.asStateFlow()

    private val _frame = MutableStateFlow<ProcessedFrame?>(null)
    val frame: StateFlow<ProcessedFrame?> = _frame.asStateFlow()

    private val _resolutions = MutableStateFlow<List<Size>>(emptyList())
    val resolutions: StateFlow<List<Size>> = _resolutions.asStateFlow()

    private var camera: CameraController? = null

    /** 供 CameraController 绑定：后台线程逐帧分析并回调处理结果。 */
    val analyzer = FrameAnalyzer(engine, faceDetector, { _params.value }, { _frame.value = it })

    fun bindCamera(controller: CameraController) {
        camera = controller
        controller.setAutoMode(!_params.value.isManual)
        controller.start(analyzer, currentConfig())
        refreshResolutions()
    }

    fun unbindCamera() {
        camera = null
    }

    private fun currentConfig(): CameraConfig {
        val p = _params.value
        val res = if (p.resolutionW > 0 && p.resolutionH > 0) Size(p.resolutionW, p.resolutionH) else null
        return CameraConfig(p.frontFacing, p.aspectRatio, res)
    }

    private fun rebindCamera() {
        camera?.apply(analyzer, currentConfig())
    }

    /** 重新枚举当前镜头支持的分辨率（切镜头 / 权限就绪后调用）。 */
    fun refreshResolutions() {
        _resolutions.value = CameraController.supportedResolutions(getApplication(), _params.value.frontFacing)
    }

    private fun update(transform: (Params) -> Params) {
        val old = _params.value
        val new = transform(old)
        _params.value = new
        if (new.isNight != old.isNight) engine.night = new.isNight
    }

    // ---- 美颜 / 画质 / 滤镜 ----
    fun setWhiten(v: Int) = update { it.copy(whiten = v) }
    fun setSlim(v: Int) = update { it.copy(slim = v) }
    fun setEye(v: Int) = update { it.copy(eye = v) }
    fun setClarity(v: Int) = update { it.copy(clarity = v) }
    fun setSharpen(v: Int) = update { it.copy(sharpen = v) }
    fun setBrightness(v: Int) = update { it.copy(brightness = v) }
    fun setContrast(v: Int) = update { it.copy(contrast = v) }
    fun setSaturation(v: Int) = update { it.copy(saturation = v) }
    fun setFilter(i: Int) = update { it.copy(filter = i.coerceIn(0, Filters.ALL.lastIndex)) }

    fun toggleNight() {
        update { it.copy(isNight = !it.isNight) }
    }

    // ---- 相机配置（切换后重绑）----
    fun toggleCamera() {
        update { it.copy(frontFacing = !it.frontFacing, resolutionW = 0, resolutionH = 0) }
        refreshResolutions()
        rebindCamera()
    }

    fun setAspectRatio(r: Float) {
        update { it.copy(aspectRatio = r, resolutionW = 0, resolutionH = 0) }
        rebindCamera()
    }

    fun setResolution(size: Size?) {
        update {
            if (size == null) it.copy(resolutionW = 0, resolutionH = 0)
            else it.copy(resolutionW = size.width, resolutionH = size.height)
        }
        rebindCamera()
    }

    fun setManual(isManual: Boolean) {
        update { it.copy(isManual = isManual) }
        engine.autoExposure = !isManual
        camera?.setAutoMode(!isManual)
    }

    // ---- 手动相机参数（实时下发 + 软件增益兜底）----
    fun setExposure(v: Int) { update { it.copy(exposure = v) }; camera?.setExposure(v) }
    fun setIso(v: Int) { update { it.copy(iso = v) }; engine.manualGain = v / 100.0 * 0.6; camera?.setIso(v) }
    fun setWb(v: Int) { update { it.copy(wb = v) }; camera?.setWb(v) }
    fun setFocus(v: Int) { update { it.copy(focus = v) }; camera?.setFocus(v) }

    /** 点击对焦：nx/ny 为显示画面归一化坐标（0..1，已含裁剪校正；前置会水平镜像回传感器坐标）。 */
    fun focusAt(nx: Float, ny: Float) {
        val f = _frame.value ?: return
        val sx = if (_params.value.frontFacing) 1f - nx else nx
        camera?.focusAt(sx * f.width, ny * f.height, f.width.toFloat(), f.height.toFloat())
    }

    /**
     * 拍照：取全分辨率帧 → 走完整美颜/滤镜管线 → 按所选比例裁剪 → 保存到相册。
     * 挂起直到保存完成（处理在相机分析线程上串行执行，避免与预览抢资源）。
     */
    suspend fun capture(): Boolean = suspendCancellableCoroutine { cont ->
        val cam = camera
        if (cam == null) {
            cont.resumeWith(Result.success(false))
            return@suspendCancellableCoroutine
        }
        val p = _params.value
        cam.takePicture { bmp ->
            var ok = false
            if (bmp != null) {
                try {
                    val processed = processBitmap(bmp, p)
                    val cropped = cropToRatio(processed, p.aspectRatio)
                    ok = PhotoSaver.save(getApplication(), cropped)
                    if (cropped !== processed) cropped.recycle()
                    processed.recycle()
                } catch (_: Exception) {
                    ok = false
                } finally {
                    bmp.recycle()
                }
            }
            cont.resumeWith(Result.success(ok))
        }
    }

    /** 对全分辨率 Bitmap 走完整管线（镜像 → 人脸检测 → 美颜/滤镜），返回处理后 Bitmap。 */
    private fun processBitmap(bmp: Bitmap, p: Params): Bitmap {
        val rgba = Mat()
        Utils.bitmapToMat(bmp, rgba)
        if (p.frontFacing) Core.flip(rgba, rgba, 1)
        val faces = faceDetector?.detect(rgba) ?: emptyList()
        val bgr = Mat()
        Imgproc.cvtColor(rgba, bgr, Imgproc.COLOR_RGBA2BGR)
        rgba.release()
        val outBgr = engine.process(bgr, faces, p)
        val outRgba = Mat()
        Imgproc.cvtColor(outBgr, outRgba, Imgproc.COLOR_BGR2RGBA)
        outBgr.release()
        val out = Bitmap.createBitmap(outRgba.cols(), outRgba.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(outRgba, out)
        outRgba.release()
        return out
    }

    private fun cropToRatio(src: Bitmap, ratio: Float): Bitmap {
        val sw = src.width.toDouble()
        val sh = src.height.toDouble()
        val cur = sw / sh
        if (abs(cur - ratio) < 0.01) return src
        val cw: Double
        val ch: Double
        if (cur > ratio) { cw = sh * ratio; ch = sh } else { cw = sw; ch = sw / ratio }
        val x = ((sw - cw) / 2.0).toInt().coerceAtLeast(0)
        val y = ((sh - ch) / 2.0).toInt().coerceAtLeast(0)
        return Bitmap.createBitmap(src, x, y, cw.toInt().coerceAtLeast(1), ch.toInt().coerceAtLeast(1))
    }
}
