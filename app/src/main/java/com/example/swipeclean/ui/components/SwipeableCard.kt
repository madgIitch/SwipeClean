package com.example.swipeclean.ui.components

import android.util.Log
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.*
import androidx.compose.ui.unit.dp
import com.example.swipeclean.ui.components.SwipeFeedbackOverlay

private const val TAG_SWIPE = "SwipeClean/Swipe"
private const val DEBUG_VERBOSE = false

@Composable
fun SwipeableCard(
    swipeEnabled: Boolean = true,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    content: @Composable () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val thresholdPx = with(density) { (screenWidthDp.dp * 0.30f).toPx() } // un poco más fácil
    val viewConfig = LocalViewConfiguration.current
    val touchSlop = viewConfig.touchSlop

    var rawOffsetX by remember { mutableFloatStateOf(0f) }
    var vibed by remember { mutableStateOf(false) }
    var horizontalDrag by remember { mutableStateOf<Boolean?>(null) }

    val dragX by animateFloatAsState(
        targetValue = rawOffsetX,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium),
        label = "dragX"
    )
    val rotation = (dragX / 60f).coerceIn(-12f, 12f)
    val progress = (dragX / thresholdPx).coerceIn(-1f, 1f)

    LaunchedEffect(progress, swipeEnabled) {
        if (!swipeEnabled) {
            Log.d(TAG_SWIPE, "swipe overlay DESHABILITADO (swipeEnabled=false)")
            return@LaunchedEffect
        }
        if (!vibed && kotlin.math.abs(progress) > 0.9f) {
            Log.d(TAG_SWIPE, "haptic LONG (progress~$progress)")
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            vibed = true
        }
        if (kotlin.math.abs(progress) < 0.5f && vibed) vibed = false
    }

    Card(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            // Captura en el ancestro y Pass.Initial para ganar prioridad
            .pointerInput(swipeEnabled) {
                awaitEachGesture {
                    val first = awaitPointerEvent(pass = PointerEventPass.Initial)
                    val down = first.changes.firstOrNull() ?: return@awaitEachGesture
                    if (!swipeEnabled || down.changedToUp()) return@awaitEachGesture

                    Log.d(TAG_SWIPE, "down @ ${down.position}")
                    var totalX = 0f
                    var totalY = 0f
                    horizontalDrag = null
                    vibed = false

                    while (true) {
                        val ev = awaitPointerEvent(pass = PointerEventPass.Initial)
                        val ch = ev.changes.firstOrNull() ?: break
                        if (ch.changedToUp()) break

                        val delta = ch.positionChange()
                        val dx = delta.x
                        val dy = delta.y
                        totalX += kotlin.math.abs(dx)
                        totalY += kotlin.math.abs(dy)

                        if (horizontalDrag == null) {
                            when {
                                totalX - totalY > touchSlop -> {
                                    horizontalDrag = true
                                    Log.d(TAG_SWIPE, "direction lock → HORIZONTAL (X=${"%.1f".format(totalX)}, Y=${"%.1f".format(totalY)}, slop=${"%.1f".format(touchSlop)})")
                                }
                                totalY - totalX > touchSlop -> {
                                    horizontalDrag = false
                                    Log.d(TAG_SWIPE, "direction lock → VERTICAL (cede)")
                                }
                            }
                        }

                        if (horizontalDrag == true) {
                            ch.consume() // prioridad al swipe
                            rawOffsetX += dx
                            if (DEBUG_VERBOSE && kotlin.math.abs(dx) > 0.5f) {
                                Log.v(TAG_SWIPE, "drag += ${"%.1f".format(dx)} → rawOffsetX=${"%.1f".format(rawOffsetX)}")
                            }
                        } else if (horizontalDrag == false) {
                            break
                        }
                    }

                    Log.d(TAG_SWIPE, "end dragX=$dragX threshold=$thresholdPx")
                    when {
                        dragX > thresholdPx -> {
                            Log.d(TAG_SWIPE, "→ SWIPE RIGHT (keep)")
                            rawOffsetX = with(density) { screenWidthDp.dp.toPx() } * 2f
                            onSwipeRight()
                            rawOffsetX = 0f
                        }
                        dragX < -thresholdPx -> {
                            Log.d(TAG_SWIPE, "→ SWIPE LEFT (trash)")
                            rawOffsetX = -with(density) { screenWidthDp.dp.toPx() } * 2f
                            onSwipeLeft()
                            rawOffsetX = 0f
                        }
                        else -> {
                            Log.d(TAG_SWIPE, "→ SNAP BACK (no umbral)")
                            rawOffsetX = 0f
                        }
                    }
                }
            }
            .graphicsLayer { translationX = dragX; rotationZ = rotation }
            .clip(RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp)
    ) {
        Box(Modifier.background(MaterialTheme.colorScheme.surface)) {
            content()
            SwipeFeedbackOverlay(progress = progress)
        }
    }
}
