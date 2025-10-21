package com.example.lacitadellevote.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.lacitadellevote.notif.NotificationHelper
import com.example.lacitadellevote.data.VoteSitesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("site_id") ?: return
        val name = intent.getStringExtra("site_name") ?: id
        val url = intent.getStringExtra("site_url") ?: ""
        val cooldown = intent.getLongExtra("cooldown", 0L)

        NotificationHelper.showVoteReminder(
            context,
            "Vote $name",
            "C'est l'heure de voter pour $name",
            url,
            id.hashCode(),
            siteId = id,
            siteName = name,
            cooldownMinutes = cooldown
        )

        // Clear next trigger so UI shows "Prêt à voter" and no auto-chain happens
        runBlocking(Dispatchers.IO) {
            VoteSitesRepository(context).setNextTrigger(id, 0L)
        }
    }
}
