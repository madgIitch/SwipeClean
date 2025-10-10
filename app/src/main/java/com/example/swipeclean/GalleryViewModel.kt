package com.madglitch.swipeclean

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.swipeclean.MediaItem
import com.tuempresa.swipeclean.MediaFilter
import com.tuempresa.swipeclean.loadMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

// ─────────────────────────────────────────────────────────────────────────────
// DataStore (estado persistente)
// ─────────────────────────────────────────────────────────────────────────────
private val Application.userDataStore by preferencesDataStore(name = "swipeclean_user_state")

// Claves legacy (retro-compat)
private val KEY_INDEX       = intPreferencesKey("index")
private val KEY_CURRENT_URI = stringPreferencesKey("current_uri")
private val KEY_PENDING     = stringSetPreferencesKey("pending_uris")
private val KEY_FILTER      = stringPreferencesKey("filter")

// Claves nuevas por filtro
private fun keyIndexFor(filter: MediaFilter) = intPreferencesKey("index_${filter.name}")
private fun keyUriFor(filter: MediaFilter)   = stringPreferencesKey("uri_${filter.name}")
private fun keyIdFor(filter: MediaFilter)    = stringPreferencesKey("id_${filter.name}") // opcional (si MediaItem.id existe)
private val KEY_LAST_FILTER = stringPreferencesKey("last_filter")

private data class UserState(
    val index: Int = 0,
    val currentUri: String? = null,
    val pending: Set<String> = emptySet(),
    val filter: String = "ALL"
)

private suspend fun readUserState(context: Application): UserState {
    return context.userDataStore.data.map { p ->
        UserState(
            index      = p[KEY_INDEX] ?: 0,
            currentUri = p[KEY_CURRENT_URI],
            pending    = p[KEY_PENDING] ?: emptySet(),
            filter     = p[KEY_FILTER] ?: "ALL"
        )
    }.first()
}

