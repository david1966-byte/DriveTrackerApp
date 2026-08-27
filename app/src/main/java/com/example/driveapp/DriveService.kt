package com.example.driveapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class DriveService : Service() {

    companion object {
        const val CHANNEL_ID = "drive_status_channel"
        const val NOTIFICATION_ID = 1001
        const val EXTRA_STATE = "extra_state"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val stateText = intent?.getStringExtra(EXTRA_STATE) ?: "מעקב כבוי"
        createNotificationChannel()

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("מצב נסיעה")
            .setContentText(stateText)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "מצב מעקב נסיעה",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
