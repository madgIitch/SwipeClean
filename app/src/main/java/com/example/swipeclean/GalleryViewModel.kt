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
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.swipeclean.MediaItem
import com.example.swipeclean.StorageAnalyzer
import com.example.swipeclean.StorageMetrics
import com.example.swipeclean.data.*
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
import android.content.ContentUris
import android.util.Log
import com.example.swipeclean.MediaMetadata

// ─────────────────────────────────────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────────────────────────────────────
class GalleryViewModel(app: Application) : AndroidViewModel(app) {

    // Usar el DataStore del singleton
    private val dataStore = getApplication<Application>().applicationContext.userDataStore

    // ---------------------------
    // State (UI)
    // ---------------------------

    // Tutorial
    private val _tutorialCompleted = MutableStateFlow(
        runBlocking(Dispatchers.IO) {
            getApplication<Application>().applicationContext.userDataStore.data.first()[KEY_TUTORIAL_COMPLETED] ?: false
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
    private var currentFilter: AdvancedFilter = AdvancedFilter()
    private val _advancedFilter = MutableStateFlow(AdvancedFilter())
    val advancedFilter: StateFlow<AdvancedFilter> = _advancedFilter

    // Nueva estructura para filtros avanzados
    data class AdvancedFilter(
        val mediaType: MediaType = MediaType.ALL,
        val dateRange: DateRange? = null,
        val sizeRange: SizeRange? = null,
        val hasLocation: Boolean? = null,
        val showDuplicatesOnly: Boolean = false
    )

    enum class MediaType { ALL, IMAGES, VIDEOS }

    sealed class DateRange {
        object Last7Days : DateRange()
        object LastMonth : DateRange()
        object LastYear : DateRange()
        data class Custom(val startMillis: Long, val endMillis: Long) : DateRange()
    }

    data class SizeRange(val minBytes: Long, val maxBytes: Long)

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

    private val _cleaningProgress = MutableStateFlow(0f)
    val cleaningProgress: StateFlow<Float> = _cleaningProgress

    // En la sección de State (UI) después de cleaningProgress
    private val _hashingProgress = MutableStateFlow(0f)
    val hashingProgress: StateFlow<Float> = _hashingProgress

    private val _isHashing = MutableStateFlow(false)
    val isHashing: StateFlow<Boolean> = _isHashing

    // ---------------------------
    // Init: restaurar estado
    // ---------------------------
    init {
        viewModelScope.launch {
            val appCtx = getApplication<Application>().applicationContext

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
                    dataStore.data
                        .map { it[KEY_LAST_FILTER] ?: legacy.filter }
                        .first()
                }
            }.onFailure {
                android.util.Log.e("SwipeClean/VM", "Leyendo KEY_LAST_FILTER falló", it)
            }.getOrElse { legacy.filter }

            val mediaType = when (lastFilterName) {
                "IMAGES" -> MediaType.IMAGES
                "VIDEOS" -> MediaType.VIDEOS
                else     -> MediaType.ALL
            }
            currentFilter = AdvancedFilter(mediaType = mediaType)
            _advancedFilter.value = currentFilter
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
                        dataStore.data.first()[KEY_TUTORIAL_COMPLETED] ?: false
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
                withContext(Dispatchers.IO) { dataStore.data.first() }
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
            val savedIdStr = prefs?.get(keyIdFor(currentFilter.mediaType.name))  // ← Changed
            val savedUriForFilter = prefs?.get(keyUriFor(currentFilter.mediaType.name))  // ← Changed
            val savedIndexForFilter = prefs?.get(keyIndexFor(currentFilter.mediaType.name)) ?: legacy.index  // ← Changed

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
                    dataStore.data.first()[KEY_TUTORIAL_COMPLETED] ?: false
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
            android.util.Log.d(TAG_TUTORIAL, "markTutorialCompleted() → writing true to DataStore…")
            dataStore.edit { p ->
                p[KEY_TUTORIAL_COMPLETED] = true
            }
            _tutorialCompleted.value = true
            android.util.Log.d(TAG_TUTORIAL, "markTutorialCompleted() → StateFlow=true (persisted)")
        }
    }

