package com.example.swipeclean

import android.content.Intent
import android.content.IntentSender
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.example.swipeclean.ui.components.CounterPill
import com.example.swipeclean.ui.components.RoundActionIcon
import com.madglitch.swipeclean.GalleryViewModel


@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardScreen(vm: GalleryViewModel) {
    val items by vm.items.collectAsState()
    val index by vm.index.collectAsState()
    val ctx = LocalContext.current

    val total = items.size
    val shownIndex = if (total > 0) (index % total) + 1 else 0
    val isEmpty = total == 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("SwipeClean")
                        Spacer(Modifier.width(12.dp))
                        // Indicador  actual/total
                        CounterPill(current = shownIndex, total = total)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { vm.undo() }, enabled = !isEmpty) {
                        Icon(
                            painterResource(R.drawable.ic_undo),
                            contentDescription = "Deshacer"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        // Abrir revisión manualmente (sin pedir permisos aquí)
                        ctx.startActivity(
                            Intent(ctx, ReviewActivity::class.java).apply {
                                putParcelableArrayListExtra(
                                    "PENDING_URIS",
                                    ArrayList(vm.getPendingTrash())
                                )
                            }
                        )
                    }) {
                        Icon(
                            painterResource(R.drawable.ic_next),
                            contentDescription = "Revisión"
                        )
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
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val media = items.getOrNull(index)
            if (media != null) {
                SwipeableMediaCard(
                    item = media,
                    onSwipedLeft  = { vm.markForTrash() },
                    onSwipedRight = { vm.keep() }
                )
            } else {
                // Lista vacía (p.ej., filtro sin resultados)
                Text(
                    "No hay elementos en la galería",
                    Modifier.align(Alignment.Center)
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
