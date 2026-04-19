package com.example.spinetrack.ui.analitica

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.spinetrack.data.model.SesionPostural
import com.example.spinetrack.data.preferences.UserPreferences
import com.example.spinetrack.data.repository.SesionesRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class AnaliticaUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val isDisconnected: Boolean = false,
    val heatmapHoras: Map<Int, Double> = emptyMap(),      // hora → ICP promedio
    val tendenciaSemanal: List<Pair<String, Double>> = emptyList(), // fecha → ICP
    val regressionSlope: Double = 0.0,                    // pendiente de la tendencia
    val icpPorSensibilidad: Map<String, Double> = emptyMap() // sensibilidad → ICP prom
)

class AnaliticaViewModel(application: Application) : AndroidViewModel(application) {

    private val userPreferences = UserPreferences(application)

    private val _uiState = MutableStateFlow(AnaliticaUiState())
    val uiState: StateFlow<AnaliticaUiState> = _uiState

    private suspend fun resolveUid(): String? {
        val firebaseUid = FirebaseAuth.getInstance().currentUser?.uid
        val storedUid = userPreferences.userIdFlow.first()

        if (!firebaseUid.isNullOrBlank()) {
            if (storedUid != firebaseUid) {
                userPreferences.syncUserId(firebaseUid)
            }
            return firebaseUid
        }

        // Sin sesión Firebase activa no debe consultar analítica remota.
        return null
    }

    init {
        cargarAnalitica()
    }

    fun cargarAnalitica() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            val uid = resolveUid()
            if (uid == null) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isDisconnected = true,
                    error = "Inicia sesión para cargar analítica desde Firebase"
                )
                return@launch
            }

            SesionesRepository.obtenerSesiones(uid)
                .onSuccess { sesiones ->
                    val heatmap      = calcularHeatmap(sesiones)
                    val tendencia    = calcularTendenciaSemanal(sesiones)
                    val slope        = calcularRegresion(tendencia)
                    val porSens      = calcularIcpPorSensibilidad(sesiones)

                    _uiState.value = AnaliticaUiState(
                        isLoading          = false,
                        heatmapHoras       = heatmap,
                        tendenciaSemanal   = tendencia,
                        regressionSlope    = slope,
                        icpPorSensibilidad = porSens
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Error cargando datos"
                    )
                }
        }
    }

    /**
     * Agrupa sesiones por hora de inicio y promedia el ICP.
     * Resultado: mapa de hora (0-23) → ICP promedio en esa hora.
     */
    private fun calcularHeatmap(sesiones: List<SesionPostural>): Map<Int, Double> {
        val porHora = mutableMapOf<Int, MutableList<Double>>()
        sesiones.forEach { s ->
            val ts = s.tsInicio
            val hora = when {
                ts.length >= 13 && ts.contains("T") -> ts.substring(11, 13).toIntOrNull()
                ts.length >= 13 && ts.contains(" ") -> ts.substring(11, 13).toIntOrNull()
                else -> null
            } ?: return@forEach
            porHora.getOrPut(hora) { mutableListOf() }.add(s.icp)
        }
        return porHora.mapValues { (_, lista) -> lista.average() }
    }

    /**
     * Agrupa por día y promedia ICP — últimas 4 semanas.
     */
    private fun calcularTendenciaSemanal(sesiones: List<SesionPostural>): List<Pair<String, Double>> {
        val porFecha = mutableMapOf<String, MutableList<Double>>()
        sesiones.forEach { s ->
            val fecha = s.tsInicio.take(10)
            porFecha.getOrPut(fecha) { mutableListOf() }.add(s.icp)
        }
        return porFecha
            .mapValues { (_, lista) -> lista.average() }
            .entries
            .sortedBy { it.key }
            .takeLast(28)
            .map { it.key to it.value }
    }

    /**
     * Regresión lineal simple sobre la tendencia semanal.
     * Devuelve la pendiente (positiva = mejora, negativa = empeora).
     */
    private fun calcularRegresion(tendencia: List<Pair<String, Double>>): Double {
        if (tendencia.size < 2) return 0.0
        val x = tendencia.indices.map { it.toDouble() }
        val y = tendencia.map { it.second }
        val xMean = x.average()
        val yMean = y.average()
        val num = x.zip(y).sumOf { (xi, yi) -> (xi - xMean) * (yi - yMean) }
        val den = x.sumOf { xi -> (xi - xMean) * (xi - xMean) }
        return if (den == 0.0) 0.0 else num / den
    }

    /**
     * Agrupa sesiones por sensibilidad del dispositivo y promedia ICP.
     * Usa el campo claseIcp como proxy hasta tener sensibilidad en el payload.
     */
    private fun calcularIcpPorSensibilidad(sesiones: List<SesionPostural>): Map<String, Double> {
        // El payload actual no incluye sensibilidad directamente,
        // así que clasificamos por rangos de thetaPromedio como proxy:
        // theta bajo → sensibilidad alta, theta alto → sensibilidad baja
        val grupos = mapOf(
            "Alta"   to sesiones.filter { it.thetaPromedio < 5.0 },
            "Normal" to sesiones.filter { it.thetaPromedio in 5.0..10.0 },
            "Baja"   to sesiones.filter { it.thetaPromedio > 10.0 }
        )
        return grupos
            .filter { (_, lista) -> lista.isNotEmpty() }
            .mapValues { (_, lista) -> lista.map { it.icp }.average() }
    }
}