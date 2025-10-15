package com.example.swipeclean.zen

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.RequiresApi

object ZenHaptics {

    fun Context.performZenHaptic(intensity: HapticsIntensity, type: HapticType) {
        if (intensity == HapticsIntensity.OFF) return

        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

        if (vibrator?.hasVibrator() != true) return

        val pattern = when (type) {
            HapticType.SWIPE_START -> when (intensity) {
                HapticsIntensity.LOW -> longArrayOf(0, 8)
                HapticsIntensity.MEDIUM -> longArrayOf(0, 12)
                HapticsIntensity.HIGH -> longArrayOf(0, 18)
                else -> return
            }
            HapticType.SWIPE_THRESHOLD -> when (intensity) {
                HapticsIntensity.LOW -> longArrayOf(0, 15, 50, 10)
                HapticsIntensity.MEDIUM -> longArrayOf(0, 20, 50, 15)
                HapticsIntensity.HIGH -> longArrayOf(0, 30, 50, 20)
                else -> return
            }
            HapticType.SWIPE_COMPLETE_KEEP -> when (intensity) {
                HapticsIntensity.LOW -> longArrayOf(0, 10, 30, 8)
                HapticsIntensity.MEDIUM -> longArrayOf(0, 15, 30, 12)
                HapticsIntensity.HIGH -> longArrayOf(0, 22, 30, 18)
                else -> return
            }
            HapticType.SWIPE_COMPLETE_DELETE -> when (intensity) {
                HapticsIntensity.LOW -> longArrayOf(0, 12, 20, 12)
                HapticsIntensity.MEDIUM -> longArrayOf(0, 18, 20, 18)
                HapticsIntensity.HIGH -> longArrayOf(0, 25, 20, 25)
                else -> return
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createWaveform(pattern, -1)
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    }
}

enum class HapticType {
    SWIPE_START,
    SWIPE_THRESHOLD,
    SWIPE_COMPLETE_KEEP,
    SWIPE_COMPLETE_DELETE
}