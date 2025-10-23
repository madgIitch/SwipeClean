package com.example.swipeclean.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Timer10
import androidx.compose.material.icons.filled.Timer3
import androidx.compose.material.icons.filled.TimerOff
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.swipeclean.R
import com.example.swipeclean.zen.HapticsIntensity
import com.example.swipeclean.zen.ZenAudioTrack
import com.example.swipeclean.zen.ZenMode


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
    "Libera espacio, libera mente.",
    "Lo simple también puede ser bello.",
    "No todo lo que guardas te pertenece todavía.",
    "Haz sitio para lo nuevo.",
    "Suelta el ruido. Quédate con la esencia.",
    "Cada swipe es una elección consciente.",
    "La calma llega cuando dejas espacio para ella.",
    "A veces limpiar también es recordar.",
    "No tienes que conservarlo todo para que haya significado.",
    "El orden exterior refleja tu orden interior.",
    "Elimina sin prisa. Observa sin juicio.",
    "Tu galería también merece respirar.",
    "Desliza con intención, no con impulso.",
    "El silencio visual también es arte.",
    "Entre borrar y conservar hay un instante de claridad.",
    "No acumules momentos, vive los que importan.",
    "Suelta. No estás perdiendo, estás aligerando.",
    "Cada foto que dejas ir abre un nuevo espacio mental.",
    "Tu paz empieza en el carrete."
)

@Composable
fun ZenModeOverlay(
    zenMode: ZenMode,
    showMessage: Boolean,
    timerProgress: Float,  // ← Nuevo parámetro
    timerRemaining: Long,  // ← Nuevo parámetro
    onDismiss: () -> Unit,
    onAudioTrackChange: (ZenAudioTrack) -> Unit,
    onHapticsIntensityChange: (HapticsIntensity) -> Unit,
    onTimerDurationChange: (Int) -> Unit,
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
                        text = currentMessage,
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Light,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Botón para cambiar intensidad de hápticos (izquierda)
            IconButton(
                onClick = {
                    val nextIntensity = when(zenMode.hapticsIntensity) {
                        HapticsIntensity.OFF -> HapticsIntensity.LOW
                        HapticsIntensity.LOW -> HapticsIntensity.MEDIUM
                        HapticsIntensity.MEDIUM -> HapticsIntensity.HIGH
                        HapticsIntensity.HIGH -> HapticsIntensity.OFF
                    }
                    onHapticsIntensityChange(nextIntensity)
                },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = when(zenMode.hapticsIntensity) {
                        HapticsIntensity.OFF -> Icons.Default.Vibration
                        else -> Icons.Default.Vibration
                    },
                    contentDescription = "Cambiar intensidad de hápticos",
                    tint = Color.White,
                    modifier = Modifier.alpha(
                        when(zenMode.hapticsIntensity) {
                            HapticsIntensity.OFF -> 0.3f
                            HapticsIntensity.LOW -> 0.5f
                            HapticsIntensity.MEDIUM -> 0.7f
                            HapticsIntensity.HIGH -> 1.0f
                        }
                    )
                )
            }

            // Botón de temporizador (centro inferior) con progreso
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            ) {
                // Indicador de progreso circular
                if (zenMode.timerDuration > 0 && timerRemaining > 0) {
                    CircularProgressIndicator(
                        progress = { timerProgress },
                        modifier = Modifier.size(56.dp),
                        color = Color.White.copy(alpha = 0.5f),
                        strokeWidth = 3.dp,
                    )
                }

                // Botón del temporizador
                IconButton(
                    onClick = {
                        val nextDuration = when (zenMode.timerDuration) {
                            0 -> 3   // ← CAMBIAR: de 5 a 3
                            3 -> 5   // ← AÑADIR: nuevo paso
                            5 -> 10  // ← CAMBIAR: de 10 a 10
                            10 -> 0  // ← CAMBIAR: de 15 a 0
                            else -> 0
                        }
                        onTimerDurationChange(nextDuration)
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            painter = when(zenMode.timerDuration) {
                                0 -> painterResource(R.drawable.ic_timeroff)
                                3 -> painterResource(R.drawable.ic_timer3)  // ← Tu nuevo icono
                                5 -> painterResource(R.drawable.ic_timer5)  // ← Tu nuevo icono
                                10 -> painterResource(R.drawable.ic_timer10) // ← Tu nuevo icono
                                else -> painterResource(R.drawable.ic_timer)
                            },
                            contentDescription = "Configurar temporizador",
                            tint = Color.White,
                            modifier = Modifier.alpha(
                                when (zenMode.timerDuration) {
                                    0 -> 0.3f
                                    3 -> 0.5f   // ← AÑADIR: opacidad para 3 min
                                    5 -> 0.7f   // ← CAMBIAR: de 0.6f a 0.7f
                                    10 -> 1.0f  // ← CAMBIAR: de 0.8f a 1.0f
                                    else -> 0.3f
                                }
                            )
                        )

                        // Mostrar tiempo restante
                        if (zenMode.timerDuration > 0 && timerRemaining > 0) {
                            val minutes = (timerRemaining / 60000).toInt()
                            val seconds = ((timerRemaining % 60000) / 1000).toInt()
                            Text(
                                text = String.format("%d:%02d", minutes, seconds),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }

            // Botón para cambiar audio (derecha)
            IconButton(
                onClick = {
                    onAudioTrackChange(zenMode.audioTrack.next())
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = if (zenMode.audioTrack == ZenAudioTrack.NONE)
                        Icons.AutoMirrored.Filled.VolumeOff
                    else
                        Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "Cambiar audio",
                    tint = Color.White
                )
            }
        }
    }
}