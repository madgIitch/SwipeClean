package com.example.swipeclean.ui.components

import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.swipeclean.MediaItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

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
        val scope = rememberCoroutineScope()

        val scaleA = remember { Animatable(1f) }
        val offsetXA = remember { Animatable(0f) }
        val offsetYA = remember { Animatable(0f) }

        var scaleRaw by remember { mutableStateOf(1f) }
        var offsetRaw by remember { mutableStateOf(Offset.Zero) }

        var runningAnim: Job? by remember { mutableStateOf(null) }
        suspend fun cancelRunning() {
            runningAnim?.cancelAndJoin()
            runningAnim = null
        }

        LaunchedEffect(Unit) {
            Log.d(TAG_ZOOM, "init for ${item.uri} → onZoomingChange(false)")
            onZoomingChange(false)
        }

        LaunchedEffect(Unit) {
            snapshotFlow { scaleA.value }
                .map { it > 1.01f }
                .distinctUntilChanged()
                .collect { zooming ->
                    Log.d(TAG_ZOOM, "onZoomingChange($zooming)")
                    onZoomingChange(zooming)
                }
        }

        fun clampScale(s: Float) = s.coerceIn(minScale, maxScale)

        suspend fun animateTo(scale: Float, offset: Offset) {
            cancelRunning()
            runningAnim = scope.launch {
                scaleA.animateTo(
                    scale,
                    spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.9f)
                )
            }
            runningAnim = scope.launch {
                offsetXA.animateTo(
                    offset.x,
                    spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.9f)
                )
            }
            runningAnim = scope.launch {
                offsetYA.animateTo(
                    offset.y,
                    spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.9f)
                )
            }
        }

        val ctx = LocalContext.current

        Box(
            modifier = modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(cornerRadius))
                .pointerInput(item.uri) {
                    detectTransformGestures(
                        panZoomLock = true,
                        onGesture = { centroid, pan, zoom, _ ->
                            val newScale = clampScale(scaleRaw * zoom)
                            val scaleChange = newScale / scaleRaw
                            scaleRaw = newScale

                            val newOffset = if (scaleChange != 1f) {
                                Offset(
                                    (offsetRaw.x - centroid.x) * scaleChange + centroid.x + pan.x,
                                    (offsetRaw.y - centroid.y) * scaleChange + centroid.y + pan.y
                                )
                            } else {
                                offsetRaw + pan
                            }
                            offsetRaw = newOffset

                            scope.launch {
                                scaleA.snapTo(scaleRaw)
                                offsetXA.snapTo(offsetRaw.x)
                                offsetYA.snapTo(offsetRaw.y)
                            }
                            if (DEBUG_VERBOSE) {
                                Log.v(
                                    TAG_ZOOM,
                                    "raw: scale=${"%.2f".format(scaleRaw)} off=(${offsetRaw.x.toInt()},${offsetRaw.y.toInt()})"
                                )
                            }
                        }
                    )
                }
                .pointerInput(item.uri) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.all { it.changedToUp() }) {
                                val EPS = 0.06f
                                val targetScale = if (scaleRaw < 1f + EPS) 1f else scaleRaw
                                val targetOffset = if (targetScale == 1f) Offset.Zero else offsetRaw
                                scope.launch { animateTo(targetScale, targetOffset) }
                                scaleRaw = targetScale
                                offsetRaw = targetOffset
                            }
                        }
                    }
                }
                .pointerInput(item.uri) {
                    detectTapGestures(
                        onDoubleTap = {
                            scope.launch {
                                if (scaleA.value <= 1f) {
                                    Log.d(TAG_ZOOM, "doubleTap → zoom IN")
                                    scaleRaw = 2.5f.coerceIn(minScale, maxScale)
                                    offsetRaw = Offset.Zero
                                    animateTo(scaleRaw, offsetRaw)
                                } else {
                                    Log.d(TAG_ZOOM, "doubleTap → reset")
                                    scaleRaw = 1f
                                    offsetRaw = Offset.Zero
                                    animateTo(1f, Offset.Zero)
                                }
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
                        scaleX = scaleA.value
                        scaleY = scaleA.value
                        translationX = offsetXA.value
                        translationY = offsetYA.value
                        clip = true
                    },
                contentScale = ContentScale.Fit
            )
        }
    }
}
