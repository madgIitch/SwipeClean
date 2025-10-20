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
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import android.util.Log
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
    val TAG = "SwipeClean/ZenAudio"  // ← Añadir constante de tag
    val context = LocalContext.current

    if (track == ZenAudioTrack.NONE) {
        Log.d(TAG, "Track is NONE, returning null player")  // ← Log 1
        return null
    }

    val player = remember(track) {
        Log.d(TAG, "Creating player for track: ${track.displayName} (rawResId=${track.rawResId})")  // ← Log 2

        ExoPlayer.Builder(context)
            .build().apply {
                val uri = RawResourceDataSource.buildRawResourceUri(track.rawResId)
                Log.d(TAG, "Built URI: $uri")  // ← Log 3

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
                Log.d(TAG, "Player prepared for track: ${track.displayName}")  // ← Log 4
            }
    }

    // Fade in/out de volumen
    LaunchedEffect(volume) {
        Log.d(TAG, "Volume changed to: $volume, applying fade")  // ← Log 5
        player?.fadeTo(volume.coerceIn(0f, 1f))
    }

    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    Log.d(TAG, "ON_RESUME → calling play()")  // ← Log 6
                    player?.play()
                }
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    Log.d(TAG, "ON_PAUSE/STOP → calling pause()")  // ← Log 7
                    player?.pause()
                }
                else -> Unit
            }
        }
        lifecycle.addObserver(obs)

        onDispose {
            Log.d(TAG, "Disposing player for track: ${track.displayName}")  // ← Log 8
            lifecycle.removeObserver(obs)
            player?.release()
        }
    }

    // ← Log 9: Añadir listener de estado del player
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
                Log.d(TAG, "isPlaying=$isPlaying, track=${track.displayName}, volume=${player?.volume}")
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "Player error for track ${track.displayName}: ${error.errorCodeName} - ${error.message}", error)
            }
        }
        player?.addListener(listener)

        onDispose {
            player?.removeListener(listener)
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