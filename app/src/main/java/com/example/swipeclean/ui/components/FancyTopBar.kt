package com.example.swipeclean.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.swipeclean.R
import com.tuempresa.swipeclean.MediaFilter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FancyTopBar(
    title: String,
    shownIndex: Int,
    total: Int,
    onUndo: () -> Unit,
    onReview: () -> Unit,
    currentFilter: MediaFilter,
    onFilterChange: (MediaFilter) -> Unit
) {
    CenterAlignedTopAppBar(
        windowInsets = TopAppBarDefaults.windowInsets, // respeta status bar
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
                    tint = Color(0xFFFF9800) // 🔸 Naranja estilo Tinder
                )
            }
        },
        title = {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.35f),
                shape = RoundedCornerShape(12.dp),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = title, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "$shownIndex / $total",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                    )
                }
            }
        },
        actions = {
            // Botón de revisión manual
            IconButton(onClick = onReview) {
                Icon(
                    painterResource(R.drawable.ic_next),
                    contentDescription = "Revisión",
                    tint = Color(0xFF4CAF50) // ✅ Verde estilo Tinder
                )
            }
            // Nuevo: menú desplegable para el filtro
            FilterDropdown(
                current = currentFilter,
                onSelected = onFilterChange
            )
        }
    )
}
