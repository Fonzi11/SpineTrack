package com.example.spinetrack.ui.dispositivo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.spinetrack.R
import com.example.spinetrack.data.model.EstadoDispositivo
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DispositivoFragment : Fragment() {

    private val vm: DispositivoViewModel by viewModels()
    private val raspberryId = "b77272f8"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_dispositivo, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val scroll = view.findViewById<ScrollView>(R.id.scroll_dispositivo)
        val dot = view.findViewById<View>(R.id.view_status_dot)
        val tvStatus = view.findViewById<TextView>(R.id.tv_status_text)
        val tvCalibrado = view.findViewById<TextView>(R.id.tv_calibrado)
        val tvSesion = view.findViewById<TextView>(R.id.tv_sesion_activa)
        val cardCalibration = view.findViewById<View>(R.id.card_calibration)
        val tvCalibrationTitle = view.findViewById<TextView>(R.id.tv_calibration_title)
        val tvCalibrationDetail = view.findViewById<TextView>(R.id.tv_calibration_detail)
        val progressCalibration = view.findViewById<ProgressBar>(R.id.progress_calibration)
        val tvStepGyro = view.findViewById<TextView>(R.id.tv_step_gyro)
        val tvStepPosture = view.findViewById<TextView>(R.id.tv_step_posture)
        val tvStepWait = view.findViewById<TextView>(R.id.tv_step_wait)

        val btnIniciar = view.findViewById<Button>(R.id.btn_iniciar)
        val btnCalibrar = view.findViewById<Button>(R.id.btn_calibrar)
        val btnDetener = view.findViewById<Button>(R.id.btn_detener)
        val btnConectar = view.findViewById<Button>(R.id.btn_conectar)
        val rgSens = view.findViewById<RadioGroup>(R.id.rg_sensibilidad)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.uiState.collectLatest { state ->
                    updateStatusUI(
                        estado = state.estadoPi,
                        isLoading = state.isLoading,
                        isCalibrating = state.isCalibrating,
                        dot = dot,
                        tvStatus = tvStatus,
                        tvCalibrado = tvCalibrado,
                        tvSesion = tvSesion,
                        btnIniciar = btnIniciar,
                        btnCalibrar = btnCalibrar,
                        btnDetener = btnDetener
                    )

                    updateCalibrationUI(
                        state = state,
                        cardCalibration = cardCalibration,
                        tvCalibrationTitle = tvCalibrationTitle,
                        tvCalibrationDetail = tvCalibrationDetail,
                        progressCalibration = progressCalibration,
                        tvStepGyro = tvStepGyro,
                        tvStepPosture = tvStepPosture,
                        tvStepWait = tvStepWait
                    )

                    if (state.calibrationVisible && state.calibrationStep in 1..3) {
                        scroll.post { scroll.smoothScrollTo(0, cardCalibration.top) }
                    }

                    state.error?.let { err ->
                        Snackbar.make(view, err, Snackbar.LENGTH_LONG).show()
                        vm.clearError()
                    }

                    state.message?.let { msg ->
                        val text = when (msg) {
                            "sens_updated" -> getString(R.string.msg_sensitivity_updated)
                            "pairing_sent" -> getString(R.string.msg_pairing_sent)
                            "pairing_accepted" -> getString(R.string.msg_pairing_accepted)
                            "calibration_started" -> getString(R.string.msg_calibration_started)
                            "calibration_ok" -> getString(R.string.msg_calibration_ok)
                            else -> getString(R.string.msg_command_sent)
                        }
                        Snackbar.make(view, text, Snackbar.LENGTH_SHORT).show()
                        vm.clearMessage()
                    }
                }
            }
        }

        btnIniciar.setOnClickListener { vm.enviarComando("iniciar") }
        btnCalibrar.setOnClickListener { vm.enviarComando("calibrar") }
        btnDetener.setOnClickListener { vm.enviarComando("detener") }

        rgSens.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rb_baja -> vm.actualizarSensibilidad("baja", 15.0, 15.0)
                R.id.rb_normal -> vm.actualizarSensibilidad("normal", 10.0, 10.0)
                R.id.rb_alta -> vm.actualizarSensibilidad("alta", 6.0, 6.0)
            }
        }

        btnConectar.setOnClickListener {
            val displayName = FirebaseAuth.getInstance().currentUser?.displayName ?: "Usuario"
            vm.requestPairing(raspberryId, displayName)
        }
    }

    private fun updateStatusUI(
        estado: EstadoDispositivo,
        isLoading: Boolean,
        isCalibrating: Boolean,
        dot: View,
        tvStatus: TextView,
        tvCalibrado: TextView,
        tvSesion: TextView,
        btnIniciar: Button,
        btnCalibrar: Button,
        btnDetener: Button
    ) {
        val online = estado.activo
        dot.setBackgroundResource(if (online) R.drawable.bg_dot_green else R.drawable.bg_dot_red)
        tvStatus.text = if (online) getString(R.string.device_connected) else getString(R.string.device_disconnected)
        val calibradoText = when {
            isCalibrating || estado.calibrando -> getString(R.string.calibration_in_progress)
            estado.calibrado -> getString(R.string.answer_yes)
            else -> getString(R.string.answer_no)
        }
        tvCalibrado.text = getString(R.string.calibrated_format, calibradoText)
        tvSesion.text = getString(R.string.session_active_format, if (estado.sesionActiva) "Si" else "No")

        // Evita deadlock: iniciar/calibrar pueden enviarse aunque el estado llegue tarde.
        btnIniciar.isEnabled = !isLoading && !isCalibrating
        btnCalibrar.isEnabled = !isLoading && !estado.sesionActiva && !isCalibrating
        btnDetener.isEnabled = estado.sesionActiva && !isLoading
    }

    private fun updateCalibrationUI(
        state: DispositivoUiState,
        cardCalibration: View,
        tvCalibrationTitle: TextView,
        tvCalibrationDetail: TextView,
        progressCalibration: ProgressBar,
        tvStepGyro: TextView,
        tvStepPosture: TextView,
        tvStepWait: TextView
    ) {
        if (!state.calibrationVisible) {
            cardCalibration.visibility = View.GONE
            return
        }

        cardCalibration.visibility = View.VISIBLE
        progressCalibration.progress = state.calibrationProgress

        val step = state.calibrationStep
        val elapsed = state.calibrationElapsedSec

        val titleRes = when (step) {
            4 -> R.string.msg_calibration_ok
            else -> R.string.calibrating
        }
        tvCalibrationTitle.text = getString(titleRes)

        val detail = when (step) {
            1 -> "Calibrando giroscopio... ${elapsed}s"
            2 -> "Adopta tu postura correcta. Midiendo postura base..."
            3 -> "Guardando parametros y esperando confirmacion del dispositivo..."
            4 -> getString(R.string.msg_calibration_ok)
            else -> getString(R.string.loading)
        }
        tvCalibrationDetail.text = detail

        tvStepGyro.alpha = if (step >= 1) 1f else 0.45f
        tvStepPosture.alpha = if (step >= 2) 1f else 0.45f
        tvStepWait.alpha = if (step >= 3) 1f else 0.45f
    }
}
