package com.example.swipeclean

import android.net.Uri

data class MediaItem(
    val uri: Uri,
    val mimeType: String,
    val isVideo: Boolean,
    val dateTaken: Long
)