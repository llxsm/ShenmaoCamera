package com.shenmao.camera

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.shenmao.camera.ui.CameraScreen

class MainActivity : ComponentActivity() {

    private val vm: CameraViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CameraScreen(vm)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        vm.faceDetector?.close()
    }
}
