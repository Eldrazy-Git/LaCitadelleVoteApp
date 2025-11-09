package com.example.lacitadellevote.notif

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.preference.PreferenceManager
import com.example.lacitadellevote.MainActivity
import com.example.lacitadellevote.R

object NotificationHelper {

    private const val GROUP_VOTES = "votes_group"
    private const val CHANNEL_PREFIX = "votes_high"
    private const val SUMMARY_NOTIFICATION_ID = 999_999

    /** À appeler depuis MyApplication.onCreate() et quand l’utilisateur change un réglage. */
    @JvmStatic
    fun ensureChannelForPrefs(context: Context): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val vibrate = prefs.getBoolean("pref_vibrate", true)
        val customSound = prefs.getBoolean("pref_custom_sound", true)
        return ensureChannel(context, vibrate, customSound)
    }

    /**
     * (Re)crée TOUJOURS le canal correspondant aux prefs (id unique par combo).
     * Si customSound=false, on NE fixe PAS de son sur le canal O+ → Android jouera le son système.
     */
    @JvmStatic
    fun ensureChannel(context: Context, vibrate: Boolean, customSound: Boolean): String {
        val channelId = buildChannelId(customSound, vibrate)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Recrée proprement le canal pour refléter les nouveaux réglages
            nm.getNotificationChannel(channelId)?.let { nm.deleteNotificationChannel(channelId) }

            val channel = NotificationChannel(
                channelId,
                context.getString(R.string.notification_channel_votes),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                enableVibration(vibrate)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC

                if (customSound) {
                    val soundUri = Uri.parse("android.resource://${context.packageName}/${R.raw.vote_bell}")
                    val attrs = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    setSound(soundUri, attrs)
                } else {
                    // Ne PAS appeler setSound → le son par défaut du système sera utilisé.
                }
            }

            nm.createNotificationChannel(channel)
        }

        return channelId
    }

    private fun buildChannelId(customSound: Boolean, vibrate: Boolean): String {
        val soundPart = if (customSound) "custom" else "default"
        val vibPart = if (vibrate) "v1" else "v0"
        return "${CHANNEL_PREFIX}_${soundPart}_${vibPart}"
    }

    @JvmStatic
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun showVoteReminder(
        context: Context,
        siteId: String,
        siteName: String,
        url: String,
        cooldownMinutes: Int,
        notificationId: Int
    ) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val vibrate = prefs.getBoolean("pref_vibrate", true)
        val customSound = prefs.getBoolean("pref_custom_sound", true)

        val channelId = ensureChannel(context, vibrate, customSound)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_url", url)
            putExtra("site_id", siteId)
            putExtra("cooldown", cooldownMinutes)
        }
        val pi = PendingIntent.getActivity(
            context, siteId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val child = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(getNotificationIcon(context))
            .setContentTitle(context.getString(R.string.notification_vote_title, siteName))
            .setContentText(context.getString(R.string.notification_vote_text))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setGroup(GROUP_VOTES)
            .apply {
                // Fallback pré-O : son + vibration posés directement sur la notif.
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                    if (vibrate) setVibrate(longArrayOf(0, 180, 120, 180)) else setVibrate(longArrayOf(0))
                    val soundUri = if (customSound) {
                        Uri.parse("android.resource://${context.packageName}/${R.raw.vote_bell}")
                    } else {
                        // Son de notification SYSTÈME
                        Settings.System.DEFAULT_NOTIFICATION_URI
                    }
                    setSound(soundUri)
                }
            }
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, child)

        val summary = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.notification_vote_group_title))
            .setContentText(context.getString(R.string.notification_vote_group_text))
            .setGroup(GROUP_VOTES)
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN) // seuls les enfants alertent
            .setSilent(true)
            .build()

        NotificationManagerCompat.from(context).notify(SUMMARY_NOTIFICATION_ID, summary)
    }

    private fun getNotificationIcon(context: Context): Int {
        val res = context.resources
        val pkg = context.packageName
        val custom = res.getIdentifier("ic_stat_citadelle", "drawable", pkg)
        if (custom != 0) return custom
        val legacy = res.getIdentifier("ic_vote_notification", "drawable", pkg)
        if (legacy != 0) return legacy
        return R.mipmap.ic_launcher
    }

    @JvmStatic
    fun cancelAllVoteReminders(context: Context) {
        NotificationManagerCompat.from(context).cancel(SUMMARY_NOTIFICATION_ID)
    }
}
