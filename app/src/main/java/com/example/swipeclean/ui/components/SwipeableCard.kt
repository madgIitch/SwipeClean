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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.example.swipeclean.zen.ZenMode
import kotlin.math.abs

private const val TAG_SWIPE = "SwipeClean/Swipe"
private const val DEBUG_VERBOSE = false

/**
 * Componente de tarjeta deslizable con soporte para gestos multi-direccionales.
 * Soporta swipe horizontal (izquierda/derecha), vertical (arriba/abajo) y ZenMode.
 *
 * @param swipeEnabled Habilita/deshabilita la detección de gestos
 * @param zenMode Estado del modo Zen para adaptar animaciones y hápticos
 * @param onSwipeLeft Callback cuando se desliza a la izquierda (borrar)
 * @param onSwipeRight Callback cuando se desliza a la derecha (guardar)
 * @param onSwipeUp Callback cuando se desliza hacia arriba (compartir)
 * @param onSwipeDown Callback cuando se desliza hacia abajo (toggle ZenMode)
 * @param content Contenido de la tarjeta
 */
@Composable
fun SwipeableCard(
    swipeEnabled: Boolean = true,
    zenMode: ZenMode? = null,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit,
    content: @Composable () -> Unit
) {
    // Servicios de plataforma
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val cfg = LocalConfiguration.current
    val viewConfig = LocalViewConfiguration.current

    // Cálculos de dimensiones y umbrales
    val screenWidthPx = remember(cfg, density) { with(density) { cfg.screenWidthDp.dp.toPx() } }
    val screenHeightPx = remember(cfg, density) { with(density) { cfg.screenHeightDp.dp.toPx() } }
    val thresholdXRef = screenWidthPx * 0.28f
    val touchSlop = viewConfig.touchSlop
    val sixDpPx = with(density) { 6.dp.toPx() }

    // Detectar si estamos en ZenMode
    val isZenMode = zenMode?.isEnabled == true

    // Estado del gesto
    var rawOffsetX by remember { mutableFloatStateOf(0f) }
    var rawOffsetY by remember { mutableFloatStateOf(0f) }
    var vibed by remember { mutableStateOf(false) }
    var horizontalDrag by remember { mutableStateOf<Boolean?>(null) }
    var verticalDragActive by remember { mutableStateOf(false) }
    var preArmUp by remember { mutableStateOf(false) }
    var preArmDown by remember { mutableStateOf(false) }

    // Animaciones con parámetros adaptados al ZenMode
    val dragX by animateFloatAsState(
        targetValue = rawOffsetX,
        animationSpec = spring(
            dampingRatio = 0.75f,
            stiffness = Spring.StiffnessMedium
        ),
        label = "dragX"
    )

    val dragY by animateFloatAsState(
        targetValue = rawOffsetY,
        animationSpec = spring(
            dampingRatio = if (isZenMode) 0.85f else 0.80f,
            stiffness = if (isZenMode) Spring.StiffnessVeryLow else Spring.StiffnessLow
        ),
        label = "dragY"
    )

    // Cálculos visuales
    val rotation = (dragX / 60f).coerceIn(-12f, 12f)
    val progressX = (dragX / thresholdXRef).coerceIn(-1f, 1f)
    var upProgress by remember { mutableFloatStateOf(0f) }
    var downProgress by remember { mutableFloatStateOf(0f) }

    // Hápticos adaptados al ZenMode
    LaunchedEffect(progressX, upProgress, downProgress, swipeEnabled, isZenMode) {
        if (!swipeEnabled) return@LaunchedEffect
        val nearCommit = (abs(progressX) > 0.9f) || (upProgress > 0.9f) || (downProgress > 0.9f)
        if (!vibed && nearCommit) {
            // En ZenMode, usar háptico más suave para mantener la calma
            val feedbackType = if (isZenMode) {
                HapticFeedbackType.TextHandleMove
            } else {
                HapticFeedbackType.LongPress
            }
            haptics.performHapticFeedback(feedbackType)
            vibed = true
        }
        if (!nearCommit && vibed) vibed = false
    }

    Card(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .pointerInput(swipeEnabled, isZenMode) {
                awaitEachGesture {
                    // Esperar primer evento de puntero
                    val first = awaitPointerEvent(pass = PointerEventPass.Initial)
                    val down = first.changes.firstOrNull() ?: return@awaitEachGesture
                    if (!swipeEnabled || down.changedToUp()) return@awaitEachGesture

                    // Ceder control si hay multi-touch (para zoom)
                    if (first.changes.size >= 2) return@awaitEachGesture

                    // Dimensiones del contenedor
                    val containerHeightPx = this.size.height.toFloat()
                    val containerWidthPx = this.size.width.toFloat()
                    val thresholdX = containerWidthPx * 0.28f
                    val thresholdY = containerHeightPx * 0.18f
                    val flingVelocityX = 1800f

                    // Pre-arm vertical: superior (40%) para down, inferior (60%) para up
                    preArmDown = down.position.y <= (containerHeightPx * 0.40f)
                    preArmUp = down.position.y >= (containerHeightPx * 0.60f)
                    val preArmVertical = preArmDown || preArmUp

                    // Resetear estado
                    verticalDragActive = false
                    horizontalDrag = null
                    vibed = false

                    // Tracker de velocidad para fling gestures
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)

                    // Pre-consumir eventos en zona vertical
                    if (preArmVertical) {
                        down.consume()
                        if (DEBUG_VERBOSE) Log.v(TAG_SWIPE, "DOWN zone (preArmUp=$preArmUp preArmDown=$preArmDown)")
                    }

                    Log.d(TAG_SWIPE, "preArmUp=$preArmUp preArmDown=$preArmDown downY=${down.position.y} height=$containerHeightPx zenMode=$isZenMode")

                    // Acumuladores de movimiento
                    var totalX = 0f
                    var totalY = 0f
                    var upwardAbs = 0f
                    var downwardAbs = 0f

                    // Loop principal de detección de gestos
                    while (true) {
                        val ev = awaitPointerEvent(pass = PointerEventPass.Initial)
                        val ch = ev.changes.firstOrNull() ?: break
                        tracker.addPosition(ch.uptimeMillis, ch.position)

                        if (ch.changedToUp()) break

                        // Ceder si aparece multi-touch durante el gesto
                        if (ev.changes.size >= 2) return@awaitEachGesture

                        val dx = ch.positionChange().x
                        val dy = ch.positionChange().y
                        totalX += abs(dx)
                        totalY += abs(dy)
                        if (dy < 0f) upwardAbs += -dy
                        if (dy > 0f) downwardAbs += dy

                        // Sistema de bloqueo de dirección
                        if (horizontalDrag == null && !verticalDragActive) {
                            val horizAdv = totalX - totalY

                            // (A) Bloquear horizontal
                            val strongHorizontal = (abs(dx) > abs(dy)) && (totalX > touchSlop * 0.5f)
                            if (horizAdv > touchSlop || strongHorizontal) {
                                horizontalDrag = true
                                preArmUp = false
                                preArmDown = false
                                if (DEBUG_VERBOSE) Log.v(TAG_SWIPE, "→ Lock H (X=$totalX Y=$totalY)")
                            } else {
                                // (B) Activar vertical con intención real
                                val verticalUpIntent = preArmUp &&
                                        upwardAbs > sixDpPx &&
                                        (upwardAbs - totalX) > (touchSlop * 0.5f)

                                val verticalDownIntent = preArmDown &&
                                        downwardAbs > sixDpPx &&
                                        (downwardAbs - totalX) > (touchSlop * 0.5f)

                                if (verticalUpIntent || verticalDownIntent) {
                                    verticalDragActive = true
                                    if (DEBUG_VERBOSE) Log.v(TAG_SWIPE, "→ Lock V (up=$upwardAbs down=$downwardAbs X=$totalX)")
                                } else {
                                    // (C) Micro-preconsumo para evitar conflictos
                                    val maybeHorizontal = (!preArmVertical && abs(dx) > abs(dy) && totalX > touchSlop * 0.3f)
                                    if (maybeHorizontal || preArmVertical) ch.consume()
                                }
                            }
                        } else {
                            // Modo decidido: actualizar offset correspondiente
                            when {
                                horizontalDrag == true -> {
                                    ch.consume()
                                    rawOffsetX += dx
                                }
                                verticalDragActive -> {
                                    ch.consume()
                                    rawOffsetY += dy
                                }
                            }
                        }
                    }

                    // Calcular velocidad del gesto
                    val velocity = tracker.calculateVelocity()
                    val vX = velocity.x

                    // Calcular progreso de gestos verticales
                    val endUpProgress = (-(dragY) / thresholdY).coerceIn(0f, 1f)
                    val endDownProgress = (dragY / thresholdY).coerceIn(0f, 1f)
                    upProgress = endUpProgress
                    downProgress = endDownProgress

                    Log.d(
                        TAG_SWIPE,
                        "end dragX=$dragX dragY=$dragY upProg=$endUpProgress downProg=$endDownProgress zenMode=$isZenMode"
                    )

                    // Resolución de gestos con prioridad
                    when {
                        // Swipe derecho por distancia (guardar)
                        dragX > thresholdX -> {
                            Log.d(TAG_SWIPE, "→ SWIPE RIGHT (keep)")
                            rawOffsetX = containerWidthPx * 2f
                            onSwipeRight()
                            rawOffsetX = 0f
                            rawOffsetY = 0f
                        }

                        // Swipe izquierdo por distancia (borrar)
                        dragX < -thresholdX -> {
                            Log.d(TAG_SWIPE, "→ SWIPE LEFT (trash)")
                            rawOffsetX = -containerWidthPx * 2f
                            onSwipeLeft()
                            rawOffsetX = 0f
                            rawOffsetY = 0f
                        }

                        // Swipe lateral por velocidad (fling)
                        abs(vX) > flingVelocityX -> {
                            if (vX > 0f) {
                                Log.d(TAG_SWIPE, "→ FLING RIGHT (keep)")
                                rawOffsetX = containerWidthPx * 2f
                                onSwipeRight()
                            } else {
                                Log.d(TAG_SWIPE, "→ FLING LEFT (trash)")
                                rawOffsetX = -containerWidthPx * 2f
                                onSwipeLeft()
                            }
                            rawOffsetX = 0f
                            rawOffsetY = 0f
                        }

                        // Swipe up por distancia (compartir)
                        verticalDragActive && preArmUp && (-dragY > thresholdY) -> {
                            Log.d(TAG_SWIPE, "→ SWIPE UP (share)")
                            rawOffsetY = -containerHeightPx * 0.8f
                            onSwipeUp()
                            rawOffsetY = 0f
                            rawOffsetX = 0f
                        }

                        // Swipe down por distancia (toggle ZenMode)
                        verticalDragActive && preArmDown && (dragY > thresholdY) -> {
                            Log.d(TAG_SWIPE, "→ SWIPE DOWN (zen mode)")
                            rawOffsetY = containerHeightPx * 0.8f
                            onSwipeDown()
                            rawOffsetY = 0f
                            rawOffsetX = 0f
                        }

                        // Reset si no se cumple ningún umbral
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
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isZenMode) Color.Transparent
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Box(Modifier.background(
            if (isZenMode) Color.Transparent
            else MaterialTheme.colorScheme.surface
        )) {
            content()
            SwipeFeedbackOverlay(
                progressX = (dragX / thresholdXRef).coerceIn(-1f, 1f),
                upProgress = upProgress,
                downProgress = downProgress,
                isZenMode = isZenMode
            )
        }
    }
}