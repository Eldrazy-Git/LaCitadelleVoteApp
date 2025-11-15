package fr.lacitadelle.votecompagnon.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import fr.lacitadelle.votecompagnon.model.VoteSite
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// DataStore unique pour les votes
private val Context.voteDataStore by preferencesDataStore(name = "vote_prefs")

class VoteSitesRepository(private val context: Context) {

    /**
     * Sites par défaut de l’app, dans l’ordre que tu utilises dans MainActivity :
     * 1) spn  → 90 min
     * 2) smv  → 90 min
     * 3) smc  → 180 min
     * 4) smo  → 1440 min
     *
     * NB : on utilise bien com.example.lacitadellevote.model.VoteSite
     */
    fun defaultSites(): List<VoteSite> = listOf(
        VoteSite(
            id = "spn",
            name = "Serveur-prive.net",
            url = "https://serveur-prive.net/minecraft/lacitadelle/vote",
            cooldownMinutes = 90
        ),
        VoteSite(
            id = "smv",
            name = "Serveur-minecraft-vote.fr",
            url = "https://serveur-minecraft-vote.fr/serveurs/la-citadelle.2168/vote",
            cooldownMinutes = 90
        ),
        VoteSite(
            id = "smc",
            name = "Serveur-minecraft.com",
            url = "https://serveur-minecraft.com/4043",
            cooldownMinutes = 180
        ),
        VoteSite(
            id = "smo",
            name = "ServeursMinecraft.org",
            url = "https://www.serveursminecraft.org/serveur/7089/",
            cooldownMinutes = 1440
        )
    )

    private fun keyFor(siteId: String): Preferences.Key<Long> =
        longPreferencesKey("next_ts_$siteId")

    /**
     * Pour l’affichage en live dans MainActivity (Flow)
     */
    fun observeNextTrigger(siteId: String): Flow<Long> =
        context.voteDataStore.data.map { prefs ->
            prefs[keyFor(siteId)] ?: 0L
        }

    /**
     * Pour SettingsActivity (lecture one-shot)
     */
    suspend fun getNextTrigger(siteId: String): Long {
        val prefs = context.voteDataStore.data.first()
        return prefs[keyFor(siteId)] ?: 0L
    }

    /**
     * Persister un prochain déclenchement
     */
    suspend fun setNextTrigger(siteId: String, triggerAtMillis: Long) {
        context.voteDataStore.edit { prefs ->
            prefs[keyFor(siteId)] = triggerAtMillis
        }
    }

    /**
     * Vider un seul site
     */
    suspend fun clearNextTrigger(siteId: String) {
        context.voteDataStore.edit { prefs ->
            prefs.remove(keyFor(siteId))
        }
    }

    /**
     * Vider TOUS les sites (bouton “Tout réinitialiser” des paramètres)
     */
    suspend fun clearAllNextTriggers() {
        context.voteDataStore.edit { prefs ->
            defaultSites().forEach { site ->
                prefs.remove(keyFor(site.id))
            }
        }
    }
}
