package com.example.swipeclean.zen

import android.util.Log
import androidx.annotation.OptIn
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.RawResourceDataSource
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay

private const val TAG = "SwipeClean/ZenAudio"

@OptIn(UnstableApi::class)
@Composable
fun rememberZenAudioPlayer(
    track: ZenAudioTrack,
    volume: Float,
    lifecycle: Lifecycle,
    isEnabled: Boolean
): ExoPlayer? {
    val context = LocalContext.current
    var currentPlayer by remember { mutableStateOf<ExoPlayer?>(null) }

    // Si Zen Mode está desactivado, liberar player y retornar null
    if (!isEnabled) {
        LaunchedEffect(isEnabled) {
            currentPlayer?.let { oldPlayer ->
                Log.d(TAG, "Zen Mode disabled, releasing player")
                oldPlayer.pause()
                oldPlayer.stop()
                oldPlayer.release()
                currentPlayer = null
            }
        }
        return null
    }

    // Crear player para CUALQUIER track (incluyendo NONE/Silencio con archivo de audio)
    // NO hay verificación especial para NONE - se trata como cualquier otro track
    val player = remember(track, isEnabled) {
        currentPlayer?.let { oldPlayer ->
            Log.d(TAG, "Releasing previous player before creating new one")
            oldPlayer.pause()
            oldPlayer.stop()
            oldPlayer.release()
        }

        Log.d(TAG, "Creating player for track: ${track.displayName} (rawResId=${track.rawResId})")

        ExoPlayer.Builder(context).build().apply {
            val uri = RawResourceDataSource.buildRawResourceUri(track.rawResId)
            setMediaItem(MediaItem.fromUri(uri))
            repeatMode = Player.REPEAT_MODE_ONE
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            prepare()
        }.also { currentPlayer = it }
    }

    // Reproducir automáticamente cuando se crea un nuevo player
    LaunchedEffect(player) {
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            Log.d(TAG, "New player created while RESUMED, starting playback")
            player.play()
        }
    }

    // Aplicar fade de volumen cuando cambia
    LaunchedEffect(volume) {
        Log.d(TAG, "Volume changed to: $volume, applying fade")
        player.fadeTo(volume.coerceIn(0f, 1f))
    }

    // Gestión del lifecycle (play/pause según estado de la app)
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    Log.d(TAG, "ON_RESUME → calling play()")
                    player.play()
                }
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    Log.d(TAG, "ON_PAUSE/STOP → calling pause()")
                    player.pause()
                }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)

        onDispose {
            Log.d(TAG, "Lifecycle disposed, removing observer")
            lifecycle.removeObserver(observer)
        }
    }

    // Listener para monitorear estado del player
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                val stateName = when (state) {
                    Player.STATE_IDLE -> "IDLE"
                    Player.STATE_BUFFERING -> "BUFFERING"
                    Player.STATE_READY -> "READY"
                    Player.STATE_ENDED -> "ENDED"
                    else -> "UNKNOWN($state)"
                }
                Log.d(TAG, "Playback state: $stateName, track=${track.displayName}")
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                Log.d(TAG, "isPlaying=$isPlaying, track=${track.displayName}, volume=${player.volume}")
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "Player error for track ${track.displayName}: ${error.errorCodeName} - ${error.message}", error)
            }
        }
        player.addListener(listener)

        onDispose {
            player.removeListener(listener)
        }
    }

    // Limpieza final cuando el composable se desmonta completamente
    DisposableEffect(Unit) {
        onDispose {
            Log.d(TAG, "Final cleanup, releasing player for track: ${track.displayName}")
            player.pause()
            player.stop()
            player.release()
            currentPlayer = null
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