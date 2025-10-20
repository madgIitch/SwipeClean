package com.example.swipeclean

import android.net.Uri
import android.util.Log
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.annotation.OptIn
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecRenderer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView

/**
 * Componente de reproducción de video usando ExoPlayer (Media3).
 *
 * Características:
 * - Soporte para content:// URIs de MediaStore
 * - Fallback automático a software decoder si hardware falla
 * - Manejo robusto de errores de codec con recuperación automática
 * - Integración completa con ciclo de vida de Android
 * - Logging detallado para debugging
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    uri: Uri,
    modifier: Modifier = Modifier,
    autoPlay: Boolean = true,
    loop: Boolean = true,
    mute: Boolean = false,
    showControls: Boolean = true,
) {
    val TAG = "SwipeClean/Video"
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // ─────────────────────────────────────────────────────────────────────────
    // ExoPlayer Initialization
    // ─────────────────────────────────────────────────────────────────────────

    val exoPlayer = remember(uri) {
        // Configuración de renderers con fallback a software decoder
        val renderers = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

        // DataSource que soporta content:// y http://
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("SwipeClean/1.0")
            .setAllowCrossProtocolRedirects(true)
        val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        // Construcción del player
        ExoPlayer.Builder(context, renderers)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(uri))
                repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                volume = if (mute) 0f else 1f
                prepare()
                playWhenReady = autoPlay
                Log.d(TAG, "Init player → uri=$uri autoPlay=$autoPlay loop=$loop mute=$mute")
            }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Asegurar reproducción después de prepare
    // ─────────────────────────────────────────────────────────────────────────

    LaunchedEffect(exoPlayer, autoPlay) {
        if (autoPlay) {
            exoPlayer.play()
            Log.d(TAG, "LaunchedEffect: play() called")
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Player Event Listener
    // ─────────────────────────────────────────────────────────────────────────

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                val stateStr = when (state) {
                    Player.STATE_IDLE -> "IDLE"
                    Player.STATE_BUFFERING -> "BUFFERING"
                    Player.STATE_READY -> "READY"
                    Player.STATE_ENDED -> "ENDED"
                    else -> "UNKNOWN($state)"
                }
                Log.d(TAG, "Playback state: $stateStr (uri=$uri)")
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                Log.d(TAG, "Is playing: $isPlaying (uri=$uri)")
            }

            override fun onTracksChanged(tracks: Tracks) {
                tracks.groups.forEach { group ->
                    repeat(group.length) { index ->
                        val format = group.getTrackFormat(index)
                        if (format.sampleMimeType?.startsWith("video/") == true) {
                            Log.d(
                                TAG,
                                "Video track: ${format.width}x${format.height} " +
                                        "mime=${format.sampleMimeType} codec=${format.codecs}"
                            )
                        }
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "Player error: ${error.errorCodeName} - ${error.message}", error)

                // Manejo específico de errores de codec
                if (error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
                    error.errorCode == PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK) {

                    Log.w(TAG, "Codec error detected, attempting recovery...")

                    // Intentar recuperación: limpiar y recargar el media item
                    try {
                        exoPlayer.stop()
                        exoPlayer.clearMediaItems()
                        exoPlayer.setMediaItem(MediaItem.fromUri(uri))
                        exoPlayer.prepare()
                        if (autoPlay) {
                            exoPlayer.play()
                        }
                        Log.i(TAG, "Recovery attempt completed")
                    } catch (e: Exception) {
                        Log.e(TAG, "Recovery failed", e)
                    }
                }

                // Logging detallado de errores de inicialización de decoder
                (error.cause as? MediaCodecRenderer.DecoderInitializationException)?.let { decoderError ->
                    Log.e(
                        TAG,
                        "Decoder initialization failed → " +
                                "mime=${decoderError.mimeType} " +
                                "secure=${decoderError.secureDecoderRequired} " +
                                "diagnostics=${decoderError.diagnosticInfo}"
                    )
                }
            }
        }

        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            Log.d(TAG, "Player listener removed")
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle Management
    // ─────────────────────────────────────────────────────────────────────────

    DisposableEffect(lifecycleOwner, exoPlayer) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> if (autoPlay) exoPlayer.play()
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> exoPlayer.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(obs)
            // Pausar antes de liberar para evitar timeout
            exoPlayer.pause()
            exoPlayer.stop()
            try {
                exoPlayer.release()
                Log.d(TAG, "Player released successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing player", e)
            }
        }
    }
    // ─────────────────────────────────────────────────────────────────────────
    // PlayerView UI
    // ─────────────────────────────────────────────────────────────────────────

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = showControls
                controllerShowTimeoutMs = if (showControls) 3000 else 0
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                keepScreenOn = true
                layoutParams = android.widget.FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                player = exoPlayer
                Log.d(TAG, "PlayerView created")
            }
        },
        update = { view ->
            // Actualizar el player si cambió
            if (view.player !== exoPlayer) {
                view.player = exoPlayer
                Log.d(TAG, "PlayerView updated with new player instance")
            }
            view.useController = showControls
        }
    )
}