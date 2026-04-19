package com.example.spinetrack.ui.lecciones

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.spinetrack.R
import com.example.spinetrack.data.model.Leccion
import com.example.spinetrack.data.model.NivelLeccion

class LeccionesAdapter(
    private val onItemClick: (Leccion) -> Unit
) : ListAdapter<Leccion, LeccionesAdapter.LeccionViewHolder>(LeccionDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LeccionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_leccion, parent, false)
        return LeccionViewHolder(view)
    }

    override fun onBindViewHolder(holder: LeccionViewHolder, position: Int) {
        holder.bind(getItem(position), onItemClick)
    }

    class LeccionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val tvNumber    = itemView.findViewById<TextView>(R.id.tv_lesson_number)
        private val tvTitle     = itemView.findViewById<TextView>(R.id.tv_lesson_title)
        private val tvDesc      = itemView.findViewById<TextView>(R.id.tv_lesson_desc)
        private val tvLevel     = itemView.findViewById<TextView>(R.id.tv_level_badge)
        private val tvDuration  = itemView.findViewById<TextView>(R.id.tv_duration)
        private val tvPoints    = itemView.findViewById<TextView>(R.id.tv_points)
        private val ivCompleted = itemView.findViewById<ImageView>(R.id.iv_completed)

        fun bind(leccion: Leccion, onItemClick: (Leccion) -> Unit) {
            val ctx = itemView.context

            tvNumber.text   = leccion.id.toString().takeLast(2)
            tvTitle.text    = leccion.titulo
            tvDesc.text     = leccion.descripcion
            tvLevel.text    = leccion.nivel.label
            tvDuration.text = "⏱ ${leccion.duracionMin} min"
            tvPoints.text   = "⭐ +${leccion.puntos}"

            when (leccion.nivel) {
                NivelLeccion.PRINCIPIANTE -> {
                    tvLevel.setTextColor(ContextCompat.getColor(ctx, R.color.good_green))
                    tvLevel.setBackgroundResource(R.drawable.bg_badge_beginner)
                }
                NivelLeccion.INTERMEDIO -> {
                    tvLevel.setTextColor(ContextCompat.getColor(ctx, R.color.warning_yellow))
                    tvLevel.setBackgroundResource(R.drawable.bg_badge_intermediate)
                }
                NivelLeccion.AVANZADO -> {
                    tvLevel.setTextColor(ContextCompat.getColor(ctx, R.color.pink_primary))
                    tvLevel.setBackgroundResource(R.drawable.bg_badge_advanced)
                }
            }

            ivCompleted.visibility = if (leccion.completada) View.VISIBLE else View.GONE
            itemView.alpha = if (leccion.completada) 0.7f else 1.0f
            itemView.setOnClickListener { onItemClick(leccion) }
        }
    }

    class LeccionDiffCallback : DiffUtil.ItemCallback<Leccion>() {
        override fun areItemsTheSame(old: Leccion, new: Leccion) = old.id == new.id
        override fun areContentsTheSame(old: Leccion, new: Leccion) = old == new
    }
}