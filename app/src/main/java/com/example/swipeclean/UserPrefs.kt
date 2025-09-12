package com.example.swipeclean.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private const val DATASTORE_NAME = "swipeclean_user_state"

val Context.userDataStore by preferencesDataStore(name = DATASTORE_NAME)

// Claves
private val KEY_INDEX       = intPreferencesKey("index")
private val KEY_CURRENT_URI = stringPreferencesKey("current_uri")
private val KEY_PENDING     = stringSetPreferencesKey("pending_uris")
private val KEY_FILTER      = stringPreferencesKey("filter")

data class UserState(
    val index: Int = 0,
    val currentUri: String? = null,
    val pending: Set<String> = emptySet(),
    val filter: String = "ALL"
)

suspend fun readUserState(context: Context): UserState {
    return context.userDataStore.data.map { prefs ->
        UserState(
            index = prefs[KEY_INDEX] ?: 0,
            currentUri = prefs[KEY_CURRENT_URI],
            pending = prefs[KEY_PENDING] ?: emptySet(),
            filter = prefs[KEY_FILTER] ?: "ALL"
        )
    }.first()
}

suspend fun saveUserState(
    context: Context,
    index: Int,
    currentUri: String?,
    pending: Set<String>,
    filter: String
) {
    context.userDataStore.edit { prefs ->
        prefs[KEY_INDEX] = index
        if (currentUri != null) {
            prefs[KEY_CURRENT_URI] = currentUri
        } else {
            prefs.remove(KEY_CURRENT_URI)
        }
        prefs[KEY_PENDING] = pending
        prefs[KEY_FILTER] = filter
    }
}
