package com.example.swipeclean

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import android.util.Log
import com.example.swipeclean.MediaFilter

fun Context.loadMedia(filter: MediaFilter): List<MediaItem> {
    val TAG = "SwipeClean/LoadMedia"
    val items = mutableListOf<MediaItem>()

    val projection = arrayOf(
        MediaStore.Files.FileColumns._ID,
        MediaStore.Files.FileColumns.MIME_TYPE,
        MediaStore.Files.FileColumns.DATE_TAKEN
    )

    val (selection, selectionArgs) = when (filter) {
        MediaFilter.IMAGES -> {
            "${MediaStore.Files.FileColumns.MEDIA_TYPE}=?" to
                    arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString())
        }
        MediaFilter.VIDEOS -> {
            "${MediaStore.Files.FileColumns.MEDIA_TYPE}=?" to
                    arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString())
        }
        else -> null to null
    }

    val sortOrder = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"

    Log.d(TAG, "Query → filter=$filter, selection=$selection, args=${selectionArgs?.toList()}")

    contentResolver.query(
        MediaStore.Files.getContentUri("external"),
        projection,
        selection,
        selectionArgs,
        sortOrder
    )?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
        val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
        val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_TAKEN)

        var count = 0
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idCol)
            val mime = cursor.getString(mimeCol) ?: ""
            val dateTaken = cursor.getLong(dateCol)

            val uri = ContentUris.withAppendedId(
                MediaStore.Files.getContentUri("external"),
                id
            )

            val isVideo = mime.startsWith("video/")

            items += MediaItem(uri, mime, isVideo, dateTaken)

            // Solo loggear las primeras 10 para no petar el Logcat
            if (count < 10) {
                Log.d(
                    TAG,
                    "[$count] id=$id | uri=$uri | mime=$mime | dateTaken=$dateTaken | isVideo=$isVideo"
                )
            }
            count++
        }
        Log.d(TAG, "Total items=${items.size}")
    } ?: Log.w(TAG, "Cursor nulo o sin resultados (¿falta permiso?)")

    return items
}
