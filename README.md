# 神猫相机 · Android 版（原生 Kotlin + OpenCV）

Windows 桌面版「神猫相机」（Python + tkinter + OpenCV）的 Android 移植版，**保留全部功能与 UI 设计**：

- 实时镜像预览（CameraX 前置摄像头）
- 美颜：磨皮 / 美白 / 瘦脸 / 大眼
- 画质：清晰度 / 锐化 / 亮度 / 对比度 / 饱和度
- 20 种摄影常用滤镜（原图/自然/鲜艳/黑白/人像/日系/清新/胶片/复古/褪色/暖阳/冷调/蓝调/青橙/黄昏/高对比/柔和/梦幻/银盐/暗角）
- 相机参数：自动 / 手动（快门·ISO·白平衡·聚焦）+ 自动曝光 + 软件增益兜底
- 复古 UI：白天木板 + 眨眼猫 + 相机/胶片/粒子；**深夜模式**：夜空 + 月亮/路灯/睡猫/星星 + 低照度增强
- 拍照保存到系统相册「照片」目录

## 技术栈

| 项 | 选择 |
|----|------|
| UI | Jetpack Compose + Material 3 |
| 相机 | CameraX 1.3.4（`ImageAnalysis` RGBA_8888） |
| 图像处理 | OpenCV Android `org.opencv:opencv:4.10.0`（内部全程 BGR） |
| 人脸检测 | ML Kit `face-detection:16.1.7`（离线 bundled，无 Play Services） |
| 并发 | Kotlin 协程 + 单线程处理 Executor + 双缓冲 Bitmap |

所有图像处理算法均由桌面版 `beauty_camera.py` 中的 cv2+numpy 逐行翻译为 OpenCV Kotlin API，
滤镜参数、美颜强度、形变场、自动曝光（中心加权测光 + 人脸背光补偿 / 夜间增益上限 2.2）等语义完全一致。

## 环境要求

- **Android Studio**（自带 JDK + Gradle + Android SDK；无需额外安装 Java，无需 NDK）
- SDK Platform 35 / Build Tools 34（Studio 打开工程时会自动下载）
- 真机前置摄像头（模拟器相机无法验证曝光/对焦）

## 构建步骤

1. 用 Android Studio 打开本目录 `beauty-camera-android/`。
2. 等待 Gradle Sync 完成（首次会下载依赖，需联网）。
3. 连接真机（开启 USB 调试）或创建模拟器，点击 **Run ▶** 即可安装运行。
4. 生成 APK：`Build → Build Bundle(s) / APK(s) → Build APK(s)`，
   产物在 `app/build/outputs/apk/debug/app-debug.apk`（或 release 需先配签名）。

命令行构建（可选）：首次用 Android Studio 打开并 Sync 后，会生成 Gradle Wrapper（`gradlew`），
之后可在工程根目录执行：

```bash
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

## 工程结构

```
app/src/main/java/com/shenmao/camera/
├── MainActivity.kt              # 入口
├── CameraViewModel.kt           # Params / 帧结果的 StateFlow，参数→引擎/相机编排
├── ui/
│   ├── CameraScreen.kt          # 主界面（头栏/预览/画廊/按钮/参数面板）
│   ├── HeaderView.kt            # 白天木板+猫+相机/胶片+粒子；深夜夜空+月亮/路灯/睡猫/星星
│   ├── ParamSliders.kt          # 美颜/画质/相机三个标签 + 滑杆
│   ├── FilterGallery.kt         # 20 滤镜缩略图 LazyRow
│   └── theme/Palette.kt         # 白天/深夜 16 角色配色
├── camera/                      # CameraController(Camera2Interop 参数下发)、FrameAnalyzer(帧管线)、ProcessedFrame
├── processing/                  # ImageProcessingEngine、BeautyProcessor、EnhanceProcessor、FilterProcessor、Filters(20 滤镜)、Thumbnails
├── facedetect/                  # FaceDetector(ML Kit)、Face(5 关键点)
└── storage/PhotoSaver.kt        # 保存到 MediaStore「照片」
```

## 关键实现说明

- **帧管线**：`ImageProxy(RGBA) → Mat → 转正(rotate) → 镜像(flip) → RGBA→BGR → 处理 → BGR→RGBA → Bitmap → Compose`；
  `STRATEGY_KEEP_ONLY_LATEST` 丢旧帧，人脸检测每 2 帧一次，双缓冲 Bitmap 避免覆盖。
- **缓存**：gamma LUT、暗角 mask、坐标网格、中心加权测光矩阵均在首次使用后缓存，保证流畅。
- **相机参数**：通过 `Camera2Interop` 下发，采用「能设则设，设不了软件增益兜底」语义（与桌面版 `_try_set` 一致）；
  手动 ISO 的软件增益映射为 `manualGain = iso/100×0.6`。
- **人脸关键点**（YuNet → ML Kit）：右眼 `RIGHT_EYE`、左眼 `LEFT_EYE`、鼻尖 `NOSE_BASE`、
  右嘴角 `MOUTH_RIGHT`、左嘴角 `MOUTH_LEFT`（瘦脸/大眼只用这 5 点）。

## 与桌面版的已知差异

- 滤镜画廊缩略图使用**合成样图**（渐变 + 肤色椭圆）生成，而非桌面版的外部「背景.jpg」。
- 手动曝光/ISO/白平衡/聚焦受 OEM 硬件能力限制，部分机型可能不生效（软件增益兜底已处理亮度）。
