package com.example.swipeclean

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.example.swipeclean.ui.components.AdaptiveBackdrop
import com.example.swipeclean.ui.components.CounterPill
import com.example.swipeclean.ui.components.RoundActionIcon
import com.example.swipeclean.ui.components.SwipeFeedbackOverlay
import com.madglitch.swipeclean.GalleryViewModel
import kotlin.math.abs

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardScreen(vm: GalleryViewModel) {
    val items by vm.items.collectAsState()
    val index by vm.index.collectAsState()
    val ctx = LocalContext.current

    val total = items.size
    val clampedIndex = if (total > 0) index.coerceIn(0, total - 1) else 0
    val shownIndex = if (total > 0) clampedIndex + 1 else 0
    val isEmpty = total == 0
    val currentItem = items.getOrNull(clampedIndex)

    // Launcher para ReviewActivity -> recibe staged/confirmed
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
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("SwipeClean")
                        Spacer(Modifier.width(12.dp))
                        CounterPill(current = shownIndex, total = total)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { vm.undo() }, enabled = !isEmpty) {
                        Icon(painterResource(R.drawable.ic_undo), contentDescription = "Deshacer")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val toReview = vm.getPendingForReview()
                        if (toReview.isEmpty()) return@IconButton
                        val intent = Intent(ctx, ReviewActivity::class.java).apply {
                            putParcelableArrayListExtra(EXTRA_PENDING_URIS, toReview)
                        }
                        reviewLauncher.launch(intent)
                    }) {
                        Icon(painterResource(R.drawable.ic_next), contentDescription = "Revisión")
                    }
                }
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
        // Fondo adaptativo según la imagen actual
        AdaptiveBackdrop(currentItem) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                if (total == 0) {
                    Text("No hay elementos en la galería", Modifier.align(Alignment.Center))
                } else {
                    // Fade elegante entre ítems usando el ÍNDICE como estado
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
                                onSwipeLeft  = { vm.markForTrash() },
                                onSwipeRight = { vm.keep() }
                            ) {
                                MediaSurface(item = itemAt)
                            }
                        } else {
                            // Fallback muy puntual si cambia la lista en caliente
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

@Composable
private fun SwipeableCard(
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    content: @Composable () -> Unit
) {
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val screenWidth = LocalConfiguration.current.screenWidthDp
    val thresholdPx = (screenWidth * 0.35f) // umbral ~35% del ancho de pantalla

    var rawOffsetX by remember { mutableFloatStateOf(0f) }
    var vibed by remember { mutableStateOf(false) }

    val dragX by animateFloatAsState(
        targetValue = rawOffsetX,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium),
        label = "dragX"
    )
    val rotation = (dragX / 60f).coerceIn(-12f, 12f)
    val progress = (dragX / thresholdPx).coerceIn(-1f, 1f)

    // Haptics cerca del umbral
    LaunchedEffect(progress) {
        val near = 0.9f
        if (!vibed && abs(progress) > near) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            vibed = true
        }
        if (abs(progress) < 0.5f) vibed = false
    }

    Card(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .graphicsLayer {
                translationX = dragX
                rotationZ = rotation
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        when {
                            dragX > thresholdPx -> {
                                // sale a la derecha
                                rawOffsetX = screenWidth * 2f
                                onSwipeRight()
                                rawOffsetX = 0f
                            }
                            dragX < -thresholdPx -> {
                                // sale a la izquierda
                                rawOffsetX = -screenWidth * 2f
                                onSwipeLeft()
                                rawOffsetX = 0f
                            }
                            else -> {
                                // vuelve al centro
                                rawOffsetX = 0f
                            }
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        rawOffsetX += dragAmount.x
                    }
                )
            }
            .clip(RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp)
    ) {
        Box(Modifier.background(MaterialTheme.colorScheme.surface)) {
            content()
            SwipeFeedbackOverlay(progress = progress)
        }
    }
}

/** Carga imagen o frame de vídeo con Coil/ExoPlayer. */
@Composable
private fun MediaSurface(
    item: MediaItem,
    forceTestVideo: Boolean = false
) {
    val TAG = "SwipeClean/MediaSurface"
    val ctx = LocalContext.current

    val resolvedMime = remember(item.uri, item.mimeType, item.isVideo) {
        runCatching { ctx.contentResolver.getType(item.uri) }.getOrNull()
    }
    val isVideo = item.isVideo ||
            item.mimeType.startsWith("video/") ||
            (resolvedMime?.startsWith("video/") == true)

    if (isVideo) {
        // Vídeos como ya lo tenías
        VideoPlayer(
            uri = item.uri,
            modifier = Modifier.fillMaxSize(),
            autoPlay = true,
            loop = true,
            mute = false,
            showControls = true
        )
    } else {
        // 👇 Imagen SIN upscale: Inside + fondo negro
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black), // fondo para letterboxing vertical si sobra
            contentAlignment = Alignment.Center
        ) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(ctx)
                    .data(item.uri)
                    .crossfade(true)
                    .size(Size.ORIGINAL)          // pide el bitmap original
                    .allowHardware(false)
                    .build(),
                contentDescription = null,
                // 👇 aquí el cambio importante
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth(),
                loading = {
                    CircularProgressIndicator(Modifier.padding(24.dp))
                },
                error = {
                    Text(
                        "Error al cargar",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                }
            )
        }

    }
}