    // ---------------------------
    // API para la UI: cambiar filtro
    // ---------------------------
    fun setAdvancedFilter(newFilter: AdvancedFilter) {
        if (newFilter == _advancedFilter.value) return
        persistNow()
        _advancedFilter.value = newFilter
        currentFilter = newFilter
        load(newFilter)
    }

    fun jumpTo(targetIndex: Int) {
        val max = _items.value.lastIndex
        _index.value = targetIndex.coerceIn(0, max.coerceAtLeast(0))
    }

    // ---------------------------
    // Carga / Filtro
    // ---------------------------
    fun load(filter: AdvancedFilter = AdvancedFilter()) {
        viewModelScope.launch {
            _advancedFilter.value = filter
            currentFilter = filter
            val appCtx = getApplication<Application>().applicationContext

            if (!hasGalleryPermissions(appCtx)) {
                return@launch
            }

            loadInternal(filter)
            val list = _items.value

            val prefs = dataStore.data.first()
            val filterKey = filter.mediaType.name
            val savedIdStr = prefs[keyIdFor(filterKey)]
            val savedUriForFilter = prefs[keyUriFor(filterKey)]
            val savedIndexForFilter = prefs[keyIndexFor(filterKey)] ?: 0

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
    // Función auxiliar para calcular hash
    private suspend fun calculateFileHash(uri: Uri): String? {
        return withContext(Dispatchers.IO) {
            try {
                val inputStream = getApplication<Application>().contentResolver.openInputStream(uri)
                val digest = java.security.MessageDigest.getInstance("MD5")
                val buffer = ByteArray(8192)
                var read: Int

                inputStream?.use { stream ->
                    while (stream.read(buffer).also { read = it } > 0) {
                        digest.update(buffer, 0, read)
                    }
                }

                digest.digest().joinToString("") { "%02x".format(it) }
            } catch (e: Exception) {
                android.util.Log.e("SwipeClean/Hash", "Error calculando hash", e)
                null
            }
        }
    }

    // Cargar caché de hashes desde DataStore
    private suspend fun loadHashCache(): Map<String, String> {
        return withContext(Dispatchers.IO) {
            try {
                val json = dataStore.data.first()[KEY_FILE_HASHES] ?: "{}"
                // Parsear JSON simple: "uri1":"hash1","uri2":"hash2"
                json.removeSurrounding("{", "}")
                    .split(",")
                    .mapNotNull { entry ->
                        val parts = entry.split(":")
                        if (parts.size == 2) {
                            parts[0].trim('"') to parts[1].trim('"')
                        } else null
                    }
                    .toMap()
            } catch (e: Exception) {
                android.util.Log.e("SwipeClean/HashCache", "Error cargando caché", e)
                emptyMap()
            }
        }
    }

    // Guardar caché de hashes en DataStore
    private suspend fun saveHashCache(cache: Map<String, String>) {
        withContext(Dispatchers.IO) {
            try {
                // Convertir a JSON simple
                val json = cache.entries.joinToString(",", "{", "}") { (uri, hash) ->
                    "\"$uri\":\"$hash\""
                }
                dataStore.edit { prefs ->
                    prefs[KEY_FILE_HASHES] = json
                }
                android.util.Log.d("SwipeClean/HashCache", "Caché guardada: ${cache.size} hashes")
            } catch (e: Exception) {
                android.util.Log.e("SwipeClean/HashCache", "Error guardando caché", e)
            }
        }
    }

    // Calcular hash con caché y progreso
    private suspend fun calculateFileHashWithCache(
        uri: Uri,
        cache: MutableMap<String, String>
    ): String? {
        val uriStr = uri.toString()

        // Verificar caché primero
        cache[uriStr]?.let { return it }

        // Calcular hash si no está en caché
        val hash = calculateFileHash(uri)

        // Guardar en caché si se calculó exitosamente
        if (hash != null) {
            cache[uriStr] = hash
        }

        return hash
    }

    private suspend fun loadInternal(filter: AdvancedFilter) {
        val ctx = getApplication<Application>().applicationContext

        val data = withContext(Dispatchers.IO) {
            // Cargar todos los items primero
            val allItems = mutableListOf<MediaItem>()

            // Si el filtro GPS está activo, consultar imágenes y videos por separado
            if (filter.hasLocation == true) {
                allItems.addAll(loadImagesWithLocation(ctx, filter))
            } else {
                allItems.addAll(loadAllMedia(ctx, filter))
            }

            // Aplicar filtro de duplicados si está activo
            if (filter.showDuplicatesOnly) {
                _isHashing.value = true
                _hashingProgress.value = 0f

                android.util.Log.d("SwipeClean/Duplicates", "Detectando duplicados en ${allItems.size} items...")

                // Cargar caché existente
                val hashCache = loadHashCache().toMutableMap()
                android.util.Log.d("SwipeClean/HashCache", "Caché cargada: ${hashCache.size} hashes")

                // Calcular hashes con progreso
                val itemsWithHash = allItems.mapIndexed { index, item ->
                    val hash = calculateFileHashWithCache(item.uri, hashCache)

                    // Actualizar progreso
                    val progress = (index + 1).toFloat() / allItems.size
                    _hashingProgress.value = progress

                    if (index % 10 == 0) {
                        android.util.Log.d(
                            "SwipeClean/Duplicates",
                            "Progreso: ${(progress * 100).toInt()}% ($index/${allItems.size})"
                        )
                    }

                    item to hash
                }

                // Guardar caché actualizada
                saveHashCache(hashCache)

                // Agrupar por hash
                val hashGroups = itemsWithHash.groupBy { it.second }

                // Filtrar solo los grupos con más de un elemento (duplicados)
                val duplicates = hashGroups
                    .filter { (hash, group) -> hash != null && group.size > 1 }
                    .flatMap { (_, group) -> group.map { it.first } }

                _isHashing.value = false
                _hashingProgress.value = 0f

                android.util.Log.d(
                    "SwipeClean/Duplicates",
                    "Encontrados ${duplicates.size} duplicados de ${allItems.size} items"
                )

                duplicates
            } else {
                allItems
            }
        }

        _items.value = data
    }
    private suspend fun loadImagesWithLocation(
        ctx: Context,
        filter: AdvancedFilter
    ): List<MediaItem> {
        val items = mutableListOf<MediaItem>()

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.LATITUDE,
            MediaStore.Images.Media.LONGITUDE
        )

        val selectionParts = mutableListOf<String>()
        val selectionArgs = mutableListOf<String>()

        // Filtro GPS obligatorio
        selectionParts.add("${MediaStore.Images.Media.LATITUDE} IS NOT NULL")
        selectionParts.add("${MediaStore.Images.Media.LONGITUDE} IS NOT NULL")

        // Filtro por rango de fechas
        filter.dateRange?.let { range ->
            val currentTime = System.currentTimeMillis() / 1000
            val threshold = when (range) {
                is DateRange.Last7Days -> currentTime - (7 * 24 * 60 * 60)
                is DateRange.LastMonth -> currentTime - (30 * 24 * 60 * 60)
                is DateRange.LastYear -> currentTime - (365 * 24 * 60 * 60)
                is DateRange.Custom -> range.startMillis / 1000
            }
            selectionParts.add("${MediaStore.Images.Media.DATE_ADDED} >= ?")
            selectionArgs.add(threshold.toString())
        }

        // Filtro por tamaño
        filter.sizeRange?.let { range ->
            selectionParts.add("${MediaStore.Images.Media.SIZE} >= ? AND ${MediaStore.Images.Media.SIZE} <= ?")
            selectionArgs.add(range.minBytes.toString())
            selectionArgs.add(range.maxBytes.toString())
        }

        val selection = selectionParts.joinToString(" AND ")

        ctx.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,  // ← Usar URI de imágenes
            projection,
            selection,
            selectionArgs.toTypedArray(),
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val colId = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val colMime = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
            val colAdded = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(colId)
                val mime = cursor.getString(colMime) ?: "image/*"
                val addedS = cursor.getLong(colAdded)

                val uri = android.content.ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id
                )

                items.add(MediaItem(
                    id = id,
                    uri = uri,
                    mimeType = mime,
                    isVideo = false,
                    dateTaken = addedS * 1000L
                ))
            }
        }

        return items
    }

    private suspend fun loadAllMedia(
        ctx: Context,
        filter: AdvancedFilter
    ): List<MediaItem> {
        val items = mutableListOf<MediaItem>()

        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.MEDIA_TYPE
        )

        val selectionParts = mutableListOf<String>()
        val selectionArgs = mutableListOf<String>()

        // Filtro por tipo de medio
        when (filter.mediaType) {
            MediaType.IMAGES -> {
                selectionParts.add("${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?")
                selectionArgs.add(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString())
            }
            MediaType.VIDEOS -> {
                selectionParts.add("${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?")
                selectionArgs.add(MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString())
            }
            MediaType.ALL -> {
                selectionParts.add("(${MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?)")
                selectionArgs.add(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString())
                selectionArgs.add(MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString())
            }
        }

        // Filtro por rango de fechas
        filter.dateRange?.let { range ->
            val currentTime = System.currentTimeMillis() / 1000
            val threshold = when (range) {
                is DateRange.Last7Days -> currentTime - (7 * 24 * 60 * 60)
                is DateRange.LastMonth -> currentTime - (30 * 24 * 60 * 60)
                is DateRange.LastYear -> currentTime - (365 * 24 * 60 * 60)
                is DateRange.Custom -> range.startMillis / 1000
            }
            selectionParts.add("${MediaStore.Files.FileColumns.DATE_ADDED} >= ?")
            selectionArgs.add(threshold.toString())
        }

        // Filtro por tamaño
        filter.sizeRange?.let { range ->
            selectionParts.add("${MediaStore.Files.FileColumns.SIZE} >= ? AND ${MediaStore.Files.FileColumns.SIZE} <= ?")
            selectionArgs.add(range.minBytes.toString())
            selectionArgs.add(range.maxBytes.toString())
        }

        val selection = selectionParts.joinToString(" AND ")

        ctx.contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            projection,
            selection,
            selectionArgs.toTypedArray(),
            "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"
        )?.use { cursor ->
            val colId = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val colMime = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
            val colAdded = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
            val colType = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(colId)
                val mime = cursor.getString(colMime) ?: ""
                val mtyp = cursor.getInt(colType)
                val addedS = cursor.getLong(colAdded)

                val isVideo = when {
                    mime.startsWith("video/") -> true
                    mime.startsWith("image/") -> false
                    else -> (mtyp == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO)
                }

                val uri = android.content.ContentUris.withAppendedId(
                    MediaStore.Files.getContentUri("external"),
                    id
                )

                items.add(MediaItem(
                    id = id,
                    uri = uri,
                    mimeType = mime.ifEmpty { if (isVideo) "video/*" else "image/*" },
                    isVideo = isVideo,
                    dateTaken = addedS * 1000L
                ))
            }
        }

        return items
    }


    private suspend fun calculateTotalSize(uris: List<Uri>): Long {
        return withContext(Dispatchers.IO) {
            var total = 0L
            val projection = arrayOf(OpenableColumns.SIZE)
            val cr = getApplication<Application>().contentResolver
            for (uri in uris) {
                runCatching {
                    cr.query(uri, projection, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                            if (sizeIndex >= 0) {
                                total += cursor.getLong(sizeIndex)
                            }
                        }
                    }
                }
            }
            total
        }
    }

    fun current(): MediaItem? = _items.value.getOrNull(_index.value)

    suspend fun getMediaMetadata(item: MediaItem): MediaMetadata? {
        return withContext(Dispatchers.IO) {
            try {
                val ctx = getApplication<Application>().applicationContext
                val projection = arrayOf(
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.SIZE,
                    MediaStore.MediaColumns.DATE_TAKEN,
                    MediaStore.MediaColumns.WIDTH,
                    MediaStore.MediaColumns.HEIGHT,
                    MediaStore.Images.Media.LATITUDE,
                    MediaStore.Images.Media.LONGITUDE,
                    MediaStore.Video.Media.DURATION
                )

                val uri = if (item.isVideo) {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                } else {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }

                ctx.contentResolver.query(
                    ContentUris.withAppendedId(uri, item.id),
                    projection,
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                        val sizeIdx = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                        val dateIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN)
                        val widthIdx = cursor.getColumnIndex(MediaStore.MediaColumns.WIDTH)
                        val heightIdx = cursor.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
                        val latIdx = cursor.getColumnIndex(MediaStore.Images.Media.LATITUDE)
                        val lonIdx = cursor.getColumnIndex(MediaStore.Images.Media.LONGITUDE)
                        val durIdx = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)

                        val width = if (widthIdx >= 0) cursor.getInt(widthIdx) else null
                        val height = if (heightIdx >= 0) cursor.getInt(heightIdx) else null
                        val resolution = if (width != null && height != null) "${width}x${height}" else null

                        MediaMetadata(
                            fileName = if (nameIdx >= 0) cursor.getString(nameIdx) else "Unknown",
                            fileSize = if (sizeIdx >= 0) cursor.getLong(sizeIdx) else 0L,
                            dateTaken = if (dateIdx >= 0) cursor.getLong(dateIdx) else item.dateTaken,
                            resolution = resolution,
                            mimeType = item.mimeType,
                            latitude = if (latIdx >= 0 && !cursor.isNull(latIdx)) cursor.getDouble(latIdx) else null,
                            longitude = if (lonIdx >= 0 && !cursor.isNull(lonIdx)) cursor.getDouble(lonIdx) else null,
                            duration = if (durIdx >= 0 && !cursor.isNull(durIdx)) cursor.getLong(durIdx) else null
                        )
                    } else null
                }
            } catch (e: Exception) {
                android.util.Log.e("SwipeClean/Metadata", "Error obteniendo metadatos", e)
                null
            }
        }
    }

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
        persistNow()
    }

    private fun prev() {
        retreatIndexWrap()
        persistNow()
    }

    // ---------------------------
    // Persistencia (síncrona/async)
    // ---------------------------
    fun persistNow() {
        val appCtx = getApplication<Application>().applicationContext
        if (!hasGalleryPermissions(appCtx)) return

        val safeIndex = if (_items.value.isEmpty()) 0
        else _index.value.coerceIn(0, _items.value.lastIndex)

        val currentUriStr = current()?.uri?.toString()
        val currentIdStr = current()?.id?.toString()
        val tutorialFlag = _tutorialCompleted.value

        runCatching {
            runBlocking(Dispatchers.IO) {
                dataStore.edit { p ->
                    // Por filtro actual (usar mediaType.name en lugar de name)
                    p[keyIndexFor(currentFilter.mediaType.name)] = safeIndex
                    if (currentUriStr != null) p[keyUriFor(currentFilter.mediaType.name)] =
                        currentUriStr
                    else p.remove(keyUriFor(currentFilter.mediaType.name))
                    if (currentIdStr != null) p[keyIdFor(currentFilter.mediaType.name)] =
                        currentIdStr
                    else p.remove(keyIdFor(currentFilter.mediaType.name))

                    // Último filtro usado
                    p[KEY_LAST_FILTER] = currentFilter.mediaType.name

                    // Legacy (retro-compat)
                    p[KEY_INDEX] = safeIndex
                    if (currentUriStr != null) p[KEY_CURRENT_URI] = currentUriStr
                    else p.remove(KEY_CURRENT_URI)
                    p[KEY_FILTER] = currentFilter.mediaType.name
                    p[KEY_PENDING] = pendingTrash.map(Uri::toString).toSet()

                    // Tutorial
                    p[KEY_TUTORIAL_COMPLETED] = tutorialFlag

                    // Stats
                    p[KEY_TOTAL_DELETED_BYTES] = _totalDeletedBytes.value.toString()
                    p[KEY_TOTAL_DELETED_COUNT] = _totalDeletedCount.value
                }
            }
        }.onSuccess {
            android.util.Log.d(
                "SwipeClean/VM",
                "persistNow() ✓ filter=${currentFilter.mediaType}, index=$safeIndex, tutorial=$tutorialFlag"
            )
        }.onFailure {
            android.util.Log.e("SwipeClean/VM", "persistNow() ✗ error", it)
        }
    }

    private fun persistAsync() = viewModelScope.launch(Dispatchers.IO) {
        val appCtx = getApplication<Application>().applicationContext
        if (!hasGalleryPermissions(appCtx)) return@launch

        val safeIndex = if (_items.value.isEmpty()) 0
        else _index.value.coerceIn(0, _items.value.lastIndex)

        val currentUriStr = current()?.uri?.toString()
        val currentIdStr = current()?.id?.toString()
        val tutorialFlag = _tutorialCompleted.value

        runCatching {
            dataStore.edit { p ->
                // Por filtro actual
                p[keyIndexFor(currentFilter.mediaType.name)] = safeIndex  // línea 545
                if (currentUriStr != null) p[keyUriFor(currentFilter.mediaType.name)] = currentUriStr  // línea 546
                else p.remove(keyUriFor(currentFilter.mediaType.name))  // línea 547
                if (currentIdStr != null) p[keyIdFor(currentFilter.mediaType.name)] = currentIdStr  // línea 548
                else p.remove(keyIdFor(currentFilter.mediaType.name))  // línea 549

                p[KEY_LAST_FILTER] = currentFilter.mediaType.name  // línea 552

                // Legacy (retro-compat)
                p[KEY_INDEX] = safeIndex
                if (currentUriStr != null) p[KEY_CURRENT_URI] = currentUriStr
                else p.remove(KEY_CURRENT_URI)
                p[KEY_FILTER] = currentFilter.mediaType.name  // línea 558
                p[KEY_PENDING] = pendingTrash.map(Uri::toString).toSet()

                // Tutorial
                p[KEY_TUTORIAL_COMPLETED] = tutorialFlag

                // Stats
                p[KEY_TOTAL_DELETED_BYTES] = _totalDeletedBytes.value.toString()
                p[KEY_TOTAL_DELETED_COUNT] = _totalDeletedCount.value
            }
        }.onSuccess {
            android.util.Log.d(
                "SwipeClean/VM",
                "persistAsync() ✓ filter=$currentFilter, index=$safeIndex"
            )
        }.onFailure {
            android.util.Log.e("SwipeClean/VM", "persistAsync() ✗ error", it)
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
            val deletedBytes = calculateTotalSize(confirmed)

            val set = confirmed.toSet()
            pendingTrash.removeAll(set)
            stagedForReview.removeAll(set)
            history.clear()

            loadInternal(currentFilter)

            // Verificar que las URIs realmente fueron eliminadas
            val stillExist = _items.value.filter { it.uri in set }
            if (stillExist.isNotEmpty()) {
                Log.w("GalleryViewModel", "${stillExist.size} items still exist after deletion")
                // Revertir las que no se eliminaron
                pendingTrash.addAll(stillExist.map { it.uri })
            }

            val n = _items.value.size
            _index.value = if (n == 0) 0 else ((_index.value % n) + n) % n

            // Solo actualizar estadísticas con archivos realmente eliminados
            val actuallyDeleted = confirmed.size - stillExist.size
            _totalDeletedBytes.value += deletedBytes
            _totalDeletedCount.value += actuallyDeleted

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
                true
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
            val successfullyDeleted = mutableListOf<Uri>()
            withContext(Dispatchers.IO) {
                val cr = context.contentResolver
                val it = pendingTrash.iterator()
                while (it.hasNext()) {
                    val uri = it.next()
                    runCatching {
                        val deleted = cr.delete(uri, null, null)
                        if (deleted > 0) {
                            successfullyDeleted.add(uri)
                        }
                    }
                    it.remove()
                }
            }

            // Solo actualizar estadísticas con archivos realmente eliminados
            if (successfullyDeleted.isNotEmpty()) {
                val deletedBytes = calculateTotalSize(successfullyDeleted)
                _totalDeletedBytes.value += deletedBytes
                _totalDeletedCount.value += successfullyDeleted.size
                updateCleaningProgress()
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