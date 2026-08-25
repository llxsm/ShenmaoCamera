package com.shenmao.camera.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.MediaStore
import android.util.Size
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.shenmao.camera.CameraViewModel
import com.shenmao.camera.camera.CameraController
import com.shenmao.camera.camera.Ratios
import com.shenmao.camera.ui.theme.Palette
import com.shenmao.camera.ui.theme.paletteFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/** 主界面：头栏 + 预览 + 比例/分辨率设置条 + 滤镜画廊 + 拍照/切换/相册/深夜按钮 + 参数面板。 */
@Composable
fun CameraScreen(vm: CameraViewModel) {
    val params by vm.params.collectAsState()
    val frame by vm.frame.collectAsState()
    val resolutions by vm.resolutions.collectAsState()
    val palette = paletteFor(params.isNight)

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var hasPermission by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { hasPermission = it }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        if (granted) hasPermission = true else permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    DisposableEffect(hasPermission) {
        if (hasPermission) {
            val controller = CameraController(context, lifecycleOwner)
            vm.bindCamera(controller)
            onDispose { controller.stop(); vm.unbindCamera() }
        } else {
            onDispose { }
        }
    }

    Column(Modifier.fillMaxSize().background(palette.bg)) {
        HeaderView(params.isNight, palette)

        // 状态行
        val fps = frame?.fps ?: 0.0
        val faces = frame?.faceCount ?: 0
        val fw = frame?.width ?: 0
        val fh = frame?.height ?: 0
        Row(
            Modifier.fillMaxWidth().background(palette.panel).padding(horizontal = 10.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("FPS %.1f".format(fps), color = palette.desc, fontSize = 11.sp)
            Text("人脸 $faces", color = palette.desc, fontSize = 11.sp)
            Text("${fw}×${fh}", color = palette.desc, fontSize = 11.sp)
        }

        // 预览（固定 9:16 比例，居中，超出部分裁剪）
        Box(
            Modifier.fillMaxWidth().weight(1f)
                .background(palette.video)
                .border(3.dp, palette.frame),
            contentAlignment = Alignment.Center,
        ) {
            val bmp = frame?.bitmap
            if (bmp != null) {
                Image(
                    bmp.asImageBitmap(), null,
                    Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(if (hasPermission) "正在打开相机…" else "需要相机权限", color = palette.fg, fontSize = 15.sp)
            }
        }

        // 比例 + 分辨率设置条
        val selectedRes = if (params.resolutionW > 0) Size(params.resolutionW, params.resolutionH) else null
        val ratioResolutions = resolutions.filter { matchesRatio(it, params.aspectRatio) }
        Row(
            Modifier.fillMaxWidth().background(palette.panel).padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("比例", color = palette.desc, fontSize = 12.sp)
            Spacer(Modifier.width(6.dp))
            RatioChips(params.aspectRatio, vm::setAspectRatio, palette)
            Spacer(Modifier.weight(1f))
            ResolutionDropdown(ratioResolutions, selectedRes, vm::setResolution, palette)
        }

        FilterGallery(vm, params, palette)

        // 动作按钮（统一风格）
        Row(
            Modifier.fillMaxWidth().background(palette.panel).padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ActionButton("📷 拍照", palette, accent = true, Modifier.weight(1.3f)) {
                scope.launch {
                    val ok = withContext(Dispatchers.Default) { vm.capture() }
                    snackbarHostState.showSnackbar(if (ok) "已保存到相册「照片」" else "保存失败")
                }
            }
            ActionButton(if (params.frontFacing) "🔄 后置" else "🔄 前置", palette, accent = false, Modifier.weight(1f)) {
                vm.toggleCamera()
            }
            ActionButton("📁 相册", palette, accent = false, Modifier.weight(1f)) {
                openGallery(context)
            }
            ActionButton(if (params.isNight) "☀️ 白天" else "🌙 深夜", palette, accent = false, Modifier.weight(1f)) {
                vm.toggleNight()
            }
        }

        ControlPanel(vm, params, palette)

        SnackbarHost(snackbarHostState)
    }
}

@Composable
private fun ActionButton(
    text: String,
    palette: Palette,
    accent: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier.height(38.dp).clip(RoundedCornerShape(8.dp))
            .background(if (accent) palette.btn else palette.btn2)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = palette.fg, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

/** 比例选择（胶囊按钮组，风格与动作按钮一致）。 */
@Composable
private fun RatioChips(selected: Float, onSelect: (Float) -> Unit, palette: Palette) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Ratios.OPTIONS.forEach { (label, r) ->
            val sel = abs(r - selected) < 0.01f
            Box(
                Modifier.clip(RoundedCornerShape(8.dp))
                    .background(if (sel) palette.btn else palette.btn2)
                    .clickable { onSelect(r) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = palette.fg, fontSize = 12.sp,
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

/** 分辨率下拉（按机型枚举，显示为竖屏「高×宽」）。 */
@Composable
private fun ResolutionDropdown(
    resolutions: List<Size>,
    selected: Size?,
    onSelect: (Size?) -> Unit,
    palette: Palette,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier.clip(RoundedCornerShape(8.dp))
                .background(palette.btn2)
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (selected == null) "自动" else "${selected.height}×${selected.width}",
                color = palette.fg, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = palette.panel) {
            DropdownMenuItem(
                text = { Text("自动", fontSize = 12.sp, color = palette.fg) },
                onClick = { onSelect(null); expanded = false },
            )
            resolutions.forEach { s ->
                DropdownMenuItem(
                    text = { Text("${s.height}×${s.width}", fontSize = 12.sp, color = palette.fg) },
                    onClick = { onSelect(s); expanded = false },
                )
            }
        }
    }
}

/** 判断横屏传感器尺寸是否匹配竖屏比例（9:16→16:9、3:4→4:3、1:1→1:1）。 */
private fun matchesRatio(s: Size, portraitRatio: Float): Boolean {
    val sensorRatio = s.width / s.height.toDouble()
    val target = if (portraitRatio >= 0.99f) 1.0 else 1.0 / portraitRatio
    return abs(sensorRatio - target) < 0.06
}

private fun openGallery(context: Context) {
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, "未找到相册应用", Toast.LENGTH_SHORT).show()
    }
}
