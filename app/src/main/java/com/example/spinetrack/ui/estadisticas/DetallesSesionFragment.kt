package com.example.spinetrack.ui.estadisticas

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.spinetrack.data.model.EventoPostural
import com.example.spinetrack.data.model.SesionPostural
import com.example.spinetrack.databinding.FragmentDetalleSesionBinding
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException

class DetalleSesionFragment : Fragment() {

    private var _binding: FragmentDetalleSesionBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDetalleSesionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnVolver.setOnClickListener { findNavController().popBackStack() }
        binding.btnErrorDetalleVolver.setOnClickListener { findNavController().popBackStack() }

        // Recibir la sesión serializada desde el argumento de forma segura
        val json = arguments?.getString("sesion_json")
        if (json.isNullOrBlank()) {
            mostrarErrorDetalle("No se encontro la sesion seleccionada.")
            return
        }

        val sesion = try {
            Gson().fromJson(json, SesionPostural::class.java)
        } catch (_: JsonSyntaxException) {
            null
        } catch (_: Exception) {
            null
        }

        if (sesion == null) {
            mostrarErrorDetalle("No se pudo leer la sesion. Intenta abrir otra.")
            return
        }

        poblarDetalle(sesion)
        poblarTimeline(sesion.eventos)
    }

    private fun mostrarErrorDetalle(mensaje: String) {
        binding.root.findViewById<View>(com.example.spinetrack.R.id.layout_detalle_data)?.visibility = View.GONE
        binding.layoutErrorDetalle.visibility = View.VISIBLE
        binding.tvErrorDetalle.text = mensaje
    }

    private fun poblarDetalle(s: SesionPostural) {
        binding.tvFechaDetalle.text = formatearFecha(s.tsInicio)
        binding.tvHoraDetalle.text = "${s.tsInicio.take(19).substring(11)} — ${s.tsFin.take(19).substring(11)}"

        binding.tvIcpDetalle.text = String.format("%.1f", s.icp)
        binding.tvIcpDetalle.setTextColor(colorIcp(s.icp))
        binding.tvClaseIcpDetalle.text = s.claseIcp

        binding.tvDuracionDetalle.text = String.format("%.0f min", s.duracionMin)
        binding.tvAlertasDetalle.text = s.numAlertas.toString()
        binding.tvCorreccionesDetalle.text = s.numCorrecciones.toString()
        binding.tvThetaDetalle.text = String.format("%.2f°", s.thetaPromedio)
        binding.tvTempDetalle.text = s.tempPromC?.let { String.format("%.1f °C", it) } ?: "—"

        // Barra buena / mala postura
        binding.progressBuenaDetalle.progress = s.pctBuena.toInt()
        binding.tvPctBuena.text = String.format("%.1f%% buena", s.pctBuena)
        binding.tvPctMala.text = String.format("%.1f%% mala", s.pctMala)

        // Distribución de ángulos
        val orden = listOf("Excelente", "Bueno", "Regular", "Malo", "Peligroso")
        val dist = orden.joinToString("  •  ") { clave ->
            val pct = s.distAnguloPct[clave] ?: 0.0
            "$clave: ${String.format("%.0f", pct)}%"
        }
        binding.tvDistribucion.text = dist
    }

    private fun poblarTimeline(eventos: List<EventoPostural>) {
        if (eventos.isEmpty()) {
            binding.tvSinEventos.visibility = View.VISIBLE
            binding.layoutTimeline.visibility = View.GONE
            return
        }

        binding.tvSinEventos.visibility = View.GONE
        binding.layoutTimeline.visibility = View.VISIBLE
        binding.layoutTimeline.removeAllViews()

        eventos.forEach { evento ->
            val item = LayoutInflater.from(requireContext())
                .inflate(com.example.spinetrack.R.layout.item_evento_timeline,
                    binding.layoutTimeline, false)

            val tvTipo = item.findViewById<android.widget.TextView>(
                com.example.spinetrack.R.id.tv_evento_tipo)
            val tvTiempo = item.findViewById<android.widget.TextView>(
                com.example.spinetrack.R.id.tv_evento_tiempo)
            val tvTheta = item.findViewById<android.widget.TextView>(
                com.example.spinetrack.R.id.tv_evento_theta)

            tvTipo.text = if (evento.esAlerta) "⚠️ ALERTA" else "✅ CORRECCIÓN"
            tvTipo.setTextColor(if (evento.esAlerta)
                Color.parseColor("#FF5722") else Color.parseColor("#4CAF50"))

            val minutos = (evento.tsRelS / 60).toInt()
            val segundos = (evento.tsRelS % 60).toInt()
            tvTiempo.text = String.format("min %d:%02d", minutos, segundos)
            tvTheta.text = String.format("θ = %.2f°", evento.thetaAbs)

            binding.layoutTimeline.addView(item)
        }
    }

    private fun formatearFecha(ts: String): String {
        if (ts.length < 10) return ts
        val partes = ts.take(10).split("-")
        if (partes.size < 3) return ts
        val meses = listOf("", "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
            "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre")
        val mes = partes[1].toIntOrNull() ?: return ts
        return "${partes[2]} de ${meses.getOrElse(mes) { partes[1] }} de ${partes[0]}"
    }

    private fun colorIcp(icp: Double) = when {
        icp >= 75 -> Color.parseColor("#4CAF50")
        icp >= 50 -> Color.parseColor("#FFC107")
        else      -> Color.parseColor("#F44336")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}