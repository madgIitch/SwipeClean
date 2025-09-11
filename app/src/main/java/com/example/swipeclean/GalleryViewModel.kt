package com.example.swipeclean

import android.app.Application
import android.content.Context
import android.content.IntentSender
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tuempresa.swipeclean.MediaFilter
import com.tuempresa.swipeclean.loadMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class GalleryViewModel(app: Application): AndroidViewModel(app) {

    private val _items = MutableStateFlow<List<MediaItem>>(emptyList())
    val items: StateFlow<List<MediaItem>> = _items

    private val _index = MutableStateFlow(0)
    val index: StateFlow<Int> = _index

    enum class Action { KEEP, TRASH, DELETE }

    fun load(filter: MediaFilter = MediaFilter.ALL) {
        viewModelScope.launch(Dispatchers.IO) {
            val data = getApplication<Application>().applicationContext.loadMedia(filter)
            _items.value = data
            _index.value = 0
        }
    }

    fun current(): MediaItem? = _items.value.getOrNull(_index.value)

    fun keep() { next() }

    fun trash(context: Context, onNeedsUserConfirm: (IntentSender) -> Unit) {
        current()?.let { item ->
            requestTrashOrDelete(context, listOf(item), trash = true, onNeedsUserConfirm)
            next()
        }
    }

    fun delete(context: Context, onNeedsUserConfirm: (IntentSender) -> Unit) {
        current()?.let { item ->
            requestTrashOrDelete(context, listOf(item), trash = false, onNeedsUserConfirm)
            next()
        }
    }

    fun undo() {
        val prev = (_index.value - 1).coerceAtLeast(0)
        _index.value = prev
    }

    private fun next() {
        _index.value = (_index.value + 1).coerceAtMost(_items.value.size)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun requestTrashOrDelete(
        context: Context,
        items: List<MediaItem>,
        trash: Boolean,
        onNeedsUserConfirm: (IntentSender) -> Unit
    ) {
        val uris = items.map { it.uri }
        val pi = if (trash) {
            // Papelera (API 30+). Si no está disponible en el dispositivo, puedes
            // ocultar esta opción en UI para versiones antiguas.
            MediaStore.createTrashRequest(context.contentResolver, uris, true)
        } else {
            MediaStore.createDeleteRequest(context.contentResolver, uris)
        }
        onNeedsUserConfirm(pi.intentSender)
    }
}
