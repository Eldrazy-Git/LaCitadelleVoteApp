package com.example.lacitadellevote

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.res.ResourcesCompat
import com.example.lacitadellevote.alarm.ExactAlarmPermission
import com.example.lacitadellevote.alarm.VoteScheduler
import com.example.lacitadellevote.data.VoteSitesRepository
import com.example.lacitadellevote.model.VoteSite
import com.example.lacitadellevote.notif.NotificationHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var btnVote1: LinearLayout
    private lateinit var btnVote4: LinearLayout
    private lateinit var btnVote2: LinearLayout
    private lateinit var btnVote3: LinearLayout
    private lateinit var timerVote1: TextView
    private lateinit var timerVote4: TextView
    private lateinit var timerVote2: TextView
    private lateinit var timerVote3: TextView
    private lateinit var logoCitadelle: ImageView
    private lateinit var logoDiscord: ImageView
    private lateinit var btnSettings: ImageButton

    private lateinit var repo: VoteSitesRepository
    private lateinit var site1: VoteSite
    private lateinit var site4: VoteSite
    private lateinit var site2: VoteSite
    private lateinit var site3: VoteSite

    private val requestNotifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* ignore */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val openedFromNotification = intent?.hasExtra("open_url") == true
        if (!openedFromNotification) {
            NotificationHelper.cancelAllVoteReminders(this)
        }

        // Bind vues
        btnVote1 = findViewById(R.id.btnVote1)
        btnVote4 = findViewById(R.id.btnVote4)
        btnVote2 = findViewById(R.id.btnVote2)
        btnVote3 = findViewById(R.id.btnVote3)
        timerVote1 = findViewById(R.id.timerVote1)
        timerVote4 = findViewById(R.id.timerVote4)
        timerVote2 = findViewById(R.id.timerVote2)
        timerVote3 = findViewById(R.id.timerVote3)
        logoCitadelle = findViewById(R.id.logoCitadelle)
        logoDiscord = findViewById(R.id.logoDiscord)
        btnSettings = findViewById(R.id.btnSettings)

        // Police custom si dispo
        applyCustomFontIfAvailable()

        repo = VoteSitesRepository(this)
        val defaults = repo.defaultSites()
        site1 = defaults[0]; site4 = defaults[3]; site2 = defaults[1]; site3 = defaults[2]

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (!ExactAlarmPermission.canScheduleExact(this) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            startActivity(Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
        }

        // Aucun scheduling automatique ici

        btnVote1.setOnClickListener { onVoteClick(site1) }
        btnVote4.setOnClickListener { onVoteClick(site4) }
        btnVote2.setOnClickListener { onVoteClick(site2) }
        btnVote3.setOnClickListener { onVoteClick(site3) }

        logoCitadelle.setOnClickListener { openUrl("https://lacitadelle-mc.fr/votes") }
        logoDiscord.setOnClickListener { openUrl("https://discord.gg/h8jr9jkQzk") }
        // btnSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }

        // Ouverture via notification → ouvre le lien + (si dispo) relance le cooldown
        intent?.getStringExtra("open_url")?.let { urlFromNotif ->
            val idFromNotif = intent?.getStringExtra("site_id")
            val nameFromNotif = intent?.getStringExtra("site_name") ?: idFromNotif ?: ""
            val cooldownFromNotif = intent?.getLongExtra("cooldown", -1L) ?: -1L
            openUrl(urlFromNotif)

            if (idFromNotif != null && cooldownFromNotif > 0L) {
                scope.launch {
                    val now = System.currentTimeMillis()
                    val next = withContext(Dispatchers.IO) { repo.observeNextTrigger(idFromNotif).first() }
                    if (next == 0L || next <= now) {
                        VoteScheduler.scheduleNext(
                            this@MainActivity,
                            VoteSite(idFromNotif, nameFromNotif, urlFromNotif, cooldownFromNotif),
                            cooldownFromNotif
                        )
                    } else {
                        // Dispo non atteinte → pas de reset
                        showRemainingToast(next - now)
                    }
                }
            }
        }

        startCountdownUpdates()
    }

    /**
     * Ouvre le site. Ne démarre le cooldown que si l’état est "Prêt à voter" (next_ts == 0 ou dépassé).
     * Sinon : on n’arm​e pas le timer et on affiche un toast "Encore HH:MM:SS — timer non relancé".
     */
    private fun onVoteClick(site: VoteSite) {
        openUrl(site.url)
        scope.launch {
            val now = System.currentTimeMillis()
            val next = withContext(Dispatchers.IO) { repo.observeNextTrigger(site.id).first() }
            if (next == 0L || next <= now) {
                VoteScheduler.scheduleNext(this@MainActivity, site, delayMinutes = site.cooldownMinutes)
            } else {
                showRemainingToast(next - now)
            }
        }
    }

    private fun openUrl(url: String) {
        try {
            val cti = CustomTabsIntent.Builder().build()
            cti.launchUrl(this, Uri.parse(url))
        } catch (_: Exception) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    private fun startCountdownUpdates() {
        scope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                try {
                    val next1 = withContext(Dispatchers.IO) { repo.observeNextTrigger(site1.id).first() }
                    val next4 = withContext(Dispatchers.IO) { repo.observeNextTrigger(site4.id).first() }
                    val next2 = withContext(Dispatchers.IO) { repo.observeNextTrigger(site2.id).first() }
                    val next3 = withContext(Dispatchers.IO) { repo.observeNextTrigger(site3.id).first() }

                    renderTimer(timerVote1, now, next1)
                    renderTimer(timerVote4, now, next4)
                    renderTimer(timerVote2, now, next2)
                    renderTimer(timerVote3, now, next3)
                } catch (_: Exception) { /* ignore */ }
                delay(1000L)
            }
        }
    }

    private fun renderTimer(view: TextView, now: Long, nextTs: Long) {
        if (nextTs <= now || nextTs == 0L) {
            view.text = "Prêt à voter"
            return
        }
        val remainingMs = nextTs - now
        val h = TimeUnit.MILLISECONDS.toHours(remainingMs)
        val m = TimeUnit.MILLISECONDS.toMinutes(remainingMs) % 60
        val s = TimeUnit.MILLISECONDS.toSeconds(remainingMs) % 60
        view.text = String.format("Vote dans %02d:%02d:%02d", h, m, s)
    }

    private fun applyCustomFontIfAvailable() {
        try {
            val tf = ResourcesCompat.getFont(this, R.font.medievalsharp)
            if (tf != null) {
                timerVote1.typeface = tf
                timerVote4.typeface = tf
                timerVote2.typeface = tf
                timerVote3.typeface = tf
            }
        } catch (_: Exception) {
            // police absente : on ignore
        }
    }

    // --- Helpers UI ---

    private fun showRemainingToast(remainingMs: Long) {
        val msg = "Encore ${formatHMS(remainingMs)}"
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun formatHMS(ms: Long): String {
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return String.format("%02d:%02d:%02d", h, m, s)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra("open_url")?.let { openUrl(it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
