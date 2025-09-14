package com.example.swipeclean

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.consumePositionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.dp
import com.example.swipeclean.ui.components.AdaptiveBackdrop
import com.example.swipeclean.ui.components.FancyTopBar
import com.example.swipeclean.ui.components.RoundActionIcon
import com.example.swipeclean.ui.components.SwipeFeedbackOverlay
import com.example.swipeclean.ui.components.ZoomableImage
import com.madglitch.swipeclean.GalleryViewModel

// -----------------------------------------------------------------------------
// Helper unificado para decidir si un MediaItem es vídeo
// -----------------------------------------------------------------------------
@Composable
private fun isVideoItem(item: MediaItem): Boolean {
    val ctx = LocalContext.current
    val realMime = remember(item.uri, item.mimeType, item.isVideo) {
        runCatching { ctx.contentResolver.getType(item.uri) }.getOrNull()
    }
    return item.isVideo ||
            item.mimeType.startsWith("video/") ||
            (realMime?.startsWith("video/") == true)
}

// -----------------------------------------------------------------------------
// Componente único de media: vídeo con ExoPlayer o foto con pinch-to-zoom
// -----------------------------------------------------------------------------
@Composable
private fun MediaCard(
    item: MediaItem?,
    onSwipeEnabledChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier.fillMaxSize()
) {
    if (item == null) {
        Box(modifier, contentAlignment = Alignment.Center) { Text("Sin imagen") }
        return
    }

    val isVideo = isVideoItem(item)
    if (isVideo) {
        // En vídeo, swipe habilitado siempre (misma lógica que MediaSurface)
        onSwipeEnabledChange(true)
        VideoPlayer(
            uri = item.uri,
            modifier = modifier,
            autoPlay = true,
            loop = true,
            mute = false,
            showControls = true
        )
    } else {
        // Foto con pinch-to-zoom: bloquea swipe cuando hay zoom activo
        ZoomableImage(
            item = item,
            modifier = modifier,
            onZoomingChange = { zooming -> onSwipeEnabledChange(!zooming) }
        )
    }
}

