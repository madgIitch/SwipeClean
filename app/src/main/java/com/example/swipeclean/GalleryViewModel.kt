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
import kotlinx.coroutines.withContext

class GalleryViewModel(app: Application) : AndroidViewModel(app) {

    private val _items = MutableStateFlow<List<MediaItem>>(emptyList())
    val items: StateFlow<List<MediaItem>> = _items

    private val _index = MutableStateFlow(0)
    val index: StateFlow<Int> = _index

    private var currentFilter: MediaFilter = MediaFilter.ALL

    private val pendingTrash = mutableListOf<Uri>()
    private val stagedForReview = mutableSetOf<Uri>()

    private sealed class UserAction {
        data class Trash(val uri: Uri) : UserAction()
        data class Keep(val uri: Uri)  : UserAction()
    }
    private val history = mutableListOf<UserAction>()

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

    fun load(filter: MediaFilter = MediaFilter.ALL) {
        viewModelScope.launch {
            currentFilter = filter
            loadInternal(filter)
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
            is UserAction.Keep -> { /* no-op */ }
        }
    }

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

            // Ajuste conservador del índice para no salirnos
            _index.value = _index.value.coerceIn(0, (_items.value.lastIndex).coerceAtLeast(0))

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
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val cr = context.contentResolver
                val it = pendingTrash.iterator()
                while (it.hasNext()) {
                    val uri = it.next()
                    try { cr.delete(uri, null, null) } catch (_: Exception) {}
                    it.remove()
                }
            }
            history.clear()
            stagedForReview.clear()
            loadInternal(currentFilter)
            _index.value = _index.value.coerceIn(0, (_items.value.lastIndex).coerceAtLeast(0))
            persistAsync()
        }
    }

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
