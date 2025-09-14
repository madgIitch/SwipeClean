package com.example.swipeclean

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.swipeclean.ui.components.AdaptiveBackdrop
import com.example.swipeclean.ui.components.FancyTopBar
import com.example.swipeclean.ui.components.MediaCard
import com.example.swipeclean.ui.components.RoundActionIcon
import com.example.swipeclean.ui.components.SwipeableCard
import com.example.swipeclean.ui.components.DebugGestureEnv
import com.madglitch.swipeclean.GalleryViewModel

private const val TAG_UI = "SwipeClean/UI"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardScreen(vm: GalleryViewModel) {
    DebugGestureEnv() // dump slop/threshold

    val items by vm.items.collectAsState()
    val index by vm.index.collectAsState()
    val filter by vm.filter.collectAsState()
    val ctx = LocalContext.current

    LaunchedEffect(items.size) { Log.d(TAG_UI, "items.size=${items.size}") }
    LaunchedEffect(index)      { Log.d(TAG_UI, "index=$index (items.size=${items.size})") }
    LaunchedEffect(filter)     { Log.d(TAG_UI, "filter=$filter") }

    val total = items.size
    val clampedIndex = if (total > 0) index.coerceIn(0, total - 1) else 0
    val shownIndex = if (total > 0) clampedIndex + 1 else 0
    val currentItem = items.getOrNull(clampedIndex)

    var swipeEnabled by remember { mutableStateOf(true) }
    LaunchedEffect(swipeEnabled) { Log.d(TAG_UI, "swipeEnabled=$swipeEnabled") }

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

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            FancyTopBar(
                title = "SwipeClean",
                shownIndex = shownIndex,
                total = total,
                onUndo = {
                    Log.d(TAG_UI, "TopBar.onUndo()"); vm.undo()
                },
                onReview = {
                    val toReview = vm.getPendingForReview()
                    Log.d(TAG_UI, "TopBar.onReview() → pending=${toReview.size}")
                    if (toReview.isEmpty()) return@FancyTopBar
                    val intent = Intent(ctx, ReviewActivity::class.java).apply {
                        putParcelableArrayListExtra(EXTRA_PENDING_URIS, toReview)
                    }
                    reviewLauncher.launch(intent)
                },
                currentFilter = filter,
                onFilterChange = {
                    Log.d(TAG_UI, "TopBar.onFilterChange($it)"); vm.setFilter(it)
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
                    onClick = { Log.d(TAG_UI, "BottomBar.delete → vm.markForTrash()"); vm.markForTrash() },
                    container = MaterialTheme.colorScheme.errorContainer,
                    content   = MaterialTheme.colorScheme.onErrorContainer,
                    size = 80.dp
                )
                RoundActionIcon(
                    icon = R.drawable.ic_check,
                    contentDesc = "Guardar",
                    onClick = { Log.d(TAG_UI, "BottomBar.keep → vm.keep()"); vm.keep() },
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
                    Log.w(TAG_UI, "Galería vacía")
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
                        Log.d(TAG_UI, "AnimatedContent idx=$idx uri=${itemAt?.uri}")
                        if (itemAt != null) {
                            SwipeableCard(
                                swipeEnabled = swipeEnabled,
                                onSwipeLeft  = { Log.d(TAG_UI, "onSwipeLeft → vm.markForTrash()"); vm.markForTrash() },
                                onSwipeRight = { Log.d(TAG_UI, "onSwipeRight → vm.keep()"); vm.keep() }
                            ) {
                                MediaCard(
                                    item = itemAt,
                                    onSwipeEnabledChange = { enabled ->
                                        Log.d(TAG_UI, "onSwipeEnabledChange($enabled)")
                                        swipeEnabled = enabled
                                    }
                                )
                            }
                        } else {
                            Log.w(TAG_UI, "itemAt=null en idx=$idx → Loading")
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
