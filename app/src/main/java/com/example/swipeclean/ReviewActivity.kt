package com.example.swipeclean

import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest

class ReviewActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pending: ArrayList<Uri> =
            intent.getParcelableArrayListExtra("PENDING_URIS") ?: arrayListOf()

        // Launcher para el diálogo del sistema (API 30+)
        val trashLauncher = registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { _ ->
            // Vuelves a la pantalla anterior tras confirmar
            finish()
        }

        setContent {
            MaterialTheme {
                Scaffold(
                    topBar = { TopAppBar(title = { Text("Revisión para borrar") }) },
                    bottomBar = {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = { finish() },
                                modifier = Modifier.weight(1f)
                            ) { Text("Volver") }

                            Button(
                                onClick = {
                                    if (pending.isEmpty()) {
                                        finish()
                                        return@Button
                                    }
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                        val pi = MediaStore.createTrashRequest(
                                            contentResolver,
                                            pending,
                                            true
                                        )
                                        trashLauncher.launch(
                                            IntentSenderRequest.Builder(pi.intentSender).build()
                                        )
                                    } else {
                                        // API < 30: borrado directo (no hay papelera del sistema)
                                        val cr = contentResolver
                                        pending.forEach { uri ->
                                            try { cr.delete(uri, null, null) } catch (_: Exception) {}
                                        }
                                        finish()
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("Mover a papelera (${pending.size})") }
                        }
                    }
                ) { padding ->
                    Column(
                        Modifier.padding(padding).padding(16.dp).fillMaxSize()
                    ) {
                        Text("Seleccionados: ${pending.size}")
                        Spacer(Modifier.height(12.dp))

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(pending, key = { it }) { uri ->
                                ElevatedCard {
                                    AsyncImage(
                                        model = ImageRequest.Builder(this@ReviewActivity)
                                            .data(uri)
                                            .size(256)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
