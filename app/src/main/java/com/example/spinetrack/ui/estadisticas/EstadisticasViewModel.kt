package com.example.spinetrack.ui.estadisticas

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.spinetrack.data.model.SesionPostural
import com.example.spinetrack.data.preferences.UserPreferences
import com.example.spinetrack.data.repository.SesionesRepository
import com.google.firebase.auth.FirebaseAuth
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class SegmentWindow(val days: Long?) {
    ALL(null),
    LAST_7(7),
    LAST_30(30),
    LAST_90(90)
}

enum class SegmentClass {
    ALL,
    EXCELENTE,
    BUENO,
    REGULAR,
    MALO,
    CRITICO
}

data class EstadisticasUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val isDisconnected: Boolean = false,
    val sesiones: List<SesionPostural> = emptyList(),
    val icpPromedio: Double = 0.0,
    val rachaActual: Int = 0,
    val totalSesiones: Int = 0,
    val tiempoTotalMin: Double = 0.0,
    val distAnguloPct: Map<String, Double> = emptyMap(),
    val segmentWindow: SegmentWindow = SegmentWindow.LAST_30,
    val segmentClass: SegmentClass = SegmentClass.ALL
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
    private var sesionesBase: List<SesionPostural> = emptyList()

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
                    sesionesBase = sesiones
                    aplicarSegmentacion()
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

                    sesionesBase = (sesionesBase + sesion)
                        .distinctBy { it.sessionId }
                        .sortedByDescending { it.tsInicio }

                    aplicarSegmentacion()
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

    fun setSegmentWindow(window: SegmentWindow) {
        if (_uiState.value.segmentWindow == window) return
        _uiState.value = _uiState.value.copy(segmentWindow = window)
        aplicarSegmentacion()
    }

    fun setSegmentClass(clase: SegmentClass) {
        if (_uiState.value.segmentClass == clase) return
        _uiState.value = _uiState.value.copy(segmentClass = clase)
        aplicarSegmentacion()
    }

    private fun aplicarSegmentacion() {
        val window = _uiState.value.segmentWindow
        val clase = _uiState.value.segmentClass
        val sesionesFiltradas = filtrarSesiones(sesionesBase, window, clase)
        val distAcumulada = acumularDistribucion(sesionesFiltradas)

        _uiState.value = _uiState.value.copy(
            isLoading = false,
            sesiones = sesionesFiltradas,
            icpPromedio = SesionesRepository.calcularIcpPromedio(sesionesFiltradas),
            rachaActual = SesionesRepository.calcularRachaActual(sesionesFiltradas),
            totalSesiones = sesionesFiltradas.size,
            tiempoTotalMin = SesionesRepository.calcularTiempoTotalMin(sesionesFiltradas),
            distAnguloPct = distAcumulada
        )
    }

    private fun filtrarSesiones(
        sesiones: List<SesionPostural>,
        window: SegmentWindow,
        clase: SegmentClass
    ): List<SesionPostural> {
        val ahora = Instant.now()
        val porVentana = sesiones.filter { sesion ->
            val inicio = parseInicio(sesion.tsInicio)
            when {
                window.days == null -> true
                inicio == null -> false
                else -> inicio.isAfter(ahora.minusSeconds(window.days * 24 * 3600))
            }
        }

        if (clase == SegmentClass.ALL) return porVentana
        return porVentana.filter { sesion ->
            normalizarClaseIcp(sesion.claseIcp) == clase
        }
    }

    private fun parseInicio(tsInicio: String): Instant? {
        if (tsInicio.isBlank()) return null
        return runCatching { Instant.parse(tsInicio) }
            .recoverCatching { OffsetDateTime.parse(tsInicio).toInstant() }
            .recoverCatching {
                LocalDateTime.parse(tsInicio, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
            }
            .getOrNull()
    }

    private fun normalizarClaseIcp(clase: String): SegmentClass {
        return when (clase.trim().lowercase()) {
            "excelente" -> SegmentClass.EXCELENTE
            "bueno" -> SegmentClass.BUENO
            "regular" -> SegmentClass.REGULAR
            "malo" -> SegmentClass.MALO
            "critico", "crítico" -> SegmentClass.CRITICO
            else -> SegmentClass.CRITICO
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