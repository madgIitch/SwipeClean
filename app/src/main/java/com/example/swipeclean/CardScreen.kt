package com.example.swipeclean

import android.content.IntentSender
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardScreen(
    vm: GalleryViewModel,
    onNeedUserConfirm: (IntentSender) -> Unit
) {
    val items by vm.items.collectAsState()
    val index by vm.index.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = { TopAppBar(title = { Text("SwipeClean") }) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedButton(onClick = {
                    vm.trash(context, onNeedUserConfirm)
                    scope.launch {
                        val res = snackbar.showSnackbar("Enviada a papelera", "Deshacer")
                        if (res == SnackbarResult.ActionPerformed) vm.undo()
                    }
                }) { Text("Borrar") }

                OutlinedButton(onClick = { vm.keep() }) { Text("Guardar") }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val media = items.getOrNull(index)
            if (media == null) {
                Text(
                    "No hay más elementos",
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                SwipeableMediaCard(
                    item = media,
                    onSwipedLeft = {
                        vm.trash(context, onNeedUserConfirm)
                        scope.launch {
                            val r = snackbar.showSnackbar("Enviada a papelera", "Deshacer")
                            if (r == SnackbarResult.ActionPerformed) vm.undo()
                        }
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

    Box(
        Modifier
            .fillMaxSize()
            .padding(24.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(24.dp))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        when {
                            offsetX < -threshold -> { onSwipedLeft(); offsetX = 0f }
                            offsetX > threshold  -> { onSwipedRight(); offsetX = 0f }
                            else -> offsetX = 0f
                        }
                    }
                ) { change, drag ->
                    change.consume()
                    offsetX += drag.x
                }
            }
            .graphicsLayer {
                translationX = offsetX
                rotationZ = (offsetX / 40f).coerceIn(-12f, 12f)
                // opcional: alpha feedback
                alpha = (1f - (abs(offsetX) / 1200f)).coerceIn(0.6f, 1f)
            }
    ) {
        // Para vídeos, Coil muestra thumbnail automáticamente
        AsyncImage(
            model = item.uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}
