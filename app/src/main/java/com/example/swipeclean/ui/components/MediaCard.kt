package com.example.swipeclean.ui.components

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.swipeclean.MediaItem
import com.example.swipeclean.VideoPlayer

private const val TAG_MEDIA = "SwipeClean/Media"

@Composable
private fun isVideoItem(item: MediaItem): Boolean {
    val ctx = LocalContext.current
    val realMime = remember(item.uri, item.mimeType, item.isVideo) {
        runCatching { ctx.contentResolver.getType(item.uri) }.getOrNull()
    }
    val isVid = item.isVideo || item.mimeType.startsWith("video/") || (realMime?.startsWith("video/") == true)
    return isVid
}
@Composable
fun MediaCard(
    item: MediaItem?,
    isZenMode: Boolean = false,
    onSwipeEnabledChange: (Boolean) -> Unit = {},
    onLongPress: () -> Unit = {},  // ← Nuevo callback
    modifier: Modifier = Modifier.fillMaxSize()
) {
    if (item == null) {
        Log.w(TAG_MEDIA, "MediaCard: item=null → placeholder")
        Box(modifier, contentAlignment = Alignment.Center) { Text("Sin imagen") }
        return
    }

    val isVideo = isVideoItem(item)
    Log.d(TAG_MEDIA, "render → uri=${item.uri}, mime=${item.mimeType}, isVideo=$isVideo")

    Box(modifier = modifier) {
        if (isVideo) {
            Log.d(TAG_MEDIA, "Video mode → swipeEnabled=true")
            onSwipeEnabledChange(true)
            VideoPlayer(
                uri = item.uri,
                modifier = Modifier.fillMaxSize(),
                autoPlay = true,
                loop = true,
                mute = false,
                showControls = true
            )
        } else {
            Log.d(TAG_MEDIA, "Image mode (ZoomableImage)")
            ZoomableImage(
                item = item,
                modifier = Modifier.fillMaxSize(),
                isZenMode = isZenMode,
                onZoomingChange = { zooming ->
                    Log.d(TAG_MEDIA, "onZoomingChange(zooming=$zooming) → swipeEnabled=${!zooming}")
                    onSwipeEnabledChange(!zooming)
                },
                onLongPress = onLongPress  // ← Pasar callback
            )
        }
    }
}