package fr.lacitadelle.votecompagnon.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import fr.lacitadelle.votecompagnon.data.GraceDelayStorage
import fr.lacitadelle.votecompagnon.data.VoteSitesRepository
import fr.lacitadelle.votecompagnon.model.VoteSite
import fr.lacitadelle.votecompagnon.work.VoteReminderWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit

object VoteScheduler {

    /** Programme une alarme EXACTE à l’horodatage donné et persiste le next_trigger. */
    fun scheduleNextExact(context: Context, site: VoteSite, triggerAtMillis: Long) {
        val intent = Intent(context, VoteAlarmReceiver::class.java).apply {
            action = "VOTE_REMINDER_${site.id}"
            putExtra("site_id", site.id)
            putExtra("site_name", site.name)
            putExtra("site_url", site.url)
            putExtra("cooldown", site.cooldownMinutes.toInt())
        }

        val pi = PendingIntent.getBroadcast(
            context,
            site.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // Réveil écran verrouillé / Doze
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)

        // Persiste le prochain déclenchement (déjà avec le décalage inclus)
        runBlocking(Dispatchers.IO) {
            VoteSitesRepository(context).setNextTrigger(site.id, triggerAtMillis)
        }
    }

    /**
     * Programme dans [delayMinutes].
     * - Si autorisation d'alarmes exactes : AlarmManager exact.
     * - Sinon : WorkManager (sans expedited, pour éviter le crash).
     * Le délai utilisateur (grace) est ajouté par-dessus.
     */
    fun scheduleNext(context: Context, site: VoteSite, delayMinutes: Long = site.cooldownMinutes) {
        val delay = delayMinutes.coerceAtLeast(1L)

        // cooldown de base (en ms) + décalage utilisateur (en ms)
        val baseDelayMs = TimeUnit.MINUTES.toMillis(delay)
        val graceMs = GraceDelayStorage.getGraceMillis(context)
        val totalDelayMs = baseDelayMs + graceMs

        val triggerAt = System.currentTimeMillis() + totalDelayMs

        if (ExactAlarmPermission.canScheduleExact(context)) {
            // Cas "clean" : alarmes exactes autorisées
            scheduleNextExact(context, site, triggerAt)
        } else {
            // Fallback WorkManager si l’appli n’a pas l’autorisation d’alarmes exactes
            val data = Data.Builder()
                .putString("site_id", site.id)
                .putString("site_name", site.name)
                .putString("site_url", site.url)
                .putInt("cooldown", site.cooldownMinutes.toInt())
                .build()

            // ⚠️ IMPORTANT : pas de setExpedited() avec un délai, sinon crash
            val req = OneTimeWorkRequestBuilder<VoteReminderWorker>()
                .setInputData(data)
                .setInitialDelay(totalDelayMs, TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context).enqueue(req)

            // On persiste aussi le déclenchement prévu (pour l’UI + reboot)
            runBlocking(Dispatchers.IO) {
                VoteSitesRepository(context).setNextTrigger(site.id, triggerAt)
            }
        }
    }

    /**
     * Replanifie uniquement ce qui existe déjà (restaure les timers au boot/update).
     * Si les alarmes exactes ne sont plus autorisées, on bascule aussi sur WorkManager.
     */
    fun rescheduleFromStore(context: Context) {
        val repo = VoteSitesRepository(context)
        val now = System.currentTimeMillis()

        runBlocking(Dispatchers.IO) {
            repo.defaultSites().forEach { site ->
                val next = repo.getNextTrigger(site.id)
                if (next > now) {
                    if (ExactAlarmPermission.canScheduleExact(context)) {
                        // On peut encore utiliser les alarmes exactes
                        scheduleNextExact(context, site, next)
                    } else {
                        // On recalcule un délai approx pour WorkManager
                        val delayMinutes = ((next - now) / TimeUnit.MINUTES.toMillis(1))
                            .coerceAtLeast(1L)
                        scheduleNext(context, site, delayMinutes)
                    }
                }
            }
        }
    }
}
