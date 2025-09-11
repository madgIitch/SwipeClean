package com.example.swipeclean.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color

@Composable
fun RoundActionIcon(
    @DrawableRes icon: Int,
    contentDesc: String,
    onClick: () -> Unit,
    container: Color,
    content: Color
) {
    FilledIconButton(
        onClick = onClick,
        modifier = Modifier.size(64.dp),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = container,
            contentColor = content
        )
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDesc,
            modifier = Modifier.size(28.dp)
        )
    }
}
