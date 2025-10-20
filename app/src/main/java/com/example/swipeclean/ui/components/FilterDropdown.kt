package com.example.swipeclean.ui.components

import androidx.compose.foundation.background
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

import com.tuempresa.swipeclean.MediaFilter

@Composable
fun FilterDropdown(
    current: MediaFilter,
    onSelected: (MediaFilter) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    // ✅ Botón para abrir el menú
    IconButton(onClick = { expanded = true }) {
        Icon(
            Icons.Outlined.FilterList,
            contentDescription = "Filtro",
            tint = Color.White // ✅ Icono blanco
        )
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
        modifier = Modifier.background(Color.White) // ✅ Fondo blanco
    ) {
        // ✅ Definir la función Item DENTRO del DropdownMenu
        @Composable
        fun Item(text: String, value: MediaFilter) {
            DropdownMenuItem(
                text = {
                    Text(
                        text + if (current == value) " •" else "",
                        color = Color.Black // ✅ Texto negro para contraste
                    )
                },
                onClick = {
                    expanded = false
                    onSelected(value)
                }
            )
        }

        // ✅ Ahora sí puedes llamar a Item
        Item("Todo", MediaFilter.ALL)
        Item("Solo fotos", MediaFilter.IMAGES)
        Item("Solo vídeos", MediaFilter.VIDEOS)
    }
}