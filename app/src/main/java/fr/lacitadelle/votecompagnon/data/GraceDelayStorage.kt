package fr.lacitadelle.votecompagnon.data

import android.content.Context

/**
 * Stocke le décalage du cooldown (en secondes) dans des SharedPreferences.
 * Exemple :
 *   10  => +10 secondes
 *   0   => pas de délai
 */
object GraceDelayStorage {

    private const val PREFS_NAME = "vote_prefs"
    private const val KEY_GRACE_SECONDS = "grace_delay_seconds"
    private const val DEFAULT_SECONDS = 10  // valeur par défaut

    /** Retourne le décalage en millisecondes (0 à 60s). */
    fun getGraceMillis(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val seconds = prefs.getInt(KEY_GRACE_SECONDS, DEFAULT_SECONDS)
        return seconds.coerceIn(0, 60).toLong() * 1000L
    }

    /** Sauvegarde un nouveau décalage (en secondes, clampé 0–60). */
    fun setGraceSeconds(context: Context, seconds: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(KEY_GRACE_SECONDS, seconds.coerceIn(0, 60))
            .apply()
    }
}
