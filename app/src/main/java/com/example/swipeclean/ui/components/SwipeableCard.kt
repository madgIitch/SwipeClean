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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.dp
import com.example.swipeclean.R
import kotlin.math.abs
import kotlin.math.min

private const val TAG_SWIPE = "SwipeClean/Swipe"
private const val DEBUG_VERBOSE = false

@Composable
fun SwipeableCard(
    swipeEnabled: Boolean = true,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    onSwipeUp: () -> Unit,
    content: @Composable () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current                  // ✅ capturado fuera
    val cfg = LocalConfiguration.current                // ✅ capturado fuera
    val viewConfig = LocalViewConfiguration.current     // ✅ capturado fuera

    // ✅ Calcula tamaños en px FUERA del pointerInput
    val screenWidthPx = remember(cfg, density) {
        with(density) { cfg.screenWidthDp.dp.toPx() }
    }
    val screenHeightPx = remember(cfg, density) {
        with(density) { cfg.screenHeightDp.dp.toPx() }
    }
    val thresholdX = screenWidthPx * 0.30f
    val thresholdY = screenHeightPx * 0.22f
    val touchSlop  = viewConfig.touchSlop

    var rawOffsetX by remember { mutableFloatStateOf(0f) }
    var rawOffsetY by remember { mutableFloatStateOf(0f) }
    var vibed by remember { mutableStateOf(false) }
    var horizontalDrag by remember { mutableStateOf<Boolean?>(null) }
    var verticalDragActive by remember { mutableStateOf(false) }
    var allowUpSwipeStart by remember { mutableStateOf(false) }

    val dragX by animateFloatAsState(
        targetValue = rawOffsetX,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium),
        label = "dragX"
    )
    val dragY by animateFloatAsState(
        targetValue = rawOffsetY,
        animationSpec = spring(dampingRatio = 0.80f, stiffness = Spring.StiffnessLow),
        label = "dragY"
    )

    val rotation   = (dragX / 60f).coerceIn(-12f, 12f)
    val progressX  = (dragX / thresholdX).coerceIn(-1f, 1f)
    val upProgress = (-(dragY) / thresholdY).coerceIn(0f, 1f)

    LaunchedEffect(progressX, upProgress, swipeEnabled) {
        if (!swipeEnabled) return@LaunchedEffect
        val nearCommit = (kotlin.math.abs(progressX) > 0.9f) || (upProgress > 0.9f)
        if (!vibed && nearCommit) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            vibed = true
        }
        if (!nearCommit && vibed) vibed = false
    }

    Card(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .pointerInput(swipeEnabled, screenHeightPx, thresholdX, thresholdY, touchSlop) { // ✅ pasa valores
                awaitEachGesture {
                    val first = awaitPointerEvent(pass = PointerEventPass.Initial)
                    val down = first.changes.firstOrNull() ?: return@awaitEachGesture
                    if (!swipeEnabled || down.changedToUp()) return@awaitEachGesture
                    if (first.changes.size >= 2) return@awaitEachGesture // cede multi-touch (zoom)

                    // ✅ usa screenHeightPx ya calculado
                    allowUpSwipeStart = down.position.y >= (screenHeightPx * 0.66f)
                    if (allowUpSwipeStart) {
                        down.consume() // bloquea que el hijo arranque pan/zoom
                    }

                    var totalX = 0f
                    var totalY = 0f
                    horizontalDrag = null
                    verticalDragActive = false
                    vibed = false

                    while (true) {
                        val ev = awaitPointerEvent(pass = PointerEventPass.Initial)
                        val ch = ev.changes.firstOrNull() ?: break
                        if (ch.changedToUp()) break
                        if (ev.changes.size >= 2) return@awaitEachGesture // cede multi-touch

                        val dx = ch.positionChange().x
                        val dy = ch.positionChange().y
                        totalX += kotlin.math.abs(dx)
                        totalY += kotlin.math.abs(dy)

                        if (horizontalDrag == null) {
                            when {
                                totalX - totalY > touchSlop -> {
                                    horizontalDrag = true
                                }
                                totalY - totalX > touchSlop -> {
                                    horizontalDrag = false
                                    verticalDragActive = allowUpSwipeStart
                                }
                                else -> {
                                    if (allowUpSwipeStart && (kotlin.math.abs(dx) > 0f || kotlin.math.abs(dy) > 0f)) {
                                        ch.consume()
                                    }
                                }
                            }
                        }

                        when {
                            horizontalDrag == true -> {
                                ch.consume()
                                rawOffsetX += dx
                            }
                            horizontalDrag == false && verticalDragActive -> {
                                ch.consume()
                                val newY = rawOffsetY + dy
                                rawOffsetY = if (newY < 0f) newY else newY * 0.2f // sólo arriba
                            }
                            else -> {
                                return@awaitEachGesture // cede vertical normal al hijo
                            }
                        }
                    }

                    when {
                        dragX > thresholdX -> {
                            rawOffsetX = screenWidthPx * 2f
                            onSwipeRight()
                            rawOffsetX = 0f; rawOffsetY = 0f
                        }
                        dragX < -thresholdX -> {
                            rawOffsetX = -screenWidthPx * 2f
                            onSwipeLeft()
                            rawOffsetX = 0f; rawOffsetY = 0f
                        }
                        verticalDragActive && (-dragY > thresholdY) -> {
                            rawOffsetY = -screenHeightPx * 0.8f
                            onSwipeUp()
                            rawOffsetY = 0f; rawOffsetX = 0f
                        }
                        else -> {
                            rawOffsetX = 0f
                            rawOffsetY = 0f
                        }
                    }
                }
            }
            .graphicsLayer {
                translationX = dragX
                translationY = dragY
                rotationZ = rotation
            }
            .clip(RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp)
    ) {
        Box(Modifier.background(MaterialTheme.colorScheme.surface)) {
            content()
            SwipeFeedbackOverlay(progressX = progressX, upProgress = upProgress)
        }
    }
}
