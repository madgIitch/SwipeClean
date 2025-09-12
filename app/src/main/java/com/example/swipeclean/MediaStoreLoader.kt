package com.tuempresa.swipeclean

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.example.swipeclean.MediaItem

enum class MediaFilter { ALL, IMAGES, VIDEOS }

/**
 * Lee fotos y vídeos del MediaStore ordenados por fecha (más recientes primero).
 * Requiere permisos:
 *  - Android 13+: READ_MEDIA_IMAGES / READ_MEDIA_VIDEO
 *  - Android 10–12: READ_EXTERNAL_STORAGE
 */
fun Context.loadMedia(filter: MediaFilter = MediaFilter.ALL): List<MediaItem> {
    val items = mutableListOf<MediaItem>()
    val collection = MediaStore.Files.getContentUri("external")

    val projection = arrayOf(
        MediaStore.Files.FileColumns._ID,
        MediaStore.Files.FileColumns.MIME_TYPE,
        MediaStore.MediaColumns.DATE_TAKEN,      // puede ser 0
        MediaStore.MediaColumns.DATE_ADDED,      // segundos UNIX
        MediaStore.Files.FileColumns.MEDIA_TYPE
    )

    val selection = when (filter) {
        MediaFilter.ALL ->
            "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?)"
        MediaFilter.IMAGES ->
            "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?"
        MediaFilter.VIDEOS ->
            "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?"
    }

    val args = when (filter) {
        MediaFilter.ALL -> arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
        )
        MediaFilter.IMAGES -> arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString()
        )
        MediaFilter.VIDEOS -> arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
        )
    }

    // Orden: primero DATE_TAKEN (ms), fallback DATE_ADDED (s)
    val sortOrder =
        "${MediaStore.MediaColumns.DATE_TAKEN} DESC, " +
                "${MediaStore.MediaColumns.DATE_ADDED} DESC"

    contentResolver.query(
        collection,
        projection,
        selection,
        args,
        sortOrder
    )?.use { cursor ->
        val idCol    = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
        val mimeCol  = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
        val takenCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
        val addedCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
        val typeCol  = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)

        while (cursor.moveToNext()) {
            val id        = cursor.getLong(idCol)
            val rawMime   = cursor.getString(mimeCol) ?: ""
            val mediaType = cursor.getInt(typeCol)

            val isVideo = when (mediaType) {
                MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO -> true
                MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE -> false
                else -> rawMime.startsWith("video/")
            }

            val dateTakenMs = cursor.getLong(takenCol).let { dt ->
                if (dt != 0L) dt else cursor.getLong(addedCol) * 1000L
            }

            val uri: Uri = if (isVideo) {
                ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
            } else {
                ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
            }

            items += MediaItem(
                id = id,
                uri = uri,
                mimeType = if (rawMime.isNotEmpty()) rawMime else if (isVideo) "video/*" else "image/*",
                isVideo = isVideo,
                dateTaken = dateTakenMs
            )
        }
    }

    return items
}
