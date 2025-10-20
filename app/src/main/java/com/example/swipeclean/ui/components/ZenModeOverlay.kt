package com.example.swipeclean.ui.components

import android.content.Context
import android.media.MediaPlayer
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.swipeclean.R
import com.example.swipeclean.zen.ZenMode
import kotlinx.coroutines.delay

// Paleta de colores Zen
val ZenPurple = Color(0xFF6B5B95)
val ZenGray = Color(0xFF8B8680)
val ZenLavender = Color(0xFFB8A9C9)
val ZenSage = Color(0xFF9CAF88)
val ZenSand = Color(0xFFD4C5B9)

// Mensajes motivacionales
private val zenMessages = listOf(
    "Respira. Observa. Suelta la foto cuando estés listo.",
    "Deja ir lo que ya no sirve.",
    "Guarda solo lo que te inspira.",
    "Cada decisión es un paso hacia la claridad.",
    "Confía en tu intuición.",
    "Menos es más.",
    "Libera espacio, libera mente."
)

@Composable
fun ZenModeOverlay(
    zenMode: ZenMode,
    showMessage: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Seleccionar un mensaje aleatorio al activar ZenMode
    val currentMessage by remember(zenMode.isEnabled) {
        mutableStateOf(zenMessages.random())
    }

    AnimatedVisibility(
        visible = zenMode.isEnabled,
        enter = fadeIn(animationSpec = tween(600)),
        exit = fadeOut(animationSpec = tween(400))
    ) {
        Box(modifier = modifier.fillMaxSize()) {
            // Fondo semi-transparente del overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                ZenPurple.copy(alpha = 0.15f),
                                ZenGray.copy(alpha = 0.10f)
                            )
                        )
                    )
            )

            // Mensaje motivacional usando la lista
            AnimatedVisibility(
                visible = showMessage,
                enter = fadeIn(animationSpec = tween(400)) + scaleIn(initialScale = 0.8f),
                exit = fadeOut(animationSpec = tween(400)) + scaleOut(targetScale = 0.8f),
                modifier = Modifier.align(Alignment.Center)
            ) {

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Text(
                            text = currentMessage,  // ← USAR EL MENSAJE DE LA LISTA
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Light,
                            textAlign = TextAlign.Center
                        )
                    }
            }
        }
    }
}