package com.madglitch.swipeclean

import android.app.Application
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.swipeclean.MediaItem
import com.tuempresa.swipeclean.MediaFilter
import com.tuempresa.swipeclean.loadMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GalleryViewModel(app: Application) : AndroidViewModel(app) {

    // ---------------------------
    // State (UI)
    // ---------------------------
    private val _items = MutableStateFlow<List<MediaItem>>(emptyList())
    val items: StateFlow<List<MediaItem>> = _items

    private val _index = MutableStateFlow(0)
    val index: StateFlow<Int> = _index

    // Filtro actual (persistido)
    private var currentFilter: MediaFilter = MediaFilter.ALL

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
            val s = readUserState(getApplication())

            currentFilter = when (s.filter) {
                "IMAGES" -> MediaFilter.IMAGES
                "VIDEOS" -> MediaFilter.VIDEOS
                else     -> MediaFilter.ALL
            }

            pendingTrash.clear()
            pendingTrash.addAll(s.pending.map(Uri::parse))

            loadInternal(currentFilter)

            val list = _items.value

            // Si existe la uri guardada, vuelve exactamente a esa;
            // si no, usa el índice guardado; si tampoco vale, 0.
            val candidateIndex = s.currentUri?.let { savedUri ->
                list.indexOfFirst { it.uri.toString() == savedUri }
            } ?: -1

            val restored = when {
                candidateIndex >= 0 -> candidateIndex
                list.isEmpty()      -> 0
                else                -> s.index.coerceIn(0, list.lastIndex)
            }

            _index.value = restored
            persistNow()
        }
    }

    // ---------------------------
    // Carga / Filtro
    // ---------------------------
    fun load(filter: MediaFilter = MediaFilter.ALL) {
        viewModelScope.launch {
            currentFilter = filter
            loadInternal(filter)
            // Al cambiar filtro, empieza por el primer elemento
            _index.value = 0
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
        persistNow()
    }

    private fun prev() {
        retreatIndexWrap()
        persistNow()
    }

    // ---------------------------
    // Persistencia ligera
    // ---------------------------
    private fun persistNow() {
        val ctx = getApplication<Application>()
        val safeIndex = if (_items.value.isEmpty()) 0 else
            _index.value.coerceIn(0, _items.value.lastIndex)

        // Escribir de forma síncrona en IO (por si la app muere)
        runCatching {
            runBlocking(Dispatchers.IO) {
                saveUserState(
                    context = ctx,
                    index = safeIndex,
                    currentUri = current()?.uri?.toString(),
                    pending = pendingTrash.map(Uri::toString).toSet(),
                    filter = when (currentFilter) {
                        MediaFilter.IMAGES -> "IMAGES"
                        MediaFilter.VIDEOS -> "VIDEOS"
                        MediaFilter.ALL    -> "ALL"
                    }
                )
            }
        }
    }

    private fun persistAsync() = viewModelScope.launch {
        val safeIndex = if (_items.value.isEmpty()) 0 else
            _index.value.coerceIn(0, _items.value.lastIndex)

        saveUserState(
            context = getApplication(),
            index = safeIndex,
            currentUri = current()?.uri?.toString(),
            pending = pendingTrash.map(Uri::toString).toSet(),
            filter = when (currentFilter) {
                MediaFilter.IMAGES -> "IMAGES"
                MediaFilter.VIDEOS -> "VIDEOS"
                MediaFilter.ALL    -> "ALL"
            }
        )
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
                // No hay estado que revertir salvo navegación
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

    @RequiresApi(Build.VERSION_CODES.R)
    fun confirmTrash(
        context: Context,
        onNeedsUserConfirm: (IntentSender) -> Unit
    ) {
        if (pendingTrash.isEmpty()) return
        val pi = MediaStore.createTrashRequest(
            context.contentResolver,
            pendingTrash.toList(),
            /* isTrashed = */ true
        )
        onNeedsUserConfirm(pi.intentSender)
    }

    fun confirmTrashCompat(
        context: Context,
        onNeedsUserConfirm: (IntentSender) -> Unit
    ) {
        if (pendingTrash.isEmpty()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // En 30+ delega al flujo moderno
            confirmTrash(context, onNeedsUserConfirm)
            return
        }

        // API antiguas: borrar directamente (sin diálogo del sistema)
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
}
