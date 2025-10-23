package com.example.swipeclean.zen

import com.example.swipeclean.R

data class ZenMode(
    val isEnabled: Boolean = false,
    val audioTrack: ZenAudioTrack = ZenAudioTrack.RAIN,
    val volume: Float = 1f,
    val hapticsIntensity: HapticsIntensity = HapticsIntensity.MEDIUM,
    val showMotivationalMessages: Boolean = true,
    val particlesEnabled: Boolean = true,
    val timerDuration: Int = 0, // 0 = sin temporizador, 5, 10, 15 minutos
    val timerStartTime: Long = 0L
)

enum class ZenAudioTrack(val rawResId: Int, val displayName: String) {
    RAIN(R.raw.zen_rain, "Lluvia"),
    OCEAN(R.raw.zen_ocean, "Olas del Mar"),
    FOREST(R.raw.zen_forest, "Bosque"),
    NONE(R.raw.zen_silence, "Silencio");  // ← Cambiar de 0 a R.raw.zen_silence

    fun next(): ZenAudioTrack = when(this) {
        RAIN -> OCEAN
        OCEAN -> FOREST
        FOREST -> NONE
        NONE -> RAIN
    }
}


enum class HapticsIntensity {
    OFF, LOW, MEDIUM, HIGH
}