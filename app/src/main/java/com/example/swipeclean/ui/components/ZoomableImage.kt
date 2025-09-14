package com.example.swipeclean.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.swipeclean.MediaItem
import kotlin.math.max
@Composable
fun ZoomableImage(
    item: MediaItem,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    minScale: Float = 1f,
    maxScale: Float = 5f,
    onZoomingChange: (Boolean) -> Unit = {}
) {
    key(item.uri) {
        var containerSize by remember { mutableStateOf(IntSize.Zero) }
        var contentSize   by remember { mutableStateOf(IntSize.Zero) }

        var scale  by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }

        LaunchedEffect(scale) { onZoomingChange(scale > 1.01f) }

        fun clampOffset(s: Float, off: Offset): Offset {
            if (containerSize == IntSize.Zero || contentSize == IntSize.Zero) return off
            val cw = containerSize.width.toFloat()
            val ch = containerSize.height.toFloat()
            val iw = contentSize.width * s
            val ih = contentSize.height * s
            val maxX = max(0f, (iw - cw) / 2f)
            val maxY = max(0f, (ih - ch) / 2f)
            return Offset(off.x.coerceIn(-maxX, maxX), off.y.coerceIn(-maxY, maxY))
        }

        val ctx = LocalContext.current

        Box(
            modifier = modifier
                .fillMaxSize()
                .onSizeChanged { containerSize = it }
                .clip(RoundedCornerShape(cornerRadius))
                .pointerInput(Unit) {
                    // ACTIVAR SIEMPRE: pinch/zoom/pan desde 1×
                    detectTransformGestures(panZoomLock = true) { _, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(minScale, maxScale)
                        if (newScale != scale) {
                            scale = newScale
                        }
                        if (pan != Offset.Zero) {
                            offset = clampOffset(scale, offset + pan)
                        }
                    }
                }
                .pointerInput(scale) {
                    // Doble-tap: 1× ↔ 2.5× (sin while(true), sin reset al terminar)
                    detectTapGestures(
                        onDoubleTap = {
                            if (scale < 1.5f) {
                                scale = 2.5f.coerceIn(minScale, maxScale)
                                offset = clampOffset(scale, offset) // opcional: centrar al tap
                            } else {
                                scale = 1f
                                offset = Offset.Zero
                            }
                        }
                    )
                }
        ) {
            AsyncImage(
                model = ImageRequest.Builder(ctx).data(item.uri).crossfade(true).build(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
                onSuccess = {
                    val d = it.result.drawable
                    contentSize = IntSize(max(1, d.intrinsicWidth), max(1, d.intrinsicHeight))
                }
            )
        }
    }
}
