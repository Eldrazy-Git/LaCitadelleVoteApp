package fr.lacitadelle.votecompagnon.settings

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.content.ContextCompat
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.animation.DecelerateInterpolator
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
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
import com.google.android.material.textfield.TextInputEditText
import fr.lacitadelle.votecompagnon.R
import fr.lacitadelle.votecompagnon.data.GraceDelayStorage
import fr.lacitadelle.votecompagnon.alarm.ExactAlarmPermission
import fr.lacitadelle.votecompagnon.alarm.VoteAlarmReceiver
import fr.lacitadelle.votecompagnon.data.VoteSitesRepository
import fr.lacitadelle.votecompagnon.legal.LegalPageActivity
import fr.lacitadelle.votecompagnon.notif.NotificationHelper
import fr.lacitadelle.votecompagnon.work.VoteReminderWorker
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

        WindowCompat.setDecorFitsSystemWindows(window, false)

        supportFragmentManager
            .beginTransaction()
            .replace(android.R.id.content, SettingsFragment())
            .commit()

    }

    class SettingsFragment : PreferenceFragmentCompat() {

        private val DOC_URL = "https://la-citadelle-vote-compagnon.gitbook.io/docs/"

        private val repo by lazy { VoteSitesRepository(requireContext()) }
        private val sites by lazy { repo.defaultSites() }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.settings_preferences, rootKey)

            // --- Bouton Documentation GitBook ---
            findPreference<Preference>("pref_documentation")
                ?.setOnPreferenceClickListener {
                    openDocumentation()
                    true
                }

            // ---- Système / Notifications ----
            // Paramètres de notifications de l'application
            findPreference<Preference>("pref_open_app_notifications")
                ?.setOnPreferenceClickListener {
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
                                    putExtra(
                                        "app_uid",
                                        requireContext().applicationInfo.uid
                                    )
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

            // --- Décalage cooldown (grace delay) ---
            findPreference<Preference>("pref_grace_delay_seconds")?.let { pref ->

                fun updateSummary() {
                    val sec = (GraceDelayStorage.getGraceMillis(requireContext()) / 1000L).toInt()
                    pref.summary = "Décalage actuel : ${sec}s"
                }

                updateSummary()

                pref.setOnPreferenceClickListener {
                    val ctx = requireContext()
                    val currentSec = (GraceDelayStorage.getGraceMillis(ctx) / 1000L).toInt()

                    val dialogView = LayoutInflater.from(ctx)
                        .inflate(R.layout.dialog_grace_delay, null)

                    val editText = dialogView.findViewById<TextInputEditText>(R.id.editGraceDelay)
                    val btnCancel = dialogView.findViewById<Button>(R.id.btnCancel)
                    val btnOk = dialogView.findViewById<Button>(R.id.btnOk)

                    editText.setText(currentSec.toString())
                    editText.setSelection(editText.text?.length ?: 0)
                    editText.inputType = InputType.TYPE_CLASS_NUMBER

                    val dialog = AlertDialog.Builder(ctx)
                        .setView(dialogView)
                        .create()

                    dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

                    btnCancel.setOnClickListener {
                        dialog.dismiss()
                    }

                    btnOk.setOnClickListener {
                        val sec = editText.text?.toString()?.toIntOrNull() ?: 0
                        GraceDelayStorage.setGraceSeconds(ctx, sec)
                        updateSummary()
                        dialog.dismiss()
                    }

                // ✨ Animation de scale + fade-in
                    dialog.setOnShowListener {
                        dialogView.scaleX = 0.8f
                        dialogView.scaleY = 0.8f
                        dialogView.alpha = 0f

                        dialogView.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .alpha(1f)
                            .setDuration(200L)
                            .setInterpolator(DecelerateInterpolator())
                            .start()
                    }

                    dialog.show()
                    true
                }
            }

            // Batterie / optimisation
            val batteryPref = findPreference<Preference>("pref_battery_optim")
            batteryPref?.let { pref ->
                val ignoring = isIgnoringBatteryOptimizations()
                pref.summary = if (ignoring) {
                    "L'application n'est pas optimisée pour la batterie. Les rappels devraient rester fiables."
                } else {
                    "Recommandé pour que les rappels fonctionnent même si l'application est fermée."
                }

                pref.setOnPreferenceClickListener {
                    if (ignoring) {
                        Toast.makeText(
                            requireContext(),
                            "L'application est déjà exclue de l’optimisation batterie.",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        openBatteryOptimizationRequest()
                    }
                    true
                }
            }

            // Alarmes exactes
            val exactPref = findPreference<Preference>("pref_exact_alarm")
            exactPref?.let { pref ->
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    // Avant Android 12 : inutile
                    pref.isVisible = false
                } else {
                    val hasExact = ExactAlarmPermission.canScheduleExact(requireContext())
                    pref.summary = if (hasExact) {
                        "Les alarmes exactes sont déjà autorisées."
                    } else {
                        "Recommandé pour une précision maximale des rappels de vote."
                    }

                    pref.setOnPreferenceClickListener {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            try {
                                startActivity(
                                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                )
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
                }
            }

            // --- Test de notification ---
            findPreference<Preference>("pref_test_notification")
                ?.setOnPreferenceClickListener {
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

            // --- Son & vibration : on régénère le canal ---
            findPreference<SwitchPreferenceCompat>("pref_custom_sound")
                ?.setOnPreferenceChangeListener { _, _ ->
                    NotificationHelper.ensureChannelForPrefs(requireContext())
                    Toast.makeText(
                        requireContext(),
                        "Son appliqué aux prochaines notifications.",
                        Toast.LENGTH_SHORT
                    ).show()
                    true
                }

            findPreference<SwitchPreferenceCompat>("pref_vibrate")
                ?.setOnPreferenceChangeListener { _, _ ->
                    NotificationHelper.ensureChannelForPrefs(requireContext())
                    Toast.makeText(
                        requireContext(),
                        "Vibration appliquée aux prochaines notifications.",
                        Toast.LENGTH_SHORT
                    ).show()
                    true
                }

            findPreference<SwitchPreferenceCompat>("pref_custom_font")
                ?.setOnPreferenceChangeListener { _, _ ->
                    activity?.recreate()
                    true
                }

            // ---- Votes ----
            setupVotesCategory()

            // ---- Infos & Légal ----
            setupInfoCategory()
        }

        /**
         * L’app est-elle déjà exclue de l’optimisation batterie ?
         */
        private fun isIgnoringBatteryOptimizations(): Boolean {
            val ctx = requireContext()
            val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager
                ?: return false
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                pm.isIgnoringBatteryOptimizations(ctx.packageName)
            } else {
                // Avant Doze : pas d’optimisation agressive
                true
            }
        }

        /**
         * Ouvre la popup système pour demander l’exclusion de l’optimisation batterie.
         */
        private fun openBatteryOptimizationRequest() {
            try {
                val ctx = requireContext()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    // Ouvre la liste des applis ignorées par l'optimisation batterie
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    startActivity(intent)
                } else {
                    Toast.makeText(
                        ctx,
                        "Non requis avant Android 6 (Marshmallow).",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    "Échec d’ouverture des paramètres batterie",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        private fun openDocumentation() {
            val url = DOC_URL

            // Tente d’ouvrir en Custom Chrome Tab (comme le reste de l’app)
            try {
                val customTabsIntent = CustomTabsIntent.Builder().build()
                customTabsIntent.launchUrl(requireContext(), Uri.parse(url))
            } catch (e: Exception) {
                // Fallback : ouvre dans le navigateur classique
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                startActivity(intent)
            }
        }

        /**
         * Réinitialisation des timers et affichage du temps restant.
         */
        private fun setupVotesCategory() {
            val votesCat =
                findPreference<PreferenceCategory>("prefcat_votes") ?: return

            // 1) Reset individuel par site
            sites.forEach { site ->
                val pref = Preference(requireContext()).apply {
                    key = "pref_vote_site_${site.id}"
                    title = site.name
                    summary = "Chargement…"
                    isIconSpaceReserved = false
                }

                pref.setOnPreferenceClickListener {
                    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            // Annule WorkManager pour ce site
                            VoteReminderWorker.cancel(requireContext(), site.id)
                            // Annule l’alarme exacte pour ce site (si programmée)
                            cancelExactAlarmForSite(site.id)
                            // Supprime la notif de ce site (si affichée)
                            NotificationHelper.cancelVoteReminderForSite(
                                requireContext(),
                                site.id
                            )
                            // Reset stockage
                            repo.setNextTrigger(site.id, 0L)

                            withContext(Dispatchers.Main) {
                                pref.summary = "Prêt à voter"
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
                                    "Erreur lors de la réinitialisation de ${site.name}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                    true
                }

                votesCat.addPreference(pref)
            }

            // 2) Reset global : tous les sites
            findPreference<Preference>("pref_vote_reset_all")
                ?.setOnPreferenceClickListener {
                    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            sites.forEach { site ->
                                VoteReminderWorker.cancel(requireContext(), site.id)
                                cancelExactAlarmForSite(site.id)
                                repo.setNextTrigger(site.id, 0L)
                            }

                            // Efface toutes les notifications de vote
                            NotificationHelper.cancelAllVoteReminders(requireContext())

                            withContext(Dispatchers.Main) {
                                sites.forEach { site ->
                                    findPreference<Preference>("pref_vote_site_${site.id}")
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

            // 3) Boucle live pour afficher le temps restant
        }

        /**
         * Annule l’alarme exacte associée à un site.
         * Doit matcher le PendingIntent utilisé dans ton scheduler.
         */
        private fun cancelExactAlarmForSite(siteId: String) {
            val ctx = requireContext()
            val am =
                ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

            val intent = Intent(ctx, VoteAlarmReceiver::class.java).apply {
                action = "VOTE_REMINDER_$siteId"
            }

            val pi = PendingIntent.getBroadcast(
                ctx,
                siteId.hashCode(),
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )

            if (pi != null) {
                am.cancel(pi)
                pi.cancel()
            }
        }

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

                                    val summary =
                                        if (next == 0L || next <= now) {
                                            "Prêt à voter"
                                        } else {
                                            val remaining = next - now
                                            "Vote dans ${formatHMS(remaining)} - Cliquez pour reset -"
                                        }

                                    withContext(Dispatchers.Main) {
                                        findPreference<Preference>("pref_vote_site_${site.id}")
                                            ?.summary = summary
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

            val rv =
                view.findViewById<androidx.recyclerview.widget.RecyclerView>(
                    androidx.preference.R.id.recycler_view
                )
            rv?.apply {
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            }

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

            // Version de l'application → ouvre changelog
            findPreference<Preference>("pref_version")?.let { pref ->
                val ctx = requireContext()
                val pm = ctx.packageManager
                val pkg = ctx.packageName

                val pInfo = runCatching { pm.getPackageInfo(pkg, 0) }.getOrNull()
                val versionName = pInfo?.versionName ?: "1.0.0"
                val versionCode =
                    if (pInfo != null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            pInfo.longVersionCode.toString()
                        } else {
                            @Suppress("DEPRECATION")
                            pInfo.versionCode.toString()
                        }
                    } else "1"

                pref.summary = "v$versionName ($versionCode)"
                pref.setOnPreferenceClickListener {
                    openLegalPage(
                        asset = "legal_changelog.html",
                        title = "Journal des versions"
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
