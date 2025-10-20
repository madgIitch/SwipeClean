package com.madglitch.swipeclean

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.swipeclean.MediaItem
import com.example.swipeclean.StorageAnalyzer
import com.example.swipeclean.StorageMetrics
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
private val KEY_INDEX = intPreferencesKey("index")
private val KEY_CURRENT_URI = stringPreferencesKey("current_uri")
private val KEY_PENDING = stringSetPreferencesKey("pending_uris")
private val KEY_FILTER = stringPreferencesKey("filter")

// Claves nuevas por filtro
private fun keyIndexFor(filter: MediaFilter) = intPreferencesKey("index_${filter.name}")
private fun keyUriFor(filter: MediaFilter) = stringPreferencesKey("uri_${filter.name}")
private fun keyIdFor(filter: MediaFilter) = stringPreferencesKey("id_${filter.name}")
private val KEY_LAST_FILTER = stringPreferencesKey("last_filter")

// Claves de estadísticas y progreso
private val KEY_TOTAL_DELETED_BYTES = stringPreferencesKey("total_deleted_bytes")
private val KEY_TOTAL_DELETED_COUNT = intPreferencesKey("total_deleted_count")
private val KEY_SESSION_STATS = stringPreferencesKey("session_stats") // reservado para futuras sesiones
private val KEY_TUTORIAL_COMPLETED = booleanPreferencesKey("tutorial_completed")

private data class UserState(
    val index: Int = 0,
    val currentUri: String? = null,
    val pending: Set<String> = emptySet(),
    val filter: String = "ALL"
)

private suspend fun readUserState(context: Application): UserState {
    return context.userDataStore.data.map { p ->
        UserState(
            index = p[KEY_INDEX] ?: 0,
            currentUri = p[KEY_CURRENT_URI],
            pending = p[KEY_PENDING] ?: emptySet(),
            filter = p[KEY_FILTER] ?: "ALL"
        )
    }.first()
}

// ─────────────────────────────────────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────────────────────────────────────
class GalleryViewModel(app: Application) : AndroidViewModel(app) {

    // ---------------------------
    // State (UI)
    // ---------------------------

    // Tutorial
    private val _tutorialCompleted = MutableStateFlow(
        runBlocking(Dispatchers.IO) {
            getApplication<Application>().userDataStore.data.first()[KEY_TUTORIAL_COMPLETED] ?: false
        }
    )
    val tutorialCompleted: StateFlow<Boolean> = _tutorialCompleted
    private val TAG_TUTORIAL = "SwipeClean/Tutorial"
    private val _bootRestored = MutableStateFlow(false)
    val bootRestored: StateFlow<Boolean> = _bootRestored


