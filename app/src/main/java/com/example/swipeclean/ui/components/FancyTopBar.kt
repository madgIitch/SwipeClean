package com.example.swipeclean.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.swipeclean.R
import com.madglitch.swipeclean.GalleryViewModel
import com.tuempresa.swipeclean.MediaFilter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FancyTopBar(
    title: String,
    shownIndex: Int,
    total: Int,
    onUndo: () -> Unit,
    onReview: () -> Unit, currentFilter: GalleryViewModel.AdvancedFilter,  // ← Cambiar de MediaFilter
    onFilterChange: (GalleryViewModel.AdvancedFilter) -> Unit,  // ← Cambiar de MediaFilter
    onCounterClick: () -> Unit,
    onStatsClick: () -> Unit,
) {
    CenterAlignedTopAppBar(
        windowInsets = TopAppBarDefaults.windowInsets,
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.onSurface
        ),
        navigationIcon = {
            IconButton(onClick = onUndo, enabled = total > 0) {
                Icon(
                    painterResource(R.drawable.ic_undo),
                    contentDescription = "Deshacer",
                    tint = Color(0xFFFFFFFF)
                )
            }
        },
        title = {
            Surface(
                color = Color.Transparent, // ✅ Fondo transparente
                shape = RoundedCornerShape(12.dp),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = title, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.width(8.dp))
                    // 👇 Reemplaza el texto por un CounterPill clicable
                    CounterPill(
                        current = shownIndex,
                        total = total,
                        onClick = {
                            if (total > 0) onCounterClick()
                        }
                    )
                }
            }
        },
        actions = {
            // Botón de estadísticas (NUEVO)
            IconButton(onClick = onStatsClick) {
                Icon(
                    painterResource(R.drawable.ic_stats), // necesitarás este icono
                    contentDescription = "Estadísticas",
                    tint = Color(0xFFFFFFFF)
                )
            }

            // En FancyTopBar.kt, línea 80-84
            var showFilterDialog by remember { mutableStateOf(false) }

            IconButton(onClick = { showFilterDialog = true }) {
                Icon(
                    Icons.Outlined.FilterList,
                    contentDescription = "Filtros avanzados",
                    tint = Color.White
                )
            }

            if (showFilterDialog) {
                AdvancedFilterDialog(
                    currentFilter = currentFilter,
                    onFilterApplied = { newFilter ->
                        onFilterChange(newFilter)
                        showFilterDialog = false
                    },
                    onDismiss = { showFilterDialog = false }
                )
            }

            // Botón de revisión manual
            IconButton(onClick = onReview) {
                Icon(
                    painterResource(R.drawable.ic_next),
                    contentDescription = "Revisión",
                    tint = Color(0xFF4CAF50)
                )
            }

        }
    )
}
