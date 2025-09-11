// SwipeCleanApp.kt
package com.example.swipeclean

import android.app.Application
import coil.Coil
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import coil.request.CachePolicy
import coil.util.DebugLogger

class SwipeCleanApp : Application() {
    override fun onCreate() {
        super.onCreate()

        val loader = ImageLoader.Builder(this)
            .components { add(VideoFrameDecoder.Factory()) }
            .logger(DebugLogger())                 // <— LOGS detallados de Coil
            .allowHardware(false)                  // <— evita “Failed to create image decoder…”
            .respectCacheHeaders(false)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()

        Coil.setImageLoader(loader)
    }
}
