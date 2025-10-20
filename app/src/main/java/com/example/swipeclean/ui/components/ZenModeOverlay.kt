package com.example.swipeclean.ui.components

import android.content.Context
import android.media.MediaPlayer
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = zenMode.isEnabled,
        enter = fadeIn(animationSpec = tween(600)),
        exit = fadeOut(animationSpec = tween(400))
    ) {
        Box(
            modifier = modifier.fillMaxSize()
        ) {
            // Fondo con gradiente muy sutil y semi-transparente
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                ZenPurple.copy(alpha = 0.15f),  // ← MUY transparente
                                ZenGray.copy(alpha = 0.10f)
                            )
                        )
                    )
            )

            // Mensaje motivacional SOLO al inicio
            var showInitialMessage by remember { mutableStateOf(true) }

            LaunchedEffect(zenMode.isEnabled) {
                if (zenMode.isEnabled) {
                    showInitialMessage = true
                    delay(5000) // Mostrar por 5 segundos
                    showInitialMessage = false
                }
            }

            // Mensaje inicial
            AnimatedVisibility(
                visible = showInitialMessage,
                enter = fadeIn(tween(800)) + scaleIn(initialScale = 0.9f),
                exit = fadeOut(tween(600)),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(32.dp)
                ) {
                    Text(
                        text = "Modo Zen",
                        style = MaterialTheme.typography.headlineLarge,
                        color = ZenLavender,
                        fontWeight = FontWeight.Light,
                        letterSpacing = 4.sp
                    )

                    Text(
                        text = zenMessages.random(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = ZenSand,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Light,
                        letterSpacing = 1.sp
                    )
                }
            }

            // Botón de salida (siempre visible, esquina superior)
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = ZenLavender
                ),
                border = BorderStroke(1.dp, ZenLavender.copy(alpha = 0.3f)),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                Text(
                    "Salir",
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.Light
                )
            }
        }
    }
}

