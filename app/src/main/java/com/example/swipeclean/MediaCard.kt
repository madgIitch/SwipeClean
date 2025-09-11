package com.example.swipeclean

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    val ctx = LocalContext.current
    val appLoader = (ctx.applicationContext as SwipeCleanApp).imageLoader
    val TAG = "SwipeClean/UI"

    if (item == null) {
        Log.w(TAG, "MediaCard: item=null → mostrando placeholder de texto")
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Sin imagen")
        }
    } else {
        Log.d(TAG, "MediaCard: preparando request → uri=${item.uri}, mime=${item.mimeType}, isVideo=${item.isVideo}")

        val request = ImageRequest.Builder(ctx)
            .data(item.uri)
            .apply {
                if (item.isVideo) {
                    setParameter("video_frame_millis", 0L)
                    Log.d(TAG, "MediaCard: es vídeo → solicitando frame inicial")
                }
            }
            .listener(
                onStart = { Log.d(TAG, "Coil onStart → uri=${item.uri}") },
                onSuccess = { req, result ->
                    Log.d(TAG, "Coil onSuccess → uri=${req.data}, size=${result.drawable.intrinsicWidth}x${result.drawable.intrinsicHeight}")
                },
                onError = { req, throwable ->
                    Log.e(TAG, "Coil onError → uri=${req.data}, error=${throwable.throwable.message}", throwable.throwable)
                },
                onCancel = { Log.w(TAG, "Coil onCancel → uri=${item.uri}") }
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
