package com.example.spinetrack.ui.estadisticas

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.example.spinetrack.R
import com.example.spinetrack.data.model.SesionPostural
import com.example.spinetrack.databinding.FragmentEstadisticasBinding
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import kotlinx.coroutines.launch

class EstadisticasFragment : Fragment() {

    private var _binding: FragmentEstadisticasBinding? = null
    private val binding get() = _binding!!
    private val viewModel: EstadisticasViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEstadisticasBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupCharts()
        observeState()

        binding.btnRefrescar.setOnClickListener {
            viewModel.cargarEstadisticas()
        }

        binding.btnReintentarConexion.setOnClickListener {
            viewModel.cargarEstadisticas()
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                binding.progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE

                when {
                    state.error != null -> {
                        mostrarEstadoError(
                            mensaje = state.error
                        )
                    }

                    state.isDisconnected -> {
                        mostrarEstadoError(
                            mensaje = "Inicia sesion y verifica internet para cargar tus sesiones."
                        )
                    }

                    !state.isLoading && state.sesiones.isEmpty() -> {
                        binding.layoutDesconexion.visibility = View.GONE
                        binding.layoutContenido.visibility = View.GONE
                        binding.layoutVacio.visibility = View.VISIBLE
                    }

                    else -> {
                        binding.layoutDesconexion.visibility = View.GONE
                        binding.layoutVacio.visibility = View.GONE
                        if (!state.isLoading) {
                            actualizarTarjetas(state)
                            actualizarGraficaIcp(state.sesiones.take(10).reversed())
                            actualizarGraficaPie(state.distAnguloPct)
                        }
                        binding.layoutContenido.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    private fun mostrarEstadoError(mensaje: String) {
        binding.layoutDesconexion.visibility = View.VISIBLE
        binding.layoutContenido.visibility = View.GONE
        binding.layoutVacio.visibility = View.GONE
        binding.tvMensajeDesconexion.text = mensaje
    }

    private fun actualizarTarjetas(state: EstadisticasUiState) {
        // ICP promedio con color según clasificación
        binding.tvIcpPromedio.text = String.format("%.1f", state.icpPromedio)
        binding.tvClaseIcp.text = clasificarIcp(state.icpPromedio)
        binding.tvIcpPromedio.setTextColor(colorIcp(state.icpPromedio))

        // Indicadores rápidos
        binding.tvRacha.text = "${state.rachaActual} días"
        binding.tvTotalSesiones.text = "${state.totalSesiones}"
        val horas = state.tiempoTotalMin / 60.0
        binding.tvTiempoTotal.text = if (horas >= 1)
            String.format("%.1f h", horas)
        else
            String.format("%.0f min", state.tiempoTotalMin)
    }

    // ── Gráfica de línea: ICP por sesión ─────────────────────────────────────

    private fun setupCharts() {
        with(binding.chartIcp) {
            description.isEnabled = false
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(false)
            legend.isEnabled = false
            setNoDataText("Sin sesiones registradas")
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.granularity = 1f
            xAxis.setDrawGridLines(false)
            axisRight.isEnabled = false
            axisLeft.axisMinimum = 0f
            axisLeft.axisMaximum = 100f
            axisLeft.granularity = 20f
        }

        with(binding.chartPie) {
            description.isEnabled = false
            isDrawHoleEnabled = true
            holeRadius = 42f
            setHoleColor(Color.TRANSPARENT)
            setUsePercentValues(true)
            setDrawEntryLabels(false)
            legend.isEnabled = true
            setNoDataText("Sin datos de distribución")
        }
    }

    private fun actualizarGraficaIcp(sesiones: List<SesionPostural>) {
        if (sesiones.isEmpty()) {
            binding.chartIcp.clear()
            return
        }

        val entries = sesiones.mapIndexed { i, s ->
            Entry(i.toFloat(), s.icp.toFloat())
        }

        val labels = sesiones.map { s ->
            s.tsInicio.take(10).removePrefix("20").replace("-", "/")
        }

        val dataSet = LineDataSet(entries, "ICP").apply {
            color = requireContext().getColor(R.color.peach_primary)
            setCircleColor(requireContext().getColor(R.color.peach_primary))
            lineWidth = 2f
            circleRadius = 4f
            setDrawValues(true)
            valueTextSize = 9f
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setDrawFilled(true)
            fillColor = requireContext().getColor(R.color.peach_primary)
            fillAlpha = 40
        }

        binding.chartIcp.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        binding.chartIcp.xAxis.labelCount = labels.size.coerceAtMost(5)
        binding.chartIcp.data = LineData(dataSet)
        binding.chartIcp.animateX(600)
    }

    // ── Gráfica de pie: distribución de ángulos ───────────────────────────────

    private fun actualizarGraficaPie(dist: Map<String, Double>) {
        if (dist.isEmpty() || dist.values.all { it == 0.0 }) {
            binding.chartPie.clear()
            return
        }

        val colores = listOf(
            Color.parseColor("#4CAF50"), // Excelente — verde
            Color.parseColor("#8BC34A"), // Bueno — verde claro
            Color.parseColor("#FFC107"), // Regular — amarillo
            Color.parseColor("#FF5722"), // Malo — naranja
            Color.parseColor("#F44336")  // Peligroso — rojo
        )

        val orden = listOf("Excelente", "Bueno", "Regular", "Malo", "Peligroso")
        val entries = orden.mapIndexedNotNull { i, clave ->
            val valor = dist[clave] ?: 0.0
            if (valor > 0) PieEntry(valor.toFloat(), clave) to colores[i] else null
        }

        val dataSet = PieDataSet(entries.map { it.first }, "").apply {
            colors = entries.map { it.second }
            sliceSpace = 2f
            setDrawValues(false)
            valueTextSize = 11f
            valueTextColor = Color.WHITE
        }

        binding.chartPie.data = PieData(dataSet)
        binding.chartPie.animateY(700)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun clasificarIcp(icp: Double) = when {
        icp >= 90 -> "Excelente"
        icp >= 75 -> "Bueno"
        icp >= 60 -> "Regular"
        icp >= 40 -> "Malo"
        else      -> "Crítico"
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