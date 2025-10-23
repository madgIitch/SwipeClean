package com.example.swipeclean.zen

import android.app.Application
import android.util.Log
import androidx.datastore.preferences.core.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.swipeclean.SwipeCleanApp
import com.example.swipeclean.data.userDataStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

private val KEY_ZEN_ENABLED = booleanPreferencesKey("zen_enabled")

class ZenViewModel(app: Application) : AndroidViewModel(app) {

    private val dataStore = (app as SwipeCleanApp).userDataStore
    private val KEY_ZEN_TRACK = stringPreferencesKey("zen_track")
    private val KEY_ZEN_VOLUME = floatPreferencesKey("zen_volume")
    private val KEY_ZEN_HAPTICS = stringPreferencesKey("zen_haptics")
    private val _zenMode = MutableStateFlow(ZenMode())
    private val KEY_ZEN_TIMER = intPreferencesKey("zen_timer")
    val zenMode: StateFlow<ZenMode> = _zenMode

    private val _timerProgress = MutableStateFlow(0f) // 0.0 a 1.0
    val timerProgress: StateFlow<Float> = _timerProgress.asStateFlow()

    private val _timerRemaining = MutableStateFlow(0L) // milisegundos restantes
    val timerRemaining: StateFlow<Long> = _timerRemaining.asStateFlow()

    private var timerJob: Job? = null

    init {
        viewModelScope.launch {
            dataStore.data.collect { prefs ->
                _zenMode.value = ZenMode(
                    isEnabled = prefs[KEY_ZEN_ENABLED] ?: false,
                    audioTrack = ZenAudioTrack.valueOf(
                        prefs[KEY_ZEN_TRACK] ?: "RAIN"
                    ),
                    volume = prefs[KEY_ZEN_VOLUME] ?: 1f,
                    hapticsIntensity = HapticsIntensity.valueOf(
                        prefs[KEY_ZEN_HAPTICS] ?: "MEDIUM"
                    )
                )
            }
        }
    }

    fun setAudioTrack(track: ZenAudioTrack) {
        viewModelScope.launch {
            dataStore.edit { it[KEY_ZEN_TRACK] = track.name }
        }
    }

    fun setVolume(volume: Float) {
        viewModelScope.launch {
            dataStore.edit { it[KEY_ZEN_VOLUME] = volume }
        }
    }

    fun setHapticsIntensity(intensity: HapticsIntensity) {
        viewModelScope.launch {
            dataStore.edit { it[KEY_ZEN_HAPTICS] = intensity.name }
        }
    }

    fun toggleZenMode(enabled: Boolean) {
        viewModelScope.launch {
            Log.d("ZenMode", "toggleZenMode($enabled) called")
            dataStore.edit { it[KEY_ZEN_ENABLED] = enabled }
            Log.d("ZenMode", "DataStore updated, new value: $enabled")
        }
    }
    fun setTimerDuration(duration: Int) {
        viewModelScope.launch {
            dataStore.edit {
                it[KEY_ZEN_TIMER] = duration
                if (duration > 0) {
                    it[stringPreferencesKey("zen_timer_start")] = System.currentTimeMillis().toString()
                }
            }

            // Iniciar el temporizador si duration > 0
            if (duration > 0) {
                startTimer(duration)
            } else {
                stopTimer()
            }
        }
    }

    private fun startTimer(durationMinutes: Int) {
        timerJob?.cancel()

        val totalMillis = durationMinutes * 60 * 1000L
        val startTime = System.currentTimeMillis()

        timerJob = viewModelScope.launch {
            while (true) {
                val elapsed = System.currentTimeMillis() - startTime
                val remaining = (totalMillis - elapsed).coerceAtLeast(0)

                _timerRemaining.value = remaining
                _timerProgress.value = elapsed.toFloat() / totalMillis.toFloat()

                if (remaining <= 0) {
                    onTimerFinished()
                    break
                }

                delay(1000) // Actualizar cada segundo
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
        _timerProgress.value = 0f
        _timerRemaining.value = 0L
    }

    private suspend fun onTimerFinished() {
        // Resetear temporizador
        dataStore.edit {
            it[KEY_ZEN_TIMER] = 0
        }
        _timerProgress.value = 1f

        // Aquí se enviará la notificación (ver paso 3)
    }
}