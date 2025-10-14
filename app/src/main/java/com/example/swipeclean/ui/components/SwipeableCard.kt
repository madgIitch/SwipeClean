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
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.sign

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
    val density = LocalDensity.current
    val cfg = LocalConfiguration.current
    val viewConfig = LocalViewConfiguration.current

    val screenWidthPx = remember(cfg, density) { with(density) { cfg.screenWidthDp.dp.toPx() } }
    val screenHeightPx = remember(cfg, density) { with(density) { cfg.screenHeightDp.dp.toPx() } }
    val thresholdXRef = screenWidthPx * 0.28f // ↓ un pelín más fácil (antes 0.30f)
    val touchSlop = viewConfig.touchSlop
    val sixDpPx = with(density) { 6.dp.toPx() }

    var rawOffsetX by remember { mutableFloatStateOf(0f) }
    var rawOffsetY by remember { mutableFloatStateOf(0f) }
    var vibed by remember { mutableStateOf(false) }
    var horizontalDrag by remember { mutableStateOf<Boolean?>(null) }
    var verticalDragActive by remember { mutableStateOf(false) }
    var preArmVertical by remember { mutableStateOf(false) }

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

    val rotation = (dragX / 60f).coerceIn(-12f, 12f)
    val progressX = (dragX / thresholdXRef).coerceIn(-1f, 1f)
    var upProgress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(progressX, upProgress, swipeEnabled) {
        if (!swipeEnabled) return@LaunchedEffect
        val nearCommit = (abs(progressX) > 0.9f) || (upProgress > 0.9f)
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
            .pointerInput(swipeEnabled) {
                awaitEachGesture {
                    val first = awaitPointerEvent(pass = PointerEventPass.Initial)
                    val down = first.changes.firstOrNull() ?: return@awaitEachGesture
                    if (!swipeEnabled || down.changedToUp()) return@awaitEachGesture
                    if (first.changes.size >= 2) return@awaitEachGesture // cede multi-touch

                    val containerHeightPx = this.size.height.toFloat()
                    val containerWidthPx = this.size.width.toFloat()
                    val thresholdX = containerWidthPx * 0.28f
                    val thresholdY = containerHeightPx * 0.18f
                    val flingVelocityX = 1800f // px/s para “swipe” por velocidad

                    // Pre-arm vertical en franja inferior
                    preArmVertical = down.position.y >= (containerHeightPx * 0.60f)
                    verticalDragActive = false
                    horizontalDrag = null
                    vibed = false

                    // Velocity tracker
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)

                    if (preArmVertical) {
                        // Evita que el hijo arranque zoom desde el DOWN
                        down.consume()
                        if (DEBUG_VERBOSE) Log.v(TAG_SWIPE, "DOWN bottom-zone (preArmVertical=true)")
                    }
                    Log.d(TAG_SWIPE, "allowUp=$preArmVertical downY=${down.position.y} height=$containerHeightPx")

                    var totalX = 0f
                    var totalY = 0f
                    var upwardAbs = 0f

                    while (true) {
                        val ev = awaitPointerEvent(pass = PointerEventPass.Initial)
                        val ch = ev.changes.firstOrNull() ?: break
                        tracker.addPosition(ch.uptimeMillis, ch.position)

                        if (ch.changedToUp()) break
                        if (ev.changes.size >= 2) return@awaitEachGesture

                        val dx = ch.positionChange().x
                        val dy = ch.positionChange().y
                        totalX += abs(dx)
                        totalY += abs(dy)
                        if (dy < 0f) upwardAbs += -dy

                        if (horizontalDrag == null && !verticalDragActive) {
                            val horizAdv = totalX - totalY

                            // (A) Lock horizontal más agresivo
                            val strongHorizontal = (abs(dx) > abs(dy)) && (totalX > touchSlop * 0.5f)
                            if (horizAdv > touchSlop || strongHorizontal) {
                                horizontalDrag = true
                                preArmVertical = false // desarma vertical si gana horizontal
                                if (DEBUG_VERBOSE) Log.v(TAG_SWIPE, "→ Lock H (X=$totalX Y=$totalY strong=$strongHorizontal)")
                            } else {
                                // (B) Activar vertical con intención real hacia ARRIBA
                                val verticalIntent =
                                    preArmVertical &&
                                            upwardAbs > sixDpPx &&
                                            (upwardAbs - totalX) > (touchSlop * 0.5f)

                                if (verticalIntent) {
                                    verticalDragActive = true
                                    if (DEBUG_VERBOSE) Log.v(TAG_SWIPE, "→ Lock V (up=$upwardAbs X=$totalX)")
                                } else {
                                    // (C) Micro-preconsumo:
                                    //  - si pinta a horizontal, consume para que el hijo no robe
                                    //  - si venimos pre-armados para vertical, consume también
                                    val maybeHorizontal = (!preArmVertical && abs(dx) > abs(dy) && totalX > touchSlop * 0.3f)
                                    if (maybeHorizontal || preArmVertical) ch.consume()
                                }
                            }
                        } else {
                            // Modo decidido → consumir según corresponda
                            when {
                                horizontalDrag == true -> {
                                    ch.consume()
                                    rawOffsetX += dx
                                }
                                verticalDragActive -> {
                                    ch.consume()
                                    val newY = rawOffsetY + dy
                                    rawOffsetY = if (newY < 0f) newY else newY * 0.2f
                                }
                            }
                        }
                        // No salimos; seguimos hasta up o lock claro.
                    }

                    // Velocidad del gesto al soltar
                    val velocity = tracker.calculateVelocity()
                    val vX = velocity.x

                    val endUpProgress = (-(dragY) / thresholdY).coerceIn(0f, 1f)
                    upProgress = endUpProgress
                    Log.d(
                        TAG_SWIPE,
                        "end dragX=$dragX dragY=$dragY upProg=$endUpProgress thrX=$thresholdX thrY=$thresholdY"
                    )

                    // Resolución por distancia o por velocidad
                    when {
                        // Swipe lateral por distancia
                        dragX > thresholdX -> {
                            rawOffsetX = containerWidthPx * 2f
                            onSwipeRight()
                            rawOffsetX = 0f; rawOffsetY = 0f
                        }
                        dragX < -thresholdX -> {
                            rawOffsetX = -containerWidthPx * 2f
                            onSwipeLeft()
                            rawOffsetX = 0f; rawOffsetY = 0f
                        }
                        // Swipe lateral por velocidad (fling)
                        abs(vX) > flingVelocityX -> {
                            if (vX > 0f) {
                                rawOffsetX = containerWidthPx * 2f
                                onSwipeRight()
                            } else {
                                rawOffsetX = -containerWidthPx * 2f
                                onSwipeLeft()
                            }
                            rawOffsetX = 0f; rawOffsetY = 0f
                        }
                        // Swipe up por distancia
                        verticalDragActive && (-dragY > thresholdY) -> {
                            rawOffsetY = -containerHeightPx * 0.8f
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
            SwipeFeedbackOverlay(
                progressX = (dragX / thresholdXRef).coerceIn(-1f, 1f),
                upProgress = upProgress
            )
        }
    }
}
