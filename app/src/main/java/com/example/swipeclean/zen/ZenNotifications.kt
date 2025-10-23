package com.example.swipeclean.zen

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.swipeclean.R

fun createZenNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            "zen_timer",
            "Temporizador Zen",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notificaciones del temporizador de modo Zen"
            enableVibration(true)
            enableLights(true)
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}

fun showTimerFinishedNotification(context: Context) {
    val notification = NotificationCompat.Builder(context, "zen_timer")
        .setSmallIcon(R.drawable.ic_timer) // Necesitarás crear este icono
        .setContentTitle("Sesión Zen Completada")
        .setContentText("Tu sesión de limpieza ha terminado")
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .setVibrate(longArrayOf(0, 500, 250, 500))
        .build()

    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    notificationManager.notify(1001, notification)
}