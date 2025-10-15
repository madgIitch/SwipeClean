package com.example.swipeclean.zen

import android.app.Application
import androidx.datastore.preferences.core.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.swipeclean.SwipeCleanApp
import com.example.swipeclean.data.userDataStore
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

private val KEY_ZEN_ENABLED = booleanPreferencesKey("zen_enabled")
private val KEY_ZEN_AUDIO = stringPreferencesKey("zen_audio")
private val KEY_ZEN_VOLUME = floatPreferencesKey("zen_volume")
private val KEY_ZEN_HAPTICS = stringPreferencesKey("zen_haptics")
private val KEY_ZEN_MESSAGES = booleanPreferencesKey("zen_messages")

class ZenViewModel(app: Application) : AndroidViewModel(app) {

    private val dataStore = (app as SwipeCleanApp).userDataStore

    private val _zenMode = MutableStateFlow(ZenMode())
    val zenMode: StateFlow<ZenMode> = _zenMode

    init {
        viewModelScope.launch {
            dataStore.data.collect { prefs ->
                _zenMode.value = ZenMode(
                    isEnabled = prefs[KEY_ZEN_ENABLED] ?: false,
                    audioTrack = ZenAudioTrack.valueOf(
                        prefs[KEY_ZEN_AUDIO] ?: ZenAudioTrack.RAIN.name
                    ),
                    volume = prefs[KEY_ZEN_VOLUME] ?: 0.5f,
                    hapticsIntensity = HapticsIntensity.valueOf(
                        prefs[KEY_ZEN_HAPTICS] ?: HapticsIntensity.MEDIUM.name
                    ),
                    showMotivationalMessages = prefs[KEY_ZEN_MESSAGES] ?: true
                )
            }
        }
    }

    fun toggleZenMode(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.edit { it[KEY_ZEN_ENABLED] = enabled }
        }
    }

    fun updateAudioTrack(track: ZenAudioTrack) {
        viewModelScope.launch {
            dataStore.edit { it[KEY_ZEN_AUDIO] = track.name }
        }
    }

    // ... otros métodos de actualización
}