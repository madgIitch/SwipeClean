package com.example.swipeclean.ui.components

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterList

import com.tuempresa.swipeclean.MediaFilter

@Composable
fun FilterDropdown(
    current: MediaFilter,
    onSelected: (MediaFilter) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    IconButton(onClick = { expanded = true }) {
        Icon(Icons.Outlined.FilterList, contentDescription = "Filtro")
    }

    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        @Composable
        fun Item(text: String, value: MediaFilter) = DropdownMenuItem(
            text = { Text(text + if (current == value) " •" else "") },
            onClick = { expanded = false; onSelected(value) }
        )
        Item("Todo", MediaFilter.ALL)
        Item("Solo fotos", MediaFilter.IMAGES)
        Item("Solo vídeos", MediaFilter.VIDEOS)
    }
}
