package fr.lacitadelle.votecompagnon.settings

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import androidx.preference.SwitchPreferenceCompat
import java.lang.ref.WeakReference

class DimmableSwitchPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SwitchPreferenceCompat(context, attrs) {

    private val dimAlpha = 0.2f
    private val brightAlpha = 1f
    private val animDuration = 180L

    // On garde une réf faible vers les vues bindées pour animer sans fuite mémoire
    private var titleRef: WeakReference<TextView>? = null
    private var summaryRef: WeakReference<TextView>? = null

    init {
        // Anime quand la valeur change (clic utilisateur ou changement programmatique)
        onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
            val targetChecked = (newValue as? Boolean) ?: return@OnPreferenceChangeListener true
            animateDim(targetChecked)
            true // autoriser le changement
        }
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        // Récupère les TextView du layout (custom ou défaut)
        val title = holder.findViewById(android.R.id.title) as? TextView
        val summary = holder.findViewById(android.R.id.summary) as? TextView

        titleRef = WeakReference(title)
        summaryRef = WeakReference(summary)

        // État initial (sans animation au premier bind)
        val a = if (isChecked) brightAlpha else dimAlpha
        title?.alpha = a
        summary?.alpha = a
    }

    private fun animateDim(checked: Boolean) {
        val start = titleRef?.get()?.alpha ?: if (checked) dimAlpha else brightAlpha
        val end = if (checked) brightAlpha else dimAlpha

        if (start == end) return

        val animator = ValueAnimator.ofFloat(start, end).apply {
            duration = animDuration
            addUpdateListener { va ->
                val v = va.animatedValue as Float
                titleRef?.get()?.alpha = v
                summaryRef?.get()?.alpha = v
            }
        }
        animator.start()
    }

    override fun onDetached() {
        super.onDetached()
        titleRef = null
        summaryRef = null
    }
}
