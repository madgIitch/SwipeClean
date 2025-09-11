package com.example.swipeclean

import android.net.Uri
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest

@Composable
fun MediaCard(item: MediaItem?) {
    val TAG = "SwipeClean/UI"
    val ctx = LocalContext.current
    val appLoader = (ctx.applicationContext as SwipeCleanApp).imageLoader

    if (item == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Sin imagen") }
        return
    }

    // 🔥 Fuerza SIEMPRE reproducir un vídeo de prueba y sal de la función
    Log.d(TAG, ">>> FORZADO: reproduciendo URL de prueba")
    VideoPlayer(
        uri = Uri.parse("https://storage.googleapis.com/exoplayer-test-media-1/mp4/480x270/matrix.mp4"),
        modifier = Modifier.fillMaxSize(),
        autoPlay = true,
        loop = false,
        mute = false,
        showControls = true
    )

    // ─────────────────────────────────────────────────────────────────────────────
    // A partir de aquí no se ejecuta mientras esté el return anterior.
    // Cuando verifiques que el player funciona, borra el bloque de arriba y
    // deja el código original de imagen/vídeo.
    // ─────────────────────────────────────────────────────────────────────────────

    // Intenta resolver el MIME real del Uri (por si el del item está vacío o es incorrecto)
    val realMime = remember(item.uri) {
        runCatching { ctx.contentResolver.getType(item.uri) }.getOrNull()
    }

    // Decide si es vídeo usando varias fuentes
    val isVideoResolved = item.isVideo ||
            item.mimeType.startsWith("video/") ||
            (realMime?.startsWith("video/") == true)

    Log.d(
        TAG,
        "MediaCard render → uri=${item.uri}, isVideoFlag=${item.isVideo}, " +
                "mimeItem=${item.mimeType}, mimeResolver=$realMime, isVideoResolved=$isVideoResolved"
    )

    if (isVideoResolved) {
        VideoPlayer(
            uri = item.uri,
            modifier = Modifier.fillMaxSize(),
            autoPlay = true,
            loop = true,
            mute = false,
            showControls = true
        )
        return
    } else {
        val request = ImageRequest.Builder(ctx)
            .data(item.uri)
            .listener(
                onStart   = { Log.d(TAG, "Coil onStart → uri=${item.uri}") },
                onSuccess = { req, res ->
                    Log.d(TAG, "Coil onSuccess → uri=${req.data}, size=${res.drawable.intrinsicWidth}x${res.drawable.intrinsicHeight}")
                },
                onError   = { req, t ->
                    Log.e(TAG, "Coil onError → uri=${req.data}, error=${t.throwable.message}", t.throwable)
                },
                onCancel  = { Log.w(TAG, "Coil onCancel → uri=${item.uri}") }
            )
            .build()

        AsyncImage(
            model = request,
            imageLoader = appLoader,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
            placeholder = painterResource(R.drawable.placeholder),
            error = painterResource(R.drawable.ic_broken_image)
        )
    }
}
