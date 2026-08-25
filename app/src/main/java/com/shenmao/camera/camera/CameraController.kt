package com.shenmao.camera.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.RggbChannelVector
import android.util.Size
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs

/**
 * CameraX 封装：
 * - ImageAnalysis 跟随所选分辨率（默认 720p）供实时预览；切高分辨率画质提升、帧率下降；
 * - ImageCapture 按所选分辨率（null=自动最高）拍照，与预览一致；
 * - 支持前置/后置切换、相片比例、目标分辨率；切换时解绑重绑。
 * 参数下发与原桌面版 `_try_set` 语义一致——能设则设，失败静默忽略，亮度由软件增益兜底。
 */
class CameraController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
) {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val providerFuture = ProcessCameraProvider.getInstance(context)
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var analyzer: ImageAnalysis.Analyzer? = null
    private var current: CameraConfig = CameraConfig()

    fun start(analyzer: ImageAnalysis.Analyzer, config: CameraConfig) {
        this.analyzer = analyzer
        this.current = config
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                bind(provider, analyzer, config)
            } catch (_: Exception) {
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /** 配置变化时重绑（前置/后置、比例、分辨率任一变化）。 */
    fun apply(analyzer: ImageAnalysis.Analyzer, config: CameraConfig) {
        this.analyzer = analyzer
        if (config == current) return
        current = config
        try {
            val provider = providerFuture.get()
            provider.unbindAll()
            bind(provider, analyzer, config)
        } catch (_: Exception) {
        }
    }

    private fun bind(provider: ProcessCameraProvider, analyzer: ImageAnalysis.Analyzer, config: CameraConfig) {
        // 预览：跟随所选分辨率（默认 720p），切换后预览帧尺寸随之变化，状态行可见
        val previewTarget = config.resolution ?: Size(1280, 720)
        val analysis = ImageAnalysis.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(aspectFor(config.ratio))
                    .setResolutionStrategy(
                        ResolutionStrategy(previewTarget, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                    )
                    .build()
            )
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        analysis.setAnalyzer(executor, analyzer)

        // 拍照：所选分辨率（null=自动最高）
        val captureBuilder = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
        if (config.resolution != null) {
            captureBuilder.setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(config.resolution, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                    )
                    .build()
            )
        }
        val capture = captureBuilder.build()

        val lens = if (config.frontFacing) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        camera = provider.bindToLifecycle(lifecycleOwner, lens, analysis, capture)
        imageCapture = capture
    }

    private fun aspectFor(ratio: Float): AspectRatioStrategy =
        if (abs(ratio - 3f / 4f) < 0.05f) AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
        else AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY

    /**
     * 拍照：在分析线程（executor，与预览串行）上取一帧 JPEG、解码并旋转为竖屏，
     * 通过回调返回 Bitmap。回调在 executor 线程执行。
     */
    fun takePicture(onResult: (Bitmap?) -> Unit) {
        val cap = imageCapture
        if (cap == null) {
            onResult(null)
            return
        }
        cap.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                var bmp: Bitmap? = null
                try {
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    var decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    val rot = image.imageInfo?.rotationDegrees ?: 0
                    if (decoded != null && rot != 0) {
                        val m = Matrix()
                        m.postRotate(rot.toFloat())
                        val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, m, true)
                        if (rotated !== decoded) decoded.recycle()
                        decoded = rotated
                    }
                    bmp = decoded
                } catch (_: Exception) {
                    bmp = null
                } finally {
                    image.close()
                }
                onResult(bmp)
            }

            override fun onError(exception: ImageCaptureException) {
                onResult(null)
            }
        })
    }

    /** 点击对焦：在表面坐标 (x, y)（0..surfaceWidth / 0..surfaceHeight）触发一次 AF + AE。 */
    fun focusAt(x: Float, y: Float, surfaceWidth: Float, surfaceHeight: Float) {
        val cam = camera ?: return
        try {
            val factory = SurfaceOrientedMeteringPointFactory(surfaceWidth, surfaceHeight)
            val point = factory.createPoint(x, y)
            val action = FocusMeteringAction.Builder(
                point,
                FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
            ).build()
            cam.cameraControl.startFocusAndMetering(action)
        } catch (_: Exception) {
        }
    }

    fun stop() {
        try {
            providerFuture.get()?.unbindAll()
        } catch (_: Exception) {
        }
        executor.shutdown()
    }

    // ---- 参数下发 ----

    fun setAutoMode(auto: Boolean) {
        setInt(CaptureRequest.CONTROL_AE_MODE,
            if (auto) CaptureRequest.CONTROL_AE_MODE_ON else CaptureRequest.CONTROL_AE_MODE_OFF)
        if (auto) {
            setInt(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
        }
    }

    /** v 0..100 -> 曝光时间（ns），0=短(暗) .. 100=长(亮)。 */
    fun setExposure(v: Int) {
        val ns = (2_000_000L + v / 100.0 * 64_666_666L).toLong()
        setLong(CaptureRequest.SENSOR_EXPOSURE_TIME, ns)
    }

    /** v 0..100 -> ISO 100..1000。 */
    fun setIso(v: Int) {
        val iso = 100 + v * 9
        setInt(CaptureRequest.SENSOR_SENSITIVITY, iso)
    }

    /** v 0..100 -> 白平衡：中间自动，偏离则手动色温近似。 */
    fun setWb(v: Int) {
        if (v in 40..60) {
            setInt(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
        } else {
            setInt(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF)
            val r = 1.0 + (v - 50) / 100.0 * 0.6
            val b = 1.0 - (v - 50) / 100.0 * 0.6
            setGains(RggbChannelVector(r.toFloat(), 1f, 1f, b.toFloat()))
        }
    }

    /** v 0..100 -> 对焦距离 0(近)..1(远)，映射 0..10 屈光度。 */
    fun setFocus(v: Int) {
        setFloat(CaptureRequest.LENS_FOCUS_DISTANCE, (v / 100.0 * 10.0).toFloat())
    }

    private fun setInt(key: CaptureRequest.Key<Int>, value: Int) {
        val cam = camera ?: return
        try {
            val control = Camera2CameraControl.from(cam.cameraControl)
            control.captureRequestOptions = CaptureRequestOptions.Builder()
                .setCaptureRequestOption(key, value)
                .build()
        } catch (_: Exception) {
        }
    }

    private fun setLong(key: CaptureRequest.Key<Long>, value: Long) {
        val cam = camera ?: return
        try {
            val control = Camera2CameraControl.from(cam.cameraControl)
            control.captureRequestOptions = CaptureRequestOptions.Builder()
                .setCaptureRequestOption(key, value)
                .build()
        } catch (_: Exception) {
        }
    }

    private fun setFloat(key: CaptureRequest.Key<Float>, value: Float) {
        val cam = camera ?: return
        try {
            val control = Camera2CameraControl.from(cam.cameraControl)
            control.captureRequestOptions = CaptureRequestOptions.Builder()
                .setCaptureRequestOption(key, value)
                .build()
        } catch (_: Exception) {
        }
    }

    private fun setGains(v: RggbChannelVector) {
        val cam = camera ?: return
        try {
            val control = Camera2CameraControl.from(cam.cameraControl)
            control.captureRequestOptions = CaptureRequestOptions.Builder()
                .setCaptureRequestOption(CaptureRequest.COLOR_CORRECTION_GAINS, v)
                .build()
        } catch (_: Exception) {
        }
    }

    companion object {
        /**
         * 枚举当前机型指定镜头（前置/后置）支持的拍照分辨率（JPEG 输出尺寸），
         * 按面积降序、去重、取前 8 档，供 UI 下拉选择。
         */
        fun supportedResolutions(context: Context, front: Boolean): List<Size> {
            val mgr = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return emptyList()
            val target = if (front) CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK
            val set = LinkedHashSet<Size>()
            try {
                for (id in mgr.cameraIdList) {
                    val ch = mgr.getCameraCharacteristics(id) ?: continue
                    if (ch.get(CameraCharacteristics.LENS_FACING) != target) continue
                    val map = ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: continue
                    for (s in map.getOutputSizes(ImageFormat.JPEG) ?: emptyArray()) set.add(s)
                }
            } catch (_: Exception) {
            }
            return set.filter { it.width <= 2560 && it.height <= 2560 }
                .sortedWith(compareByDescending<Size> { it.width * it.height })
                .take(8)
        }
    }
}
