package com.example.spinetrack.ui.estadisticas

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

data class EstadisticasUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val isDisconnected: Boolean = false,
    val sesiones: List<SesionPostural> = emptyList(),
    val icpPromedio: Double = 0.0,
    val rachaActual: Int = 0,
    val totalSesiones: Int = 0,
    val tiempoTotalMin: Double = 0.0,
    val distAnguloPct: Map<String, Double> = emptyMap()
)

class EstadisticasViewModel(application: Application) : AndroidViewModel(application) {

    private val userPreferences = UserPreferences(application)

    private val _uiState = MutableStateFlow(EstadisticasUiState())
    val uiState: StateFlow<EstadisticasUiState> = _uiState
    // eventos de resultado de importación (mensaje corto para UI)
    private val _importResult = MutableStateFlow<String?>(null)
    val importResult: StateFlow<String?> = _importResult
    private var lastImportedSessionId: String? = null
    private var importingSessionId: String? = null

    private suspend fun resolveUid(): String? {
        val firebaseUid = FirebaseAuth.getInstance().currentUser?.uid
        val storedUid = userPreferences.userIdFlow.first()

        // Fuente canónica: FirebaseAuth; DataStore se corrige automáticamente.
        if (!firebaseUid.isNullOrBlank()) {
            if (storedUid != firebaseUid) {
                userPreferences.syncUserId(firebaseUid)
            }
            return firebaseUid
        }

        // Si no hay sesión Firebase activa, no leer datos remotos.
        return null
    }

    init {
        cargarEstadisticas()
        // Observa cambios en la última session id publicada para importar automáticamente
        viewModelScope.launch {
            userPreferences.lastSessionIdFlow.collect { lastId ->
                if (!lastId.isNullOrBlank()) {
                    importarSesionPorId(lastId, force = true)
                }
            }
        }
        // Observa una marca que indica que las estadísticas del usuario han cambiado
        viewModelScope.launch {
            userPreferences.lastStatsUpdateFlow.collect { ts ->
                if (!ts.isNullOrBlank()) {
                    // recargar estadísticas cuando haya un cambio externo
                    cargarEstadisticas()
                }
            }
        }
    }

    fun cargarEstadisticas() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            val uid = resolveUid()
            if (uid == null) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isDisconnected = true,
                    error = "Inicia sesión para cargar estadísticas desde Firebase"
                )
                return@launch
            }

            SesionesRepository.obtenerUltimasSesiones(uid, limite = 30)
                .onSuccess { sesiones ->
                    val distAcumulada = acumularDistribucion(sesiones)
                    _uiState.value = EstadisticasUiState(
                        isLoading       = false,
                        sesiones        = sesiones,
                        icpPromedio     = SesionesRepository.calcularIcpPromedio(sesiones.take(7)),
                        rachaActual     = SesionesRepository.calcularRachaActual(sesiones),
                        totalSesiones   = sesiones.size,
                        tiempoTotalMin  = SesionesRepository.calcularTiempoTotalMin(sesiones),
                        distAnguloPct   = distAcumulada
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
     * Importa una sesión concreta (por sessionId) desde Firebase y la añade
     * a las estadísticas locales si pertenece al usuario.
     */
    fun importarSesionPorId(sessionId: String, force: Boolean = false) {
        viewModelScope.launch {
            // evitar reimportar la misma sesión repetidamente
            if (!force && sessionId == lastImportedSessionId) {
                _importResult.value = "Sesión ya importada"
                return@launch
            }
            if (importingSessionId == sessionId) {
                return@launch
            }
            importingSessionId = sessionId
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val uid = resolveUid()
            if (uid == null) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isDisconnected = true,
                    error = "Inicia sesión para importar sesiones"
                )
                importingSessionId = null
                return@launch
            }

            val result = SesionesRepository.obtenerSesion(uid, sessionId)
            result
                .onSuccess { sesion ->
                    if (sesion == null) {
                        _uiState.value = _uiState.value.copy(isLoading = false, error = "Sesión no encontrada")
                        _importResult.value = "Sesión no encontrada"
                        viewModelScope.launch { userPreferences.saveLastSessionId(null) }
                        return@onSuccess
                    }

                    // Añadir/actualizar la sesión en la lista actual y recalcular métricas
                    val sesionesActuales = (_uiState.value.sesiones + sesion)
                        .distinctBy { it.sessionId }
                        .sortedByDescending { it.tsInicio }

                    val distAcumulada = acumularDistribucion(sesionesActuales)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        sesiones = sesionesActuales,
                        icpPromedio = SesionesRepository.calcularIcpPromedio(sesionesActuales.take(7)),
                        rachaActual = SesionesRepository.calcularRachaActual(sesionesActuales),
                        totalSesiones = sesionesActuales.size,
                        tiempoTotalMin = SesionesRepository.calcularTiempoTotalMin(sesionesActuales),
                        distAnguloPct = distAcumulada
                    )
                    lastImportedSessionId = sesion.sessionId
                    _importResult.value = "Sesión importada: ${sesion.sessionId.take(8)}"
                    viewModelScope.launch { userPreferences.saveLastSessionId(null) }
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "Error importando sesión")
                    _importResult.value = e.message ?: "Error importando sesión"
                }
            importingSessionId = null
        }
    }

    /**
     * Promedia la distribución de ángulos de todas las sesiones.
     */
    private fun acumularDistribucion(sesiones: List<SesionPostural>): Map<String, Double> {
        if (sesiones.isEmpty()) return emptyMap()
        val claves = listOf("Excelente", "Bueno", "Regular", "Malo", "Peligroso")
        return claves.associateWith { clave ->
            sesiones.mapNotNull { it.distAnguloPct[clave] }.average().let {
                if (it.isNaN()) 0.0 else it
            }
        }
    }
}