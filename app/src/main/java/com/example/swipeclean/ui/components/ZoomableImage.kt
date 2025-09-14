package com.example.swipeclean.ui.components

import android.util.Log
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.swipeclean.MediaItem
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private const val TAG_ZOOM = "SwipeClean/Zoom"
private const val DEBUG_VERBOSE = false

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
        var scale  by remember { mutableStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }

        LaunchedEffect(Unit) {
            Log.d(TAG_ZOOM, "init for ${item.uri} → onZoomingChange(false)")
            onZoomingChange(false)
        }

        fun normalizeScale(raw: Float): Float {
            val EPS = 0.02f
            return if (raw < 1f + EPS) 1f else raw.coerceIn(minScale, maxScale)
        }

        LaunchedEffect(Unit) {
            snapshotFlow { scale }
                .map { s -> normalizeScale(s) > 1f }
                .distinctUntilChanged()
                .collect { zooming ->
                    Log.d(TAG_ZOOM, "onZoomingChange($zooming)")
                    onZoomingChange(zooming)
                }
        }

        val ctx = LocalContext.current

        Box(
            modifier = modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(cornerRadius))
                .pointerInput(scale) {
                    detectTransformGestures(panZoomLock = true) { _, pan, zoom, _ ->
                        val nextScale = normalizeScale(scale * zoom)
                        if (nextScale != scale) {
                            if (DEBUG_VERBOSE) Log.v(TAG_ZOOM, "scale: ${"%.2f".format(scale)} → ${"%.2f".format(nextScale)}")
                            scale = nextScale
                            if (nextScale == 1f) offset = Offset.Zero
                        }
                        if (pan != Offset.Zero && nextScale > 1f) {
                            offset += pan
                            if (DEBUG_VERBOSE) Log.v(TAG_ZOOM, "pan dx=${"%.1f".format(pan.x)} dy=${"%.1f".format(pan.y)} → off=(${offset.x.toInt()},${offset.y.toInt()})")
                        }
                    }
                }
                .pointerInput(scale) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (scale <= 1f) {
                                Log.d(TAG_ZOOM, "doubleTap → zoom IN")
                                scale = 2.5f.coerceIn(minScale, maxScale)
                            } else {
                                Log.d(TAG_ZOOM, "doubleTap → reset")
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
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                        clip = true
                    },
                contentScale = ContentScale.Fit
            )
        }
    }
}
