package com.example.swipeclean

import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.exoplayer.ExoPlayer
import com.example.swipeclean.ui.components.*
import com.example.swipeclean.zen.ZenAudioTrack
import com.example.swipeclean.zen.ZenViewModel
import com.example.swipeclean.zen.rememberZenAudioPlayer
import com.madglitch.swipeclean.GalleryViewModel
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.rememberCoroutineScope
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.launch  // ← Agregar esta línea
import com.example.swipeclean.zen.createZenNotificationChannel
import com.example.swipeclean.zen.showTimerFinishedNotification


private const val TAG_UI = "SwipeClean/UI"

@androidx.annotation.OptIn(UnstableApi::class)
@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardScreen(vm: GalleryViewModel) {
    DebugGestureEnv()

    // Obtener ZenViewModel
    val zenViewModel: ZenViewModel = viewModel()
    val zenMode by zenViewModel.zenMode.collectAsState()
    LaunchedEffect(zenMode) {
        Log.d(TAG_UI, "zenMode state: isEnabled=${zenMode.isEnabled}, haptics=${zenMode.hapticsIntensity}")
    }
    var showZenMessage by rememberSaveable { mutableStateOf(false) }
    var showMetadata by remember { mutableStateOf(false) }
    var currentMetadata by remember { mutableStateOf<MediaMetadata?>(null) }
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle


    val items by vm.items.collectAsState()
    val index by vm.index.collectAsState()
    val filter by vm.advancedFilter.collectAsState()
    val ctx = LocalContext.current

    val totalDeletedBytes by vm.totalDeletedBytes.collectAsState()
    val totalDeletedCount by vm.totalDeletedCount.collectAsState()
    val total = items.size
    val clampedIndex = if (total > 0) index.coerceIn(0, total - 1) else 0
    val shownIndex = if (total > 0) clampedIndex + 1 else 0
    val nextItem = remember(clampedIndex, items) {
        if (total > 0) {
            val nextIndex = (clampedIndex + 1) % total
            items.getOrNull(nextIndex)
        } else null
    }

    val currentItem = items.getOrNull(clampedIndex)

    val isCurrentItemVideo = remember(currentItem) {
        currentItem?.let { item ->
            item.isVideo ||
                    item.mimeType.startsWith("video/") ||
                    ctx.contentResolver.getType(item.uri)?.startsWith("video/") == true
        } ?: false
    }

    val zenPlayer = if (zenMode.isEnabled) {
        rememberZenAudioPlayer(
            track = zenMode.audioTrack,
            volume = if (isCurrentItemVideo && zenMode.isEnabled) 0f else zenMode.volume,
            lifecycle = lifecycle,
            isEnabled = zenMode.isEnabled
        )
    } else {
        null
    }

    val isHashing by vm.isHashing.collectAsState()
    val hashingProgress by vm.hashingProgress.collectAsState()

    val timerProgress by zenViewModel.timerProgress.collectAsState()
    val timerRemaining by zenViewModel.timerRemaining.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(items.size) { Log.d(TAG_UI, "items.size=${items.size}") }
    LaunchedEffect(index)      { Log.d(TAG_UI, "index=$index (items.size=${items.size})") }
    LaunchedEffect(filter)     { Log.d(TAG_UI, "filter=$filter") }
    LaunchedEffect(zenMode.isEnabled) {
        if (zenMode.isEnabled) {
            showZenMessage = true
            delay(5000)
            showZenMessage = false
        } else {
            showZenMessage = false
        }
    }
    LaunchedEffect(zenPlayer, zenMode.audioTrack) {
        Log.d(TAG_UI, "ZenPlayer created: ${zenPlayer != null}, track=${zenMode.audioTrack.displayName}, volume=${zenMode.volume}")
    }
    LaunchedEffect(zenMode.isEnabled) {
        Log.d(TAG_UI, "ZenMode.isEnabled = ${zenMode.isEnabled}")
    }
    LaunchedEffect(zenMode.audioTrack) {
        Log.d(TAG_UI, "Current audioTrack = ${zenMode.audioTrack.displayName}")
    }
    // Agregar log para debugging
    LaunchedEffect(zenMode.isEnabled, zenPlayer) {
        Log.d(TAG_UI, "Zen Mode enabled: ${zenMode.isEnabled}, Player created: ${zenPlayer != null}")
    }
    LaunchedEffect(Unit) {
        createZenNotificationChannel(context)  // ← Nombre correcto
    }
    // Monitorear finalización del temporizador
    LaunchedEffect(timerProgress) {
        if (timerProgress >= 1f && zenMode.timerDuration > 0) {
            Log.d(TAG_UI, "Timer finished, showing notification")
            showTimerFinishedNotification(context)

            // Opcional: desactivar Zen Mode automáticamente
            // zenViewModel.toggleZenMode(false)
        }
    }

// Log de progreso (debugging)
    LaunchedEffect(timerRemaining) {
        if (timerRemaining > 0) {
            Log.d(TAG_UI, "Timer remaining: ${timerRemaining / 1000}s")
        }
    }

    // Compartir elemento actual (ACTION_SEND)
    fun shareCurrent() {
        val item = vm.current() ?: return
        val uri = item.uri
        val mime = ctx.contentResolver.getType(uri)
            ?: if (toKindInt(uri) == 1) "image/*" else "video/*"

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(intent, "Compartir con…"))
    }




    var swipeEnabled by remember { mutableStateOf(true) }
    LaunchedEffect(swipeEnabled) { Log.d(TAG_UI, "swipeEnabled=$swipeEnabled") }

    // Revisión (staging/confirm)
    val reviewLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data ?: return@rememberLauncherForActivityResult

        val staged: ArrayList<Uri> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                data.getParcelableArrayListExtra(EXTRA_STAGED_URIS, Uri::class.java) ?: arrayListOf()
            else @Suppress("DEPRECATION")
            data.getParcelableArrayListExtra(EXTRA_STAGED_URIS) ?: arrayListOf()

        val confirmed: ArrayList<Uri> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                data.getParcelableArrayListExtra(EXTRA_CONFIRMED_URIS, Uri::class.java) ?: arrayListOf()
            else @Suppress("DEPRECATION")
            data.getParcelableArrayListExtra(EXTRA_CONFIRMED_URIS) ?: arrayListOf()

        Log.d(TAG_UI, "ReviewActivity → staged=${staged.size}, confirmed=${confirmed.size}")
        if (staged.isNotEmpty())    vm.applyStagedSelection(staged)
        if (confirmed.isNotEmpty()) vm.confirmDeletionConfirmed(confirmed)
    }

    // Selector de índice desde la galería de miniaturas
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val idx = result.data?.getIntExtra(EXTRA_SELECTED_INDEX, clampedIndex) ?: clampedIndex
            Log.d(TAG_UI, "GalleryActivity → selected_index=$idx")
            vm.jumpTo(idx)
        }
    }

    Scaffold(
        containerColor = if (zenMode.isEnabled) Color.Transparent else MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            FancyTopBar(
                title = "SwipeClean",
                shownIndex = shownIndex,
                total = total,
                onUndo = { Log.d(TAG_UI, "TopBar.onUndo()"); vm.undo() },
                onReview = {
                    val toReview = vm.getPendingForReview()
                    Log.d(TAG_UI, "TopBar.onReview() → pending=${toReview.size}")
                    if (toReview.isEmpty()) return@FancyTopBar
                    val intent = Intent(ctx, ReviewActivity::class.java).apply {
                        putParcelableArrayListExtra(EXTRA_PENDING_URIS, toReview)
                    }
                    reviewLauncher.launch(intent)
                },
                currentFilter = vm.advancedFilter.collectAsState().value,
                onFilterChange = { vm.setAdvancedFilter(it) },
                onCounterClick = {
                    if (items.isEmpty()) return@FancyTopBar
                    val ids = LongArray(items.size) { i -> extractIdFromUri(items[i].uri) }
                    val kinds = IntArray(items.size) { i -> toKindInt(items[i].uri) }
                    val intent = Intent(ctx, GalleryActivity::class.java).apply {
                        putExtra("ids", ids)
                        putExtra("kinds", kinds)
                        putExtra("current_index", clampedIndex)
                    }
                    galleryLauncher.launch(intent)
                },
                onStatsClick = {
                    val intent = Intent(ctx, StatsActivity::class.java).apply {
                        putExtra("total_bytes", totalDeletedBytes)
                        putExtra("total_count", totalDeletedCount)
                    }
                    ctx.startActivity(intent)
                }
            )
        },
        bottomBar = {
            AnimatedVisibility(
                visible = !zenMode.isEnabled,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp, vertical = 32.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RoundActionIcon(
                        icon = R.drawable.ic_delete,
                        contentDesc = "Delete",
                        onClick = {
                            Log.d(TAG_UI, "BottomBar.delete → vm.markForTrash()")
                            vm.markForTrash()
                        },
                        size = 80.dp
                    )
                    RoundActionIcon(
                        icon = R.drawable.ic_share,
                        contentDesc = "Share",
                        onClick = {
                            Log.d(TAG_UI, "BottomBar.share → shareCurrent()")
                            shareCurrent()
                        },
                        size = 64.dp
                    )
                    RoundActionIcon(
                        icon = R.drawable.ic_check,
                        contentDesc = "Save",
                        onClick = {
                            Log.d(TAG_UI, "BottomBar.keep → vm.keep()")
                            vm.keep()
                        },
                        size = 80.dp
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            // Contenido principal
            AdaptiveBackdrop(currentItem) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    if (total == 0) {
                        Log.w(TAG_UI, "Galería vacía")
                        Text("No hay elementos en la galería", Modifier.align(Alignment.Center))
                    } else {
                        // Tarjeta de previsualización (siguiente item) - detrás
                        nextItem?.let { next ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp)
                                    .graphicsLayer {
                                        scaleX = 0.95f
                                        scaleY = 0.95f
                                        alpha = 0.5f
                                    }
                            ) {
                                MediaCard(
                                    item = next,
                                    isZenMode = zenMode.isEnabled,
                                    onSwipeEnabledChange = { /* No-op para preview */ }
                                )
                            }
                        }

                        // Tarjeta actual - encima
                        AnimatedContent(
                            targetState = clampedIndex,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(180)) togetherWith
                                        fadeOut(animationSpec = tween(120))
                            },
                            label = "MediaCrossfade"
                        ) { idx ->
                            val itemAt = items.getOrNull(idx)
                            Log.d(TAG_UI, "AnimatedContent idx=$idx uri=${itemAt?.uri}")

                            if (itemAt != null) {
                                SwipeableCard(
                                    swipeEnabled = swipeEnabled && !showMetadata,  // ← Deshabilitar swipe cuando se muestra metadata
                                    zenMode = zenMode,
                                    onSwipeLeft = {
                                        Log.d(TAG_UI, "onSwipeLeft → vm.markForTrash()")
                                        vm.markForTrash()
                                    },
                                    onSwipeRight = {
                                        Log.d(TAG_UI, "onSwipeRight → vm.keep()")
                                        vm.keep()
                                    },
                                    onSwipeUp = {
                                        Log.d(TAG_UI, "onSwipeUp → shareCurrent()")
                                        shareCurrent()
                                    },
                                    onSwipeDown = {
                                        val newState = !zenMode.isEnabled
                                        Log.d(
                                            TAG_UI,
                                            "onSwipeDown → toggle ZenMode: ${zenMode.isEnabled} → $newState"
                                        )
                                        zenViewModel.toggleZenMode(newState)
                                    }
                                ) {
                                    MediaCard(
                                        item = itemAt,
                                        isZenMode = zenMode.isEnabled,
                                        onSwipeEnabledChange = { enabled ->
                                            Log.d(TAG_UI, "onSwipeEnabledChange($enabled)")
                                            swipeEnabled = enabled
                                        },
                                        onLongPress = {
                                            Log.d(TAG_UI, "Long press → loading metadata")
                                            scope.launch {  // ← Cambiar de viewModelScope a scope
                                                currentMetadata = vm.getMediaMetadata(itemAt)
                                                showMetadata = true
                                            }
                                        }
                                    )
                                }

                                // Overlay de metadatos
                                if (showMetadata && currentMetadata != null) {
                                    MetadataOverlay(
                                        metadata = currentMetadata!!,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .pointerInput(Unit) {
                                                detectTapGestures {
                                                    showMetadata = false
                                                }
                                            }
                                    )
                                }

                                ZenModeOverlay(
                                    zenMode = zenMode,
                                    showMessage = showZenMessage,
                                    timerProgress = timerProgress,      // ← AÑADIR
                                    timerRemaining = timerRemaining,    // ← AÑADIR
                                    onDismiss = {
                                        Log.d(TAG_UI, "ZenMode dismissed")
                                        zenViewModel.toggleZenMode(false)
                                    },
                                    onAudioTrackChange = { track ->
                                        Log.d(
                                            TAG_UI,
                                            "Audio track changed to: ${track.displayName}"
                                        )
                                        zenViewModel.setAudioTrack(track)
                                    },
                                    onHapticsIntensityChange = { intensity ->
                                        Log.d(TAG_UI, "Haptics intensity changed to: $intensity")
                                        zenViewModel.setHapticsIntensity(intensity)
                                    },
                                    onTimerDurationChange = { duration ->
                                        Log.d(TAG_UI, "Timer duration changed to: $duration min")
                                        zenViewModel.setTimerDuration(duration)
                                    }
                                )
                            }else {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("Cargando…")
                                }
                            }
                        }
                    }
                }
            }

            // ← OVERLAY DE PROGRESO DE HASHING (NUEVO)
            if (isHashing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            progress = hashingProgress,
                            modifier = Modifier.size(64.dp),
                            strokeWidth = 6.dp
                        )
                        Text(
                            "Detectando duplicados...",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        Text(
                            "${(hashingProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

// Helpers
private fun extractIdFromUri(uri: Uri): Long =
    try { ContentUris.parseId(uri) } catch (_: Throwable) { -1L }

private fun toKindInt(uri: Uri): Int {
    val path = uri.path.orEmpty().lowercase()
    return when {
        path.contains("/images/") || path.contains("media/external/images") -> 1
        path.contains("/video/")  || path.contains("media/external/video")  -> 2
        else -> 1
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${bytes / (1024 * 1024)} MB"
    }
}