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

    val exoPlayer = remember(uri) {
        // Renderers con fallback si el HW decoder no soporta el perfil
        val renderers = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER) // ✅ NUEVO

        // DataSource que soporta content:// (y http:// si alguna vez lo usas)
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("SwipeClean/1.0")
            .setAllowCrossProtocolRedirects(true)
        val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        ExoPlayer.Builder(context, renderers)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(uri))
                repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                volume = if (mute) 0f else 1f
                prepare()
                playWhenReady = autoPlay
                Log.d(TAG, "Init local → uri=$uri autoPlay=$autoPlay loop=$loop mute=$mute")
            }
    }

    // Asegura play() tras prepare
    LaunchedEffect(exoPlayer, autoPlay) {
        if (autoPlay) exoPlayer.play()
    }

    // Logs útiles
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                val s = when (state) {
                    Player.STATE_IDLE -> "IDLE"
                    Player.STATE_BUFFERING -> "BUFFERING"
                    Player.STATE_READY -> "READY"
                    Player.STATE_ENDED -> "ENDED"
                    else -> "UNKNOWN($state)"
                }
                Log.d(TAG, "state=$s uri=$uri")
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                Log.d(TAG, "isPlaying=$isPlaying uri=$uri")
            }
            override fun onTracksChanged(tracks: Tracks) {
                tracks.groups.forEach { g ->
                    repeat(g.length) { i ->
                        val f = g.getTrackFormat(i)
                        if (f.sampleMimeType?.startsWith("video/") == true) {
                            Log.d(TAG, "track → ${f.width}x${f.height} mime=${f.sampleMimeType} codec=${f.codecs}")
                        }
                    }
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "playerError: ${error.errorCodeName} - ${error.message}", error)

                // ✅ NUEVO: Manejo específico para errores de codec
                if (error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
                    error.errorCode == PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK) {

                    Log.w(TAG, "Codec error detected, attempting recovery...")

                    // Liberar y recrear el player
                    try {
                        exoPlayer.stop()
                        exoPlayer.clearMediaItems()
                        exoPlayer.setMediaItem(MediaItem.fromUri(uri))
                        exoPlayer.prepare()
                        if (autoPlay) exoPlayer.play()
                    } catch (e: Exception) {
                        Log.e(TAG, "Recovery failed", e)
                    }
                }

                (error.cause as? MediaCodecRenderer.DecoderInitializationException)?.let { c ->
                    Log.e(TAG, "Decoder init failed → mime=${c.mimeType} secure=${c.secureDecoderRequired} diag=${c.diagnosticInfo}")
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    // Ciclo de vida
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> {
                    // ✅ Verificar que el player no esté liberado
                    if (autoPlay && exoPlayer.playbackState != Player.STATE_IDLE) {
                        try {
                            exoPlayer.play()
                        } catch (e: IllegalStateException) {
                            Log.e(TAG, "Cannot play - player may be released", e)
                        }
                    }
                }
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    try {
                        exoPlayer.pause()
                    } catch (e: IllegalStateException) {
                        Log.e(TAG, "Cannot pause - player may be released", e)
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(obs)
            // ✅ Asegurar que el player se libere de forma segura
            try {
                exoPlayer.stop()
                exoPlayer.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing player", e)
            }
        }
    }


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
            }
        },
        update = { view ->
            if (view.player !== exoPlayer) view.player = exoPlayer
            view.useController = showControls
        }
    )
}
