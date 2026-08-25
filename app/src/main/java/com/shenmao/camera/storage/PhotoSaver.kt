package com.shenmao.camera.storage

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 将处理后的照片保存到系统相册的「Pictures/照片」目录（对应桌面版 exe 同级的「照片」文件夹）。 */
object PhotoSaver {

    fun save(context: Context, bitmap: Bitmap): Boolean {
        val name = "照片_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date()) + ".jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/照片")
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        return try {
            val out = resolver.openOutputStream(uri) ?: return false
            out.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        } catch (e: Exception) {
            false
        }
    }
}
