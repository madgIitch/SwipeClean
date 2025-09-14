package com.example.swipeclean

import android.content.Context
import androidx.compose.runtime.remember

fun isVideoItem(ctx: Context, item: MediaItem): Boolean {
    val realMime = runCatching { ctx.contentResolver.getType(item.uri) }.getOrNull()
    return item.isVideo ||
            item.mimeType.startsWith("video/") ||
            (realMime?.startsWith("video/") == true)
}
