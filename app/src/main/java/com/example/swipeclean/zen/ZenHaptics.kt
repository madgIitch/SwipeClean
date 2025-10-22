package com.example.swipeclean.zen

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * Sistema de feedback háptico para el modo Zen de SwipeClean.
 * Proporciona vibraciones diferenciadas según el tipo de gesto y la intensidad configurada.
 */
object ZenHaptics {

    private const val TAG = "ZenHaptics"

    /**
     * Ejecuta un patrón de vibración según la intensidad y tipo de háptico.
     *
     * @param intensity Nivel de intensidad (OFF, LOW, MEDIUM, HIGH)
     * @param type Tipo de evento háptico (SWIPE_START, SWIPE_THRESHOLD, etc.)
     */
    fun Context.performZenHaptic(intensity: HapticsIntensity, type: HapticType) {
        Log.d(TAG, "performZenHaptic called: intensity=$intensity, type=$type")

        // Salir si los hápticos están desactivados
        if (intensity == HapticsIntensity.OFF) {
            Log.d(TAG, "Haptics disabled (OFF), skipping")
            return
        }

        // Obtener el servicio de vibración según la versión de Android
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

        Log.d(TAG, "Vibrator obtained: ${vibrator != null}, hasVibrator=${vibrator?.hasVibrator()}")

        // Verificar que el dispositivo tenga vibrador
        if (vibrator?.hasVibrator() != true) {
            Log.w(TAG, "Device has no vibrator or vibrator unavailable")
            return
        }

        // Verificar capacidades del vibrador (solo para debug)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d(TAG, "Vibrator capabilities: hasAmplitudeControl=${vibrator.hasAmplitudeControl()}")
        }

        // Definir patrones de tiempo (en milisegundos)
        val timings = when (type) {
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

        // Definir amplitudes correspondientes (0-255, donde 0 = pausa)
        val amplitudes = when (type) {
            HapticType.SWIPE_START -> when (intensity) {
                HapticsIntensity.LOW -> intArrayOf(0, 128)
                HapticsIntensity.MEDIUM -> intArrayOf(0, 180)
                HapticsIntensity.HIGH -> intArrayOf(0, 255)
                else -> return
            }
            HapticType.SWIPE_THRESHOLD -> when (intensity) {
                HapticsIntensity.LOW -> intArrayOf(0, 128, 0, 128)
                HapticsIntensity.MEDIUM -> intArrayOf(0, 180, 0, 180)
                HapticsIntensity.HIGH -> intArrayOf(0, 255, 0, 255)
                else -> return
            }
            HapticType.SWIPE_COMPLETE_KEEP -> when (intensity) {
                HapticsIntensity.LOW -> intArrayOf(0, 128, 0, 100)
                HapticsIntensity.MEDIUM -> intArrayOf(0, 180, 0, 150)
                HapticsIntensity.HIGH -> intArrayOf(0, 255, 0, 200)
                else -> return
            }
            HapticType.SWIPE_COMPLETE_DELETE -> when (intensity) {
                HapticsIntensity.LOW -> intArrayOf(0, 128, 0, 128)
                HapticsIntensity.MEDIUM -> intArrayOf(0, 180, 0, 180)
                HapticsIntensity.HIGH -> intArrayOf(0, 255, 0, 255)
                else -> return
            }
        }

        Log.d(TAG, "Pattern: timings=${timings.contentToString()}, amplitudes=${amplitudes.contentToString()}")

        // Ejecutar vibración según la versión de Android
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // API 26+: Usar VibrationEffect con amplitudes
                val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
                vibrator.vibrate(effect)
                Log.d(TAG, "Vibration executed (API 26+) with amplitudes")
            } else {
                // API < 26: Usar método legacy (sin control de amplitud)
                @Suppress("DEPRECATION")
                vibrator.vibrate(timings, -1)
                Log.d(TAG, "Vibration executed (legacy API, no amplitude control)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing vibration: ${e.message}", e)
        }
    }
}

/**
 * Tipos de eventos hápticos en SwipeClean.
 */
enum class HapticType {
    /** Inicio de un gesto de swipe */
    SWIPE_START,

    /** Cuando el swipe alcanza el 90% del umbral de decisión */
    SWIPE_THRESHOLD,

    /** Completar acción de guardar/mantener */
    SWIPE_COMPLETE_KEEP,

    /** Completar acción de eliminar */
    SWIPE_COMPLETE_DELETE
}