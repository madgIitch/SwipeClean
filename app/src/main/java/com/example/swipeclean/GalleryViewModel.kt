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

    // Cola de URIs pendientes de mover a papelera / borrar (persistida)
    private val pendingTrash = mutableListOf<Uri>()

    // Historial para Undo (no se persiste; es solo de sesión)
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
            // 1) Leer snapshot de DataStore
            val s = readUserState(getApplication())

            // 2) Restaurar filtro y pendientes
            currentFilter = when (s.filter) {
                "IMAGES" -> MediaFilter.IMAGES
                "VIDEOS" -> MediaFilter.VIDEOS
                else     -> MediaFilter.ALL
            }
            pendingTrash.clear()
            pendingTrash.addAll(s.pending.map(Uri::parse))

            // 3) Cargar medios con el filtro restaurado
            loadInternal(currentFilter)

            // 4) Intentar posicionar por currentUri, si no por índice
            val savedIndex = s.index
            val candidateIndex = s.currentUri?.let { savedUri ->
                _items.value.indexOfFirst { it.uri.toString() == savedUri }
            } ?: -1

            _index.value = when {
                candidateIndex >= 0 -> candidateIndex
                _items.value.isEmpty() -> 0
                else -> savedIndex.coerceIn(0, _items.value.lastIndex)
            }

            // 5) Persistir (por si el índice clamped ha cambiado)
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
            // tras cada nueva carga, situamos al inicio y limpiamos undo (no tocamos pending)
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
    // Helpers de navegación/estado
    // ---------------------------
    fun current(): MediaItem? = _items.value.getOrNull(_index.value)

    private fun next() {
        if (_items.value.isEmpty()) return
        // No avanzar más allá del último índice válido
        _index.value = (_index.value + 1).coerceAtMost(_items.value.lastIndex)
        persistAsync()
    }

    private fun prev() {
        if (_items.value.isEmpty()) return
        _index.value = (_index.value - 1).coerceAtLeast(0)
        persistAsync()
    }

    // ---------------------------
    // Acciones del usuario
    // ---------------------------
    fun markForTrash() {
        current()?.let { item ->
            pendingTrash += item.uri
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

        // Volvemos visualmente al anterior
        prev()

        // Revertimos el efecto de la acción deshecha
        when (val last = history.removeLast()) {
            is UserAction.Trash -> {
                // Elimina la última aparición de esa URI en pendingTrash (por seguridad)
                val idx = pendingTrash.lastIndexOf(last.uri)
                if (idx >= 0) pendingTrash.removeAt(idx)
                persistAsync()
            }
            is UserAction.Keep -> {
                // No hay efecto en pendingTrash
            }
        }
    }

    fun pendingCount(): Int = pendingTrash.size
    fun getPendingTrash(): List<Uri> = pendingTrash.toList()

    // ---------------------------
    // Confirmación de papelera/borrado
    // ---------------------------
    /** Android 11+ (API 30): mueve a Papelera con diálogo del sistema en lote */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
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
        pendingTrash.clear()
        history.clear()
        persistAsync()
    }

    /**
     * Compat para minSdk 26:
     * - API 30+: usa createTrashRequest (diálogo único por lote).
     * - API <30: no hay papelera del sistema → borrado directo best-effort.
     */
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
