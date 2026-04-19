package com.example.spinetrack.ui.dispositivo

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.spinetrack.data.model.EstadoDispositivo
import com.google.firebase.auth.FirebaseAuth
import com.example.spinetrack.data.preferences.UserPreferences
import com.example.spinetrack.data.repository.DispositivoRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class DispositivoUiState(
    val estadoPi: EstadoDispositivo = EstadoDispositivo(),
    val isLoading: Boolean = false,
    val isCalibrating: Boolean = false,
    val calibrationVisible: Boolean = false,
    val calibrationStep: Int = 0,
    val calibrationElapsedSec: Int = 0,
    val calibrationProgress: Int = 0,
    val error: String? = null,
    val message: String? = null
)

class DispositivoViewModel(application: Application) : AndroidViewModel(application) {

    private val userPreferences = UserPreferences(application)

    private val _uiState = MutableStateFlow(DispositivoUiState())
    val uiState: StateFlow<DispositivoUiState> = _uiState.asStateFlow()

    private var currentUid: String? = null
    private var calibrationWatcherJob: Job? = null
    private var wasCalibrandoReported: Boolean = false

    private suspend fun resolveUid(): String? {
        val firebaseUid = FirebaseAuth.getInstance().currentUser?.uid
        val storedUid = userPreferences.userIdFlow.first()
        if (!firebaseUid.isNullOrBlank()) {
            if (storedUid != firebaseUid) {
                userPreferences.syncUserId(firebaseUid)
            }
            return firebaseUid
        }
        return storedUid
    }

    private suspend fun resolveDisplayName(): String {
        val firebaseUser = FirebaseAuth.getInstance().currentUser
        return firebaseUser?.displayName
            ?.takeIf { it.isNotBlank() }
            ?: userPreferences.userNameFlow.first()?.takeIf { it.isNotBlank() }
            ?: firebaseUser?.email?.substringBefore("@")?.takeIf { it.isNotBlank() }
            ?: "Usuario"
    }

    init {
        viewModelScope.launch {
            val uid = resolveUid()
            if (uid != null) {
                currentUid = uid
                val displayName = resolveDisplayName()
                val regResult = try {
                    DispositivoRepository.registrarDispositivo(uid, displayName)
                } catch (e: Exception) {
                    Result.failure<Unit>(e)
                }
                if (regResult.isFailure) {
                    android.util.Log.w("DispositivoVM", "registrarDispositivo: ${regResult.exceptionOrNull()?.message}")
                    // Mostrar mensaje no fatal en UI
                    _uiState.update { it.copy(error = regResult.exceptionOrNull()?.message) }
                }
                listenEstado(uid)
            } else {
                _uiState.update { it.copy(error = "Usuario no identificado") }
            }
        }
    }

    private fun listenEstado(uid: String) {
        viewModelScope.launch {
            DispositivoRepository.escucharEstado(uid)
                .catch { e -> _uiState.update { it.copy(error = e.message) } }
                .collect { map ->
                    val estado = EstadoDispositivo.fromMap(map)
                    val currentState = _uiState.value

                    if (estado.calibrando && !wasCalibrandoReported && !currentState.isCalibrating) {
                        beginCalibrationFlow()
                    }

                    wasCalibrandoReported = estado.calibrando

                    _uiState.update { current ->
                        val calibrationFinished = current.isCalibrating && estado.calibrado
                        current.copy(
                            estadoPi = estado,
                            isCalibrating = if (calibrationFinished) false else (current.isCalibrating || estado.calibrando),
                            calibrationVisible = current.calibrationVisible || estado.calibrando || calibrationFinished,
                            calibrationStep = if (calibrationFinished) 4 else current.calibrationStep,
                            calibrationProgress = if (calibrationFinished) 100 else current.calibrationProgress,
                            error = null,
                            message = if (calibrationFinished) "calibration_ok" else current.message
                        )
                    }
                }
        }
    }

