package com.example.swipeclean.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// ÚNICA definición de DataStore en toda la app
private const val DATASTORE_NAME = "swipeclean_user_state"
val Context.userDataStore by preferencesDataStore(name = DATASTORE_NAME)

// Claves para GalleryViewModel
val KEY_INDEX = intPreferencesKey("index")
val KEY_CURRENT_URI = stringPreferencesKey("current_uri")
val KEY_PENDING = stringSetPreferencesKey("pending_uris")
val KEY_FILTER = stringPreferencesKey("filter")
val KEY_LAST_FILTER = stringPreferencesKey("last_filter")

// Funciones helper para claves por filtro
fun keyIndexFor(filterName: String) = intPreferencesKey("index_$filterName")
fun keyUriFor(filterName: String) = stringPreferencesKey("uri_$filterName")
fun keyIdFor(filterName: String) = stringPreferencesKey("id_$filterName")

// Claves para ZenViewModel
val KEY_ZEN_ENABLED = booleanPreferencesKey("zen_enabled")

// En data/UserPreferences.kt o donde definas las claves
val KEY_FILE_HASHES = stringPreferencesKey("file_hashes")  // JSON map de URI -> hash

// Claves para Tutorial y Estadísticas ← AÑADIR ESTAS LÍNEAS
val KEY_TUTORIAL_COMPLETED = booleanPreferencesKey("tutorial_completed")
val KEY_TOTAL_DELETED_BYTES = stringPreferencesKey("total_deleted_bytes")
val KEY_TOTAL_DELETED_COUNT = intPreferencesKey("total_deleted_count")

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