private suspend fun saveUserState(
    context: Application,
    index: Int,
    currentUri: String?,
    pending: Set<String>,
    filter: String
) {
    context.userDataStore.edit { p ->
        p[KEY_INDEX] = index
        if (currentUri != null) p[KEY_CURRENT_URI] = currentUri else p.remove(KEY_CURRENT_URI)
        p[KEY_PENDING] = pending
        p[KEY_FILTER]  = filter
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────────────────────────────────────
class GalleryViewModel(app: Application) : AndroidViewModel(app) {

    // ---------------------------
    // State (UI)
    // ---------------------------
    private val _items = MutableStateFlow<List<MediaItem>>(emptyList())
    val items: StateFlow<List<MediaItem>> = _items

    private val _index = MutableStateFlow(0)
    val index: StateFlow<Int> = _index

    // Filtro actual (persistido) + expuesto a la UI
    private var currentFilter: MediaFilter = MediaFilter.ALL
    private val _filter = MutableStateFlow(MediaFilter.ALL)
    val filter: StateFlow<MediaFilter> = _filter

    // Colas/sets para acciones
    private val pendingTrash = mutableListOf<Uri>()
    private val stagedForReview = mutableSetOf<Uri>()

    // Historial para undo
    private sealed class UserAction {
        data class Trash(val uri: Uri) : UserAction()
        data class Keep(val uri: Uri)  : UserAction()
    }
    private val history = mutableListOf<UserAction>()

    // ---------------------------
    // Init: restaurar estado
    // ---------------------------
    init {
        viewModelScope.launch {
            val appCtx = getApplication<Application>()
            val legacy = readUserState(appCtx)

            // Último filtro usado (nuevo) con fallback a legacy
            val lastFilterName = appCtx.userDataStore.data
                .map { it[KEY_LAST_FILTER] ?: legacy.filter }
                .first()

            currentFilter = when (lastFilterName) {
                "IMAGES" -> MediaFilter.IMAGES
                "VIDEOS" -> MediaFilter.VIDEOS
                else     -> MediaFilter.ALL
            }
            _filter.value = currentFilter

            pendingTrash.clear()
            pendingTrash.addAll(legacy.pending.map(Uri::parse))

            if (!hasGalleryPermissions(appCtx)) {
                // Sin permisos: restaura índice legacy y sal (evita sobreescribir buen estado).
                _index.value = legacy.index
                return@launch
            }

            // Carga de elementos
            loadInternal(currentFilter)

            // Restauración por-filtro (ID → URI → índice)
            val prefs = appCtx.userDataStore.data.first()
            val savedIdStr = prefs[keyIdFor(currentFilter)]
            val savedUriForFilter = prefs[keyUriFor(currentFilter)]
            val savedIndexForFilter = prefs[keyIndexFor(currentFilter)] ?: legacy.index

            val list = _items.value

            val candidateById = savedIdStr?.toLongOrNull()?.let { id ->
                list.indexOfFirst { it.id == id } // requiere MediaItem.id: Long?; si no existe, siempre -1
            } ?: -1

            val candidateByUri = if (candidateById < 0 && savedUriForFilter != null) {
                list.indexOfFirst { it.uri.toString() == savedUriForFilter }
            } else -1

            val restored = when {
                candidateById >= 0 -> candidateById
                candidateByUri >= 0 -> candidateByUri
                list.isEmpty()      -> 0
                else                -> savedIndexForFilter.coerceIn(0, list.lastIndex)
            }

            _index.value = restored
            // Guardamos checkpoint inmediato
            persistNow()
        }
    }

    // ---------------------------
    // API para la UI: cambiar filtro
    // ---------------------------
    fun setFilter(newFilter: MediaFilter) {
        if (newFilter == _filter.value) return
        // Checkpoint del filtro actual antes de movernos
        persistNow()
        _filter.value = newFilter
        load(newFilter)
    }

    // Ajusta los nombres si en tu VM se llaman distinto (_items/_index)
    fun jumpTo(targetIndex: Int) {
        val max = _items.value.lastIndex
        _index.value = targetIndex.coerceIn(0, max.coerceAtLeast(0))
    }


    // ---------------------------
    // Carga / Filtro
    // ---------------------------
    fun load(filter: MediaFilter = MediaFilter.ALL) {
        viewModelScope.launch {
            _filter.value = filter
            currentFilter = filter
            val appCtx = getApplication<Application>()

            if (!hasGalleryPermissions(appCtx)) {
                return@launch
            }

            loadInternal(filter)
            val list = _items.value

            // Restaurar por-filtro (ID → URI → índice)
            val prefs = appCtx.userDataStore.data.first()
            val savedIdStr = prefs[keyIdFor(filter)]
            val savedUriForFilter = prefs[keyUriFor(filter)]
            val savedIndexForFilter = prefs[keyIndexFor(filter)] ?: 0

            val candidateById = savedIdStr?.toLongOrNull()?.let { id ->
                list.indexOfFirst { it.id == id }
            } ?: -1

            val candidateByUri = if (candidateById < 0 && savedUriForFilter != null) {
                list.indexOfFirst { it.uri.toString() == savedUriForFilter }
            } else -1

            val restored = when {
                candidateById >= 0 -> candidateById
                candidateByUri >= 0 -> candidateByUri
                list.isEmpty()      -> 0
                else                -> savedIndexForFilter.coerceIn(0, list.lastIndex)
            }

            _index.value = restored
            history.clear()
            persistAsync()
        }
    }

    private suspend fun loadInternal(filter: MediaFilter) {
        val ctx = getApplication<Application>().applicationContext
        val data = withContext(Dispatchers.IO) { ctx.loadMedia(filter) }
        _items.value = data
    }

    fun current(): MediaItem? = _items.value.getOrNull(_index.value)

    // ---------------------------
    // Navegación (wrap-around)
    // ---------------------------
    private fun advanceIndexWrap() {
        val n = _items.value.size
        if (n == 0) { _index.value = 0; return }
        _index.value = (_index.value + 1) % n
    }

    private fun retreatIndexWrap() {
        val n = _items.value.size
        if (n == 0) { _index.value = 0; return }
        _index.value = (_index.value - 1 + n) % n
    }

    private fun next() {
        advanceIndexWrap()
        persistNow() // checkpoint inmediato
    }

    private fun prev() {
        retreatIndexWrap()
        persistNow() // checkpoint inmediato
    }

    // ---------------------------
    // Persistencia (síncrona/async)
    // ---------------------------
    fun persistNow() {
        val appCtx = getApplication<Application>()
        if (!hasGalleryPermissions(appCtx)) return

        val safeIndex = if (_items.value.isEmpty()) 0
        else _index.value.coerceIn(0, _items.value.lastIndex)

        val currentUriStr = current()?.uri?.toString()
        val currentIdStr  = current()?.id?.toString() // si MediaItem.id existe

        // Síncrono en IO para resistir cierre brusco
        runCatching {
            runBlocking(Dispatchers.IO) {
                appCtx.userDataStore.edit { p ->
                    // Por filtro actual
                    p[keyIndexFor(currentFilter)] = safeIndex
                    if (currentUriStr != null) p[keyUriFor(currentFilter)] = currentUriStr else p.remove(keyUriFor(currentFilter))
                    if (currentIdStr  != null) p[keyIdFor(currentFilter)]  = currentIdStr  else p.remove(keyIdFor(currentFilter))

                    // Último filtro usado
                    p[KEY_LAST_FILTER] = currentFilter.name

                    // Legacy (retro-compat)
                    p[KEY_INDEX] = safeIndex
                    if (currentUriStr != null) p[KEY_CURRENT_URI] = currentUriStr else p.remove(KEY_CURRENT_URI)
                    p[KEY_FILTER] = currentFilter.name
                    p[KEY_PENDING] = pendingTrash.map(Uri::toString).toSet()
                }
            }
        }
    }

    private fun persistAsync() = viewModelScope.launch(Dispatchers.IO) {
        val appCtx = getApplication<Application>()
        if (!hasGalleryPermissions(appCtx)) return@launch

        val safeIndex = if (_items.value.isEmpty()) 0
        else _index.value.coerceIn(0, _items.value.lastIndex)

        val currentUriStr = current()?.uri?.toString()
        val currentIdStr  = current()?.id?.toString()

        appCtx.userDataStore.edit { p ->
            // Por filtro actual
            p[keyIndexFor(currentFilter)] = safeIndex
            if (currentUriStr != null) p[keyUriFor(currentFilter)] = currentUriStr else p.remove(keyUriFor(currentFilter))
            if (currentIdStr  != null) p[keyIdFor(currentFilter)]  = currentIdStr  else p.remove(keyIdFor(currentFilter))

            // Último filtro usado
            p[KEY_LAST_FILTER] = currentFilter.name

            // Legacy (retro-compat)
            p[KEY_INDEX] = safeIndex
            if (currentUriStr != null) p[KEY_CURRENT_URI] = currentUriStr else p.remove(KEY_CURRENT_URI)
            p[KEY_FILTER] = currentFilter.name
            p[KEY_PENDING] = pendingTrash.map(Uri::toString).toSet()
        }
    }

    // ---------------------------
    // Acciones usuario
    // ---------------------------
    fun markForTrash() {
        current()?.let { item ->
            if (!pendingTrash.contains(item.uri)) {
                pendingTrash += item.uri
            }
            history += UserAction.Trash(item.uri)
            next()
        }
    }

    fun keep() {
        current()?.let { item ->
            history += UserAction.Keep(item.uri)
            next()
        }
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun undo() {
        if (history.isEmpty()) return
        // Retrocede visualmente primero
        prev()

        when (val last = history.removeLast()) {
            is UserAction.Trash -> {
                val idx = pendingTrash.lastIndexOf(last.uri)
                if (idx >= 0) pendingTrash.removeAt(idx)
                persistAsync()
            }
            is UserAction.Keep -> {
                // Nada que revertir salvo navegación
            }
        }
    }

    // ---------------------------
    // Review / Trash
    // ---------------------------
    fun pendingCount(): Int = pendingTrash.size
    fun getPendingTrash(): List<Uri> = pendingTrash.toList()

    fun getPendingForReview(): ArrayList<Uri> =
        ArrayList(pendingTrash.filterNot { stagedForReview.contains(it) })

    fun applyStagedSelection(uris: List<Uri>) {
        stagedForReview.addAll(uris)
    }

    fun confirmDeletionConfirmed(confirmed: List<Uri>) {
        if (confirmed.isEmpty()) return

        viewModelScope.launch {
            val set = confirmed.toSet()
            pendingTrash.removeAll(set)
            stagedForReview.removeAll(set)
            history.clear()

            loadInternal(currentFilter)

            // Ajuste del índice con wrap (por si cambió el tamaño)
            val n = _items.value.size
            _index.value = if (n == 0) 0 else ((_index.value % n) + n) % n

            persistAsync()
        }
    }

    fun clearStage() = stagedForReview.clear()

    /**
     * Android 11+ (API 30): manda a Papelera con diálogo del sistema.
     * No vacía la cola: espera a que la actividad informe del resultado.
     */
    fun confirmTrash(
        context: Context,
        onNeedsUserConfirm: (IntentSender) -> Unit
    ) {
        if (pendingTrash.isEmpty()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val pi = MediaStore.createTrashRequest(
                context.contentResolver,
                pendingTrash.toList(),
                /* isTrashed = */ true
            )
            onNeedsUserConfirm(pi.intentSender)
        } else {
            // Compat < 30 → borrar directamente
            confirmTrashCompat(context, onNeedsUserConfirm)
        }
    }

    /**
     * Compat para API < 30: borra directamente sin diálogo.
     */
    fun confirmTrashCompat(
        context: Context,
        @Suppress("UNUSED_PARAMETER") onNeedsUserConfirm: (IntentSender) -> Unit
    ) {
        if (pendingTrash.isEmpty()) return

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val cr = context.contentResolver
                val it = pendingTrash.iterator()
                while (it.hasNext()) {
                    val uri = it.next()
                    runCatching { cr.delete(uri, null, null) }
                    it.remove()
                }
            }
            history.clear()
            stagedForReview.clear()
            loadInternal(currentFilter)

            val n = _items.value.size
            _index.value = if (n == 0) 0 else ((_index.value % n) + n) % n

            persistAsync()
        }
    }

    // ---------------------------
    // Permisos
    // ---------------------------
    private fun hasGalleryPermissions(ctx: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= 33) {
            // Con que tenga uno de los dos nos vale según filtro; OR mantiene tu lógica actual
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_MEDIA_IMAGES) ==
                    PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_MEDIA_VIDEO) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                    PackageManager.PERMISSION_GRANTED
        }
    }
}
