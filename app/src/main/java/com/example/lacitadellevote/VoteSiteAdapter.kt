package com.example.lacitadellevote.ui

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.lacitadellevote.R
import com.example.lacitadellevote.model.VoteSite

class VoteSiteAdapter(
    private val items: MutableList<VoteSiteUi>,
    private val customFont: Typeface? = null,
    private val onVoteClick: (VoteSite) -> Unit
) : RecyclerView.Adapter<VoteSiteAdapter.VoteSiteViewHolder>() {

    data class VoteSiteUi(
        val site: VoteSite,
        var nextTriggerMillis: Long // 0L = prêt à voter
    )

    inner class VoteSiteViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgSite: ImageView = view.findViewById(R.id.imgSite)
        val txtSiteName: TextView = view.findViewById(R.id.txtSiteName)
        val txtCountdown: TextView = view.findViewById(R.id.txtCountdown)
        val btnVote: Button = view.findViewById(R.id.btnVote)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VoteSiteViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_vote_site, parent, false)
        return VoteSiteViewHolder(v)
    }

    override fun onBindViewHolder(holder: VoteSiteViewHolder, position: Int) {
        val item = items[position]

        holder.txtSiteName.text = item.site.name

        if (customFont != null) {
            holder.txtCountdown.typeface = customFont
        }

        holder.txtCountdown.text = buildCountdownText(item.nextTriggerMillis)

        // si tu veux mapper les logos :
        // holder.imgSite.setImageResource(resolveSiteLogo(item.site.id))

        holder.btnVote.setOnClickListener {
            onVoteClick(item.site)
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateSiteTrigger(siteId: String, nextTriggerMillis: Long) {
        val index = items.indexOfFirst { it.site.id == siteId }
        if (index != -1) {
            items[index].nextTriggerMillis = nextTriggerMillis
            notifyItemChanged(index)
        }
    }

    private fun buildCountdownText(nextTrigger: Long): String {
        if (nextTrigger == 0L) return "Prêt à voter"
        val remaining = nextTrigger - System.currentTimeMillis()
        if (remaining <= 0L) return "Prêt à voter"

        val totalSec = remaining / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60

        return if (h > 0) {
            String.format("Dans %02d:%02d:%02d", h, m, s)
        } else {
            String.format("Dans %02d:%02d", m, s)
        }
    }
}
