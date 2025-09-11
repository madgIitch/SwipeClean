package com.example.swipeclean

import android.content.IntentSender
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.example.swipeclean.ui.components.RoundActionIcon
import com.madglitch.swipeclean.GalleryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardScreen(
    vm: GalleryViewModel,
    autoBatchSize: Int = 5 // confirma en lote cada N elementos marcados
) {
    val items by vm.items.collectAsState()
    val index by vm.index.collectAsState()
    val context = LocalContext.current

    // Launcher para ejecutar el IntentSender (API 30+)
    val intentSenderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { /* opcional: comprobar resultCode */ }

    // Confirma el lote si toca (usa compat para API<30)
    fun maybeConfirmTrashBatch() {
        if (vm.pendingCount() > 0 &&
            (vm.pendingCount() >= autoBatchSize || index >= items.size)
        ) {
            vm.confirmTrashCompat(context) { sender: IntentSender ->
                intentSenderLauncher.launch(
                    IntentSenderRequest.Builder(sender).build()
                )
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("SwipeClean") }) },
        bottomBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                RoundActionIcon(
                    icon = R.drawable.ic_delete,
                    contentDesc = "Borrar",
                    onClick = {
                        vm.markForTrash()
                        maybeConfirmTrashBatch()
                    },
                    container = MaterialTheme.colorScheme.errorContainer,
                    content = MaterialTheme.colorScheme.onErrorContainer
                )

                RoundActionIcon(
                    icon = R.drawable.ic_check,
                    contentDesc = "Guardar",
                    onClick = { vm.keep() },
                    container = MaterialTheme.colorScheme.primaryContainer,
                    content = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

    )
    { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val media = items.getOrNull(index)

            if (media == null) {
                // Al llegar al final, si quedan pendientes, confirma el lote.
                LaunchedEffect(Unit) { maybeConfirmTrashBatch() }
                Text(
                    text = "No hay más elementos",
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                SwipeableMediaCard(
                    item = media,
                    onSwipedLeft = {
                        vm.markForTrash()
                        maybeConfirmTrashBatch()
                    },
                    onSwipedRight = { vm.keep() }
                )
            }
        }
    }
}

@Composable
fun SwipeableMediaCard(
    item: MediaItem,
    onSwipedLeft: () -> Unit,
    onSwipedRight: () -> Unit
) {
    var offsetX by remember { mutableStateOf(0f) }
    val threshold = 200f
    val context = LocalContext.current

    Box(
        Modifier
            .fillMaxSize()
            .padding(24.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(24.dp)
            )
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        when {
                            offsetX < -threshold -> { onSwipedLeft(); offsetX = 0f }
                            offsetX >  threshold -> { onSwipedRight(); offsetX = 0f }
                            else -> offsetX = 0f
                        }
                    }
                ) { change, drag ->
                    change.consume() // marca el evento como consumido
                    offsetX += drag.x
                }
            }
            .graphicsLayer {
                translationX = offsetX
                rotationZ = (offsetX / 40f).coerceIn(-12f, 12f)
            }
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Mostrar a resolución original y sin recorte
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(item.uri)
                .size(Size.ORIGINAL)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
    }
}
