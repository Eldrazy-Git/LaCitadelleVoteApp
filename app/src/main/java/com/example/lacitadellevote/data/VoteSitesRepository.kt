package com.example.lacitadellevote.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.lacitadellevote.model.VoteSite
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first

private val Context.dataStore by preferencesDataStore("vote_prefs")

class VoteSitesRepository(private val context: Context) {

    private fun nextKey(siteId: String) = longPreferencesKey("next_ts_$siteId")

    fun defaultSites(): List<VoteSite> = listOf(
        VoteSite("spn", "serveur-prive.net", "https://serveur-prive.net/minecraft/lacitadelle/vote", 90),
        VoteSite("smc", "serveur-minecraft.com", "https://serveur-minecraft.com/4043", 180),
        VoteSite("smo", "serveursminecraft.org", "https://www.serveursminecraft.org/serveur/7089/", 1440),
		VoteSite("smv", "serveur-minecraft-vote.fr", "https://serveur-minecraft-vote.fr/serveurs/la-citadelle.2168/vote", 90)
    )

    fun observeNextTrigger(siteId: String): Flow<Long> =
        context.dataStore.data.map { it[nextKey(siteId)] ?: 0L }

    suspend fun setNextTrigger(siteId: String, epochMillis: Long) {
        context.dataStore.edit { it[nextKey(siteId)] = epochMillis }
    }

    suspend fun clearAllNextTriggers() {
        context.dataStore.edit { prefs ->
            defaultSites().forEach { prefs.remove(nextKey(it.id)) }
        }
    }

    suspend fun getNextTrigger(siteId: String): Long {
        return context.dataStore.data.first()[nextKey(siteId)] ?: 0L
    }
}
