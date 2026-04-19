package com.example.spinetrack.ui.analitica

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.spinetrack.R
import com.example.spinetrack.databinding.FragmentAnaliticaBinding
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import kotlinx.coroutines.launch

class AnaliticaFragment : Fragment() {

    private var _binding: FragmentAnaliticaBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AnaliticaViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAnaliticaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupCharts()
        observeState()
        binding.btnRefrescar.setOnClickListener { viewModel.cargarAnalitica() }
        binding.btnReintentarConexion.setOnClickListener { viewModel.cargarAnalitica() }
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
                            mensaje = "Inicia sesion y verifica internet para ver tendencias."
                        )
                    }

                    else -> {
                        binding.layoutDesconexion.visibility = View.GONE
                        binding.layoutContenido.visibility = if (state.isLoading) View.GONE else View.VISIBLE

                        if (!state.isLoading) {
                            actualizarHeatmap(state.heatmapHoras)
                            actualizarTendencia(state.tendenciaSemanal, state.regressionSlope)
                            actualizarSensibilidad(state.icpPorSensibilidad)
                        }
                    }
                }
            }
        }
    }

    private fun mostrarEstadoError(mensaje: String) {
        binding.layoutDesconexion.visibility = View.VISIBLE
        binding.layoutContenido.visibility = View.GONE
        binding.tvMensajeDesconexion.text = mensaje
    }

    // ── Setup inicial de charts ───────────────────────────────────────────────

    private fun setupCharts() {
        // Heatmap (BarChart horizontal por hora)
        with(binding.chartHeatmap) {
            description.isEnabled = false
            legend.isEnabled = false
            setTouchEnabled(false)
            setNoDataText("Sin datos por hora")
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.setDrawGridLines(false)
            xAxis.granularity = 1f
            axisRight.isEnabled = false
            axisLeft.axisMinimum = 0f
            axisLeft.axisMaximum = 100f
        }

        // Tendencia semanal (LineChart)
        with(binding.chartTendencia) {
            description.isEnabled = false
            legend.isEnabled = false
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(false)
            setNoDataText("Sin datos de tendencia")
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.setDrawGridLines(false)
            xAxis.granularity = 1f
            axisRight.isEnabled = false
            axisLeft.axisMinimum = 0f
            axisLeft.axisMaximum = 100f
        }

        // Sensibilidad vs ICP (BarChart)
        with(binding.chartSensibilidad) {
            description.isEnabled = false
            legend.isEnabled = false
            setTouchEnabled(false)
            setNoDataText("Sin datos de sensibilidad")
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.setDrawGridLines(false)
            xAxis.granularity = 1f
            axisRight.isEnabled = false
            axisLeft.axisMinimum = 0f
            axisLeft.axisMaximum = 100f
        }
    }

    // ── Heatmap por hora ──────────────────────────────────────────────────────

    private fun actualizarHeatmap(heatmap: Map<Int, Double>) {
        if (heatmap.isEmpty()) {
            binding.chartHeatmap.clear()
            return
        }

        // Mostrar las 24 horas, rellenar con 0 las que no tienen datos
        val entries = (0..23).map { hora ->
            val icp = heatmap[hora] ?: 0.0
            BarEntry(hora.toFloat(), icp.toFloat())
        }

        val colores = entries.map { e ->
            when {
                e.y == 0f  -> Color.parseColor("#E0E0E0")  // sin datos
                e.y >= 75  -> Color.parseColor("#4CAF50")  // bueno
                e.y >= 50  -> Color.parseColor("#FFC107")  // regular
                else       -> Color.parseColor("#F44336")  // malo
            }
        }

        val labels = (0..23).map { h -> if (h % 3 == 0) "${h}h" else "" }

        val dataSet = BarDataSet(entries, "ICP por hora").apply {
            setColors(colores)
            setDrawValues(false)
        }

        binding.chartHeatmap.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        binding.chartHeatmap.data = BarData(dataSet).apply { barWidth = 0.8f }
        binding.chartHeatmap.animateY(600)

        // Texto de la peor hora
        val peorHora = heatmap.filter { it.value > 0 }.minByOrNull { it.value }
        val mejorHora = heatmap.filter { it.value > 0 }.maxByOrNull { it.value }
        if (peorHora != null && mejorHora != null) {
            binding.tvInsightHora.text =
                "📉 Peor hora: ${peorHora.key}:00 (ICP ${String.format("%.0f", peorHora.value)})  " +
                        "📈 Mejor: ${mejorHora.key}:00 (${String.format("%.0f", mejorHora.value)})"
        }
    }

    // ── Tendencia semanal ─────────────────────────────────────────────────────

    private fun actualizarTendencia(
        tendencia: List<Pair<String, Double>>,
        slope: Double
    ) {
        if (tendencia.isEmpty()) {
            binding.chartTendencia.clear()
            return
        }

        val entries = tendencia.mapIndexed { i, (_, icp) ->
            Entry(i.toFloat(), icp.toFloat())
        }

        // Línea de regresión
        val n = tendencia.size
        val yMean = tendencia.map { it.second }.average()
        val intercept = yMean - slope * (n / 2.0)
        val regressionEntries = listOf(
            Entry(0f, (intercept).toFloat().coerceIn(0f, 100f)),
            Entry((n - 1).toFloat(), (intercept + slope * (n - 1)).toFloat().coerceIn(0f, 100f))
        )

        val dataSetIcp = LineDataSet(entries, "ICP diario").apply {
            color = requireContext().getColor(R.color.peach_primary)
            setCircleColor(requireContext().getColor(R.color.peach_primary))
            lineWidth = 2f
            circleRadius = 3f
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setDrawFilled(true)
            fillColor = requireContext().getColor(R.color.peach_primary)
            fillAlpha = 30
        }

        val dataSetRegresion = LineDataSet(regressionEntries, "Tendencia").apply {
            color = if (slope >= 0) Color.parseColor("#4CAF50") else Color.parseColor("#F44336")
            lineWidth = 1.5f
            enableDashedLine(10f, 5f, 0f)
            setDrawCircles(false)
            setDrawValues(false)
        }

        val labels = tendencia.map { (fecha, _) ->
            fecha.removePrefix("20").replace("-", "/")
        }

        binding.chartTendencia.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        binding.chartTendencia.xAxis.labelCount = labels.size.coerceAtMost(7)
        binding.chartTendencia.data = LineData(dataSetIcp, dataSetRegresion)
        binding.chartTendencia.animateX(700)

        // Insight de tendencia
        val tendenciaTexto = when {
            slope > 0.5  -> "📈 Tu postura está mejorando progresivamente"
            slope < -0.5 -> "📉 Tu postura muestra tendencia a empeorar"
            else         -> "➡️ Tu postura se mantiene estable"
        }
        val slopeTexto = String.format("%+.2f pts/día", slope)
        binding.tvInsightTendencia.text = "$tendenciaTexto ($slopeTexto)"
        binding.tvInsightTendencia.setTextColor(
            if (slope >= 0) Color.parseColor("#4CAF50") else Color.parseColor("#F44336")
        )
    }

    // ── Sensibilidad vs ICP ───────────────────────────────────────────────────

    private fun actualizarSensibilidad(porSensibilidad: Map<String, Double>) {
        if (porSensibilidad.isEmpty()) {
            binding.chartSensibilidad.clear()
            return
        }

        val orden = listOf("Alta", "Normal", "Baja")
        val etiquetas = orden.filter { porSensibilidad.containsKey(it) }
        val entries = etiquetas.mapIndexed { i, clave ->
            BarEntry(i.toFloat(), porSensibilidad[clave]!!.toFloat())
        }

        val colores = listOf(
            Color.parseColor("#FF5722"),  // Alta — más exigente
            Color.parseColor("#FFC107"),  // Normal
            Color.parseColor("#4CAF50")   // Baja — más permisiva
        )

        val dataSet = BarDataSet(entries, "ICP por sensibilidad").apply {
            setColors(colores.take(etiquetas.size))
            valueTextSize = 11f
            valueTextColor = Color.DKGRAY
        }

        binding.chartSensibilidad.xAxis.valueFormatter =
            IndexAxisValueFormatter(etiquetas)
        binding.chartSensibilidad.xAxis.labelCount = etiquetas.size
        binding.chartSensibilidad.data = BarData(dataSet).apply { barWidth = 0.5f }
        binding.chartSensibilidad.animateY(600)

        // Insight
        val mejor = porSensibilidad.maxByOrNull { it.value }
        if (mejor != null) {
            binding.tvInsightSensibilidad.text =
                "💡 Mejor ICP con sensibilidad ${mejor.key} " +
                        "(${String.format("%.1f", mejor.value)} pts)"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}