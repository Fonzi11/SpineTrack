package com.example.spinetrack.ui.perfil

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.spinetrack.data.preferences.UserPreferences
import com.example.spinetrack.data.repository.UserStatsRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * ViewModel de Perfil.
 * Mantiene nombre, nivel y estadisticas sincronizadas con Firebase en tiempo real.
 */
class PerfilViewModel(application: Application) : AndroidViewModel(application) {

    data class UiState(
        val isLoading: Boolean = true,
        val isError: Boolean = false,
        val errorMessage: String? = null,
        val nombre: String = "Usuario",
        val email: String = "",
        val nivel: Int = 1,
        val puntosNivelActual: Int = 0,
        val puntosSiguienteNivel: Int = 100,
        val rachaActual: Int = 0,
        val mejorRacha: Int = 0,
        val puntosTotales: Int = 0,
        val leccionesCompletadas: Int = 0
    )

    private val preferences = UserPreferences(application)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        observarPerfil()
    }

    private fun observarPerfil() {
        viewModelScope.launch {
            val auth = FirebaseAuth.getInstance().currentUser
            val uid = auth?.uid ?: preferences.userIdFlow.first()

            if (uid.isNullOrBlank()) {
                _uiState.value = UiState(
                    isLoading = false,
                    isError = true,
                    errorMessage = "Usuario no autenticado."
                )
                return@launch
            }

            UserStatsRepository.observeUserProfileStats(uid).collect { result ->
                result.fold(
                    onSuccess = { profile ->
                        _uiState.value = UiState(
                            isLoading = false,
                            isError = false,
                            errorMessage = null,
                            nombre = profile.nombre,
                            email = profile.email,
                            nivel = profile.nivel,
                            puntosNivelActual = profile.puntosNivelActual,
                            puntosSiguienteNivel = profile.puntosSiguienteNivel,
                            rachaActual = profile.rachaActual,
                            mejorRacha = profile.mejorRacha,
                            puntosTotales = profile.puntos,
                            leccionesCompletadas = profile.lecciones
                        )

                        // Mantiene DataStore alineado con los datos visibles del perfil.
                        if (profile.nombre.isNotBlank()) {
                            viewModelScope.launch {
                                preferences.updateUserName(profile.nombre)
                            }
                        }
                    },
                    onFailure = { error ->
                        val current = _uiState.value
                        _uiState.value = current.copy(
                            isLoading = false,
                            isError = true,
                            errorMessage = error.message ?: "No fue posible cargar el perfil."
                        )
                    }
                )
            }
        }
    }
}


