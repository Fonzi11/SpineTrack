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
        setupSegmentacion()
        observeState()

        binding.btnRefrescar.setOnClickListener {
            viewModel.cargarEstadisticas()
        }

        binding.btnReintentarConexion.setOnClickListener {
            viewModel.cargarEstadisticas()
        }
    }

    private fun setupSegmentacion() {
        binding.chipGroupWindow.setOnCheckedChangeListener { _, checkedId ->
            val window = when (checkedId) {
                R.id.chip_window_7d -> SegmentWindow.LAST_7
                R.id.chip_window_90d -> SegmentWindow.LAST_90
                R.id.chip_window_all -> SegmentWindow.ALL
                else -> SegmentWindow.LAST_30
            }
            viewModel.setSegmentWindow(window)
        }

        binding.chipGroupClass.setOnCheckedChangeListener { _, checkedId ->
            val clase = when (checkedId) {
                R.id.chip_class_excellent -> SegmentClass.EXCELENTE
                R.id.chip_class_good -> SegmentClass.BUENO
                R.id.chip_class_regular -> SegmentClass.REGULAR
                R.id.chip_class_bad -> SegmentClass.MALO
                R.id.chip_class_critical -> SegmentClass.CRITICO
                else -> SegmentClass.ALL
            }
            viewModel.setSegmentClass(clase)
        }

        sincronizarChips(viewModel.uiState.value.segmentWindow, viewModel.uiState.value.segmentClass)
    }

    private fun sincronizarChips(window: SegmentWindow, clase: SegmentClass) {
        val windowId = when (window) {
            SegmentWindow.LAST_7 -> R.id.chip_window_7d
            SegmentWindow.LAST_30 -> R.id.chip_window_30d
            SegmentWindow.LAST_90 -> R.id.chip_window_90d
            SegmentWindow.ALL -> R.id.chip_window_all
        }
        if (binding.chipGroupWindow.checkedChipId != windowId) {
            binding.chipGroupWindow.check(windowId)
        }

        val classId = when (clase) {
            SegmentClass.EXCELENTE -> R.id.chip_class_excellent
            SegmentClass.BUENO -> R.id.chip_class_good
            SegmentClass.REGULAR -> R.id.chip_class_regular
            SegmentClass.MALO -> R.id.chip_class_bad
            SegmentClass.CRITICO -> R.id.chip_class_critical
            SegmentClass.ALL -> R.id.chip_class_all
        }
        if (binding.chipGroupClass.checkedChipId != classId) {
            binding.chipGroupClass.check(classId)
        }
    }

    private fun etiquetaVentana(window: SegmentWindow): String = when (window) {
        SegmentWindow.ALL -> getString(R.string.stats_window_all)
        SegmentWindow.LAST_7 -> getString(R.string.stats_window_7d)
        SegmentWindow.LAST_30 -> getString(R.string.stats_window_30d)
        SegmentWindow.LAST_90 -> getString(R.string.stats_window_90d)
    }

    private fun etiquetaClase(clase: SegmentClass): String = when (clase) {
        SegmentClass.ALL -> getString(R.string.stats_class_all)
        SegmentClass.EXCELENTE -> getString(R.string.stats_class_excellent)
        SegmentClass.BUENO -> getString(R.string.stats_class_good)
        SegmentClass.REGULAR -> getString(R.string.stats_class_regular)
        SegmentClass.MALO -> getString(R.string.stats_class_bad)
        SegmentClass.CRITICO -> getString(R.string.stats_class_critical)
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                binding.progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE

                val segmentLabel = getString(
                    R.string.stats_segment_label_format,
                    etiquetaVentana(state.segmentWindow),
                    etiquetaClase(state.segmentClass)
                )
                binding.tvSegmentoSubtitulo.text = segmentLabel
                sincronizarChips(state.segmentWindow, state.segmentClass)

                when {
                    state.error != null -> {
                        mostrarEstadoError(
                            mensaje = state.error
                        )
                    }

                    state.isDisconnected -> {
                        mostrarEstadoError(
                            mensaje = getString(R.string.stats_error_disconnected)
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
        binding.tvRacha.text = getString(R.string.stats_streak_format, state.rachaActual)
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
            setNoDataText(getString(R.string.stats_chart_empty))
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
            setNoDataText(getString(R.string.stats_pie_empty))
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
        icp >= 90 -> getString(R.string.stats_class_excellent)
        icp >= 75 -> getString(R.string.stats_class_good)
        icp >= 60 -> getString(R.string.stats_class_regular)
        icp >= 40 -> getString(R.string.stats_class_bad)
        else      -> getString(R.string.stats_class_critical)
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