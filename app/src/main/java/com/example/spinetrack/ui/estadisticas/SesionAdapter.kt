package com.example.spinetrack.ui.estadisticas

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.navigation.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.spinetrack.R
import com.example.spinetrack.data.model.SesionPostural
import com.example.spinetrack.databinding.ItemSesionBinding
import com.google.gson.Gson

class SesionAdapter : ListAdapter<SesionPostural, SesionAdapter.ViewHolder>(DiffCallback) {

    inner class ViewHolder(private val binding: ItemSesionBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(sesion: SesionPostural) {
            binding.tvFecha.text = formatearFecha(sesion.tsInicio)
            binding.tvDuracion.text = String.format("%.0f min", sesion.duracionMin)
            binding.tvIcp.text = String.format("%.1f", sesion.icp)
            binding.tvClaseIcp.text = sesion.claseIcp
            binding.tvIcp.setTextColor(colorIcp(sesion.icp))
            binding.tvAlertas.text = "${sesion.numAlertas} alertas"
            binding.progressBuena.progress = sesion.pctBuena.toInt()

            // ← Navegar al detalle serializando la sesión como JSON
            binding.root.setOnClickListener {
                val json = Gson().toJson(sesion)
                val bundle = Bundle().apply {
                    putString("sesion_json", json)
                }
                it.findNavController().navigate(R.id.detalleSesionFragment, bundle)
            }
        }

        private fun formatearFecha(ts: String): String {
            if (ts.length < 10) return ts
            val partes = ts.take(10).split("-")
            if (partes.size < 3) return ts
            val meses = listOf("", "Ene", "Feb", "Mar", "Abr", "May", "Jun",
                "Jul", "Ago", "Sep", "Oct", "Nov", "Dic")
            val mes = partes[1].toIntOrNull() ?: return ts
            return "${partes[2]} ${meses.getOrElse(mes) { partes[1] }} ${partes[0]}"
        }

        private fun colorIcp(icp: Double) = when {
            icp >= 75 -> Color.parseColor("#4CAF50")
            icp >= 50 -> Color.parseColor("#FFC107")
            else      -> Color.parseColor("#F44336")
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSesionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object DiffCallback : DiffUtil.ItemCallback<SesionPostural>() {
        override fun areItemsTheSame(a: SesionPostural, b: SesionPostural) =
            a.sessionId == b.sessionId
        override fun areContentsTheSame(a: SesionPostural, b: SesionPostural) = a == b
    }
}