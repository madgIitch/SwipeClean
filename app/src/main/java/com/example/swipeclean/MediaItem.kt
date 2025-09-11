package com.example.swipeclean

import android.net.Uri
import android.util.Log

data class MediaItem(
    val uri: Uri,
    val mimeType: String,
    val isVideo: Boolean,
    val dateTaken: Long
) {
    init {
        Log.v("SwipeClean/MediaItem", "Creado MediaItem → uri=$uri, mime=$mimeType, isVideo=$isVideo, date=$dateTaken")
    }
}
