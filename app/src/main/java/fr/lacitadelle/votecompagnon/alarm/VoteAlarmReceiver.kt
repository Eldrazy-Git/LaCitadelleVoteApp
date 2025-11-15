package fr.lacitadelle.votecompagnon.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import fr.lacitadelle.votecompagnon.data.VoteSitesRepository
import fr.lacitadelle.votecompagnon.notif.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Déclenché par AlarmManager à la fin du cooldown.
 * Affiche la notif et remet next_trigger à 0.
 */
class VoteAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val siteId = intent.getStringExtra("site_id") ?: return
        val siteName = intent.getStringExtra("site_name") ?: siteId
        val siteUrl = intent.getStringExtra("site_url") ?: ""
        val cooldown = intent.getIntExtra("cooldown", 90)

        // Notification immédiate (même écran éteint)
        NotificationHelper.showVoteReminder(
            context = context,
            siteId = siteId,
            siteName = siteName,
            url = siteUrl,
            cooldownMinutes = cooldown,
            notificationId = siteId.hashCode()
        )

        // Reset du next_trigger pour l’UI
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { VoteSitesRepository(context).setNextTrigger(siteId, 0L) }
        }
    }
}
