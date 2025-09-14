package com.example.swipeclean.ui.components

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.dp

private const val TAG_ENV = "SwipeClean/Env"

@Composable
fun DebugGestureEnv(thresholdFactor: Float = 0.35f) {
    val cfg = LocalViewConfiguration.current
    val density = LocalDensity.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    SideEffect {
        val thresholdPx = with(density) { (screenWidthDp.dp * thresholdFactor).toPx() }
        Log.i(
            TAG_ENV,
            "touchSlop=${"%.1f".format(cfg.touchSlop)}px, screenWidthDp=$screenWidthDp, thresholdPx=${"%.1f".format(thresholdPx)} (factor=$thresholdFactor)"
        )
    }
}
