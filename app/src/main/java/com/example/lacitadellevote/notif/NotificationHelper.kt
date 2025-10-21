package com.example.lacitadellevote.notif

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.lacitadellevote.MainActivity
import com.example.lacitadellevote.R
import android.app.PendingIntent
import com.example.lacitadellevote.data.VoteSitesRepository

object NotificationHelper {
    const val CHANNEL_ID = "vote_reminders"

    fun createDefaultChannel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // (Re)crée le canal avec le son custom (les canaux sont immuables)
        try { nm.deleteNotificationChannel(CHANNEL_ID) } catch (_: Exception) {}

        val soundUri = Uri.parse("android.resource://${context.packageName}/${R.raw.vote_bell}")
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val channel = NotificationChannel(
            CHANNEL_ID, "Rappels de vote",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            enableVibration(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            description = "Notifications pour rappeler de voter"
            setSound(soundUri, attrs)
        }
        nm.createNotificationChannel(channel)
    }

    fun showVoteReminder(context: Context, title: String, text: String, destinationUrl: String, notifId: Int, siteId: String? = null, siteName: String? = null, cooldownMinutes: Long? = null) {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("open_url", destinationUrl)
            siteId?.let { putExtra("site_id", it) }
            siteName?.let { putExtra("site_name", it) }
            cooldownMinutes?.let { putExtra("cooldown", it) }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context, notifId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = Uri.parse("android.resource://${context.packageName}/${R.raw.vote_bell}")

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            // Fallback pour < Android O (les canaux n'existent pas)
            .setSound(soundUri)
            .build()

        NotificationManagerCompat.from(context).notify(notifId, notif)
    }

    fun cancelAllVoteReminders(context: Context) {
        val nm = NotificationManagerCompat.from(context)
        val ids = VoteSitesRepository(context).defaultSites().map { it.id.hashCode() }
        ids.forEach { nm.cancel(it) }
    }
}
