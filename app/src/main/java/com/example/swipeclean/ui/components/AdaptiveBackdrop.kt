package com.example.swipeclean.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import com.example.swipeclean.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AdaptiveBackdrop(item: MediaItem?, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    var colors by remember { mutableStateOf(listOf(Color(0xFF111111), Color(0xFF000000))) }

    LaunchedEffect(item?.uri) {
        if (item == null) {
            colors = listOf(Color(0xFF111111), Color(0xFF000000))
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            ctx.contentResolver.openInputStream(item.uri)?.use { input ->
                BitmapFactory.decodeStream(input)?.let { bmp ->
                    val p = Palette.from(bmp).clearFilters().generate()
                    val c1 = p.getDarkMutedColor(0xFF111111.toInt())
                    val c2 = p.getMutedColor(0xFF000000.toInt())
                    colors = listOf(Color(c1), Color(c2))
                }
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = colors))
    ) { content() }
}
