package com.example.swipeclean

import android.content.ContentUris
import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * Métricas de almacenamiento y limpieza calculadas dinámicamente.
 */
data class StorageMetrics(
    val freeBytes: Long,
    val totalBytes: Long,
    val freeRatio: Float,
    val estimatedMediaBytes: Long,
    val targetBytes: Long,
    val heavyBoost: Long,
    val topHeavyFiles: List<MediaItem>
)

/**
 * Analizador de almacenamiento que calcula metas de limpieza dinámicas
 * basadas en la presión de almacenamiento del dispositivo.
 */
object StorageAnalyzer {

    /**
     * Calcula la meta de limpieza dinámica basada en presión de almacenamiento.
     */
    suspend fun calculateCleaningTarget(context: Context): StorageMetrics = withContext(Dispatchers.IO) {
        // 1. Telemetría de almacenamiento (StatFs)
        val statFs = StatFs(Environment.getDataDirectory().path)
        val totalBytes = statFs.totalBytes
        val freeBytes = statFs.availableBytes
        val freeRatio = freeBytes.toFloat() / totalBytes.toFloat()

        // 2. Estimación de tamaño total de media (muestreo)
        val estimatedMediaBytes = estimateMediaSize(context)

        // 3. Cálculo de target según presión de almacenamiento
        val baseTarget = when {
            freeRatio < 0.08f -> {
                max(2_000_000_000L, (estimatedMediaBytes * 0.15).toLong())
                    .coerceIn(1_000_000_000L, 5_000_000_000L)
            }
            freeRatio < 0.15f -> {
                max(1_000_000_000L, (estimatedMediaBytes * 0.10).toLong())
                    .coerceIn(500_000_000L, 3_000_000_000L)
            }
            else -> {
                max(500_000_000L, (estimatedMediaBytes * 0.05).toLong())
                    .coerceIn(300_000_000L, 2_000_000_000L)
            }
        }

        // 4. "Pesados primero" - Boost opcional
        val (heavyBoost, topHeavy) = calculateHeavyBoost(context)
        val finalTarget = if (heavyBoost >= baseTarget * 0.6) {
            heavyBoost
        } else {
            baseTarget
        }

        StorageMetrics(
            freeBytes = freeBytes,
            totalBytes = totalBytes,
            freeRatio = freeRatio,
            estimatedMediaBytes = estimatedMediaBytes,
            targetBytes = finalTarget,
            heavyBoost = heavyBoost,
            topHeavyFiles = topHeavy
        )
    }

    /**
     * Calcula el "heavy boost" sumando los archivos más grandes.
     * Retorna el tamaño total y la lista de MediaItems.
     */
    private suspend fun calculateHeavyBoost(
        context: Context,
        topK: Int = 200
    ): Pair<Long, List<MediaItem>> = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_ADDED
        )

        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?)"
        val selectionArgs = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
        )

        val sortOrder = "${MediaStore.Files.FileColumns.SIZE} DESC"

        var totalHeavySize = 0L
        val heavyFiles = mutableListOf<MediaItem>()

        context.contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val colId = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val colSize = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val colType = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            val colMime = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
            val colDateTaken = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
            val colDateAdded = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)

            var count = 0
            while (cursor.moveToNext() && count < topK) {
                val id = cursor.getLong(colId)
                val size = cursor.getLong(colSize)
                val type = cursor.getInt(colType)
                val mime = cursor.getString(colMime) ?: "unknown"

                val takenMs = cursor.getLong(colDateTaken)
                val addedS = cursor.getLong(colDateAdded)
                val dateTaken = if (takenMs > 0) takenMs else addedS * 1000L

                val contentUri = if (type == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) {
                    ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        id
                    )
                } else {
                    ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id
                    )
                }

                heavyFiles.add(
                    MediaItem(
                        id = id,
                        uri = contentUri,
                        mimeType = mime,
                        isVideo = type == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO,
                        dateTaken = dateTaken
                    )
                )

                totalHeavySize += size
                count++
            }
        }

        return@withContext Pair(totalHeavySize, heavyFiles)
    }
}

/**
 * Estima el tamaño total de media mediante muestreo.
 */
private suspend fun estimateMediaSize(
    context: Context,
    sampleSize: Int = 300
): Long = withContext(Dispatchers.IO) {
    val projection = arrayOf(MediaStore.Files.FileColumns.SIZE)

    val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?)"
    val selectionArgs = arrayOf(
        MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
        MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
    )

    var totalCount = 0L
    var sampledSize = 0L
    var sampledCount = 0

    try {
        // Contar total de items
        context.contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            arrayOf(MediaStore.Files.FileColumns._ID),
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            totalCount = cursor.count.toLong()
        }

        if (totalCount == 0L) return@withContext 0L

        // Muestrear para calcular tamaño promedio
        context.contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            projection,
            selection,
            selectionArgs,
            "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
        )?.use { cursor ->
            val colSize = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)

            while (cursor.moveToNext() && sampledCount < sampleSize) {
                if (!cursor.isNull(colSize)) {
                    sampledSize += cursor.getLong(colSize)
                    sampledCount++
                }
            }
        }

        if (sampledCount == 0) return@withContext 0L

        val avgSize = sampledSize / sampledCount
        avgSize * totalCount

    } catch (e: Exception) {
        Log.e("StorageAnalyzer", "Error estimating media size", e)
        0L
    }
}