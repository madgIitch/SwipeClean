package com.example.swipeclean.zen

import android.content.Context
import android.os.Build
import androidx.annotation.OptIn
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.RawResourceDataSource
import androidx.media3.exoplayer.ExoPlayer
import com.example.swipeclean.R
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun rememberZenAudioPlayer(
    track: ZenAudioTrack,
    volume: Float,
    lifecycle: Lifecycle
): ExoPlayer? {
    val context = LocalContext.current
    if (track == ZenAudioTrack.NONE) return null

    val player = remember(track) {
        ExoPlayer.Builder(context)
            .build().apply {
                // Usar res/raw en lugar de assets
                val uri = RawResourceDataSource.buildRawResourceUri(track.rawResId)
                setMediaItem(MediaItem.fromUri(uri))
                repeatMode = Player.REPEAT_MODE_ONE

                // AudioFocus para pausar en llamadas/alarmas
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    true // handleAudioFocus = true
                )
                prepare()
            }
    }

    // Fade in/out de volumen
    LaunchedEffect(volume) {
        player?.fadeTo(volume.coerceIn(0f, 1f))
    }

    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> player?.play()
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> player?.pause()
                else -> Unit
            }
        }
        lifecycle.addObserver(obs)

        onDispose {
            lifecycle.removeObserver(obs)
            player?.release()
        }
    }

    return player
}

// Extensión para fade suave de volumen
suspend fun ExoPlayer.fadeTo(target: Float, durationMs: Long = 600) {
    val start = volume
    val steps = 20
    val step = (target - start) / steps
    val delayMs = durationMs / steps

    repeat(steps) {
        volume = (volume + step).coerceIn(0f, 1f)
        delay(delayMs)
    }
}