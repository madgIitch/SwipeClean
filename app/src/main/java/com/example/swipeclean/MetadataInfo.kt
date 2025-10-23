package com.example.swipeclean

data class MediaMetadata(
    val fileName: String,
    val fileSize: Long,
    val dateTaken: Long,
    val resolution: String?, // "1920x1080"
    val mimeType: String,
    val latitude: Double?,
    val longitude: Double?,
    val duration: Long? // Para videos, en milisegundos
)