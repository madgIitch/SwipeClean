package com.example.swipeclean

import android.net.Uri
import android.util.Log
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecRenderer
import androidx.media3.ui.PlayerView
import androidx.media3.common.MediaItem as M3Item

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    uri: Uri,
    modifier: Modifier = Modifier,
    autoPlay: Boolean = true,
    loop: Boolean = true,
    mute: Boolean = true,
    showControls: Boolean = true,
) {
    val TAG = "SwipeClean/Video"
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val exoPlayer = remember(uri) {
        val renderers = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true) // intenta SW si el HW no soporta el perfil
        ExoPlayer.Builder(context, renderers).build().apply {
            setMediaItem(M3Item.fromUri(uri)) // ¡no fuerces MIME!
            repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            volume = if (mute) 0f else 1f
            prepare()
            playWhenReady = autoPlay
            Log.d(TAG, "Init → uri=$uri autoPlay=$autoPlay loop=$loop mute=$mute")
        }
    }

    LaunchedEffect(exoPlayer, autoPlay) {
        if (autoPlay) exoPlayer.play()
    }

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
                    for (i in 0 until g.length) {
                        val f = g.getTrackFormat(i)
                        if (f.sampleMimeType?.startsWith("video/") == true) {
                            Log.d(TAG, "track → ${f.width}x${f.height} mime=${f.sampleMimeType} codec=${f.codecs}")
                        }
                    }
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "playerError: ${error.errorCodeName} - ${error.message}", error)
                (error.cause as? MediaCodecRenderer.DecoderInitializationException)?.let { c ->
                    Log.e(TAG, "Decoder init failed → mime=${c.mimeType} secure=${c.secureDecoderRequired} diag=${c.diagnosticInfo}")
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

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
            exoPlayer.release()
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
                this.player = exoPlayer
            }
        },
        update = { view ->
            if (view.player !== exoPlayer) view.player = exoPlayer
            view.useController = showControls
        }
    )
}
