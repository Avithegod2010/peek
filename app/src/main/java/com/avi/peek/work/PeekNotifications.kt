package com.avi.peek.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.avi.peek.MainActivity
import com.avi.peek.R
import com.avi.peek.data.repo.SyncReport

object PeekNotifications {

    private const val CHANNEL_ID = "new_posts"
    private const val NOTIFICATION_ID = 1001

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "New posts",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "A quiet heads-up when the accounts you follow post something new."
        }
        manager.createNotificationChannel(channel)
    }

    fun postNewPosts(
        context: Context,
        report: SyncReport,
        hiddenPrivateUsernames: Set<String> = emptySet(),
    ) {
        val visible = report.newCodesByUsername.filterKeys { it !in hiddenPrivateUsernames }
        if (visible.isEmpty()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val lines = visible.entries.take(6).map { (username, codes) ->
            "@$username · ${codes.size} new ${if (codes.size == 1) "post" else "posts"}"
        }
        val summary = if (lines.size == 1) lines.first() else "${visible.size} accounts have something new"

        val firstUsername = visible.keys.firstOrNull()
        val intent = Intent(context, MainActivity::class.java).apply {
            data = Uri.parse(if (firstUsername != null) "peek://profile/$firstUsername" else "peek://home")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            2001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("New from your circle")
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }
}
