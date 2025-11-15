package fr.lacitadelle.votecompagnon.notif

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
import fr.lacitadelle.votecompagnon.MainActivity
import fr.lacitadelle.votecompagnon.R

object NotificationHelper {

    // Préfixe commun à tous les canaux liés aux votes
    private const val CHANNEL_PREFIX = "votes_high"

    /**
     * Appelé au lancement ou quand les prefs changent.
     * On crée un canal "global" basé sur les prefs, surtout utile pour la notif de test.
     */
    @JvmStatic
    fun ensureChannelForPrefs(context: Context): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val vibrate = prefs.getBoolean("pref_vibrate", true)
        val customSound = prefs.getBoolean("pref_custom_sound", true)
        // Canal générique (siteId = "global")
        return ensureSiteChannel(context, "global", vibrate, customSound)
    }

    /**
     * Crée (si nécessaire) un canal spécifique à un site et aux prefs actuelles.
     *
     * - 1 site = 1 channel (ou plusieurs variantes si l'utilisateur change son/vibration).
     * - On NE supprime PAS les anciens canaux pour éviter d'effacer des notifs existantes.
     */
    @JvmStatic
    fun ensureSiteChannel(
        context: Context,
        siteId: String,
        vibrate: Boolean,
        customSound: Boolean
    ): String {
        val safeSiteId = siteId.lowercase().replace(Regex("[^a-z0-9_]"), "_")
        val soundPart = if (customSound) "c" else "d"
        val vibPart = if (vibrate) "v1" else "v0"
        val channelId = "${CHANNEL_PREFIX}_${safeSiteId}_${soundPart}_${vibPart}"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val existing = nm.getNotificationChannel(channelId)
            if (existing == null) {
                val channel = NotificationChannel(
                    channelId,
                    // Nom lisible dans les paramètres système
                    context.getString(R.string.notification_channel_votes) +
                            " - " + siteId.uppercase(),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    enableVibration(vibrate)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC

                    if (customSound) {
                        val soundUri =
                            Uri.parse("android.resource://${context.packageName}/${R.raw.vote_bell}")
                        val attrs = AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                        setSound(soundUri, attrs)
                    } else {
                        // Son par défaut du système
                    }
                }

                nm.createNotificationChannel(channel)
            }
        }

        return channelId
    }

    /**
     * Affiche une notification de rappel pour un site.
     *
     * - Utilise un canal dédié au site.
     * - Pas de groupe, pas de summary.
     * - notificationId doit être stable par site (ex: siteId.hashCode()).
     */
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

        // 🔔 Canal spécifique à ce site
        val channelId = ensureSiteChannel(context, siteId, vibrate, customSound)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_url", url)
            putExtra("site_id", siteId)
            putExtra("cooldown", cooldownMinutes)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            siteId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(getNotificationIcon(context))
            .setContentTitle(
                context.getString(
                    R.string.notification_vote_title,
                    siteName
                )
            )
            .setContentText(context.getString(R.string.notification_vote_text))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .apply {
                // Pré-Android O : gérer manuellement son + vibration
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                    if (vibrate) {
                        setVibrate(longArrayOf(0, 180, 120, 180))
                    } else {
                        setVibrate(longArrayOf(0))
                    }

                    val soundUri = if (customSound) {
                        Uri.parse("android.resource://${context.packageName}/${R.raw.vote_bell}")
                    } else {
                        Settings.System.DEFAULT_NOTIFICATION_URI
                    }
                    setSound(soundUri)
                }
            }

        // 1 notif par site (id stable), plusieurs sites => plusieurs notifs visibles
        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
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

    /**
     * Annule la notification associée à un site spécifique.
     * (utilisé lors d’un reset individuel)
     */
    @JvmStatic
    fun cancelVoteReminderForSite(context: Context, siteId: String) {
        NotificationManagerCompat.from(context).cancel(siteId.hashCode())
    }

    /**
     * Annule toutes les notifications de l’app.
     * (utilisé lors d’un reset global)
     */
    @JvmStatic
    fun cancelAllVoteReminders(context: Context) {
        NotificationManagerCompat.from(context).cancelAll()
    }
}
