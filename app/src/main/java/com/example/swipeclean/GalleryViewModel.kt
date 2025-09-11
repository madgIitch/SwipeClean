// GalleryViewModel.kt
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

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

    // URIs marcadas por deslizamiento para enviar a papelera/borrar (persistidas)
    private val pendingTrash = mutableListOf<Uri>()

    // URIs que el usuario ya “apartó” en ReviewActivity (no deben volver a salir en la siguiente revisión)
    private val stagedForReview = mutableSetOf<Uri>()

    // Historial para Undo (solo sesión)
    private sealed class UserAction {
        data class Trash(val uri: Uri) : UserAction()
        data class Keep(val uri: Uri)  : UserAction()
    }
    private val history = mutableListOf<UserAction>()

    // ---------------------------
    // Init: restaurar estado guardado y cargar media
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

            val savedIndex = s.index
            val candidateIndex = s.currentUri?.let { savedUri ->
                _items.value.indexOfFirst { it.uri.toString() == savedUri }
            } ?: -1

            _index.value = when {
                candidateIndex >= 0    -> candidateIndex
                _items.value.isEmpty() -> 0
                else                   -> savedIndex.coerceIn(0, _items.value.lastIndex)
            }

            persistAsync()
        }
    }

    // ---------------------------
    // Carga de Media (pública)
    // ---------------------------
    fun load(filter: MediaFilter = MediaFilter.ALL) {
        viewModelScope.launch {
            currentFilter = filter
            loadInternal(filter)
            _index.value = 0
            history.clear()
            persistAsync()
        }
    }

    // Carga real en IO
    private suspend fun loadInternal(filter: MediaFilter) {
        val ctx = getApplication<Application>().applicationContext
        val data = with(Dispatchers.IO) { ctx.loadMedia(filter) }
        _items.value = data
    }

    // ---------------------------
    // Helpers navegación/estado
    // ---------------------------
    fun current(): MediaItem? = _items.value.getOrNull(_index.value)

    private fun next() {
        val size = _items.value.size
        if (size == 0) return
        _index.value = (_index.value + 1) % size
        persistAsync()
    }

    private fun prev() {
        val size = _items.value.size
        if (size == 0) return
        _index.value = (_index.value - 1 + size) % size
        persistAsync()
    }

    // ---------------------------
    // Acciones del usuario (card)
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

    fun undo() {
        if (history.isEmpty()) return
        prev()
        when (val last = history.removeLast()) {
            is UserAction.Trash -> {
                val idx = pendingTrash.lastIndexOf(last.uri)
                if (idx >= 0) pendingTrash.removeAt(idx)
                persistAsync()
            }
            is UserAction.Keep -> { /* no-op */ }
        }
    }

    fun pendingCount(): Int = pendingTrash.size
    fun getPendingTrash(): List<Uri> = pendingTrash.toList()

    // ---------------------------
    // API para ReviewActivity (staging)
    // ---------------------------

    /** Lista que debe ver ReviewActivity ahora: pendientes – ya “staged”. */
    fun getPendingForReview(): ArrayList<Uri> =
        ArrayList(pendingTrash.filterNot { stagedForReview.contains(it) })

    /** Guardar selección hecha en ReviewActivity cuando el usuario vuelve sin borrar. */
    fun applyStagedSelection(uris: List<Uri>) {
        stagedForReview.addAll(uris)
    }

    /** Tras confirmación de borrado: sacar de pending y limpiar staged para esas URIs. */
    fun confirmDeletionConfirmed(confirmed: List<Uri>) {
        if (confirmed.isEmpty()) return
        val set = confirmed.toSet()
        pendingTrash.removeAll(set)
        stagedForReview.removeAll(set)
        history.clear()
        persistAsync()
    }

    /** Si quisieras permitir “Reset selección” en Ajustes/Review. */
    fun clearStage() = stagedForReview.clear()

    // ---------------------------
    // Confirmación compat (si algún día la haces desde aquí)
    // ---------------------------
    @RequiresApi(Build.VERSION_CODES.R)
    fun confirmTrash(
        context: Context,
        onNeedsUserConfirm: (IntentSender) -> Unit
    ) {
        if (pendingTrash.isEmpty()) return
        val pi = MediaStore.createTrashRequest(context.contentResolver, pendingTrash.toList(), true)
        onNeedsUserConfirm(pi.intentSender)
    }

    fun confirmTrashCompat(
        context: Context,
        onNeedsUserConfirm: (IntentSender) -> Unit
    ) {
        if (pendingTrash.isEmpty()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            confirmTrash(context, onNeedsUserConfirm)
            return
        }
        val cr = context.contentResolver
        val it = pendingTrash.iterator()
        while (it.hasNext()) {
            val uri = it.next()
            try { cr.delete(uri, null, null) } catch (_: Exception) {}
            it.remove()
        }
        history.clear()
        stagedForReview.clear()
        persistAsync()
    }

    // ---------------------------
    // Persistencia con DataStore
    // ---------------------------
    private fun persistAsync() = viewModelScope.launch {
        saveUserState(
            context = getApplication(),
            index = _index.value.coerceIn(0, (_items.value.lastIndex).coerceAtLeast(0)),
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