    // Items y navegación
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
        data class Keep(val uri: Uri) : UserAction()
    }
    private val history = mutableListOf<UserAction>()

    // Estadísticas de limpieza
    private val _totalDeletedBytes = MutableStateFlow(0L)
    val totalDeletedBytes: StateFlow<Long> = _totalDeletedBytes

    private val _totalDeletedCount = MutableStateFlow(0)
    val totalDeletedCount: StateFlow<Int> = _totalDeletedCount

    // Métricas de almacenamiento y progreso
    private val _storageMetrics = MutableStateFlow<StorageMetrics?>(null)
    val storageMetrics: StateFlow<StorageMetrics?> = _storageMetrics

    private val _cleaningProgress = MutableStateFlow(0f) // 0.0 a 1.0
    val cleaningProgress: StateFlow<Float> = _cleaningProgress

    // ---------------------------
    // Init: restaurar estado
    // ---------------------------
    init {
        viewModelScope.launch {
            val appCtx = getApplication<Application>()

            // ─────────────────────────────────────────────────────────────
            // 1) Carga legacy + último filtro usado (en IO)
            // ─────────────────────────────────────────────────────────────
            val legacy = runCatching {
                withContext(Dispatchers.IO) { readUserState(appCtx) }
            }.onFailure {
                android.util.Log.e("SwipeClean/VM", "readUserState() falló", it)
            }.getOrElse { UserState() }

            val lastFilterName = runCatching {
                withContext(Dispatchers.IO) {
                    appCtx.userDataStore.data
                        .map { it[KEY_LAST_FILTER] ?: legacy.filter }
                        .first()
                }
            }.onFailure {
                android.util.Log.e("SwipeClean/VM", "Leyendo KEY_LAST_FILTER falló", it)
            }.getOrElse { legacy.filter }

            currentFilter = when (lastFilterName) {
                "IMAGES" -> MediaFilter.IMAGES
                "VIDEOS" -> MediaFilter.VIDEOS
                else     -> MediaFilter.ALL
            }
            _filter.value = currentFilter
            android.util.Log.d("SwipeClean/VM", "init → lastFilter=$lastFilterName → currentFilter=$currentFilter")

            // Restaurar cola pendiente (legacy)
            pendingTrash.clear()
            pendingTrash.addAll(legacy.pending.map(Uri::parse))
            android.util.Log.d("SwipeClean/VM", "init → legacy.pending=${legacy.pending.size}")

            // ─────────────────────────────────────────────────────────────
            // 2) Permisos: si faltan, restaura índice legacy y sólo el flag de tutorial
            // ─────────────────────────────────────────────────────────────
            if (!hasGalleryPermissions(appCtx)) {
                _index.value = legacy.index
                val dsTutorialNoPerm = runCatching {
                    withContext(Dispatchers.IO) {
                        appCtx.userDataStore.data.first()[KEY_TUTORIAL_COMPLETED] ?: false
                    }
                }.getOrElse { false }
                _tutorialCompleted.value = dsTutorialNoPerm

                android.util.Log.w("SwipeClean/VM", "init → SIN permisos. index(legacy)=${legacy.index}")
                android.util.Log.d("SwipeClean/Tutorial", "init(no-perm) → tutorialCompleted(DataStore)=$dsTutorialNoPerm")
                return@launch
            }

            // ─────────────────────────────────────────────────────────────
            // 3) Carga de elementos para el filtro actual (en IO)
            // ─────────────────────────────────────────────────────────────
            runCatching {
                withContext(Dispatchers.IO) { loadInternal(currentFilter) }
            }.onFailure {
                android.util.Log.e("SwipeClean/MediaLoad", "loadInternal($currentFilter) falló", it)
            }
            android.util.Log.d("SwipeClean/VM", "init → items.size=${_items.value.size} para filter=$currentFilter")

            // ─────────────────────────────────────────────────────────────
            // 4) Preferencias (stats, tutorial, claves por filtro)
            // ─────────────────────────────────────────────────────────────
            val prefs = runCatching {
                withContext(Dispatchers.IO) { appCtx.userDataStore.data.first() }
            }.onFailure {
                android.util.Log.e("SwipeClean/VM", "DataStore.first() falló", it)
            }.getOrNull()

            // Stats
            _totalDeletedBytes.value = prefs?.get(KEY_TOTAL_DELETED_BYTES)?.toLongOrNull() ?: 0L
            _totalDeletedCount.value = prefs?.get(KEY_TOTAL_DELETED_COUNT) ?: 0
            android.util.Log.d(
                "SwipeClean/Stats",
                "init → deletedBytes=${_totalDeletedBytes.value}, deletedCount=${_totalDeletedCount.value}"
            )

            // Tutorial (restaurar pronto para evitar relanzos)
            val tutorialFromDs = prefs?.get(KEY_TUTORIAL_COMPLETED) ?: false
            _tutorialCompleted.value = tutorialFromDs
            android.util.Log.d("SwipeClean/Tutorial", "init → tutorialCompleted(DataStore)=$tutorialFromDs")

            // Métricas de almacenamiento (async en IO)
            viewModelScope.launch(Dispatchers.IO) {
                runCatching { StorageAnalyzer.calculateCleaningTarget(appCtx) }
                    .onSuccess {
                        _storageMetrics.value = it
                        updateCleaningProgress()
                        android.util.Log.d("SwipeClean/Stats", "init → storage targetBytes=${it.targetBytes}")
                    }
                    .onFailure { android.util.Log.e("SwipeClean/Stats", "calculateCleaningTarget() falló", it) }
            }

            // ─────────────────────────────────────────────────────────────
            // 5) Restauración por-filtro: ID → URI → índice seguro
            // ─────────────────────────────────────────────────────────────
            val savedIdStr          = prefs?.get(keyIdFor(currentFilter))
            val savedUriForFilter   = prefs?.get(keyUriFor(currentFilter))
            val savedIndexForFilter = prefs?.get(keyIndexFor(currentFilter)) ?: legacy.index

            val list = _items.value

            val candidateById = savedIdStr?.toLongOrNull()
                ?.let { id -> list.indexOfFirst { it.id == id } }
                ?: -1

            val candidateByUri = if (candidateById < 0 && savedUriForFilter != null) {
                list.indexOfFirst { it.uri.toString() == savedUriForFilter }
            } else -1

            val restored = when {
                candidateById  >= 0 -> candidateById
                candidateByUri >= 0 -> candidateByUri
                list.isEmpty()       -> 0
                else                 -> savedIndexForFilter.coerceIn(0, list.lastIndex)
            }

            _index.value = restored
            android.util.Log.d(
                "SwipeClean/VM",
                "init → restoredIndex=$restored (byId=$candidateById, byUri=$candidateByUri, savedIndex=$savedIndexForFilter, items=${list.size})"
            )

            // ─────────────────────────────────────────────────────────────
            // 6) Checkpoint inmediato (persistir estado actual)
            // ─────────────────────────────────────────────────────────────
            runCatching { persistNow() }
                .onFailure { android.util.Log.e("SwipeClean/VM", "persistNow() falló en init", it) }

            // Diagnóstico: comparar DS vs StateFlow tras persistir
            viewModelScope.launch(Dispatchers.IO) {
                val ds = runCatching {
                    appCtx.userDataStore.data.first()[KEY_TUTORIAL_COMPLETED] ?: false
                }.getOrElse { false }
                val sf = _tutorialCompleted.value
                android.util.Log.d("SwipeClean/Tutorial", "init(post-persist) → DataStore=$ds, StateFlow=$sf")
            }
        }
    }
    // ---------------------------
    // Tutorial
    // ---------------------------
    fun markTutorialCompleted() {
        viewModelScope.launch(Dispatchers.IO) {
            val appCtx = getApplication<Application>()
            android.util.Log.d(TAG_TUTORIAL, "markTutorialCompleted() → writing true to DataStore…")
            appCtx.userDataStore.edit { p ->
                p[KEY_TUTORIAL_COMPLETED] = true
            }
            _tutorialCompleted.value = true
            android.util.Log.d(TAG_TUTORIAL, "markTutorialCompleted() → StateFlow=true (persisted)")
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

    fun jumpTo(targetIndex: Int) {
        val max = _items.value.lastIndex
        _index.value = targetIndex.coerceIn(0, max.coceateAtLeast0())
    }

    // Helper para evitar warnings (inline)
    private fun Int.coceateAtLeast0() = this.coerceAtLeast(0)

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
                list.isEmpty() -> 0
                else -> savedIndexForFilter.coerceIn(0, list.lastIndex)
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

    private suspend fun calculateTotalSize(uris: List<Uri>): Long {
        return withContext(Dispatchers.IO) {
            var total = 0L
            val projection = arrayOf(OpenableColumns.SIZE)
            val cr = getApplication<Application>().contentResolver
            for (uri in uris) {
                try {
                    cr.query(uri, projection, null, null, null)?.use { c ->
                        val idx = c.getColumnIndex(OpenableColumns.SIZE)
                        if (idx != -1 && c.moveToFirst() && !c.isNull(idx)) {
                            total += c.getLong(idx)
                        }
                    }
                } catch (_: Exception) {
                    // ignorar errores por URIs no accesibles
                }
            }
            total
        }
    }

    fun current(): MediaItem? = _items.value.getOrNull(_index.value)

    // ---------------------------
    // Navegación (wrap-around)
    // ---------------------------
    private fun advanceIndexWrap() {
        val n = _items.value.size
        if (n == 0) {
            _index.value = 0
            return
        }
        _index.value = (_index.value + 1) % n
    }

    private fun retreatIndexWrap() {
        val n = _items.value.size
        if (n == 0) {
            _index.value = 0
            return
        }
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
        if (!hasGalleryPermissions(appCtx)) {
            android.util.Log.w("SwipeClean/VM", "persistNow() → sin permisos, no se persiste")
            return
        }

        val safeIndex = if (_items.value.isEmpty()) 0
        else _index.value.coerceIn(0, _items.value.lastIndex)

        val currentUriStr = current()?.uri?.toString()
        val currentIdStr  = current()?.id?.toString()
        val tutorialFlag  = _tutorialCompleted.value

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
                    p[KEY_FILTER]  = currentFilter.name
                    p[KEY_PENDING] = pendingTrash.map(Uri::toString).toSet()

                    // 👇 persistimos también el estado del tutorial
                    p[KEY_TUTORIAL_COMPLETED] = tutorialFlag
                }
            }
        }.onSuccess {
            android.util.Log.d(
                "SwipeClean/VM",
                "persistNow() ✓ filter=$currentFilter, index=$safeIndex, uri=${currentUriStr ?: "null"}, id=${currentIdStr ?: "null"}, tutorial=$tutorialFlag, pending=${pendingTrash.size}"
            )
        }.onFailure {
            android.util.Log.e("SwipeClean/VM", "persistNow() ✗ error guardando DataStore", it)
        }
    }

    private fun persistAsync() = viewModelScope.launch(Dispatchers.IO) {
        val appCtx = getApplication<Application>()
        if (!hasGalleryPermissions(appCtx)) {
            android.util.Log.w("SwipeClean/VM", "persistAsync() → sin permisos, no se persiste")
            return@launch
        }

        val safeIndex = if (_items.value.isEmpty()) 0
        else _index.value.coerceIn(0, _items.value.lastIndex)

        val currentUriStr = current()?.uri?.toString()
        val currentIdStr  = current()?.id?.toString()
        val tutorialFlag  = _tutorialCompleted.value

        runCatching {
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
                p[KEY_FILTER]  = currentFilter.name
                p[KEY_PENDING] = pendingTrash.map(Uri::toString).toSet()

                // 👇 persistimos también el estado del tutorial
                p[KEY_TUTORIAL_COMPLETED] = tutorialFlag
            }
        }.onSuccess {
            android.util.Log.d(
                "SwipeClean/VM",
                "persistAsync() ✓ filter=$currentFilter, index=$safeIndex, uri=${currentUriStr ?: "null"}, id=${currentIdStr ?: "null"}, tutorial=$tutorialFlag, pending=${pendingTrash.size}"
            )
        }.onFailure {
            android.util.Log.e("SwipeClean/VM", "persistAsync() ✗ error guardando DataStore", it)
        }
    }


    // ---------------------------
    // Progreso de limpieza
    // ---------------------------
    private fun updateCleaningProgress() {
        val metrics = _storageMetrics.value ?: return
        val deletedBytes = _totalDeletedBytes.value
        val targetBytes = metrics.targetBytes

        if (targetBytes > 0) {
            _cleaningProgress.value = (deletedBytes.toFloat() / targetBytes).coerceIn(0f, 1f)
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
            // Calcular tamaño de archivos confirmados
            val deletedBytes = calculateTotalSize(confirmed)

            val set = confirmed.toSet()
            pendingTrash.removeAll(set)
            stagedForReview.removeAll(set)
            history.clear()

            loadInternal(currentFilter)

            val n = _items.value.size
            _index.value = if (n == 0) 0 else ((_index.value % n) + n) % n

            // Actualizar estadísticas
            _totalDeletedBytes.value += deletedBytes
            _totalDeletedCount.value += confirmed.size

            // Persistir estadísticas
            val appCtx = getApplication<Application>()
            appCtx.userDataStore.edit { p ->
                p[KEY_TOTAL_DELETED_BYTES] = _totalDeletedBytes.value.toString()
                p[KEY_TOTAL_DELETED_COUNT] = _totalDeletedCount.value
            }

            // Actualizar progreso
            updateCleaningProgress()

            persistAsync()
        }
    }

    fun clearStage() = stagedForReview.clear()

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
            confirmTrashCompat(context, onNeedsUserConfirm)
        }
    }

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
