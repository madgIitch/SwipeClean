package com.example.swipeclean.zen

import com.example.swipeclean.R

data class ZenMode(
    val isEnabled: Boolean = false,
    val audioTrack: ZenAudioTrack = ZenAudioTrack.RAIN,
    val volume: Float = 0.5f,
    val hapticsIntensity: HapticsIntensity = HapticsIntensity.MEDIUM,
    val showMotivationalMessages: Boolean = true,
    val particlesEnabled: Boolean = false
)

enum class ZenAudioTrack(val rawResId: Int, val displayName: String) {
    RAIN(R.raw.zen_rain, "Lluvia"),
    OCEAN(R.raw.zen_ocean, "Olas del Mar"),
    FOREST(R.raw.zen_forest, "Bosque"),
    BINAURAL_432(R.raw.zen_432hz, "432 Hz"),
    BINAURAL_528(R.raw.zen_528hz, "528 Hz"),
    NONE(0, "Silencio")
}

enum class HapticsIntensity {
    OFF, LOW, MEDIUM, HIGH
}