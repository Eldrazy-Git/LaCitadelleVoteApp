package fr.lacitadelle.votecompagnon.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import fr.lacitadelle.votecompagnon.data.VoteSitesRepository
import fr.lacitadelle.votecompagnon.model.VoteSite
import fr.lacitadelle.votecompagnon.notif.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class VoteReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val siteId = inputData.getString(KEY_SITE_ID) ?: return Result.failure()
        val siteName = inputData.getString(KEY_SITE_NAME) ?: siteId
        val siteUrl = inputData.getString(KEY_SITE_URL) ?: ""
        val cooldown = inputData.getInt(KEY_COOLDOWN, 90)

        // 1 notif par site (id stable) => visible tant que non effacée
        NotificationHelper.showVoteReminder(
            context = applicationContext,
            siteId = siteId,
            siteName = siteName,
            url = siteUrl,
            cooldownMinutes = cooldown,
            notificationId = siteId.hashCode()
        )

        // On marque ce site comme "prêt" après déclenchement
        withContext(Dispatchers.IO) {
            VoteSitesRepository(applicationContext).setNextTrigger(siteId, 0L)
        }

        return Result.success()
    }

    companion object {
        private const val KEY_SITE_ID = "site_id"
        private const val KEY_SITE_NAME = "site_name"
        private const val KEY_SITE_URL = "site_url"
        private const val KEY_COOLDOWN = "cooldown"

        private fun uniqueWorkNameFor(siteId: String) = "vote_reminder_$siteId"

        fun scheduleNext(
            context: Context,
            site: VoteSite,
            delayMinutes: Long
        ) {
            val safeDelay = delayMinutes.coerceAtLeast(1L)
            val triggerAt = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(safeDelay)

            val repo = VoteSitesRepository(context)
            runBlocking(Dispatchers.IO) {
                repo.setNextTrigger(site.id, triggerAt)
            }

            val input = Data.Builder()
                .putString(KEY_SITE_ID, site.id)
                .putString(KEY_SITE_NAME, site.name)
                .putString(KEY_SITE_URL, site.url)
                .putInt(KEY_COOLDOWN, safeDelay.toInt())
                .build()

            val request = OneTimeWorkRequestBuilder<VoteReminderWorker>()
                .setInitialDelay(safeDelay, TimeUnit.MINUTES)
                .setInputData(input)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                uniqueWorkNameFor(site.id),
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun cancel(context: Context, siteId: String) {
            WorkManager.getInstance(context).cancelUniqueWork(uniqueWorkNameFor(siteId))
        }
    }
}
