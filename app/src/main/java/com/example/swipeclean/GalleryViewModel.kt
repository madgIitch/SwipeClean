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

    private val _items = MutableStateFlow<List<MediaItem>>(emptyList())
    val items: StateFlow<List<MediaItem>> = _items

    private val _index = MutableStateFlow(0)
    val index: StateFlow<Int> = _index

    // Cola de URIs a enviar a papelera / borrar
    private val pendingTrash = mutableListOf<Uri>()

    fun load(filter: MediaFilter = MediaFilter.ALL) {
        viewModelScope.launch(Dispatchers.IO) {
            val data = getApplication<Application>().applicationContext.loadMedia(filter)
            _items.value = data
            _index.value = 0
        }
    }

    fun current(): MediaItem? = _items.value.getOrNull(_index.value)

    private fun next() {
        _index.value = (_index.value + 1).coerceAtMost(_items.value.size)
    }

    fun markForTrash() {
        current()?.let { item ->
            pendingTrash += item.uri
            next()
        }
    }

    fun keep() {
        next()
    }

    fun undo() {
        _index.value = (_index.value - 1).coerceAtLeast(0)
    }

    fun pendingCount(): Int = pendingTrash.size

    /** Android 11+ (API 30): mueve a Papelera con diálogo del sistema en lote */
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
        pendingTrash.clear()
    }
    // GalleryViewModel.kt
    fun getPendingTrash(): List<Uri> = pendingTrash.toList()

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

        // API 26–29: intentar borrar directamente.
        val cr = context.contentResolver
        val it = pendingTrash.iterator()
        while (it.hasNext()) {
            val uri = it.next()
            try {
                cr.delete(uri, null, null)
            } catch (_: Exception) {
                // ignora errores individuales
            }
            it.remove()
        }
    }
}
