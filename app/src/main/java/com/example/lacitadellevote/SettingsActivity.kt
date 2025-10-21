package com.example.lacitadellevote

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.lacitadellevote.alarm.VoteScheduler
import com.example.lacitadellevote.data.VoteSitesRepository
import com.example.lacitadellevote.databinding.ActivitySettingsBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val repo = VoteSitesRepository(this)

        binding.btnTestNotif.setOnClickListener {
            CoroutineScope(Dispatchers.IO).launch {
                repo.defaultSites().forEach {
                    VoteScheduler.scheduleNext(this@SettingsActivity, it, delayMinutes = 0)
                }
            }
            Toast.makeText(this, "Notifications de test en chemin…", Toast.LENGTH_SHORT).show()
        }

        binding.btnResetCooldowns.setOnClickListener {
            CoroutineScope(Dispatchers.IO).launch {
                repo.clearAllNextTriggers()
                repo.defaultSites().forEach { VoteScheduler.scheduleNext(this@SettingsActivity, it, 1) }
            }
            Toast.makeText(this, "Cooldowns réinitialisés.", Toast.LENGTH_SHORT).show()
        }
    }
}
