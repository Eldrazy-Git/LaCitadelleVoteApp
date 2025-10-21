package com.example.lacitadellevote.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.example.lacitadellevote.data.VoteSitesRepository
import com.example.lacitadellevote.model.VoteSite
import com.example.lacitadellevote.work.VoteReminderWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit

object VoteScheduler {

    fun scheduleNextExact(context: Context, site: VoteSite, triggerAtMillis: Long) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "VOTE_REMINDER_${site.id}"
            putExtra("site_id", site.id)
            putExtra("site_name", site.name)
            putExtra("site_url", site.url)
            putExtra("cooldown", site.cooldownMinutes)
        }
        val pi = PendingIntent.getBroadcast(
            context, site.id.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)

        runBlocking(Dispatchers.IO) {
            VoteSitesRepository(context).setNextTrigger(site.id, triggerAtMillis)
        }
    }

    fun scheduleNext(context: Context, site: VoteSite, delayMinutes: Long = site.cooldownMinutes) {
        val triggerAt = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(delayMinutes)
        if (ExactAlarmPermission.canScheduleExact(context)) {
            scheduleNextExact(context, site, triggerAt)
        } else {
            val data = Data.Builder()
                .putString("site_id", site.id)
                .putString("site_name", site.name)
                .putString("site_url", site.url)
                .putLong("cooldown", site.cooldownMinutes)
                .build()

            val req = OneTimeWorkRequestBuilder<VoteReminderWorker>()
                .setInputData(data)
                .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context).enqueue(req)
        }
    }

    /**
     * Replanifie uniquement les alarmes qui existaient déjà (next_ts futur stocké).
     * Ne crée rien de nouveau.
     */
    fun rescheduleFromStore(context: Context) {
        val repo = VoteSitesRepository(context)
        val now = System.currentTimeMillis()
        runBlocking(Dispatchers.IO) {
            repo.defaultSites().forEach { site ->
                val next = repo.getNextTrigger(site.id)
                if (next > now) {
                    // Repose exactement à la même heure
                    scheduleNextExact(context, site, next)
                }
            }
        }
    }
}
