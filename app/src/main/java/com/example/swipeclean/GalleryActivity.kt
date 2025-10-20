package com.example.swipeclean

import android.app.Activity
import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest

private const val TAG_GALLERY = "SwipeClean/Gallery"

// Extras esperados
private const val EXTRA_IDS = "ids"
private const val EXTRA_KINDS = "kinds"         // 1=image, 2=video
private const val EXTRA_CURRENT_INDEX = "current_index"
const val EXTRA_SELECTED_INDEX = "selected_index"

class GalleryActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val ids = intent.getLongArrayExtra(EXTRA_IDS) ?: longArrayOf()
        val kinds = intent.getIntArrayExtra(EXTRA_KINDS) ?: intArrayOf()
        val currentIndex = intent.getIntExtra(EXTRA_CURRENT_INDEX, 0)

        if (ids.isEmpty() || kinds.isEmpty() || ids.size != kinds.size) {
            Log.w(TAG_GALLERY, "Extras inválidos: ids=${ids.size}, kinds=${kinds.size}")
        }

        val uris: List<Uri> = ids.indices.map { i ->
            reconstructUri(ids[i], kinds.getOrNull(i) ?: 1)
        }

        setContent {
            MaterialTheme {
                GalleryScreen(
                    uris = uris,
                    kinds = kinds.toList(),
                    initialIndex = currentIndex,
                    onClose = { finish() },
                    onPick = { index ->
                        setResult(
                            Activity.RESULT_OK,
                            Intent().putExtra(EXTRA_SELECTED_INDEX, index)
                        )
                        finish()
                    }
                )
            }
        }
    }
}

/** Reconstruye el Uri de MediaStore a partir de (id, kind). kind: 1 imagen, 2 vídeo. */
private fun reconstructUri(id: Long, kind: Int): Uri {
    val base = if (kind == 2) {
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    } else {
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }
    return ContentUris.withAppendedId(base, id)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun GalleryScreen(
    uris: List<Uri>,
    kinds: List<Int>,
    initialIndex: Int,
    onClose: () -> Unit,
    onPick: (Int) -> Unit
) {
    var selectedIndex by remember {
        mutableStateOf(initialIndex.coerceIn(0, (uris.size - 1).coerceAtLeast(0)))
    }

    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = initialIndex.coerceIn(0, (uris.size - 1).coerceAtLeast(0))
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Galería", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${(selectedIndex + 1).coerceAtMost(uris.size)} / ${uris.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Volver"
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = { onPick(selectedIndex) },
                        enabled = uris.isNotEmpty()
                    ) { Text("Seleccionar") }
                }
            )
        }
    ) { padding ->
        if (uris.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("No hay elementos")
            }
            return@Scaffold
        }

        LazyVerticalGrid(
            state = gridState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            columns = GridCells.Adaptive(minSize = 120.dp),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(
                items = uris,
                key = { index, _ -> index }
            ) { index, uri ->
                val isVideo = kinds.getOrNull(index) == 2
                val isSelected = index == selectedIndex

                GalleryTile(
                    uri = uri,
                    isVideo = isVideo,
                    isSelected = isSelected,
                    onClick = { selectedIndex = index },
                    onDoubleClick = { onPick(index) }
                )
            }
        }
    }
}

@Composable
private fun GalleryTile(
    uri: Uri,
    isVideo: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit
) {
    // Doble click simple sin libs externas
    var lastClickTime by remember { mutableStateOf(0L) }
    fun handleClick() {
        val now = System.currentTimeMillis()
        if (now - lastClickTime < 250L) onDoubleClick() else onClick()
        lastClickTime = now
    }

    val borderColor =
        if (isSelected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outlineVariant

    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = { handleClick() }
            )
            .then(Modifier.padding(0.dp)),
        contentAlignment = Alignment.BottomStart
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(uri)
                // Para vídeos, Coil puede extraer un frame automáticamente; si quieres forzar:
                // .videoFrameMillis(1000)
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        if (isVideo) {
            Surface(
                color = Color.Black.copy(alpha = 0.45f),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = "VIDEO",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(2.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(Color.Transparent)
                .border(
                    width = if (isSelected) 3.dp else 1.dp,
                    color = borderColor,
                    shape = MaterialTheme.shapes.medium
                )
        )
    }
}
