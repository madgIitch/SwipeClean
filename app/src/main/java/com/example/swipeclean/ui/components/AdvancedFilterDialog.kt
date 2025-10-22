package com.example.swipeclean.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.madglitch.swipeclean.GalleryViewModel
import com.tuempresa.swipeclean.MediaFilter
import java.util.Locale

@Composable
fun AdvancedFilterDialog(
    currentFilter: GalleryViewModel.AdvancedFilter,  // ← Tipo correcto
    onFilterApplied: (GalleryViewModel.AdvancedFilter) -> Unit,
    onDismiss: () -> Unit
) {
    var tempFilter by remember { mutableStateOf(currentFilter) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filtros Avanzados") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Tipo de medio (chips)
                Text("Tipo de medio", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = tempFilter.mediaType == GalleryViewModel.MediaType.ALL,
                        onClick = { tempFilter = tempFilter.copy(mediaType = GalleryViewModel.MediaType.ALL) },
                        label = { Text("Todo") }
                    )
                    FilterChip(
                        selected = tempFilter.mediaType == GalleryViewModel.MediaType.IMAGES,
                        onClick = { tempFilter = tempFilter.copy(mediaType = GalleryViewModel.MediaType.IMAGES) },
                        label = { Text("Fotos") }
                    )
                    FilterChip(
                        selected = tempFilter.mediaType == GalleryViewModel.MediaType.VIDEOS,
                        onClick = { tempFilter = tempFilter.copy(mediaType = GalleryViewModel.MediaType.VIDEOS) },
                        label = { Text("Vídeos") }
                    )
                }

                // Rango de fechas
                Text("Fecha", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = tempFilter.dateRange is GalleryViewModel.DateRange.Last7Days,
                        onClick = { tempFilter = tempFilter.copy(dateRange = GalleryViewModel.DateRange.Last7Days) },
                        label = { Text("7 días") }
                    )
                    FilterChip(
                        selected = tempFilter.dateRange is GalleryViewModel.DateRange.LastMonth,
                        onClick = { tempFilter = tempFilter.copy(dateRange = GalleryViewModel.DateRange.LastMonth) },
                        label = { Text("Mes") }
                    )
                    FilterChip(
                        selected = tempFilter.dateRange is GalleryViewModel.DateRange.LastYear,
                        onClick = { tempFilter = tempFilter.copy(dateRange = GalleryViewModel.DateRange.LastYear) },
                        label = { Text("Año") }
                    )
                }

                // Tamaño de archivo (slider)
                Text("Tamaño de archivo", style = MaterialTheme.typography.titleSmall)
                var sizeRange by remember {
                    mutableStateOf(tempFilter.sizeRange ?: GalleryViewModel.SizeRange(0L, 100L * 1024 * 1024))
                }
                RangeSlider(
                    value = sizeRange.minBytes.toFloat()..sizeRange.maxBytes.toFloat(),
                    onValueChange = { range ->
                        sizeRange = GalleryViewModel.SizeRange(range.start.toLong(), range.endInclusive.toLong())
                        tempFilter = tempFilter.copy(sizeRange = sizeRange)
                    },
                    valueRange = 0f..(100f * 1024 * 1024),
                    steps = 10
                )
                Text("${formatBytes(sizeRange.minBytes)} - ${formatBytes(sizeRange.maxBytes)}")

                // Ubicación GPS
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Solo con ubicación GPS")
                    Switch(
                        checked = tempFilter.hasLocation == true,
                        onCheckedChange = {
                            tempFilter = tempFilter.copy(hasLocation = if (it) true else null)
                        }
                    )
                }

                // Duplicados
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Solo duplicados")
                    Switch(
                        checked = tempFilter.showDuplicatesOnly,
                        onCheckedChange = {
                            tempFilter = tempFilter.copy(showDuplicatesOnly = it)
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onFilterApplied(tempFilter)
                onDismiss()
            }) {
                Text("Aplicar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

private fun formatBytes(bytes: Long): String {
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