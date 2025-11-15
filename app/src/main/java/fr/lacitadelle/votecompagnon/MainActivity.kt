package fr.lacitadelle.votecompagnon

import android.Manifest
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.preference.PreferenceManager
import com.bumptech.glide.Glide
import fr.lacitadelle.votecompagnon.alarm.ExactAlarmPermission
import fr.lacitadelle.votecompagnon.alarm.VoteScheduler
import fr.lacitadelle.votecompagnon.data.VoteSitesRepository
import fr.lacitadelle.votecompagnon.model.VoteSite
import fr.lacitadelle.votecompagnon.notif.NotificationHelper
import fr.lacitadelle.votecompagnon.utils.CustomTypefaceSpan
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import android.text.Spannable
import android.text.SpannableString

class MainActivity : ComponentActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var rightDrawer: NavigationView

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
    private lateinit var repo: VoteSitesRepository
    private lateinit var site1: VoteSite
    private lateinit var site4: VoteSite
    private lateinit var site2: VoteSite
    private lateinit var site3: VoteSite

    private val requestNotifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* ignore */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        // plein écran
        WindowCompat.setDecorFitsSystemWindows(window, false)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        val openedFromNotification = intent?.hasExtra("open_url") == true
        if (!openedFromNotification) {
            NotificationHelper.cancelAllVoteReminders(this)
        }

        // bind vues "vote"
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

        // drawer
        drawerLayout = findViewById(R.id.drawerLayout)
        rightDrawer = findViewById(R.id.rightDrawer)

        // Icône Elytreum animée dans le drawer
        val elytreumMenuItem = rightDrawer.menu.findItem(R.id.nav_Elytreum)
        val elytreumActionView = elytreumMenuItem?.actionView

        if (elytreumActionView != null) {
            val img = elytreumActionView.findViewById<ImageView?>(R.id.imgElytreum)
            if (img != null) {
                Glide.with(this)
                    .asGif()
                    .load(R.drawable.ic_elytreum) // ton GIF (ic_elytreum.gif) dans res/drawable
                    .into(img)
            }

            // Clique sur toute la ligne Elytreum
            elytreumActionView.setOnClickListener {
                startActivity(Intent(this, ElytreumActivity::class.java))
                drawerLayout.closeDrawer(GravityCompat.END)
            }
        }



        // pas de marges cheloues
        rightDrawer.fitsSystemWindows = false
        ViewCompat.setOnApplyWindowInsetsListener(rightDrawer) { v, _ ->
            v.setPadding(0, 0, 0, 0)
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.setOnApplyWindowInsetsListener(drawerLayout) { _, insets -> insets }

        // on bloque le swipe (geste système ne sera plus en conflit)
        drawerLayout.setDrawerLockMode(
            DrawerLayout.LOCK_MODE_LOCKED_CLOSED,
            GravityCompat.END
        )

        // re-lock quand on ferme
        drawerLayout.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerClosed(drawerView: View) {
                if (drawerView.id == R.id.rightDrawer) {
                    drawerLayout.setDrawerLockMode(
                        DrawerLayout.LOCK_MODE_LOCKED_CLOSED,
                        GravityCompat.END
                    )
                }
            }
        })

        // bouton/zone d’ouverture du drawer (si présents dans le layout)
        val handleContainer = findViewById<View?>(R.id.handleContainer)
        val handle = findViewById<ImageView?>(R.id.handleDrawer)
        handleContainer?.setOnClickListener { openRightDrawer() }
        handle?.setOnClickListener { openRightDrawer() }

        // menu
        rightDrawer.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_settings -> {
                    startActivity(
                        Intent(
                            this,
                            fr.lacitadelle.votecompagnon.settings.SettingsActivity::class.java
                        )
                    )
                }
                R.id.nav_wiki -> openUrl("https://lacitadelle-mc.fr/wiki")
                R.id.nav_rankup -> startActivity(Intent(this, RankupActivity::class.java))
                R.id.nav_Elytreum -> startActivity(Intent(this, ElytreumActivity::class.java))
                R.id.nav_more -> Toast.makeText(this, "Bientôt 👀", Toast.LENGTH_SHORT).show()
            }
            drawerLayout.closeDrawer(GravityCompat.END)
            drawerLayout.setDrawerLockMode(
                DrawerLayout.LOCK_MODE_LOCKED_CLOSED,
                GravityCompat.END
            )
            true
        }

        // repo & sites
        repo = VoteSitesRepository(this)
        val defaults = repo.defaultSites()
        site1 = defaults[0]; site2 = defaults[1]; site3 = defaults[2]; site4 = defaults[3]

        // police custom
        applyCustomFontIfAvailable()

        // permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (!ExactAlarmPermission.canScheduleExact(this) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            startActivity(Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
        }

        // clicks votes
        btnVote1.setOnClickListener { onVoteClick(site1) }
        btnVote4.setOnClickListener { onVoteClick(site4) }
        btnVote2.setOnClickListener { onVoteClick(site2) }
        btnVote3.setOnClickListener { onVoteClick(site3) }

        logoCitadelle.setOnClickListener { openUrl("https://lacitadelle-mc.fr/votes") }
        logoDiscord.setOnClickListener { openUrl("https://discord.gg/h8jr9jkQzk") }

        // notif → ouverture depuis une notif
        handleNotificationLaunch(intent)

        // timers
        startCountdownUpdates()
    }

    private fun openRightDrawer() {
        drawerLayout.setDrawerLockMode(
            DrawerLayout.LOCK_MODE_UNLOCKED,
            GravityCompat.END
        )
        drawerLayout.openDrawer(GravityCompat.END)
    }

    private fun applyDrawerFont() {
        if (!::rightDrawer.isInitialized) return

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val useMedieval = prefs.getBoolean("pref_custom_font", true)

        val menu = rightDrawer.menu
        val medieval = ResourcesCompat.getFont(this, R.font.medievalsharp)
        val defaultFont = Typeface.create("sans-serif-medium", Typeface.NORMAL)

        // 1) Appliquer la police aux items "normaux" (tous sauf Elytreum avec layout custom)
        for (i in 0 until menu.size()) {
            val item = menu.getItem(i)
            val rawTitle = item.title?.toString() ?: continue

            // on saute Elytreum, géré plus bas
            if (item.itemId == R.id.nav_Elytreum) continue

            if (useMedieval && medieval != null) {
                val span = SpannableString(rawTitle)
                span.setSpan(
                    CustomTypefaceSpan(medieval),
                    0,
                    span.length,
                    Spannable.SPAN_INCLUSIVE_INCLUSIVE
                )
                item.title = span
            } else {
                // reset : texte brut avec police standard
                item.title = rawTitle
            }
        }

        // 2) Cas particulier : nav_Elytreum utilise un layout custom (menu_item_elytreum.xml)
        val elyItem = menu.findItem(R.id.nav_Elytreum)
        val actionView = elyItem?.actionView

        if (actionView != null) {
            // fonction récursive pour appliquer la police aux TextView enfants
            fun applyToTextViews(root: ViewGroup) {
                for (j in 0 until root.childCount) {
                    val child = root.getChildAt(j)
                    when (child) {
                        is TextView -> {
                            child.typeface = if (useMedieval && medieval != null) {
                                medieval
                            } else {
                                defaultFont
                            }
                        }
                        is ViewGroup -> applyToTextViews(child)
                    }
                }
            }

            when (actionView) {
                is TextView -> {
                    actionView.typeface = if (useMedieval && medieval != null) {
                        medieval
                    } else {
                        defaultFont
                    }
                }
                is ViewGroup -> applyToTextViews(actionView)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        NotificationHelper.cancelAllVoteReminders(this)
        applyDrawerFont()
        applyCustomFontIfAvailable()
    }

    private fun handleNotificationLaunch(intent: Intent?) {
        val urlFromNotif = intent?.getStringExtra("open_url") ?: return
        val idFromNotif = intent.getStringExtra("site_id")
        val cooldownFromNotif = intent.getIntExtra("cooldown", 0)

        openUrl(urlFromNotif)

        if (idFromNotif != null && cooldownFromNotif > 0) {
            scope.launch {
                val now = System.currentTimeMillis()
                val storedNext = withContext(Dispatchers.IO) {
                    repo.observeNextTrigger(idFromNotif).first()
                }

                if (storedNext == 0L || storedNext <= now) {
                    val siteFromRepo = withContext(Dispatchers.IO) {
                        repo.defaultSites().firstOrNull { it.id == idFromNotif }
                    }
                    val siteToUse = siteFromRepo ?: VoteSite(
                        id = idFromNotif,
                        name = idFromNotif,
                        url = urlFromNotif,
                        cooldownMinutes = cooldownFromNotif.toLong()
                    )
                    // → planification via AlarmManager (VoteScheduler)
                    VoteScheduler.scheduleNext(
                        this@MainActivity,
                        siteToUse,
                        delayMinutes = cooldownFromNotif.toLong()
                    )
                } else {
                    showRemainingToast(storedNext - now)
                }
            }
        }
    }

    private fun onVoteClick(site: VoteSite) {
        openUrl(site.url)
        scope.launch {
            val now = System.currentTimeMillis()
            val next = withContext(Dispatchers.IO) { repo.observeNextTrigger(site.id).first() }
            if (next == 0L || next <= now) {
                // → planification via AlarmManager (VoteScheduler)
                VoteScheduler.scheduleNext(
                    this@MainActivity,
                    site,
                    delayMinutes = site.cooldownMinutes
                )
            } else {
                showRemainingToast(next - now)
            }
        }
    }

    private fun applyCustomFontIfAvailable() {
        if (!::timerVote1.isInitialized) return

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val useFont = prefs.getBoolean("pref_custom_font", true)
        val labels = listOf(timerVote1, timerVote2, timerVote3, timerVote4)

        if (useFont) {
            val tf = runCatching { ResourcesCompat.getFont(this, R.font.medievalsharp) }.getOrNull()
            if (tf != null) {
                labels.forEach { it.typeface = tf }
            } else {
                labels.forEach { it.typeface = Typeface.DEFAULT }
            }
        } else {
            labels.forEach { it.typeface = Typeface.DEFAULT }
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
                } catch (_: Exception) { }
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
        view.text = "Vote dans : ${formatRemaining(remainingMs)}"
    }

    private fun showRemainingToast(remainingMs: Long) {
        Toast.makeText(this, "Vote dans : ${formatRemaining(remainingMs)}", Toast.LENGTH_SHORT).show()
    }

    private fun formatRemaining(ms: Long): String {
        val totalSec = ms / 1000
        val totalMin = totalSec / 60
        val hours = totalMin / 60
        val minutes = totalMin % 60
        val seconds = totalSec % 60

        return when {
            totalSec < 60 -> "${seconds} s"
            totalMin < 60 -> "${minutes} min ${seconds} s"
            else -> "${hours} h ${minutes} min"
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationLaunch(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
