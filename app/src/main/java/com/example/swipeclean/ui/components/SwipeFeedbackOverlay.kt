package com.example.swipeclean.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
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
fun SwipeFeedbackOverlay(progress: Float) {
    // progress: -1 (izq/borrar) → 0 → +1 (dcha/guardar)
    val alpha = min(1f, abs(progress))
    val isRight = progress > 0f
    val label = if (isRight) "Guardar" else "Papelera"
    val iconRes = if (isRight) R.drawable.ic_check else R.drawable.ic_delete

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = if (isRight) Alignment.TopStart else Alignment.TopEnd
    ) {
        AssistChip(
            onClick = {},
            leadingIcon = { Icon(painterResource(iconRes), contentDescription = null) },
            label = { Text(label) },
            enabled = false,
            modifier = Modifier.graphicsLayer { this.alpha = alpha }
        )
    }
}
