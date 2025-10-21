package com.example.swipeclean.zen

import android.app.Application
import android.util.Log
import androidx.datastore.preferences.core.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.swipeclean.SwipeCleanApp
import com.example.swipeclean.data.userDataStore
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

private val KEY_ZEN_ENABLED = booleanPreferencesKey("zen_enabled")

class ZenViewModel(app: Application) : AndroidViewModel(app) {

    private val dataStore = (app as SwipeCleanApp).userDataStore
    private val KEY_ZEN_TRACK = stringPreferencesKey("zen_track")
    private val KEY_ZEN_VOLUME = floatPreferencesKey("zen_volume")
    private val KEY_ZEN_HAPTICS = stringPreferencesKey("zen_haptics")
    private val _zenMode = MutableStateFlow(ZenMode())
    val zenMode: StateFlow<ZenMode> = _zenMode

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
}