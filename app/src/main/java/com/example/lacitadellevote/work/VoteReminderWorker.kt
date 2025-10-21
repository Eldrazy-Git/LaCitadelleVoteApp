package com.example.lacitadellevote.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.lacitadellevote.notif.NotificationHelper
import com.example.lacitadellevote.data.VoteSitesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VoteReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getString("site_id") ?: return Result.failure()
        val name = inputData.getString("site_name") ?: id
        val url = inputData.getString("site_url") ?: ""
        val cooldown = inputData.getLong("cooldown", 0L)

        NotificationHelper.showVoteReminder(
            applicationContext,
            "Vote $name",
            "C'est l'heure de voter pour $name",
            url,
            id.hashCode(),
            siteId = id,
            siteName = name,
            cooldownMinutes = cooldown
        )

        // Clear next trigger so no auto rechain
        withContext(Dispatchers.IO) {
            VoteSitesRepository(applicationContext).setNextTrigger(id, 0L)
        }

        return Result.success()
    }
}
