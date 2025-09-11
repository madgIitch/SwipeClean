package com.example.swipeclean

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.swipeclean.ui.theme.SwipeCleanTheme
import java.util.Locale



class ReviewActivity : ComponentActivity() {

    private lateinit var selected: SnapshotStateList<Uri>

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val initialPending: ArrayList<Uri> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayListExtra(EXTRA_PENDING_URIS, Uri::class.java) ?: arrayListOf()
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra(EXTRA_PENDING_URIS) ?: arrayListOf()
            }

        // Tras el diálogo del sistema, devolvemos las URIs confirmadas
        val trashLauncher = registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { _ ->
            val data = Intent().apply {
                putParcelableArrayListExtra(EXTRA_CONFIRMED_URIS, ArrayList(selected))
            }
            setResult(RESULT_OK, data)
            finish()
        }

        setContent {
            SwipeCleanTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    color = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground
                ) {
                    var items by remember { mutableStateOf(initialPending.toList()) }
                    val allCount = items.size

                    selected = remember { mutableStateListOf<Uri>() }
                    LaunchedEffect(Unit) { selected.clear(); selected.addAll(items) }

                    val selectedBytes by remember(selected.toList()) {
                        mutableStateOf(sumSizesSafely(selected))
                    }

                    val selectAllState: ToggleableState = when {
                        selected.isEmpty()       -> ToggleableState.Off
                        selected.size == allCount -> ToggleableState.On
                        else                      -> ToggleableState.Indeterminate
                    }

                    Scaffold(
                        containerColor = MaterialTheme.colorScheme.background,
                        contentColor = MaterialTheme.colorScheme.onBackground,
                        topBar = {
                            TopAppBar(
                                title = { Text("Revisión para borrar") },
                                navigationIcon = {
                                    // Volver ➜ devolver selección staged (sin borrar aún)
                                    IconButton(onClick = {
                                        val data = Intent().apply {
                                            putParcelableArrayListExtra(EXTRA_STAGED_URIS, ArrayList(selected))
                                        }
                                        setResult(RESULT_OK, data)
                                        finish()
                                    }) {
                                        Icon(
                                            painterResource(id = R.drawable.ic_undo),
                                            contentDescription = "Volver"
                                        )
                                    }
                                },
                                actions = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .padding(end = 12.dp)
                                            .clip(RoundedCornerShape(20.dp))
                                            .clickable(
                                                role = Role.Checkbox,
                                                onClick = {
                                                    if (selected.size == allCount) selected.clear()
                                                    else { selected.clear(); selected.addAll(items) }
                                                }
                                            )
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        TriStateCheckbox(
                                            state = selectAllState,
                                            onClick = {
                                                if (selected.size == allCount) selected.clear()
                                                else { selected.clear(); selected.addAll(items) }
                                            }
                                        )
                                        Text("Seleccionar todo")
                                    }
                                }
                            )
                        },
                        bottomBar = {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.background)
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    text = "Espacio a liberar: ${formatSize(selectedBytes)}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            val data = Intent().apply {
                                                putParcelableArrayListExtra(EXTRA_STAGED_URIS, ArrayList(selected))
                                            }
                                            setResult(RESULT_OK, data)
                                            finish()
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) { Text("Volver") }

                                    Button(
                                        onClick = {
                                            val toTrash = selected.toList()
                                            if (toTrash.isEmpty()) {
                                                setResult(RESULT_CANCELED); finish(); return@Button
                                            }
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                                val pi = MediaStore.createTrashRequest(
                                                    contentResolver,
                                                    ArrayList(toTrash),
                                                    true
                                                )
                                                trashLauncher.launch(
                                                    IntentSenderRequest.Builder(pi.intentSender).build()
                                                )
                                            } else {
                                                // API < 30: borrado directo
                                                val cr = contentResolver
                                                toTrash.forEach { uri ->
                                                    try { cr.delete(uri, null, null) } catch (_: Exception) {}
                                                }
                                                val data = Intent().apply {
                                                    putParcelableArrayListExtra(EXTRA_CONFIRMED_URIS, ArrayList(toTrash))
                                                }
                                                setResult(RESULT_OK, data)
                                                finish()
                                            }
                                        },
                                        enabled = selected.isNotEmpty(),
                                        modifier = Modifier.weight(1f)
                                    ) { Text("Mover a papelera (${selected.size})") }
                                }
                            }
                        }
                    ) { padding ->
                        Column(
                            Modifier
                                .padding(padding)
                                .padding(16.dp)
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background)
                        ) {
                            Text("Seleccionados: ${selected.size} de $allCount")
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Espacio que se liberará: ${formatSize(selectedBytes)}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.height(12.dp))

                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(items, key = { it }) { uri ->
                                    SelectableThumb(
                                        uri = uri,
                                        selected = selected.contains(uri),
                                        onToggle = {
                                            if (selected.contains(uri)) selected.remove(uri)
                                            else selected.add(uri)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ---- Helpers ----
    private fun sumSizesSafely(uris: List<Uri>): Long {
        var total = 0L
        val projection = arrayOf(OpenableColumns.SIZE)
        for (uri in uris) {
            try {
                contentResolver.query(uri, projection, null, null, null)?.use { c ->
                    val idx = c.getColumnIndex(OpenableColumns.SIZE)
                    if (idx != -1 && c.moveToFirst() && !c.isNull(idx)) {
                        total += c.getLong(idx)
                    }
                }
            } catch (_: Exception) { /* ignore */ }
        }
        return total
    }

    private fun formatSize(bytes: Long): String {
        val kb = 1024.0
        val mb = kb * 1024
        val gb = mb * 1024
        return when {
            bytes >= gb -> String.format(Locale.getDefault(), "%.2f GB", bytes / gb)
            bytes >= mb -> String.format(Locale.getDefault(), "%.2f MB", bytes / mb)
            bytes >= kb -> String.format(Locale.getDefault(), "%.2f KB", bytes / kb)
            else -> "$bytes B"
        }
    }
}

@Composable
private fun SelectableThumb(
    uri: Uri,
    selected: Boolean,
    onToggle: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)

    Box(
        modifier = Modifier
            .clip(shape)
            .clickable { onToggle() }
            .fillMaxWidth()
            .aspectRatio(1f)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(uri)
                .size(256)
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier = Modifier
                .matchParentSize()
                .clip(shape)
        )

        if (selected) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color(0x552B2BFF))
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(1.dp)
                    .clip(shape)
                    .border(2.dp, Color(0xFF6750A4), shape)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xFF6750A4))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text("✓", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}
