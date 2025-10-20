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

    private val _zenMode = MutableStateFlow(ZenMode())
    val zenMode: StateFlow<ZenMode> = _zenMode

    init {
        viewModelScope.launch {
            dataStore.data.collect { prefs ->
                _zenMode.value = ZenMode(
                    isEnabled = prefs[KEY_ZEN_ENABLED] ?: false
                )
            }
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