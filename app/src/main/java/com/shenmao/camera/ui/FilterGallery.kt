package com.shenmao.camera.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shenmao.camera.CameraViewModel
import com.shenmao.camera.processing.Filters
import com.shenmao.camera.processing.Params
import com.shenmao.camera.processing.Thumbnails
import com.shenmao.camera.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 滤镜画廊：当前滤镜名 + 描述 + 20 个缩略图的横向画廊，对应桌面版胶片条。
 */
@Composable
fun FilterGallery(vm: CameraViewModel, params: Params, palette: Palette, modifier: Modifier = Modifier) {
    val thumbs by produceState(initialValue = emptyList<Bitmap>()) {
        value = withContext(Dispatchers.Default) { Thumbnails.generate() }
    }
    val current = Filters.ALL[params.filter]

    Column(modifier.fillMaxWidth().background(palette.panel).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(current.name, color = palette.fg, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(current.desc, color = palette.desc, fontSize = 11.sp, maxLines = 1)
        LazyRow(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            itemsIndexed(Filters.ALL) { i, filter ->
                val bmp = thumbs.getOrNull(i)
                val sel = i == params.filter
                Column(
                    Modifier.padding(horizontal = 3.dp).width(54.dp)
                        .clickable { vm.setFilter(i) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.size(50.dp, 40.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(palette.film)
                            .border(
                                if (sel) 2.dp else 0.dp,
                                if (sel) palette.accent else Color.Transparent,
                                RoundedCornerShape(4.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (bmp != null) {
                            Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }
                    }
                    Text(
                        filter.name,
                        color = if (sel) palette.accent else palette.fg,
                        fontSize = 10.sp,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
