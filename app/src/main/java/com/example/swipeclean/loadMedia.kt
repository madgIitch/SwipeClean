package com.example.swipeclean

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import android.util.Log

fun Context.loadMedia(filter: MediaFilter): List<MediaItem> {
    val TAG = "SwipeClean/LoadMedia"
    val items = mutableListOf<MediaItem>()

    val projection = arrayOf(
        MediaStore.Files.FileColumns._ID,
        MediaStore.Files.FileColumns.MIME_TYPE,
        MediaStore.MediaColumns.DATE_TAKEN,     // puede venir 0
        MediaStore.MediaColumns.DATE_ADDED,     // fallback (segundos UNIX)
        MediaStore.Files.FileColumns.MEDIA_TYPE // para inferir si es vídeo si MIME está vacío
    )

    val (selection, selectionArgs) = when (filter) {
        MediaFilter.IMAGES -> (
                "${MediaStore.Files.FileColumns.MEDIA_TYPE}=?" to
                        arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString())
                )
        MediaFilter.VIDEOS -> (
                "${MediaStore.Files.FileColumns.MEDIA_TYPE}=?" to
                        arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString())
                )
        else -> (null to null)
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
        val colId      = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
        val colMime    = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
        val colTaken   = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
        val colAdded   = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
        val colType    = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)

        var count = 0
        while (cursor.moveToNext()) {
            val id   = cursor.getLong(colId)
            val mime = cursor.getString(colMime) ?: ""
            val mtyp = cursor.getInt(colType)

            // isVideo por MIME o por MEDIA_TYPE
            val isVideo = when {
                mime.startsWith("video/") -> true
                mime.startsWith("image/") -> false
                else -> (mtyp == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO)
            }

            // DATE_TAKEN (ms). Si no hay, usa DATE_ADDED (segundos) → ms
            val takenMs = cursor.getLong(colTaken)
            val addedS  = cursor.getLong(colAdded)
            val dateTaken = if (takenMs > 0) takenMs else addedS * 1000L

            val uri = ContentUris.withAppendedId(
                MediaStore.Files.getContentUri("external"),
                id
            )

            // IMPORTANTE: tu MediaItem debe tener 'id: Long'
            items += MediaItem(
                id = id,
                uri = uri,
                mimeType = mime.ifEmpty { if (isVideo) "video/*" else "image/*" },
                isVideo = isVideo,
                dateTaken = dateTaken
            )

            if (count < 10) {
                Log.d(
                    TAG,
                    "[$count] id=$id | uri=$uri | mime=${mime.ifEmpty { "-" }} | isVideo=$isVideo | dateTaken=$dateTaken"
                )
            }
            count++
        }
        Log.d(TAG, "Total items=${items.size}")
    } ?: Log.w(TAG, "Cursor nulo o sin resultados (¿falta permiso?)")

    return items
}
