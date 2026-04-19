package com.example.spinetrack.ui.realtime

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import androidx.core.graphics.toColorInt
import java.util.Locale
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.fragment.app.activityViewModels
import com.example.spinetrack.data.model.RealtimePostura
import com.example.spinetrack.databinding.FragmentRealtimeBinding
import kotlinx.coroutines.launch
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.example.spinetrack.R

class RealtimeFragment : Fragment() {

    private var _binding: FragmentRealtimeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: RealtimeViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRealtimeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeState()
        binding.btnReintentar.setOnClickListener { viewModel.iniciarEscucha() }
        // Mostrar el último payload JSON (debug) en la UI
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.rawJson.collect { payload ->
                binding.tvDebugPayload.text = payload
            }
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                when (state) {
                    is RealtimeUiState.Conectando   -> mostrarConectando()
                    is RealtimeUiState.SinDispositivo -> mostrarSinDispositivo()
                    is RealtimeUiState.Activo       -> mostrarDatos(state.datos)
                    is RealtimeUiState.Error        -> mostrarError(state.mensaje)
                }
            }
        }
    }

    // ── Estados de UI ─────────────────────────────────────────────────────────

    private fun mostrarConectando() {
        binding.layoutConectando.visibility  = View.VISIBLE
        binding.layoutDatos.visibility       = View.GONE
        binding.layoutSinDispositivo.visibility = View.GONE
    }

    private fun mostrarSinDispositivo() {
        binding.layoutConectando.visibility     = View.GONE
        binding.layoutDatos.visibility          = View.GONE
        binding.layoutSinDispositivo.visibility = View.VISIBLE
    }

    private fun mostrarError(mensaje: String) {
        mostrarSinDispositivo()
        binding.tvMensajeSinDispositivo.text = mensaje
    }

    private fun mostrarDatos(datos: RealtimePostura) {
        @SuppressLint("SetTextI18n")
        binding.layoutConectando.visibility     = View.GONE
        binding.layoutSinDispositivo.visibility = View.GONE
        binding.layoutDatos.visibility          = View.VISIBLE

        actualizarEstadoPrincipal(datos)
        actualizarIcp(datos)
        actualizarAngulos(datos)
        actualizarContadores(datos)
        actualizarTemperatura(datos)
    }

    // ── Actualización de vistas ───────────────────────────────────────────────

    private fun actualizarEstadoPrincipal(datos: RealtimePostura) {
        @SuppressLint("SetTextI18n")
        if (datos.buenaPostura) {
            binding.tvEstado.text = getString(R.string.status_good)
            binding.tvEstado.setTextColor("#4CAF50".toColorInt())
            binding.cardEstado.setCardBackgroundColor("#F1F8F1".toColorInt())
            binding.tvClaseAngulo.text = datos.claseAngulo
        } else {
            binding.tvEstado.text = getString(R.string.status_bad)
            binding.tvEstado.setTextColor("#FF5722".toColorInt())
            binding.cardEstado.setCardBackgroundColor("#FFF3F0".toColorInt())
            binding.tvClaseAngulo.text = datos.claseAngulo
        }
    }

    private fun actualizarIcp(datos: RealtimePostura) {
        // Animar el progreso del ICP
        val progreso = datos.icpParcial.toInt()
        animarProgreso(binding.progressIcp, progreso)

        binding.tvIcpValor.text = String.format(Locale.getDefault(), "%.1f", datos.icpParcial)
        binding.tvIcpClase.text = datos.claseIcp
        binding.tvIcpValor.setTextColor(colorIcp(datos.icpParcial))
    }

    private fun actualizarAngulos(datos: RealtimePostura) {
        binding.tvPitch.text    = String.format(Locale.getDefault(), "%+.1f°", datos.pitch)
        binding.tvRoll.text     = String.format(Locale.getDefault(), "%+.1f°", datos.roll)
        binding.tvTheta.text    = String.format(Locale.getDefault(), "%.2f°", datos.thetaAbs)

        // Color según desviación
        val colorPitch = if (Math.abs(datos.pitchDev) > 10) "#FF5722".toColorInt()
        else "#4CAF50".toColorInt()
        val colorRoll  = if (Math.abs(datos.rollDev) > 10) "#FF5722".toColorInt()
        else "#4CAF50".toColorInt()
        binding.tvPitch.setTextColor(colorPitch)
        binding.tvRoll.setTextColor(colorRoll)
    }

    private fun actualizarContadores(datos: RealtimePostura) {
        binding.tvTiempoBueno.text  = String.format(Locale.getDefault(), "%.1f min", datos.tBuenaMin)
        binding.tvTiempoMalo.text   = String.format(Locale.getDefault(), "%.1f min", datos.tMalaMin)
        binding.tvNumAlertas.text   = datos.numAlertas.toString()

        // Barra de progreso buena vs mala postura
        val total = datos.tBuenaMin + datos.tMalaMin
        val pct   = if (total > 0) ((datos.tBuenaMin / total) * 100).toInt() else 100
        animarProgreso(binding.progressPostura, pct)
    }

    private fun actualizarTemperatura(datos: RealtimePostura) {
        binding.tvTemp.text = datos.tempC?.let {
            String.format(Locale.getDefault(), "%.1f °C", it)
        } ?: "—"
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun animarProgreso(progressBar: android.widget.ProgressBar, target: Int) {
        val animator = ValueAnimator.ofInt(progressBar.progress, target)
        animator.duration = 400
        animator.interpolator = DecelerateInterpolator()
        animator.addUpdateListener { progressBar.progress = it.animatedValue as Int }
        animator.start()
    }

    private fun colorIcp(icp: Double) = when {
        icp >= 75 -> "#4CAF50".toColorInt()
        icp >= 50 -> "#FFC107".toColorInt()
        else      -> "#F44336".toColorInt()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}