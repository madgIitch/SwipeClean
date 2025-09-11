// UserStateStore.kt
package com.madglitch.swipeclean

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

// Extensión DataStore a nivel de Context
val Context.userStateDataStore by preferencesDataStore(name = "user_state")

private object UserStateKeys {
    val CURRENT_INDEX = intPreferencesKey("current_index")
    val CURRENT_URI   = stringPreferencesKey("current_uri")
    val PENDING_SET   = stringSetPreferencesKey("pending_trash_uris")
    val FILTER        = stringPreferencesKey("media_filter") // "ALL" | "IMAGES" | "VIDEOS"
}

data class UserStateSnapshot(
    val index: Int,
    val currentUri: String?,
    val pending: Set<String>,
    val filter: String
)

// Guardar estado
suspend fun saveUserState(
    context: Context,
    index: Int,
    currentUri: String?,
    pending: Set<String>,
    filter: String
) {
    context.userStateDataStore.edit { p ->
        p[UserStateKeys.CURRENT_INDEX] = index
        if (currentUri != null) {
            p[UserStateKeys.CURRENT_URI] = currentUri
        } else {
            p.remove(UserStateKeys.CURRENT_URI)
        }
        p[UserStateKeys.PENDING_SET] = pending
        p[UserStateKeys.FILTER] = filter
    }
}

// Leer estado
suspend fun readUserState(context: Context): UserStateSnapshot {
    val p = context.userStateDataStore.data.first()
    return UserStateSnapshot(
        index = p[UserStateKeys.CURRENT_INDEX] ?: 0,
        currentUri = p[UserStateKeys.CURRENT_URI],
        pending = p[UserStateKeys.PENDING_SET] ?: emptySet(),
        filter = p[UserStateKeys.FILTER] ?: "ALL"
    )
}
