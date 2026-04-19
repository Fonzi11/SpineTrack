package com.example.spinetrack.ui.ranking

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.spinetrack.R
import com.example.spinetrack.data.model.RankingUser

class RankingAdapter(
    private val scoreGetter: (RankingUser) -> Int,
    private val subtitleGetter: (RankingUser) -> String
) : ListAdapter<RankingUser, RankingAdapter.RankingViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RankingViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ranking, parent, false)
        return RankingViewHolder(view)
    }

    override fun onBindViewHolder(holder: RankingViewHolder, position: Int) {
        holder.bind(position + 1, getItem(position), scoreGetter, subtitleGetter)
    }

    class RankingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvPosition = itemView.findViewById<TextView>(R.id.tv_position)
        private val tvAvatar   = itemView.findViewById<TextView>(R.id.tv_avatar)
        private val tvName     = itemView.findViewById<TextView>(R.id.tv_user_name)
        private val tvSub      = itemView.findViewById<TextView>(R.id.tv_user_subtitle)
        private val tvScore    = itemView.findViewById<TextView>(R.id.tv_score)
        private val card       = itemView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.card_ranking_item)

        fun bind(
            position: Int,
            user: RankingUser,
            scoreGetter: (RankingUser) -> Int,
            subtitleGetter: (RankingUser) -> String
        ) {
            val ctx = itemView.context

            tvPosition.text = position.toString()
            tvAvatar.text   = user.nombre.first().uppercase()
            tvName.text     = user.nombre
            tvSub.text      = subtitleGetter(user)
            tvScore.text    = scoreGetter(user).toString()

            // Color del círculo según posición
            val bgRes = when (position) {
                1 -> R.drawable.bg_position_gold
                2 -> R.drawable.bg_position_silver
                3 -> R.drawable.bg_position_bronze
                else -> R.drawable.bg_position_circle
            }
            tvPosition.setBackgroundResource(bgRes)

            // Color texto posición top 3
            val textColor = when (position) {
                1, 2, 3 -> ContextCompat.getColor(ctx, R.color.text_dark)
                else    -> ContextCompat.getColor(ctx, R.color.peach_dark)
            }
            tvPosition.setTextColor(textColor)

            // Resaltar fila del usuario actual (id == 1 como mock)
            if (user.id == 1) {
                card.setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.peach_container))
            } else {
                card.setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.surface_white))
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<RankingUser>() {
        override fun areItemsTheSame(old: RankingUser, new: RankingUser) = old.id == new.id
        override fun areContentsTheSame(old: RankingUser, new: RankingUser) = old == new
    }
}