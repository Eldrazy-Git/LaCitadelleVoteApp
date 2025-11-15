// MyApplication.kt
package fr.lacitadelle.votecompagnon

import android.app.Application
import fr.lacitadelle.votecompagnon.notif.NotificationHelper

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Crée le canal au démarrage (Android O+)
        NotificationHelper.ensureChannelForPrefs(this)

    }
}
