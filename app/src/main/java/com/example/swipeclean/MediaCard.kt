package com.example.swipeclean

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import coil.size.Size

@Composable
fun MediaCard(item: MediaItem?) {
    val TAG = "SwipeClean/MediaCard"
    val ctx = LocalContext.current

    if (item == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Sin imagen") }
        return
    }

    val realMime = remember(item.uri) {
        runCatching { ctx.contentResolver.getType(item.uri) }.getOrNull()
    }
    val isVideo = item.isVideo ||
            item.mimeType.startsWith("video/") ||
            (realMime?.startsWith("video/") == true)

    Log.d(TAG, "render → uri=${item.uri}, itemMime=${item.mimeType}, crMime=$realMime, isVideo=$isVideo")

    if (isVideo) {
        VideoPlayer(
            uri = item.uri,
            modifier = Modifier.fillMaxSize(),
            autoPlay = true,
            loop = true,
            mute = false,
            showControls = true
        )
    } else {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(ctx)
                .data(item.uri)
                .size(Size.ORIGINAL)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            loading = {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            },
            error = {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Error al cargar", style = MaterialTheme.typography.bodyMedium)
                }
            }
        )
    }
}
