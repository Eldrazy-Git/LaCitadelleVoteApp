package fr.lacitadelle.votecompagnon

import android.Manifest
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
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
import com.google.android.material.navigation.NavigationView
import fr.lacitadelle.votecompagnon.alarm.ExactAlarmPermission
import fr.lacitadelle.votecompagnon.alarm.VoteScheduler
import fr.lacitadelle.votecompagnon.data.VoteSitesRepository
import fr.lacitadelle.votecompagnon.model.VoteSite
import fr.lacitadelle.votecompagnon.notif.NotificationHelper
import fr.lacitadelle.votecompagnon.utils.CustomTypefaceSpan
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {

    // Drawer
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var rightDrawer: NavigationView

    // Coroutine scope
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // UI vote
    private lateinit var btnVote1: LinearLayout
    private lateinit var btnVote2: LinearLayout
    private lateinit var btnVote3: LinearLayout
    private lateinit var btnVote4: LinearLayout

    private lateinit var timerVote1: TextView
    private lateinit var timerVote2: TextView
    private lateinit var timerVote3: TextView
    private lateinit var timerVote4: TextView

    private lateinit var logoCitadelle: ImageView
    private lateinit var logoDiscord: ImageView

    // Repository
    private lateinit var repo: VoteSitesRepository

    // Sites
    private lateinit var site1: VoteSite
    private lateinit var site2: VoteSite
    private lateinit var site3: VoteSite
    private lateinit var site4: VoteSite

    // Permission
    private val requestNotifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* ignore */ }

    private val ID_NAV_DISCORD = View.generateViewId()



    // =======================================================================
    // ON CREATE
    // =======================================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Reset notifications unless opened FROM notification
        val openedFromNotification = intent?.hasExtra("open_url") == true
        if (!openedFromNotification) NotificationHelper.cancelAllVoteReminders(this)

        bindViews()
        setupDrawer()
        applyCustomFontIfAvailable()

        // Permissions
        requestRuntimePermissions()

        setupVoteButtons()
        loadVoteSites()
        handleNotificationLaunch(intent)

        adjustButtonPadding()

        // ----- Layout enhancements -----
        applyCompactLayoutIfNeeded()
        setupDiscordAutoHide()
        enforceUniformButtonHeight()  // <<< AJOUT IMPORTANT

        // Start countdown timers
        startCountdownUpdates()
    }
    // =======================================================================
    // VIEW BINDING
    // =======================================================================

    private fun bindViews() {
        btnVote1 = findViewById(R.id.btnVote1)
        btnVote2 = findViewById(R.id.btnVote2)
        btnVote3 = findViewById(R.id.btnVote3)
        btnVote4 = findViewById(R.id.btnVote4)

        timerVote1 = findViewById(R.id.timerVote1)
        timerVote2 = findViewById(R.id.timerVote2)
        timerVote3 = findViewById(R.id.timerVote3)
        timerVote4 = findViewById(R.id.timerVote4)

        logoCitadelle = findViewById(R.id.logoCitadelle)
        logoDiscord = findViewById(R.id.logoDiscord)

        drawerLayout = findViewById(R.id.drawerLayout)
        rightDrawer = findViewById(R.id.rightDrawer)
    }


    // =======================================================================
    // DRAWER
    // =======================================================================

    private fun setupDrawer() {
        rightDrawer.fitsSystemWindows = false
        ViewCompat.setOnApplyWindowInsetsListener(rightDrawer) { v, _ ->
            v.setPadding(0, 0, 0, 0)
            WindowInsetsCompat.CONSUMED
        }

        ViewCompat.setOnApplyWindowInsetsListener(drawerLayout) { _, insets -> insets }

        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, GravityCompat.END)

        drawerLayout.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerClosed(drawerView: View) {
                if (drawerView.id == R.id.rightDrawer) {
                    drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, GravityCompat.END)
                }
            }
        })

        val handleContainer = findViewById<View?>(R.id.handleContainer)
        val handle = findViewById<View?>(R.id.handleDrawer)

        handleContainer?.setOnClickListener { openRightDrawer() }
        handle?.setOnClickListener { openRightDrawer() }

        setupDrawerMenu()
        setupDrawerAnimatedIcon()
    }

    private fun openRightDrawer() {
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, GravityCompat.END)
        drawerLayout.openDrawer(GravityCompat.END)
    }

    private fun setupDrawerMenu() {
        rightDrawer.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_settings -> {
                    startActivity(Intent(this, fr.lacitadelle.votecompagnon.settings.SettingsActivity::class.java))
                }
                ID_NAV_DISCORD -> {
                    openUrl("https://discord.gg/h8jr9jkQzk")
                }
                R.id.nav_wiki -> openUrl("https://lacitadelle-mc.fr/wiki")
                R.id.nav_rankup -> startActivity(Intent(this, RankupActivity::class.java))
                R.id.nav_Elytreum -> startActivity(Intent(this, ElytreumActivity::class.java))
                R.id.nav_more -> Toast.makeText(this, "Bientôt 👀", Toast.LENGTH_SHORT).show()
            }

            drawerLayout.closeDrawer(GravityCompat.END)
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, GravityCompat.END)
            true
        }
    }

    private fun setupDrawerAnimatedIcon() {
        val elytreumMenuItem = rightDrawer.menu.findItem(R.id.nav_Elytreum)
        val actionView = elytreumMenuItem?.actionView ?: return

        val img = actionView.findViewById<ImageView?>(R.id.imgElytreum)
        if (img != null) {
            Glide.with(this)
                .asGif()
                .load(R.drawable.ic_elytreum)
                .into(img)
        }

        actionView.setOnClickListener {
            startActivity(Intent(this, ElytreumActivity::class.java))
            drawerLayout.closeDrawer(GravityCompat.END)
        }
    }


    // =======================================================================
    // PERMISSIONS
    // =======================================================================

    private fun requestRuntimePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (!ExactAlarmPermission.canScheduleExact(this) &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        ) {
            startActivity(Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
        }
    }


    // =======================================================================
    // VOTE SITES
    // =======================================================================

    private fun loadVoteSites() {
        repo = VoteSitesRepository(this)
        val defaults = repo.defaultSites()

        site1 = defaults[0]
        site2 = defaults[1]
        site3 = defaults[2]
        site4 = defaults[3]
    }

    private fun setupVoteButtons() {
        btnVote1.setOnClickListener { onVoteClick(site1) }
        btnVote2.setOnClickListener { onVoteClick(site2) }
        btnVote3.setOnClickListener { onVoteClick(site3) }
        btnVote4.setOnClickListener { onVoteClick(site4) }

        logoCitadelle.setOnClickListener { openUrl("https://lacitadelle-mc.fr/votes") }
        logoDiscord.setOnClickListener { openUrl("https://discord.gg/h8jr9jkQzk") }
    }

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


    // =======================================================================
    // CUSTOM FONTS
    // =======================================================================

    override fun onResume() {
        super.onResume()
        NotificationHelper.cancelAllVoteReminders(this)
        applyDrawerFont()
        applyCustomFontIfAvailable()
    }

    private fun applyDrawerFont() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val useMedieval = prefs.getBoolean("pref_custom_font", true)

        val menu = rightDrawer.menu
        val medieval = ResourcesCompat.getFont(this, R.font.medievalsharp)
        val defaultFont = Typeface.create("sans-serif-medium", Typeface.NORMAL)

        for (i in 0 until menu.size()) {
            val item = menu.getItem(i)
            val rawTitle = item.title?.toString() ?: continue

            if (item.itemId == R.id.nav_Elytreum) continue

            if (useMedieval && medieval != null) {
                val span = SpannableString(rawTitle)
                span.setSpan(CustomTypefaceSpan(medieval), 0, span.length, Spannable.SPAN_INCLUSIVE_INCLUSIVE)
                item.title = span
            } else {
                item.title = rawTitle
            }
        }

        // Apply font to Elytreum (custom layout)
        val ely = menu.findItem(R.id.nav_Elytreum)
        val action = ely?.actionView ?: return

        fun applyToAllTexts(root: ViewGroup) {
            for (j in 0 until root.childCount) {
                when (val child = root.getChildAt(j)) {
                    is TextView -> child.typeface = if (useMedieval && medieval != null) medieval else defaultFont
                    is ViewGroup -> applyToAllTexts(child)
                }
            }
        }

        when (action) {
            is TextView -> action.typeface = if (useMedieval && medieval != null) medieval else defaultFont
            is ViewGroup -> applyToAllTexts(action)
        }
    }

    private fun applyCustomFontIfAvailable() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val useFont = prefs.getBoolean("pref_custom_font", true)

        val labels = listOf(timerVote1, timerVote2, timerVote3, timerVote4)
        val tf = if (useFont) ResourcesCompat.getFont(this, R.font.medievalsharp) else null

        labels.forEach { it.typeface = tf ?: Typeface.DEFAULT }
    }
    // =======================================================================
    // NOTIFICATIONS (depuis notif)
    // =======================================================================

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


    // =======================================================================
    // TIMERS
    // =======================================================================

    private fun startCountdownUpdates() {
        scope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                try {
                    val next1 = withContext(Dispatchers.IO) { repo.observeNextTrigger(site1.id).first() }
                    val next2 = withContext(Dispatchers.IO) { repo.observeNextTrigger(site2.id).first() }
                    val next3 = withContext(Dispatchers.IO) { repo.observeNextTrigger(site3.id).first() }
                    val next4 = withContext(Dispatchers.IO) { repo.observeNextTrigger(site4.id).first() }

                    renderTimer(timerVote1, now, next1)
                    renderTimer(timerVote2, now, next2)
                    renderTimer(timerVote3, now, next3)
                    renderTimer(timerVote4, now, next4)
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


    // =======================================================================
    // COMPACT LAYOUT (petits écrans)
    // =======================================================================

    private fun applyCompactLayoutIfNeeded() {
        val screenHeightDp = resources.displayMetrics.heightPixels / resources.displayMetrics.density
        if (screenHeightDp >= 650f) return

        // Réduire logo principal
        logoCitadelle.layoutParams = logoCitadelle.layoutParams.apply {
            width = 180.dp()
        }

        // Réduire largeur, padding
        fun shrinkButton(btn: LinearLayout) {
            val lp = btn.layoutParams as ViewGroup.MarginLayoutParams
            lp.width = 220.dp()
            btn.layoutParams = lp
            btn.setPadding(10.dp(), 10.dp(), 10.dp(), 10.dp())
        }

        shrinkButton(btnVote1)
        shrinkButton(btnVote2)
        shrinkButton(btnVote3)
        shrinkButton(btnVote4)

        // Réduire images internes
        fun shrinkInnerImage(parent: LinearLayout) {
            val img = parent.getChildAt(0) as? ImageView ?: return
            img.layoutParams = img.layoutParams.apply {
                width = 90.dp()
            }
        }

        shrinkInnerImage(btnVote1)
        shrinkInnerImage(btnVote2)
        shrinkInnerImage(btnVote3)
        shrinkInnerImage(btnVote4)

        // Réduire timers
        listOf(timerVote1, timerVote2, timerVote3, timerVote4).forEach {
            it.textSize = 12f
        }
    }


    // =======================================================================
    // AUTO-HIDE DISCORD IF SCROLL TOO TALL
    // =======================================================================

    private fun setupDiscordAutoHide() {
        val scrollView = findViewById<ScrollView>(R.id.scrollViewMain)

        scrollView.viewTreeObserver.addOnGlobalLayoutListener(
            object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    scrollView.viewTreeObserver.removeOnGlobalLayoutListener(this)

                    val content = scrollView.getChildAt(0) ?: return
                    val shouldHide = content.height > scrollView.height

                    logoDiscord.visibility = if (shouldHide) View.GONE else View.VISIBLE

                    // ⇨ ajout / suppression dans le Drawer
                    setDiscordDrawerVisibility(shouldHide)
                }
            }
        )
    }




    // =======================================================================
    // UNIFORM BUTTON HEIGHT
    // =======================================================================

    private fun enforceUniformButtonHeight() {
        val buttons = listOf(btnVote1, btnVote2, btnVote3, btnVote4)

        var maxHeight = 0
        buttons.forEach { btn ->
            btn.measure(
                View.MeasureSpec.UNSPECIFIED,
                View.MeasureSpec.UNSPECIFIED
            )
            maxHeight = maxOf(maxHeight, btn.measuredHeight)
        }

        val minHeight = 72.dp()  // ← ton min esthétique
        val heightToApply = maxOf(maxHeight, minHeight)

        buttons.forEach { btn ->
            btn.layoutParams.height = heightToApply
            btn.requestLayout()
        }
    }

    private fun adjustButtonPadding() {
        val buttons = listOf(btnVote1, btnVote2, btnVote3, btnVote4)
        val screenHeightDp = resources.displayMetrics.heightPixels / resources.displayMetrics.density

        val verticalPadding =
            if (screenHeightDp < 650f) 8.dp() else 12.dp()

        buttons.forEach { btn ->
            btn.setPadding(
                btn.paddingLeft,
                verticalPadding,
                btn.paddingRight,
                verticalPadding
            )
        }
    }



    // =======================================================================
    // HELPERS
    // =======================================================================
    private fun setDiscordDrawerVisibility(visible: Boolean) {
        val menu = rightDrawer.menu
        val existing = menu.findItem(ID_NAV_DISCORD)

        if (visible) {
            if (existing == null) {
                // On veut : Paramètres (0), Discord (1), Wiki (2)
                val discordOrder = 1

                menu.add(
                    R.id.group_main,        // même groupe que les autres
                    ID_NAV_DISCORD,         // notre ID dynamique
                    discordOrder,           // position dans le groupe
                    "Discord"
                ).setIcon(R.drawable.ic_discord_nav) // drawable spécial menu (voir point 2)
            }
        } else {
            if (existing != null) {
                menu.removeItem(ID_NAV_DISCORD)
            }
        }

        rightDrawer.invalidate()
    }

    private fun openUrl(url: String) {
        try {
            CustomTabsIntent.Builder().build().launchUrl(this, Uri.parse(url))
        } catch (_: Exception) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

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