// -----------------------------------------------------------------------------
// Pantalla principal
// -----------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardScreen(vm: GalleryViewModel) {
    val items by vm.items.collectAsState()
    val index by vm.index.collectAsState()
    val filter by vm.filter.collectAsState()
    val ctx = LocalContext.current

    val total = items.size
    val clampedIndex = if (total > 0) index.coerceIn(0, total - 1) else 0
    val shownIndex = if (total > 0) clampedIndex + 1 else 0
    val currentItem = items.getOrNull(clampedIndex)

    // Bloquea el swipe cuando hay zoom activo
    var swipeEnabled by remember { mutableStateOf(true) }

    val reviewLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data ?: return@rememberLauncherForActivityResult

        val staged: ArrayList<Uri> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                data.getParcelableArrayListExtra(EXTRA_STAGED_URIS, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                data.getParcelableArrayListExtra(EXTRA_STAGED_URIS)
            } ?: arrayListOf()

        val confirmed: ArrayList<Uri> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                data.getParcelableArrayListExtra(EXTRA_CONFIRMED_URIS, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                data.getParcelableArrayListExtra(EXTRA_CONFIRMED_URIS)
            } ?: arrayListOf()

        if (staged.isNotEmpty()) vm.applyStagedSelection(staged)
        if (confirmed.isNotEmpty()) vm.confirmDeletionConfirmed(confirmed)
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            FancyTopBar(
                title = "SwipeClean",
                shownIndex = shownIndex,
                total = total,
                onUndo = { vm.undo() },
                onReview = {
                    val toReview = vm.getPendingForReview()
                    if (toReview.isEmpty()) return@FancyTopBar
                    val intent = Intent(ctx, ReviewActivity::class.java).apply {
                        putParcelableArrayListExtra(EXTRA_PENDING_URIS, toReview)
                    }
                    reviewLauncher.launch(intent)
                },
                currentFilter = filter,
                onFilterChange = { vm.setFilter(it) }
            )
        },
        bottomBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                RoundActionIcon(
                    icon = R.drawable.ic_delete,
                    contentDesc = "Borrar",
                    onClick = { vm.markForTrash() },
                    container = MaterialTheme.colorScheme.errorContainer,
                    content   = MaterialTheme.colorScheme.onErrorContainer,
                    size = 80.dp
                )
                RoundActionIcon(
                    icon = R.drawable.ic_check,
                    contentDesc = "Guardar",
                    onClick = { vm.keep() },
                    container = MaterialTheme.colorScheme.primaryContainer,
                    content   = MaterialTheme.colorScheme.onPrimaryContainer,
                    size = 80.dp
                )
            }
        }
    ) { padding ->
        AdaptiveBackdrop(currentItem) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                if (total == 0) {
                    Text("No hay elementos en la galería", Modifier.align(Alignment.Center))
                } else {
                    AnimatedContent(
                        targetState = clampedIndex,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(180)) togetherWith
                                    fadeOut(animationSpec = tween(120))
                        },
                        label = "MediaCrossfade"
                    ) { idx ->
                        val itemAt = items.getOrNull(idx)
                        if (itemAt != null) {
                            SwipeableCard(
                                swipeEnabled = swipeEnabled,
                                onSwipeLeft  = { vm.markForTrash() },
                                onSwipeRight = { vm.keep() }
                            ) {
                                MediaCard(
                                    item = itemAt,
                                    onSwipeEnabledChange = { enabled -> swipeEnabled = enabled }
                                )
                            }
                        } else {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Cargando…")
                            }
                        }
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Tarjeta swipeable con bloqueo direccional y feedback háptico
// -----------------------------------------------------------------------------
@Composable
private fun SwipeableCard(
    swipeEnabled: Boolean = true,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    content: @Composable () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val thresholdPx = with(density) { (screenWidthDp.dp * 0.35f).toPx() }
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
        if (!swipeEnabled) return@LaunchedEffect
        val near = 0.9f
        if (!vibed && kotlin.math.abs(progress) > near) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            vibed = true
        }
        if (kotlin.math.abs(progress) < 0.5f) vibed = false
    }

    Card(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .graphicsLayer {
                translationX = dragX
                rotationZ = rotation
            }
            .clip(RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp)
    ) {
        // Contenido (debajo)
        Box(Modifier.background(MaterialTheme.colorScheme.surface)) {
            content()
            SwipeFeedbackOverlay(progress = progress)
        }

        // Overlay que captura swipe solo si está habilitado
        if (swipeEnabled) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(swipeEnabled) {
                        detectDragGestures(
                            onDragStart = {
                                horizontalDrag = null
                                vibed = false
                            },
                            onDragCancel = {
                                horizontalDrag = null
                                rawOffsetX = 0f
                            },
                            onDragEnd = {
                                horizontalDrag = null
                                when {
                                    dragX > thresholdPx -> {
                                        rawOffsetX = with(density) { screenWidthDp.dp.toPx() } * 2f
                                        onSwipeRight()
                                        rawOffsetX = 0f
                                    }
                                    dragX < -thresholdPx -> {
                                        rawOffsetX = -with(density) { screenWidthDp.dp.toPx() } * 2f
                                        onSwipeLeft()
                                        rawOffsetX = 0f
                                    }
                                    else -> rawOffsetX = 0f
                                }
                            },
                            onDrag = { change, dragAmount ->
                                // decidir dirección con slop
                                if (horizontalDrag == null) {
                                    val absX = kotlin.math.abs(dragAmount.x)
                                    val absY = kotlin.math.abs(dragAmount.y)
                                    when {
                                        absX - absY > touchSlop -> horizontalDrag = true
                                        absY - absX > touchSlop -> horizontalDrag = false
                                        else -> return@detectDragGestures
                                    }
                                }
                                if (horizontalDrag == true) {
                                    change.consumePositionChange()
                                    rawOffsetX += dragAmount.x
                                }
                            }
                        )
                    }
            )
        }
    }
}