    fun enviarComando(comando: String) {
        val uid = currentUid ?: run {
            _uiState.update { it.copy(error = "Usuario no identificado") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val res = DispositivoRepository.enviarComando(uid, comando)
            if (res.isSuccess) {
                if (comando == "calibrar" || comando == "iniciar") {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isCalibrating = true,
                            calibrationVisible = true,
                            calibrationStep = 1,
                            calibrationElapsedSec = 0,
                            calibrationProgress = 5,
                            error = null,
                            message = "calibration_started"
                        )
                    }
                    startCalibrationWatcher()
                } else {
                    _uiState.update { it.copy(isLoading = false, error = null, message = "cmd_$comando") }
                }
            } else {
                _uiState.update { it.copy(isLoading = false, error = res.exceptionOrNull()?.message, message = null) }
            }
        }
    }

    private fun startCalibrationWatcher() {
        calibrationWatcherJob?.cancel()
        calibrationWatcherJob = viewModelScope.launch {
            val timeoutMs = 70_000L
            var elapsed = 0L
            while (elapsed < timeoutMs) {
                val st = _uiState.value.estadoPi
                if (st.calibrado) {
                    _uiState.update {
                        it.copy(
                            isCalibrating = false,
                            calibrationVisible = true,
                            calibrationStep = 4,
                            calibrationElapsedSec = (elapsed / 1000L).toInt(),
                            calibrationProgress = 100,
                            message = "calibration_ok"
                        )
                    }
                    return@launch
                }

                val elapsedSec = (elapsed / 1000L).toInt()
                val step = when {
                    elapsedSec < 8 -> 1
                    elapsedSec < 18 -> 2
                    else -> 3
                }
                val progress = when (step) {
                    1 -> (10 + elapsedSec * 5).coerceAtMost(40)
                    2 -> (45 + (elapsedSec - 8) * 4).coerceAtMost(78)
                    else -> (80 + (elapsedSec - 18) * 2).coerceAtMost(98)
                }

                _uiState.update {
                    it.copy(
                        isCalibrating = true,
                        calibrationVisible = true,
                        calibrationStep = step,
                        calibrationElapsedSec = elapsedSec,
                        calibrationProgress = progress
                    )
                }

                delay(1_000L)
                elapsed += 1_000L
            }
            _uiState.update {
                it.copy(
                    isCalibrating = false,
                    calibrationStep = 0,
                    calibrationProgress = 0,
                    error = "La calibración no se confirmó a tiempo"
                )
            }
        }
    }

    private fun beginCalibrationFlow() {
        if (_uiState.value.isCalibrating) return
        _uiState.update {
            it.copy(
                isCalibrating = true,
                calibrationVisible = true,
                calibrationStep = 1,
                calibrationElapsedSec = 0,
                calibrationProgress = 5,
                error = null
            )
        }
        startCalibrationWatcher()
    }

    fun actualizarSensibilidad(sensibilidad: String, pitchOn: Double, rollOn: Double) {
        val uid = currentUid ?: run {
            _uiState.update { it.copy(error = "Usuario no identificado") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val res = DispositivoRepository.actualizarSensibilidad(uid, sensibilidad, pitchOn, rollOn)
            if (res.isSuccess) {
                _uiState.update { it.copy(isLoading = false, error = null, message = "sens_updated") }
            } else {
                _uiState.update { it.copy(isLoading = false, error = res.exceptionOrNull()?.message, message = null) }
            }
        }
    }

    /**
     * Solicita emparejamiento con la Raspberry (pairing). Escribe la petición y espera un ack.
     */
    fun requestPairing(raspberryId: String, displayName: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: currentUid
                ?: run {
                    _uiState.update { it.copy(error = "Usuario no identificado") }
                    return
                }
        currentUid = uid

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val resolvedName = if (displayName.isNotBlank()) displayName else resolveDisplayName()
            val res = DispositivoRepository.requestPairing(raspberryId, resolvedName)
            if (res.isSuccess) {
                _uiState.update { it.copy(isLoading = false, message = "pairing_sent") }
                // Esperar ack (timeout 30s)
                val ack = withTimeoutOrNull(30_000) {
                    DispositivoRepository.observePairingAck(raspberryId, uid)
                        .filterNotNull()
                        .first()
                }
                if (ack == null) {
                    _uiState.update { it.copy(isLoading = false, error = "No se recibio respuesta del dispositivo (timeout)") }
                } else {
                    val accepted = when (val v = ack["accepted"]) {
                        is Boolean -> v
                        is Long -> v != 0L
                        is Number -> v.toInt() != 0
                        else -> false
                    }
                    if (accepted) {
                        _uiState.update { it.copy(isLoading = false, message = "pairing_accepted") }
                    } else {
                        val msg = ack["message"] as? String ?: "Emparejamiento rechazado"
                        _uiState.update { it.copy(isLoading = false, error = msg) }
                    }
                }
            } else {
                _uiState.update { it.copy(isLoading = false, error = res.exceptionOrNull()?.message) }
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}

