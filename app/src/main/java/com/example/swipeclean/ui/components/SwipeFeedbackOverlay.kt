package com.example.swipeclean.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.swipeclean.R
import kotlin.math.abs
import kotlin.math.min

@Composable
fun SwipeFeedbackOverlay(
    progressX: Float,
    upProgress: Float,
    downProgress: Float = 0f,
    isZenMode: Boolean = false
) {
    val alphaX = min(1f, abs(progressX))
    val isRight = progressX > 0f

    // Textos según modo
    val labelX = if (isZenMode) {
        if (isRight) "Conservar" else "Liberar"
    } else {
        if (isRight) "Guardar" else "Papelera"
    }

    val iconResX = if (isRight) R.drawable.ic_check else R.drawable.ic_delete

    // Colores suaves en ZenMode
    val chipColors = if (isZenMode) {
        AssistChipDefaults.assistChipColors(
            containerColor = ZenLavender.copy(alpha = 0.2f),
            labelColor = ZenSand
        )
    } else {
        AssistChipDefaults.assistChipColors()
    }

    Box(Modifier.fillMaxSize()) {
        AssistChip(
            onClick = {},
            leadingIcon = { Icon(painterResource(iconResX), null) },
            label = { Text(labelX) },
            enabled = false,
            colors = chipColors,
            modifier = Modifier
                .padding(24.dp)
                .align(if (isRight) Alignment.TopStart else Alignment.TopEnd)
                .graphicsLayer { alpha = alphaX }
        )

        // Indicador de compartir (swipe up)
        if (upProgress > 0.01f) {
            AssistChip(
                onClick = {},
                leadingIcon = { Icon(painterResource(R.drawable.ic_share), null) },
                label = { Text("Compartir") },
                enabled = false,
                colors = chipColors,
                modifier = Modifier
                    .padding(top = 24.dp)
                    .align(Alignment.TopCenter)
                    .graphicsLayer { alpha = upProgress.coerceIn(0f, 1f) }
            )
        }
    }
}