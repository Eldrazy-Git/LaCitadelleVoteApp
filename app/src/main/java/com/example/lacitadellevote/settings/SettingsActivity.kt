package com.example.lacitadellevote.settings

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import com.example.lacitadellevote.R
import com.example.lacitadellevote.data.VoteSitesRepository
import com.example.lacitadellevote.legal.LegalPageActivity
import com.example.lacitadellevote.notif.NotificationHelper
import com.example.lacitadellevote.work.VoteReminderWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_LaCitadelleVoteApp_Preferences)
        super.onCreate(savedInstanceState)

        // Edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)

        supportFragmentManager
            .beginTransaction()
            .replace(android.R.id.content, SettingsFragment())
            .commit()
    }

    class SettingsFragment : PreferenceFragmentCompat() {

        private val repo by lazy { VoteSitesRepository(requireContext()) }
        private val sites by lazy { repo.defaultSites() }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.settings_preferences, rootKey)

            // ---- Système / Notifications ----
            findPreference<Preference>("pref_open_app_notifications")?.setOnPreferenceClickListener {
                try {
                    val intent =
                        Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                putExtra(
                                    android.provider.Settings.EXTRA_APP_PACKAGE,
                                    requireContext().packageName
                                )
                            } else {
                                putExtra("app_package", requireContext().packageName)
                                putExtra("app_uid", requireContext().applicationInfo.uid)
                            }
                        }
                    startActivity(intent)
                } catch (_: Exception) {
                    Toast.makeText(
                        requireContext(),
                        "Échec d’ouverture des paramètres",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                true
            }

            findPreference<Preference>("pref_battery_optim")?.setOnPreferenceClickListener {
                try {
                    startActivity(Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                } catch (_: Exception) {
                    Toast.makeText(
                        requireContext(),
                        "Échec d’ouverture des paramètres batterie",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                true
            }

            findPreference<Preference>("pref_exact_alarm")?.setOnPreferenceClickListener {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    try {
                        startActivity(Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                    } catch (_: Exception) {
                        Toast.makeText(
                            requireContext(),
                            "Échec d’ouverture des alarmes exactes",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Non requis avant Android 12",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                true
            }

            // --- Test de notification ---
            findPreference<Preference>("pref_test_notification")?.setOnPreferenceClickListener {
                val prefs =
                    PreferenceManager.getDefaultSharedPreferences(requireContext())
                val notificationsEnabled =
                    prefs.getBoolean("pref_notifications_enabled", true)
                if (!notificationsEnabled) {
                    Toast.makeText(
                        requireContext(),
                        "Notifications désactivées dans l’app.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnPreferenceClickListener true
                }

                NotificationHelper.showVoteReminder(
                    context = requireContext(),
                    siteId = "test",
                    siteName = getString(R.string.app_name),
                    url = "https://lacitadelle-mc.fr/votes",
                    cooldownMinutes = 60,
                    notificationId = 9991
                )
                Toast.makeText(
                    requireContext(),
                    "Notification de test affichée.",
                    Toast.LENGTH_SHORT
                ).show()
                true
            }

            // 🔊 Son custom → on régénère le canal
            findPreference<SwitchPreferenceCompat>("pref_custom_sound")
                ?.setOnPreferenceChangeListener { _, _ ->
                    NotificationHelper.ensureChannelForPrefs(requireContext())
                    Toast.makeText(
                        requireContext(),
                        "Préférences son appliquées aux prochaines notifications.",
                        Toast.LENGTH_SHORT
                    ).show()
                    true
                }

            // 📳 Vibration → on régénère le canal
            findPreference<SwitchPreferenceCompat>("pref_vibrate")
                ?.setOnPreferenceChangeListener { _, _ ->
                    NotificationHelper.ensureChannelForPrefs(requireContext())
                    Toast.makeText(
                        requireContext(),
                        "Préférences vibration appliquées aux prochaines notifications.",
                        Toast.LENGTH_SHORT
                    ).show()
                    true
                }

            // Police médiévale
            findPreference<SwitchPreferenceCompat>("pref_custom_font")
                ?.setOnPreferenceChangeListener { _, _ ->
                    activity?.recreate()
                    true
                }

            // ---- Catégorie Votes ----
            setupVotesCategory()

            // ---- Infos & Légal ----
            setupInfoCategory()
        }

        private fun setupVotesCategory() {
            val votesCat = findPreference<PreferenceCategory>("prefcat_votes") ?: return

            // 1) Items par site (clic = reset individuel)
            sites.forEach { site ->
                val p = Preference(requireContext()).apply {
                    key = "pref_vote_site_${site.id}"
                    title = site.name
                    summary = "Chargement…"
                    isIconSpaceReserved = false
                }
                p.setOnPreferenceClickListener {
                    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            // Annule le job WorkManager pour ce site
                            VoteReminderWorker.cancel(requireContext(), site.id)
                            // Nettoie les notifs
                            NotificationHelper.cancelAllVoteReminders(requireContext())
                            // Reset stockage
                            repo.setNextTrigger(site.id, 0L)

                            withContext(Dispatchers.Main) {
                                p.summary = "Prêt à voter"
                                Toast.makeText(
                                    requireContext(),
                                    "« ${site.name} » réinitialisé.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } catch (_: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    requireContext(),
                                    "Erreur: impossible de réinitialiser ${site.name}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                    true
                }
                votesCat.addPreference(p)
            }

            // 2) Reset global
            findPreference<Preference>("pref_vote_reset_all")
                ?.setOnPreferenceClickListener {
                    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            sites.forEach { site ->
                                VoteReminderWorker.cancel(requireContext(), site.id)
                                repo.setNextTrigger(site.id, 0L)
                            }
                            NotificationHelper.cancelAllVoteReminders(requireContext())

                            withContext(Dispatchers.Main) {
                                sites.forEach {
                                    findPreference<Preference>("pref_vote_site_${it.id}")
                                        ?.summary = "Prêt à voter"
                                }
                                Toast.makeText(
                                    requireContext(),
                                    "Tous les timers ont été réinitialisés.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } catch (_: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    requireContext(),
                                    "Erreur lors de la réinitialisation globale.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                    true
                }
        }

        // --- Live updates (optionnel) ---
        private fun startLiveUpdatesLoop() {
            val liveSwitch =
                findPreference<SwitchPreferenceCompat>("pref_vote_live_updates")

            viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    while (true) {
                        if (liveSwitch?.isChecked != false) {
                            val now = System.currentTimeMillis()
                            withContext(Dispatchers.IO) {
                                sites.forEach { site ->
                                    val next = runCatching {
                                        repo.getNextTrigger(site.id)
                                    }.getOrElse {
                                        runCatching {
                                            repo.observeNextTrigger(site.id).first()
                                        }.getOrDefault(0L)
                                    }

                                    val summaryText =
                                        if (next == 0L || next <= now) {
                                            "Prêt à voter"
                                        } else {
                                            val rem = next - now
                                            "Vote dans ${formatHMS(rem)} - Cliquez pour reset -"
                                        }

                                    withContext(Dispatchers.Main) {
                                        findPreference<Preference>("pref_vote_site_${site.id}")
                                            ?.summary = summaryText
                                    }
                                }
                            }
                        }
                        delay(1000L)
                    }
                }
            }
        }

        private fun formatHMS(ms: Long): String {
            val h = TimeUnit.MILLISECONDS.toHours(ms)
            val m = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
            val s = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
            return String.format("%02d:%02d:%02d", h, m, s)
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)

            val rv = view.findViewById<androidx.recyclerview.widget.RecyclerView>(
                androidx.preference.R.id.recycler_view
            )
            rv?.apply {
                // Pas de scrollbars visibles
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false

                // Laisse Android gérer l’overscroll (stretch/bounce sur Android 12+)
                overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            }

            // Insets système
            ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
                val sysBars =
                    insets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.setPadding(
                    v.paddingLeft,
                    sysBars.top,
                    v.paddingRight,
                    sysBars.bottom
                )
                insets
            }
            ViewCompat.requestApplyInsets(view)

            startLiveUpdatesLoop()
        }

        /** Nouvelle section "Infos & Légal" en bas des paramètres. */
        private fun setupInfoCategory() {
            // Mentions légales
            findPreference<Preference>("pref_legal_mentions")
                ?.setOnPreferenceClickListener {
                    openLegalPage(
                        asset = "legal_mentions.html",
                        title = "Mentions légales"
                    )
                    true
                }

            // Politique de confidentialité
            findPreference<Preference>("pref_privacy")
                ?.setOnPreferenceClickListener {
                    openLegalPage(
                        asset = "legal_privacy.html",
                        title = "Politique de confidentialité"
                    )
                    true
                }

            // Licences & crédits
            findPreference<Preference>("pref_licenses")
                ?.setOnPreferenceClickListener {
                    openLegalPage(
                        asset = "legal_licenses.html",
                        title = "Licences & crédits"
                    )
                    true
                }

            // Version de l'application : affiche version + ouvre changelog
            findPreference<Preference>("pref_version")?.let { pref ->
                val ctx = requireContext()
                val pm = ctx.packageManager
                val pkg = ctx.packageName

                val pInfo = try {
                    pm.getPackageInfo(pkg, 0)
                } catch (_: Exception) {
                    null
                }

                val versionName = pInfo?.versionName ?: "1.0.0"
                val versionCode =
                    if (pInfo != null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            pInfo.longVersionCode.toString()
                        } else {
                            @Suppress("DEPRECATION")
                            pInfo.versionCode.toString()
                        }
                    } else {
                        "1"
                    }

                pref.summary = "v$versionName ($versionCode)"

                pref.setOnPreferenceClickListener {
                    // Ouvre le changelog (local + remote GitHub)
                    openLegalPage(
                        asset = "legal_changelog.html",
                        title = "Journal des versions",
                    )
                    true
                }
            }
        }

        private fun openLegalPage(asset: String, title: String) {
            val ctx = requireContext()
            val intent = Intent(ctx, LegalPageActivity::class.java).apply {
                putExtra("asset", asset)
                putExtra("title", title)
            }
            startActivity(intent)
        }

    }
}
