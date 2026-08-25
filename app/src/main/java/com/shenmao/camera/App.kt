package com.shenmao.camera

import android.app.Application
import org.opencv.android.OpenCVLoader

/** 应用入口：在使用任何 Mat 之前加载 OpenCV 原生库。 */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        OpenCVLoader.initLocal()
    }
}
