package com.example.swipeclean

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.swipeclean.ui.theme.SwipeCleanTheme
import java.util.Locale
import kotlin.math.roundToInt

class StatsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val totalBytes = intent.getLongExtra("total_bytes", 0L)
        val totalCount = intent.getIntExtra("total_count", 0)


        setContent {
            SwipeCleanTheme {
                StatsScreen(
                    totalBytes = totalBytes,
                    totalCount = totalCount,
                    onBack = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    totalBytes: Long,
    totalCount: Int,
    onBack: () -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(
                            "Estadísticas",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            "Resumen de limpieza",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.ic_back),
                            contentDescription = "Volver"
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                HeroStatCard(
                    title = "Espacio liberado total",
                    bytes = totalBytes
                )
            }

            item {
                StatChipsRow(
                    items = listOf(
                        StatChip(
                            "Elementos eliminados",
                            totalCount.toString(),
                            Icons.Filled.Delete
                        )
                    )
                )
            }

            if (totalBytes > 0L) {
                item {
                    ProgressBlock(totalBytes = totalBytes, totalCount = totalCount)
                }
            } else {
                item {
                    EmptyStateCard(
                        message = "Aún no has liberado espacio",
                        hint = "Desliza a la izquierda los elementos que no quieras conservar."
                    )
                }
            }

            item { Spacer(Modifier.height(8.dp)) }

            item {
                Text(
                    "¡Sigue limpiando para liberar más espacio!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/* ---------- UI Components ---------- */

@Composable
private fun HeroStatCard(
    title: String,
    bytes: Long,
    modifier: Modifier = Modifier
) {
    val gradient = Brush.verticalGradient(
        listOf(
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f)
        )
    )

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp)
    ) {
        Box(
            Modifier
                .background(gradient)
                .padding(22.dp)
        ) {
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PillHeader(
                    icon = Icons.Filled.Storage,
                    text = title,
                    container = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.08f),
                    content = MaterialTheme.colorScheme.onPrimaryContainer
                )
                AnimatedMetricValue(
                    targetBytes = bytes,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun PillHeader(
    icon: ImageVector,
    text: String,
    container: Color,
    content: Color
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(container)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = content, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = content)
    }
}

@Composable
private fun MetricValue(
    value: String,
    unit: String,
    color: Color
) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = value,
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = unit,
            style = MaterialTheme.typography.titleLarge,
            color = color.copy(alpha = 0.8f)
        )
    }
}

@Composable
private fun AnimatedMetricValue(
    targetBytes: Long,
    color: Color
) {
    var animatedBytes by remember { mutableStateOf(0L) }

    LaunchedEffect(targetBytes) {
        animate(
            initialValue = 0f,
            targetValue = targetBytes.toFloat(),
            animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing)
        ) { value, _ ->
            animatedBytes = value.toLong()
        }
    }

    MetricValue(
        value = formatSizeNumber(animatedBytes),
        unit = formatSizeUnit(animatedBytes),
        color = color
    )
}

data class StatChip(val title: String, val value: String, val icon: ImageVector)

@Composable
private fun StatChipsRow(items: List<StatChip>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.chunked(2).forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                row.forEach { chip ->
                    TonalChipCard(chip, Modifier.weight(1f))
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TonalChipCard(item: StatChip, modifier: Modifier = Modifier) {
    ElevatedCard(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    item.icon,
                    null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    item.title,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    style = MaterialTheme.typography.titleSmall
                )
            }
            Text(
                item.value,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ProgressBlock(totalBytes: Long, totalCount: Int) {
    val maxBytes = 10L * 1024 * 1024 * 1024 // 10 GB ficticio  
    val progress = (totalBytes.toFloat() / maxBytes).coerceIn(0f, 1f)

    TonalElevatedCard {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "Progreso de limpieza",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = progress,
                    strokeWidth = 8.dp,
                    modifier = Modifier.fillMaxSize()
                )
                Text(
                    "${(progress * 100).roundToInt()}%",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                "$totalCount archivos eliminados",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TonalElevatedCard(content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(content = content)
    }
}

@Composable
private fun EmptyStateCard(message: String, hint: String) {
    TonalElevatedCard {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = null,
                modifier = Modifier.size(28.dp)
            )
            Text(
                message,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                hint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/* ---------- Helper Functions ---------- */

private fun formatSizeNumber(bytes: Long): String {
    val kb = 1024.0
    val mb = kb * 1024
    val gb = mb * 1024
    return when {
        bytes >= gb -> String.format(Locale.getDefault(), "%.2f", bytes / gb)
        bytes >= mb -> String.format(Locale.getDefault(), "%.2f", bytes / mb)
        bytes >= kb -> String.format(Locale.getDefault(), "%.0f", bytes / kb)
        else -> bytes.toString()
    }
}

private fun formatSizeUnit(bytes: Long): String = when {
    bytes >= 1024.0 * 1024 * 1024 -> "GB"
    bytes >= 1024.0 * 1024 -> "MB"
    bytes >= 1024.0 -> "KB"
    else -> "B"
}